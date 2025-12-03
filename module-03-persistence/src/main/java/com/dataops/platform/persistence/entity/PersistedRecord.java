package com.dataops.platform.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "persisted_record",
        indexes = {
                @Index(name = "idx_source", columnList = "source"),
                @Index(name = "idx_type", columnList = "type"),
                @Index(name = "idx_ingested_at", columnList = "ingested_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PersistedRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String source;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "ingested_at", nullable = false)
    private LocalDateTime ingestedAt;

    @Column(columnDefinition = "JSON", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payload;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}