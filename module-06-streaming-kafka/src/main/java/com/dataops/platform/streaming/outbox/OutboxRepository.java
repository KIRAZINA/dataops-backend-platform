package com.dataops.platform.streaming.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Pull the next batch of PENDING events in FIFO order, with a
     * {@code SELECT ... FOR UPDATE} lock so two concurrent relay instances
     * can't double-publish the same row.
     *
     * <p>{@link Pageable} is used purely as a {@code limit} carrier — the
     * relay doesn't page through results, it just wants the oldest N.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status ORDER BY e.createdAt ASC")
    List<OutboxEvent> findBatchForUpdate(@Param("status") OutboxStatus status, Pageable pageable);

    /**
     * Bulk-delete terminal events older than the cutoff. Used by the cleanup
     * task; intentionally not gated by status so callers pass the status they
     * want to clean (typically {@link OutboxStatus#PUBLISHED}).
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = :status AND e.createdAt < :cutoff")
    int deleteOlderThan(@Param("status") OutboxStatus status, @Param("cutoff") LocalDateTime cutoff);
}
