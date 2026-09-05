package com.dataops.platform.monolith.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} support for future async methods.
 *
 * <p>Previously this class also defined a {@code fileWriterTaskExecutor} bean; that
 * executor has been removed because its only consumer ({@code AsyncFileWriter}) was
 * dead code with no production callers. Add new executors here when there is a
 * concrete async method to back them.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
