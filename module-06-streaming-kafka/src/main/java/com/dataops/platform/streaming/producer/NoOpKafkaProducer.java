package com.dataops.platform.streaming.producer;

import com.dataops.platform.common.model.DataRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class NoOpKafkaProducer implements KafkaProducer {

    @Override
    public void publish(String topic, DataRecord record) {
        log.info("Kafka disabled -> skipping publish: topic={}, key={}", topic, record.getKey());
    }
}
