package com.dataops.platform.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * RateLimitConfig integration test driving the real interceptor end-to-end.
 *
 * <p>Uses {@link MockMvc} with a stub {@code /api/v1/ping} controller so we can
 * dispatch 100+ requests quickly without depending on real network I/O.
 *
 * <p>The interceptor identifies "per IP" via the {@code X-Forwarded-For} header
 * first, falling back to {@code remoteAddr}. This test exercises both signals.
 */
@DisplayName("RateLimitConfig enforcement")
class RateLimitConfigTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PingController())
                .addInterceptors(new RateLimitConfig.RateLimitInterceptor())
                .build();
    }

    @Test
    @DisplayName("Single IP under 100 requests is allowed")
    void underLimitIsAllowed() throws Exception {
        for (int i = 0; i < 99; i++) {
            MvcResult res = mockMvc.perform(get("/api/v1/ping").header("X-Forwarded-For", "10.0.0.1"))
                    .andReturn();
            assertEquals(200, res.getResponse().getStatus(),
                    "Request " + (i + 1) + " should be allowed but got " + res.getResponse().getStatus());
        }
    }

    @Test
    @DisplayName("Single IP over 100 requests returns 429 with structured JSON body")
    void overLimitReturns429() throws Exception {
        // burn through 100 allowed
        for (int i = 0; i < 100; i++) {
            mockMvc.perform(get("/api/v1/ping").header("X-Forwarded-For", "10.0.0.2"))
                    .andReturn();
        }
        // 101st should be rate-limited
        MvcResult rejected = mockMvc.perform(get("/api/v1/ping").header("X-Forwarded-For", "10.0.0.2"))
                .andReturn();
        assertEquals(429, rejected.getResponse().getStatus(),
                "101st request from same IP should be rejected with 429");
        String body = rejected.getResponse().getContentAsString();
        assertNotNull(body);
        assertTrue(body.contains("Too Many Requests"),
                "429 body should mention Too Many Requests but was: " + body);
        assertTrue(body.contains("Rate limit exceeded"),
                "429 body should explain the cause but was: " + body);
    }

    @Test
    @DisplayName("Different IPs have independent rate-limit buckets")
    void differentIpsAreIndependent() throws Exception {
        // exhaust bucket for IP A
        for (int i = 0; i < 101; i++) {
            mockMvc.perform(get("/api/v1/ping").header("X-Forwarded-For", "10.0.0.3"))
                    .andReturn();
        }
        // IP B should still be unaffected
        for (int i = 0; i < 50; i++) {
            MvcResult res = mockMvc.perform(get("/api/v1/ping").header("X-Forwarded-For", "10.0.0.4"))
                    .andReturn();
            assertEquals(200, res.getResponse().getStatus(),
                    "Different IP should not be affected by another IP's bucket");
        }
    }

    @Test
    @DisplayName("Rate-limit response carries X-RateLimit-* headers on every response")
    void rateLimitHeadersAlwaysPresent() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/ping").header("X-Forwarded-For", "10.0.0.5"))
                .andReturn();
        assertEquals(200, res.getResponse().getStatus());
        assertNotNull(res.getResponse().getHeader("X-RateLimit-Limit"));
        assertNotNull(res.getResponse().getHeader("X-RateLimit-Remaining"));
        assertNotNull(res.getResponse().getHeader("X-RateLimit-Reset"));
        assertEquals("100", res.getResponse().getHeader("X-RateLimit-Limit"));
        assertEquals("99", res.getResponse().getHeader("X-RateLimit-Remaining"),
                "Remaining should drop by 1 per request");
    }

    @Test
    @DisplayName("Falls back to remoteAddr when X-Forwarded-For is absent")
    void fallsBackToRemoteAddr() throws Exception {
        // No X-Forwarded-For header set — remoteAddr defaults to "127.0.0.1" in MockMvc.
        // Two requests from the same remoteAddr share a bucket.
        MvcResult r1 = mockMvc.perform(get("/api/v1/ping")).andReturn();
        MvcResult r2 = mockMvc.perform(get("/api/v1/ping")).andReturn();
        assertEquals(200, r1.getResponse().getStatus());
        assertEquals(200, r2.getResponse().getStatus());
        assertEquals("98", r2.getResponse().getHeader("X-RateLimit-Remaining"),
                "Without XFF header, the interceptor must still count requests against a single bucket");
    }

    @RestController
    @RequestMapping("/api/v1")
    static class PingController {
        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }
    }
}
