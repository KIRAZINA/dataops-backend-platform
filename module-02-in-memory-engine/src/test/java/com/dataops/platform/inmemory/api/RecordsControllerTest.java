package com.dataops.platform.inmemory.api;

import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.service.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    // ---------- /{id} ----------

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

    // ---------- getAllRecords: paged query path ----------

    @Test
    @DisplayName("getAllRecords uses PersistenceService.findRecords(spec, pageable) - not findAll")
    void getAllRecordsUsesFindRecords() {
        Pageable pageable = PageRequest.of(0, 20);
        when(persistenceService.findRecords(any(Specification.class), any(Pageable.class)))
                .thenReturn(paged(List.of(record(1), record(2), record(3)), pageable, 50));

        controller.getAllRecords(null, null, null, null, "ingestedAt", "desc", 0, 20);

        verify(persistenceService).findRecords(any(Specification.class), any(Pageable.class));
        verify(persistenceService, never()).findAll();
        verify(persistenceService, never()).findAllPaged(any(Pageable.class));
        verify(persistenceService, never()).count();
    }

    @Test
    @DisplayName("getAllRecords returns correct page contents and totalElements from Page")
    void getAllRecordsCorrectContents() {
        Pageable pageable = PageRequest.of(0, 20);
        when(persistenceService.findRecords(any(Specification.class), any(Pageable.class)))
                .thenReturn(paged(List.of(record(1), record(2), record(3)), pageable, 50));

        var response = controller.getAllRecords(null, null, null, null, "ingestedAt", "desc", 0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, response.getBody().getContent().size());
        assertEquals(50L, response.getBody().getTotalElements());
        assertEquals(0, response.getBody().getPageNumber());
        assertEquals(20, response.getBody().getPageSize());
    }

    @Test
    @DisplayName("getAllRecords honors custom page size and page number")
    void getAllRecordsCustomPage() {
        Pageable pageable = PageRequest.of(1, 5);
        when(persistenceService.findRecords(any(Specification.class), any(Pageable.class)))
                .thenReturn(paged(List.of(record(6), record(7), record(8), record(9), record(10)),
                        pageable, 30));

        var response = controller.getAllRecords(null, null, null, null, "ingestedAt", "desc", 1, 5);

        assertEquals(5, response.getBody().getContent().size());
        assertEquals(30L, response.getBody().getTotalElements());
        assertEquals("6", response.getBody().getContent().get(0).getKey());
    }

    @Test
    @DisplayName("getAllRecords with out-of-range page returns empty content, not an error")
    void getAllRecordsOutOfRange() {
        Pageable pageable = PageRequest.of(10, 20);
        when(persistenceService.findRecords(any(Specification.class), any(Pageable.class)))
                .thenReturn(paged(List.of(), pageable, 1));

        var response = controller.getAllRecords(null, null, null, null, "ingestedAt", "desc", 10, 20);

        assertEquals(200, response.getStatusCode().value(),
                "Out-of-range page should return 200 with empty content, not 4xx");
        assertTrue(response.getBody().getContent().isEmpty());
        assertEquals(1L, response.getBody().getTotalElements());
    }

    // ---------- B4: deterministic tiebreaker (secondary sort = id, same direction) ----------

    @Test
    @DisplayName("getAllRecords always appends 'id' as secondary sort in the same direction")
    void getAllRecordsAppendsIdTiebreaker() {
        Pageable pageable = PageRequest.of(0, 20);
        when(persistenceService.findRecords(any(Specification.class), any(Pageable.class)))
                .thenReturn(paged(List.of(record(1)), pageable, 1));

        controller.getAllRecords(null, null, null, null, "ingestedAt", "desc", 0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(persistenceService).findRecords(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();

        List<Sort.Order> orders = sort.toList();
        assertEquals(2, orders.size(), "primary + tiebreaker");
        assertEquals("ingestedAt", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
        assertEquals("id", orders.get(1).getProperty(), "tiebreaker must be id");
        assertEquals(Sort.Direction.DESC, orders.get(1).getDirection(),
                "tiebreaker must match primary direction");
    }

    @Test
    @DisplayName("getAllRecords ascending: tiebreaker is also ascending")
    void getAllRecordsTiebreakerAsc() {
        Pageable pageable = PageRequest.of(0, 20);
        when(persistenceService.findRecords(any(Specification.class), any(Pageable.class)))
                .thenReturn(paged(List.of(record(1)), pageable, 1));

        controller.getAllRecords(null, null, null, null, "ingestedAt", "asc", 0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(persistenceService).findRecords(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();
        List<Sort.Order> orders = sort.toList();
        assertEquals(Sort.Direction.ASC, orders.get(1).getDirection());
    }

    // ---------- B2: contract guard on sortBy / sortDir ----------

    @Test
    @DisplayName("getAllRecords rejects unknown sortBy with IllegalArgumentException (-> 400)")
    void getAllRecordsRejectsUnknownSortBy() {
        assertThrows(IllegalArgumentException.class, () ->
                controller.getAllRecords(null, null, null, null, "source", "asc", 0, 20));
        verify(persistenceService, never()).findRecords(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("getAllRecords rejects sortBy=bogus with IllegalArgumentException (-> 400)")
    void getAllRecordsRejectsBogusSortBy() {
        assertThrows(IllegalArgumentException.class, () ->
                controller.getAllRecords(null, null, null, null, "bogus", "asc", 0, 20));
    }

    @Test
    @DisplayName("getAllRecords rejects unknown sortDir with IllegalArgumentException (-> 400)")
    void getAllRecordsRejectsUnknownSortDir() {
        assertThrows(IllegalArgumentException.class, () ->
                controller.getAllRecords(null, null, null, null, "ingestedAt", "sideways", 0, 20));
    }

    // ---------- B3 / B5: range validation ----------

    @Test
    @DisplayName("getAllRecords rejects from > to with IllegalArgumentException (-> 400)")
    void getAllRecordsRejectsInvertedRange() {
        Instant from = Instant.parse("2026-09-01T13:00:00Z");
        Instant to = Instant.parse("2026-09-01T12:00:00Z");
        assertThrows(IllegalArgumentException.class, () ->
                controller.getAllRecords(null, null, from, to, "ingestedAt", "desc", 0, 20));
    }

    @Test
    @DisplayName("getAllRecords accepts from == to (inclusive boundary is valid)")
    void getAllRecordsAcceptsEqualBounds() {
        Pageable pageable = PageRequest.of(0, 20);
        when(persistenceService.findRecords(any(Specification.class), any(Pageable.class)))
                .thenReturn(paged(List.of(record(1)), pageable, 1));

        Instant same = Instant.parse("2026-09-01T12:00:00Z");
        var response = controller.getAllRecords(null, null, same, same, "ingestedAt", "desc", 0, 20);

        assertEquals(200, response.getStatusCode().value());
    }

    // ---------- omitted parameters (no predicates) ----------

    @Test
    @DisplayName("getAllRecords with no filters builds a no-op specification (returns everything)")
    void getAllRecordsNoFiltersPassesEmptySpec() {
        Pageable pageable = PageRequest.of(0, 20);
        when(persistenceService.findRecords(any(Specification.class), any(Pageable.class)))
                .thenReturn(paged(List.of(record(1), record(2)), pageable, 2));

        var response = controller.getAllRecords(null, null, null, null, "ingestedAt", "desc", 0, 20);

        assertEquals(2, response.getBody().getContent().size());
        verify(persistenceService).findRecords(any(Specification.class), any(Pageable.class));
    }

    // ---------- getRecordsBySource (legacy endpoint) ----------

    @Test
    @DisplayName("getRecordsBySource uses the paged source query, never findBySource + size")
    void getRecordsBySourceUsesPagedQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        when(persistenceService.findBySourcePaged("api", pageable))
                .thenReturn(paged(List.of(record(1), record(2), record(3)), pageable, 3));

        controller.getRecordsBySource("api", 0, 20);

        verify(persistenceService).findBySourcePaged("api", pageable);
        verify(persistenceService, never()).findBySource(any(String.class));
        verify(persistenceService, never()).count();
    }

    @Test
    @DisplayName("getRecordsBySource returns only matching source records")
    void getRecordsBySourceFilters() {
        Pageable pageable = PageRequest.of(0, 20);
        when(persistenceService.findBySourcePaged("api", pageable))
                .thenReturn(paged(List.of(record(1), record(2), record(3)), pageable, 3));

        var response = controller.getRecordsBySource("api", 0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, response.getBody().getContent().size());
        assertEquals(3L, response.getBody().getTotalElements());
    }

    @Test
    @DisplayName("getRecordsBySource returns empty list when source has zero matches")
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
        Pageable p0 = PageRequest.of(0, 10);
        Pageable p1 = PageRequest.of(1, 10);
        Pageable p2 = PageRequest.of(2, 10);

        List<PersistedRecord> page0 = new ArrayList<>();
        for (int i = 1; i <= 10; i++) page0.add(record(i));
        List<PersistedRecord> page1 = new ArrayList<>();
        for (int i = 11; i <= 20; i++) page1.add(record(i));
        List<PersistedRecord> page2 = new ArrayList<>();
        for (int i = 21; i <= 25; i++) page2.add(record(i));

        when(persistenceService.findBySourcePaged("api", p0)).thenReturn(paged(page0, p0, 25));
        when(persistenceService.findBySourcePaged("api", p1)).thenReturn(paged(page1, p1, 25));
        when(persistenceService.findBySourcePaged("api", p2)).thenReturn(paged(page2, p2, 25));

        var r1 = controller.getRecordsBySource("api", 0, 10);
        var r2 = controller.getRecordsBySource("api", 1, 10);
        var r3 = controller.getRecordsBySource("api", 2, 10);

        assertEquals(10, r1.getBody().getContent().size());
        assertEquals(10, r2.getBody().getContent().size());
        assertEquals(5, r3.getBody().getContent().size());
        assertEquals(25L, r1.getBody().getTotalElements());
    }
}
