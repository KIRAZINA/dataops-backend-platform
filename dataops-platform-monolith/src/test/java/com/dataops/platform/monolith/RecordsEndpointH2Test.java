package com.dataops.platform.monolith;

import com.dataops.platform.monolith.DataOpsMonolithApplication;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = DataOpsMonolithApplication.class,
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:records-test;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.format_sql=false",
                "spring.flyway.enabled=false",
                "app.kafka.enabled=false",
                "API_KEY=test-key"
        })
@DisplayName("Records endpoint H2 integration tests")
class RecordsEndpointH2Test {

    private static final String API_KEY_HEADER = "X-API-Key";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set(API_KEY_HEADER, "test-key");
        return h;
    }

    /**
     * Insert one row directly via SQL so we can control {@code ingested_at}
     * exactly. The controller normalises {@code from}/{@code to} to UTC and
     * compares against this column, so for boundary tests we need known
     * timestamps rather than the {@code LocalDateTime.now()} that the ingest
     * endpoint would write.
     *
     * <p>H2 stores TIMESTAMP values without timezone information; JPA reads them
     * into {@code LocalDateTime} as-is. We therefore pass a {@code LocalDateTime}
     * already in UTC (the same shape the Specification will compare against)
     * rather than {@code Timestamp.from(Instant)}, which would shift to the JVM
     * default timezone and silently desynchronise the comparison.
     */
    private void insertRecord(long id, String source, String type, String payloadJson, Instant ingestedAt) {
        java.time.LocalDateTime ingestedLocal =
                java.time.LocalDateTime.ofInstant(ingestedAt, java.time.ZoneOffset.UTC);
        jdbcTemplate.update(
                "INSERT INTO persisted_record (id, source, type, ingested_at, payload, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id,
                source,
                type,
                ingestedLocal,
                payloadJson,
                ingestedLocal);
    }

    @BeforeEach
    void seedRecords() {
        jdbcTemplate.update("DELETE FROM persisted_record");
        Instant base = Instant.parse("2026-09-01T12:00:00Z");
        for (int i = 0; i < 5; i++) {
            insertRecord(
                    100L + i,
                    "api",
                    "JSON",
                    "{\"index\":" + i + ",\"tag\":\"alpha\"}",
                    base.plusSeconds(i));
        }
    }

    // ---------- pagination ----------

    @Test
    @DisplayName("GET /api/v1/records returns paginated list with default page and size")
    void getAllRecordsDefaultPagination() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        assertEquals(5, body.get("total_elements"));
        List<?> content = (List<?>) body.get("content");
        assertEquals(5, content.size());
    }

    @Test
    @DisplayName("GET /api/v1/records?page=0&pageSize=2 returns only 2 records")
    void getAllRecordsCustomPageSize() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?page=0&pageSize=2",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        List<?> content = (List<?>) body.get("content");
        assertEquals(2, content.size());
        assertEquals(5, body.get("total_elements"));
    }

    @Test
    @DisplayName("GET /api/v1/records with no records (out-of-range page) returns 200 with empty content")
    void getAllRecordsOutOfRangePage() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?page=100&pageSize=10",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        List<?> content = (List<?>) body.get("content");
        assertTrue(content.isEmpty());
        assertEquals(5, body.get("total_elements"));
    }

    // ---------- sortBy / sortDir contract (B2) ----------

    @Test
    @DisplayName("GET /api/v1/records?sortBy=ingestedAt&sortDir=asc returns sorted records")
    void getAllRecordsWithSort() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?page=0&pageSize=20&sortBy=ingestedAt&sortDir=asc",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        List<?> content = (List<?>) body.get("content");
        assertEquals(5, content.size());
    }

    @Test
    @DisplayName("GET /api/v1/records?sortBy=id returns records sorted by id (allowed)")
    void getAllRecordsSortById() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?sortBy=id&sortDir=asc&pageSize=10",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        List<?> content = (List<?>) response.getBody().get("content");
        assertEquals(5, content.size());
    }

    @Test
    @DisplayName("GET /api/v1/records?sortBy=bogus returns 400 (contract guard)")
    void getAllRecordsUnknownSortBy() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?sortBy=bogus",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(400, response.getStatusCode().value(),
                "Unknown sortBy must be rejected at the API boundary");
        assertErrorEnvelope(response.getBody());
    }

    @Test
    @DisplayName("GET /api/v1/records?sortBy=source returns 400 (out-of-contract sort field)")
    void getAllRecordsSortBySourceRejected() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?sortBy=source",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(400, response.getStatusCode().value(),
                "Sorting by source/type is out of contract - filtering already covers that");
        assertErrorEnvelope(response.getBody());
    }

    @Test
    @DisplayName("GET /api/v1/records with invalid sortDir returns 400")
    void getAllRecordsInvalidSortDir() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?sortBy=ingestedAt&sortDir=sideways",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(400, response.getStatusCode().value());
        assertErrorEnvelope(response.getBody());
    }

    // ---------- @Max enforcement on pageSize ----------

    @Test
    @DisplayName("GET /api/v1/records?pageSize=501 returns 400 (@Max enforcement)")
    void getAllRecordsPageSizeTooLarge() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?page=0&pageSize=501",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(400, response.getStatusCode().value(),
                "Oversized page size must be rejected at the Spring MVC layer");
        assertErrorEnvelope(response.getBody());
    }

    @Test
    @DisplayName("GET /api/v1/records?pageSize=0 returns 400 (@Min enforcement)")
    void getAllRecordsPageSizeZero() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?page=0&pageSize=0",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /api/v1/records?page=-1 returns 400 (@Min enforcement)")
    void getAllRecordsNegativePage() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?page=-1",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(400, response.getStatusCode().value());
    }

    // ---------- date filters (B3: Instant / UTC) ----------

    @Test
    @DisplayName("GET /api/v1/records?from=not-a-date returns 400 (Spring rejects bad Instant)")
    void getAllRecordsInvalidFromDate() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?from=not-a-date",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(400, response.getStatusCode().value());
        assertErrorEnvelope(response.getBody());
    }

    @Test
    @DisplayName("GET /api/v1/records accepts from with non-UTC offset and normalises to UTC")
    void getAllRecordsAcceptsNonUtcOffset() {
        // base records are 2026-09-01T12:00:00Z..+4s.
        // Querying with +02:00 for the same instant in UTC must return the same hits.
        // Use URI builder so the '+' is properly percent-encoded as %2B before
        // hitting the wire; concatenating into a String would lose the encoding
        // because '+' decodes to a space at the server.
        java.net.URI url = java.net.URI.create(baseUrl() + "/api/v1/records"
                + "?from=" + java.net.URLEncoder.encode("2026-09-01T14:00:00+02:00",
                        java.nio.charset.StandardCharsets.UTF_8)
                + "&to=" + java.net.URLEncoder.encode("2026-09-01T14:00:10+02:00",
                        java.nio.charset.StandardCharsets.UTF_8));
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value(),
                "+02:00 offset must be accepted; conversion happens at the persistence boundary");
        Map<?, ?> body = response.getBody();
        List<?> content = (List<?>) body.get("content");
        assertEquals(5, content.size(),
                "All 5 base records fall within 12:00:00Z..12:00:04Z = 14:00:00+02:00..14:00:04+02:00");
        assertEquals(5, body.get("total_elements"));
    }

    @Test
    @DisplayName("Inclusive boundary: record exactly at 'from' is included")
    void getAllRecordsInclusiveFromBoundary() {
        Instant base = Instant.parse("2026-09-01T12:00:00Z");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?from=" + base.toString(),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        List<?> content = (List<?>) response.getBody().get("content");
        assertEquals(5, content.size(),
                "All 5 records have ingested_at >= 2026-09-01T12:00:00Z (inclusive)");
    }

    @Test
    @DisplayName("Inclusive boundary: record exactly at 'to' is included")
    void getAllRecordsInclusiveToBoundary() {
        Instant base = Instant.parse("2026-09-01T12:00:00Z");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?to=" + base.toString(),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        List<?> content = (List<?>) response.getBody().get("content");
        assertEquals(1, content.size(),
                "Only the first record (id=100, ingested_at = base) is <= base (inclusive)");
    }

    @Test
    @DisplayName("GET /api/v1/records?from=...&to=... returns records in range (UTC)")
    void getAllRecordsWithDateRangeUtc() {
        Instant base = Instant.parse("2026-09-01T12:00:00Z");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?from=" + base.toString()
                        + "&to=" + base.plusSeconds(2).toString(),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = response.getBody();
        List<?> content = (List<?>) body.get("content");
        assertEquals(3, content.size(),
                "Range covers records at +0s, +1s, +2s (inclusive)");
        assertEquals(3, body.get("total_elements"));
    }

    @Test
    @DisplayName("GET /api/v1/records?from>to returns 400")
    void getAllRecordsFromAfterToRejected() {
        Instant base = Instant.parse("2026-09-01T12:00:00Z");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?from=" + base.plusSeconds(10).toString()
                        + "&to=" + base.toString(),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(400, response.getStatusCode().value(),
                "from > to must be rejected - silent range inversion corrupts result sets");
        assertErrorEnvelope(response.getBody());
    }

    // ---------- source / type filters + combined ----------

    @Test
    @DisplayName("GET /api/v1/records?source=api returns only matching records")
    void getAllRecordsWithSourceFilter() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?source=api",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        List<?> content = (List<?>) body.get("content");
        assertEquals(5, content.size());
        for (Object item : content) {
            Map<?, ?> record = (Map<?, ?>) item;
            assertEquals("api", record.get("source"));
        }
    }

    @Test
    @DisplayName("GET /api/v1/records?type=JSON returns only matching records")
    void getAllRecordsWithTypeFilter() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?type=JSON",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        List<?> content = (List<?>) response.getBody().get("content");
        assertEquals(5, content.size());
    }

    @Test
    @DisplayName("GET /api/v1/records?type=XML returns 200 with empty content (no 404 for empty filtered set)")
    void getAllRecordsEmptyFilteredResult() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?type=XML",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value(),
                "Empty filtered set must be 200 with empty content, never 404");
        Map<?, ?> body = response.getBody();
        List<?> content = (List<?>) body.get("content");
        assertTrue(content.isEmpty());
        assertEquals(0, body.get("total_elements"),
                "filtered totalElements must reflect the filtered count, not the whole table");
    }

    @Test
    @DisplayName("GET /api/v1/records?source=api&type=JSON returns filtered totalElements = 5 (not total table)")
    void getAllRecordsFilteredTotalElementsReflectsFilter() {
        // Seed a second source so we can prove totalElements is the FILTERED count
        insertRecord(200L, "other", "JSON", "{\"other\":true}",
                Instant.parse("2026-09-02T00:00:00Z"));

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?source=api",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = response.getBody();
        assertEquals(5, body.get("total_elements"),
                "totalElements must reflect the filtered count (5 api records), not the whole table (6)");
        List<?> content = (List<?>) body.get("content");
        assertEquals(5, content.size());
    }

    @Test
    @DisplayName("GET /api/v1/records with all filters (source + type + dates + sort) returns filtered results")
    void getAllRecordsWithAllFilters() {
        Instant base = Instant.parse("2026-09-01T12:00:00Z");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?source=api&type=JSON"
                        + "&from=" + base.minusSeconds(1).toString()
                        + "&to=" + base.plusSeconds(10).toString()
                        + "&sortBy=ingestedAt&sortDir=asc",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = response.getBody();
        List<?> content = (List<?>) body.get("content");
        assertEquals(5, content.size());
        for (Object item : content) {
            Map<?, ?> record = (Map<?, ?>) item;
            assertEquals("api", record.get("source"));
            assertEquals("JSON", record.get("type"));
        }
    }

    // ---------- omitted parameters ----------

    @Test
    @DisplayName("GET /api/v1/records with no params returns all records (no predicates when filters absent)")
    void getAllRecordsNoFiltersNoPredicates() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = response.getBody();
        assertEquals(5, body.get("total_elements"));
        assertEquals(5, ((List<?>) body.get("content")).size());
    }

    // ---------- over-length source / type (B5) ----------

    @Test
    @DisplayName("GET /api/v1/records?source=<256 chars> returns 400")
    void getAllRecordsOversizedSource() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256; i++) sb.append('a');
        String longSource = sb.toString();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?source=" + longSource,
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(400, response.getStatusCode().value(),
                "source longer than 255 chars must be rejected at the API boundary");
    }

    @Test
    @DisplayName("GET /api/v1/records?type=<51 chars> returns 400")
    void getAllRecordsOversizedType() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 51; i++) sb.append('t');
        String longType = sb.toString();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?type=" + longType,
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(400, response.getStatusCode().value(),
                "type longer than 50 chars must be rejected at the API boundary");
    }

    // ---------- tiebreaker determinism (B4) ----------

    @Test
    @DisplayName("Two records sharing ingestedAt page deterministically thanks to id tiebreaker")
    void getAllRecordsTiebreakerDeterministic() {
        jdbcTemplate.update("DELETE FROM persisted_record");
        Instant same = Instant.parse("2026-09-01T12:00:00Z");
        // 5 records with the EXACT same ingested_at, ordered by id asc
        for (long i = 1; i <= 5; i++) {
            insertRecord(i, "api", "JSON", "{\"id\":" + i + "}", same);
        }

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?sortBy=ingestedAt&sortDir=asc&pageSize=2",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        List<?> content = (List<?>) response.getBody().get("content");
        assertEquals(2, content.size());
        assertEquals("1", String.valueOf(((Map<?, ?>) content.get(0)).get("id")));
        assertEquals("2", String.valueOf(((Map<?, ?>) content.get(1)).get("id")));

        // Page 2 must continue from id=3 - without the tiebreaker, this would be non-deterministic
        ResponseEntity<Map> response2 = restTemplate.exchange(
                baseUrl() + "/api/v1/records?sortBy=ingestedAt&sortDir=asc&pageSize=2&page=1",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);
        List<?> content2 = (List<?>) response2.getBody().get("content");
        assertEquals(2, content2.size());
        assertEquals("3", String.valueOf(((Map<?, ?>) content2.get(0)).get("id")));
        assertEquals("4", String.valueOf(((Map<?, ?>) content2.get(1)).get("id")));
    }

    @Test
    @DisplayName("Tiebreaker respects desc direction")
    void getAllRecordsTiebreakerDesc() {
        jdbcTemplate.update("DELETE FROM persisted_record");
        Instant same = Instant.parse("2026-09-01T12:00:00Z");
        for (long i = 1; i <= 5; i++) {
            insertRecord(i, "api", "JSON", "{\"id\":" + i + "}", same);
        }

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/records?sortBy=ingestedAt&sortDir=desc&pageSize=2",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                Map.class);

        assertEquals(200, response.getStatusCode().value());
        List<?> content = (List<?>) response.getBody().get("content");
        assertEquals("5", String.valueOf(((Map<?, ?>) content.get(0)).get("id")));
        assertEquals("4", String.valueOf(((Map<?, ?>) content.get(1)).get("id")));
    }

    private void assertErrorEnvelope(Map<?, ?> body) {
        assertNotNull(body, "Error response body must not be null");
        assertEquals(400, body.get("status"));
        assertNotNull(body.get("error"));
        assertNotNull(body.get("message"));
        assertNotNull(body.get("timestamp"));
    }
}
