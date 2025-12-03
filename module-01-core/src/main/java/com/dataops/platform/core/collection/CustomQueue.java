package com.dataops.platform.core.collection;

/**
 * Simple FIFO queue based on DynamicArray.
 * Not thread-safe.
 */
public class CustomQueue<T> {

    private final DynamicArray<T> storage = new DynamicArray<>();
    private int head = 0;

    public void enqueue(T element) {
        storage.add(element);
    }

    public T dequeue() {
        if (isEmpty()) throw new IllegalStateException("Queue is empty");
        T value = storage.get(head);
        head++;
        return value;
    }

    public T peek() {
        if (isEmpty()) throw new IllegalStateException("Queue is empty");
        return storage.get(head);
    }

    public int size() {
        return storage.size() - head;
    }

    public boolean isEmpty() {
        return size() == 0;
    }
}