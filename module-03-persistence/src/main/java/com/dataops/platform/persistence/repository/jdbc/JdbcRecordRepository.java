package com.dataops.platform.persistence.repository.jdbc;

import com.dataops.platform.persistence.entity.PersistedRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
        String sql = """
            INSERT INTO persisted_record (source, type, ingested_at, payload)
            VALUES (?, ?, ?, ?::json)
            RETURNING id
            """;

        Long id = jdbc.queryForObject(sql,
                Long.class,
                record.getSource(),
                record.getType(),
                record.getIngestedAt(),
                toJson(record.getPayload())
        );

        record.setId(id);
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