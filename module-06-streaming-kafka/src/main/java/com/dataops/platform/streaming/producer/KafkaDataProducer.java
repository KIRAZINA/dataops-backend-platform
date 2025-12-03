// src/main/java/com/dataops/platform/streaming/producer/KafkaDataProducer.java
package com.dataops.platform.streaming.producer;

import com.dataops.platform.common.model.DataRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaDataProducer {

    private final KafkaTemplate<String, DataRecord> kafkaTemplate;

    public void publish(String topic, DataRecord record) {
        try {
            kafkaTemplate.send(topic, record.getKey(), record)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Published to Kafka → topic={}, key={}, offset={}",
                                    topic, record.getKey(), result.getRecordMetadata().offset());
                        } else {
                            log.warn("Failed to publish to Kafka: {}", ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("Kafka is not available. Record not published: {}", e.getMessage());
        }
    }
}