package com.caciopee.loganalyzer.analysis.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CatalogUserLabelsTest {

    private WorksMessageCatalogService catalog;

    @BeforeEach
    void setUp() {
        catalog = new WorksMessageCatalogService(new ObjectMapper());
        catalog.loadCatalog();
    }

    @Test
    void displayLabel_doesNotStartWithTechnicalId() {
        for (WorksMessageFamily family : catalog.getFamilies()) {
            String display = family.getDisplayLabel();
            assertNotNull(display);
            assertFalse(display.isBlank());
            assertFalse(display.startsWith(family.getId() + " —"),
                    () -> "Libellé trop technique pour " + family.getId() + ": " + display);
            assertFalse(display.matches("^(MET|PROC|WS|TOM|L1|EXT)-\\d+.*"),
                    () -> "Code technique visible pour " + family.getId());
        }
    }

    @Test
    void displayLabel_knownExamples() {
        WorksMessageFamily met001 = catalog.getFamilies().stream()
                .filter(f -> "MET-001".equals(f.getId())).findFirst().orElseThrow();
        assertEquals("Filtre métier appliqué", met001.getDisplayLabel());

        WorksMessageFamily l1pf = catalog.getFamilies().stream()
                .filter(f -> "L1-PF".equals(f.getId())).findFirst().orElseThrow();
        assertEquals("Écran filtre WORKS", l1pf.getDisplayLabel());
    }
}
