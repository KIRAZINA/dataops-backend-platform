package com.dataops.platform.filestorage.async;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Component
public class AsyncFileWriter {

    @Async
    public CompletableFuture<Path> writeAsync(Path path, byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(path.getParent());
                Files.write(path, data);
                return path;
            } catch (Exception e) {
                throw new RuntimeException("Async write failed", e);
            }
        });
    }
}