package com.dataops.platform.monolith.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Profiles in which a missing/blank API key is permitted (e.g. local developer startup).
     * In any other profile, the application fails fast at startup with a clear error.
     */
    private static final Set<String> RELAXED_PROFILES = new HashSet<>(Arrays.asList("dev", "local", "test"));

    @Value("${app.security.api-key:}")
    private String apiKey;

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateApiKey() {
        boolean relaxed = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(RELAXED_PROFILES::contains);
        if (apiKey == null || apiKey.isBlank()) {
            if (relaxed) {
                return;
            }
            throw new IllegalStateException(
                    "app.security.api-key is not configured. Set the API_KEY environment variable "
                            + "(or app.security.api-key property) before starting the application in a non-dev profile.");
        }
        if ("change-me".equals(apiKey)) {
            throw new IllegalStateException(
                    "app.security.api-key is set to the insecure default value 'change-me'. "
                            + "Configure a real API key via the API_KEY environment variable.");
        }
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(new AntPathRequestMatcher("/actuator/health"),
                                         new AntPathRequestMatcher("/actuator/info"),
                                         new AntPathRequestMatcher("/actuator/prometheus")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/api/**")).authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new ApiKeyAuthFilter(apiKey), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    static class ApiKeyAuthFilter extends OncePerRequestFilter {

        private static final String API_KEY_HEADER = "X-API-Key";
        private final String requiredApiKey;

        ApiKeyAuthFilter(String requiredApiKey) {
            this.requiredApiKey = requiredApiKey;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            if (!request.getRequestURI().startsWith("/api/")) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = request.getHeader(API_KEY_HEADER);
            if (token == null || token.isBlank()) {
                token = request.getParameter("apiKey");
            }

            if (requiredApiKey == null || requiredApiKey.isBlank() || !requiredApiKey.equals(token)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid API key\"}");
                return;
            }

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("api-key", null, Collections.emptyList())
            );
            filterChain.doFilter(request, response);
        }
    }
}
