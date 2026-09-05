package com.dataops.platform.persistence.service;

import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.repository.jpa.JpaRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PersistenceService {

    private final JpaRecordRepository jpaRepo;

    @Transactional
    @CacheEvict(cacheNames = "records-by-source", key = "#source")
    public PersistedRecord saveViaJpa(String source, String type, Map<String, Object> payload) {
        PersistedRecord record = buildRecord(source, type, payload);
        return jpaRepo.save(record);
    }

    @Transactional
    @CacheEvict(cacheNames = "records-by-source", key = "#source")
    public List<PersistedRecord> saveBatchViaJpa(String source, String type, List<Map<String, Object>> payloads) {
        List<PersistedRecord> persistedRecords = new ArrayList<>(payloads.size());
        for (Map<String, Object> payload : payloads) {
            persistedRecords.add(jpaRepo.save(buildRecord(source, type, payload)));
        }
        return persistedRecords;
    }

    @Transactional(readOnly = true)
    public List<PersistedRecord> findAll() {
        return jpaRepo.findAll();
    }

    /**
     * Paged lookup that pushes skip/limit down to the database — no longer loads
     * the full record set into memory before slicing. Use this for paginated
     * list endpoints.
     */
    @Transactional(readOnly = true)
    public Page<PersistedRecord> findAllPaged(Pageable pageable) {
        return jpaRepo.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<PersistedRecord> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepo.findById(id);
    }

    @Transactional(readOnly = true)
    public List<PersistedRecord> findBySource(String source) {
        return jpaRepo.findBySourceOrderByIngestedAtDesc(source);
    }

    /**
     * Paged variant of {@link #findBySource(String)} — derives the
     * {@code findBySourceOrderByIngestedAtDesc(String, Pageable)} query from
     * Spring Data's derived-query rules. Use this for the paginated
     * {@code /api/v1/records/by-source} endpoint.
     *
     * <p>Cached under {@code records-by-source} with a 10-minute TTL and 10k-entry
     * cap (see {@code CacheConfig}). Caching the full list per source is the
     * right granularity: pagination is applied after the cache lookup, so a
     * page-1 hit returns instantly even under heavy traffic.
     */
    @Cacheable(cacheNames = "records-by-source", key = "#source")
    @Transactional(readOnly = true)
    public Page<PersistedRecord> findBySourcePaged(String source, Pageable pageable) {
        return jpaRepo.findBySourceOrderByIngestedAtDesc(source, pageable);
    }

    @Transactional(readOnly = true)
    public List<PersistedRecord> findByType(String type) {
        return jpaRepo.findByTypeCustom(type);
    }

    @Transactional(readOnly = true)
    public long count() {
        return jpaRepo.count();
    }

    private PersistedRecord buildRecord(String source, String type, Map<String, Object> payload) {
        return PersistedRecord.builder()
                .source(source)
                .type(type)
                .ingestedAt(LocalDateTime.now())
                .payload(Map.copyOf(payload))
                .build();
    }
}
