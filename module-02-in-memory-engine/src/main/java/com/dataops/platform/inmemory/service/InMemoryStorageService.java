package com.dataops.platform.inmemory.service;

import com.dataops.platform.common.event.DataRecordIngestedEvent;
import com.dataops.platform.common.model.DataRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory storage service for managing data records.
 * Uses built-in concurrent collections to provide thread-safe cache semantics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InMemoryStorageService {

    private final CopyOnWriteArrayList<DataRecord> records = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, CopyOnWriteArrayList<DataRecord>> sourceIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArrayList<DataRecord>> typeIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DataRecord> idIndex = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher eventPublisher;

    private long sequence = 1L;

    @Transactional
    public DataRecord save(String source, String type, Map<String, Object> payload) {
        log.debug("Saving new record with source: {}, type: {}", source, type);
        DataRecord record = buildRecord(source, type, payload);
        addRecord(record);
        return record;
    }

    @Transactional
    public List<DataRecord> saveBatch(String source, String type, List<Map<String, Object>> payloads) {
        log.debug("Saving batch of {} records with source: {}, type: {}", payloads.size(), source, type);
        List<DataRecord> records = new ArrayList<>();
        for (Map<String, Object> payload : payloads) {
            DataRecord record = buildRecord(source, type, payload);
            addRecord(record);
            records.add(record);
        }
        log.info("Saved batch of {} records with source: {}", records.size(), source);
        return records;
    }

    public DataRecord saveRecord(DataRecord record) {
        addRecord(record);
        return record;
    }

    public List<DataRecord> saveRecords(List<DataRecord> recordsToSave) {
        for (DataRecord record : recordsToSave) {
            addRecord(record);
        }
        return recordsToSave;
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
        List<DataRecord> result = sourceIndex.getOrDefault(source, new CopyOnWriteArrayList<>());
        log.debug("Found {} records by source: {}", result.size(), source);
        return List.copyOf(result);
    }

    public List<DataRecord> findByType(String type) {
        log.debug("Finding records by type: {}", type);
        List<DataRecord> result = typeIndex.getOrDefault(type, new CopyOnWriteArrayList<>());
        log.debug("Found {} records by type: {}", result.size(), type);
        return List.copyOf(result);
    }

    public List<DataRecord> findAllRecords() {
        log.debug("Retrieving all records, current count: {}", records.size());
        return List.copyOf(records);
    }

    public List<DataRecord> findAllRecordsPaginated(int page, int pageSize) {
        log.debug("Retrieving records with pagination: page={}, pageSize={}", page, pageSize);
        validatePagination(page, pageSize);

        List<DataRecord> snapshot = List.copyOf(records);
        int start = page * pageSize;
        int end = Math.min(start + pageSize, snapshot.size());

        if (start >= snapshot.size()) {
            log.debug("Requested page {} is beyond available records", page);
            return Collections.emptyList();
        }

        List<DataRecord> result = new ArrayList<>(snapshot.subList(start, end));
        log.debug("Retrieved {} records for page {}", result.size(), page);
        return Collections.unmodifiableList(result);
    }

    public List<DataRecord> findBySourcePaginated(String source, int page, int pageSize) {
        log.debug("Finding paginated records by source: {}, page={}, pageSize={}", source, page, pageSize);
        validatePagination(page, pageSize);

        List<DataRecord> snapshot = List.copyOf(sourceIndex.getOrDefault(source, new CopyOnWriteArrayList<>()));
        int start = page * pageSize;
        int end = Math.min(start + pageSize, snapshot.size());

        if (start >= snapshot.size()) {
            log.debug("Requested page {} is beyond available records for source: {}", page, source);
            return Collections.emptyList();
        }

        List<DataRecord> result = new ArrayList<>(snapshot.subList(start, end));
        log.debug("Retrieved {} records for page {} by source: {}", result.size(), page, source);
        return Collections.unmodifiableList(result);
    }

    public long getTotalRecordCount() {
        return records.size();
    }

    public long getTotalRecordCountBySource(String source) {
        return sourceIndex.getOrDefault(source, new CopyOnWriteArrayList<>()).size();
    }

    @Transactional
    public void removeById(String id) {
        log.debug("Removing record with ID: {}", id);
        DataRecord record = idIndex.remove(id);
        if (record == null) {
            log.warn("Attempted to remove non-existent record with ID: {}", id);
            return;
        }

        records.remove(record);
        sourceIndex.computeIfPresent(record.getSource(), (key, list) -> {
            list.remove(record);
            return list.isEmpty() ? null : list;
        });
        typeIndex.computeIfPresent(record.getType(), (key, list) -> {
            list.remove(record);
            return list.isEmpty() ? null : list;
        });

        log.info("Removed record with ID: {}", id);
    }

    @Transactional
    public void clear() {
        log.info("Clearing all records from storage");
        records.clear();
        sourceIndex.clear();
        typeIndex.clear();
        idIndex.clear();
        log.info("Cleared all records from storage");
    }

    public int size() {
        return records.size();
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 0) {
            throw new IllegalArgumentException("Page number must be non-negative");
        }
        if (pageSize < 1 || pageSize > 500) {
            throw new IllegalArgumentException("Page size must be between 1 and 500");
        }
    }

    private void addRecord(DataRecord record) {
        records.add(record);
        idIndex.put(record.id(), record);
        sourceIndex.computeIfAbsent(record.getSource(), key -> new CopyOnWriteArrayList<>()).add(record);
        typeIndex.computeIfAbsent(record.getType(), key -> new CopyOnWriteArrayList<>()).add(record);
        eventPublisher.publishEvent(new DataRecordIngestedEvent(this, record));
        log.info("Saved record with ID: {} and key: {}", record.id(), record.getKey());
    }

    private DataRecord buildRecord(String source, String type, Map<String, Object> payload) {
        return DataRecord.builder()
                .key(String.valueOf(sequence++))
                .source(source)
                .type(type)
                .payload(Map.copyOf(payload))
                .timestamp(Instant.now())
                .build();
    }
}
