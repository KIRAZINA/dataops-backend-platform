package com.dataops.platform.streaming.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayService unit tests")
class OutboxRelayServiceTest {

    @Mock
    private OutboxRepository repository;

    @Mock
    private com.dataops.platform.streaming.producer.KafkaProducer kafkaProducer;

    private SimpleMeterRegistry meterRegistry;
    private OutboxRelayService relay;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        objectMapper = new ObjectMapper();
        relay = new OutboxRelayService(repository, kafkaProducer, meterRegistry, objectMapper);
    }

    private OutboxEvent evt(String aggregateId, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.setAggregateId(aggregateId);
        e.setEventType("RecordIngested");
        e.setPayload(payload);
        e.setStatus(OutboxStatus.PENDING);
        e.setRetries(0);
        return e;
    }

    private double failedCount() {
        Counter c = meterRegistry.find("dataops.outbox.failed").counter();
        return c == null ? 0.0 : c.count();
    }

    @Test
    @DisplayName("Successful relay: status → PUBLISHED, count unchanged, batch polled and locked")
    void successfulRelay() {
        OutboxEvent e1 = evt("agg-1", "{\"key\":\"a\"}");
        OutboxEvent e2 = evt("agg-2", "{\"key\":\"b\"}");
        List<OutboxEvent> batch = List.of(e1, e2);

        when(repository.findBatchForUpdate(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(batch);

        relay.processOutbox();

        verify(kafkaProducer, times(2)).publish(eq("dataops-raw-ingest"), any(String.class));
        assertEquals(OutboxStatus.PUBLISHED, e1.getStatus());
        assertEquals(OutboxStatus.PUBLISHED, e2.getStatus());
        assertEquals(0.0, failedCount(), 0.0001);
    }

    @Test
    @DisplayName("Kafka failure: retries incremented, remains PENDING, NOT marked FAILED")
    void kafkaFailureTriggersRetry() {
        OutboxEvent e = evt("agg-1", "{\"key\":\"a\"}");
        when(repository.findBatchForUpdate(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(e));
        doThrow(new RuntimeException("broker down")).when(kafkaProducer)
                .publish(any(String.class), any(String.class));

        relay.processOutbox();

        assertEquals(OutboxStatus.PENDING, e.getStatus(),
                "PENDING event must remain PENDING on transient failure (retry on next tick)");
        assertEquals(1, e.getRetries());
        assertEquals(0.0, failedCount(),
                "Transient failure must NOT increment dataops.outbox.failed");
    }

    @Test
    @DisplayName("Max retries reached: status → FAILED, dataops.outbox.failed metric incremented")
    void maxRetriesReachesFailed() {
        OutboxEvent e = evt("agg-1", "{\"key\":\"a\"}");
        e.setRetries(2);
        when(repository.findBatchForUpdate(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(e));
        doThrow(new RuntimeException("broker permanently down")).when(kafkaProducer)
                .publish(any(String.class), any(String.class));

        relay.processOutbox();

        assertEquals(OutboxStatus.FAILED, e.getStatus());
        assertEquals(3, e.getRetries(), "Retries incremented before max check");
        assertEquals(1.0, failedCount(), 0.0001);
    }

    @Test
    @DisplayName("Empty batch: no publisher calls, no DB writes, no metric changes")
    void emptyBatchIsNoOp() {
        when(repository.findBatchForUpdate(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of());

        relay.processOutbox();

        verify(kafkaProducer, never()).publish(any(String.class), any(String.class));
        assertEquals(0.0, failedCount(), 0.0001);
    }

    @Test
    @DisplayName("Relay polls with PENDING status and PageRequest of size 100")
    void relayUsesCorrectBatchSizeAndStatus() {
        when(repository.findBatchForUpdate(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of());
        relay.processOutbox();
        verify(repository).findBatchForUpdate(eq(OutboxStatus.PENDING), any(PageRequest.class));
    }

    @Test
    @DisplayName("Multiple failed events each increment dataops.outbox.failed metric")
    void failedEventsAreVisibleInMetric() {
        OutboxEvent e1 = evt("agg-1", "{\"key\":\"a\"}");
        e1.setRetries(2);
        OutboxEvent e2 = evt("agg-2", "{\"key\":\"b\"}");
        e2.setRetries(2);
        when(repository.findBatchForUpdate(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(e1, e2));
        doThrow(new RuntimeException("down")).when(kafkaProducer)
                .publish(any(String.class), any(String.class));

        relay.processOutbox();

        assertEquals(2.0, failedCount(), 0.0001);
        assertEquals(OutboxStatus.FAILED, e1.getStatus());
        assertEquals(OutboxStatus.FAILED, e2.getStatus());
    }

    @Test
    @DisplayName("Per-event publish failure is isolated: OK event published, bad event FAILED")
    void perEventFailureIsolation() {
        OutboxEvent ok = evt("agg-1", "{\"key\":\"ok\"}");
        ok.setRetries(0);
        OutboxEvent bad = evt("agg-2", "{\"key\":\"bad\"}");
        bad.setRetries(2);
        when(repository.findBatchForUpdate(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(ok, bad));

        org.mockito.Mockito.lenient().doAnswer(inv -> {
            String payload = inv.getArgument(1);
            if (payload.equals("{\"key\":\"bad\"}")) {
                throw new RuntimeException("bad msg");
            }
            return null;
        }).when(kafkaProducer).publish(any(String.class), any(String.class));

        relay.processOutbox();

        assertEquals(OutboxStatus.PUBLISHED, ok.getStatus());
        assertEquals(OutboxStatus.FAILED, bad.getStatus());
        assertEquals(1.0, failedCount(), 0.0001);
    }

    @Test
    @DisplayName("Retries between 1 and MAX_RETRIES keep event PENDING")
    void retriesUnderMaxKeepsPending() {
        OutboxEvent e1 = evt("agg-1", "{\"key\":\"a\"}");
        e1.setRetries(0);
        OutboxEvent e2 = evt("agg-2", "{\"key\":\"b\"}");
        e2.setRetries(1);
        when(repository.findBatchForUpdate(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(e1, e2));
        doThrow(new RuntimeException("transient")).when(kafkaProducer)
                .publish(any(String.class), any(String.class));

        relay.processOutbox();

        assertEquals(OutboxStatus.PENDING, e1.getStatus());
        assertEquals(1, e1.getRetries());
        assertEquals(OutboxStatus.PENDING, e2.getStatus());
        assertEquals(2, e2.getRetries());
        assertEquals(0.0, failedCount(), 0.0001);
    }

    @Test
    @DisplayName("Relay tolerates null MeterRegistry")
    void nullMeterRegistryIsTolerated() {
        OutboxRelayService relayNoMetrics = new OutboxRelayService(repository, kafkaProducer, null, objectMapper);
        OutboxEvent e = evt("agg-1", "{\"key\":\"a\"}");
        e.setRetries(2);
        when(repository.findBatchForUpdate(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(e));
        doThrow(new RuntimeException("down")).when(kafkaProducer)
                .publish(any(String.class), any(String.class));

        relayNoMetrics.processOutbox();

        assertEquals(OutboxStatus.FAILED, e.getStatus());
    }
}
