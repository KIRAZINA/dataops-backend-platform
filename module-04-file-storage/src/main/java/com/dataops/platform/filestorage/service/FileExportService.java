package com.dataops.platform.filestorage.service;

import com.dataops.platform.common.model.DataRecord;
import com.dataops.platform.inmemory.service.InMemoryStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FileExportService {

    private final InMemoryStorageService storage;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public ResponseEntity<byte[]> exportAsJson() throws IOException {
        List<DataRecord> records = storage.findAllRecords();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (SequenceWriter writer = mapper.writerWithDefaultPrettyPrinter().writeValues(baos)) {
            for (DataRecord r : records) {
                writer.write(r);
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dataops_" + timestamp() + ".json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(baos.toByteArray());
    }

    public ResponseEntity<byte[]> exportAsCsv() throws IOException {
        List<DataRecord> records = storage.findAllRecords();
        StringBuilder csv = new StringBuilder();
        csv.append("id,source,type,timestamp,payload\n");

        for (DataRecord r : records) {
            String payload = r.getPayload().toString()
                    .replace("\"", "\"\"")
                    .replace("\n", " ")
                    .replace("\r", "");

            csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    r.id(),
                    escapeCsv(r.getSource()),
                    escapeCsv(r.getType()),
                    r.getTimestamp().atOffset(ZoneOffset.UTC),
                    payload));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dataops_" + timestamp() + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString().getBytes());
    }

    private String timestamp() {
        return Instant.now().atZone(ZoneOffset.UTC).format(FILE_TS);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }
}