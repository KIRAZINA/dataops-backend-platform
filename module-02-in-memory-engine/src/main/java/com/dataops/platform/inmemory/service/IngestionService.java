package com.dataops.platform.inmemory.service;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.service.PersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

    public DataRecord ingest(String source, String type, Map<String, Object> payload) {
        PersistedRecord persisted;
        try {
            persisted = persistenceService.saveViaJpa(source, type, payload);
        } catch (Exception e) {
            log.warn("Persistence failed for record", e);
            throw new IllegalStateException("Failed to persist record to database", e);
        }

        return toDataRecord(persisted, payload);
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
            records.add(toDataRecord(persistedRecords.get(i), payloads.get(i)));
        }

        return List.copyOf(records);
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
