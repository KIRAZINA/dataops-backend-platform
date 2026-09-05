package com.dataops.platform.inmemory.service;

import com.dataops.platform.common.model.DataRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("InMemoryStorageService Concurrency Tests")
class InMemoryStorageServiceConcurrencyTest {

    @Test
    @DisplayName("Concurrent saveRecord calls must not corrupt indexes or counts")
    void concurrentSavesPreserveIndexes() throws Exception {
        InMemoryStorageService service = new InMemoryStorageService(mock(ApplicationEventPublisher.class));

        int threadCount = 50;
        int savesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            executor.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < savesPerThread; i++) {
                        DataRecord r = DataRecord.builder()
                                .key("t" + threadIdx + "-" + i)
                                .source("src-" + threadIdx)
                                .type("JSON")
                                .payload(Map.of("i", i))
                                .timestamp(Instant.now())
                                .build();
                        service.saveRecord(r);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "Concurrent saves did not complete in time");
        executor.shutdown();

        // total count correct
        assertEquals(threadCount * savesPerThread, service.size());

        // every saved key must be retrievable by id
        Set<String> seenKeys = new HashSet<>();
        List<DataRecord> all = service.findAllRecords();
        for (DataRecord r : all) {
            assertTrue(seenKeys.add(r.id()), "Duplicate id in in-memory store: " + r.id());
            assertEquals(r, service.findById(r.id()));
        }
        assertEquals(all.size(), seenKeys.size());

        // source/type indexes must agree with the master list
        long sourceTotal = 0;
        for (int t = 0; t < threadCount; t++) {
            sourceTotal += service.getTotalRecordCountBySource("src-" + t);
        }
        assertEquals(all.size(), sourceTotal);
    }
}
