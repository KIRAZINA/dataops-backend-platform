package com.dataops.platform.inmemory.api;

import com.dataops.platform.inmemory.service.InMemoryStorageService;
import com.dataops.platform.inmemory.service.IngestionService;
import com.dataops.platform.persistence.entity.PersistedRecord;
import com.dataops.platform.persistence.service.PersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IngestController Tests")
@ExtendWith(MockitoExtension.class)
class IngestControllerTest {

    @Mock
    private PersistenceService persistenceService;

    @Mock
    private InMemoryStorageService storageService;

    private IngestController controller;

    @BeforeEach
    void setUp() {
        IngestionService ingestionService = new IngestionService(persistenceService, storageService);
        controller = new IngestController(ingestionService, new ObjectMapper(), new XmlMapper());
    }

    @Test
    @DisplayName("Should persist JSON payload through persistence service")
    void ingestJsonSuccess() {
        Map<String, Object> payload = Map.of("value", 42, "name", "record");
        when(persistenceService.saveViaJpa(eq("api"), eq("JSON"), anyMap()))
                .thenReturn(PersistedRecord.builder().id(1L).source("api").type("JSON").payload(payload).build());

        ResponseEntity<?> response = controller.ingestJson(payload);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(persistenceService).saveViaJpa(eq("api"), eq("JSON"), anyMap());
    }

    @Test
    @DisplayName("Should fail fast when JSON persistence fails")
    void ingestJsonRollbackOnPersistenceFailure() {
        Map<String, Object> payload = Map.of("value", 42, "name", "record");
        when(persistenceService.saveViaJpa(eq("api"), eq("JSON"), anyMap()))
                .thenThrow(new RuntimeException("db unavailable"));

        assertThrows(IllegalStateException.class, () -> controller.ingestJson(payload));
        verify(persistenceService).saveViaJpa(eq("api"), eq("JSON"), anyMap());
    }

    @Test
    @DisplayName("Should fail fast when CSV persistence fails")
    void ingestCsvRollbackOnPersistenceFailure() {
        String csv = "id,name\n1,alpha\n2,beta";
        when(persistenceService.saveBatchViaJpa(eq("api"), eq("CSV"), anyList()))
                .thenThrow(new RuntimeException("db unavailable"));

        assertThrows(IllegalStateException.class, () -> controller.ingestCsv(csv));
        verify(persistenceService).saveBatchViaJpa(eq("api"), eq("CSV"), anyList());
    }
}
