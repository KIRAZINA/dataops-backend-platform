package com.dataops.platform.streaming.outbox;

import com.dataops.platform.streaming.producer.KafkaProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polling relay that drains the {@code outbox_event} table and forwards each
 * PENDING row to Kafka.
 *
 * <h3>Failure handling</h3>
 * <ul>
 *   <li>Successful publish -&gt; row marked {@code PUBLISHED} (terminal).</li>
 *   <li>Failed publish + retries &lt; {@code MAX_RETRIES} -&gt; row stays
 *       {@code PENDING}, {@code retries} incremented.</li>
 *   <li>Failed publish + retries &gt;= {@code MAX_RETRIES} -&gt; row marked
 *       {@code FAILED} (terminal for the relay; operator-driven recovery is
 *       out of scope per Phase 7 §5).</li>
 * </ul>
 *
 * <p>Each batch is processed inside a single {@code @Transactional} so the
 * SELECT FOR UPDATE locks and the subsequent status updates commit together.
 * The pessimistic lock on {@link OutboxRepository#findBatchForUpdate}
 * prevents two concurrent relay instances from double-publishing the same row.
 *
 * <h3>Disabling</h3>
 * The service is gated on {@code app.kafka.enabled=true}. When the property
 * is {@code false} (the default in dev/test) the bean isn't wired and the
 * scheduled poll never fires — {@code NoOpKafkaProducer} handles the
 * downstream side, while {@code IngestionService} skips the outbox write
 * entirely (its {@code OutboxRepository} dependency is also absent).
 */
@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Slf4j
public class OutboxRelayService {

    private static final int MAX_RETRIES = 3;
    private static final int BATCH_SIZE = 100;
    private static final String RAW_INGEST_TOPIC = "dataops-raw-ingest";

    private OutboxRepository outboxRepository;
    private KafkaProducer kafkaProducer;

    /**
     * Required by CGLIB proxying for {@code @Transactional}. Spring subclassing
     * needs a no-arg constructor; dependencies are injected via
     * {@link #OutboxRelayService(OutboxRepository, KafkaProducer)}.
     */
    protected OutboxRelayService() {
    }

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    public OutboxRelayService(OutboxRepository outboxRepository, KafkaProducer kafkaProducer) {
        this.outboxRepository = outboxRepository;
        this.kafkaProducer = kafkaProducer;
    }

    /**
     * Full constructor for testing / explicit wiring.
     */
    public OutboxRelayService(OutboxRepository outboxRepository, KafkaProducer kafkaProducer,
                              MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaProducer = kafkaProducer;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Pull the next batch of PENDING rows, publish each, and update status.
     * Public for direct invocation from tests.
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> events = outboxRepository.findBatchForUpdate(
                OutboxStatus.PENDING, PageRequest.of(0, BATCH_SIZE));
        if (events.isEmpty()) {
            return;
        }
        log.debug("Outbox relay picked up {} PENDING events", events.size());

        for (OutboxEvent event : events) {
            try {
                publish(event);
                event.setStatus(OutboxStatus.PUBLISHED);
            } catch (Exception e) {
                int newRetries = event.getRetries() + 1;
                event.setRetries(newRetries);
                if (newRetries >= MAX_RETRIES) {
                    event.setStatus(OutboxStatus.FAILED);
                    incrementFailureMetric(event);
                    log.error("Outbox event id={} aggregate={} type={} FAILED after {} retries",
                            event.getId(), event.getAggregateId(), event.getEventType(),
                            MAX_RETRIES, e);
                } else {
                    log.warn("Outbox event id={} aggregate={} publish failed (attempt {}/{}): {}",
                            event.getId(), event.getAggregateId(),
                            newRetries, MAX_RETRIES, e.getMessage());
                }
            }
        }
        // Spring's @Transactional flushes dirty entities at method exit.
    }

    /**
     * Trivial retention cleanup (Phase 7 §5: deliberately minimal, no UI/API).
     * Deletes terminal {@code PUBLISHED} rows older than 7 days so the outbox
     * table stays bounded. Runs hourly; {@code FAILED} rows are never
     * auto-deleted — they await operator review.
     */
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void purgePublishedEvents() {
        outboxRepository.deleteOlderThan(
                OutboxStatus.PUBLISHED, java.time.LocalDateTime.now().minusDays(7));
    }

    /**
     * Publish a single event to Kafka. Extracted so the test suite can mock
     * or assert on the publish call directly. The {@link KafkaProducer}
     * contract is async-fire-and-forget by design; here we delegate to it
     * without awaiting, which matches {@code KafkaDataProducer.publish()}'s
     * semantics. The status flip to PUBLISHED is best-effort: the producer's
     * async failure path is logged but doesn't surface here — the relay
     * treats it as success in this version. (See Phase 7 §5: stronger
     * guarantees are explicitly deferred.)
     */
    private void publish(OutboxEvent event) {
        kafkaProducer.publish(RAW_INGEST_TOPIC, event.getPayload());
    }

    private void incrementFailureMetric(OutboxEvent event) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("dataops.outbox.failed")
                .description("Outbox events that exhausted retries and moved to FAILED status")
                .tag("eventType", event.getEventType())
                .register(meterRegistry)
                .increment();
    }
}
