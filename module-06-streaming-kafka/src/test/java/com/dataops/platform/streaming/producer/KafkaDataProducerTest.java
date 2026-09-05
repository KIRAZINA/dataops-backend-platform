package com.dataops.platform.streaming.producer;

import com.dataops.platform.common.model.DataRecord;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import org.apache.kafka.clients.producer.RecordMetadata;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaDataProducer unit tests")
class KafkaDataProducerTest {

    @Mock
    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, DataRecord> kafkaTemplate;

    private SimpleMeterRegistry meterRegistry;
    private KafkaDataProducer producer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        producer = new KafkaDataProducer(kafkaTemplate, meterRegistry);
    }

    private DataRecord record(String key) {
        return DataRecord.builder()
                .key(key)
                .source("api")
                .type("JSON")
                .payload(Map.of("v", 1))
                .timestamp(Instant.now())
                .build();
    }

    private double countFor(String name) {
        Counter c = meterRegistry.find(name).counter();
        return c == null ? 0.0 : c.count();
    }

    /**
     * Build a fully-stubbed {@link SendResult} with non-null metadata so the
     * producer's success log path ({@code result.getRecordMetadata().offset()})
     * doesn't NPE in the test.
     */
    private SendResult<String, DataRecord> stubSendResult() {
        SendResult<String, DataRecord> result = mock(SendResult.class);
        when(result.getRecordMetadata()).thenReturn(
                new RecordMetadata(null, 0, 0, 0L, 0L, 0, 0));
        return result;
    }

    @Test
    @DisplayName("Successful publish increments kafka.records.published and does not propagate")
    void successfulPublish() {
        // CompletableFuture.completedFuture(...) is already-done, so the
        // whenComplete callback runs synchronously on the calling thread
        // (per CompletableFuture contract: callback fires inline if the
        // future is complete at the time whenComplete is invoked).
        SendResult<String, DataRecord> result = stubSendResult();
        CompletableFuture<SendResult<String, DataRecord>> completedFuture =
                CompletableFuture.completedFuture(result);
        DataRecord record = record("k1");
        when(kafkaTemplate.send(eq("topic-x"), eq("k1"), eq(record)))
                .thenReturn(completedFuture);

        producer.publish("topic-x", record);

        // No waiting needed — callback already ran on the calling thread.
        verify(kafkaTemplate).send(eq("topic-x"), eq("k1"), eq(record));
        assertEquals(1.0, countFor("kafka.records.published"), 0.0001);
        assertEquals(0.0, countFor("kafka.records.failed"), 0.0001);
    }

    @Test
    @DisplayName("Synchronous KafkaTemplate.send() throwing is caught, counted as failed, and not propagated")
    void synchronousFailureIsCountedAndSwallowed() {
        DataRecord record = record("k2");
        when(kafkaTemplate.send(any(String.class), any(String.class), any(DataRecord.class)))
                .thenThrow(new RuntimeException("kafka broker down"));

        assertDoesNotThrow(() -> producer.publish("topic-x", record),
                "Synchronous Kafka failure must NOT propagate to caller");

        assertEquals(1.0, countFor("kafka.records.failed"), 0.0001);
    }

    @Test
    @DisplayName("Asynchronous send-result failure is counted as failed and not propagated")
    void asynchronousFailureIsCounted() {
        RuntimeException failure = new RuntimeException("kafka async failure");
        // Pre-failed future: completeExceptionally on a completed-future-shaped
        // instance. Using completedFuture() with the result null and then
        // explicitly completing exceptionally doesn't work — use a future that
        // is already failed.
        CompletableFuture<SendResult<String, DataRecord>> failedFuture =
                CompletableFuture.failedFuture(failure);
        DataRecord record = record("k3");
        when(kafkaTemplate.send(any(String.class), any(String.class), any(DataRecord.class)))
                .thenReturn(failedFuture);

        assertDoesNotThrow(() -> producer.publish("topic-x", record),
                "Async Kafka failure must NOT propagate to caller");

        // whenComplete fires synchronously on a pre-failed future — no waiting needed.
        assertEquals(1.0, countFor("kafka.records.failed"), 0.0001);
    }

    @Test
    @DisplayName("Mixed success and failure produce independent counter tags")
    void mixedOutcomesProduceIndependentCounters() {
        SendResult<String, DataRecord> result = stubSendResult();
        DataRecord ok = record("ok");
        DataRecord bad = record("bad");
        CompletableFuture<SendResult<String, DataRecord>> okFuture =
                CompletableFuture.completedFuture(result);
        CompletableFuture<SendResult<String, DataRecord>> badFuture =
                CompletableFuture.failedFuture(new RuntimeException("nope"));

        when(kafkaTemplate.send(eq("topic-x"), eq("ok"), eq(ok))).thenReturn(okFuture);
        when(kafkaTemplate.send(eq("topic-x"), eq("bad"), eq(bad))).thenReturn(badFuture);

        producer.publish("topic-x", ok);
        producer.publish("topic-x", bad);

        assertEquals(1.0, countFor("kafka.records.published"), 0.0001);
        assertEquals(1.0, countFor("kafka.records.failed"), 0.0001);
    }

    @Test
    @DisplayName("Producer works without a MeterRegistry wired (no NPE)")
    void noMeterRegistryIsTolerated() {
        KafkaDataProducer producerNoMetrics = new KafkaDataProducer(kafkaTemplate);
        // No meterRegistry passed — producer must function without metrics.
        SendResult<String, DataRecord> result = stubSendResult();
        CompletableFuture<SendResult<String, DataRecord>> completedFuture =
                CompletableFuture.completedFuture(result);
        DataRecord r = record("nom");
        when(kafkaTemplate.send(any(String.class), any(String.class), any(DataRecord.class)))
                .thenReturn(completedFuture);

        assertDoesNotThrow(() -> producerNoMetrics.publish("topic-x", r),
                "Producer must function even when MeterRegistry is not present");
        assertDoesNotThrow(() -> producerNoMetrics.publish("topic-x", r),
                "Producer must continue to function even when MeterRegistry is not present");
    }

    @Test
    @DisplayName("Counter tags include topic and reason for filtering by metric labels")
    void counterTagsIncludeTopicAndReason() {
        SendResult<String, DataRecord> result = stubSendResult();
        CompletableFuture<SendResult<String, DataRecord>> completedFuture =
                CompletableFuture.completedFuture(result);
        DataRecord r = record("tagged");
        when(kafkaTemplate.send(any(String.class), any(String.class), any(DataRecord.class)))
                .thenReturn(completedFuture);

        producer.publish("topic-orders", r);

        Counter counter = meterRegistry.find("kafka.records.published").tag("topic", "topic-orders").counter();
        assertNotNull(counter, "Counter must be registered with topic tag");
        assertEquals(1.0, counter.count(), 0.0001);
        Counter tagged = meterRegistry.find("kafka.records.published").tag("reason", "success").counter();
        assertTrue(tagged != null, "Counter must be registered with reason tag");
    }
}
