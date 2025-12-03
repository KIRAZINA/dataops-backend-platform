package com.dataops.platform.filestorage.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class DistributedLockService {

    public boolean tryLock(String key) { return true; }
    public void unlock(String key) { /* no-op */ }
}