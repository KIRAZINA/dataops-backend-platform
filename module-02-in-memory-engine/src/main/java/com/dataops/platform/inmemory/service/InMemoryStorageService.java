package com.dataops.platform.inmemory.service;

import com.dataops.platform.common.event.DataRecordIngestedEvent;
import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.core.collection.DynamicArray;
import com.dataops.platform.core.collection.SimpleInMemoryIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory storage service for managing data records.
 * Provides O(1) lookups via custom indexes and supports pagination.
 * All write operations are transactional.
 */
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

    /**
     * Save a single record with transaction support.
     * Publishes DataRecordIngestedEvent upon successful save.
     *
     * @param source the source of the record
     * @param type the type of the record
     * @param payload the record payload
     * @return the saved record with generated ID
     */
    @Transactional
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

    /**
     * Save a batch of records with transaction support.
     * All-or-nothing semantics: either all records are saved or transaction rolls back.
     *
     * @param source the source of all records
     * @param type the type of all records
     * @param payloads list of record payloads to save
     * @return list of saved records with generated IDs
     */
    @Transactional
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

    public synchronized DataRecord findById(String id) {
        log.debug("Searching for record with ID: {}", id);
        DataRecord record = idIndex.get(id);
        if (record != null) {
            log.debug("Found record with ID: {}", id);
        } else {
            log.warn("Record with ID: {} not found", id);
        }
        return record;
    }

    public synchronized List<DataRecord> findBySource(String source) {
        log.debug("Finding records by source: {}", source);
        List<DataRecord> result = getRecordsByIndex(sourceIndex, source);
        log.debug("Found {} records by source: {}", result.size(), source);
        return result;
    }

    public synchronized List<DataRecord> findByType(String type) {
        log.debug("Finding records by type: {}", type);
        List<DataRecord> result = getRecordsByIndex(typeIndex, type);
        log.debug("Found {} records by type: {}", result.size(), type);
        return result;
    }

    public synchronized List<DataRecord> findAllRecords() {
        log.debug("Retrieving all records, current count: {}", storage.size());
        List<DataRecord> snapshot = snapshotRecords();
        log.debug("Retrieved {} records", snapshot.size());
        return Collections.unmodifiableList(snapshot);
    }

    /**
     * Find all records with pagination support
     *
     * @param page zero-indexed page number
     * @param pageSize number of records per page
     * @return list of records for the given page
     */
    public synchronized List<DataRecord> findAllRecordsPaginated(int page, int pageSize) {
        log.debug("Retrieving records with pagination: page={}, pageSize={}", page, pageSize);
        validatePagination(page, pageSize);

        int start = page * pageSize;
        int end = Math.min(start + pageSize, storage.size());

        if (start >= storage.size()) {
            log.debug("Requested page {} is beyond available records", page);
            return Collections.emptyList();
        }

        List<DataRecord> result = new ArrayList<>();
        for (int i = start; i < end; i++) {
            result.add(storage.get(i));
        }
        log.debug("Retrieved {} records for page {}", result.size(), page);
        return Collections.unmodifiableList(result);
    }

    /**
     * Find records by source with pagination support
     *
     * @param source the source filter
     * @param page zero-indexed page number
     * @param pageSize number of records per page
     * @return list of records matching source for the given page
     */
    public synchronized List<DataRecord> findBySourcePaginated(String source, int page, int pageSize) {
        log.debug("Finding paginated records by source: {}, page={}, pageSize={}", source, page, pageSize);
        validatePagination(page, pageSize);

        List<DataRecord> allRecords = getRecordsByIndex(sourceIndex, source);
        int start = page * pageSize;
        int end = Math.min(start + pageSize, allRecords.size());

        if (start >= allRecords.size()) {
            log.debug("Requested page {} is beyond available records for source: {}", page, source);
            return Collections.emptyList();
        }

        List<DataRecord> result = new ArrayList<>(allRecords.subList(start, end));
        log.debug("Retrieved {} records for page {} by source: {}", result.size(), page, source);
        return Collections.unmodifiableList(result);
    }

    /**
     * Get total record count
     *
     * @return total number of records in storage
     */
    public synchronized long getTotalRecordCount() {
        return storage.size();
    }

    /**
     * Get total record count filtered by source
     *
     * @param source the source filter
     * @return total number of records matching the source
     */
    public synchronized long getTotalRecordCountBySource(String source) {
        return getRecordsByIndex(sourceIndex, source).size();
    }

    /**
     * Remove a record by ID with transaction support.
     *
     * @param id the record ID to remove
     */
    @Transactional
    public synchronized void removeById(String id) {
        log.debug("Removing record with ID: {}", id);
        DataRecord record = idIndex.get(id);
        if (record == null) {
            log.warn("Attempted to remove non-existent record with ID: {}", id);
            return;
        }

        List<DataRecord> remainingRecords = new ArrayList<>(Math.max(0, storage.size() - 1));
        boolean removed = false;
        for (DataRecord current : snapshotRecords()) {
            if (current.id().equals(id)) {
                removed = true;
                continue;
            }
            remainingRecords.add(current);
        }

        if (!removed) {
            log.warn("Record with ID: {} was missing from storage snapshot during removal", id);
            return;
        }

        rebuildState(remainingRecords);
        log.info("Removed record with ID: {}", id);
    }

    /**
     * Clear all records from storage with transaction support.
     */
    @Transactional
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
        return List.copyOf(result);
    }

    public synchronized int size() {
        return storage.size();
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 0) {
            throw new IllegalArgumentException("Page number must be non-negative");
        }
        if (pageSize < 1 || pageSize > 500) {
            throw new IllegalArgumentException("Page size must be between 1 and 500");
        }
    }

    private List<DataRecord> snapshotRecords() {
        List<DataRecord> snapshot = new ArrayList<>(storage.size());
        for (int i = 0; i < storage.size(); i++) {
            snapshot.add(storage.get(i));
        }
        return snapshot;
    }

    private void rebuildState(List<DataRecord> records) {
        storage.clear();
        sourceIndex.clear();
        typeIndex.clear();
        idIndex.clear();

        for (int i = 0; i < records.size(); i++) {
            DataRecord current = records.get(i);
            storage.add(current);
            sourceIndex.add(current.getSource(), (long) i);
            typeIndex.add(current.getType(), (long) i);
            idIndex.put(current.id(), current);
        }
    }
}
