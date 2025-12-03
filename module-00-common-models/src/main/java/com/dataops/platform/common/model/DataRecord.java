package com.dataops.platform.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataRecord {

    @NotBlank(message = "Key is required")
    private String key;

    @NotBlank(message = "Source is required")
    private String source;

    @NotBlank(message = "Type is required")
    private String type;

    @NotNull(message = "Payload cannot be null")
    private Map<String, Object> payload;

    @Builder.Default
    private Instant timestamp = Instant.now();

    @JsonProperty("id")
    public String id() { return key; }

    @JsonProperty("ingested_at")
    public Instant getTimestamp() { return timestamp; }

    @JsonProperty("data")
    public Map<String, Object> getPayload() { return payload; }

    @JsonProperty("id")
    public String getKey() {
        return key;
    }
}