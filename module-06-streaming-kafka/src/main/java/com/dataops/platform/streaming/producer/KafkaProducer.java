// src/main/java/com/dataops/platform/streaming/producer/KafkaProducer.java
package com.dataops.platform.streaming.producer;

import com.dataops.platform.common.model.DataRecord;

public interface KafkaProducer {
    void publish(String topic, DataRecord record);
}