package com.dataops.platform.inmemory.api;

import com.dataops.platform.common.dto.PagedResponse;
import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.repository.jpa.RecordSpecifications;
import com.dataops.platform.persistence.service.PersistenceService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST Controller for retrieving stored data records with pagination support.
 * Provides endpoints for browsing persisted records.
 *
 * <p>Query contract for {@code GET /api/v1/records}:
 * <ul>
 *   <li>{@code source} optional, exact match, max 255 chars</li>
 *   <li>{@code type}   optional, exact match, max 50 chars</li>
 *   <li>{@code from}   optional, ISO-8601 instant (e.g. {@code 2026-09-06T21:08:40Z}
 *       or {@code 2026-09-06T21:08:40+02:00}); inclusive lower bound, normalised to UTC</li>
 *   <li>{@code to}     optional, ISO-8601 instant; inclusive upper bound, normalised to UTC</li>
 *   <li>{@code sortBy} {@code ingestedAt} (default) or {@code id}; anything else -> 400</li>
 *   <li>{@code sortDir} {@code asc} or {@code desc}; anything else -> 400</li>
 *   <li>{@code page} 0-indexed page number (default 0)</li>
 *   <li>{@code pageSize} page size 1..500 (default 20)</li>
 * </ul>
 *
 * <p>Deterministic paging: {@code id} is always appended as a secondary sort in
 * the same direction as the primary. Without this, two records sharing an
 * {@code ingestedAt} timestamp would shuffle between pages.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/records")
@Validated
@RequiredArgsConstructor
public class RecordsController {

    private static final Set<String> ALLOWED_SORT_BY = Set.of("ingestedAt", "id");
    private static final Set<String> ALLOWED_SORT_DIR = Set.of("asc", "desc");
    private static final int MAX_SOURCE_LENGTH = 255;
    private static final int MAX_TYPE_LENGTH = 50;

    private final PersistenceService persistenceService;

    /**
     * Retrieve records with optional filters, sort, and pagination.
     *
     * <p>All filters are optional. When every filter is omitted, the predicate
     * is empty ({@code WHERE 1=1}) and the query degrades to a plain paged
     * {@code findAll}. When the result set is empty, the response is still
     * {@code 200} with {@code content: []} and {@code total_elements: 0} —
     * never 404.
     */
    @GetMapping
    public ResponseEntity<PagedResponse<DataRecord>> getAllRecords(
            @Parameter(description = "Exact-match source filter. Max 255 chars.")
            @RequestParam(required = false) @Size(max = MAX_SOURCE_LENGTH) String source,

            @Parameter(description = "Exact-match type filter. Max 50 chars.")
            @RequestParam(required = false) @Size(max = MAX_TYPE_LENGTH) String type,

            @Parameter(description = "Inclusive lower bound on ingestedAt, ISO-8601 instant. "
                    + "Accepts any offset; normalised to UTC at the persistence boundary.")
            @RequestParam(required = false) Instant from,

            @Parameter(description = "Inclusive upper bound on ingestedAt, ISO-8601 instant.")
            @RequestParam(required = false) Instant to,

            @Parameter(description = "Sort field. One of: ingestedAt (default), id.")
            @RequestParam(defaultValue = "ingestedAt") String sortBy,

            @Parameter(description = "Sort direction. One of: asc, desc (default).")
            @RequestParam(defaultValue = "desc") String sortDir,

            @Parameter(description = "Zero-indexed page number.")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Page size, 1..500.")
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int pageSize) {

        if (!ALLOWED_SORT_BY.contains(sortBy)) {
            throw new IllegalArgumentException(
                    "Invalid sortBy '" + sortBy + "'. Allowed values: " + ALLOWED_SORT_BY);
        }
        if (!ALLOWED_SORT_DIR.contains(sortDir)) {
            throw new IllegalArgumentException(
                    "Invalid sortDir '" + sortDir + "'. Allowed values: " + ALLOWED_SORT_DIR);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Invalid range: from (" + from + ") is after to (" + to + ")");
        }

        Sort.Direction direction = Sort.Direction.fromString(sortDir);
        Sort primary = Sort.by(direction, sortBy);
        Sort withTiebreaker = primary.and(Sort.by(direction, "id"));
        Pageable pageable = PageRequest.of(page, pageSize, withTiebreaker);

        Specification<PersistedRecord> spec =
                RecordSpecifications.withFilters(source, type, from, to);

        Page<PersistedRecord> result = persistenceService.findRecords(spec, pageable);

        List<DataRecord> records = result.getContent().stream()
                .map(this::toDataRecord)
                .collect(Collectors.toList());

        log.info("records.query source={} type={} from={} to={} sortBy={} sortDir={} "
                        + "page={} pageSize={} hits={} total={}",
                source, type, from, to, sortBy, sortDir,
                page, pageSize, records.size(), result.getTotalElements());

        return ResponseEntity.ok(PagedResponse.of(records, page, pageSize, result.getTotalElements()));
    }

    /**
     * Retrieve records filtered by source with pagination.
     *
     * <p>Kept for backwards compatibility — equivalent to
     * {@code GET /api/v1/records?source=<source>} but documented as a
     * dedicated route for callers that want to make the source filter
     * structurally mandatory. Uses the cached repository path internally.
     */
    @GetMapping("/by-source")
    public ResponseEntity<PagedResponse<DataRecord>> getRecordsBySource(
            @RequestParam @NotBlank @Size(max = MAX_SOURCE_LENGTH) String source,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int pageSize) {
        log.info("Retrieving records by source: {}, page={}, pageSize={}", source, page, pageSize);

        Pageable pageable = PageRequest.of(page, pageSize);
        Page<PersistedRecord> result = persistenceService.findBySourcePaged(source, pageable);

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
                .timestamp(persisted.getIngestedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                .build();
    }
}
