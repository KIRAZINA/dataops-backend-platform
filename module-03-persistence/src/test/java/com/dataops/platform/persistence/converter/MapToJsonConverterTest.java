package com.dataops.platform.persistence.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MapToJsonConverter Tests")
class MapToJsonConverterTest {

    private final MapToJsonConverter converter = new MapToJsonConverter();

    @Test
    @DisplayName("Should serialize and deserialize payload map")
    void roundTripPayload() {
        Map<String, Object> payload = Map.of(
                "name", "record",
                "value", 42,
                "nested", Map.of("enabled", true),
                "items", List.of("a", "b")
        );

        String json = converter.convertToDatabaseColumn(payload);
        Map<String, Object> restored = converter.convertToEntityAttribute(json);

        assertTrue(json.contains("\"name\":\"record\""));
        assertEquals("record", restored.get("name"));
        assertEquals(42, ((Number) restored.get("value")).intValue());
        assertTrue(restored.get("nested") instanceof Map);
        assertTrue(restored.get("items") instanceof List);
    }

    @Test
    @DisplayName("Should return empty map for blank database values")
    void blankDatabaseValueReturnsEmptyMap() {
        assertTrue(converter.convertToEntityAttribute("").isEmpty());
        assertTrue(converter.convertToEntityAttribute("   ").isEmpty());
    }

    @Test
    @DisplayName("Should serialize null attribute as empty object")
    void nullAttributeSerializesAsEmptyObject() {
        String json = converter.convertToDatabaseColumn(null);
        assertEquals("{}", json);
    }
}
