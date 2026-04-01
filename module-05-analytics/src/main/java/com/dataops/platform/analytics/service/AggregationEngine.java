package com.dataops.platform.analytics.service;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.core.algorithm.Sorter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        DataRecord[] sortedArray = records.toArray(new DataRecord[0]);

        Comparator<DataRecord> comparator = Comparator.comparingDouble(r -> {
            Object value = r.getPayload().getOrDefault("value", 0);
            return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
        });

        switch (sortType.toLowerCase()) {
            case "quicksort" -> Sorter.quickSort(sortedArray, comparator);
            case "mergesort" -> Sorter.mergeSort(sortedArray, comparator);
            case "heapsort" -> Sorter.heapSort(sortedArray, comparator);
            default -> Arrays.sort(sortedArray, comparator);
        }

        return new ArrayList<>(Arrays.asList(sortedArray));
    }
}
