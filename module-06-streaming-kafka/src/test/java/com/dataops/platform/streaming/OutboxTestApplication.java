package com.dataops.platform.streaming;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal bootstrap for module-06 slice tests ({@code @DataJpaTest}).
 * The module ships no production application class, so slice tests need a
 * {@code @SpringBootConfiguration} in a parent package to locate entities
 * and repositories under {@code com.dataops.platform.streaming}.
 */
@SpringBootApplication
public class OutboxTestApplication {
}
