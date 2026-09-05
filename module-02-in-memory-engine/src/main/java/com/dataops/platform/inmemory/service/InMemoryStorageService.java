package com.dataops.platform.inmemory.service;

import com.dataops.platform.common.event.DataRecordIngestedEvent;
import com.dataops.platform.common.model.DataRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

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
 *
 * <p>Note: methods here are intentionally NOT annotated {@code @Transactional}.
 * This class holds no transactional resources; the annotation would be misleading.
 *
 * <p>Records are added only via {@link #saveRecord(DataRecord)} / {@link #saveRecords(List)},
 * which take fully-built {@link DataRecord} instances (typically produced upstream by
 * {@code IngestionService} once JPA has assigned the ID). This service does not generate keys.
 *
 * <h3>Cache invalidation</h3>
 * Both write methods evict the {@code analytics-stats} and {@code analytics-sorted} caches
 * declared in the monolith's {@code CacheConfig}. Without this, a record ingested via
 * {@code IngestionService} would not be reflected by
 * {@code AnalyticsController}/{@code AnalyticsService} until the 3-5 minute TTL elapsed —
 * reintroducing, in time-boxed form, the data-consistency bug the original engagement
 * started with. {@code allEntries = true} because a new record with any source affects
 * both the per-source cache entry AND the global (source=null) entry, and clearing
 * specific source keys is brittle against future cache-key changes.
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

    @CacheEvict(cacheNames = {"analytics-stats", "analytics-sorted"}, allEntries = true)
    public DataRecord saveRecord(DataRecord record) {
        addRecord(record);
        return record;
    }

    @CacheEvict(cacheNames = {"analytics-stats", "analytics-sorted"}, allEntries = true)
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
}
