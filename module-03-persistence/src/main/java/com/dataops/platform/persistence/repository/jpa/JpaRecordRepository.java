package com.dataops.platform.persistence.repository.jpa;

import com.dataops.platform.persistence.entity.PersistedRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaRecordRepository extends JpaRepository<PersistedRecord, Long> {

    List<PersistedRecord> findBySourceOrderByIngestedAtDesc(String source);

    /**
     * Paged variant of {@link #findBySourceOrderByIngestedAtDesc(String)}. Spring
     * Data doesn't derive a Pageable overload from the single-arg method name,
     * so it's declared explicitly here. Pagination + ordering both happen in SQL.
     */
    Page<PersistedRecord> findBySourceOrderByIngestedAtDesc(String source, Pageable pageable);

    @Query("SELECT r FROM PersistedRecord r WHERE r.type = :type ORDER BY r.ingestedAt DESC")
    List<PersistedRecord> findByTypeCustom(String type);
}