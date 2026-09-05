package com.dataops.platform.filestorage.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BinaryRecordSerializer round-trip tests")
class BinaryRecordSerializerTest {

    @Test
    @DisplayName("Should round-trip a record with mixed-type payload")
    void roundTripsMixedPayload() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "alpha");
        payload.put("count", 7);
        payload.put("ratio", 1.5);
        payload.put("flag", true);
        payload.put("missing", null);

        LocalDateTime ts = LocalDateTime.of(2026, 1, 2, 3, 4, 5);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            BinaryRecordSerializer.writeRecord(out, 42L, "src", "JSON", ts, payload);
        }

        BinaryRecordSerializer.BinaryRecord read;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            read = BinaryRecordSerializer.readRecord(in);
        }

        assertEquals(42L, read.id());
        assertEquals("src", read.source());
        assertEquals("JSON", read.type());
        assertEquals(ts.toEpochSecond(ZoneOffset.UTC), read.ingestedAtEpochSeconds());
        assertEquals(5, read.payload().size());
        assertEquals("alpha", read.payload().get("name"));
        assertEquals(7.0, read.payload().get("count"));
        assertEquals(1.5, read.payload().get("ratio"));
        assertEquals(Boolean.TRUE, read.payload().get("flag"));
        assertNull(read.payload().get("missing"), "null payload entries should decode as null");
    }

    @Test
    @DisplayName("Should encode a null id as zero")
    void encodesNullIdAsZero() throws Exception {
        Map<String, Object> payload = Map.of("k", "v");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            BinaryRecordSerializer.writeRecord(out, null, "src", "JSON",
                    LocalDateTime.of(2026, 1, 1, 0, 0, 0), payload);
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            BinaryRecordSerializer.BinaryRecord read = BinaryRecordSerializer.readRecord(in);
            assertEquals(0L, read.id());
            assertEquals("src", read.source());
            assertEquals("v", read.payload().get("k"));
        }
    }

    @Test
    @DisplayName("Should treat unsupported payload value types as null rather than throwing")
    void encodesUnsupportedTypesAsNull() throws Exception {
        // arbitrary nested map is not natively supported; serializer writes it as null
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "ok");
        payload.put("nested", Map.of("inner", "value"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            BinaryRecordSerializer.writeRecord(out, 1L, "s", "T",
                    LocalDateTime.of(2026, 1, 1, 0, 0, 0), payload);
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            BinaryRecordSerializer.BinaryRecord read = BinaryRecordSerializer.readRecord(in);
            assertEquals("ok", read.payload().get("name"));
            assertNull(read.payload().get("nested"));
            assertNotNull(read);
        }
    }

    @Test
    @DisplayName("Should preserve insertion order of payload keys via LinkedHashMap")
    void preservesPayloadKeyOrder() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("z", 1);
        payload.put("a", 2);
        payload.put("m", 3);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            BinaryRecordSerializer.writeRecord(out, 1L, "s", "T",
                    LocalDateTime.now(), payload);
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            BinaryRecordSerializer.BinaryRecord read = BinaryRecordSerializer.readRecord(in);
            assertNotNull(read);
            var keys = read.payload().keySet().iterator();
            assertTrue(keys.hasNext());
            assertEquals("z", keys.next());
            assertEquals("a", keys.next());
            assertEquals("m", keys.next());
        }
    }
}
