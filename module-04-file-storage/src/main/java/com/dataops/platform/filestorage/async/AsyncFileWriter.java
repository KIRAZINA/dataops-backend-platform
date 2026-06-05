package com.dataops.platform.filestorage.async;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Component
public class AsyncFileWriter {

    @Async("fileWriterTaskExecutor")
    public CompletableFuture<Path> writeAsync(Path path, byte[] data) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, data);
            return CompletableFuture.completedFuture(path);
        } catch (Exception e) {
            CompletableFuture<Path> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("Async write failed", e));
            return failed;
        }
    }
}