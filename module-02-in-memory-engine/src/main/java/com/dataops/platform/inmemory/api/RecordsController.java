package com.dataops.platform.inmemory.api;

import com.dataops.platform.common.dto.PagedResponse;
import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.inmemory.service.InMemoryStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for retrieving stored data records with pagination support.
 * Provides endpoints for browsing ingested records.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/records")
@RequiredArgsConstructor
public class RecordsController {

    private final InMemoryStorageService storageService;

    /**
     * Retrieve all records with pagination
     *
     * @param page zero-indexed page number (default: 0)
     * @param pageSize number of records per page (default: 20, max: 500)
     * @return paginated list of all records
     */
    @GetMapping
    public ResponseEntity<?> getAllRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("Retrieving all records with pagination: page={}, pageSize={}", page, pageSize);
        try {
            // Validate pagination parameters
            if (page < 0) {
                log.warn("Invalid page number: {}", page);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Page number must be non-negative"));
            }
            if (pageSize < 1 || pageSize > 500) {
                log.warn("Invalid page size: {}", pageSize);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Page size must be between 1 and 500"));
            }

            List<DataRecord> records = storageService.findAllRecordsPaginated(page, pageSize);
            long totalElements = storageService.getTotalRecordCount();
            
            log.debug("Successfully retrieved {} records for page {}", records.size(), page);
            
            PagedResponse<DataRecord> response = PagedResponse.of(records, page, pageSize, totalElements);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to retrieve records", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve records: " + e.getMessage()));
        }
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
    public ResponseEntity<?> getRecordsBySource(
            @RequestParam String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("Retrieving records by source: {}, page={}, pageSize={}", source, page, pageSize);
        try {
            // Validate pagination parameters
            if (page < 0) {
                log.warn("Invalid page number: {}", page);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Page number must be non-negative"));
            }
            if (pageSize < 1 || pageSize > 500) {
                log.warn("Invalid page size: {}", pageSize);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Page size must be between 1 and 500"));
            }

            List<DataRecord> records = storageService.findBySourcePaginated(source, page, pageSize);
            long totalElements = storageService.getTotalRecordCountBySource(source);
            
            log.debug("Successfully retrieved {} records for page {} by source: {}", 
                    records.size(), page, source);
            
            PagedResponse<DataRecord> response = PagedResponse.of(records, page, pageSize, totalElements);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to retrieve records by source: {}", source, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve records: " + e.getMessage()));
        }
    }

    /**
     * Retrieve a single record by ID
     *
     * @param id the record ID
     * @return the record if found, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getRecordById(@PathVariable String id) {
        log.info("Retrieving record by ID: {}", id);
        try {
            DataRecord record = storageService.findById(id);
            if (record == null) {
                log.warn("Record not found with ID: {}", id);
                return ResponseEntity.notFound().build();
            }
            log.debug("Successfully retrieved record with ID: {}", id);
            return ResponseEntity.ok(record);
        } catch (Exception e) {
            log.error("Failed to retrieve record by ID: {}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve record: " + e.getMessage()));
        }
    }
}
