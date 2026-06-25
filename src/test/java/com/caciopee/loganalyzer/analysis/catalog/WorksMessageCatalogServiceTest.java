package com.caciopee.loganalyzer.analysis.catalog;

import com.caciopee.loganalyzer.entity.LogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorksMessageCatalogServiceTest {

    private WorksMessageCatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new WorksMessageCatalogService(new ObjectMapper());
        catalogService.loadCatalog();
    }

    @Test
    void loadCatalog_hasFamilies() {
        assertTrue(catalogService.familyCount() >= 65);
    }

    @Test
    void classify_filterCodeMessage() {
        LogEntry log = new LogEntry();
        log.setProcessName("processFilter-0-ZRE22-0-LOAD");
        log.setMessage("filter code [TRCEXP_BYAMPE] uuid [-1]");

        WorksMessageFamily family = catalogService.classify(log).orElseThrow();
        assertEquals("MET-001", family.getId());
    }

    @Test
    void classify_tomcatPutKey() {
        LogEntry log = new LogEntry();
        log.setProcessName("processRunRules-0-DATA_LOAD_TASK-0-SAVE");
        log.setMessage("put key [compteRemboursement.numeroCompte] value [42501486352]");

        WorksMessageFamily family = catalogService.classify(log).orElseThrow();
        assertEquals("TOM-004", family.getId());
    }

    @Test
    void classify_unknownReturnsEmpty() {
        LogEntry log = new LogEntry();
        log.setMessage("completely unknown random log line without works markers");

        assertTrue(catalogService.classify(log).isEmpty());
    }

    @Test
    void classifyForGraph_uuidOnlyUsesColumnFamily() {
        LogEntry log = new LogEntry();
        log.setProcessName("processFilter-0-ZRE22-0-LOAD");
        log.setMessage("uuid [123456789]");

        assertEquals("MET-024", catalogService.classify(log).orElseThrow().getId());
        assertEquals("L1-PF", catalogService.classifyForGraph(log).orElseThrow().getId());
    }
}
