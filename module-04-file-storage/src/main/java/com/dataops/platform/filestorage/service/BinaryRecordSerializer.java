package com.dataops.platform.filestorage.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public final class BinaryRecordSerializer {

    private BinaryRecordSerializer() {}

    public static void writeRecord(DataOutputStream out,
                                   Long id, String source, String type,
                                   LocalDateTime ingestedAt,
                                   Map<String, Object> payload) throws IOException {

        out.writeLong(id != null ? id : 0L);
        writeString(out, source);
        writeString(out, type);
        out.writeLong(ingestedAt.toEpochSecond(ZoneOffset.UTC));

        out.writeInt(payload.size());
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            writeString(out, e.getKey());
            writeObject(out, e.getValue());
        }
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void writeObject(DataOutputStream out, Object o) throws IOException {
        if (o instanceof String s) {
            out.writeByte(1);
            writeString(out, s);
        } else if (o instanceof Number n) {
            out.writeByte(2);
            out.writeDouble(n.doubleValue());
        } else if (o instanceof Boolean b) {
            out.writeByte(3);
            out.writeBoolean(b);
        } else {
            out.writeByte(0); // null
        }
    }
}