package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.dto.LatencyEvidenceLinkDto;
import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;
import com.caciopee.loganalyzer.dto.LatencyQueryGroupDto;
import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;
import com.caciopee.loganalyzer.entity.LogEntry;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Construit les preuves chiffrées cliquables (chiffre → ligne de log).
 */
public final class LatencyEvidenceBuilder {

    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern TOOK_BRACKET = Pattern.compile("took\\s*\\[(\\d+)]", Pattern.CASE_INSENSITIVE);

    private LatencyEvidenceBuilder() {
    }

    public static List<LatencyEvidenceLinkDto> build(WorksLatencyFacts facts,
                                                     LatencyBottleneckAnalyzer.NarrativeKind kind,
                                                     LatencyTimelineStepDto primary,
                                                     LatencyOriginReportDto report,
                                                     List<LogEntry> windowLogs,
                                                     List<LatencyTimelineStepDto> steps) {
        List<LatencyEvidenceLinkDto> links = new ArrayList<>();
        String family = kind != null ? kind.name() : "COMPLEX";

        addDurationEvidence(links, "rulesMs", "Durée moteur de règles",
                facts != null ? facts.getRulesEngineMs() : null,
                "running rules", "in host took", windowLogs, steps, family);
        addDurationEvidence(links, "rootSqlMs", "Requête SQL racine",
                facts != null ? facts.getRootQueryMs() : null,
                "prepareSearchByRoot", null, windowLogs, steps, family);
        addDurationEvidence(links, "globalMs", "Recherche globale",
                facts != null ? facts.getGlobalMs() : null,
                "global searchComposantByRoot", null, windowLogs, steps, family);
        addDurationEvidence(links, "preRulesMs", "Volume amont (avant règles)",
                facts != null ? facts.getPreRulesSearchMs() : null,
                "global searchComposantByRoot",
                facts != null ? facts.getPreRulesFilterCode() : null,
                windowLogs, steps, family);
        addCountEvidence(links, "preRulesRows", "Lignes chargées avant règles",
                facts != null ? facts.getPreRulesRowCount() : null,
                facts != null && facts.getPreRulesRowCount() != null
                        ? facts.getPreRulesRowCount() + " row" : null,
                facts != null ? facts.getPreRulesFilterCode() : null,
                windowLogs, family);
        addCountEvidence(links, "majInsert", "Écritures MAJ_Insert",
                facts != null && facts.getMajInsertTotal() > 0 ? facts.getMajInsertTotal() : null,
                facts != null && facts.getMajInsertDominantType() != null
                        ? "MAJ_Insert_" + facts.getMajInsertDominantType()
                        : "MAJ_Insert_",
                null, windowLogs, family);
        addDurationEvidence(links, "saveMs", "SAVE / persistance",
                facts != null ? facts.getSavePersistMs() : null,
                "total time SAVE", null, windowLogs, steps, family);
        addDurationEvidence(links, "renderingMs", "Affichage",
                facts != null ? facts.getRenderingMs() : null,
                "rendering result", null, windowLogs, steps, family);
        addDurationEvidence(links, "doActionMs", "Action métier (doAction)",
                null, "doAction for",
                facts != null ? facts.getBusinessActionLabel() : null,
                windowLogs, steps, family);

        if (primary != null && primary.getDurationMs() != null && primary.getDurationMs() > 0) {
            LatencyEvidenceLinkDto bottleneck = new LatencyEvidenceLinkDto(
                    "bottleneck", "Goulot principal", formatMs(primary.getDurationMs()));
            bottleneck.setValueMs(primary.getDurationMs());
            bottleneck.setLogId(primary.getLogId());
            bottleneck.setSearchTerm(primary.getDurationMs() != null
                    ? "took [" + primary.getDurationMs() + "]"
                    : primary.getOperationName());
            bottleneck.setExcerpt(primary.getOperationName());
            if (primary.getTimestamp() != null) {
                bottleneck.setTimestamp(primary.getTimestamp().format(HMS));
            }
            bottleneck.setFamily(family);
            // Évite le doublon si déjà présent avec le même took.
            boolean dup = links.stream().anyMatch(l ->
                    primary.getDurationMs().equals(l.getValueMs()) && l.getLogId() != null);
            if (!dup) {
                links.add(0, bottleneck);
            }
        }

        LatencyQueryGroupDto dominant = facts != null ? facts.dominantRepeatedGroup() : null;
        if (dominant != null) {
            LatencyEvidenceLinkDto g = new LatencyEvidenceLinkDto(
                    "repeatedSql",
                    "Requêtes répétées « " + friendlyRule(dominant.getFilterCode()) + " »",
                    dominant.getCount() + "× / " + formatMs(dominant.getTotalMs()));
            g.setValueMs(dominant.getTotalMs());
            g.setValueCount(dominant.getCount());
            g.setSearchTerm(dominant.getFilterCode());
            g.setFamily(family);
            attachBestLog(g, windowLogs, "prepareSearchByRoot", dominant.getFilterCode());
            links.add(g);
        }

        if (facts != null && facts.getDroolsRuleHits() != null) {
            int i = 0;
            for (LatencyQueryGroupDto hit : facts.getDroolsRuleHits()) {
                if (hit.getFilterCode() == null || hit.getCount() <= 0) continue;
                LatencyEvidenceLinkDto rule = new LatencyEvidenceLinkDto(
                        "droolsRule" + i,
                        i == 0 ? "Règle Drools dominante" : "Règle Drools",
                        friendlyRule(hit.getFilterCode()) + " ×" + hit.getCount());
                rule.setValueCount(hit.getCount());
                rule.setSearchTerm(hit.getFilterCode());
                rule.setFamily(family);
                attachBestLog(rule, windowLogs, hit.getFilterCode(), null);
                links.add(rule);
                if (++i >= 5) break;
            }
        }

        if (report != null && report.getMaxDurationMs() != null && report.getMaxDurationMs() > 0) {
            boolean hasTotal = links.stream().anyMatch(l ->
                    report.getMaxDurationMs().equals(l.getValueMs()));
            if (!hasTotal) {
                LatencyEvidenceLinkDto total = new LatencyEvidenceLinkDto(
                        "totalMs", "Durée totale mesurée", formatMs(report.getMaxDurationMs()));
                total.setValueMs(report.getMaxDurationMs());
                total.setSearchTerm("took [" + report.getMaxDurationMs() + "]");
                total.setFamily(family);
                attachBestLog(total, windowLogs, "took [" + report.getMaxDurationMs() + "]", null);
                links.add(total);
            }
        }

        return links;
    }

    private static void addDurationEvidence(List<LatencyEvidenceLinkDto> links,
                                            String id, String label, Long ms,
                                            String mustContain, String alsoContain,
                                            List<LogEntry> windowLogs,
                                            List<LatencyTimelineStepDto> steps,
                                            String family) {
        if (ms == null || ms <= 0) {
            return;
        }
        LatencyEvidenceLinkDto link = new LatencyEvidenceLinkDto(id, label, formatMs(ms));
        link.setValueMs(ms);
        link.setSearchTerm("took [" + ms + "]");
        link.setFamily(family);
        if (steps != null) {
            for (LatencyTimelineStepDto step : steps) {
                if (step.getDurationMs() != null && step.getDurationMs().equals(ms) && step.getLogId() != null) {
                    link.setLogId(step.getLogId());
                    link.setExcerpt(step.getOperationName());
                    if (step.getTimestamp() != null) {
                        link.setTimestamp(step.getTimestamp().format(HMS));
                    }
                    break;
                }
            }
        }
        if (link.getLogId() == null) {
            attachBestLog(link, windowLogs, mustContain, alsoContain);
        }
        links.add(link);
    }

    private static void addCountEvidence(List<LatencyEvidenceLinkDto> links,
                                         String id, String label, Integer count,
                                         String searchHint, String alsoContain,
                                         List<LogEntry> windowLogs,
                                         String family) {
        if (count == null || count <= 0) {
            return;
        }
        LatencyEvidenceLinkDto link = new LatencyEvidenceLinkDto(id, label,
                String.format(Locale.FRANCE, "%,d", count));
        link.setValueCount(count);
        link.setSearchTerm(searchHint != null ? searchHint : String.valueOf(count));
        link.setFamily(family);
        attachBestLog(link, windowLogs, searchHint, alsoContain);
        links.add(link);
    }

    private static void attachBestLog(LatencyEvidenceLinkDto link,
                                      List<LogEntry> windowLogs,
                                      String mustContain,
                                      String alsoContain) {
        if (windowLogs == null || mustContain == null || mustContain.isBlank()) {
            return;
        }
        String must = mustContain.toLowerCase(Locale.ROOT);
        String also = alsoContain != null ? alsoContain.toLowerCase(Locale.ROOT) : null;
        LogEntry best = null;
        for (LogEntry log : windowLogs) {
            String text = combine(log).toLowerCase(Locale.ROOT);
            if (!text.contains(must)) {
                continue;
            }
            if (also != null && !also.isBlank() && !text.contains(also)) {
                continue;
            }
            best = log;
            // Préférer la ligne qui porte aussi la durée recherchée.
            if (link.getValueMs() != null) {
                Matcher m = TOOK_BRACKET.matcher(text);
                while (m.find()) {
                    try {
                        if (Long.parseLong(m.group(1)) == link.getValueMs()) {
                            fillFromLog(link, log);
                            return;
                        }
                    } catch (NumberFormatException ignore) {
                        // continue
                    }
                }
            }
        }
        if (best != null) {
            fillFromLog(link, best);
        }
    }

    private static void fillFromLog(LatencyEvidenceLinkDto link, LogEntry log) {
        link.setLogId(log.getId());
        link.setSourceFile(log.getSourceFileName());
        if (log.getLogTimestamp() != null) {
            link.setTimestamp(log.getLogTimestamp().format(HMS));
        }
        String raw = combine(log).replaceAll("\\s+", " ").trim();
        link.setExcerpt(raw.length() > 220 ? raw.substring(0, 220) + "…" : raw);
        if (link.getSearchTerm() == null || link.getSearchTerm().isBlank()) {
            if (link.getValueMs() != null) {
                link.setSearchTerm("took [" + link.getValueMs() + "]");
            }
        }
    }

    private static String combine(LogEntry log) {
        String m = log.getMessage() != null ? log.getMessage() : "";
        String r = log.getRawLog() != null ? log.getRawLog() : "";
        return m + " " + r;
    }

    private static String formatMs(Long ms) {
        if (ms == null || ms <= 0) {
            return "n/a";
        }
        if (ms >= 1000) {
            return String.format(Locale.FRANCE, "%.1f s", ms / 1000.0);
        }
        return ms + " ms";
    }

    private static String friendlyRule(String raw) {
        if (raw == null || raw.isBlank()) return "n/a";
        String s = raw.trim();
        if (s.toLowerCase(Locale.ROOT).endsWith(".java")) {
            return s.substring(0, s.length() - 5);
        }
        return s;
    }
}
