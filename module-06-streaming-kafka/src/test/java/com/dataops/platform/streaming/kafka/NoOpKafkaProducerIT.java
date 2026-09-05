package com.dataops.platform.streaming.kafka;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.streaming.producer.KafkaDataProducer;
import com.dataops.platform.streaming.producer.KafkaProducer;
import com.dataops.platform.streaming.producer.NoOpKafkaProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {
                KafkaDataProducer.class,
                NoOpKafkaProducer.class
        },
        properties = {
                "app.kafka.enabled=false"
        })
@DisplayName("NoOpKafkaProducer is active when kafka is disabled")
class NoOpKafkaProducerIT {

    @Autowired
    KafkaProducer kafkaProducer;

    @Test
    void shouldUseNoOpProducerWhenDisabled() {
        assertTrue(kafkaProducer instanceof NoOpKafkaProducer,
                "Expected NoOpKafkaProducer to be active when app.kafka.enabled=false");
    }
}
