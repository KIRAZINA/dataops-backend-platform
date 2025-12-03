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
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        DataRecord saved = storage.save("api", "JSON", payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping(value = "/csv", consumes = "text/csv")
    public ResponseEntity<DataRecord> ingestCsv(@RequestBody String csv) throws Exception {
        Map<String, Object> payload = parseCsvContent(csv);
        DataRecord saved = storage.save("api", "CSV", payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping(value = "/xml", consumes = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<DataRecord> ingestXml(@RequestBody String xml) throws Exception {
        Map<String, Object> map = xmlMapper.readValue(xml, new TypeReference<>() {});
        DataRecord saved = storage.save("api", "XML", map);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> ingestFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "JSON") String type) throws Exception {

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        Map<String, Object> payload = switch (type.toUpperCase()) {
            case "JSON" -> jsonMapper.readValue(content, new TypeReference<>() {});
            case "XML" -> xmlMapper.readValue(content, new TypeReference<>() {});
            case "CSV" -> parseCsvContent(content);
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };

        DataRecord saved = storage.save("file-upload", type.toUpperCase(), payload);
        return ResponseEntity.ok("Ingested successfully. ID = " + saved.id());
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

    @GetMapping("/{id}")
    public ResponseEntity<DataRecord> getById(@PathVariable String id) {
        DataRecord record = storage.findById(id);
        return record != null ? ResponseEntity.ok(record) : ResponseEntity.notFound().build();
    }

    @GetMapping("/source/{source}")
    public List<DataRecord> getBySource(@PathVariable String source) {
        return storage.findBySource(source);
    }

    @GetMapping("/type/{type}")
    public List<DataRecord> getByType(@PathVariable String type) {
        return storage.findByType(type);
    }
}