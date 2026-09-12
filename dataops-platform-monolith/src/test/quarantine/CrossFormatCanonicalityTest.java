package com.dataops.platform.monolith;

import com.dataops.platform.monolith.DataOpsMonolithApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keystone integration test for Phase 2 cross-format canonicality.
 *
 * <p>Verifies the acceptance property: <em>the same logical record ingested via
 * JSON, CSV, and XML must produce deeply-equal persisted payloads.</em>
 *
 * <p>Uses an in-memory H2 database (no Docker required) so the test runs in any
 * environment. The logical dataset deliberately spans every coercion case:
 * Integer (JSON-native) vs String (CSV/XML), overflow-integer-stays-String,
 * Double vs String, Boolean vs String, and scientific-notation parsing.
 */
@SpringBootTest(
        classes = DataOpsMonolithApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:canonicalitytest;DB_CLOSE_DELAY=-1",
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
@DisplayName("Cross-format canonicality")
class CrossFormatCanonicalityTest {

    private static final String API_KEY_HEADER = "X-API-Key";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/ingest";
    }

    private HttpHeaders apiHeaders(MediaType mediaType) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(mediaType);
        h.set(API_KEY_HEADER, "test-key");
        return h;
    }

    /**
     * POST a body to an ingest endpoint and extract the {@code data} (payload)
     * field from the response as a {@link JsonNode}. CSV returns an array,
     * JSON/XML return a single object — handled transparently.
     */
    private JsonNode postAndExtractData(String url, String body, MediaType contentType) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, apiHeaders(contentType)), String.class);

        assertEquals(201, response.getStatusCode().value(),
                "Ingestion should succeed (201). Response body: " + response.getBody());

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode dataNode;
        if (root.isArray()) {
            dataNode = root.get(0).get("data");
        } else {
            dataNode = root.get("data");
        }
        assertNotNull(dataNode, "Response must contain a 'data' (payload) field");
        return dataNode;
    }

    @Test
    @DisplayName("Same logical record via JSON, CSV, and XML produces deeply-equal persisted payloads")
    void crossFormatCanonicality() throws Exception {
        // Covers: Integer vs String convergence, overflow-integer-stays-String,
        // Double vs String, Boolean vs String, scientific-notation double.
        String jsonPayload =
                "{\"id\": 42, \"count\": -7, \"price\": 3.14, \"active\": true, "
                + "\"name\": \"test\", \"overflow\": \"99999999999999999999999\", \"ratio\": \"1e10\"}";

        String csvContent = "id,count,price,active,name,overflow,ratio\n"
                + "42,-7,3.14,true,test,99999999999999999999999,1e10";

        String xmlContent =
                "<record><id>42</id><count>-7</count><price>3.14</price>"
                + "<active>true</active><name>test</name>"
                + "<overflow>99999999999999999999999</overflow><ratio>1e10</ratio></record>";

        JsonNode jsonNode = postAndExtractData(baseUrl() + "/json", jsonPayload, MediaType.APPLICATION_JSON);
        JsonNode csvNode = postAndExtractData(baseUrl() + "/csv", csvContent, MediaType.valueOf("text/csv"));
        JsonNode xmlNode = postAndExtractData(baseUrl() + "/xml", xmlContent, MediaType.APPLICATION_XML);

        // Keystone assertion: deeply-equal canonical payloads
        assertEquals(jsonNode, csvNode,
                "JSON and CSV payloads must be deeply equal after normalization");
        assertEquals(jsonNode, xmlNode,
                "JSON and XML payloads must be deeply equal after normalization");

        // Spot-check canonical types to make the assertion self-documenting:
        // id = Long (not String), price = Double (not String), active = Boolean (not String),
        // overflow stays String (lossless), ratio parses to Double.
        assertEquals(42, jsonNode.get("id").asLong());
        assertEquals(-7, jsonNode.get("count").asLong());
        assertEquals(3.14, jsonNode.get("price").asDouble(), 1e-9);
        assertTrue(jsonNode.get("active").asBoolean());
        assertEquals("test", jsonNode.get("name").asText());
        assertEquals("99999999999999999999999", jsonNode.get("overflow").asText(),
                "overflow integer must stay String (lossless, not promoted to Double)");
        assertEquals(1e10, jsonNode.get("ratio").asDouble(), 1e-9);
    }
}
