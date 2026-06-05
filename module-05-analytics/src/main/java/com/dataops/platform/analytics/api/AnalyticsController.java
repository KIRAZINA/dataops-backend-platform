package com.dataops.platform.analytics.api;

import com.dataops.platform.analytics.service.AnalyticsService;
import com.dataops.platform.common.dto.PagedResponse;
import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.inmemory.service.InMemoryStorageService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final InMemoryStorageService storageService;

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int pageSize) {
        log.info("Retrieving analytics stats{} with pagination: page={}, pageSize={}",
                source != null ? " for source: " + source : "", page, pageSize);

        List<DataRecord> records = resolveRecords(source);
        log.debug("Processing {} records for stats", records.size());

        Map<String, Object> stats = analyticsService.getStats(records, source);
        PagedResponse<Map<String, Object>> response = PagedResponse.<Map<String, Object>>builder()
                .content(List.of(stats))
                .pageNumber(0)
                .pageSize(1)
                .totalElements(1)
                .totalPages(1)
                .isFirst(true)
                .isLast(true)
                .build();

        log.debug("Successfully retrieved analytics stats");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sorted")
    public ResponseEntity<?> getSortedData(
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "quicksort") String sortType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int pageSize) {
        log.info("Retrieving sorted data{} with sort type: {} (pagination: page={}, pageSize={})",
                source != null ? " for source: " + source : "", sortType, page, pageSize);

        List<DataRecord> records = resolveRecords(source);
        long totalElements = records.size();

        log.debug("Processing {} records for sorting", records.size());
        List<DataRecord> sortedData = analyticsService.getSortedData(records, source, sortType);
        List<DataRecord> pagedData = paginate(sortedData, page, pageSize);

        PagedResponse<DataRecord> response = PagedResponse.of(pagedData, page, pageSize, totalElements);

        log.debug("Successfully retrieved {} sorted records", pagedData.size());
        return ResponseEntity.ok(response);
    }

    private List<DataRecord> resolveRecords(String source) {
        return source == null ? storageService.findAllRecords() : storageService.findBySource(source);
    }

    private List<DataRecord> paginate(List<DataRecord> records, int page, int pageSize) {
        int start = page * pageSize;
        if (start >= records.size()) {
            return Collections.emptyList();
        }

        int end = Math.min(start + pageSize, records.size());
        return List.copyOf(records.subList(start, end));
    }
}
