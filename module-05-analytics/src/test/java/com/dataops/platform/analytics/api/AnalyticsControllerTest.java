package com.dataops.platform.analytics.api;

import com.dataops.platform.analytics.service.AggregationEngine;
import com.dataops.platform.analytics.service.AnalyticsService;
import com.dataops.platform.common.dto.PagedResponse;
import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.inmemory.service.InMemoryStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("AnalyticsController endpoint tests")
class AnalyticsControllerTest {

    private InMemoryStorageService storage;
    private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        storage = new InMemoryStorageService(mock(ApplicationEventPublisher.class));
        AnalyticsService analyticsService = new AnalyticsService(new AggregationEngine());
        controller = new AnalyticsController(analyticsService, storage);
    }

    private DataRecord rec(String key, String source, String type, double value) {
        return DataRecord.builder()
                .key(key)
                .source(source)
                .type(type)
                .payload(Map.of("value", value))
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("stats endpoint returns correct count for a known seeded dataset")
    void statsReturnsCorrectCount() {
        storage.saveRecord(rec("1", "src", "JSON", 10.0));
        storage.saveRecord(rec("2", "src", "JSON", 20.0));
        storage.saveRecord(rec("3", "src", "JSON", 30.0));

        ResponseEntity<?> response = controller.getStats(null, 0, 20);

        assertEquals(200, response.getStatusCode().value());
        Object body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        PagedResponse<Map<String, Object>> paged = (PagedResponse<Map<String, Object>>) body;
        assertEquals(1, paged.getContent().size());
        Map<String, Object> stats = paged.getContent().get(0);
        assertEquals(3, ((Number) stats.get("count")).intValue());
    }

    @Test
    @DisplayName("stats endpoint for empty store does not throw on zero-count aggregation")
    void statsEmptyStoreDoesNotThrow() {
        ResponseEntity<?> response = controller.getStats(null, 0, 20);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        PagedResponse<Map<String, Object>> paged = (PagedResponse<Map<String, Object>>) response.getBody();
        Map<String, Object> stats = paged.getContent().get(0);
        assertEquals(0, ((Number) stats.get("count")).intValue());
        // groupBySource and averageByType should be present, not null
        assertNotNull(stats.get("groupBySource"));
        assertNotNull(stats.get("averageByType"));
    }

    @Test
    @DisplayName("stats endpoint filters by source when source param is supplied")
    void statsFiltersBySource() {
        storage.saveRecord(rec("1", "src-a", "JSON", 1.0));
        storage.saveRecord(rec("2", "src-a", "JSON", 2.0));
        storage.saveRecord(rec("3", "src-b", "JSON", 3.0));

        ResponseEntity<?> response = controller.getStats("src-a", 0, 20);

        @SuppressWarnings("unchecked")
        PagedResponse<Map<String, Object>> paged = (PagedResponse<Map<String, Object>>) response.getBody();
        assertEquals(2, ((Number) paged.getContent().get(0).get("count")).intValue());
    }

    @Test
    @DisplayName("sorted endpoint uses quicksort by default and returns ascending order")
    void sortedAscendingByDefault() {
        storage.saveRecord(rec("1", "src", "JSON", 30.0));
        storage.saveRecord(rec("2", "src", "JSON", 10.0));
        storage.saveRecord(rec("3", "src", "JSON", 20.0));

        ResponseEntity<?> response = controller.getSortedData(null, "quicksort", 0, 20);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        PagedResponse<DataRecord> paged = (PagedResponse<DataRecord>) response.getBody();
        List<DataRecord> data = paged.getContent();
        assertEquals(3, data.size());
        assertEquals(10.0, data.get(0).getPayload().get("value"));
        assertEquals(20.0, data.get(1).getPayload().get("value"));
        assertEquals(30.0, data.get(2).getPayload().get("value"));
    }

    @Test
    @DisplayName("sorted endpoint honors mergesort selector")
    void sortedMergesort() {
        storage.saveRecord(rec("1", "src", "JSON", 5.0));
        storage.saveRecord(rec("2", "src", "JSON", 15.0));
        storage.saveRecord(rec("3", "src", "JSON", 10.0));

        ResponseEntity<?> response = controller.getSortedData(null, "mergesort", 0, 20);

        @SuppressWarnings("unchecked")
        PagedResponse<DataRecord> paged = (PagedResponse<DataRecord>) response.getBody();
        List<DataRecord> data = paged.getContent();
        assertEquals(5.0, data.get(0).getPayload().get("value"));
        assertEquals(10.0, data.get(1).getPayload().get("value"));
        assertEquals(15.0, data.get(2).getPayload().get("value"));
    }

    @Test
    @DisplayName("sorted endpoint honors heapsort selector")
    void sortedHeapsort() {
        storage.saveRecord(rec("1", "src", "JSON", 25.0));
        storage.saveRecord(rec("2", "src", "JSON", 35.0));
        storage.saveRecord(rec("3", "src", "JSON", 15.0));

        ResponseEntity<?> response = controller.getSortedData(null, "heapsort", 0, 20);

        @SuppressWarnings("unchecked")
        PagedResponse<DataRecord> paged = (PagedResponse<DataRecord>) response.getBody();
        List<DataRecord> data = paged.getContent();
        assertEquals(15.0, data.get(0).getPayload().get("value"));
        assertEquals(25.0, data.get(1).getPayload().get("value"));
        assertEquals(35.0, data.get(2).getPayload().get("value"));
    }

    @Test
    @DisplayName("sorted endpoint paginates the sorted result set")
    void sortedPaginates() {
        for (int i = 0; i < 25; i++) {
            storage.saveRecord(rec(String.valueOf(i), "src", "JSON", i));
        }

        ResponseEntity<?> response = controller.getSortedData(null, "quicksort", 0, 10);

        @SuppressWarnings("unchecked")
        PagedResponse<DataRecord> paged = (PagedResponse<DataRecord>) response.getBody();
        assertEquals(10, paged.getContent().size());
        assertEquals(25L, paged.getTotalElements());
        assertTrue(paged.getContent().get(0).getPayload().get("value") instanceof Number);
    }
}
