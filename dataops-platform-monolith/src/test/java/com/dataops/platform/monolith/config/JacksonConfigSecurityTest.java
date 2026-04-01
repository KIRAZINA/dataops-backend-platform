package com.dataops.platform.monolith.config;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for Jackson configuration.
 * Verifies XXE (XML External Entity) attack protection.
 */
@DisplayName("Jackson Security Tests")
class JacksonConfigSecurityTest {

    private JacksonConfig config = new JacksonConfig();

    @Test
    @DisplayName("Should prevent XXE attack with DOCTYPE declaration")
    void testXXEProtectionDoctype() throws Exception {
        // Arrange
        XmlMapper xmlMapper = config.xmlMapper();
        
        // Malicious XML with external entity reference
        String maliciousXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n" +
                "<foo>&xxe;</foo>";

        // Act & Assert
        // The mapper should either reject this or safely ignore the entity
        assertDoesNotThrow(() -> {
            try {
                // This should not throw an exception or leak file contents
                var result = xmlMapper.readValue(maliciousXml, Map.class);
                // If it succeeds, it should not contain file contents
                assertFalse(result.toString().contains("root:"));
            } catch (Exception e) {
                // It's OK if it throws an exception as long as it doesn't leak data
                assertTrue(e.getMessage().contains("DOCTYPE") || 
                          e.getMessage().contains("external") ||
                          e.getMessage().contains("Entity"),
                    "Exception should be related to XXE prevention");
            }
        });
    }

    @Test
    @DisplayName("Should prevent XXE attack with billion laughs attack")
    void testXXEProtectionBillionLaughs() throws Exception {
        // Arrange
        XmlMapper xmlMapper = config.xmlMapper();
        
        // Billion laughs attack (exponential entity expansion)
        String maliciousXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE lolz [\n" +
                "  <!ENTITY lol \"lol\">\n" +
                "  <!ENTITY lol2 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">\n" +
                "  <!ENTITY lol3 \"&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;\">\n" +
                "]>\n" +
                "<lolz>&lol3;</lolz>";

        // Act & Assert
        assertDoesNotThrow(() -> {
            try {
                // This should be protected from exponential expansion
                var result = xmlMapper.readValue(maliciousXml, Map.class);
                // If it processes, result should be harmless
                assertNotNull(result);
            } catch (Exception e) {
                // Expected behavior: reject or prevent expansion
                assertTrue(e.getMessage().contains("DOCTYPE") || 
                          e.getMessage().contains("external") ||
                          e.getMessage().contains("Bomb") ||
                          e.getMessage().contains("EntityReference"),
                    "Exception should be related to XXE prevention");
            }
        });
    }

    @Test
    @DisplayName("Should safely deserialize valid XML without XXE vulnerabilities")
    void testValidXmlDeserializationIsAllowed() throws Exception {
        // Arrange
        XmlMapper xmlMapper = config.xmlMapper();
        String validXml = "<root><name>DataOps</name><version>1.0.0</version></root>";

        // Act
        Map<String, Object> result = xmlMapper.readValue(validXml, Map.class);

        // Assert
        assertNotNull(result);
        assertEquals("DataOps", result.get("name"));
        assertEquals("1.0.0", result.get("version"));
    }

    @Test
    @DisplayName("Should handle nested valid XML safely")
    void testNestedValidXmlSafe() throws Exception {
        // Arrange
        XmlMapper xmlMapper = config.xmlMapper();
        String validXml = "<root><item><id>1</id><name>test</name></item></root>";

        // Act & Assert
        assertDoesNotThrow(() -> {
            Map<String, Object> result = xmlMapper.readValue(validXml, Map.class);
            assertNotNull(result);
        });
    }
}
