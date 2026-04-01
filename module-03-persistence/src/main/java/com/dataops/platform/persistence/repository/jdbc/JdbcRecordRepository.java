package com.dataops.platform.persistence.repository.jdbc;

import com.dataops.platform.persistence.entity.PersistedRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcRecordRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PersistedRecord save(PersistedRecord record) {
        String sql = "INSERT INTO persisted_record (source, type, ingested_at, payload) VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, record.getSource());
            statement.setString(2, record.getType());
            statement.setTimestamp(3, Timestamp.valueOf(record.getIngestedAt()));
            statement.setString(4, toJson(record.getPayload()));
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated ID for persisted record");
        }

        record.setId(key.longValue());
        return record;
    }

    public List<PersistedRecord> findBySource(String source) {
        String sql = """
            SELECT id, source, type, ingested_at, payload, created_at
            FROM persisted_record
            WHERE source = ?
            ORDER BY ingested_at DESC
            """;

        return jdbc.query(sql, (rs, row) -> PersistedRecord.builder()
                .id(rs.getLong("id"))
                .source(rs.getString("source"))
                .type(rs.getString("type"))
                .ingestedAt(rs.getTimestamp("ingested_at").toLocalDateTime())
                .payload(fromJson(rs.getString("payload")))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build(), source);
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
