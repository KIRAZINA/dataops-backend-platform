package com.dataops.platform.inmemory.service;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.service.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IngestionService Tests")
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private PersistenceService persistenceService;

    @Mock
    private InMemoryStorageService storageService;

    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new IngestionService(persistenceService, storageService);
    }

    @Test
    @DisplayName("Should save to in-memory store exactly once per ingest call")
    void shouldDelegateToInMemoryStore() {
        Map<String, Object> payload = Map.of("value", 42);
        when(persistenceService.saveViaJpa(eq("api"), eq("JSON"), anyMap()))
                .thenReturn(PersistedRecord.builder().id(7L).source("api").type("JSON").payload(payload).build());

        DataRecord result = ingestionService.ingest("api", "JSON", payload);

        assertNotNull(result);
        assertEquals("7", result.getKey());
        assertEquals("api", result.getSource());
        assertEquals("JSON", result.getType());

        ArgumentCaptor<DataRecord> captor = ArgumentCaptor.forClass(DataRecord.class);
        verify(storageService, times(1)).saveRecord(captor.capture());
        DataRecord saved = captor.getValue();
        assertEquals("7", saved.getKey());
        assertEquals(payload, saved.getPayload());
    }

    @Test
    @DisplayName("Should propagate persistence failures and not call in-memory store")
    void shouldPropagatePersistenceFailure() {
        Map<String, Object> payload = Map.of("value", 1);
        when(persistenceService.saveViaJpa(any(), any(), anyMap()))
                .thenThrow(new RuntimeException("db down"));

        try {
            ingestionService.ingest("api", "JSON", payload);
        } catch (IllegalStateException expected) {
            // expected
        }
        verify(storageService, times(0)).saveRecord(any());
    }

    @Test
    @DisplayName("Should still return record when in-memory store save fails")
    void shouldTolerateInMemorySaveFailure() {
        Map<String, Object> payload = Map.of("value", 1);
        when(persistenceService.saveViaJpa(any(), any(), anyMap()))
                .thenReturn(PersistedRecord.builder().id(1L).source("api").type("JSON").payload(payload).build());
        org.mockito.Mockito.doThrow(new RuntimeException("oom")).when(storageService).saveRecord(any());

        DataRecord result = ingestionService.ingest("api", "JSON", payload);
        assertNotNull(result);
        assertEquals("1", result.getKey());
    }

    @Test
    @DisplayName("Should save every batched record to in-memory store with matching IDs")
    void shouldSaveBatch() {
        Map<String, Object> p1 = Map.of("id", 1);
        Map<String, Object> p2 = Map.of("id", 2);
        when(persistenceService.saveBatchViaJpa(eq("api"), eq("CSV"), anyList()))
                .thenReturn(List.of(
                        PersistedRecord.builder().id(10L).source("api").type("CSV").payload(p1).build(),
                        PersistedRecord.builder().id(11L).source("api").type("CSV").payload(p2).build()));

        List<DataRecord> result = ingestionService.ingestBatch("api", "CSV", List.of(p1, p2));

        assertEquals(2, result.size());
        assertEquals("10", result.get(0).getKey());
        assertEquals("11", result.get(1).getKey());
        verify(storageService, times(2)).saveRecord(any(DataRecord.class));
    }
}
