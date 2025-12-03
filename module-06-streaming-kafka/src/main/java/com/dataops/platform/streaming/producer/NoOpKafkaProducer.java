// src/main/java/com/dataops/platform/streaming/producer/NoOpKafkaProducer.java
package com.dataops.platform.streaming.producer;

import com.dataops.platform.common.model.DataRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!kafka")
@Slf4j
public class NoOpKafkaProducer implements KafkaProducer {

    @Override
    public void publish(String topic, DataRecord record) {
        log.info("Kafka disabled → skipping publish: key={}", record.getKey());
    }
}