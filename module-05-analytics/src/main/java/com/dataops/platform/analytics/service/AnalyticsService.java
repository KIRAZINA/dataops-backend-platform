package com.dataops.platform.analytics.service;

import com.dataops.platform.common.model.DataRecord;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private final MeterRegistry meterRegistry;

    @Cacheable(value = "stats", key = "#source")
    public Map<String, Object> getStats(List<DataRecord> records, String source) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("count", records.size());
        stats.put("groupBySource", engine.groupBySource(records));
        stats.put("averageByType", engine.calculateAverageByType(records));

        meterRegistry.counter("analytics.records.processed", "source", source != null ? source : "all")
                .increment(records.size());

        if (!records.isEmpty()) {
            double maxValue = records.stream()
                    .mapToDouble(r -> {
                        Object val = r.getPayload().getOrDefault("value", 0);
                        return val instanceof Number ? ((Number) val).doubleValue() : 0.0;
                    })
                    .max()
                    .orElse(0.0);

            meterRegistry.gauge("analytics.payload.max_value", maxValue);
        }

        return stats;
    }

    @Cacheable(value = "sorted", key = "#source + '_' + #sortType")
    public List<DataRecord> getSortedData(List<DataRecord> records, String source, String sortType) {
        Timer timer = meterRegistry.timer("analytics.sort.time", "algorithm", sortType);
        return timer.record(() -> engine.sortByPayloadValue(records, sortType));
    }
}