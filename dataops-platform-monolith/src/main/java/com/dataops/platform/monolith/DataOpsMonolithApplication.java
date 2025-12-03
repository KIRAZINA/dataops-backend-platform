package com.dataops.platform.monolith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.dataops.platform")
public class DataOpsMonolithApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataOpsMonolithApplication.class, args);
    }
}