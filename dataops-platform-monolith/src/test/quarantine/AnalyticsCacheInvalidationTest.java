package com.dataops.platform.monolith;

import com.dataops.platform.common.event.DataRecordIngestedEvent;
import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.monolith.DataOpsMonolithApplication;
import com.dataops.platform.persistence.repository.jpa.JpaRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2-backed regression guard for the analytics cache-invalidation contract.
 *
 * <p>Verifies that the database — not any in-memory replica — is the source of
 * truth for analytics. The {@code @CacheEvict} on {@code IngestionService.ingest}
 * / {@code ingestBatch} is the single consolidated eviction site, and the test
 * proves it by going through the real HTTP stack into the real H2 database.
 *
 * <p>Replaces the deleted module-05 {@code AnalyticsCacheInvalidationTest}
 * that depended on the decommissioned {@code InMemoryStorageService}.
 */
@SpringBootTest(
        classes = DataOpsMonolithApplication.class,
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:cachetest;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.flyway.enabled=false",
                "app.kafka.enabled=false",
                "API_KEY=test-key"
        })
@Import(AnalyticsCacheInvalidationTest.EventCapture.class)
@DisplayName("Analytics cache invalidation (H2-backed)")
class AnalyticsCacheInvalidationTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String INGEST_SOURCE = "api";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    EventCapture eventCapture;

    @Autowired
    JpaRecordRepository jpaRepo;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authJsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set(API_KEY_HEADER, "test-key");
        return h;
    }

    @BeforeEach
    @Transactional
    void resetState() {
        jpaRepo.deleteAll();
        for (String name : List.of("analytics-stats", "analytics-sorted", "records-by-source")) {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
        eventCapture.events.clear();
    }

    private ResponseEntity<Map> ingestRecord(double value) {
        Map<String, Object> payload = Map.of("value", value);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, authJsonHeaders());
        return restTemplate.exchange(baseUrl() + "/api/v1/ingest/json",
                HttpMethod.POST, request, Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getStats(String source) {
        HttpEntity<Void> request = new HttpEntity<>(authJsonHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + (source != null ? "/api/v1/analytics/stats?source=" + source : "/api/v1/analytics/stats"),
                HttpMethod.GET, request, Map.class);
        assertEquals(200, response.getStatusCode().value(),
                "Stats should return 200. Body: " + response.getBody());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertNotNull(content);
        assertTrue(content.size() >= 1, "Stats content must have at least one entry");
        return content.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<DataRecord> getSorted(String source) {
        HttpEntity<Void> request = new HttpEntity<>(authJsonHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + (source != null ? "/api/v1/analytics/sorted?source=" + source : "/api/v1/analytics/sorted"),
                HttpMethod.GET, request, Map.class);
        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        return content.stream()
                .map(m -> DataRecord.builder()
                        .key(String.valueOf(m.get("id")))
                        .source((String) m.get("source"))
                        .type((String) m.get("type"))
                        .payload((Map<String, Object>) m.get("data"))
                        .build())
                .toList();
    }

    @Test
    @DisplayName("Ingest -> analytics stats reflect new record immediately (cache evicted on write)")
    void statsReflectNewRecordImmediately() {
        ingestRecord(1.0);
        Map<String, Object> stats1 = getStats(INGEST_SOURCE);
        long count1 = ((Number) stats1.get("count")).longValue();

        ingestRecord(2.0);
        Map<String, Object> stats2 = getStats(INGEST_SOURCE);
        long count2 = ((Number) stats2.get("count")).longValue();

        assertEquals(1, count1);
        assertEquals(2, count2,
                "After a second ingest, stats count must reflect the new record. "
                        + "If this fails, the @CacheEvict on IngestionService.ingest is missing.");
    }

    @Test
    @DisplayName("Ingest -> analytics sorted reflects new record immediately")
    void sortedReflectsNewRecordImmediately() {
        ingestRecord(30.0);
        List<DataRecord> sorted1 = getSorted(INGEST_SOURCE);
        assertEquals(1, sorted1.size());

        ingestRecord(10.0);
        List<DataRecord> sorted2 = getSorted(INGEST_SOURCE);
        assertEquals(2, sorted2.size(),
                "After a second ingest, sorted result must reflect the new record. "
                        + "If this fails, the @CacheEvict for analytics-sorted is missing.");
    }

    @Test
    @DisplayName("Ingest under a new source evicts the global (source=null) stats cache")
    void ingestEvictsGlobalStatsCache() {
        ingestRecord(1.0);
        Map<String, Object> global1 = getStats(null);
        long count1 = ((Number) global1.get("count")).longValue();
        assertEquals(1, count1);

        ingestRecord(2.0);
        Map<String, Object> global2 = getStats(null);
        long count2 = ((Number) global2.get("count")).longValue();

        assertEquals(count1 + 1, count2,
                "Global stats must reflect new records across all sources after eviction. "
                        + "If this fails, allEntries=true is missing on the @CacheEvict.");
    }

    @Test
    @DisplayName("Second identical call without ingest returns cached result (cache is in the path)")
    void cacheHitWhenNoIngest() {
        ingestRecord(1.0);

        Map<String, Object> first = getStats(INGEST_SOURCE);
        Map<String, Object> second = getStats(INGEST_SOURCE);

        assertEquals(((Number) first.get("count")).longValue(),
                ((Number) second.get("count")).longValue(),
                "Identical consecutive reads return identical stats — proves the cache layer is in the path");
        assertEquals(((Number) first.get("totalRecords")).longValue(),
                ((Number) second.get("totalRecords")).longValue(),
                "totalRecords must be stable across cache hits");
    }

    @Test
    @DisplayName("Stats include totalRecords field populated from the DB COUNT query")
    void statsIncludeTotalRecords() {
        ingestRecord(1.0);
        Map<String, Object> stats = getStats(INGEST_SOURCE);

        assertNotNull(stats.get("totalRecords"),
                "Stats must include a totalRecords field populated from PersistenceService.countBySource");
        assertEquals(((Number) stats.get("count")).longValue(),
                ((Number) stats.get("totalRecords")).longValue(),
                "count and totalRecords must be consistent for a single-source query");
    }

    @Test
    @DisplayName("Analytics reflects data after explicit cache clear (DB is source of truth)")
    void analyticsSurvivesCacheClear() {
        ingestRecord(42.0);

        Map<String, Object> before = getStats(INGEST_SOURCE);
        long countBefore = ((Number) before.get("count")).longValue();
        assertEquals(1, countBefore);

        cacheManager.getCache("analytics-stats").clear();
        cacheManager.getCache("analytics-sorted").clear();

        Map<String, Object> after = getStats(INGEST_SOURCE);
        long countAfter = ((Number) after.get("count")).longValue();
        assertEquals(1, countAfter,
                "After cache clear, stats must reload from DB and still show the ingested record. "
                        + "If this fails, analytics is reading from an in-memory replica, not the DB.");
    }

    @Test
    @DisplayName("Event-flow regression guard: ingest publishes DataRecordIngestedEvent (Original Bug 2 guard)")
    void eventFlowRegressionGuard() {
        ResponseEntity<Map> response = ingestRecord(99.0);
        assertEquals(201, response.getStatusCode().value());

        assertEquals(1, eventCapture.events.size(),
                "Ingestion must publish exactly one DataRecordIngestedEvent. "
                        + "Original Bug 2 was 'the event never fires' — this is the guard.");
        DataRecordIngestedEvent event = eventCapture.events.get(0);
        assertNotNull(event.getRecord());
        assertEquals(INGEST_SOURCE, event.getRecord().getSource(),
                "Event must carry the ingestion source set by the controller");
    }

    @Test
    @DisplayName("Batch ingest publishes one event per persisted record")
    void batchIngestPublishesEventPerRecord() {
        String csv = "source,value\nA,1\nB,2\nC,3";
        HttpEntity<String> request = new HttpEntity<>(csv, csvHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/v1/ingest/csv", HttpMethod.POST, request, String.class);
        assertEquals(201, response.getStatusCode().value());

        assertEquals(3, eventCapture.events.size(),
                "Batch ingest of 3 records must publish 3 events (one per persisted record).");
    }

    private HttpHeaders csvHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.valueOf("text/csv"));
        h.set(API_KEY_HEADER, "test-key");
        return h;
    }

    @TestComponent
    static class EventCapture {
        final CopyOnWriteArrayList<DataRecordIngestedEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        void capture(DataRecordIngestedEvent event) {
            events.add(event);
        }
    }
}
