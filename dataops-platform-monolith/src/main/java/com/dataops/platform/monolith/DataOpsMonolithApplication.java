package com.dataops.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.dataops.platform")
@EntityScan(basePackages = "com.dataops.platform")
public class DataOpsMonolithApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataOpsMonolithApplication.class, args);
    }
}