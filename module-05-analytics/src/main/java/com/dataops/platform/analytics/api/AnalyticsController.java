package com.dataops.platform.analytics.api;

import com.dataops.platform.analytics.service.AnalyticsService;
import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.inmemory.service.InMemoryStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final InMemoryStorageService storageService;

    @GetMapping("/stats")
    public Map<String, Object> getStats(@RequestParam(required = false) String source) {
        List<DataRecord> records = source == null ? storageService.findAllRecords() : storageService.findBySource(source);
        return analyticsService.getStats(records, source);
    }

    @GetMapping("/sorted")
    public List<DataRecord> getSortedData(
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "quicksort") String sortType) {
        List<DataRecord> records = source == null ? storageService.findAllRecords() : storageService.findBySource(source);
        return analyticsService.getSortedData(records, source, sortType);
    }
}