package com.dataops.platform.inmemory.api;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.inmemory.service.InMemoryStorageService;
import com.dataops.platform.persistence.service.PersistenceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ingest")
@OpenAPIDefinition(info = @Info(title = "DataOps In-Memory Engine", version = "1.0"))
@RequiredArgsConstructor
public class IngestController {

    private final InMemoryStorageService storage;
    private final PersistenceService persistenceService;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;

    @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> ingestJson(@Valid @RequestBody Map<String, Object> payload) {
        log.info("Received JSON ingestion request");
        try {
            DataRecord saved = saveToMemoryAndPersistence("api", "JSON", payload);
            log.info("Successfully saved JSON record with ID: {}", saved.id());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalStateException e) {
            log.error("Failed to persist JSON payload: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to ingest JSON: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/csv", consumes = "text/csv")
    public ResponseEntity<?> ingestCsv(@RequestBody String csv) {
        log.info("Received CSV ingestion request");
        try {
            List<Map<String, Object>> payloads = parseCsvContentMultipleRows(csv);
            List<DataRecord> saved = saveBatchToMemoryAndPersistence("api", "CSV", payloads);
            log.info("Successfully saved {} CSV records", saved.size());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalStateException e) {
            log.error("Failed to persist CSV payload: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to ingest CSV: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to parse CSV: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to parse CSV: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/xml", consumes = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> ingestXml(@RequestBody String xml) {
        log.info("Received XML ingestion request");
        try {
            Map<String, Object> map = xmlMapper.readValue(xml, new TypeReference<>() {});
            DataRecord saved = saveToMemoryAndPersistence("api", "XML", map);
            log.info("Successfully saved XML record with ID: {}", saved.id());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalStateException e) {
            log.error("Failed to persist XML payload: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to ingest XML: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to parse XML: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to parse XML: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> ingestFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "JSON") String type) {

        log.info("Received file upload request for type: {}", type);
        try {
            if (file.isEmpty()) {
                log.warn("Received empty file upload request");
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }

            if (file.getSize() > 10 * 1024 * 1024) {
                log.warn("File size exceeds limit: {} bytes", file.getSize());
                return ResponseEntity.badRequest().body(Map.of("error", "File size exceeds 10MB limit"));
            }

            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            String normalizedType = type.toUpperCase();

            return switch (normalizedType) {
                case "JSON" -> {
                    Map<String, Object> payload = objectMapper.readValue(content, new TypeReference<>() {});
                    DataRecord saved = saveToMemoryAndPersistence("file-upload", normalizedType, payload);
                    log.info("Successfully saved JSON file upload with ID: {}", saved.id());
                    yield ResponseEntity.ok("Ingested successfully. ID = " + saved.id());
                }
                case "XML" -> {
                    Map<String, Object> payload = xmlMapper.readValue(content, new TypeReference<>() {});
                    DataRecord saved = saveToMemoryAndPersistence("file-upload", normalizedType, payload);
                    log.info("Successfully saved XML file upload with ID: {}", saved.id());
                    yield ResponseEntity.ok("Ingested successfully. ID = " + saved.id());
                }
                case "CSV" -> {
                    List<Map<String, Object>> payloads = parseCsvContentMultipleRows(content);
                    List<DataRecord> saved = saveBatchToMemoryAndPersistence("file-upload", normalizedType, payloads);
                    log.info("Successfully saved CSV file upload with {} records", saved.size());
                    yield ResponseEntity.ok("Ingested " + saved.size() + " records successfully.");
                }
                default -> {
                    log.warn("Unsupported file type: {}", type);
                    yield ResponseEntity.badRequest().body(Map.of("error", "Unsupported type: " + type));
                }
            };
        } catch (IllegalStateException e) {
            log.error("Failed to persist file upload: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to persist file: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to process file upload: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to process file: " + e.getMessage()));
        }
    }

    private List<Map<String, Object>> parseCsvContentMultipleRows(String csv) throws Exception {
        try (CSVParser parser = CSVParser.parse(csv, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            var csvRecords = parser.getRecords();
            if (csvRecords.isEmpty()) {
                throw new IllegalArgumentException("CSV has no data rows");
            }

            List<Map<String, Object>> payloads = new ArrayList<>();
            var headerMap = parser.getHeaderMap();

            for (var record : csvRecords) {
                Map<String, Object> map = new LinkedHashMap<>();
                headerMap.forEach((header, position) -> {
                    String value = record.get(header);
                    map.put(header, value != null ? value.trim() : "");
                });
                payloads.add(map);
            }
            return payloads;
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        log.info("Retrieving record with ID: {}", id);
        try {
            DataRecord record = storage.findById(id);
            if (record != null) {
                log.debug("Successfully retrieved record with ID: {}", id);
                return ResponseEntity.ok(record);
            } else {
                log.warn("Record with ID: {} not found", id);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Failed to retrieve record with ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to retrieve record: " + e.getMessage()));
        }
    }

    @GetMapping("/source/{source}")
    public ResponseEntity<?> getBySource(@PathVariable String source) {
        log.info("Retrieving records by source: {}", source);
        try {
            List<DataRecord> records = storage.findBySource(source);
            log.debug("Retrieved {} records by source: {}", records.size(), source);
            return ResponseEntity.ok(records);
        } catch (Exception e) {
            log.error("Failed to retrieve records by source {}: {}", source, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to retrieve records by source: " + e.getMessage()));
        }
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<?> getByType(@PathVariable String type) {
        log.info("Retrieving records by type: {}", type);
        try {
            List<DataRecord> records = storage.findByType(type);
            log.debug("Retrieved {} records by type: {}", records.size(), type);
            return ResponseEntity.ok(records);
        } catch (Exception e) {
            log.error("Failed to retrieve records by type {}: {}", type, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to retrieve records by type: " + e.getMessage()));
        }
    }

    private DataRecord saveToMemoryAndPersistence(String source, String type, Map<String, Object> payload) {
        DataRecord saved = storage.save(source, type, payload);
        try {
            persistenceService.saveViaJpa(source, type, payload);
            return saved;
        } catch (Exception e) {
            storage.removeById(saved.id());
            throw new IllegalStateException("Failed to persist record to database", e);
        }
    }

    private List<DataRecord> saveBatchToMemoryAndPersistence(String source, String type, List<Map<String, Object>> payloads) {
        List<DataRecord> savedRecords = storage.saveBatch(source, type, payloads);
        try {
            persistenceService.saveBatchViaJpa(source, type, payloads);
            return savedRecords;
        } catch (Exception e) {
            for (DataRecord savedRecord : savedRecords) {
                storage.removeById(savedRecord.id());
            }
            throw new IllegalStateException("Failed to persist records to database", e);
        }
    }
}
