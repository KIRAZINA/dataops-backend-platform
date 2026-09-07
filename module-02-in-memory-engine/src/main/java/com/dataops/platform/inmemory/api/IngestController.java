package com.dataops.platform.inmemory.api;

import com.dataops.platform.common.exception.RecordValidationException;
import com.dataops.platform.common.exception.ValidationIssue;
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

    /**
     * Removes a leading UTF-8 BOM ({@code U+FEFF}) if present. Otherwise returns
     * the input unchanged. Returns {@code null} if the input is {@code null}.
     */
    public static String stripUtf8Bom(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        if (input.charAt(0) == '\uFEFF') {
            return input.substring(1);
        }
        return input;
    }

    private Map<String, Object> parseXmlContent(String xml) {
        try {
            return xmlMapper.readValue(xml, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse XML payload", e);
        }
    }

    private List<Map<String, Object>> parseCsvContentMultipleRows(String csv) {
        String normalized = stripUtf8Bom(csv);
        List<ValidationIssue> issues = new ArrayList<>();
        List<Map<String, Object>> payloads = new ArrayList<>();

        try (CSVParser parser = CSVParser.parse(normalized,
                CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            // Duplicate header detection: parse the header line directly so we can
            // catch repeated names before the per-row loop runs.
            String[] headers = parser.getHeaderNames().toArray(new String[0]);
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (String h : headers) {
                if (!seen.add(h)) {
                    issues.add(ValidationIssue.of("csv.header",
                            "duplicate CSV header: '" + h + "'"));
                }
            }
            if (!issues.isEmpty()) {
                throw new RecordValidationException(issues, 0);
            }

            List<org.apache.commons.csv.CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                throw new IllegalArgumentException("CSV has no data rows");
            }

            int expectedColumns = parser.getHeaderMap().size();
            int rowNumber = 1;
            int recordIdx = 0;
            for (var record : records) {
                rowNumber++;
                // Detect ragged rows BEFORE accessing cells — accessing a header
                // that doesn't exist in a short row throws IllegalArgumentException
                // inside Apache Commons CSV, which we don't want to mistake for a
                // parse failure.
                if (record.size() != expectedColumns) {
                    issues.add(ValidationIssue.of(rowNumber, "csv.row",
                            "expected " + expectedColumns + " columns, got " + record.size()));
                    recordIdx++;
                    continue;
                }
                Map<String, Object> map = new LinkedHashMap<>();
                for (String header : headers) {
                    String raw = record.isMapped(header) ? record.get(header) : null;
                    Object value;
                    // Empty field semantics: empty string -> null. Per the
                    // documented contract, the parser is responsible for this
                    // distinction; the normaliser never sees the empty string.
                    if (raw == null || raw.isEmpty()) {
                        value = null;
                    } else {
                        value = coerceScalar(raw);
                    }
                    map.put(header, value);
                }
                payloads.add(map);
                recordIdx++;
            }
        } catch (RecordValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CSV payload", e);
        }

        if (!issues.isEmpty()) {
            throw new RecordValidationException(issues, 0);
        }
        return payloads;
    }

    /**
     * Numeric coercion for CSV cell values. Strings that parse as integers
     * become {@link Long}; everything else stays a {@link String}. This is a
     * parsing concern (the consumer expects typed numbers, not stringified
     * ones) so it lives here rather than in the normalisation layer.
     */
    private static Object coerceScalar(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }
}

