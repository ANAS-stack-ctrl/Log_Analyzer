package com.caciopee.loganalyzer.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiResponseSanitizerTest {

    @Test
    void sanitize_removesThinkBlocks() {
        String tagOpen = new String(new char[] { '<', 't', 'h', 'i', 'n', 'k', '>' });
        String tagClose = new String(new char[] { '<', '/', 't', 'h', 'i', 'n', 'k', '>' });
        String raw = "Bonjour " + tagOpen + "hidden" + tagClose + " monde";
        assertEquals("Bonjour monde", AiResponseSanitizer.sanitize(raw));
    }

    @Test
    void looksLikeEnglish_detectsEnglishReport() {
        String en = "The provided log entries appear to be system logs. Here are recommendations for optimization.";
        assertTrue(AiResponseSanitizer.looksLikeEnglish(en));
    }

    @Test
    void looksLikeEnglish_acceptsFrench() {
        String fr = "## Résumé\nAucune erreur critique. Recommandation : vérifier les filtres avec 0 résultat.";
        assertFalse(AiResponseSanitizer.looksLikeEnglish(fr));
    }
}
