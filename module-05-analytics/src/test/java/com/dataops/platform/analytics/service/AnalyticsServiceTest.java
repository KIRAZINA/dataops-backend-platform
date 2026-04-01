package com.dataops.platform.analytics.service;

import com.dataops.platform.common.model.DataRecord;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AnalyticsService.
 * Tests cover statistics generation, sorting, and metrics recording.
 */
@DisplayName("AnalyticsService Tests")
class AnalyticsServiceTest {

    private AnalyticsService analyticsService;
    private AggregationEngine aggregationEngine;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        aggregationEngine = new AggregationEngine();
        meterRegistry = new SimpleMeterRegistry();
        analyticsService = new AnalyticsService(aggregationEngine, meterRegistry);
    }

    @Test
    @DisplayName("Should calculate statistics for records")
    void testGetStats() {
        List<DataRecord> records = createTestRecords(5);

        Map<String, Object> stats = analyticsService.getStats(records, "test-source");

        assertNotNull(stats);
        assertEquals(5, stats.get("count"));
        assertNotNull(stats.get("groupBySource"));
        assertNotNull(stats.get("averageByType"));
    }

    @Test
    @DisplayName("Should return stats with count zero for empty records")
    void testGetStatsEmptyRecords() {
        List<DataRecord> records = new ArrayList<>();

        Map<String, Object> stats = analyticsService.getStats(records, null);

        assertNotNull(stats);
        assertEquals(0, stats.get("count"));
    }

    @Test
    @DisplayName("Should record analytics metrics during stats calculation")
    void testGetStatsRecordsMetrics() {
        List<DataRecord> records = createTestRecords(10);

        analyticsService.getStats(records, "test-source");

        assertTrue(meterRegistry.find("analytics.records.processed").counter() != null);
    }

    @Test
    @DisplayName("Should handle records with numeric values for max calculation")
    void testGetStatsWithNumericPayload() {
        List<DataRecord> records = Arrays.asList(
                createRecordWithValue(1, 10.5),
                createRecordWithValue(2, 20.0),
                createRecordWithValue(3, 15.7)
        );

        Map<String, Object> stats = analyticsService.getStats(records, "numeric");

        assertNotNull(stats);
        assertEquals(3, stats.get("count"));
    }

    @Test
    @DisplayName("Should handle records with non-numeric values gracefully")
    void testGetStatsWithNonNumericPayload() {
        List<DataRecord> records = Arrays.asList(
                DataRecord.builder()
                        .key("1")
                        .source("api")
                        .type("TEXT")
                        .payload(Map.of("text", "hello"))
                        .timestamp(Instant.now())
                        .build(),
                DataRecord.builder()
                        .key("2")
                        .source("api")
                        .type("TEXT")
                        .payload(Map.of("text", "world"))
                        .timestamp(Instant.now())
                        .build()
        );

        Map<String, Object> stats = analyticsService.getStats(records, "text");

        assertNotNull(stats);
        assertEquals(2, stats.get("count"));
    }

    @Test
    @DisplayName("Should sort records using specified algorithm")
    void testGetSortedData() {
        List<DataRecord> records = List.of(
                createRecordWithValue(1, 30.0),
                createRecordWithValue(2, 10.0),
                createRecordWithValue(3, 20.0)
        );

        List<DataRecord> sorted = analyticsService.getSortedData(records, "test", "quicksort");

        assertNotNull(sorted);
        assertEquals(3, sorted.size());
        assertEquals(10.0, sorted.get(0).getPayload().get("value"));
        assertEquals(20.0, sorted.get(1).getPayload().get("value"));
        assertEquals(30.0, sorted.get(2).getPayload().get("value"));
    }

    @Test
    @DisplayName("Should sort using mergesort algorithm")
    void testGetSortedDataMergesort() {
        List<DataRecord> records = List.of(
                createRecordWithValue(1, 15.0),
                createRecordWithValue(2, 5.0),
                createRecordWithValue(3, 10.0)
        );

        List<DataRecord> sorted = analyticsService.getSortedData(records, "test", "mergesort");

        assertNotNull(sorted);
        assertEquals(5.0, sorted.get(0).getPayload().get("value"));
        assertEquals(10.0, sorted.get(1).getPayload().get("value"));
        assertEquals(15.0, sorted.get(2).getPayload().get("value"));
    }

    @Test
    @DisplayName("Should sort using heapsort algorithm")
    void testGetSortedDataHeapsort() {
        List<DataRecord> records = List.of(
                createRecordWithValue(1, 25.0),
                createRecordWithValue(2, 35.0),
                createRecordWithValue(3, 15.0)
        );

        List<DataRecord> sorted = analyticsService.getSortedData(records, "test", "heapsort");

        assertNotNull(sorted);
        assertEquals(15.0, sorted.get(0).getPayload().get("value"));
        assertEquals(25.0, sorted.get(1).getPayload().get("value"));
        assertEquals(35.0, sorted.get(2).getPayload().get("value"));
    }

    @Test
    @DisplayName("Should handle empty records in sort")
    void testGetSortedDataEmpty() {
        List<DataRecord> records = new ArrayList<>();

        List<DataRecord> sorted = analyticsService.getSortedData(records, "test", "quicksort");

        assertNotNull(sorted);
        assertTrue(sorted.isEmpty());
    }

    @Test
    @DisplayName("Should record sort timing metrics")
    void testGetSortedDataRecordsMetrics() {
        List<DataRecord> records = createTestRecords(5);

        analyticsService.getSortedData(records, "test-source", "quicksort");

        assertTrue(meterRegistry.find("analytics.sort.time").timer() != null);
    }

    @Test
    @DisplayName("Should handle source=null in stats")
    void testGetStatsNullSource() {
        List<DataRecord> records = createTestRecords(3);

        Map<String, Object> stats = analyticsService.getStats(records, null);

        assertNotNull(stats);
        assertEquals(3, stats.get("count"));
    }

    private List<DataRecord> createTestRecords(int count) {
        List<DataRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(createRecordWithValue(i, i * 10.0));
        }
        return records;
    }

    private DataRecord createRecordWithValue(int id, double value) {
        return DataRecord.builder()
                .key(String.valueOf(id))
                .source("test-source")
                .type("NUMERIC")
                .payload(Map.of("value", value, "id", id))
                .timestamp(Instant.now())
                .build();
    }
}
