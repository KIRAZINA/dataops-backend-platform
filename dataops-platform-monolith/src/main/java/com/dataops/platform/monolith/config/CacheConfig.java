package com.dataops.platform.monolith.config;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spring Cache wiring.
 *
 * <p>Only caches that have an actual {@code @Cacheable} consumer in the codebase
 * are configured here. Earlier iterations declared five caches
 * ({@code records-by-id}, {@code records-by-source}, {@code analytics-stats},
 * {@code analytics-sorted}, {@code export-cache}) but none were consumed;
 * caches without consumers are dead code that misleads future readers about
 * what the system actually does. See the audit accompanying Stage 1 for the
 * per-cache decision matrix.
 *
 * <p>Current consumers:
 * <ul>
 *   <li>{@code records-by-source} — {@code PersistenceService.findBySource(source)}</li>
 *   <li>{@code analytics-stats} — {@code AnalyticsService.getStats(records, source)}</li>
 *   <li>{@code analytics-sorted} — {@code AnalyticsService.getSortedData(records, source, sortType)}</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

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

        cacheManager.setCaches(List.of(recordsBySource, analyticsStats, analyticsSorted));

        return cacheManager;
    }
}
