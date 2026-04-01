package com.dataops.platform.streaming.kafka;

import com.dataops.platform.common.event.DataRecordIngestedEvent;
import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.streaming.producer.KafkaProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.verify;

@DisplayName("KafkaRecordPublisher Tests")
@ExtendWith(MockitoExtension.class)
class KafkaRecordPublisherTest {

    @Mock
    private KafkaProducer kafkaProducer;

    @Test
    @DisplayName("Should delegate ingested records to the configured Kafka producer")
    void shouldDelegateEventToProducer() {
        KafkaRecordPublisher publisher = new KafkaRecordPublisher(kafkaProducer);
        DataRecord record = DataRecord.builder()
                .key("1")
                .source("api")
                .type("JSON")
                .payload(Map.of("value", 42))
                .timestamp(Instant.now())
                .build();

        publisher.handleRecordIngested(new DataRecordIngestedEvent(this, record));

        verify(kafkaProducer).publish("dataops-raw-ingest", record);
    }
}
