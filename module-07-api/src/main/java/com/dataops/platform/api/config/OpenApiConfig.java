package com.dataops.platform.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for OpenAPI/Swagger documentation and CORS policy.
 * CORS origins are configurable via application.yml (app.api.cors.allowed-origins)
 * to support different environments (dev, test, prod).
 */
@Slf4j
@Configuration
public class OpenApiConfig {

    @Value("${app.api.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String allowedOrigins;

    @Bean
    public OpenAPI dataOpsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DataOps Backend Platform")
                        .description("High-performance data ingestion, storage & analytics engine")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("DataOps Team")
                                .email("team@dataops.dev")))
                .addServersItem(new Server().url("/").description("Default Server"));
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] origins = allowedOrigins.split(",");
                
                log.info("Configuring CORS for {} origin(s)", origins.length);
                for (String origin : origins) {
                    log.debug("Allowing CORS origin: {}", origin.trim());
                }

                registry.addMapping("/api/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}