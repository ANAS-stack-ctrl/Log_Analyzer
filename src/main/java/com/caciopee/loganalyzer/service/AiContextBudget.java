package com.caciopee.loganalyzer.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Troncature intelligente : préserve les sections critiques en fin de contexte.
 */
public final class AiContextBudget {

    private static final List<String> PRIORITY_ANCHORS = List.of(
            // Latency dossier : protéger logs + mission (tail-first)
            "=== VRAIES LIGNES DE LOGS CONCERNÉES",
            "=== PREUVES CLÉS",
            "=== ANALYSE PRÉ-CALCULÉE",
            "=== TA MISSION ===",
            // Chat / full-analysis
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

        // Pour les dossiers latence : préserver d'abord les logs + preuves + mission
        // (sinon un truncate naïf coupe la fin — exactement ce dont le LLM a besoin).
        int logsIdx = indexOfAny(context,
                "=== VRAIES LIGNES DE LOGS CONCERNÉES",
                "=== PREUVES CLÉS",
                "=== ANALYSE PRÉ-CALCULÉE",
                "=== TA MISSION ===");
        if (logsIdx >= 0) {
            String priorityTail = context.substring(logsIdx);
            if (priorityTail.length() >= maxChars) {
                // Même le bloc prioritaire déborde : garder la FIN (mission + fin des logs)
                // plutôt que le début (faits génériques).
                int from = priorityTail.length() - maxChars + 80;
                return "… (début du dossier tronqué — logs/preuves prioritaires)\n\n"
                        + priorityTail.substring(Math.max(0, from));
            }
            int headBudget = maxChars - priorityTail.length();
            String head = context.substring(0, logsIdx);
            return truncate(head, Math.max(1200, headBudget))
                    + "\n… (milieu tronqué — logs et preuves ci-dessous)\n\n"
                    + priorityTail;
        }

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

    private static int indexOfAny(String text, String... needles) {
        int best = -1;
        for (String n : needles) {
            int i = text.indexOf(n);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
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
