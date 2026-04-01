package com.dataops.platform.persistence.service;

import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.repository.jdbc.JdbcRecordRepository;
import com.dataops.platform.persistence.repository.jpa.JpaRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PersistenceService {

    private final JdbcRecordRepository jdbcRepo;
    private final JpaRecordRepository jpaRepo;

    @Transactional
    public PersistedRecord saveViaJdbc(String source, String type, Map<String, Object> payload) {
        PersistedRecord record = buildRecord(source, type, payload);
        return jdbcRepo.save(record);
    }

    @Transactional
    public PersistedRecord saveViaJpa(String source, String type, Map<String, Object> payload) {
        PersistedRecord record = buildRecord(source, type, payload);
        return jpaRepo.save(record);
    }

    @Transactional
    public List<PersistedRecord> saveBatchViaJpa(String source, String type, List<Map<String, Object>> payloads) {
        List<PersistedRecord> persistedRecords = new ArrayList<>(payloads.size());
        for (Map<String, Object> payload : payloads) {
            persistedRecords.add(jpaRepo.save(buildRecord(source, type, payload)));
        }
        return persistedRecords;
    }

    @Transactional(readOnly = true)
    public List<PersistedRecord> findBySource(String source) {
        return jpaRepo.findBySourceOrderByIngestedAtDesc(source);
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
