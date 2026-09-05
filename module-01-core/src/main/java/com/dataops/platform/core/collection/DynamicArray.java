package com.dataops.platform.core.collection;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Generic dynamic array with amortized O(1) append.
 *
 * <p><b>Not thread-safe.</b> Mutations to {@code elements} and {@code size} fields are performed
 * without any synchronization or memory-barrier guarantees. Callers that need concurrent access
 * must synchronize externally (e.g. wrap access in a {@code synchronized} block or use a
 * purpose-built concurrent structure such as {@link java.util.concurrent.CopyOnWriteArrayList}).
 *
 * <p>The earlier class-level comment claimed "Thread-safe for single producer / single consumer"
 * but no field of this class is {@code volatile} and reads of {@code elements} / {@code size}
 * are not guarded by any happens-before relationship, so the SPSC claim was misleading.
 */
public class DynamicArray<T> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final float GROWTH_FACTOR = 1.5f;

    private Object[] elements;
    private int size;

    public DynamicArray() {
        this(DEFAULT_CAPACITY);
    }

    public DynamicArray(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException("Capacity < 0");
        this.elements = new Object[initialCapacity];
    }

    public void add(T element) {
        ensureCapacity(size + 1);
        elements[size++] = element;
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        checkIndex(index);
        return (T) elements[index];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > elements.length) {
            int newCapacity = Math.max((int) (elements.length * GROWTH_FACTOR), minCapacity);
            elements = Arrays.copyOf(elements, newCapacity);
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size)
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
    }

    public T removeLast() {
        if (isEmpty()) throw new NoSuchElementException("Array is empty");
        T element = (T) elements[--size];
        elements[size] = null; // avoid memory leak
        return element;
    }

    public void clear() {
        Arrays.fill(elements, 0, size, null);
        size = 0;
    }
}