package com.dataops.platform.inmemory.service;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.service.PersistenceService;
import com.dataops.platform.streaming.outbox.OutboxEvent;
import com.dataops.platform.streaming.outbox.OutboxRepository;
import com.dataops.platform.streaming.outbox.OutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IngestionService outbox integration tests")
class IngestionServiceOutboxTest {

    @Mock
    private PersistenceService persistenceService;

    @Mock
    private InMemoryStorageService storageService;

    @Mock
    private OutboxRepository outboxRepository;

    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new IngestionService(persistenceService, storageService);
    }

    private void enableOutbox() {
        reflectionSetField(ingestionService, "outboxRepository", outboxRepository);
        reflectionSetField(ingestionService, "objectMapper", new ObjectMapper().findAndRegisterModules());
        reflectionSetField(ingestionService, "kafkaEnabled", true);
    }

    private PersistedRecord stubPersisted(Map<String, Object> payload) {
        return PersistedRecord.builder()
                .id(99L)
                .source("api")
                .type("JSON")
                .payload(payload)
                .build();
    }

    @Test
    @DisplayName("With OutboxRepository wired: ingest writes an outbox row in PENDING status")
    void outboxRowWrittenWhenRepositoryPresent() {
        enableOutbox();

        Map<String, Object> payload = Map.of("value", 42);
        when(persistenceService.saveViaJpa(eq("api"), eq("JSON"), anyMap()))
                .thenReturn(stubPersisted(payload));

        DataRecord record = ingestionService.ingest("api", "JSON", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent evt = captor.getValue();
        assertEquals(OutboxStatus.PENDING, evt.getStatus());
        assertEquals(0, evt.getRetries());
        assertEquals("DATA_RECORD_INGESTED", evt.getEventType());
        assertEquals("99", evt.getAggregateId());
        assertNotNull(evt.getPayload());
        assertTrue(evt.getPayload().contains("\"value\":42"));
        assertNotNull(record);
    }

    @Test
    @DisplayName("Without OutboxRepository (NoOp bypass): no outbox row is written")
    void noOutboxRowWhenRepositoryAbsent() {
        Map<String, Object> payload = Map.of("value", 42);
        when(persistenceService.saveViaJpa(eq("api"), eq("JSON"), anyMap()))
                .thenReturn(stubPersisted(payload));

        DataRecord record = ingestionService.ingest("api", "JSON", payload);

        verify(outboxRepository, never()).save(any());
        assertNotNull(record);
    }

    @Test
    @DisplayName("NoOp bypass: app.kafka.enabled=false skips the outbox write even when a repository is wired")
    void noOutboxRowWhenKafkaDisabled() {
        reflectionSetField(ingestionService, "outboxRepository", outboxRepository);
        reflectionSetField(ingestionService, "objectMapper", new ObjectMapper().findAndRegisterModules());
        reflectionSetField(ingestionService, "kafkaEnabled", false);

        Map<String, Object> payload = Map.of("value", 42);
        when(persistenceService.saveViaJpa(eq("api"), eq("JSON"), anyMap()))
                .thenReturn(stubPersisted(payload));

        DataRecord record = ingestionService.ingest("api", "JSON", payload);

        verify(outboxRepository, never()).save(any());
        assertNotNull(record);
    }

    @Test
    @DisplayName("Batch ingest with OutboxRepository: one outbox row per record")
    void batchIngestWritesMultipleOutboxRows() {
        enableOutbox();

        Map<String, Object> p1 = Map.of("id", 1);
        Map<String, Object> p2 = Map.of("id", 2);
        when(persistenceService.saveBatchViaJpa(eq("api"), eq("CSV"), anyList()))
                .thenReturn(List.of(
                        PersistedRecord.builder().id(10L).source("api").type("CSV").payload(p1).build(),
                        PersistedRecord.builder().id(11L).source("api").type("CSV").payload(p2).build()));

        List<DataRecord> records = ingestionService.ingestBatch("api", "CSV", List.of(p1, p2));

        verify(outboxRepository, times(2)).save(any(OutboxEvent.class));
        assertEquals(2, records.size());
    }

    @Test
    @DisplayName("Without OutboxRepository: batch still saves to in-memory store")
    void batchIngestNoOutboxStillSavesToMemory() {
        Map<String, Object> p1 = Map.of("id", 1);
        when(persistenceService.saveBatchViaJpa(eq("api"), eq("CSV"), anyList()))
                .thenReturn(List.of(
                        PersistedRecord.builder().id(10L).source("api").type("CSV").payload(p1).build()));

        List<DataRecord> records = ingestionService.ingestBatch("api", "CSV", List.of(p1));

        verify(outboxRepository, never()).save(any());
        verify(storageService, times(1)).saveRecord(any(DataRecord.class));
        assertEquals(1, records.size());
    }

    @SuppressWarnings("unchecked")
    private static <T> void reflectionSetField(Object target, String fieldName, T value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
