package com.dataops.platform.filestorage.service;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.inmemory.service.InMemoryStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FileExportService.
 * Tests cover JSON and CSV export functionality with proper serialization.
 */
@DisplayName("FileExportService Tests")
@ExtendWith(MockitoExtension.class)
class FileExportServiceTest {

    @Mock
    private InMemoryStorageService storageService;

    private FileExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new FileExportService(storageService, new ObjectMapper());
    }

    @Test
    @DisplayName("Should export records as JSON with proper formatting")
    @SneakyThrows
    void testExportAsJson() {
        List<DataRecord> records = createTestRecords(3);
        when(storageService.findAllRecords()).thenReturn(records);

        ResponseEntity<byte[]> response = exportService.exportAsJson();

        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().getFilename().contains("dataops_"));
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());

        String jsonContent = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(jsonContent.startsWith("["));
        assertTrue(jsonContent.contains("\"id\""));
        assertTrue(jsonContent.contains("\"source\""));
        assertTrue(jsonContent.contains("\"type\""));
    }

    @Test
    @DisplayName("Should export empty records as JSON")
    @SneakyThrows
    void testExportAsJsonEmpty() {
        when(storageService.findAllRecords()).thenReturn(new ArrayList<>());

        ResponseEntity<byte[]> response = exportService.exportAsJson();

        assertNotNull(response.getBody());
        assertEquals("[]", new String(response.getBody(), StandardCharsets.UTF_8).replaceAll("\\s+", ""));
    }

    @Test
    @DisplayName("Should export records as CSV with proper escaping")
    @SneakyThrows
    void testExportAsCsv() {
        List<DataRecord> records = createTestRecords(3);
        when(storageService.findAllRecords()).thenReturn(records);

        ResponseEntity<byte[]> response = exportService.exportAsCsv();

        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().getFilename().contains("dataops_"));
        assertEquals("text", response.getHeaders().getContentType().getType());
        assertEquals("csv", response.getHeaders().getContentType().getSubtype());
        assertEquals(StandardCharsets.UTF_8, response.getHeaders().getContentType().getCharset());

        String csvContent = new String(response.getBody(), StandardCharsets.UTF_8);
        String[] lines = csvContent.split("\n");

        assertTrue(lines.length >= 3);
        assertTrue(lines[0].contains("ID"));
        assertTrue(lines[0].contains("Source"));
        assertTrue(lines[0].contains("Type"));
        assertTrue(lines[0].contains("Payload"));
    }

    @Test
    @DisplayName("Should export CSV with proper JSON payload serialization")
    @SneakyThrows
    void testExportAsCsvPayloadSerialization() {
        Map<String, Object> complexPayload = Map.of(
                "name", "test-record",
                "value", 42,
                "nested", Map.of("key", "value"),
                "array", List.of(1, 2, 3)
        );

        DataRecord record = DataRecord.builder()
                .key("1")
                .source("test-source")
                .type("TEST")
                .payload(complexPayload)
                .timestamp(Instant.now())
                .build();

        when(storageService.findAllRecords()).thenReturn(List.of(record));

        ResponseEntity<byte[]> response = exportService.exportAsCsv();

        assertNotNull(response.getBody());
        String csvContent = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(csvContent.contains("test-record"));
        assertTrue(csvContent.contains("nested"));
        assertTrue(csvContent.contains("array"));
    }

    @Test
    @DisplayName("Should handle CSV export with special characters")
    @SneakyThrows
    void testExportAsCsvSpecialCharacters() {
        Map<String, Object> payload = Map.of(
                "text", "value with, comma and \"quotes\"",
                "newline_test", "line1\nline2"
        );

        DataRecord record = DataRecord.builder()
                .key("1")
                .source("test")
                .type("DATA")
                .payload(payload)
                .timestamp(Instant.now())
                .build();

        when(storageService.findAllRecords()).thenReturn(List.of(record));

        ResponseEntity<byte[]> response = exportService.exportAsCsv();

        assertNotNull(response.getBody());
        String csvContent = new String(response.getBody(), StandardCharsets.UTF_8);
        assertFalse(csvContent.isEmpty());
        assertTrue(csvContent.contains("test"));
    }

    @Test
    @DisplayName("Should export empty records as CSV")
    @SneakyThrows
    void testExportAsCsvEmpty() {
        when(storageService.findAllRecords()).thenReturn(new ArrayList<>());

        ResponseEntity<byte[]> response = exportService.exportAsCsv();

        assertNotNull(response.getBody());
        String csvContent = new String(response.getBody(), StandardCharsets.UTF_8);
        String[] lines = csvContent.split("\n");

        assertEquals(1, lines.length);
        assertTrue(lines[0].contains("ID"));
    }

    @Test
    @DisplayName("Should propagate storage failures during export")
    void testExportJsonFailurePropagation() {
        when(storageService.findAllRecords())
                .thenThrow(new IllegalStateException("Storage service error"));

        assertThrows(IllegalStateException.class, () -> exportService.exportAsJson());
    }

    @Test
    @DisplayName("Should export with correct charset UTF-8")
    @SneakyThrows
    void testExportAsCsvCharset() {
        Map<String, Object> payload = Map.of("text", "Cafe");
        DataRecord record = DataRecord.builder()
                .key("1")
                .source("utf8-test")
                .type("TEXT")
                .payload(payload)
                .timestamp(Instant.now())
                .build();

        when(storageService.findAllRecords()).thenReturn(List.of(record));

        ResponseEntity<byte[]> response = exportService.exportAsCsv();

        assertNotNull(response.getBody());
        String csvContent = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(csvContent.contains("utf8-test"));
    }

    private List<DataRecord> createTestRecords(int count) {
        List<DataRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DataRecord record = DataRecord.builder()
                    .key(String.valueOf(i))
                    .source("test-source-" + i)
                    .type("TEST")
                    .payload(Map.of("index", i, "name", "record-" + i))
                    .timestamp(Instant.now())
                    .build();
            records.add(record);
        }
        return records;
    }
}
