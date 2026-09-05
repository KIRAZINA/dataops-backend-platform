package com.dataops.platform.inmemory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.dataops.platform")
public class InMemoryEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(InMemoryEngineApplication.class, args);
    }
}
