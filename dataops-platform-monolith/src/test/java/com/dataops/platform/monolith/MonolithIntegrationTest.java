package com.dataops.platform.monolith;

import com.dataops.platform.persistence.service.PersistenceService;
import com.dataops.platform.monolith.DataOpsMonolithApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test using a real Postgres via Testcontainers.
 *
 * <p>This is the regression guard for Phase 1 Item 1 (in-memory store not being
 * populated by ingestion): if the {@code IngestionService -> InMemoryStorageService}
 * wire-up is broken, the analytics assertion fails because the record is in
 * Postgres but not in the in-memory cache the analytics controller reads from.
 *
 * <p>The class is annotated {@link EnabledIfSystemProperty} with
 * {@code runDockerIT} — set {@code -DrunDockerIT=true} to enable in environments
 * with Docker (the project's CI does this). Without it the class is skipped, so
 * local builds on machines without Docker do not fail.
 */
@Testcontainers
@EnabledIfSystemProperty(named = "runDockerIT", matches = "true")
@SpringBootTest(
        classes = DataOpsMonolithApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.hbm2ddl.validate=false",
                "app.kafka.enabled=false",
                "API_KEY=test-key"
        })
@DisplayName("Monolith end-to-end integration with Postgres Testcontainer")
class MonolithIntegrationTest {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("dataops")
            .withUsername("test")
            .withPassword("test");

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    PersistenceService persistenceService;

    private HttpHeaders authedHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set(API_KEY_HEADER, "test-key");
        return h;
    }

    @Test
    @DisplayName("Full ingest -> retrieve by id -> analytics path against real Postgres")
    void ingestRetrieveAndAnalyticsAgainstRealPostgres() {
        // 1) Ingest a record through the controller
        Map<String, Object> payload = Map.of("name", "integration", "value", 42);
        HttpEntity<Map<String, Object>> ingest = new HttpEntity<>(payload, authedHeaders());
        ResponseEntity<Map> ingestResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/ingest/json",
                HttpMethod.POST, ingest, Map.class);
        assertEquals(201, ingestResponse.getStatusCode().value());
        assertNotNull(ingestResponse.getBody());
        String recordId = String.valueOf(ingestResponse.getBody().get("id"));
        assertNotNull(recordId);

        // 2) Retrieve it by id
        HttpEntity<Void> get = new HttpEntity<>(authedHeaders());
        ResponseEntity<Map> byId = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/records/" + recordId,
                HttpMethod.GET, get, Map.class);
        assertEquals(200, byId.getStatusCode().value());
        assertEquals("integration", byId.getBody().get("source"));

        // 3) Analytics stats must see the record via the in-memory store.
        //    Before Phase 1 Item 1's fix this would return zero counts.
        ResponseEntity<Map> stats = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/analytics/stats",
                HttpMethod.GET, get, Map.class);
        assertEquals(200, stats.getStatusCode().value());
        Map<?, ?> statsBody = stats.getBody();
        assertNotNull(statsBody);
        Map<?, ?> content = (Map<?, ?>) ((List<?>) statsBody.get("content")).get(0);
        assertNotNull(content, "Stats content must include the aggregation");
        Object countObj = content.get("totalRecords");
        assertNotNull(countObj, "Stats must include a totalRecords field; missing in-memory wiring would produce null/0 here");
        assertEquals(1.0, ((Number) countObj).doubleValue(),
                "In-memory store must contain the ingested record (Phase 1 Item 1 regression guard)");

        // 4) Analytics sorted must return the record too
        ResponseEntity<Map> sorted = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/analytics/sorted",
                HttpMethod.GET, get, Map.class);
        assertEquals(200, sorted.getStatusCode().value());
        Map<?, ?> sortedBody = sorted.getBody();
        List<?> sortedContent = (List<?>) sortedBody.get("content");
        assertEquals(1, sortedContent.size(),
                "Sorted analytics must return exactly one record (the one we just ingested)");
    }

    @Test
    @DisplayName("Persistence service can read the record back out by id (round-trip through real Postgres)")
    void persistenceServiceRoundTrip() {
        Map<String, Object> payload = Map.of("name", "rt");
        HttpEntity<Map<String, Object>> ingest = new HttpEntity<>(payload, authedHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/ingest/json",
                HttpMethod.POST, ingest, Map.class);
        assertEquals(201, response.getStatusCode().value());
        Long id = Long.valueOf(String.valueOf(response.getBody().get("id")));

        Optional<?> persisted = persistenceService.findById(id);
        assertTrue(persisted.isPresent(), "Record persisted via JPA must be retrievable by indexed id");
    }

    @Test
    @DisplayName("Actuator health endpoint responds 200 (regression guard for management: dedup)")
    void actuatorHealthIsUp() {
        HttpEntity<Void> empty = new HttpEntity<>(new HttpHeaders());
        ResponseEntity<Map> health = restTemplate.exchange(
                "http://localhost:" + port + "/actuator/health",
                HttpMethod.GET, empty, Map.class);
        assertEquals(200, health.getStatusCode().value());
        assertNotNull(health.getBody());
        assertEquals("UP", health.getBody().get("status"));
    }

    @Test
    @DisplayName("Actuator info endpoint is exposed (regression guard for management: dedup)")
    void actuatorInfoIsExposed() {
        HttpEntity<Void> empty = new HttpEntity<>(new HttpHeaders());
        ResponseEntity<String> info = restTemplate.exchange(
                "http://localhost:" + port + "/actuator/info",
                HttpMethod.GET, empty, String.class);
        // /actuator/info is a permitAll endpoint that returns 200 with body or empty payload
        assertEquals(200, info.getStatusCode().value(),
                "actuator/info must be exposed (Phase 2 #4 dedup must not have stripped it)");
    }
}
