package com.dataops.platform.api.config;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies GlobalExceptionHandler maps every exception type it claims to handle
 * to the documented HTTP status with the documented ErrorResponse shape.
 */
@DisplayName("GlobalExceptionHandler mapping")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("IllegalArgumentException maps to 400 with the exception message")
    void illegalArgumentMapsTo400() throws Exception {
        mockMvc.perform(get("/api/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("bad arg from controller"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("IllegalStateException maps to 500 with the exception message")
    void illegalStateMapsTo500() throws Exception {
        mockMvc.perform(get("/api/test/illegal-state"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("broken state from controller"));
    }

    @Test
    @DisplayName("ConstraintViolationException maps to 400")
    void constraintViolationMapsTo400() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/test/constraint-violation")).andReturn();
        // Verify either the exception handler caught it (status 400) or the test setup needs work.
        // The current stub controller throws ConstraintViolationException directly; the handler's
        // @ExceptionHandler({IllegalArgumentException, MethodArgumentTypeMismatchException, ConstraintViolationException})
        // should map it to 400.
        assertEquals(400, result.getResponse().getStatus(),
                "ConstraintViolationException should map to 400, got: " + result.getResponse().getStatus()
                        + " body=" + result.getResponse().getContentAsString());
        // body should contain the standard envelope (timestamp, status, error, message)
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"status\":400") || body.contains("\"status\": 400"),
                "Body should include status=400: " + body);
    }

    @Test
    @DisplayName("Unhandled exception is caught and returns 500 with a generic message (no internals leaked)")
    void unhandledExceptionMapsTo500Generic() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/test/unhandled")).andReturn();
        assertEquals(500, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        assertNotNull(body);
        assertTrue(body.contains("An unexpected error occurred"),
                "Generic 500 body should use safe wording. Was: " + body);
        // must not leak the underlying exception message
        assertTrue(!body.contains("internal-secret-stacktrace"),
                "Generic 500 must not leak the underlying exception message. Was: " + body);
    }

    @Test
    @DisplayName("All error responses include the structured envelope (timestamp, status, error, message)")
    void allResponsesHaveEnvelope() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/test/illegal-argument")).andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"timestamp\""));
        assertTrue(body.contains("\"status\""));
        assertTrue(body.contains("\"error\""));
        assertTrue(body.contains("\"message\""));
        assertEquals(MediaType.APPLICATION_JSON_VALUE,
                result.getResponse().getContentType(),
                "Error responses should be JSON-encoded");
    }

    @RestController
    @RequestMapping("/api/test")
    static class StubController {

        @GetMapping("/illegal-argument")
        public String illegalArgument() {
            throw new IllegalArgumentException("bad arg from controller");
        }

        @GetMapping("/illegal-state")
        public String illegalState() {
            throw new IllegalStateException("broken state from controller");
        }

        @GetMapping("/constraint-violation")
        public String constraintViolation(@RequestParam String p) {
            throw new ConstraintViolationException("cv from controller", Set.of());
        }

        @GetMapping("/unhandled")
        public String unhandled() {
            throw new RuntimeException("internal-secret-stacktrace");
        }
    }
}
