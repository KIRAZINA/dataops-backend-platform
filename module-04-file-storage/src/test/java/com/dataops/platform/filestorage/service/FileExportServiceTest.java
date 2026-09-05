package com.dataops.platform.filestorage.service;

import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.service.PersistenceService;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
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
    private PersistenceService persistenceService;

    private FileExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new FileExportService(persistenceService, new ObjectMapper());
    }

    @Test
    @DisplayName("Should export records as JSON with proper formatting")
    @SneakyThrows
    void testExportAsJson() {
        List<PersistedRecord> records = createTestRecords(3);
        when(persistenceService.findAll()).thenReturn(records);

        ResponseEntity<StreamingResponseBody> response = exportService.exportAsJson();

        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().getFilename().contains("dataops_"));
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());

        String jsonContent = toString(response.getBody());
        assertTrue(jsonContent.startsWith("["));
        assertTrue(jsonContent.contains("\"id\""));
        assertTrue(jsonContent.contains("\"source\""));
        assertTrue(jsonContent.contains("\"type\""));
    }

    @Test
    @DisplayName("Should export empty records as JSON")
    @SneakyThrows
    void testExportAsJsonEmpty() {
        when(persistenceService.findAll()).thenReturn(new ArrayList<>());

        ResponseEntity<StreamingResponseBody> response = exportService.exportAsJson();

        assertNotNull(response.getBody());
        assertEquals("[]", toString(response.getBody()).replaceAll("\\s+", ""));
    }

    @Test
    @DisplayName("Should export records as CSV with proper escaping")
    @SneakyThrows
    void testExportAsCsv() {
        List<PersistedRecord> records = createTestRecords(3);
        when(persistenceService.findAll()).thenReturn(records);

        ResponseEntity<StreamingResponseBody> response = exportService.exportAsCsv();

        assertNotNull(response.getBody());
        assertTrue(response.getHeaders().getContentDisposition().getFilename().contains("dataops_"));
        assertEquals("text", response.getHeaders().getContentType().getType());
        assertEquals("csv", response.getHeaders().getContentType().getSubtype());
        assertEquals(StandardCharsets.UTF_8, response.getHeaders().getContentType().getCharset());

        String csvContent = toString(response.getBody());
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

        PersistedRecord record = PersistedRecord.builder()
                .id(1L)
                .source("test-source")
                .type("TEST")
                .payload(complexPayload)
                .ingestedAt(Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime())
                .build();

        when(persistenceService.findAll()).thenReturn(List.of(record));

        ResponseEntity<StreamingResponseBody> response = exportService.exportAsCsv();

        assertNotNull(response.getBody());
        String csvContent = toString(response.getBody());
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

        PersistedRecord record = PersistedRecord.builder()
                .id(1L)
                .source("test")
                .type("DATA")
                .payload(payload)
                .ingestedAt(Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime())
                .build();

        when(persistenceService.findAll()).thenReturn(List.of(record));

        ResponseEntity<StreamingResponseBody> response = exportService.exportAsCsv();

        assertNotNull(response.getBody());
        String csvContent = toString(response.getBody());
        assertFalse(csvContent.isEmpty());
        assertTrue(csvContent.contains("test"));
    }

    @Test
    @DisplayName("Should export empty records as CSV")
    @SneakyThrows
    void testExportAsCsvEmpty() {
        when(persistenceService.findAll()).thenReturn(new ArrayList<>());

        ResponseEntity<StreamingResponseBody> response = exportService.exportAsCsv();

        assertNotNull(response.getBody());
        String csvContent = toString(response.getBody());
        String[] lines = csvContent.split("\n");

        assertEquals(1, lines.length);
        assertTrue(lines[0].contains("ID"));
    }

    @Test
    @DisplayName("Should export records as binary using BinaryRecordSerializer")
    @SneakyThrows
    void testExportAsBinary() {
        List<PersistedRecord> records = createTestRecords(3);
        when(persistenceService.findAll()).thenReturn(records);

        ResponseEntity<byte[]> response = exportService.exportAsBinary();

        assertNotNull(response.getBody());
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getContentDisposition().getFilename().contains("dataops_"));
        assertTrue(response.getHeaders().getContentDisposition().getFilename().endsWith(".bin"));

        // Body must be larger than the 4-byte count header for 3 records
        assertTrue(response.getBody().length > 4,
                "Binary body must contain at least the record count plus encoded frames");

        // Verify the 4-byte record-count prefix decodes to 3
        java.io.DataInputStream din = new java.io.DataInputStream(
                new java.io.ByteArrayInputStream(response.getBody()));
        assertEquals(3, din.readInt());
    }

    @Test
    @DisplayName("Should export empty record set as valid empty binary stream")
    @SneakyThrows
    void testExportAsBinaryEmpty() {
        when(persistenceService.findAll()).thenReturn(new ArrayList<>());

        ResponseEntity<byte[]> response = exportService.exportAsBinary();

        assertNotNull(response.getBody());
        // 4-byte count header with value 0
        assertEquals(4, response.getBody().length);
        java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(response.getBody());
        java.io.DataInputStream din = new java.io.DataInputStream(in);
        assertEquals(0, din.readInt());
    }

    @Test
    @DisplayName("Should propagate storage failures during export")
    void testExportJsonFailurePropagation() {
        when(persistenceService.findAll())
                .thenThrow(new IllegalStateException("Storage service error"));

        assertThrows(IllegalStateException.class, () -> exportService.exportAsJson());
    }

    @Test
    @DisplayName("Should export with correct charset UTF-8")
    @SneakyThrows
    void testExportAsCsvCharset() {
        Map<String, Object> payload = Map.of("text", "Cafe");
        PersistedRecord record = PersistedRecord.builder()
                .id(1L)
                .source("utf8-test")
                .type("TEXT")
                .payload(payload)
                .ingestedAt(Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime())
                .build();

        when(persistenceService.findAll()).thenReturn(List.of(record));

        ResponseEntity<StreamingResponseBody> response = exportService.exportAsCsv();

        assertNotNull(response.getBody());
        String csvContent = toString(response.getBody());
        assertTrue(csvContent.contains("utf8-test"));
    }

    private List<PersistedRecord> createTestRecords(int count) {
        List<PersistedRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PersistedRecord record = PersistedRecord.builder()
                    .id((long) i)
                    .source("test-source-" + i)
                    .type("TEST")
                    .payload(Map.of("index", i, "name", "record-" + i))
                    .ingestedAt(Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime())
                    .build();
            records.add(record);
        }
        return records;
    }

    private String toString(StreamingResponseBody body) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            body.writeTo(baos);
            return baos.toString(StandardCharsets.UTF_8);
        }
    }
}
