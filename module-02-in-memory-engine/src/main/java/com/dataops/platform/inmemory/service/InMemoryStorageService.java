package com.dataops.platform.inmemory.service;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.common.event.DataRecordIngestedEvent;
import com.dataops.platform.core.collection.DynamicArray;
import com.dataops.platform.core.collection.SimpleInMemoryIndex;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InMemoryStorageService {

    private final DynamicArray<DataRecord> storage = new DynamicArray<>(1024);
    private final SimpleInMemoryIndex sourceIndex = new SimpleInMemoryIndex();
    private final SimpleInMemoryIndex typeIndex = new SimpleInMemoryIndex();

    private final ApplicationEventPublisher eventPublisher;

    private long sequence = 1L;

    public synchronized DataRecord save(String source, String type, Map<String, Object> payload) {
        DataRecord record = DataRecord.builder()
                .key(String.valueOf(sequence++))
                .source(source)
                .type(type)
                .payload(Map.copyOf(payload))
                .timestamp(Instant.now())
                .build();

        long position = storage.size();
        storage.add(record);

        sourceIndex.add(source, position);
        typeIndex.add(type, position);

        eventPublisher.publishEvent(new DataRecordIngestedEvent(this, record));

        return record;
    }

    public DataRecord findById(String id) {
        for (int i = 0; i < storage.size(); i++) {
            DataRecord r = storage.get(i);
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    public List<DataRecord> findBySource(String source) {
        return getRecordsByIndex(sourceIndex, source);
    }

    public List<DataRecord> findByType(String type) {
        return getRecordsByIndex(typeIndex, type);
    }

    public List<DataRecord> findAllRecords() {
        List<DataRecord> snapshot = new ArrayList<>(storage.size());
        for (int i = 0; i < storage.size(); i++) {
            snapshot.add(storage.get(i));
        }
        return Collections.unmodifiableList(snapshot);
    }

    private List<DataRecord> getRecordsByIndex(SimpleInMemoryIndex index, String key) {
        List<DataRecord> result = new ArrayList<>();
        for (Long pos : index.get(key)) {
            result.add(storage.get(pos.intValue()));
        }
        return List.copyOf(result); // immutable
    }

    public int size() {
        return storage.size();
    }
}