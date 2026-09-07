package com.dataops.platform.streaming.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the transactional boundary of the outbox write. When
 * {@code outboxRepository.save()} is called within a {@code @Transactional}
 * method and a subsequent operation throws, the outbox INSERT is rolled back
 * alongside the rest of the transaction.
 *
 * <p>Uses H2 in-memory with schema auto-creation from Hibernate DDL.
 */
@DataJpaTest
@ActiveProfiles("test")
class OutboxTransactionalBoundaryTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @PersistenceContext
    private EntityManager em;

    private OutboxEvent validEvent(String aggregateId) {
        return OutboxEvent.builder()
                .aggregateId(aggregateId)
                .eventType("DATA_RECORD_INGESTED")
                .payload("{\"key\":\"42\",\"source\":\"api\"}")
                .status(OutboxStatus.PENDING)
                .retries(0)
                .build();
    }

    private long pendingCount() {
        return outboxRepository.findBatchForUpdate(
                OutboxStatus.PENDING, PageRequest.of(0, 100)).size();
    }

    @Test
    @DisplayName("Successful save persists the OutboxEvent row")
    @Transactional
    void successfulSavePersistsRow() {
        OutboxEvent event = validEvent("agg-1");
        outboxRepository.save(event);
        em.flush();

        assertNotNull(event.getId());
        assertEquals(1, pendingCount());
    }

    @Test
    @DisplayName("Constraint violation on outbox save throws and the transaction rolls back")
    @Transactional
    void constraintViolationRollsBackTransaction() {
        long countBefore = pendingCount();

        OutboxEvent broken = OutboxEvent.builder()
                .eventType("DATA_RECORD_INGESTED")
                .payload("{\"key\":\"42\"}")
                .status(OutboxStatus.PENDING)
                .retries(0)
                .aggregateId(null)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            outboxRepository.saveAndFlush(broken);
        });
        // The failed flush poisons the persistence context ("don't flush the
        // Session after an exception occurs"); detach everything so the
        // follow-up count runs against a clean session within the same tx.
        em.clear();

        assertEquals(countBefore, pendingCount(),
                "Constraint-violating outbox save must roll back (no partial row)");
    }

    @Test
    @DisplayName("Outbox row visible to relay query after successful save")
    @Transactional
    void outboxRowVisibleToRelayAfterSave() {
        OutboxEvent e1 = validEvent("agg-a");
        OutboxEvent e2 = validEvent("agg-b");
        outboxRepository.saveAll(java.util.List.of(e1, e2));
        em.flush();

        var results = outboxRepository.findBatchForUpdate(OutboxStatus.PENDING, PageRequest.of(0, 100));
        assertEquals(2, results.size());
    }
}
