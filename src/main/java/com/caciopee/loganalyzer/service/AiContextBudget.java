package com.caciopee.loganalyzer.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Troncature intelligente : préserve les sections critiques en fin de contexte.
 */
public final class AiContextBudget {

    private static final List<String> PRIORITY_ANCHORS = List.of(
            "FOCUS SUR LA QUESTION",
            "Erreurs constatées",
            "5a. ERREURS EXACTES",
            "Lenteurs détectées",
            "5b. LENTEURS",
            "Incidents de latence",
            "Recherches sans résultat par filtre",
            "SYNTHÈSE MÉTIER"
    );

    private AiContextBudget() {
    }

    public static String apply(String context, int maxChars) {
        if (context == null) return "";
        if (context.length() <= maxChars) return context;

        for (String anchor : PRIORITY_ANCHORS) {
            int idx = context.indexOf(anchor);
            if (idx < 0) continue;

            String tail = context.substring(idx);
            if (tail.length() >= maxChars) {
                return truncate(tail, maxChars);
            }

            int headBudget = maxChars - tail.length();
            String head = context.substring(0, idx);
            return truncate(head, Math.max(800, headBudget))
                    + "\n… (milieu tronqué — sections prioritaires ci-dessous)\n\n"
                    + tail;
        }

        return truncate(context, maxChars);
    }

    public static String extractSummary(String context, int max) {
        if (context == null) return "";
        int end = context.indexOf("\n\n2. ");
        if (end < 0) end = context.indexOf("\n\nPÉRIMÈTRE");
        if (end < 0) end = Math.min(context.length(), max);
        return truncate(context.substring(0, end).trim(), max);
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, max) + "…";
    }
}
