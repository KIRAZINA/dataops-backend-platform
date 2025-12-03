package com.dataops.platform.analytics.service;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.core.algorithm.Sorter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AggregationEngine {

    public Map<String, Long> groupBySource(List<DataRecord> records) {
        return records.stream()
                .collect(Collectors.groupingBy(DataRecord::getSource, Collectors.counting()));
    }

    public Map<String, Double> calculateAverageByType(List<DataRecord> records) {
        Map<String, List<Double>> grouped = records.stream()
                .collect(Collectors.groupingBy(
                        DataRecord::getType,
                        Collectors.mapping(
                                r -> {
                                    Object value = r.getPayload().getOrDefault("value", 0);
                                    return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
                                },
                                Collectors.toList()
                        )
                ));

        return grouped.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .mapToDouble(Double::doubleValue)
                                .average()
                                .orElse(0.0)
                ));
    }

    public List<DataRecord> sortByPayloadValue(List<DataRecord> records, String sortType) {
        List<DataRecord> sorted = new ArrayList<>(records);

        Comparator<DataRecord> comparator = Comparator.comparingDouble(r -> {
            Object value = r.getPayload().getOrDefault("value", 0);
            return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
        });

        switch (sortType.toLowerCase()) {
            case "quicksort" -> Sorter.quickSort(sorted.toArray(new DataRecord[0]), comparator);
            case "mergesort" -> Sorter.mergeSort(sorted.toArray(new DataRecord[0]), comparator);
            case "heapsort" -> Sorter.heapSort(sorted.toArray(new DataRecord[0]), comparator);
            default -> sorted.sort(comparator);
        }

        return sorted;
    }
}