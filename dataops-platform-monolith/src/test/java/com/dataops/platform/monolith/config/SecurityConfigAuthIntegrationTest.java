package com.dataops.platform.monolith.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end authentication and authorization tests for SecurityConfig.
 *
 * <p>Exercises the actual Spring Security filter chain (including the
 * {@code ApiKeyAuthFilter}) and the path-based authorization rules declared
 * in {@link SecurityConfig}. The startup {@code @PostConstruct validateApiKey()}
 * check is covered separately by {@link SecurityConfigValidationTest}.
 *
 * <p>Runs against a self-contained Spring Boot slice that imports
 * {@link SecurityConfig} plus one stub controller so the filter chain has
 * something to authenticate. Avoids the full monolith's DataSource/JPA stack
 * to keep the test fast and isolated.
 */
@SpringBootTest(
        classes = SecurityConfigAuthIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.kafka.enabled=false",
        "API_KEY=test-api-key",
        "spring.main.web-application-type=servlet",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
@DisplayName("SecurityConfig filter chain integration")
class SecurityConfigAuthIntegrationTest {

    @SpringBootApplication(exclude = SecurityAutoConfiguration.class)
    @ComponentScan(
            basePackages = "com.dataops.platform.monolith",
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {
                            SecurityConfig.class,
                            com.dataops.platform.monolith.config.AsyncConfig.class
                    }))
    @Import({SecurityConfig.class, PingController.class})
    static class TestApp {
    }

    @RestController
    @RequestMapping("/api/v1")
    static class PingController {
        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("API key via X-API-Key header grants access to /api/**")
    void validApiKeyHeaderAllowsAccess() throws Exception {
        mockMvc.perform(get("/api/v1/ping")
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("API key via ?apiKey= query param also grants access")
    void validApiKeyQueryParamAllowsAccess() throws Exception {
        mockMvc.perform(get("/api/v1/ping?apiKey=test-api-key"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Missing API key returns 401 with structured JSON body")
    void missingApiKeyReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid API key"));
    }

    @Test
    @DisplayName("Wrong API key returns 401 with the same JSON body shape")
    void wrongApiKeyReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/ping")
                        .header("X-API-Key", "definitely-not-the-real-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid API key"));
    }

    @Test
    @DisplayName("Blank API key header is treated as missing")
    void blankApiKeyReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/ping")
                        .header("X-API-Key", "   "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/** without API key returns 401")
    void postToApiRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/ping")
                        .contentType("application/json")
                        .content("{\"value\":42}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/** with valid API key passes the auth filter (not 401)")
    void postToApiWithKeyPassesAuth() throws Exception {
        mockMvc.perform(post("/api/v1/ping")
                        .header("X-API-Key", "test-api-key")
                        .contentType("application/json")
                        .content("{\"value\":42}"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 401) {
                        throw new AssertionError("Authenticated POST should not return 401; got " + s);
                    }
                });
    }

    @Test
    @DisplayName("Non-api paths outside actuator do not require auth (permitAll)")
    void nonApiPathsArePermitAll() throws Exception {
        // /v3/api-docs is a public OpenAPI endpoint and should not require auth.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
