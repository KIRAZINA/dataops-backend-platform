package com.dataops.platform.inmemory.api;

import com.dataops.platform.inmemory.service.InMemoryStorageService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private ApplicationEventPublisher eventPublisher;

    private InMemoryStorageService storageService;
    private IngestController controller;

    @BeforeEach
    void setUp() {
        storageService = new InMemoryStorageService(eventPublisher);
        controller = new IngestController(storageService, persistenceService, new ObjectMapper(), new XmlMapper());
    }

    @Test
    @DisplayName("Should persist JSON into memory and database")
    void ingestJsonSuccess() {
        Map<String, Object> payload = Map.of("value", 42, "name", "record");
        when(persistenceService.saveViaJpa(eq("api"), eq("JSON"), anyMap()))
                .thenReturn(PersistedRecord.builder().id(1L).source("api").type("JSON").payload(payload).build());

        ResponseEntity<?> response = controller.ingestJson(payload);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, storageService.getTotalRecordCount());
        assertNotNull(storageService.findAllRecords().get(0));
        verify(persistenceService).saveViaJpa(eq("api"), eq("JSON"), anyMap());
    }

    @Test
    @DisplayName("Should rollback in-memory JSON when database persistence fails")
    void ingestJsonRollbackOnPersistenceFailure() {
        Map<String, Object> payload = Map.of("value", 42, "name", "record");
        when(persistenceService.saveViaJpa(eq("api"), eq("JSON"), anyMap()))
                .thenThrow(new RuntimeException("db unavailable"));

        ResponseEntity<?> response = controller.ingestJson(payload);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(0, storageService.getTotalRecordCount());
        assertTrue(storageService.findAllRecords().isEmpty());
    }

    @Test
    @DisplayName("Should rollback in-memory CSV batch when database persistence fails")
    void ingestCsvRollbackOnPersistenceFailure() {
        String csv = "id,name\n1,alpha\n2,beta";
        when(persistenceService.saveBatchViaJpa(eq("api"), eq("CSV"), anyList()))
                .thenThrow(new RuntimeException("db unavailable"));

        ResponseEntity<?> response = controller.ingestCsv(csv);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(0, storageService.getTotalRecordCount());
        assertTrue(storageService.findAllRecords().isEmpty());
    }
}
