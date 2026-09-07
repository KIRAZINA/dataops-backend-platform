// src/main/java/com/dataops/platform/streaming/producer/KafkaProducer.java
package com.dataops.platform.streaming.producer;

import com.dataops.platform.common.model.DataRecord;

public interface KafkaProducer {
    void publish(String topic, DataRecord record);

    /**
     * Publish a pre-serialized JSON payload (the outbox relay path).
     * The payload is the JSON form of a {@link DataRecord}; implementations
     * deserialize it back and delegate to {@link #publish(String, DataRecord)}.
     */
    void publish(String topic, String rawJsonPayload);
}