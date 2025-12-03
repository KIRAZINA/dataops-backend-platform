package com.dataops.platform.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dataOpsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DataOps Backend Platform")
                        .description("High-performance data ingestion, storage & analytics engine")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Legend Developer")
                                .email("you@legend.dev")))
                .addServersItem(new Server().url("/").description("Default Server"));
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*")
                        .allowedMethods("*")
                        .allowedHeaders("*");
            }
        };
    }
}