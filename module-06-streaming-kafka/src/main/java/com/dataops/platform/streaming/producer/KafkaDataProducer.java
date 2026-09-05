package com.dataops.platform.streaming.producer;

import com.dataops.platform.common.model.DataRecord;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka publish path for ingested records.
 *
 * <h3>Failure-handling strategy (decision record)</h3>
 *
 * Kafka is an <em>extension mechanism</em> for the platform — analytics, exports,
 * retrieval all work without it. The vision doc's principle (Phase 4.4) is
 * that "the primary ingestion path should remain understandable," and Kafka
 * is explicitly not part of that core. Accordingly:
 *
 * <ul>
 *   <li><b>Strategy: observe, don't retry, don't block.</b> Publish is fire-and-forget.
 *       Synchronous KafkaTemplate.send() failures and asynchronous send-result
 *       failures are logged at {@code ERROR} and counted via Micrometer, but
 *       the caller ({@code IngestionService}) does not see them and ingestion
 *       does not block or roll back.</li>
 *   <li><b>What this handles:</b> transient broker outages show up as
 *       {@code kafka.records.failed} counter increments and ERROR log lines
 *       with topic/key/excerpt. Operators monitoring the Prometheus endpoint
 *       ({@code /actuator/prometheus}) get an alertable signal; operators
 *       tailing logs see the failure with enough context to investigate.</li>
 *   <li><b>What this does NOT handle:</b> data loss for the failed record
 *       on the Kafka side. The record is durably persisted to JPA and the
 *       in-memory store regardless of Kafka outcome. Replay from JPA is the
 *       recovery path; this is documented as a deliberate choice rather
 *       than an oversight. If a stronger guarantee is ever needed (e.g.,
 *       "every ingested record must reach Kafka before 200 OK"), the
 *       decision should be revisited and the strategy replaced with
 *       synchronous publish + bounded retry — not patched here.</li>
 * </ul>
 *
 * <p>For the disabled case (no Kafka producer wired), see {@link NoOpKafkaProducer}.
 */
@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class KafkaDataProducer implements KafkaProducer {

    private final KafkaTemplate<String, DataRecord> kafkaTemplate;

    /**
     * Optional — present in production with the monolith's Prometheus wiring,
     * absent in standalone module-06 tests and minimal deployments. Producer
     * tolerates the absence (publish still succeeds, just no metric increments).
     */
    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    public KafkaDataProducer(KafkaTemplate<String, DataRecord> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Test/diagnostic constructor allowing the meterRegistry to be injected
     * directly rather than relying on Spring autowiring. Used by
     * {@code KafkaDataProducerTest}.
     */
    public KafkaDataProducer(KafkaTemplate<String, DataRecord> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void publish(String topic, DataRecord record) {
        try {
            kafkaTemplate.send(topic, record.getKey(), record)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Published to Kafka -> topic={}, key={}, offset={}",
                                    topic, record.getKey(), result.getRecordMetadata().offset());
                            recordOutcome("kafka.records.published", topic, "success");
                        } else {
                            log.error("Failed to publish to Kafka: topic={}, key={}, error={}",
                                    topic, record.getKey(), ex.getMessage(), ex);
                            recordOutcome("kafka.records.failed", topic, "async_error");
                        }
                    });
        } catch (Exception e) {
            // Synchronous failure from KafkaTemplate.send() (e.g., serializer bug,
            // metadata lookup failure before send completes). Same observability
            // contract as the async path.
            log.error("Synchronous Kafka publish failure: topic={}, key={}, error={}",
                    topic, record.getKey(), e.getMessage(), e);
            recordOutcome("kafka.records.failed", topic, "sync_error");
        }
    }

    private void recordOutcome(String metric, String topic, String reason) {
        if (meterRegistry != null) {
            Counter.builder(metric)
                    .description("Kafka publish outcomes for ingested records")
                    .tag("topic", topic)
                    .tag("reason", reason)
                    .register(meterRegistry)
                    .increment();
        }
    }
}
