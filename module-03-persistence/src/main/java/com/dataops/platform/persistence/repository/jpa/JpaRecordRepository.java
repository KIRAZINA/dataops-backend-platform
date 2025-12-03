package com.dataops.platform.persistence.repository.jpa;

import com.dataops.platform.persistence.entity.PersistedRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaRecordRepository extends JpaRepository<PersistedRecord, Long> {

    List<PersistedRecord> findBySourceOrderByIngestedAtDesc(String source);

    @Query("SELECT r FROM PersistedRecord r WHERE r.type = :type ORDER BY r.ingestedAt DESC")
    List<PersistedRecord> findByTypeCustom(String type);
}