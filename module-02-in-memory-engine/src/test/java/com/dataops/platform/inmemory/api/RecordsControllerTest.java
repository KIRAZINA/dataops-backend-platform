package com.dataops.platform.inmemory.api;

import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.service.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RecordsController Tests")
@ExtendWith(MockitoExtension.class)
class RecordsControllerTest {

    @Mock
    private PersistenceService persistenceService;

    private RecordsController controller;

    @BeforeEach
    void setUp() {
        controller = new RecordsController(persistenceService);
    }

    private PersistedRecord record(long id) {
        return PersistedRecord.builder()
                .id(id)
                .source("src")
                .type("JSON")
                .payload(Map.of("k", "v" + id))
                .ingestedAt(Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime())
                .build();
    }

    private Page<PersistedRecord> paged(List<PersistedRecord> content, Pageable pageable, long total) {
        return new PageImpl<>(content, pageable, total);
    }

    @Test
    @DisplayName("getRecordById should use indexed findById, not findAll")
    void getByIdUsesIndexedLookup() {
        when(persistenceService.findById(42L)).thenReturn(Optional.of(record(42)));

        ResponseEntity<?> response = controller.getRecordById("42");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        verify(persistenceService).findById(42L);
        verify(persistenceService, never()).findAll();
        verify(persistenceService, never()).findAllPaged(any(Pageable.class));
    }

    @Test
    @DisplayName("getRecordById should return 404 when not found")
    void getByIdNotFound() {
        when(persistenceService.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getRecordById("999");

        assertEquals(404, response.getStatusCode().value());
        verify(persistenceService).findById(999L);
        verify(persistenceService, never()).findAll();
    }

    @Test
    @DisplayName("getRecordById should return 404 on non-numeric id")
    void getByIdInvalidFormat() {
        ResponseEntity<?> response = controller.getRecordById("not-a-number");

        assertEquals(404, response.getStatusCode().value());
        verify(persistenceService, never()).findById(any(Long.class));
        verify(persistenceService, never()).findAll();
        verify(persistenceService, never()).findAllPaged(any(Pageable.class));
    }

    @Test
    @DisplayName("getAllRecords uses the paged repository query, never findAll (O(n) regression guard)")
    void getAllRecordsUsesPagedQuery() {
        List<PersistedRecord> pageContent = List.of(record(1), record(2), record(3));
        Pageable pageable = PageRequest.of(0, 20);
        when(persistenceService.findAllPaged(pageable))
                .thenReturn(paged(pageContent, pageable, 50));

        controller.getAllRecords(0, 20);

        verify(persistenceService).findAllPaged(pageable);
        verify(persistenceService, never()).findAll();
        verify(persistenceService, never()).count();
    }

    @Test
    @DisplayName("getAllRecords returns correct page contents and totalElements from Page")
    void getAllRecordsCorrectContents() {
        List<PersistedRecord> pageContent = List.of(record(1), record(2), record(3));
        Pageable pageable = PageRequest.of(0, 20);
        when(persistenceService.findAllPaged(pageable))
                .thenReturn(paged(pageContent, pageable, 50));

        var response = controller.getAllRecords(0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, response.getBody().getContent().size());
        assertEquals(50L, response.getBody().getTotalElements());
        assertEquals(0, response.getBody().getPageNumber());
        assertEquals(20, response.getBody().getPageSize());
    }

    @Test
    @DisplayName("getAllRecords honors custom page size and page number")
    void getAllRecordsCustomPage() {
        // 30 records, page=1, size=5 should return records 6-10
        List<PersistedRecord> pageContent = List.of(record(6), record(7), record(8), record(9), record(10));
        Pageable pageable = PageRequest.of(1, 5);
        when(persistenceService.findAllPaged(pageable))
                .thenReturn(paged(pageContent, pageable, 30));

        var response = controller.getAllRecords(1, 5);

        assertEquals(5, response.getBody().getContent().size());
        assertEquals(30L, response.getBody().getTotalElements());
        assertEquals("6", response.getBody().getContent().get(0).getKey());
    }

    @Test
    @DisplayName("getAllRecords with out-of-range page returns empty content, not an error")
    void getAllRecordsOutOfRange() {
        Pageable pageable = PageRequest.of(10, 20);
        when(persistenceService.findAllPaged(pageable))
                .thenReturn(paged(List.of(), pageable, 1));

        var response = controller.getAllRecords(10, 20);

        assertEquals(200, response.getStatusCode().value(),
                "Out-of-range page should return 200 with empty content, not 4xx");
        assertTrue(response.getBody().getContent().isEmpty());
        assertEquals(1L, response.getBody().getTotalElements());
    }

    @Test
    @DisplayName("getAllRecords with pageSize=1000 does not throw when invoked directly — @Max enforcement happens at the request-mapping layer (via Spring's MethodValidationPostProcessor)")
    void getAllRecordsPageSizeEnforcedAtRequestMappingLayer() {
        // Note: the @Max(500) constraint is enforced by Spring's MethodValidationPostProcessor
        // when the controller is invoked through Spring MVC. Calling the method directly
        // bypasses that validation, so we cannot test the rejection in this isolated unit test.
        // The integration test in the monolith covers the full Spring MVC path. Here we just
        // assert the method accepts the value without crashing — the contract under test is
        // "no surprise throws for oversized values at the data layer".
        Pageable pageable = PageRequest.of(0, 1000);
        when(persistenceService.findAllPaged(pageable))
                .thenReturn(paged(List.of(record(1)), pageable, 1));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> controller.getAllRecords(0, 1000));
    }

    @Test
    @DisplayName("getRecordsBySource uses the paged source query, never findBySource + size (O(n) regression guard)")
    void getRecordsBySourceUsesPagedQuery() {
        List<PersistedRecord> pageContent = List.of(record(1), record(2), record(3));
        Pageable pageable = PageRequest.of(0, 20);
        when(persistenceService.findBySourcePaged("api", pageable))
                .thenReturn(paged(pageContent, pageable, 3));

        controller.getRecordsBySource("api", 0, 20);

        verify(persistenceService).findBySourcePaged("api", pageable);
        // Old implementation called findBySource twice (once for content, once for size) plus count.
        // Any call to those should be absent in the new paged path.
        verify(persistenceService, never()).findBySource(any(String.class));
        verify(persistenceService, never()).count();
    }

    @Test
    @DisplayName("getRecordsBySource returns only matching source records")
    void getRecordsBySourceFilters() {
        List<PersistedRecord> matching = List.of(record(1), record(2), record(3));
        Pageable pageable = PageRequest.of(0, 20);
        when(persistenceService.findBySourcePaged("api", pageable))
                .thenReturn(paged(matching, pageable, 3));

        var response = controller.getRecordsBySource("api", 0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, response.getBody().getContent().size());
        assertEquals(3L, response.getBody().getTotalElements());
    }

    @Test
    @DisplayName("getRecordsBySource returns empty list when source has zero matches, not an error")
    void getRecordsBySourceEmpty() {
        Pageable pageable = PageRequest.of(0, 20);
        when(persistenceService.findBySourcePaged("missing-source", pageable))
                .thenReturn(paged(List.of(), pageable, 0));

        var response = controller.getRecordsBySource("missing-source", 0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().getContent().isEmpty());
        assertEquals(0L, response.getBody().getTotalElements());
        verify(persistenceService, never()).findBySource(any(String.class));
    }

    @Test
    @DisplayName("getRecordsBySource paginates correctly across multiple pages")
    void getRecordsBySourcePaginates() {
        // page 0 (records 1-10), page 1 (records 11-20), page 2 (records 21-25)
        List<PersistedRecord> page0 = new ArrayList<>();
        for (int i = 1; i <= 10; i++) page0.add(record(i));
        List<PersistedRecord> page1 = new ArrayList<>();
        for (int i = 11; i <= 20; i++) page1.add(record(i));
        List<PersistedRecord> page2 = new ArrayList<>();
        for (int i = 21; i <= 25; i++) page2.add(record(i));

        when(persistenceService.findBySourcePaged("api", PageRequest.of(0, 10)))
                .thenReturn(paged(page0, PageRequest.of(0, 10), 25));
        when(persistenceService.findBySourcePaged("api", PageRequest.of(1, 10)))
                .thenReturn(paged(page1, PageRequest.of(1, 10), 25));
        when(persistenceService.findBySourcePaged("api", PageRequest.of(2, 10)))
                .thenReturn(paged(page2, PageRequest.of(2, 10), 25));

        var p1 = controller.getRecordsBySource("api", 0, 10);
        var p2 = controller.getRecordsBySource("api", 1, 10);
        var p3 = controller.getRecordsBySource("api", 2, 10);

        assertEquals(10, p1.getBody().getContent().size());
        assertEquals(10, p2.getBody().getContent().size());
        assertEquals(5, p3.getBody().getContent().size());
        assertEquals(25L, p1.getBody().getTotalElements());
    }
}
