package com.dataops.platform.persistence.entity;

import com.dataops.platform.persistence.converter.MapToJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Lob
    @Convert(converter = MapToJsonConverter.class)
    @Column(columnDefinition = "CLOB NOT NULL", nullable = false)
    private Map<String, Object> payload;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
