package com.dataops.platform.core.collection;

public class RingBuffer<T> {

    private final Object[] buffer;
    private final int capacity;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public RingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be > 0");
        }
        this.capacity = capacity;
        this.buffer = new Object[capacity];
    }

    public boolean offer(T element) {
        if (size == capacity) {
            return false; // full
        }
        buffer[tail] = element;
        tail = (tail + 1) % capacity;
        size++;
        return true;
    }

    @SuppressWarnings("unchecked")
    public T poll() {
        if (size == 0) {
            return null; // empty
        }
        T element = (T) buffer[head];
        buffer[head] = null; 
        head = (head + 1) % capacity;
        size--;
        return element;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean isFull() {
        return size == capacity;
    }

    public int capacity() {
        return capacity;
    }

    @SuppressWarnings("unchecked")
    public T peek() {
        return isEmpty() ? null : (T) buffer[head];
    }

    /** Overwrite oldest element if full (useful for metrics, logs) */
    public void offerOverwrite(T element) {
        if (isFull()) {
            buffer[head] = element;
            head = (head + 1) % capacity;
            tail = head;
        } else {
            offer(element);
        }
    }
}