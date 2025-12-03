package com.dataops.platform.core.collection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingBufferTest {

    @Test
    void shouldWorkAsCircularBuffer() {
        RingBuffer<Integer> rb = new RingBuffer<>(3);

        assertTrue(rb.offer(1));
        assertTrue(rb.offer(2));
        assertTrue(rb.offer(3));
        assertFalse(rb.offer(4));

        assertEquals(3, rb.size());
        assertEquals(Integer.valueOf(1), rb.poll());
        assertEquals(Integer.valueOf(2), rb.poll());
        assertEquals(1, rb.size());

        assertTrue(rb.offer(4));
        assertEquals(Integer.valueOf(3), rb.poll());
        assertEquals(Integer.valueOf(4), rb.poll());
        assertTrue(rb.isEmpty());
    }
}