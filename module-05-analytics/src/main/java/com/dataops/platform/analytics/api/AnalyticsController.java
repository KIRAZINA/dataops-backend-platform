package com.dataops.platform.analytics.api;

import com.dataops.platform.analytics.service.AnalyticsService;
import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.inmemory.service.InMemoryStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final InMemoryStorageService storageService;

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@RequestParam(required = false) String source) {
        log.info("Retrieving analytics stats{}", source != null ? " for source: " + source : "");
        try {
            List<DataRecord> records = source == null ? storageService.findAllRecords() : storageService.findBySource(source);
            log.debug("Processing {} records for stats", records.size());
            Map<String, Object> stats = analyticsService.getStats(records, source);
            log.debug("Successfully retrieved analytics stats");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to retrieve stats{}", source != null ? " for source " + source : "", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to retrieve stats: " + e.getMessage()));
        }
    }

    @GetMapping("/sorted")
    public ResponseEntity<?> getSortedData(
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "quicksort") String sortType) {
        log.info("Retrieving sorted data{} with sort type: {}",
                 source != null ? " for source: " + source : "", sortType);
        try {
            List<DataRecord> records = source == null ? storageService.findAllRecords() : storageService.findBySource(source);
            log.debug("Processing {} records for sorting", records.size());
            List<DataRecord> sortedData = analyticsService.getSortedData(records, source, sortType);
            log.debug("Successfully retrieved {} sorted records", sortedData.size());
            return ResponseEntity.ok(sortedData);
        } catch (Exception e) {
            log.error("Failed to retrieve sorted data{} with sort type: {}",
                      source != null ? " for source " + source : "", sortType, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to retrieve sorted data: " + e.getMessage()));
        }
    }
}