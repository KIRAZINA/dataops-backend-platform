package com.dataops.platform.inmemory.api;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.inmemory.service.InMemoryStorageService;
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
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ingest")
@CrossOrigin(origins = "*")
@OpenAPIDefinition(info = @Info(title = "DataOps In-Memory Engine", version = "1.0"))
@RequiredArgsConstructor
public class IngestController {

    private final InMemoryStorageService storage;

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final XmlMapper xmlMapper = new XmlMapper();

    @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DataRecord> ingestJson(@Valid @RequestBody Map<String, Object> payload) {
        log.info("Received JSON ingestion request");
        DataRecord saved = storage.save("api", "JSON", payload);
        log.info("Successfully saved JSON record with ID: {}", saved.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping(value = "/csv", consumes = "text/csv")
    public ResponseEntity<?> ingestCsv(@RequestBody String csv) {
        log.info("Received CSV ingestion request");
        try {
            List<Map<String, Object>> payloads = parseCsvContentMultipleRows(csv);
            List<DataRecord> saved = storage.saveBatch("api", "CSV", payloads);
            log.info("Successfully saved {} CSV records", saved.size());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
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
            DataRecord saved = storage.save("api", "XML", map);
            log.info("Successfully saved XML record with ID: {}", saved.id());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
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

            // Check file size (e.g., 10MB limit)
            if (file.getSize() > 10 * 1024 * 1024) {
                log.warn("File size exceeds limit: {} bytes", file.getSize());
                return ResponseEntity.badRequest().body(Map.of("error", "File size exceeds 10MB limit"));
            }

            String content = new String(file.getBytes(), StandardCharsets.UTF_8);

            switch (type.toUpperCase()) {
                case "JSON" -> {
                    Map<String, Object> payload = jsonMapper.readValue(content, new TypeReference<>() {});
                    DataRecord saved = storage.save("file-upload", type.toUpperCase(), payload);
                    log.info("Successfully saved JSON file upload with ID: {}", saved.id());
                    return ResponseEntity.ok("Ingested successfully. ID = " + saved.id());
                }
                case "XML" -> {
                    Map<String, Object> payload = xmlMapper.readValue(content, new TypeReference<>() {});
                    DataRecord saved = storage.save("file-upload", type.toUpperCase(), payload);
                    log.info("Successfully saved XML file upload with ID: {}", saved.id());
                    return ResponseEntity.ok("Ingested successfully. ID = " + saved.id());
                }
                case "CSV" -> {
                    List<Map<String, Object>> payloads = parseCsvContentMultipleRows(content);
                    List<DataRecord> saved = storage.saveBatch("file-upload", type.toUpperCase(), payloads);
                    log.info("Successfully saved CSV file upload with {} records", saved.size());
                    return ResponseEntity.ok("Ingested " + saved.size() + " records successfully.");
                }
                default -> {
                    log.warn("Unsupported file type: {}", type);
                    return ResponseEntity.badRequest().body(Map.of("error", "Unsupported type: " + type));
                }
            }
        } catch (Exception e) {
            log.error("Failed to process file upload: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to process file: " + e.getMessage()));
        }
    }

    private Map<String, Object> parseCsvContent(String csv) throws Exception {
        try (CSVParser parser = CSVParser.parse(csv, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            var records = parser.getRecords();
            if (records.isEmpty()) throw new IllegalArgumentException("CSV has no data rows");

            Map<String, Object> map = new LinkedHashMap<>();
            var record = records.get(0);
            parser.getHeaderMap().forEach((header, position) -> {
                String value = record.get(header);
                map.put(header, value != null ? value.trim() : "");
            });
            return map;
        }
    }

    private List<Map<String, Object>> parseCsvContentMultipleRows(String csv) throws Exception {
        try (CSVParser parser = CSVParser.parse(csv, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            var csvRecords = parser.getRecords();
            if (csvRecords.isEmpty()) throw new IllegalArgumentException("CSV has no data rows");

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
}