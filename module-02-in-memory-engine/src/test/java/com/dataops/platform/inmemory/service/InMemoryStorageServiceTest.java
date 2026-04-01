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

    @Test
    @DisplayName("Should save a single record successfully")
    void testSaveRecord() {
        // Arrange
        Map<String, Object> payload = Map.of("name", "test-data", "value", 42);

        // Act
        DataRecord record = service.save("api", "JSON", payload);

        // Assert
        assertNotNull(record);
        assertNotNull(record.id());
        assertEquals("api", record.getSource());
        assertEquals("JSON", record.getType());
        assertEquals(payload, record.getPayload());
        assertEquals(1, service.getTotalRecordCount());
        verify(eventPublisher, times(1)).publishEvent(any(DataRecordIngestedEvent.class));
    }

    @Test
    @DisplayName("Should save batch of records")
    void testSaveBatch() {
        // Arrange
        List<Map<String, Object>> payloads = Arrays.asList(
                Map.of("id", 1, "name", "record1"),
                Map.of("id", 2, "name", "record2"),
                Map.of("id", 3, "name", "record3")
        );

        // Act
        List<DataRecord> records = service.saveBatch("csv-upload", "CSV", payloads);

        // Assert
        assertEquals(3, records.size());
        assertEquals(3, service.getTotalRecordCount());
        assertEquals("csv-upload", records.get(0).getSource());
        assertEquals("CSV", records.get(0).getType());
        verify(eventPublisher, times(3)).publishEvent(any(DataRecordIngestedEvent.class));
    }

    @Test
    @DisplayName("Should find record by ID using O(1) index")
    void testFindById() {
        // Arrange
        Map<String, Object> payload = Map.of("data", "test");
        DataRecord saved = service.save("api", "JSON", payload);

        // Act
        DataRecord found = service.findById(saved.id());

        // Assert
        assertNotNull(found);
        assertEquals(saved.id(), found.id());
        assertEquals(payload, found.getPayload());
    }

    @Test
    @DisplayName("Should return null for non-existent ID")
    void testFindByIdNotFound() {
        // Act
        DataRecord found = service.findById("non-existent-id");

        // Assert
        assertNull(found);
    }

    @Test
    @DisplayName("Should find records by source")
    void testFindBySource() {
        // Arrange
        service.save("api", "JSON", Map.of("data", "record1"));
        service.save("api", "JSON", Map.of("data", "record2"));
        service.save("file-upload", "CSV", Map.of("data", "record3"));

        // Act
        List<DataRecord> apiRecords = service.findBySource("api");

        // Assert
        assertEquals(2, apiRecords.size());
        assertTrue(apiRecords.stream().allMatch(r -> "api".equals(r.getSource())));
    }

    @Test
    @DisplayName("Should find records by type")
    void testFindByType() {
        // Arrange
        service.save("api", "JSON", Map.of("data", "record1"));
        service.save("api", "XML", Map.of("data", "record2"));
        service.save("file-upload", "JSON", Map.of("data", "record3"));

        // Act
        List<DataRecord> jsonRecords = service.findByType("JSON");

        // Assert
        assertEquals(2, jsonRecords.size());
        assertTrue(jsonRecords.stream().allMatch(r -> "JSON".equals(r.getType())));
    }

    @Test
    @DisplayName("Should retrieve all records")
    void testFindAllRecords() {
        // Arrange
        for (int i = 0; i < 5; i++) {
            service.save("api", "JSON", Map.of("i", i));
        }

        // Act
        List<DataRecord> allRecords = service.findAllRecords();

        // Assert
        assertEquals(5, allRecords.size());
        assertEquals(5, service.getTotalRecordCount());
    }

    @Test
    @DisplayName("Should support pagination for all records")
    void testFindAllRecordsPaginated() {
        // Arrange
        for (int i = 0; i < 50; i++) {
            service.save("api", "JSON", Map.of("index", i));
        }

        // Act
        List<DataRecord> page0 = service.findAllRecordsPaginated(0, 10);
        List<DataRecord> page1 = service.findAllRecordsPaginated(1, 10);
        List<DataRecord> page5 = service.findAllRecordsPaginated(5, 10);

        // Assert
        assertEquals(10, page0.size());
        assertEquals(10, page1.size());
        assertTrue(page5.isEmpty());
        
        // Verify no overlap
        assertNotEquals(page0.get(0).id(), page1.get(0).id());
    }

    @Test
    @DisplayName("Should return empty list for out-of-bounds page")
    void testFindAllRecordsPaginatedOutOfBounds() {
        // Arrange
        for (int i = 0; i < 5; i++) {
            service.save("api", "JSON", Map.of("index", i));
        }

        // Act
        List<DataRecord> page10 = service.findAllRecordsPaginated(10, 10);

        // Assert
        assertTrue(page10.isEmpty());
    }

    @Test
    @DisplayName("Should support pagination by source")
    void testFindBySourcePaginated() {
        // Arrange
        for (int i = 0; i < 25; i++) {
            service.save("api", "JSON", Map.of("index", i));
            service.save("file-upload", "CSV", Map.of("index", i));
        }

        // Act
        List<DataRecord> apiPage0 = service.findBySourcePaginated("api", 0, 10);
        List<DataRecord> apiPage1 = service.findBySourcePaginated("api", 1, 10);

        // Assert
        assertEquals(10, apiPage0.size());
        assertEquals(10, apiPage1.size());
        assertEquals(5, service.findBySourcePaginated("api", 2, 10).size());
        assertTrue(apiPage0.stream().allMatch(r -> "api".equals(r.getSource())));
    }

    @Test
    @DisplayName("Should get correct total counts")
    void testGetTotalCounts() {
        // Arrange
        for (int i = 0; i < 15; i++) {
            service.save("api", "JSON", Map.of("i", i));
            service.save("upload", "CSV", Map.of("i", i));
        }

        // Act & Assert
        assertEquals(30, service.getTotalRecordCount());
        assertEquals(15, service.getTotalRecordCountBySource("api"));
        assertEquals(15, service.getTotalRecordCountBySource("upload"));
    }

    @Test
    @DisplayName("Should make payload immutable")
    void testPayloadImmutability() {
        // Arrange
        Map<String, Object> originalPayload = new HashMap<>(Map.of("key", "value"));
        DataRecord record = service.save("api", "JSON", originalPayload);

        // Act
        originalPayload.put("key", "modified");

        // Assert
        assertEquals("value", record.getPayload().get("key"));
    }

    @Test
    @DisplayName("Should make returned records list immutable")
    void testReturnedListImmutability() {
        // Arrange
        service.save("api", "JSON", Map.of("data", 1));
        List<DataRecord> records = service.findAllRecords();

        // Act & Assert
        assertThrows(UnsupportedOperationException.class, () -> records.add(
                DataRecord.builder()
                        .key("new")
                        .source("api")
                        .type("JSON")
                        .payload(Map.of())
                        .build()
        ));
    }

    @Test
    @DisplayName("Should remove record by ID")
    void testRemoveById() {
        // Arrange
        DataRecord saved = service.save("api", "JSON", Map.of("data", "test"));
        long countBefore = service.getTotalRecordCount();

        // Act
        service.removeById(saved.id());

        // Assert
        long countAfter = service.getTotalRecordCount();
        assertEquals(countBefore - 1, countAfter);
        assertNull(service.findById(saved.id()));
    }

    @Test
    @DisplayName("Should clear all records")
    void testClear() {
        // Arrange
        for (int i = 0; i < 5; i++) {
            service.save("api", "JSON", Map.of("i", i));
        }
        assertEquals(5, service.getTotalRecordCount());

        // Act
        service.clear();

        // Assert
        assertEquals(0, service.getTotalRecordCount());
        assertTrue(service.findAllRecords().isEmpty());
    }
}

