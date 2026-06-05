package com.dataops.platform.inmemory.api;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.inmemory.service.IngestionService;
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
import org.springframework.validation.annotation.Validated;
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
@Validated
@RestController
@RequestMapping("/api/v1/ingest")
@OpenAPIDefinition(info = @Info(title = "DataOps In-Memory Engine", version = "1.0"))
@RequiredArgsConstructor
public class IngestController {

    private final IngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;

    @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> ingestJson(@Valid @RequestBody Map<String, Object> payload) {
        log.info("Received JSON ingestion request");
        DataRecord saved = ingestionService.ingest("api", "JSON", payload);
        log.info("Successfully saved JSON record with ID: {}", saved.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping(value = "/csv", consumes = "text/csv")
    public ResponseEntity<?> ingestCsv(@RequestBody String csv) {
        log.info("Received CSV ingestion request");
        List<Map<String, Object>> payloads = parseCsvContentMultipleRows(csv);
        List<DataRecord> saved = ingestionService.ingestBatch("api", "CSV", payloads);
        log.info("Successfully saved {} CSV records", saved.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping(value = "/xml", consumes = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> ingestXml(@RequestBody String xml) {
        log.info("Received XML ingestion request");
        Map<String, Object> map = parseXmlContent(xml);
        DataRecord saved = ingestionService.ingest("api", "XML", map);
        log.info("Successfully saved XML record with ID: {}", saved.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> ingestFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "JSON") String type) {

        log.info("Received file upload request for type: {}", type);
        if (file.isEmpty()) {
            log.warn("Received empty file upload request");
            throw new IllegalArgumentException("File is empty");
        }

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file", e);
        }
        String normalizedType = type.toUpperCase();

        return switch (normalizedType) {
            case "JSON" -> {
                Map<String, Object> payload = parseJsonContent(content);
                DataRecord saved = ingestionService.ingest("file-upload", normalizedType, payload);
                log.info("Successfully saved JSON file upload with ID: {}", saved.id());
                yield ResponseEntity.ok("Ingested successfully. ID = " + saved.id());
            }
            case "XML" -> {
                Map<String, Object> payload = parseXmlContent(content);
                DataRecord saved = ingestionService.ingest("file-upload", normalizedType, payload);
                log.info("Successfully saved XML file upload with ID: {}", saved.id());
                yield ResponseEntity.ok("Ingested successfully. ID = " + saved.id());
            }
            case "CSV" -> {
                List<Map<String, Object>> payloads = parseCsvContentMultipleRows(content);
                List<DataRecord> saved = ingestionService.ingestBatch("file-upload", normalizedType, payloads);
                log.info("Successfully saved CSV file upload with {} records", saved.size());
                yield ResponseEntity.ok("Ingested " + saved.size() + " records successfully.");
            }
            default -> {
                log.warn("Unsupported file type: {}", type);
                throw new IllegalArgumentException("Unsupported type: " + type);
            }
        };
    }

    private Map<String, Object> parseJsonContent(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JSON payload", e);
        }
    }

    private Map<String, Object> parseXmlContent(String xml) {
        try {
            return xmlMapper.readValue(xml, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse XML payload", e);
        }
    }

    private List<Map<String, Object>> parseCsvContentMultipleRows(String csv) {
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
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CSV payload", e);
        }
    }
}

