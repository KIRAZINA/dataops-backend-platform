package com.dataops.platform.filestorage.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BinaryRecordSerializer {

    private static final byte TYPE_NULL = 0;
    private static final byte TYPE_STRING = 1;
    private static final byte TYPE_NUMBER = 2;
    private static final byte TYPE_BOOLEAN = 3;

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

    /**
     * Read a single record frame previously written by {@link #writeRecord}.
     *
     * @return a record summary with id, source, type, timestamp (epoch seconds),
     *         and payload entries (numbers decoded as {@link Double},
     *         booleans/strings/nulls preserved as-is, other types decoded as null).
     */
    public static BinaryRecord readRecord(DataInputStream in) throws IOException {
        long id = in.readLong();
        String source = readString(in);
        String type = readString(in);
        long epochSeconds = in.readLong();

        int size = in.readInt();
        Map<String, Object> payload = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = readString(in);
            Object value = readObject(in);
            payload.put(key, value);
        }
        return new BinaryRecord(id, source, type, epochSeconds, payload);
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeObject(DataOutputStream out, Object o) throws IOException {
        if (o == null) {
            out.writeByte(TYPE_NULL);
        } else if (o instanceof String s) {
            out.writeByte(TYPE_STRING);
            writeString(out, s);
        } else if (o instanceof Number n) {
            out.writeByte(TYPE_NUMBER);
            out.writeDouble(n.doubleValue());
        } else if (o instanceof Boolean b) {
            out.writeByte(TYPE_BOOLEAN);
            out.writeBoolean(b);
        } else {
            // unsupported value type — write as null rather than throwing,
            // since the original ingest path allows arbitrary JSON-shaped payloads.
            out.writeByte(TYPE_NULL);
        }
    }

    private static Object readObject(DataInputStream in) throws IOException {
        byte tag = in.readByte();
        return switch (tag) {
            case TYPE_STRING -> readString(in);
            case TYPE_NUMBER -> in.readDouble();
            case TYPE_BOOLEAN -> in.readBoolean();
            default -> null;
        };
    }

    /** Lightweight value carrier for records decoded by {@link #readRecord}. */
    public record BinaryRecord(long id, String source, String type,
                               long ingestedAtEpochSeconds,
                               Map<String, Object> payload) {}
}