package com.dataops.platform.inmemory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.dataops.platform")
public class InMemoryEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(InMemoryEngineApplication.class, args);
    }
}
