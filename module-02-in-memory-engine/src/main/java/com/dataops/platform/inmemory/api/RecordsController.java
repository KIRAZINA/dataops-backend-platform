package com.dataops.platform.inmemory.api;

import com.dataops.platform.common.dto.PagedResponse;
import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.service.PersistenceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for retrieving stored data records with pagination support.
 * Provides endpoints for browsing persisted records.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/records")
@Validated
@RequiredArgsConstructor
public class RecordsController {

    private final PersistenceService persistenceService;

    /**
     * Retrieve all records with pagination
     *
     * @param page zero-indexed page number (default: 0)
     * @param pageSize number of records per page (default: 20, max: 500)
     * @return paginated list of all records
     */
    @GetMapping
    public ResponseEntity<PagedResponse<DataRecord>> getAllRecords(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int pageSize) {
        log.info("Retrieving all persisted records with pagination: page={}, pageSize={}", page, pageSize);

        List<DataRecord> records = persistenceService.findAll().stream()
                .skip((long) page * pageSize)
                .limit(pageSize)
                .map(this::toDataRecord)
                .collect(Collectors.toList());

        long totalElements = persistenceService.count();

        log.debug("Successfully retrieved {} records for page {}", records.size(), page);

        return ResponseEntity.ok(PagedResponse.of(records, page, pageSize, totalElements));
    }

    /**
     * Retrieve records filtered by source with pagination
     *
     * @param source the source filter
     * @param page zero-indexed page number (default: 0)
     * @param pageSize number of records per page (default: 20, max: 500)
     * @return paginated list of records matching the source
     */
    @GetMapping("/by-source")
    public ResponseEntity<PagedResponse<DataRecord>> getRecordsBySource(
            @RequestParam @NotBlank String source,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int pageSize) {
        log.info("Retrieving records by source: {}, page={}, pageSize={}", source, page, pageSize);

        List<DataRecord> records = persistenceService.findBySource(source).stream()
                .skip((long) page * pageSize)
                .limit(pageSize)
                .map(this::toDataRecord)
                .collect(Collectors.toList());

        long totalElements = persistenceService.findBySource(source).size();

        log.debug("Successfully retrieved {} records for page {} by source: {}", records.size(), page, source);

        return ResponseEntity.ok(PagedResponse.of(records, page, pageSize, totalElements));
    }

    /**
     * Retrieve a single record by ID
     *
     * @param id the record ID
     * @return the record if found, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<DataRecord> getRecordById(@PathVariable @NotBlank String id) {
        log.info("Retrieving record by ID: {}", id);
        PersistedRecord persisted = persistenceService.findAll().stream()
                .filter(record -> String.valueOf(record.getId()).equals(id))
                .findFirst()
                .orElse(null);

        if (persisted == null) {
            log.warn("Record not found with ID: {}", id);
            return ResponseEntity.notFound().build();
        }

        log.debug("Successfully retrieved record with ID: {}", id);
        return ResponseEntity.ok(toDataRecord(persisted));
    }

    private DataRecord toDataRecord(PersistedRecord persisted) {
        return DataRecord.builder()
                .key(String.valueOf(persisted.getId()))
                .source(persisted.getSource())
                .type(persisted.getType())
                .payload(persisted.getPayload())
                .timestamp(persisted.getIngestedAt().atZone(ZoneId.systemDefault()).toInstant())
                .build();
    }
}
