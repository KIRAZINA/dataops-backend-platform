// module-01-core/src/test/java/com/dataops/platform/core/collection/SimpleInMemoryIndexTest.java

package com.dataops.platform.core.collection;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleInMemoryIndexTest {

    @Test
    void shouldIndexAndRetrieveCorrectly() {
        SimpleInMemoryIndex index = new SimpleInMemoryIndex();

        index.add("Moscow", 1L);
        index.add("London", 2L);
        index.add("Moscow", 3L);

        assertEquals(List.of(1L, 3L), index.get("Moscow"));
        assertEquals(List.of(2L), index.get("London"));
        assertTrue(index.get("Paris").isEmpty());
    }

    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        SimpleInMemoryIndex index = new SimpleInMemoryIndex();

        Runnable writer = () -> {
            for (int i = 0; i < 1000; i++) {
                index.add("key" + (i % 10), (long) i);
            }
        };

        Thread t1 = new Thread(writer);
        Thread t2 = new Thread(writer);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // 2 потока × 1000 записей = 2000 записей
        // 10 разных ключей → по 200 записей на каждый ключ
        for (int i = 0; i < 10; i++) {
            assertEquals(200, index.get("key" + i).size());
        }
    }
}