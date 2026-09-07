package com.dataops.platform.monolith.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Async} support for future async methods.
 *
 * <p>Previously this class also defined a {@code fileWriterTaskExecutor} bean; that
 * executor has been removed because its only consumer ({@code AsyncFileWriter}) was
 * dead code with no production callers. Add new executors here when there is a
 * concrete async method to back them.
 *
 * <p>{@code @EnableScheduling} is also declared here so the transactional
 * outbox relay ({@code OutboxRelayService.processOutbox} /
 * {@code purgePublishedEvents}) polls when {@code app.kafka.enabled=true}.
 * With the default ({@code false}) the relay bean is absent and no scheduled
 * outbox task fires.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
