package com.dataops.platform.monolith;

import com.dataops.platform.inmemory.service.IngestionService;
import com.dataops.platform.persistence.repository.jpa.JpaRecordRepository;
import com.dataops.platform.streaming.outbox.OutboxEvent;
import com.dataops.platform.streaming.outbox.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7 happy-path proof with real beans: with {@code app.kafka.enabled=true},
 * a single {@code IngestionService.ingest} commits both the
 * {@code PersistedRecord} and its {@code OutboxEvent} row.
 *
 * <p>Status is deliberately NOT asserted: the polling relay (1s cadence, active
 * in this context because Kafka is enabled) may legitimately flip the row to
 * {@code PUBLISHED} between the ingest commit and the assertion. Row counts
 * and the aggregate linkage are stable under the relay (it never deletes
 * non-terminal rows), so those are the deterministic signals.
 */
@SpringBootTest(
        classes = DataOpsMonolithApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:outbox-happy;DB_CLOSE_DELAY=-1",
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
@DisplayName("Outbox happy path (real beans, kafka enabled)")
class OutboxHappyPathTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private JpaRecordRepository recordRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    @DisplayName("Ingest commits the record and its linked outbox row in one transaction")
    void ingestCommitsRecordAndOutboxRow() {
        long recordsBefore = recordRepository.count();
        long outboxBefore = outboxRepository.count();

        var record = ingestionService.ingest("api", "JSON", Map.of("value", 11));

        assertNotNull(record);
        assertEquals(recordsBefore + 1, recordRepository.count());
        assertEquals(outboxBefore + 1, outboxRepository.count());

        List<OutboxEvent> rows = outboxRepository.findAll().stream()
                .filter(e -> record.id().equals(e.getAggregateId()))
                .toList();
        assertEquals(1, rows.size(), "Exactly one outbox row must link to the ingested record");
        assertEquals("DATA_RECORD_INGESTED", rows.get(0).getEventType());
        assertTrue(rows.get(0).getPayload().contains("\"value\":11"));
    }
}
