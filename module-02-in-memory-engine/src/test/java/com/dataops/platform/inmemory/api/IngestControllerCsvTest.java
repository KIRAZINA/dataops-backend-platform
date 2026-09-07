package com.dataops.platform.inmemory.api;

import com.dataops.platform.common.exception.RecordValidationException;
import com.dataops.platform.common.exception.ValidationIssue;
import com.dataops.platform.inmemory.service.IngestionService;
import com.dataops.platform.inmemory.service.InMemoryStorageService;
import com.dataops.platform.persistence.service.PersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CSV-specific parser rules. Tests the controller's CSV path without standing
 * up the full pipeline — focuses on what the parser itself must enforce before
 * the service's normalizer even sees the data.
 */
@DisplayName("IngestController CSV specifics")
class IngestControllerCsvTest {

    private IngestController controller;
    private PersistenceService persistenceService;
    private InMemoryStorageService storageService;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        persistenceService = mock(PersistenceService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        storageService = mock(InMemoryStorageService.class);
        IngestionService ingestionService = new IngestionService(persistenceService, storageService);
        controller = new IngestController(ingestionService, new ObjectMapper(), new XmlMapper());
    }

    @Test
    @DisplayName("UTF-8 BOM is stripped from CSV input")
    void bomIsStripped() {
        String csv = "\uFEFFid,name\n1,alpha";
        when(persistenceService.saveBatchViaJpa(
                eq("api"),
                eq("CSV"),
                anyList()))
                .thenReturn(List.of());
        controller.ingestCsv(csv);
        org.mockito.ArgumentCaptor<List<Map<String, Object>>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(persistenceService).saveBatchViaJpa(
                eq("api"),
                eq("CSV"),
                captor.capture());
        List<Map<String, Object>> sent = captor.getValue();
        assertEquals(1, sent.size());
        Map<String, Object> row = sent.get(0);
        assertTrue(row.containsKey("id"), "First column should be 'id' after BOM strip, was: " + row.keySet());
        assertEquals(1L, ((Number) row.get("id")).longValue());
        assertEquals("alpha", row.get("name"));
    }

    @Test
    @DisplayName("Empty CSV field becomes null (parsing-semantics rule, not normalization)")
    void emptyFieldBecomesNull() {
        String csv = "id,name\n,alpha\n2,";
        when(persistenceService.saveBatchViaJpa(any(), any(), anyList()))
                .thenReturn(List.of());
        controller.ingestCsv(csv);
        org.mockito.ArgumentCaptor<List<Map<String, Object>>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(persistenceService).saveBatchViaJpa(
                any(),
                any(),
                captor.capture());
        List<Map<String, Object>> sent = captor.getValue();
        assertEquals(2, sent.size());
        assertNull(sent.get(0).get("id"), "Empty first-column value must be null (not \"\")");
        assertEquals("alpha", sent.get(0).get("name"));
        assertEquals(2L, ((Number) sent.get(1).get("id")).longValue());
        assertNull(sent.get(1).get("name"));
    }

    @Test
    @DisplayName("Ragged row produces a per-row validation error with row number")
    void raggedRowReportsRowNumber() {
        String csv = "id,name\n1,alpha\n2";
        RecordValidationException ex = assertThrows(RecordValidationException.class,
                () -> controller.ingestCsv(csv));
        ValidationIssue issue = ex.getIssues().stream()
                .filter(i -> "csv.row".equals(i.field()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected csv.row issue, got: " + ex.getIssues()));
        assertEquals(3, issue.row());
        assertTrue(issue.message().contains("expected 2 columns"));
    }

    @Test
    @DisplayName("Duplicate headers reject the whole request with a single clear message")
    void duplicateHeadersRejected() {
        String csv = "id,name,id\n1,alpha,99";
        RecordValidationException ex = assertThrows(RecordValidationException.class,
                () -> controller.ingestCsv(csv));
        assertTrue(ex.getIssues().stream()
                .anyMatch(i -> "csv.header".equals(i.field()) && i.message().contains("duplicate CSV header")),
                "Expected csv.header duplicate issue, got: " + ex.getIssues());
    }

    @Test
    @DisplayName("stripUtf8Bom leaves well-formed input untouched")
    void bomHelperNoopOnCleanInput() {
        assertEquals("id,name", IngestController.stripUtf8Bom("id,name"));
        assertEquals("", IngestController.stripUtf8Bom(""));
        assertNull(IngestController.stripUtf8Bom(null));
    }

    @Test
    @DisplayName("stripUtf8Bom removes the leading BOM character")
    void bomHelperStripsBOM() {
        assertEquals("id,name", IngestController.stripUtf8Bom("\uFEFFid,name"));
    }
}
