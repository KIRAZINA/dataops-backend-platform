package com.dataops.platform.monolith;

import com.dataops.platform.monolith.DataOpsMonolithApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T2: Local (no-Docker) regression guard for malformed-input handling.
 *
 * <p>The Docker-gated {@code MonolithIntegrationTest} covers the same scenarios
 * against real Postgres; this class covers them against an in-memory H2 database
 * so feedback stays local and fast. Every malformed input must produce a 400
 * with the uniform {@code ErrorResponse} envelope — never a 500.
 */
@SpringBootTest(
        classes = DataOpsMonolithApplication.class,
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:malformedtest;DB_CLOSE_DELAY=-1",
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
@DisplayName("Malformed input guards (H2, local)")
class MalformedInputH2Test {

    private static final String API_KEY_HEADER = "X-API-Key";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set(API_KEY_HEADER, "test-key");
        return h;
    }

    private HttpHeaders csvHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.valueOf("text/csv"));
        h.set(API_KEY_HEADER, "test-key");
        return h;
    }

    private HttpHeaders xmlHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_XML);
        h.set(API_KEY_HEADER, "test-key");
        return h;
    }

    private void assertErrorEnvelope(Map<?, ?> body) {
        assertNotNull(body, "Error response body must not be null");
        assertEquals(400, body.get("status"));
        assertNotNull(body.get("error"));
        assertNotNull(body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("Malformed JSON → 400 + uniform envelope")
    void malformedJsonReturns400Envelope() {
        String malformedJson = "{\"id\": 42, \"name\":}";
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/ingest/json",
                HttpMethod.POST,
                new HttpEntity<>(malformedJson, jsonHeaders()),
                Map.class);
        assertEquals(400, response.getStatusCode().value());
        assertErrorEnvelope(response.getBody());
    }

    @Test
    @DisplayName("Malformed CSV (ragged row) → 400 + uniform envelope with errors array")
    void malformedCsvReturns400EnvelopeWithErrors() {
        String csv = "id,name\n1,alpha\n2";
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/ingest/csv",
                HttpMethod.POST,
                new HttpEntity<>(csv, csvHeaders()),
                Map.class);
        assertEquals(400, response.getStatusCode().value());
        Map<?, ?> body = response.getBody();
        assertErrorEnvelope(body);
        assertTrue(body.containsKey("errors"),
                "Validation failures must include an errors array. Was: " + body);
    }

    @Test
    @DisplayName("Malformed XML → 400 + uniform envelope")
    void malformedXmlReturns400Envelope() {
        String malformedXml = "<record><id>42</id><name>unclosed";
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/ingest/xml",
                HttpMethod.POST,
                new HttpEntity<>(malformedXml, xmlHeaders()),
                Map.class);
        assertEquals(400, response.getStatusCode().value());
        assertErrorEnvelope(response.getBody());
    }

    @Test
    @DisplayName("Valid request to malformed-input test class still succeeds (sanity)")
    void validRequestSucceeds() {
        String validJson = "{\"value\": 42}";
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/ingest/json",
                HttpMethod.POST,
                new HttpEntity<>(validJson, jsonHeaders()),
                Map.class);
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("id"));
    }
}
