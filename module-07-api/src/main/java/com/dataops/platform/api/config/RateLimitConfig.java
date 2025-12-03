package com.dataops.platform.api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor())
                .addPathPatterns("/api/**");
    }

    private static class RateLimitInterceptor implements HandlerInterceptor {

        private final com.github.benmanes.caffeine.cache.Cache<String, AtomicInteger> rateLimitCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.MINUTES)
                        .build();

        @Override
        public boolean preHandle(HttpServletRequest request,
                                 HttpServletResponse response,
                                 Object handler) throws IOException {

            String clientIp = getClientIp(request);
            AtomicInteger counter = rateLimitCache.get(clientIp, k -> new AtomicInteger(0));
            int current = counter.incrementAndGet();

            response.setHeader("X-RateLimit-Limit", "100");
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, 100 - current)));
            response.setHeader("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond()));

            if (current > 100) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // 429
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again in 60 seconds.\"}"
                );
                return false;
            }

            return true;
        }

        private String getClientIp(HttpServletRequest request) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
    }
}