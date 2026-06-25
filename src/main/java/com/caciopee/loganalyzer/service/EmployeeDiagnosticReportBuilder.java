package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.GroupAnalysisItemDto;
import com.caciopee.loganalyzer.dto.GroupAnalysisResponseDto;
import com.caciopee.loganalyzer.dto.ImportAnalysisSummaryDto;
import com.caciopee.loganalyzer.dto.IncidentCandidateDto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Rapport lisible en français pour les employés (sans appel IA).
 */
public final class EmployeeDiagnosticReportBuilder {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private EmployeeDiagnosticReportBuilder() {
    }

    public static String buildSummary(GroupAnalysisResponseDto analysis, List<IncidentCandidateDto> incidents) {
        return buildSummary(analysis, incidents, null);
    }

    public static String buildSummary(GroupAnalysisResponseDto analysis,
                                      List<IncidentCandidateDto> incidents,
                                      ImportAnalysisSummaryDto importQuality) {
        if (analysis == null) {
            return "Aucune donnée d'analyse disponible.";
        }

        StringBuilder sb = new StringBuilder();
        String scope = scopeTitle(analysis.getGroupBy(), analysis.getGroupKey());
        long total = analysis.getTotalLogs() != null ? analysis.getTotalLogs() : 0;

        sb.append(scope).append("\n");
        sb.append("Période : ").append(formatPeriod(analysis.getFirstTimestamp(), analysis.getLastTimestamp()));
        String duration = formatDuration(analysis.getFirstTimestamp(), analysis.getLastTimestamp());
        if (!duration.isBlank()) {
            sb.append(" (durée estimée : ").append(duration).append(")");
        }
        sb.append("\n");
        sb.append("Volume : ").append(nz(analysis.getTotalLogs())).append(" lignes de log analysées.\n");
        if (importQuality != null && importQuality.getFileName() != null) {
            sb.append("Fichier source : ").append(importQuality.getFileName().trim()).append("\n");
        }
        sb.append("\n");

        sb.append("Synthèse exécutive :\n");
        sb.append(buildExecutiveSummary(analysis, incidents, total)).append("\n\n");

        sb.append("Bilan :\n");
        appendBilanLine(sb, analysis.getErrorCount() != null && analysis.getErrorCount() > 0,
                "Erreurs critiques : " + nz(analysis.getErrorCount()), "Aucune erreur critique détectée.");
        appendBilanLine(sb, analysis.getZeroResultCount() != null && analysis.getZeroResultCount() > 0,
                "Recherches sans résultat (0 row) : " + nz(analysis.getZeroResultCount())
                        + ratioLabel(analysis.getZeroResultCount(), total),
                "Aucune recherche à 0 résultat.");
        appendBilanLine(sb, analysis.getWarningCount() != null && analysis.getWarningCount() > 0,
                "Avertissements / règles absentes : " + nz(analysis.getWarningCount())
                        + ratioLabel(analysis.getWarningCount(), total),
                "Pas d'avertissement notable.");
        long perfCount = analysis.getPerformanceSignals() != null ? analysis.getPerformanceSignals().size() : 0;
        appendBilanLine(sb, perfCount > 0,
                "Signaux lenteur ou mémoire : " + perfCount + " étape(s) marquante(s)",
                "Pas de signal performance majeur.");
        if (incidents != null && !incidents.isEmpty()) {
            sb.append("• ⚠ Incidents de latence liés : ").append(incidents.size()).append("\n");
        }

        sb.append("\nInterprétation :\n");
        if (analysis.getConclusion() != null && !analysis.getConclusion().isBlank()) {
            sb.append(analysis.getConclusion().trim()).append("\n");
        }
        if (analysis.getNarrative() != null && !analysis.getNarrative().isBlank()) {
            sb.append("\n").append(analysis.getNarrative().trim());
        }

        sb.append("\n\nActivité principale :\n");
        appendRankedLines(sb, "Processus", analysis.getProcesses(), 8);
        appendRankedLines(sb, "Actions", analysis.getActions(), 8);
        appendRankedLines(sb, "Filtres", analysis.getFilters(), 10);
        appendRankedLines(sb, "Objets métier", analysis.getBusinessObjects(), 8);

        appendZeroResultsSection(sb, analysis);
        appendWarningsSection(sb, analysis);
        appendPerformanceSection(sb, analysis);
        appendTimelineSection(sb, analysis);

        sb.append("\nActions recommandées :\n");
        if (analysis.getRecommendation() != null && !analysis.getRecommendation().isBlank()) {
            sb.append("• ").append(analysis.getRecommendation().trim()).append("\n");
        }
        appendActionHints(sb, analysis, incidents);

        appendIncidentsSection(sb, incidents);

        return sb.toString().trim();
    }

    public static String buildMarkdownReport(GroupAnalysisResponseDto analysis, List<IncidentCandidateDto> incidents) {
        return buildMarkdownReport(analysis, incidents, null);
    }

    public static String buildMarkdownReport(GroupAnalysisResponseDto analysis,
                                             List<IncidentCandidateDto> incidents,
                                             ImportAnalysisSummaryDto importQuality) {
        if (analysis == null) {
            return "## Résumé\n\nAucune donnée disponible.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(scopeTitle(analysis.getGroupBy(), analysis.getGroupKey())).append("\n\n");
        sb.append("**Période :** ").append(formatPeriod(analysis.getFirstTimestamp(), analysis.getLastTimestamp()));
        String duration = formatDuration(analysis.getFirstTimestamp(), analysis.getLastTimestamp());
        if (!duration.isBlank()) {
            sb.append(" (").append(duration).append(")");
        }
        sb.append("  \n**Volume :** ").append(nz(analysis.getTotalLogs())).append(" logs  \n");
        sb.append("**Erreurs :** ").append(nz(analysis.getErrorCount()));
        sb.append(" | **0 row :** ").append(nz(analysis.getZeroResultCount()));
        sb.append(" | **Avertissements :** ").append(nz(analysis.getWarningCount())).append("\n\n");

        sb.append("## Synthèse exécutive\n\n");
        sb.append(buildExecutiveSummary(analysis, incidents,
                analysis.getTotalLogs() != null ? analysis.getTotalLogs() : 0)).append("\n\n");

        sb.append("## Ce que l'on peut en déduire\n\n");
        if (analysis.getConclusion() != null) sb.append(analysis.getConclusion()).append("\n\n");
        if (analysis.getNarrative() != null) sb.append(analysis.getNarrative()).append("\n\n");

        sb.append("## Activité principale détectée\n\n");
        sb.append("- **Processus :** ").append(topNames(analysis.getProcesses(), 8)).append("\n");
        sb.append("- **Actions :** ").append(topNames(analysis.getActions(), 8)).append("\n");
        sb.append("- **Filtres :** ").append(topNames(analysis.getFilters(), 10)).append("\n");
        sb.append("- **Objets métier :** ").append(topNames(analysis.getBusinessObjects(), 8)).append("\n\n");

        if (analysis.getZeroResultCount() != null && analysis.getZeroResultCount() > 0) {
            sb.append("## Recherches sans résultat\n\n");
            appendMarkdownItems(sb, analysis.getZeroResults(), 12);
            sb.append("\n");
        }

        if (analysis.getWarningCount() != null && analysis.getWarningCount() > 0) {
            sb.append("## Avertissements\n\n");
            appendMarkdownItems(sb, analysis.getWarnings(), 10);
            sb.append("\n");
        }

        List<GroupAnalysisItemDto> perf = analysis.getPerformanceSignals();
        if (perf != null && !perf.isEmpty()) {
            sb.append("## Signaux performance / mémoire\n\n");
            appendMarkdownItems(sb, perf, 10);
            sb.append("\n");
        }

        List<String> timeline = analysis.getTimeline();
        if (timeline != null && !timeline.isEmpty()) {
            sb.append("## Chronologie (extraits)\n\n");
            timeline.stream().limit(20).forEach(line -> sb.append("- ").append(line).append("\n"));
            sb.append("\n");
        }

        sb.append("## Recommandations\n\n");
        sb.append("- ").append(nullSafe(analysis.getRecommendation())).append("\n\n");

        if (incidents != null && !incidents.isEmpty()) {
            sb.append("## Latences détectées\n\n");
            incidents.stream().limit(10).forEach(inc -> appendIncidentMarkdown(sb, inc));
        }

        return sb.toString().trim();
    }

    private static String buildExecutiveSummary(GroupAnalysisResponseDto analysis,
                                                List<IncidentCandidateDto> incidents,
                                                long total) {
        StringBuilder sb = new StringBuilder();
        String who = scopeTitle(analysis.getGroupBy(), analysis.getGroupKey());
        String duration = formatDuration(analysis.getFirstTimestamp(), analysis.getLastTimestamp());

        sb.append("Sur ");
        if (!duration.isBlank()) {
            sb.append("environ ").append(duration).append(", ");
        }
        sb.append(who.toLowerCase(Locale.ROOT));
        sb.append(" a produit ").append(nz(analysis.getTotalLogs())).append(" lignes de log");

        long errors = analysis.getErrorCount() != null ? analysis.getErrorCount() : 0;
        if (errors > 0) {
            sb.append(", dont ").append(errors).append(" erreur(s) critique(s)");
        } else {
            sb.append(" sans erreur critique");
        }
        sb.append(". ");

        String mainProcess = firstName(analysis.getProcesses());
        if (mainProcess != null) {
            sb.append("L'activité est dominée par le processus ").append(mainProcess);
            String mainAction = firstName(analysis.getActions());
            if (mainAction != null) {
                sb.append(" et l'action ").append(mainAction);
            }
            sb.append(". ");
        }

        long zeroRow = analysis.getZeroResultCount() != null ? analysis.getZeroResultCount() : 0;
        if (zeroRow > 0) {
            sb.append(zeroRow).append(" recherche(s) (").append(percent(zeroRow, total))
                    .append(" %) n'ont retourné aucune ligne");
            String topZero = firstName(analysis.getZeroResults());
            if (topZero != null) {
                sb.append(" — filtre le plus concerné : ").append(topZero);
            }
            sb.append(". ");
        }

        long warnings = analysis.getWarningCount() != null ? analysis.getWarningCount() : 0;
        if (warnings > 0) {
            sb.append(warnings).append(" avertissement(s) ont été relevés. ");
        }

        long perf = analysis.getPerformanceSignals() != null ? analysis.getPerformanceSignals().size() : 0;
        if (perf > 0) {
            sb.append(perf).append(" signal(aux) de lenteur ou de mémoire élevée. ");
        }

        if (incidents != null && !incidents.isEmpty()) {
            sb.append(incidents.size()).append(" incident(s) de latence lié(s) à ce périmètre.");
        }

        return sb.toString().trim();
    }

    private static void appendRankedLines(StringBuilder sb, String label, List<GroupAnalysisItemDto> items, int max) {
        if (items == null || items.isEmpty()) return;
        sb.append("• ").append(label).append(" : ");
        sb.append(items.stream()
                .limit(max)
                .map(i -> i.getName() + " (" + nz(i.getCount()) + ")")
                .collect(Collectors.joining(", ")));
        sb.append("\n");
    }

    private static void appendZeroResultsSection(StringBuilder sb, GroupAnalysisResponseDto analysis) {
        if (analysis.getZeroResultCount() == null || analysis.getZeroResultCount() <= 0) return;
        sb.append("\nRecherches sans résultat (échantillon) :\n");
        List<GroupAnalysisItemDto> zeros = analysis.getZeroResults();
        if (zeros == null || zeros.isEmpty()) {
            sb.append("• ").append(analysis.getZeroResultCount())
                    .append(" occurrence(s) — consulter le graphe ou le regroupement pour le détail.\n");
            return;
        }
        zeros.stream().limit(12).forEach(z -> {
            sb.append("• ").append(nullSafe(z.getName()));
            if (z.getCount() != null && z.getCount() > 1) {
                sb.append(" — ").append(z.getCount()).append(" fois");
            }
            if (z.getDiagnostic() != null && !z.getDiagnostic().isBlank()) {
                sb.append(" : ").append(z.getDiagnostic().trim());
            }
            sb.append("\n");
        });
    }

    private static void appendWarningsSection(StringBuilder sb, GroupAnalysisResponseDto analysis) {
        if (analysis.getWarningCount() == null || analysis.getWarningCount() <= 0) return;
        List<GroupAnalysisItemDto> warnings = analysis.getWarnings();
        if (warnings == null || warnings.isEmpty()) return;
        sb.append("\nAvertissements notables :\n");
        warnings.stream().limit(10).forEach(w -> {
            sb.append("• ").append(nullSafe(w.getName()));
            if (w.getCount() != null && w.getCount() > 1) {
                sb.append(" (").append(w.getCount()).append(")");
            }
            if (w.getDiagnostic() != null && !w.getDiagnostic().isBlank()) {
                sb.append(" — ").append(w.getDiagnostic().trim());
            }
            sb.append("\n");
        });
    }

    private static void appendPerformanceSection(StringBuilder sb, GroupAnalysisResponseDto analysis) {
        List<GroupAnalysisItemDto> perf = analysis.getPerformanceSignals();
        if (perf == null || perf.isEmpty()) return;
        sb.append("\nSignaux performance / mémoire :\n");
        perf.stream().limit(10).forEach(p -> {
            sb.append("• ").append(nullSafe(p.getName()));
            if (p.getDiagnostic() != null && !p.getDiagnostic().isBlank()) {
                sb.append(" — ").append(p.getDiagnostic().trim());
            }
            sb.append("\n");
        });
    }

    private static void appendTimelineSection(StringBuilder sb, GroupAnalysisResponseDto analysis) {
        List<String> timeline = analysis.getTimeline();
        if (timeline == null || timeline.isEmpty()) return;
        sb.append("\nChronologie (extraits) :\n");
        timeline.stream().limit(18).forEach(line -> sb.append("• ").append(line).append("\n"));
        if (timeline.size() > 18) {
            sb.append("• … et ").append(timeline.size() - 18).append(" autre(s) étape(s) (voir analyse groupe).\n");
        }
    }

    private static void appendActionHints(StringBuilder sb,
                                          GroupAnalysisResponseDto analysis,
                                          List<IncidentCandidateDto> incidents) {
        if (analysis.getZeroResultCount() != null && analysis.getZeroResultCount() > 0) {
            List<GroupAnalysisItemDto> zeros = analysis.getZeroResults();
            if (zeros != null && !zeros.isEmpty()) {
                String names = zeros.stream().limit(6).map(GroupAnalysisItemDto::getName).collect(Collectors.joining(", "));
                sb.append("• Vérifier en priorité les filtres : ").append(names).append("\n");
            }
            sb.append("• Confirmer avec le métier si l'absence de résultat était attendue sur ces recherches.\n");
        }
        if (analysis.getPerformanceSignals() != null && !analysis.getPerformanceSignals().isEmpty()) {
            String sample = analysis.getPerformanceSignals().stream()
                    .limit(4).map(GroupAnalysisItemDto::getName).collect(Collectors.joining(", "));
            sb.append("• Investiguer les étapes lentes ou la mémoire élevée : ").append(sample).append("\n");
        }
        if (incidents != null && !incidents.isEmpty()) {
            sb.append("• Ouvrir le détail des incidents de latence pour identifier processus et filtres concernés.\n");
        }
        if (analysis.getErrorCount() != null && analysis.getErrorCount() > 0) {
            sb.append("• Traiter les erreurs critiques en commençant par la première occurrence chronologique.\n");
        }
    }

    private static void appendIncidentsSection(StringBuilder sb, List<IncidentCandidateDto> incidents) {
        if (incidents == null || incidents.isEmpty()) return;
        sb.append("\nIncidents de latence :\n");
        incidents.stream().limit(10).forEach(inc -> appendIncidentLine(sb, inc));
    }

    private static void appendIncidentLine(StringBuilder sb, IncidentCandidateDto inc) {
        sb.append("• ");
        sb.append(nullSafe(inc.getTitle()));
        if (inc.getMaxDurationMs() != null) {
            sb.append(" — durée max ").append(inc.getMaxDurationMs()).append(" ms");
        }
        if (inc.getProcessName() != null) {
            sb.append(" — process ").append(inc.getProcessName());
        }
        if (inc.getFilterCode() != null) {
            sb.append(" — filtre ").append(inc.getFilterCode());
        }
        sb.append("\n");
        String detail = inc.getExplanation() != null ? inc.getExplanation() : inc.getProbableCause();
        if (detail != null && !detail.isBlank()) {
            sb.append("  ").append(detail.trim()).append("\n");
        }
    }

    private static void appendIncidentMarkdown(StringBuilder sb, IncidentCandidateDto inc) {
        sb.append("- **").append(nullSafe(inc.getTitle())).append("**");
        if (inc.getMaxDurationMs() != null) sb.append(" (").append(inc.getMaxDurationMs()).append(" ms)");
        if (inc.getProcessName() != null) sb.append(" — process ").append(inc.getProcessName());
        sb.append("\n");
        String detail = inc.getExplanation() != null ? inc.getExplanation() : inc.getProbableCause();
        if (detail != null && !detail.isBlank()) {
            sb.append("  ").append(detail.trim()).append("\n");
        }
    }

    private static void appendMarkdownItems(StringBuilder sb, List<GroupAnalysisItemDto> items, int max) {
        if (items == null || items.isEmpty()) {
            sb.append("_Aucun détail disponible._\n");
            return;
        }
        items.stream().limit(max).forEach(item -> {
            sb.append("- **").append(nullSafe(item.getName())).append("**");
            if (item.getCount() != null && item.getCount() > 1) {
                sb.append(" (").append(item.getCount()).append(")");
            }
            if (item.getDiagnostic() != null && !item.getDiagnostic().isBlank()) {
                sb.append(" — ").append(item.getDiagnostic().trim());
            }
            sb.append("\n");
        });
    }

    private static void appendBilanLine(StringBuilder sb, boolean warn, String warnText, String okText) {
        sb.append(warn ? "• ⚠ " : "• ✓ ").append(warn ? warnText : okText).append("\n");
    }

    private static String scopeTitle(String groupBy, String groupKey) {
        String label = switch (groupBy != null ? groupBy : "") {
            case "userName" -> "Utilisateur";
            case "sessionId" -> "Session";
            case "processName" -> "Processus";
            case "sourceFileName", "fileName" -> "Fichier";
            default -> "Groupe";
        };
        return label + " : " + nullSafe(groupKey);
    }

    private static String formatPeriod(LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) return "non déterminée";
        if (from != null && to != null) {
            return from.format(FMT) + " → " + to.format(FMT);
        }
        if (from != null) return "à partir du " + from.format(FMT);
        return "jusqu'au " + to.format(FMT);
    }

    private static String formatDuration(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) return "";
        long minutes = Duration.between(from, to).toMinutes();
        if (minutes < 1) return "moins d'une minute";
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        long rem = minutes % 60;
        if (rem == 0) return hours + " h";
        return hours + " h " + rem + " min";
    }

    private static String ratioLabel(Long count, long total) {
        if (count == null || count <= 0 || total <= 0) return "";
        return " (" + percent(count, total) + " % des logs)";
    }

    private static String percent(long part, long total) {
        if (total <= 0) return "0";
        double p = 100.0 * part / total;
        return p < 10 ? String.format(Locale.FRANCE, "%.1f", p) : String.format(Locale.FRANCE, "%.0f", p);
    }

    private static String firstName(List<GroupAnalysisItemDto> items) {
        if (items == null || items.isEmpty()) return null;
        return items.get(0).getName();
    }

    private static String topNames(List<GroupAnalysisItemDto> items, int max) {
        if (items == null || items.isEmpty()) return "—";
        return items.stream()
                .limit(max)
                .map(i -> i.getName() + " (" + nz(i.getCount()) + ")")
                .collect(Collectors.joining(", "));
    }

    private static String nz(Long v) {
        return v == null ? "0" : String.valueOf(v);
    }

    private static String nullSafe(String v) {
        return v == null || v.isBlank() ? "—" : v.trim();
    }
}
