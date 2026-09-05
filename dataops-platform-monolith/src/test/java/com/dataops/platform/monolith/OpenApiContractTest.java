package com.dataops.platform.monolith;

import com.dataops.platform.monolith.DataOpsMonolithApplication;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAPI contract tests.
 *
 * <p>Two-part contract:
 * <ol>
 *   <li>The generated spec is well-formed OpenAPI 3.x and parses without errors.</li>
 *   <li>Every controller method annotated with {@code @RequestMapping}/{@code @GetMapping}/{@code @PostMapping}
 *       has a corresponding path in the spec — catches future endpoints added without OpenAPI annotations.</li>
 * </ol>
 */
@SpringBootTest(
        classes = DataOpsMonolithApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:openapitest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "app.kafka.enabled=false",
        "API_KEY=test-api-key"
})
@DisplayName("OpenAPI contract tests")
class OpenApiContractTest {

    @Autowired
    TestRestTemplate restTemplate;

    @LocalServerPort
    int port;

    @Test
    @DisplayName("Generated /v3/api-docs is well-formed OpenAPI 3.x")
    void generatedSpecIsValidOpenApi() {
        String specJson = restTemplate.getForObject(
                "http://localhost:" + port + "/v3/api-docs", String.class);
        assertNotNull(specJson, "Spec endpoint must return a body");

        var parsed = new OpenAPIV3Parser().readContents(specJson);
        assertNotNull(parsed);
        assertNotNull(parsed.getOpenAPI(), "Spec must parse as a valid OpenAPI document");

        OpenAPI api = parsed.getOpenAPI();
        assertNotNull(api.getPaths(), "OpenAPI document must declare at least one path");
        assertFalse(api.getPaths().isEmpty(), "Generated spec must include paths for the controllers");
        assertNotNull(api.getInfo(), "Generated spec must have Info section");
    }

    @Test
    @DisplayName("Spec includes every controller endpoint declared in the codebase")
    void specContainsAllControllerEndpoints() {
        String specJson = restTemplate.getForObject(
                "http://localhost:" + port + "/v3/api-docs", String.class);
        OpenAPI api = new OpenAPIV3Parser().readContents(specJson).getOpenAPI();

        Set<String> specPaths = normalizePaths(api.getPaths().keySet());

        Set<String> controllerPaths = new ReflectiveControllerEnumerator().allPaths();

        List<String> missing = new ArrayList<>();
        for (String cp : controllerPaths) {
            if (!specPaths.contains(cp)) {
                missing.add(cp);
            }
        }
        assertTrue(missing.isEmpty(),
                "These controller endpoints are missing from the OpenAPI spec: " + missing
                        + "\nDeclared controller paths: " + controllerPaths
                        + "\nSpec paths: " + specPaths);
    }

    private static Set<String> normalizePaths(Set<String> raw) {
        Set<String> out = new HashSet<>();
        for (String p : raw) {
            String n = p;
            if (!n.startsWith("/")) n = "/" + n;
            if (n.endsWith("/") && n.length() > 1) n = n.substring(0, n.length() - 1);
            out.add(n);
        }
        return out;
    }

    static class ReflectiveControllerEnumerator {

        private static final String[] CONTROLLER_PACKAGES = {
                "com.dataops.platform.inmemory.api",
                "com.dataops.platform.analytics.api",
                "com.dataops.platform.filestorage.api",
        };

        Set<String> allPaths() {
            Set<String> paths = new HashSet<>();
            for (String pkg : CONTROLLER_PACKAGES) {
                collectFromPackage(pkg, paths);
            }
            return paths;
        }

        private void collectFromPackage(String pkg, Set<String> out) {
            String pkgPath = pkg.replace('.', '/');
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            java.net.URL resource = cl.getResource(pkgPath);
            if (resource == null) return;
            try {
                java.io.File dir = new java.io.File(resource.toURI());
                if (!dir.isDirectory()) return;
                for (java.io.File f : dir.listFiles()) {
                    if (!f.getName().endsWith(".class")) continue;
                    String className = pkg + "." + f.getName().substring(0, f.getName().length() - 6);
                    try {
                        Class<?> clazz = Class.forName(className);
                        if (clazz.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class)
                                || clazz.isAnnotationPresent(org.springframework.stereotype.Controller.class)) {
                            collectFromClass(clazz, out);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }

        private void collectFromClass(Class<?> clazz, Set<String> out) {
            String classPrefix = "";
            org.springframework.web.bind.annotation.RequestMapping classMapping =
                    clazz.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
            if (classMapping != null && classMapping.value().length > 0) {
                classPrefix = String.join("", classMapping.value());
            }
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.isSynthetic()) continue;
                String methodPath = "";
                for (java.lang.annotation.Annotation a : m.getAnnotations()) {
                    if (a instanceof org.springframework.web.bind.annotation.RequestMapping rm) {
                        methodPath = String.join("", rm.value());
                        break;
                    } else if (a instanceof org.springframework.web.bind.annotation.GetMapping gm) {
                        methodPath = String.join("", gm.value());
                        break;
                    } else if (a instanceof org.springframework.web.bind.annotation.PostMapping pm) {
                        methodPath = String.join("", pm.value());
                        break;
                    } else if (a instanceof org.springframework.web.bind.annotation.PutMapping pum) {
                        methodPath = String.join("", pum.value());
                        break;
                    } else if (a instanceof org.springframework.web.bind.annotation.DeleteMapping dm) {
                        methodPath = String.join("", dm.value());
                        break;
                    } else if (a instanceof org.springframework.web.bind.annotation.PatchMapping pam) {
                        methodPath = String.join("", pam.value());
                        break;
                    }
                }
                if (methodPath.isEmpty()) continue;
                String full = classPrefix + methodPath;
                if (!full.startsWith("/")) full = "/" + full;
                out.add(full);
            }
        }
    }
}
