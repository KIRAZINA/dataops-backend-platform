package com.dataops.platform.streaming.kafka;

import com.dataops.platform.common.event.DataRecordIngestedEvent;
import com.dataops.platform.common.model.DataRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
@RequiredArgsConstructor
public class KafkaRecordPublisher {

    private final KafkaTemplate<String, DataRecord> kafkaTemplate;

    @EventListener
    public void handleRecordIngested(DataRecordIngestedEvent event) {
        DataRecord record = event.getRecord();
        kafkaTemplate.send("dataops-raw-ingest", record.id(), record)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        var meta = result.getRecordMetadata();
                        log.info("Kafka → sent id={} partition={} offset={}", record.id(), meta.partition(), meta.offset());
                    } else {
                        log.warn("Kafka send failed for id={}: {}", record.id(), ex.getMessage());
                    }
                });
    }
}