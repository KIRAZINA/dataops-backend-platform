package com.dataops.platform.inmemory.service;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.service.PersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final PersistenceService persistenceService;
    private final InMemoryStorageService storageService;

    public DataRecord ingest(String source, String type, Map<String, Object> payload) {
        PersistedRecord persisted;
        try {
            persisted = persistenceService.saveViaJpa(source, type, payload);
        } catch (Exception e) {
            log.warn("Persistence failed for record", e);
            throw new IllegalStateException("Failed to persist record to database", e);
        }

        DataRecord record = toDataRecord(persisted, payload);
        saveToInMemoryStore(record);
        return record;
    }

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
            saveToInMemoryStore(record);
            records.add(record);
        }

        return List.copyOf(records);
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
