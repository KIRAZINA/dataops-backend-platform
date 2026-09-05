package com.dataops.platform.filestorage.api;

import com.dataops.platform.filestorage.service.FileExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/storage/export")
@RequiredArgsConstructor
public class FileStorageController {

    private final FileExportService exportService;

    @GetMapping("/json")
    public ResponseEntity<StreamingResponseBody> exportJson() throws IOException {
        return exportService.exportAsJson();
    }

    @GetMapping("/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv() throws IOException {
        return exportService.exportAsCsv();
    }

    @GetMapping("/binary")
    public ResponseEntity<byte[]> exportBinary() throws IOException {
        return exportService.exportAsBinary();
    }
}