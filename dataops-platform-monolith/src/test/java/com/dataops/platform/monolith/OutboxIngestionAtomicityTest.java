package com.dataops.platform.monolith;

import com.dataops.platform.inmemory.service.IngestionService;
import com.dataops.platform.persistence.repository.jpa.JpaRecordRepository;
import com.dataops.platform.streaming.outbox.OutboxEvent;
import com.dataops.platform.streaming.outbox.OutboxRepository;
import com.dataops.platform.streaming.outbox.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7 required test 1 (transactional boundary) at the service level.
 *
 * <p>The {@code OutboxRepository} is replaced with a Mockito mock so the
 * outbox write can be forced to fail deterministically. When it throws, the
 * {@code @Transactional} on {@code IngestionService.ingest} must roll the
 * already-executed {@code PersistedRecord} insert back: the record count is
 * unchanged afterwards. This is the core guarantee of the transactional
 * outbox pattern.
 *
 * <p>The scheduled relay is neutralised by stubbing the batch poll to an
 * empty list (the mock replaces the real repository for the relay too).
 */
@SpringBootTest(
        classes = DataOpsMonolithApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:outbox-atomicity;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.format_sql=false",
                "spring.flyway.enabled=false",
                "app.kafka.enabled=true",
                "spring.kafka.bootstrap-servers=localhost:19092",
                "API_KEY=test-key"
        })
@DisplayName("Outbox ingestion atomicity (mocked outbox repository)")
class OutboxIngestionAtomicityTest {

    @MockBean
    private OutboxRepository outboxRepository;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private JpaRecordRepository recordRepository;

    @BeforeEach
    void stubRelayPoll() {
        when(outboxRepository.findBatchForUpdate(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("Outbox save failure rolls back the PersistedRecord insert")
    void outboxFailureRollsBackRecord() {
        when(outboxRepository.save(any(OutboxEvent.class)))
                .thenThrow(new RuntimeException("outbox down"));

        long recordsBefore = recordRepository.count();

        assertThrows(RuntimeException.class,
                () -> ingestionService.ingest("api", "JSON", Map.of("value", 42)));

        assertEquals(recordsBefore, recordRepository.count(),
                "Forced OutboxRepository.save() failure must roll back the PersistedRecord insert");
    }

    @Test
    @DisplayName("Happy path writes exactly one PENDING outbox row for the persisted record")
    void happyPathWritesPendingOutboxRow() {
        when(outboxRepository.save(any(OutboxEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        long recordsBefore = recordRepository.count();

        var record = ingestionService.ingest("api", "JSON", Map.of("value", 7));

        assertNotNull(record);
        assertEquals(recordsBefore + 1, recordRepository.count());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getRetries());
        assertEquals("DATA_RECORD_INGESTED", event.getEventType());
        assertEquals(record.id(), event.getAggregateId());
        assertTrue(event.getPayload().contains("\"value\":7"));
    }

    @Test
    @DisplayName("Batch outbox failure rolls back all PersistedRecord inserts")
    void batchOutboxFailureRollsBackAllRecords() {
        when(outboxRepository.save(any(OutboxEvent.class)))
                .thenThrow(new RuntimeException("outbox down"));

        long recordsBefore = recordRepository.count();

        assertThrows(RuntimeException.class, () -> ingestionService.ingestBatch(
                "api", "CSV", List.of(Map.of("id", 1), Map.of("id", 2))));

        assertEquals(recordsBefore, recordRepository.count(),
                "Batch: forced outbox failure must roll back every PersistedRecord insert");
    }
}
