package com.dataops.platform.filestorage.service;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.inmemory.service.InMemoryStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for exporting data records in various formats (JSON, CSV).
 * Uses injected ObjectMapper for consistent JSON handling.
 * All export operations are transactional with read-only semantics.
 */
@Service
public class FileExportService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final InMemoryStorageService storage;
    private final ObjectMapper objectMapper;

    public FileExportService(InMemoryStorageService storage, ObjectMapper objectMapper) {
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Export all records as JSON with pretty printing.
     * Read-only transaction to ensure consistent snapshot of data.
     *
     * @return ResponseEntity with JSON file attachment
     * @throws IOException if serialization fails
     */
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportAsJson() throws IOException {
        List<DataRecord> records = storage.findAllRecords();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(baos, records);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dataops_" + timestamp() + ".json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(baos.toByteArray());
    }

    /**
     * Export all records as CSV with proper escaping.
     * Read-only transaction to ensure consistent snapshot of data.
     *
     * @return ResponseEntity with CSV file attachment
     * @throws IOException if serialization fails
     */
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportAsCsv() throws IOException {
        List<DataRecord> records = storage.findAllRecords();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("ID", "Source", "Type", "Timestamp", "Payload")
                     .build())) {

            for (DataRecord r : records) {
                String payloadJson = objectMapper.writeValueAsString(r.getPayload());
                csvPrinter.printRecord(
                        r.id(),
                        r.getSource(),
                        r.getType(),
                        r.getTimestamp().atOffset(ZoneOffset.UTC),
                        payloadJson
                );
            }
            csvPrinter.flush();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dataops_" + timestamp() + ".csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(baos.toByteArray());
    }

    private String timestamp() {
        return Instant.now().atZone(ZoneOffset.UTC).format(FILE_TS);
    }
}
