package com.dataops.platform.persistence.service;

import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.repository.jdbc.JdbcRecordRepository;
import com.dataops.platform.persistence.repository.jpa.JpaRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PersistenceService {

    private final JdbcRecordRepository jdbcRepo;
    private final JpaRecordRepository jpaRepo;

    public PersistedRecord saveViaJdbc(String source, String type, Map<String, Object> payload) {
        PersistedRecord record = PersistedRecord.builder()
                .source(source)
                .type(type)
                .ingestedAt(LocalDateTime.now())
                .payload(payload)
                .build();
        return jdbcRepo.save(record);
    }

    public PersistedRecord saveViaJpa(String source, String type, Map<String, Object> payload) {
        PersistedRecord record = PersistedRecord.builder()
                .source(source)
                .type(type)
                .ingestedAt(LocalDateTime.now())
                .payload(payload)
                .build();
        return jpaRepo.save(record);
    }

    public List<PersistedRecord> findBySource(String source) {
        return jpaRepo.findBySourceOrderByIngestedAtDesc(source);
    }

    public List<PersistedRecord> findByType(String type) {
        return jpaRepo.findByTypeCustom(type);
    }

    public long count() {
        return jpaRepo.count();
    }
}