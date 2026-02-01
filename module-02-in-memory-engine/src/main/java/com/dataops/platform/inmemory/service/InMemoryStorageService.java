package com.dataops.platform.inmemory.service;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.common.event.DataRecordIngestedEvent;
import com.dataops.platform.core.collection.DynamicArray;
import com.dataops.platform.core.collection.SimpleInMemoryIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InMemoryStorageService {

    private final DynamicArray<DataRecord> storage = new DynamicArray<>(1024);
    private final SimpleInMemoryIndex sourceIndex = new SimpleInMemoryIndex();
    private final SimpleInMemoryIndex typeIndex = new SimpleInMemoryIndex();
    private final Map<String, DataRecord> idIndex = new HashMap<>();

    private final ApplicationEventPublisher eventPublisher;

    private long sequence = 1L;

    public synchronized DataRecord save(String source, String type, Map<String, Object> payload) {
        log.debug("Saving new record with source: {}, type: {}", source, type);
        DataRecord record = DataRecord.builder()
                .key(String.valueOf(sequence++))
                .source(source)
                .type(type)
                .payload(Map.copyOf(payload))
                .timestamp(Instant.now())
                .build();

        long position = storage.size();
        storage.add(record);

        sourceIndex.add(source, position);
        typeIndex.add(type, position);
        idIndex.put(record.id(), record);

        eventPublisher.publishEvent(new DataRecordIngestedEvent(this, record));
        log.info("Saved record with ID: {} and key: {}", record.id(), record.getKey());

        return record;
    }

    public synchronized List<DataRecord> saveBatch(String source, String type, List<Map<String, Object>> payloads) {
        log.debug("Saving batch of {} records with source: {}, type: {}", payloads.size(), source, type);
        List<DataRecord> records = new ArrayList<>();
        for (Map<String, Object> payload : payloads) {
            DataRecord record = DataRecord.builder()
                    .key(String.valueOf(sequence++))
                    .source(source)
                    .type(type)
                    .payload(Map.copyOf(payload))
                    .timestamp(Instant.now())
                    .build();

            long position = storage.size();
            storage.add(record);

            sourceIndex.add(source, position);
            typeIndex.add(type, position);
            idIndex.put(record.id(), record);

            eventPublisher.publishEvent(new DataRecordIngestedEvent(this, record));
            records.add(record);
        }
        log.info("Saved batch of {} records with source: {}", records.size(), source);
        return records;
    }

    public DataRecord findById(String id) {
        log.debug("Searching for record with ID: {}", id);
        DataRecord record = idIndex.get(id);
        if (record != null) {
            log.debug("Found record with ID: {}", id);
        } else {
            log.warn("Record with ID: {} not found", id);
        }
        return record;
    }

    public List<DataRecord> findBySource(String source) {
        log.debug("Finding records by source: {}", source);
        List<DataRecord> result = getRecordsByIndex(sourceIndex, source);
        log.debug("Found {} records by source: {}", result.size(), source);
        return result;
    }

    public List<DataRecord> findByType(String type) {
        log.debug("Finding records by type: {}", type);
        List<DataRecord> result = getRecordsByIndex(typeIndex, type);
        log.debug("Found {} records by type: {}", result.size(), type);
        return result;
    }

    public List<DataRecord> findAllRecords() {
        log.debug("Retrieving all records, current count: {}", storage.size());
        List<DataRecord> snapshot = new ArrayList<>(storage.size());
        for (int i = 0; i < storage.size(); i++) {
            snapshot.add(storage.get(i));
        }
        log.debug("Retrieved {} records", snapshot.size());
        return Collections.unmodifiableList(snapshot);
    }

    public synchronized void removeById(String id) {
        log.debug("Removing record with ID: {}", id);
        DataRecord record = idIndex.get(id);
        if (record != null) {
            // Find the record position in storage and remove references from indexes
            for (int i = 0; i < storage.size(); i++) {
                if (storage.get(i).id().equals(id)) {
                    // Remove from source and type indexes
                    sourceIndex.remove(record.getSource(), (long)i);
                    typeIndex.remove(record.getType(), (long)i);
                    break;
                }
            }
            idIndex.remove(id);
            log.info("Removed record with ID: {}", id);
        } else {
            log.warn("Attempted to remove non-existent record with ID: {}", id);
        }
    }

    public synchronized void clear() {
        log.info("Clearing all records from storage");
        storage.clear();
        sourceIndex.clear();
        typeIndex.clear();
        idIndex.clear();
        log.info("Cleared all records from storage");
    }

    private List<DataRecord> getRecordsByIndex(SimpleInMemoryIndex index, String key) {
        List<DataRecord> result = new ArrayList<>();
        for (Long pos : index.get(key)) {
            result.add(storage.get(pos.intValue()));
        }
        return List.copyOf(result); // immutable
    }

    public int size() {
        return storage.size();
    }
}