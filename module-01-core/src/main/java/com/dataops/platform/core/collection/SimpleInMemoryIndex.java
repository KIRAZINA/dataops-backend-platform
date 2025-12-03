package com.dataops.platform.core.collection;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe inverted index: key → List<Long> ids.
 */
public class SimpleInMemoryIndex {

    private final Map<String, List<Long>> index = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public void add(String key, Long id) {
        lock.writeLock().lock();
        try {
            index.computeIfAbsent(key, k -> new ArrayList<>()).add(id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Long> get(String key) {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(
                    index.getOrDefault(key, Collections.emptyList())
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    public void remove(String key, Long id) {
        lock.writeLock().lock();
        try {
            Optional.ofNullable(index.get(key)).ifPresent(list -> list.remove(id));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            index.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return index.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean containsKey(String key) {
        lock.readLock().lock();
        try {
            return index.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }
}