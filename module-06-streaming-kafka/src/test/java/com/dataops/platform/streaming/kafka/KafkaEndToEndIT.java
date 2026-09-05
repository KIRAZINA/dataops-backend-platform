package com.dataops.platform.streaming.kafka;

import com.dataops.platform.common.event.DataRecordIngestedEvent;
import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.streaming.producer.KafkaDataProducer;
import com.dataops.platform.streaming.producer.KafkaProducer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {
                KafkaDataProducer.class,
                KafkaRecordPublisher.class,
                KafkaEndToEndIT.Cfg.class
        },
        properties = {
                "spring.kafka.bootstrap-servers=${spring.kafka.bootstrap-servers}",
                "app.kafka.enabled=true"
        })
@Testcontainers
@DisplayName("Kafka end-to-end event publishing with Testcontainers")
class KafkaEndToEndIT {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @TestConfiguration
    static class Cfg {
        @Bean
        KafkaConsumer<String, DataRecord> testConsumer() {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + System.nanoTime());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
            props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.dataops.platform.common.model");
            props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, DataRecord.class.getName());
            return new KafkaConsumer<>(props);
        }
    }

    @Autowired
    KafkaRecordPublisher publisher;

    @Autowired
    KafkaProducer kafkaProducer;

    @Autowired
    KafkaConsumer<String, DataRecord> consumer;

    private static final String RAW_INGEST_TOPIC = "dataops-raw-ingest";

    @Test
    void shouldPublishRecordToKafkaWhenEventFires() {
        assertTrue(kafkaProducer instanceof KafkaDataProducer,
                "Expected KafkaDataProducer to be active when app.kafka.enabled=true");

        consumer.subscribe(Collections.singletonList(RAW_INGEST_TOPIC));
        consumer.poll(Duration.ofMillis(500));

        DataRecord record = DataRecord.builder()
                .key("42")
                .source("test")
                .type("JSON")
                .payload(Map.of("hello", "world"))
                .timestamp(Instant.now())
                .build();

        publisher.handleRecordIngested(new DataRecordIngestedEvent(this, record));

        ConsumerRecord<String, DataRecord> received = null;
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline && received == null) {
            var batch = consumer.poll(Duration.ofMillis(500));
            for (var r : batch.records(RAW_INGEST_TOPIC)) {
                if ("42".equals(r.key())) {
                    received = r;
                    break;
                }
            }
        }

        assertNotNull(received, "Did not receive expected record on " + RAW_INGEST_TOPIC);
        assertEquals("test", received.value().getSource());
        assertEquals("world", received.value().getPayload().get("hello"));
    }
}
