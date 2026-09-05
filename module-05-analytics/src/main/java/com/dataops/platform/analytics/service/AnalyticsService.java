package com.dataops.platform.analytics.service;

import com.dataops.platform.common.model.DataRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AggregationEngine engine;

    /**
     * Aggregate stats for a record list. Cached under {@code analytics-stats}
     * with key {@code source} — same source returns the same result until the
     * cache TTL (5 min, see CacheConfig) elapses or an explicit eviction runs.
     *
     * <p><b>Invalidation:</b> {@code InMemoryStorageService.saveRecord}/{@code saveRecords}
     * evict this cache (see {@code InMemoryStorageService} Javadoc), so freshly ingested
     * records are reflected immediately on the next {@code /analytics/stats} call —
     * not after the 5-minute TTL. This is the cache-invalidation contract that
     * prevents reintroducing the original "analytics endpoints don't reflect new
     * data" bug in time-boxed form.
     *
     * <p>Records list is NOT part of the cache key by design (same source =
     * same records during the TTL window between evictions). The only caller is
     * {@code AnalyticsController}, which always pulls the source's records from
     * {@code InMemoryStorageService}. If a future caller passes records not sourced
     * from {@code InMemoryStorageService}, this needs to be revisited.
     */
    @Cacheable(cacheNames = "analytics-stats", key = "#source != null ? #source : 'all'")
    public Map<String, Object> getStats(List<DataRecord> records, String source) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("count", records.size());
        stats.put("groupBySource", engine.groupBySource(records));
        stats.put("averageByType", engine.calculateAverageByType(records));
        return stats;
    }

    /**
     * Sorted-data result. Cached under {@code analytics-sorted} with a compound
     * key (source + sortType) — different sort algorithms produce different
     * orderings, so caching across algorithms would be wrong.
     *
     * <p><b>Invalidation:</b> same eviction contract as {@link #getStats}.
     */
    @Cacheable(cacheNames = "analytics-sorted", key = "(#source != null ? #source : 'all') + '-' + #sortType")
    public List<DataRecord> getSortedData(List<DataRecord> records, String source, String sortType) {
        return engine.sortByPayloadValue(records, sortType);
    }
}
