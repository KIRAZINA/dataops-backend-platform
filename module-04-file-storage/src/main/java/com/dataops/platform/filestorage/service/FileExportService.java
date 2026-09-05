package com.dataops.platform.filestorage.service;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.service.PersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
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

    private final PersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    public FileExportService(PersistenceService persistenceService, ObjectMapper objectMapper) {
        this.persistenceService = persistenceService;
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
    public ResponseEntity<StreamingResponseBody> exportAsJson() throws IOException {
        List<DataRecord> records = persistenceService.findAll().stream()
                .map(this::toDataRecord)
                .toList();

        StreamingResponseBody body = outputStream -> objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputStream, records);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dataops_" + timestamp() + ".json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * Export all records as CSV with proper escaping.
     * Read-only transaction to ensure consistent snapshot of data.
     *
     * @return ResponseEntity with CSV file attachment
     * @throws IOException if serialization fails
     */
    @Transactional(readOnly = true)
    public ResponseEntity<StreamingResponseBody> exportAsCsv() throws IOException {
        List<DataRecord> records = persistenceService.findAll().stream()
                .map(this::toDataRecord)
                .toList();

        StreamingResponseBody body = outputStream -> {
            try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
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
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dataops_" + timestamp() + ".csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(body);
    }

    private String timestamp() {
        return Instant.now().atZone(ZoneOffset.UTC).format(FILE_TS);
    }

    /**
     * Export all records as a custom binary format produced by
     * {@link BinaryRecordSerializer}.
     *
     * <p>The output is a length-prefixed sequence of records. Each record frame
     * begins with a 4-byte {@code int} record-size, followed by the record body
     * (see {@link BinaryRecordSerializer} for the body schema). The stream is
     * fully buffered before responding so the {@code Content-Length} header is
     * accurate and clients can detect truncation.
     *
     * <p>Read-only transaction to ensure a consistent snapshot of data.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportAsBinary() throws IOException {
        List<PersistedRecord> records = persistenceService.findAll();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024 + records.size() * 256);
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(records.size());
            for (PersistedRecord p : records) {
                ByteArrayOutputStream frame = new ByteArrayOutputStream(256);
                try (DataOutputStream frameOut = new DataOutputStream(frame)) {
                    BinaryRecordSerializer.writeRecord(
                            frameOut,
                            p.getId(),
                            p.getSource(),
                            p.getType(),
                            p.getIngestedAt(),
                            p.getPayload());
                }
                byte[] body = frame.toByteArray();
                out.writeInt(body.length);
                out.write(body);
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=dataops_" + timestamp() + ".bin")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(baos.toByteArray());
    }

    private DataRecord toDataRecord(com.dataops.platform.persistence.entity.PersistedRecord persisted) {
        return DataRecord.builder()
                .key(String.valueOf(persisted.getId()))
                .source(persisted.getSource())
                .type(persisted.getType())
                .payload(persisted.getPayload())
                .timestamp(persisted.getIngestedAt().atZone(ZoneOffset.systemDefault()).toInstant())
                .build();
    }
}
