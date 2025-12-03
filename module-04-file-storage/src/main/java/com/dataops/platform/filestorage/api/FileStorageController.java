package com.dataops.platform.filestorage.api;

import com.dataops.platform.filestorage.service.FileExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/storage/export")
@RequiredArgsConstructor
public class FileStorageController {

    private final FileExportService exportService;

    @GetMapping("/json")
    public ResponseEntity<byte[]> exportJson() throws IOException {
        return exportService.exportAsJson();
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportCsv() throws IOException {
        return exportService.exportAsCsv();
    }

    @GetMapping("/binary")
    public ResponseEntity<String> exportBinary() {
        return ResponseEntity.ok()
                .body("Binary export (Parquet/Avro) — roadmap for v2.0");
    }
}