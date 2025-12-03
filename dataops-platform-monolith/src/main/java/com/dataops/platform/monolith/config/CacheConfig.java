package com.dataops.platform.monolith.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        Cache recordsById = new CaffeineCache("records-by-id", Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(50_000)
                .recordStats()
                .build());

        Cache recordsBySource = new CaffeineCache("records-by-source", Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build());

        Cache analyticsStats = new CaffeineCache("analytics-stats", Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build());

        Cache analyticsSorted = new CaffeineCache("analytics-sorted", Caffeine.newBuilder()
                .expireAfterWrite(3, TimeUnit.MINUTES)
                .maximumSize(50)
                .build());

        Cache exportCache = new CaffeineCache("export-cache", Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(10)
                .build());

        cacheManager.setCaches(Arrays.asList(
                recordsById,
                recordsBySource,
                analyticsStats,
                analyticsSorted,
                exportCache
        ));

        return cacheManager;
    }
}