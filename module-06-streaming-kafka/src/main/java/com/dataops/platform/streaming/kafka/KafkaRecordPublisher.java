package com.dataops.platform.streaming.kafka;

import com.dataops.platform.common.event.DataRecordIngestedEvent;
import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.streaming.producer.KafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaRecordPublisher {

    private static final String RAW_INGEST_TOPIC = "dataops-raw-ingest";

    private final KafkaProducer kafkaProducer;

    @EventListener
    public void handleRecordIngested(DataRecordIngestedEvent event) {
        DataRecord record = event.getRecord();
        log.debug("Forwarding ingested record {} to Kafka producer", record.id());
        kafkaProducer.publish(RAW_INGEST_TOPIC, record);
    }
}
