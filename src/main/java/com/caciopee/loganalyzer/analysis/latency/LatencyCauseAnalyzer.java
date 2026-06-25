package com.caciopee.loganalyzer.analysis.latency;



import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;

import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;

import com.caciopee.loganalyzer.entity.LogEntry;

import org.springframework.stereotype.Component;



import java.util.Comparator;

import java.util.List;

import java.util.Locale;

import java.util.Objects;

import java.util.regex.Matcher;

import java.util.regex.Pattern;

import java.util.stream.Collectors;



@Component

public class LatencyCauseAnalyzer {



    private static final Pattern JOIN_PATTERN = Pattern.compile(

            "\\b(inner\\s+join|join)\\b", Pattern.CASE_INSENSITIVE);



    private final LatencyOperationClassifier classifier;

    private final WorksLatencyFactExtractor factExtractor;

    private final ClientLatencyNarrativeBuilder clientNarrativeBuilder;

    public LatencyCauseAnalyzer(LatencyOperationClassifier classifier,
                                WorksLatencyFactExtractor factExtractor,
                                ClientLatencyNarrativeBuilder clientNarrativeBuilder) {
        this.classifier = classifier;
        this.factExtractor = factExtractor;
        this.clientNarrativeBuilder = clientNarrativeBuilder;
    }



    public void enrichReport(LatencyOriginReportDto report,

                             List<LatencyTimelineStepDto> steps,

                             List<LogEntry> windowLogs) {

        if (steps == null || steps.isEmpty()) {

            report.setAnalysisConfidence("LOW");

            report.setPrimaryCause("Aucune étape mesurable dans la fenêtre analysée.");

            report.setNarrativeSummary(buildFallbackNarrative(report));

            report.setChainExplanation(buildChain(report, steps));

            return;

        }



        LatencyBottleneckAnalyzer.BottleneckResult bottleneck =

                LatencyBottleneckAnalyzer.analyze(steps, classifier);

        LatencyTimelineStepDto primary = bottleneck != null ? bottleneck.primary() : steps.get(0);

        LatencyBottleneckAnalyzer.NarrativeKind kind = bottleneck != null

                ? bottleneck.kind()

                : LatencyBottleneckAnalyzer.NarrativeKind.SINGLE_STEP;



        markBottleneck(steps, primary);

        WorksLatencyFacts facts = factExtractor.extract(report, windowLogs, steps);

        report.setPrimaryCause(clientNarrativeBuilder.buildPrimaryCause(facts, kind, primary));
        report.setClientSummary(clientNarrativeBuilder.buildSummary(facts, kind));
        report.setNarrativeSummary(clientNarrativeBuilder.buildFullNarrative(
                facts, kind, report.getMaxDurationMs() != null ? report.getMaxDurationMs() : 0L));
        report.setChainExplanation(buildChain(report, steps));
        report.setAnalysisConfidence(resolveConfidence(steps, kind, facts));

    }



    private void markBottleneck(List<LatencyTimelineStepDto> steps, LatencyTimelineStepDto primary) {

        for (LatencyTimelineStepDto step : steps) {

            step.setBottleneck(sameStep(step, primary));

        }

    }



    private String describeCause(LatencyTimelineStepDto primary, int sqlJoins) {

        String type = safe(primary.getStepType());

        String label = classifier.humanLabel(type, primary.getOperationName());



        StringBuilder sb = new StringBuilder();

        sb.append(label).append(" — ").append(formatDuration(primary.getDurationMs()));

        if (primary.getRowCount() != null) {

            sb.append(" — ").append(primary.getRowCount()).append(" ligne(s)");

        }

        if (primary.getMemoryMo() != null) {

            sb.append(" — ").append(primary.getMemoryMo()).append(" Mo");

        }

        if (LatencyOperationClassifier.ROOT_QUERY.equals(type) && sqlJoins >= 3) {

            sb.append(" — ").append(sqlJoins).append(" jointures SQL");

        }

        return sb.toString();

    }



    private String buildNarrative(LatencyOriginReportDto report,

                                  List<LatencyTimelineStepDto> steps,

                                  LatencyTimelineStepDto primary,

                                  LatencyBottleneckAnalyzer.NarrativeKind kind,

                                  int sqlJoins) {

        return switch (kind) {

            case SQL_DOMINATED -> buildSqlDominatedNarrative(report, steps, sqlJoins);

            case PARALLEL_LOAD_DOMINATED -> buildParallelLoadNarrative(report, steps, primary);

            case RULES_DOMINATED -> buildRulesNarrative(report, primary);

            case RENDERING_DOMINATED -> buildRenderingNarrative(report, primary);

            case CONTAINER_DOMINATED -> buildContainerNarrative(report, primary, steps);

            case COMPLEX -> buildComplexNarrative(report, steps, primary);

            case SINGLE_STEP -> buildSingleStepNarrative(report, primary);

        };

    }



    private String buildSqlDominatedNarrative(LatencyOriginReportDto report,

                                              List<LatencyTimelineStepDto> steps,

                                              int sqlJoins) {

        LatencyTimelineStepDto root = findStep(steps, LatencyOperationClassifier.ROOT_QUERY);

        LatencyTimelineStepDto global = findStep(steps, LatencyOperationClassifier.GLOBAL_CONTAINER,

                LatencyOperationClassifier.PARTITIONAL);

        LatencyTimelineStepDto load = findStep(steps, LatencyOperationClassifier.CHILD_LOAD);

        LatencyTimelineStepDto searchAttr = findStep(steps, LatencyOperationClassifier.CHILD_QUERY);

        LatencyTimelineStepDto loadB2 = findStep(steps, LatencyOperationClassifier.CHILD_LOAD_B2);



        StringBuilder sb = new StringBuilder();

        sb.append("La lenteur provient quasi entièrement de la requête SQL racine.\n\n");



        appendStepLine(sb, 1, root);

        sb.append("   → ");

        if (sqlJoins >= 3) {

            sb.append("Requête SQL complexe (").append(sqlJoins).append(" jointures détectées).\n");

        } else {

            sb.append("Exécution SQL lente sur la recherche racine.\n");

        }



        long globalMs = global != null && global.getDurationMs() != null

                ? global.getDurationMs()

                : root.getDurationMs();

        double share = globalMs > 0 ? (100.0 * root.getDurationMs() / globalMs) : 100.0;

        sb.append("   → Représente ~").append(String.format(Locale.FRANCE, "%.0f", share))

                .append(" % du temps total de l'opération globale (")

                .append(formatDuration(globalMs)).append(").\n");



        if (global != null) {

            sb.append("\n");

            appendStepLine(sb, 2, global);

            sb.append("   → Phase aval rapide");

            if (load != null) {

                sb.append(" : ").append(load.getOperationName()).append(" ")

                        .append(formatDuration(load.getDurationMs()));

            }

            if (searchAttr != null) {

                sb.append(", ").append(searchAttr.getOperationName()).append(" ")

                        .append(formatDuration(searchAttr.getDurationMs()));

            }

            if (loadB2 != null) {

                sb.append(", Query B2 ").append(formatDuration(loadB2.getDurationMs()));

            }

            sb.append(".\n");

        }



        sb.append("\nConclusion : avec ")

                .append(root.getRowCount() != null ? root.getRowCount() : "peu")

                .append(" ligne(s) retournée(s), le goulet est l'exécution SQL initiale, ")

                .append("pas le chargement parallèle des enfants.");



        return sb.toString().trim();

    }



    private String buildParallelLoadNarrative(LatencyOriginReportDto report,

                                              List<LatencyTimelineStepDto> steps,

                                              LatencyTimelineStepDto primary) {

        LatencyTimelineStepDto root = findStep(steps, LatencyOperationClassifier.ROOT_QUERY);

        LatencyTimelineStepDto global = findStep(steps, LatencyOperationClassifier.GLOBAL_CONTAINER,

                LatencyOperationClassifier.PARTITIONAL);

        LatencyTimelineStepDto multithread = findStep(steps, LatencyOperationClassifier.MULTITHREAD_START,

                LatencyOperationClassifier.MULTITHREAD_CONFIG);



        StringBuilder sb = new StringBuilder();

        sb.append("La lenteur est dominée par le chargement parallèle des composants enfants.\n\n");



        if (root != null) {

            appendStepLine(sb, 1, root);

            sb.append("   → La requête racine retourne ")

                    .append(root.getRowCount() != null ? root.getRowCount() : "un volume")

                    .append(" ligne(s) et déclenche le chargement parallèle.\n\n");

        }



        if (multithread != null && multithread.getRowCount() != null && multithread.getRowCount() > 1) {

            sb.append("2. Multithreading : ")

                    .append(multithread.getRowCount()).append(" lot(s) traités en parallèle\n");

        }



        int n = root != null ? 2 : 1;

        appendStepLine(sb, n, primary);

        sb.append("   → Cette étape concentre le temps d'attente utilisateur.\n");



        if (global != null && !sameStep(primary, global)) {

            sb.append("\n");

            appendStepLine(sb, n + 1, global);

            sb.append("   → Durée globale incluant toutes les phases de recherche.\n");

        }



        sb.append("\nLe symptôme (").append(formatDuration(report.getMaxDurationMs()))

                .append(") est porté principalement par « ")

                .append(primary.getOperationName()).append(" ».");



        return sb.toString().trim();

    }



    private String buildRulesNarrative(LatencyOriginReportDto report, LatencyTimelineStepDto primary) {

        StringBuilder sb = new StringBuilder();

        sb.append("La lenteur provient du moteur de règles métier (running rules).\n\n");

        appendStepLine(sb, 1, primary);

        sb.append("\nConclusion : l'exécution des règles Drools sur ")

                .append(label(report.getFilterCode(), report.getProcessName()))

                .append(" consomme la quasi-totalité du temps (")

                .append(formatDuration(primary.getDurationMs()))

                .append("). Vérifier la complexité des règles et le volume de faits en mémoire.");

        return sb.toString().trim();

    }



    private String buildRenderingNarrative(LatencyOriginReportDto report, LatencyTimelineStepDto primary) {

        StringBuilder sb = new StringBuilder();

        sb.append("La lenteur est concentrée sur le rendu du résultat.\n\n");

        appendStepLine(sb, 1, primary);

        sb.append("\nLes phases de recherche/chargement sont terminées ; le goulet est le rendu UI (");

        sb.append(formatDuration(primary.getDurationMs())).append(").");

        return sb.toString().trim();

    }



    private String buildContainerNarrative(LatencyOriginReportDto report,

                                             LatencyTimelineStepDto primary,

                                             List<LatencyTimelineStepDto> steps) {

        StringBuilder sb = new StringBuilder();

        sb.append("La lenteur est portée par l'opération globale, sans sous-étape clairement dominante.\n\n");

        appendStepLine(sb, 1, primary);



        List<LatencyTimelineStepDto> children = steps.stream()

                .filter(s -> s.getDurationMs() != null && s.getDurationMs() >= 500)

                .filter(s -> !sameStep(s, primary))

                .sorted(Comparator.comparingLong((LatencyTimelineStepDto s) -> s.getDurationMs()).reversed())

                .limit(3)

                .toList();



        if (!children.isEmpty()) {

            sb.append("\n\nSous-étapes significatives :\n");

            int i = 2;

            for (LatencyTimelineStepDto child : children) {

                appendStepLine(sb, i++, child);

            }

        }



        sb.append("\n\nSymptôme global : ").append(formatDuration(report.getMaxDurationMs()));

        return sb.toString().trim();

    }



    private String buildComplexNarrative(LatencyOriginReportDto report,

                                         List<LatencyTimelineStepDto> steps,

                                         LatencyTimelineStepDto primary) {

        StringBuilder sb = new StringBuilder();

        sb.append("Plusieurs étapes contribuent à la lenteur sur ")

                .append(label(report.getFilterCode(), report.getProcessName()))

                .append(" :\n\n");



        List<LatencyTimelineStepDto> significant = steps.stream()

                .filter(s -> s.getDurationMs() != null && s.getDurationMs() >= 500)

                .sorted(Comparator.comparingLong((LatencyTimelineStepDto s) -> s.getDurationMs()).reversed())

                .limit(5)

                .toList();



        int i = 1;

        for (LatencyTimelineStepDto step : significant) {

            appendStepLine(sb, i++, step);

            if (sameStep(step, primary)) {

                sb.append("   → Étape principale identifiée.\n");

            }

        }



        sb.append("\nLe symptôme (").append(formatDuration(report.getMaxDurationMs()))

                .append(") est porté principalement par « ")

                .append(primary.getOperationName()).append(" ».");



        return sb.toString().trim();

    }



    private String buildSingleStepNarrative(LatencyOriginReportDto report, LatencyTimelineStepDto primary) {

        StringBuilder sb = new StringBuilder();

        sb.append("Origine de la lenteur sur ")

                .append(label(report.getFilterCode(), report.getProcessName()))

                .append(" :\n\n");

        appendStepLine(sb, 1, primary);

        sb.append("\nLe symptôme observé (")

                .append(formatDuration(report.getMaxDurationMs()))

                .append(") correspond à l'étape « ")

                .append(primary.getOperationName())

                .append(" ».");

        return sb.toString().trim();

    }



    private String buildChain(LatencyOriginReportDto report, List<LatencyTimelineStepDto> steps) {

        String user = notBlank(report.getUserName()) ? report.getUserName() : "utilisateur";

        String filter = notBlank(report.getFilterCode()) ? report.getFilterCode() : "filtre métier";

        String process = notBlank(report.getProcessName()) ? report.getProcessName() : "processus";



        if (steps == null || steps.isEmpty()) {

            return user + " → " + process + " → filtre " + filter;

        }



        List<String> chainParts = steps.stream()

                .filter(s -> s.getDurationMs() != null || LatencyOperationClassifier.SEARCH_START.equals(s.getStepType()))

                .map(s -> {

                    if (LatencyOperationClassifier.SEARCH_START.equals(s.getStepType())) {

                        return "début recherche";

                    }

                    if (LatencyOperationClassifier.ROOT_QUERY.equals(s.getStepType())) {

                        return "requête racine SQL";

                    }

                    if (LatencyOperationClassifier.CHILD_LOAD.equals(s.getStepType())) {

                        return "chargement enfants";

                    }

                    if (LatencyOperationClassifier.GLOBAL_CONTAINER.equals(s.getStepType())) {

                        return "opération globale";

                    }

                    if (LatencyOperationClassifier.RULES_ENGINE.equals(s.getStepType())) {

                        return "moteur de règles";

                    }

                    if (LatencyOperationClassifier.RENDERING.equals(s.getStepType())) {

                        return "rendu résultat";

                    }

                    return s.getOperationName();

                })

                .distinct()

                .limit(6)

                .collect(Collectors.toList());



        String chain = String.join(" → ", chainParts);

        return user + " → " + process + " → filtre " + filter + " → " + chain;

    }



    private String buildFallbackNarrative(LatencyOriginReportDto report) {

        return "Latence détectée (" + formatDuration(report.getMaxDurationMs()) + ") sur "

                + label(report.getFilterCode(), report.getProcessName())

                + ". Les logs de la fenêtre ne contiennent pas assez d'étapes mesurées (took [N] ms) "

                + "pour isoler l'origine. Consultez la chronologie ci-dessous ou affinez le périmètre (session + uuid).";

    }



    private String resolveConfidence(List<LatencyTimelineStepDto> steps,
                                     LatencyBottleneckAnalyzer.NarrativeKind kind,
                                     WorksLatencyFacts facts) {

        long measuredCount = steps.stream()

                .filter(s -> s.getDurationMs() != null && s.getDurationMs() >= 2000)

                .count();



        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.SQL_DOMINATED
                || kind == LatencyBottleneckAnalyzer.NarrativeKind.PARALLEL_LOAD_DOMINATED
                || kind == LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED) {
            return "HIGH";
        }
        if (facts.getGlobalMs() != null || facts.getRootQueryMs() != null || facts.getRulesEngineMs() != null) {
            return "HIGH";
        }

        if (measuredCount >= 2) {

            return "HIGH";

        }

        if (measuredCount >= 1) {

            return "MEDIUM";

        }

        return "LOW";

    }



    private int countSqlJoins(List<LogEntry> windowLogs) {

        if (windowLogs == null) {

            return 0;

        }

        int maxJoins = 0;

        for (LogEntry log : windowLogs) {

            String text = safe(log.getMessage()) + " " + safe(log.getRawLog());

            if (!text.toLowerCase(Locale.ROOT).contains("query:")) {

                continue;

            }

            int joins = 0;

            Matcher matcher = JOIN_PATTERN.matcher(text);

            while (matcher.find()) {

                joins++;

            }

            maxJoins = Math.max(maxJoins, joins);

        }

        return maxJoins;

    }



    private void appendStepLine(StringBuilder sb, int index, LatencyTimelineStepDto step) {

        if (step == null) {

            return;

        }

        sb.append(index).append(". ").append(step.getOperationName()).append(" : ");

        sb.append(formatDuration(step.getDurationMs()));

        if (step.getRowCount() != null) {

            sb.append(" — ").append(step.getRowCount()).append(" ligne(s)");

        }

        if (step.getMemoryMo() != null) {

            sb.append(" — ").append(step.getMemoryMo()).append(" Mo");

        }

        sb.append('\n');

    }



    private LatencyTimelineStepDto findStep(List<LatencyTimelineStepDto> steps, String... types) {

        for (String type : types) {

            for (LatencyTimelineStepDto step : steps) {

                if (type.equals(step.getStepType())) {

                    return step;

                }

            }

        }

        return null;

    }



    private boolean sameStep(LatencyTimelineStepDto a, LatencyTimelineStepDto b) {

        if (a == null || b == null) {

            return false;

        }

        if (a.getLogId() != null && b.getLogId() != null) {

            return Objects.equals(a.getLogId(), b.getLogId());

        }

        return Objects.equals(a.getStepType(), b.getStepType())

                && Objects.equals(a.getOperationName(), b.getOperationName());

    }



    private String label(String filter, String process) {

        if (notBlank(filter)) {

            return "le filtre " + filter.trim();

        }

        if (notBlank(process)) {

            return "le process " + process.trim();

        }

        return "cette opération";

    }



    private String formatDuration(Long ms) {

        if (ms == null || ms <= 0) {

            return "durée non mesurée";

        }

        if (ms >= 1000) {

            double sec = ms / 1000.0;

            return String.format(Locale.FRANCE, "%.1f s", sec);

        }

        return ms + " ms";

    }



    private String safe(String value) {

        return value != null ? value : "";

    }



    private boolean notBlank(String value) {

        return value != null && !value.isBlank();

    }

}


