package com.dataops.platform.filestorage.api;

import com.dataops.platform.filestorage.service.FileExportService;
import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.service.PersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@DisplayName("FileStorageController endpoint tests")
class FileStorageControllerTest {

    private PersistenceService persistenceService;
    private FileStorageController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        persistenceService = mock(PersistenceService.class);
        controller = new FileStorageController(new FileExportService(persistenceService, new ObjectMapper()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private PersistedRecord record(long id, double value) {
        return PersistedRecord.builder()
                .id(id)
                .source("test")
                .type("JSON")
                .payload(Map.of("value", value, "id", id))
                .ingestedAt(Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime())
                .build();
    }

    @Test
    @DisplayName("/export/json returns JSON content-type with attachment header")
    void exportJsonHeadersAndContent() throws Exception {
        when(persistenceService.findAll()).thenReturn(List.of(record(1, 1.0), record(2, 2.0)));

        MvcResult started = mockMvc.perform(get("/api/v1/storage/export/json")).andReturn();
        MvcResult result = mockMvc.perform(asyncDispatch(started)).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        String contentType = result.getResponse().getContentType();
        assertNotNull(contentType);
        assertTrue(contentType.startsWith("application/json")
                        || contentType.startsWith("text/json"),
                "Expected application/json but got " + contentType);
        assertNotNull(result.getResponse().getHeader("Content-Disposition"));
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains("attachment"));
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains(".json"));
        byte[] body = result.getResponse().getContentAsByteArray();
        String bodyStr = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(bodyStr.startsWith("["),
                "Expected JSON array but got: " + bodyStr.substring(0, Math.min(100, bodyStr.length())));
        assertTrue(bodyStr.contains("\"id\"") || bodyStr.contains("\"key\""),
                "Expected an id/key field in JSON body. Body: " + bodyStr.substring(0, Math.min(200, bodyStr.length())));
    }

    @Test
    @DisplayName("/export/csv returns CSV content-type with attachment header")
    void exportCsvHeadersAndContent() throws Exception {
        when(persistenceService.findAll()).thenReturn(List.of(record(1, 1.0), record(2, 2.0)));

        MvcResult started = mockMvc.perform(get("/api/v1/storage/export/csv")).andReturn();
        MvcResult result = mockMvc.perform(asyncDispatch(started)).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        String contentType = result.getResponse().getContentType();
        assertNotNull(contentType);
        String primary = contentType.split(";")[0].trim();
        assertEquals("text/csv", primary, "Expected text/csv but got " + contentType);
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains(".csv"));
        byte[] body = result.getResponse().getContentAsByteArray();
        String bodyStr = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(bodyStr.contains("Source") || bodyStr.contains("ID"),
                "Expected CSV header in body. Got: <" + bodyStr + ">");
    }

    @Test
    @DisplayName("/export/binary returns octet-stream with valid binary payload")
    void exportBinaryHeadersAndContent() throws Exception {
        List<PersistedRecord> records = new ArrayList<>();
        for (int i = 1; i <= 3; i++) records.add(record(i, i));
        when(persistenceService.findAll()).thenReturn(records);

        MvcResult result = mockMvc.perform(get("/api/v1/storage/export/binary")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(MediaType.APPLICATION_OCTET_STREAM_VALUE,
                result.getResponse().getContentType());
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains(".bin"));

        byte[] body = result.getResponse().getContentAsByteArray();
        assertNotNull(body);
        assertTrue(body.length > 4, "Binary body should contain at least the count prefix");

        java.io.DataInputStream din = new java.io.DataInputStream(new java.io.ByteArrayInputStream(body));
        assertEquals(3, din.readInt(), "First 4 bytes should encode the record count (3)");
    }

    @Test
    @DisplayName("Empty store produces empty-but-valid binary export (JSON/CSV are streamed)")
    void emptyStoreProducesValidExports() throws Exception {
        when(persistenceService.findAll()).thenReturn(List.of());

        // JSON/CSV are StreamingResponseBody — only the binary endpoint returns a buffered byte[].
        MvcResult bin = mockMvc.perform(get("/api/v1/storage/export/binary")).andReturn();

        assertEquals(200, bin.getResponse().getStatus());
        byte[] body = bin.getResponse().getContentAsByteArray();
        assertEquals(4, body.length, "Empty binary export is exactly the 4-byte count header (0)");
        java.io.DataInputStream din = new java.io.DataInputStream(new java.io.ByteArrayInputStream(body));
        assertEquals(0, din.readInt());
    }

    @Test
    @DisplayName("Moderately large dataset (5000 records) exports without error and matches declared length")
    void moderateDatasetExportsCorrectly() throws Exception {
        List<PersistedRecord> records = new ArrayList<>();
        for (int i = 1; i <= 5000; i++) records.add(record(i, i));
        when(persistenceService.findAll()).thenReturn(records);

        MvcResult bin = mockMvc.perform(get("/api/v1/storage/export/binary")).andReturn();

        assertEquals(200, bin.getResponse().getStatus());
        byte[] body = bin.getResponse().getContentAsByteArray();
        assertEquals(5000, new java.io.DataInputStream(new java.io.ByteArrayInputStream(body)).readInt(),
                "5000-record export must encode the record count in the prefix");
        assertTrue(body.length > 100_000,
                "5000 records should produce a sizable payload; smaller would indicate a regression");
    }
}
