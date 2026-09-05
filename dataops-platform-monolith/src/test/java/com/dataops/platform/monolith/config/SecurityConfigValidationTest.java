package com.dataops.platform.monolith.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("SecurityConfig fail-fast API key validation")
class SecurityConfigValidationTest {

    private static SecurityConfig build(String apiKey, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        SecurityConfig cfg = new SecurityConfig(env);
        ReflectionTestUtils.setField(cfg, "apiKey", apiKey);
        return cfg;
    }

    @Test
    @DisplayName("Should fail when key is missing in a non-dev profile")
    void failsWhenKeyMissingInProd() {
        assertThrows(IllegalStateException.class, () -> build("", "prod").validateApiKey());
    }

    @Test
    @DisplayName("Should fail when key is the insecure default 'change-me'")
    void failsOnInsecureDefault() {
        assertThrows(IllegalStateException.class, () -> build("change-me", "prod").validateApiKey());
    }

    @Test
    @DisplayName("Should allow missing key in dev profile")
    void allowsMissingKeyInDev() {
        assertDoesNotThrow(() -> build("", "dev").validateApiKey());
    }

    @Test
    @DisplayName("Should allow missing key in local profile")
    void allowsMissingKeyInLocal() {
        assertDoesNotThrow(() -> build("", "local").validateApiKey());
    }

    @Test
    @DisplayName("Should allow missing key in test profile")
    void allowsMissingKeyInTest() {
        assertDoesNotThrow(() -> build("", "test").validateApiKey());
    }

    @Test
    @DisplayName("Should pass when a real key is supplied")
    void passesWithRealKey() {
        assertDoesNotThrow(() -> build("s3cret-real-key", "prod").validateApiKey());
    }
}

