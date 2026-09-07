package com.dataops.platform.inmemory.service;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.service.PersistenceService;
import com.dataops.platform.streaming.outbox.OutboxEvent;
import com.dataops.platform.streaming.outbox.OutboxRepository;
import com.dataops.platform.streaming.outbox.OutboxStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ingestion entry point: persists the record to JPA, appends a transactional
 * outbox row for the Kafka relay, then mirrors into the in-memory store.
 *
 * <p>The JPA insert and the outbox insert share this method's
 * {@code @Transactional} boundary (single DataSource / single transaction
 * manager in the monolith), which is the core guarantee of the transactional
 * outbox pattern: either both rows commit or neither does. The outbox failure
 * therefore propagates (never swallowed) so the record insert rolls back with
 * it.
 *
 * <p>The outbox write is skipped entirely when Kafka is disabled
 * ({@code app.kafka.enabled=false}, the dev/test default) or when no
 * {@code OutboxRepository} bean is wired (standalone module-02 contexts
 * without JPA) — the NoOp bypass. The polling relay
 * ({@code OutboxRelayService}) is likewise conditional on the same property,
 * so disabled mode performs zero outbox I/O.
 */
@Slf4j
@Service
public class IngestionService {

    private static final String OUTBOX_EVENT_TYPE = "DATA_RECORD_INGESTED";

    private final PersistenceService persistenceService;
    private final InMemoryStorageService storageService;

    @Autowired(required = false)
    private OutboxRepository outboxRepository;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Value("${app.kafka.enabled:false}")
    private boolean kafkaEnabled;

    public IngestionService(PersistenceService persistenceService, InMemoryStorageService storageService) {
        this.persistenceService = persistenceService;
        this.storageService = storageService;
    }

    @Transactional
    public DataRecord ingest(String source, String type, Map<String, Object> payload) {
        PersistedRecord persisted;
        try {
            persisted = persistenceService.saveViaJpa(source, type, payload);
        } catch (Exception e) {
            log.warn("Persistence failed for record", e);
            throw new IllegalStateException("Failed to persist record to database", e);
        }

        DataRecord record = toDataRecord(persisted, payload);
        writeOutboxEvent(persisted, record);
        saveToInMemoryStore(record);
        return record;
    }

    @Transactional
    public List<DataRecord> ingestBatch(String source, String type, List<Map<String, Object>> payloads) {
        List<PersistedRecord> persistedRecords;
        try {
            persistedRecords = persistenceService.saveBatchViaJpa(source, type, payloads);
        } catch (Exception e) {
            log.warn("Persistence failed for batch", e);
            throw new IllegalStateException("Failed to persist records to database", e);
        }

        List<DataRecord> records = new ArrayList<>(persistedRecords.size());
        for (int i = 0; i < persistedRecords.size(); i++) {
            DataRecord record = toDataRecord(persistedRecords.get(i), payloads.get(i));
            writeOutboxEvent(persistedRecords.get(i), record);
            saveToInMemoryStore(record);
            records.add(record);
        }

        return List.copyOf(records);
    }

    private void writeOutboxEvent(PersistedRecord persisted, DataRecord record) {
        if (!kafkaEnabled || outboxRepository == null) {
            return;
        }
        try {
            ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateId(String.valueOf(persisted.getId()))
                    .eventType(OUTBOX_EVENT_TYPE)
                    .payload(mapper.writeValueAsString(record))
                    .status(OutboxStatus.PENDING)
                    .retries(0)
                    .build();
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize record for outbox", e);
        }
    }

    private void saveToInMemoryStore(DataRecord record) {
        try {
            storageService.saveRecord(record);
        } catch (Exception e) {
            log.warn("In-memory store save failed for record id={} (record already persisted to DB): {}",
                    record.id(), e.getMessage());
        }
    }

    private DataRecord toDataRecord(PersistedRecord persisted, Map<String, Object> payload) {
        LocalDateTime ingestedAt = persisted.getIngestedAt() != null
                ? persisted.getIngestedAt()
                : LocalDateTime.now();

        return DataRecord.builder()
                .key(String.valueOf(persisted.getId()))
                .source(persisted.getSource())
                .type(persisted.getType())
                .payload(Map.copyOf(payload))
                .timestamp(ingestedAt.atZone(ZoneId.systemDefault()).toInstant())
                .build();
    }
}
