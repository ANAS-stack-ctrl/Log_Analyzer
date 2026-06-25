package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.*;

import java.util.List;

/**
 * Dossier d'investigation structuré pour l'analyse IA complète (évite le dump brut tronqué).
 */
public final class AiInvestigationDossierBuilder {

    private AiInvestigationDossierBuilder() {
    }

    public static String build(SessionDiagnosticResponseDto diag) {
        GroupAnalysisResponseDto ga = diag.getGroupAnalysis() != null
                ? diag.getGroupAnalysis() : new GroupAnalysisResponseDto();
        List<IncidentCandidateDto> incidents = diag.getRelatedIncidents() != null
                ? diag.getRelatedIncidents() : List.of();
        ImportAnalysisSummaryDto quality = diag.getImportQuality();
        ImportExtractionAuditDto audit = diag.getExtractionAudit();

        StringBuilder sb = new StringBuilder();

        sb.append("=== DOSSIER D'INVESTIGATION WORKS ===\n\n");

        sb.append("## A. SYNTHÈSE MÉTIER (moteur — source de vérité)\n\n");
        sb.append(EmployeeDiagnosticReportBuilder.buildSummary(ga, incidents, quality)).append("\n\n");

        sb.append("## B. RAPPORT GUIDÉ STRUCTURÉ\n\n");
        sb.append(EmployeeDiagnosticReportBuilder.buildMarkdownReport(ga, incidents, quality)).append("\n\n");

        sb.append("## C. PÉRIMÈTRE TECHNIQUE\n");
        sb.append("- regroupement : ").append(ns(diag.getGroupBy())).append("\n");
        sb.append("- clé : ").append(ns(diag.getGroupKey())).append("\n");
        if (quality != null && quality.getFileName() != null) {
            sb.append("- fichier : ").append(quality.getFileName()).append("\n");
        }
        sb.append("\n");

        if (!incidents.isEmpty()) {
            sb.append("## D. INCIDENTS DE LATENCE (détail)\n\n");
            incidents.stream().limit(12).forEach(inc -> {
                sb.append("- **").append(ns(inc.getTitle())).append("**");
                if (inc.getMaxDurationMs() != null) sb.append(" — ").append(inc.getMaxDurationMs()).append(" ms");
                if (inc.getProcessName() != null) sb.append(" — process ").append(inc.getProcessName());
                if (inc.getFilterCode() != null) sb.append(" — filtre ").append(inc.getFilterCode());
                sb.append("\n");
                String detail = inc.getExplanation() != null ? inc.getExplanation() : inc.getProbableCause();
                if (detail != null && !detail.isBlank()) {
                    sb.append("  ").append(detail.trim()).append("\n");
                }
            });
            sb.append("\n");
        }

        if (audit != null) {
            sb.append("## E. QUALITÉ EXTRACTION\n");
            sb.append("- couverture workflow : ").append(audit.getWorkflowCoveragePercent()).append(" %\n");
            sb.append("- couverture catalogue : ").append(audit.getCatalogCoveragePercent()).append(" %\n\n");
        }

        sb.append("## F. CHRONOLOGIE (extraits moteur)\n\n");
        List<String> timeline = ga.getTimeline();
        if (timeline != null && !timeline.isEmpty()) {
            timeline.stream().limit(25).forEach(line -> sb.append("- ").append(line).append("\n"));
        } else {
            sb.append("_Chronologie non disponible._\n");
        }
        sb.append("\n");

        sb.append("## G. PREUVES TECHNIQUES (échantillon — contexte IA tronqué)\n\n");
        String ctx = diag.getAiContext() != null ? diag.getAiContext().getContext() : "";
        sb.append(extractTechnicalProofs(ctx, 12000));

        sb.append("\n\n## INSTRUCTIONS POUR LE MODÈLE\n");
        sb.append("Rédige en français. Complète et nuance le rapport guidé (sections A et B).\n");
        sb.append("Ne contredis pas les chiffres. Cite 3 à 8 horodatages ISO des preuves (section G).\n");
        sb.append("Structure : Résumé, Chronologie, Problèmes, Causes probables, Actions, Preuves.\n");

        return sb.toString();
    }

    private static String extractTechnicalProofs(String ctx, int max) {
        if (ctx == null || ctx.isBlank()) return "—";
        int err = indexOfAny(ctx, "Erreurs constatées", "5a. ERREURS EXACTES");
        int slow = indexOfAny(ctx, "Lenteurs détectées", "5b. LENTEURS");
        int proofs = ctx.indexOf("PREUVES REPRÉSENTATIVES");
        if (proofs < 0) proofs = ctx.indexOf("6. PREUVES");

        StringBuilder out = new StringBuilder();
        if (err >= 0) out.append(sliceUntil(ctx, err, slow > err ? slow : proofs > err ? proofs : ctx.length()));
        if (slow >= 0) out.append("\n").append(sliceUntil(ctx, slow, proofs > slow ? proofs : ctx.length()));
        if (proofs >= 0) out.append("\n").append(sliceUntil(ctx, proofs, ctx.length()));

        if (out.length() == 0) {
            return AiContextBudget.apply(ctx, max);
        }
        return AiContextBudget.apply(out.toString().trim(), max);
    }

    private static int indexOfAny(String ctx, String... needles) {
        for (String n : needles) {
            int i = ctx.indexOf(n);
            if (i >= 0) return i;
        }
        return -1;
    }

    private static String sliceUntil(String ctx, int start, int end) {
        if (start < 0) return "";
        end = Math.min(end, ctx.length());
        if (end <= start) end = ctx.length();
        return ctx.substring(start, end).trim();
    }

    private static String ns(String v) {
        return v == null || v.isBlank() ? "—" : v.trim();
    }
}
