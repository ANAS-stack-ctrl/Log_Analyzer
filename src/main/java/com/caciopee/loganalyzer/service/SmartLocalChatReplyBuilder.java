package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.GroupAnalysisResponseDto;

import java.util.Locale;

/**
 * Réponses chat intelligentes sans LLM (parsing du contexte structuré).
 */
public final class SmartLocalChatReplyBuilder {

    private SmartLocalChatReplyBuilder() {
    }

    public static String build(String contextText, String question, GroupAnalysisResponseDto analysis) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        sb.append("### Réponse guidée (moteur d'analyse)\n\n");
        sb.append("**Votre question :** ").append(question != null ? question.trim() : "—").append("\n\n");

        if (q.contains("erreur") || q.contains("error") || q.contains("incident") && q.contains("critique")) {
            appendErrorsSection(sb, contextText, analysis);
        } else if (q.contains("0 row") || q.contains("résultat") || q.contains("resultat") || q.contains("vide")) {
            appendZeroRowSection(sb, contextText, analysis);
        } else if (q.contains("lent") || q.contains("perf") || q.contains("durée") || q.contains("duree")
                || q.contains("latence") || q.contains("mémoire") || q.contains("memoire")) {
            appendPerformanceSection(sb, contextText, analysis);
        } else if (q.contains("règle") || q.contains("regle") || q.contains("warning") || q.contains("avert")) {
            appendWarningsSection(sb, contextText, analysis);
        } else if (q.contains("résume") || q.contains("resume") || q.contains("passe") || q.contains("raconte")
                || q.contains("qu'est") || q.contains("que s") || q.contains("comprendre") || q.contains("fait")) {
            appendNarrativeSection(sb, contextText, analysis);
        } else if (q.contains("filtre") || q.contains("filter")) {
            appendFiltersSection(sb, contextText, analysis, question);
        } else if (q.contains("process")) {
            appendProcessSection(sb, contextText, analysis);
        } else {
            appendNarrativeSection(sb, contextText, analysis);
            sb.append("\n**Précisez** si vous voulez le détail sur : erreurs, 0 row, lenteurs, filtres ou règles.\n");
        }

        String recommendation = analysis != null ? analysis.getRecommendation() : null;
        if (recommendation != null && !recommendation.isBlank()) {
            sb.append("\n**Recommandation du moteur :** ").append(recommendation.trim()).append("\n");
        }

        return sb.toString().trim();
    }

    private static void appendNarrativeSection(StringBuilder sb, String ctx, GroupAnalysisResponseDto analysis) {
        sb.append("**Ce qui s'est passé :**\n\n");
        if (analysis != null) {
            if (analysis.getConclusion() != null && !analysis.getConclusion().isBlank()) {
                sb.append(analysis.getConclusion().trim()).append("\n\n");
            }
            if (analysis.getNarrative() != null && !analysis.getNarrative().isBlank()) {
                sb.append(analysis.getNarrative().trim()).append("\n\n");
            }
            sb.append("- **Volume :** ").append(nz(analysis.getTotalLogs())).append(" logs\n");
            sb.append("- **Erreurs :** ").append(nz(analysis.getErrorCount())).append("\n");
            sb.append("- **0 row :** ").append(nz(analysis.getZeroResultCount())).append("\n");
            sb.append("- **Avertissements :** ").append(nz(analysis.getWarningCount())).append("\n");
        }
        String synth = extractSection(ctx, "SYNTHÈSE MÉTIER");
        if (synth != null) {
            sb.append("\n").append(synth.trim()).append("\n");
        }
    }

    private static void appendErrorsSection(StringBuilder sb, String ctx, GroupAnalysisResponseDto analysis) {
        long errors = analysis != null && analysis.getErrorCount() != null ? analysis.getErrorCount() : -1;
        if (errors == 0) {
            sb.append("**Aucune erreur ERROR critique** n'a été détectée dans ce périmètre.\n\n");
        } else if (errors > 0) {
            sb.append("**").append(errors).append(" erreur(s) critique(s)** détectée(s). Commencez par la première chronologiquement.\n\n");
        }
        String errSection = extractSection(ctx, "Erreurs constatées");
        if (errSection == null) errSection = extractSection(ctx, "5a. ERREURS EXACTES");
        if (errSection != null) {
            sb.append(errSection.trim()).append("\n");
        } else {
            sb.append("_Détail des erreurs non disponible dans le contexte._\n");
        }
    }

    private static void appendZeroRowSection(StringBuilder sb, String ctx, GroupAnalysisResponseDto analysis) {
        long zero = analysis != null && analysis.getZeroResultCount() != null ? analysis.getZeroResultCount() : 0;
        sb.append("**").append(zero).append(" recherche(s) sans résultat (0 row)** ont été comptées.\n");
        sb.append("Cela peut être normal si aucun objet n'était attendu — à confirmer avec le métier.\n\n");

        String zeroSection = extractSection(ctx, "Recherches sans résultat par filtre");
        if (zeroSection != null) {
            sb.append(zeroSection.trim()).append("\n");
        }
    }

    private static void appendPerformanceSection(StringBuilder sb, String ctx, GroupAnalysisResponseDto analysis) {
        String inc = extractSection(ctx, "Incidents de latence");
        if (inc != null) {
            sb.append("**Incidents de latence :**\n\n").append(inc.trim()).append("\n\n");
        }
        String slow = extractSection(ctx, "Lenteurs détectées");
        if (slow == null) slow = extractSection(ctx, "5b. LENTEURS");
        if (slow != null) {
            sb.append("**Lenteurs (≥ 1 s) :**\n\n").append(slow.trim()).append("\n");
        }
        if (inc == null && slow == null) {
            sb.append("_Aucune lenteur majeure signalée dans le contexte._\n");
        }
    }

    private static void appendWarningsSection(StringBuilder sb, String ctx, GroupAnalysisResponseDto analysis) {
        long w = analysis != null && analysis.getWarningCount() != null ? analysis.getWarningCount() : 0;
        sb.append("**").append(w).append(" avertissement(s) / règle(s) absente(s)** relevé(s).\n\n");
        String diag = extractSection(ctx, "POINTS DE DIAGNOSTIC");
        if (diag == null) diag = extractSection(ctx, "Points de diagnostic");
        if (diag != null) sb.append(diag.trim()).append("\n");
    }

    private static void appendFiltersSection(StringBuilder sb, String ctx, GroupAnalysisResponseDto analysis, String question) {
        String activity = extractSection(ctx, "STRUCTURE MÉTIER");
        if (activity == null) activity = extractSection(ctx, "Activité métier");
        if (activity != null) {
            sb.append(activity.trim()).append("\n\n");
        }
        String zero = extractSection(ctx, "Recherches sans résultat par filtre");
        if (zero != null) sb.append(zero.trim()).append("\n");
    }

    private static void appendProcessSection(StringBuilder sb, String ctx, GroupAnalysisResponseDto analysis) {
        String activity = extractSection(ctx, "STRUCTURE MÉTIER");
        if (activity == null) activity = extractSection(ctx, "Activité métier");
        if (activity != null) {
            sb.append(activity.trim()).append("\n");
        } else if (analysis != null && analysis.getProcesses() != null) {
            analysis.getProcesses().stream().limit(8).forEach(p ->
                    sb.append("- ").append(p.getName()).append(" (").append(p.getCount()).append(")\n"));
        }
    }

    private static String extractSection(String ctx, String header) {
        if (ctx == null || header == null) return null;
        int start = ctx.indexOf(header);
        if (start < 0) return null;
        int lineEnd = ctx.indexOf('\n', start);
        int contentStart = lineEnd >= 0 ? lineEnd + 1 : start + header.length();
        int next = findNextSection(ctx, contentStart);
        return ctx.substring(contentStart, next).trim();
    }

    private static int findNextSection(String ctx, int from) {
        String[] markers = {
                "\n\nSYNTHÈSE", "\n\nPÉRIMÈTRE", "\n\n1. ", "\n\n2. ", "\n\n3. ", "\n\n4. ", "\n\n5. ",
                "\n\nErreurs", "\n\nLenteurs", "\n\nIncidents", "\n\nRecherches", "\n\nFOCUS",
                "\n\nPREUVES", "\n\nCHRONOLOGIE"
        };
        int end = ctx.length();
        for (String m : markers) {
            int i = ctx.indexOf(m, from);
            if (i >= 0 && i < end) end = i;
        }
        return end;
    }

    private static String nz(Long v) {
        return v == null ? "0" : String.valueOf(v);
    }
}
