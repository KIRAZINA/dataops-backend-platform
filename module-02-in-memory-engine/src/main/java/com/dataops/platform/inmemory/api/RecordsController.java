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
     * Retrieve all records with pagination.
     *
     * <p>Uses the database-level {@link org.springframework.data.domain.Pageable}
     * so skip/limit happen in SQL, not via {@code findAll().stream().skip(...).limit(...)}.
     * Memory cost is proportional to page size, not to total record count.
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

        var pageRequest = org.springframework.data.domain.PageRequest.of(page, pageSize);
        org.springframework.data.domain.Page<PersistedRecord> result = persistenceService.findAllPaged(pageRequest);

        List<DataRecord> records = result.getContent().stream()
                .map(this::toDataRecord)
                .collect(Collectors.toList());

        log.debug("Successfully retrieved {} records for page {}", records.size(), page);

        return ResponseEntity.ok(PagedResponse.of(records, page, pageSize, result.getTotalElements()));
    }

    /**
     * Retrieve records filtered by source with pagination.
     *
     * <p>Uses the database-level paged query
     * ({@code findBySourceOrderByIngestedAtDesc(String, Pageable)}) so the
     * source filter and pagination both happen in SQL. Avoids the
     * load-full-list-then-slice pattern that the previous version used.
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

        var pageRequest = org.springframework.data.domain.PageRequest.of(page, pageSize);
        org.springframework.data.domain.Page<PersistedRecord> result =
                persistenceService.findBySourcePaged(source, pageRequest);

        List<DataRecord> records = result.getContent().stream()
                .map(this::toDataRecord)
                .collect(Collectors.toList());

        log.debug("Successfully retrieved {} records for page {} by source: {}", records.size(), page, source);

        return ResponseEntity.ok(PagedResponse.of(records, page, pageSize, result.getTotalElements()));
    }

    /**
     * Retrieve a single record by ID.
     *
     * <p>Uses an indexed JPA {@code findById} lookup rather than scanning the entire
     * record set, so this endpoint is O(1) in the database rather than O(n).
     *
     * @param id the record ID
     * @return the record if found, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<DataRecord> getRecordById(@PathVariable @NotBlank String id) {
        log.info("Retrieving record by ID: {}", id);

        Long parsedId;
        try {
            parsedId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            log.warn("Invalid record id format: {}", id);
            return ResponseEntity.notFound().build();
        }

        return persistenceService.findById(parsedId)
                .map(p -> {
                    log.debug("Successfully retrieved record with ID: {}", id);
                    return ResponseEntity.ok(toDataRecord(p));
                })
                .orElseGet(() -> {
                    log.warn("Record not found with ID: {}", id);
                    return ResponseEntity.notFound().build();
                });
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
