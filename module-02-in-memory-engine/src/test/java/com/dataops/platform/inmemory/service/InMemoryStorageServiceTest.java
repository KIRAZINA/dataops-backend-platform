package com.dataops.platform.inmemory.service;

import com.dataops.platform.common.event.DataRecordIngestedEvent;
import com.dataops.platform.common.model.DataRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InMemoryStorageService.
 * Tests cover core functionality: saving, retrieval, pagination, and event publishing.
 */
@DisplayName("InMemoryStorageService Tests")
@ExtendWith(MockitoExtension.class)
class InMemoryStorageServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private InMemoryStorageService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryStorageService(eventPublisher);
    }

    private DataRecord build(String source, String type, Map<String, Object> payload, String key) {
        return DataRecord.builder()
                .key(key)
                .source(source)
                .type(type)
                .payload(payload)
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should save a single record successfully")
    void testSaveRecord() {
        Map<String, Object> payload = Map.of("name", "test-data", "value", 42);
        DataRecord record = build("api", "JSON", payload, "1");

        DataRecord saved = service.saveRecord(record);

        assertNotNull(saved);
        assertNotNull(saved.id());
        assertEquals("api", saved.getSource());
        assertEquals("JSON", saved.getType());
        assertEquals(payload, saved.getPayload());
        assertEquals(1, service.getTotalRecordCount());
        verify(eventPublisher, times(1)).publishEvent(any(DataRecordIngestedEvent.class));
    }

    @Test
    @DisplayName("Should save batch of records")
    void testSaveBatch() {
        List<DataRecord> records = List.of(
                build("csv-upload", "CSV", Map.of("id", 1, "name", "record1"), "1"),
                build("csv-upload", "CSV", Map.of("id", 2, "name", "record2"), "2"),
                build("csv-upload", "CSV", Map.of("id", 3, "name", "record3"), "3"));

        List<DataRecord> saved = service.saveRecords(records);

        assertEquals(3, saved.size());
        assertEquals(3, service.getTotalRecordCount());
        assertEquals("csv-upload", saved.get(0).getSource());
        assertEquals("CSV", saved.get(0).getType());
        verify(eventPublisher, times(3)).publishEvent(any(DataRecordIngestedEvent.class));
    }

    @Test
    @DisplayName("Should find record by ID using O(1) index")
    void testFindById() {
        Map<String, Object> payload = Map.of("data", "test");
        DataRecord saved = service.saveRecord(build("api", "JSON", payload, "1"));

        DataRecord found = service.findById(saved.id());

        assertNotNull(found);
        assertEquals(saved.id(), found.id());
        assertEquals(payload, found.getPayload());
    }

    @Test
    @DisplayName("Should return null for non-existent ID")
    void testFindByIdNotFound() {
        DataRecord found = service.findById("non-existent-id");
        assertNull(found);
    }

    @Test
    @DisplayName("Should find records by source")
    void testFindBySource() {
        service.saveRecord(build("api", "JSON", Map.of("data", "record1"), "1"));
        service.saveRecord(build("api", "JSON", Map.of("data", "record2"), "2"));
        service.saveRecord(build("file-upload", "CSV", Map.of("data", "record3"), "3"));

        List<DataRecord> apiRecords = service.findBySource("api");

        assertEquals(2, apiRecords.size());
        assertTrue(apiRecords.stream().allMatch(r -> "api".equals(r.getSource())));
    }

    @Test
    @DisplayName("Should find records by type")
    void testFindByType() {
        service.saveRecord(build("api", "JSON", Map.of("data", "record1"), "1"));
        service.saveRecord(build("api", "XML", Map.of("data", "record2"), "2"));
        service.saveRecord(build("file-upload", "JSON", Map.of("data", "record3"), "3"));

        List<DataRecord> jsonRecords = service.findByType("JSON");

        assertEquals(2, jsonRecords.size());
        assertTrue(jsonRecords.stream().allMatch(r -> "JSON".equals(r.getType())));
    }

    @Test
    @DisplayName("Should retrieve all records")
    void testFindAllRecords() {
        for (int i = 0; i < 5; i++) {
            service.saveRecord(build("api", "JSON", Map.of("i", i), String.valueOf(i)));
        }

        List<DataRecord> allRecords = service.findAllRecords();

        assertEquals(5, allRecords.size());
        assertEquals(5, service.getTotalRecordCount());
    }

    @Test
    @DisplayName("Should support pagination for all records")
    void testFindAllRecordsPaginated() {
        for (int i = 0; i < 50; i++) {
            service.saveRecord(build("api", "JSON", Map.of("index", i), String.valueOf(i)));
        }

        List<DataRecord> page0 = service.findAllRecordsPaginated(0, 10);
        List<DataRecord> page1 = service.findAllRecordsPaginated(1, 10);
        List<DataRecord> page5 = service.findAllRecordsPaginated(5, 10);

        assertEquals(10, page0.size());
        assertEquals(10, page1.size());
        assertTrue(page5.isEmpty());

        assertNotEquals(page0.get(0).id(), page1.get(0).id());
    }

    @Test
    @DisplayName("Should return empty list for out-of-bounds page")
    void testFindAllRecordsPaginatedOutOfBounds() {
        for (int i = 0; i < 5; i++) {
            service.saveRecord(build("api", "JSON", Map.of("index", i), String.valueOf(i)));
        }

        List<DataRecord> page10 = service.findAllRecordsPaginated(10, 10);

        assertTrue(page10.isEmpty());
    }

    @Test
    @DisplayName("Should support pagination by source")
    void testFindBySourcePaginated() {
        for (int i = 0; i < 25; i++) {
            service.saveRecord(build("api", "JSON", Map.of("index", i), "a-" + i));
            service.saveRecord(build("file-upload", "CSV", Map.of("index", i), "f-" + i));
        }

        List<DataRecord> apiPage0 = service.findBySourcePaginated("api", 0, 10);
        List<DataRecord> apiPage1 = service.findBySourcePaginated("api", 1, 10);

        assertEquals(10, apiPage0.size());
        assertEquals(10, apiPage1.size());
        assertEquals(5, service.findBySourcePaginated("api", 2, 10).size());
        assertTrue(apiPage0.stream().allMatch(r -> "api".equals(r.getSource())));
    }

    @Test
    @DisplayName("Should get correct total counts")
    void testGetTotalCounts() {
        for (int i = 0; i < 15; i++) {
            service.saveRecord(build("api", "JSON", Map.of("i", i), "a-" + i));
            service.saveRecord(build("upload", "CSV", Map.of("i", i), "u-" + i));
        }

        assertEquals(30, service.getTotalRecordCount());
        assertEquals(15, service.getTotalRecordCountBySource("api"));
        assertEquals(15, service.getTotalRecordCountBySource("upload"));
    }

    @Test
    @DisplayName("Should make returned records list immutable")
    void testReturnedListImmutability() {
        service.saveRecord(build("api", "JSON", Map.of("data", 1), "1"));
        List<DataRecord> records = service.findAllRecords();

        assertThrows(UnsupportedOperationException.class, () -> records.add(
                DataRecord.builder().key("new").source("api").type("JSON").payload(Map.of()).build()
        ));
    }

    @Test
    @DisplayName("Should remove record by ID")
    void testRemoveById() {
        DataRecord saved = service.saveRecord(build("api", "JSON", Map.of("data", "test"), "1"));
        long countBefore = service.getTotalRecordCount();

        service.removeById(saved.id());

        long countAfter = service.getTotalRecordCount();
        assertEquals(countBefore - 1, countAfter);
        assertNull(service.findById(saved.id()));
    }

    @Test
    @DisplayName("Should clear all records")
    void testClear() {
        for (int i = 0; i < 5; i++) {
            service.saveRecord(build("api", "JSON", Map.of("i", i), String.valueOf(i)));
        }
        assertEquals(5, service.getTotalRecordCount());

        service.clear();

        assertEquals(0, service.getTotalRecordCount());
        assertTrue(service.findAllRecords().isEmpty());
    }
}
