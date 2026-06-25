package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.*;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ImportComparisonServiceImpl implements ImportComparisonService {

    private static final long SLOW_THRESHOLD_MS = 2000L;
    private static final int TOP_USERS_LIMIT = 8;

    private final LogAnalysisService logAnalysisService;
    private final LogEntryRepository logEntryRepository;

    public ImportComparisonServiceImpl(LogAnalysisService logAnalysisService,
                                       LogEntryRepository logEntryRepository) {
        this.logAnalysisService = logAnalysisService;
        this.logEntryRepository = logEntryRepository;
    }

    @Override
    public ImportComparisonResponseDto compare(ImportComparisonRequestDto request) {
        if (request == null || request.getImportIdA() == null || request.getImportIdB() == null) {
            throw new IllegalArgumentException("importIdA et importIdB sont requis.");
        }
        if (request.getImportIdA().equals(request.getImportIdB())) {
            throw new IllegalArgumentException("Choisissez deux imports différents.");
        }

        ImportAnalysisSummaryDto a = logAnalysisService.getImportSummary(request.getImportIdA());
        ImportAnalysisSummaryDto b = logAnalysisService.getImportSummary(request.getImportIdB());

        ImportComparisonMetricsDto metricsA = buildMetrics(request.getImportIdA());
        ImportComparisonMetricsDto metricsB = buildMetrics(request.getImportIdB());
        List<ImportUserDeltaDto> userDeltas = buildUserDeltas(request.getImportIdA(), request.getImportIdB());
        List<String> regressions = buildRegressions(a, b, metricsA, metricsB, userDeltas);

        ImportComparisonResponseDto response = new ImportComparisonResponseDto();
        response.setSummaryA(a);
        response.setSummaryB(b);
        response.setMetricsA(metricsA);
        response.setMetricsB(metricsB);
        response.setUserDeltas(userDeltas);
        response.setRegressions(regressions);
        response.setDifferences(buildDifferences(a, b, metricsA, metricsB));
        response.setSummary(buildSummary(a, b, regressions));
        response.setRecommendation(buildRecommendation(a, b, metricsA, metricsB, regressions, userDeltas));
        return response;
    }

    private ImportComparisonMetricsDto buildMetrics(Long importId) {
        ImportComparisonMetricsDto metrics = new ImportComparisonMetricsDto();
        metrics.setDistinctUsers(logEntryRepository.countDistinctUsersByImport(importId));
        metrics.setSlowLogCount(logEntryRepository.countSlowLogsByImport(importId, SLOW_THRESHOLD_MS));
        Long maxDuration = logEntryRepository.maxDurationMsByImport(importId);
        metrics.setMaxDurationMs(maxDuration == null ? 0L : maxDuration);
        metrics.setTopUsers(mapCountRows(logEntryRepository.countUsersByImport(importId), TOP_USERS_LIMIT));
        metrics.setTopUsersByErrors(mapCountRows(logEntryRepository.countErrorsByUserForImport(importId), TOP_USERS_LIMIT));
        return metrics;
    }

    private List<ImportUserDeltaDto> buildUserDeltas(Long importIdA, Long importIdB) {
        Map<String, long[]> agg = new LinkedHashMap<>();

        for (Object[] row : logEntryRepository.countUsersByImport(importIdA)) {
            String user = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            agg.computeIfAbsent(user, k -> new long[4])[0] = count;
        }
        for (Object[] row : logEntryRepository.countUsersByImport(importIdB)) {
            String user = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            agg.computeIfAbsent(user, k -> new long[4])[1] = count;
        }
        for (Object[] row : logEntryRepository.countErrorsByUserForImport(importIdA)) {
            String user = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            agg.computeIfAbsent(user, k -> new long[4])[2] = count;
        }
        for (Object[] row : logEntryRepository.countErrorsByUserForImport(importIdB)) {
            String user = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            agg.computeIfAbsent(user, k -> new long[4])[3] = count;
        }

        return agg.entrySet().stream()
                .map(e -> {
                    ImportUserDeltaDto dto = new ImportUserDeltaDto();
                    dto.setUserName(e.getKey());
                    dto.setLogsA(e.getValue()[0]);
                    dto.setLogsB(e.getValue()[1]);
                    dto.setErrorsA(e.getValue()[2]);
                    dto.setErrorsB(e.getValue()[3]);
                    dto.setErrorDelta(e.getValue()[3] - e.getValue()[2]);
                    return dto;
                })
                .sorted(Comparator.comparingLong(ImportUserDeltaDto::getErrorDelta).reversed()
                        .thenComparingLong(d -> Math.abs(d.getLogsB() - d.getLogsA())).reversed())
                .limit(15)
                .toList();
    }

    private List<String> buildRegressions(ImportAnalysisSummaryDto a,
                                         ImportAnalysisSummaryDto b,
                                         ImportComparisonMetricsDto metricsA,
                                         ImportComparisonMetricsDto metricsB,
                                         List<ImportUserDeltaDto> userDeltas) {
        List<String> regressions = new ArrayList<>();

        if (b.getTotalErrors() > a.getTotalErrors()) {
            regressions.add("Erreurs en hausse : " + a.getTotalErrors() + " → " + b.getTotalErrors()
                    + " (+" + (b.getTotalErrors() - a.getTotalErrors()) + ")");
        }
        if (b.getErrorRate() > a.getErrorRate() + 0.5) {
            regressions.add(String.format(Locale.ROOT,
                    "Taux d'erreur en hausse : %.2f%% → %.2f%%", a.getErrorRate(), b.getErrorRate()));
        }
        if (metricsB.getSlowLogCount() > metricsA.getSlowLogCount()) {
            regressions.add("Lenteurs (≥ " + SLOW_THRESHOLD_MS + " ms) : "
                    + metricsA.getSlowLogCount() + " → " + metricsB.getSlowLogCount()
                    + " (+" + (metricsB.getSlowLogCount() - metricsA.getSlowLogCount()) + ")");
        }
        if (metricsB.getMaxDurationMs() > metricsA.getMaxDurationMs()) {
            regressions.add("Pic de latence : " + metricsA.getMaxDurationMs() + " ms → "
                    + metricsB.getMaxDurationMs() + " ms");
        }

        userDeltas.stream()
                .filter(u -> u.getErrorDelta() > 0)
                .limit(5)
                .forEach(u -> regressions.add("Utilisateur « " + u.getUserName() + " » : "
                        + u.getErrorsA() + " → " + u.getErrorsB() + " erreur(s)"));

        if (regressions.isEmpty()) {
            regressions.add("Aucune régression majeure détectée sur erreurs, latences ou utilisateurs.");
        }
        return regressions;
    }

    private List<String> buildDifferences(ImportAnalysisSummaryDto a,
                                          ImportAnalysisSummaryDto b,
                                          ImportComparisonMetricsDto metricsA,
                                          ImportComparisonMetricsDto metricsB) {
        List<String> diffs = new ArrayList<>();
        diffs.add("Logs : " + a.getTotalLogs() + " → " + b.getTotalLogs() + " (Δ " + (b.getTotalLogs() - a.getTotalLogs()) + ")");
        diffs.add("Erreurs : " + a.getTotalErrors() + " → " + b.getTotalErrors() + " (Δ " + (b.getTotalErrors() - a.getTotalErrors()) + ")");
        diffs.add("Taux d'erreur : " + a.getErrorRate() + "% → " + b.getErrorRate() + "%");
        diffs.add("Utilisateurs distincts : " + metricsA.getDistinctUsers() + " → " + metricsB.getDistinctUsers());
        diffs.add("Lenteurs ≥ " + SLOW_THRESHOLD_MS + " ms : " + metricsA.getSlowLogCount() + " → " + metricsB.getSlowLogCount());
        diffs.add("Latence max : " + metricsA.getMaxDurationMs() + " ms → " + metricsB.getMaxDurationMs() + " ms");
        diffs.add("Lignes parsées : " + a.getParsedLines() + " / " + a.getTotalLines() + " → "
                + b.getParsedLines() + " / " + b.getTotalLines());
        if (a.getFileName() != null && b.getFileName() != null) {
            diffs.add("Fichier A : " + a.getFileName());
            diffs.add("Fichier B : " + b.getFileName());
        }
        return diffs;
    }

    private String buildSummary(ImportAnalysisSummaryDto a,
                                ImportAnalysisSummaryDto b,
                                List<String> regressions) {
        String regressionHint = regressions.size() == 1 && regressions.get(0).startsWith("Aucune")
                ? "Situation stable."
                : regressions.size() + " signal(aux) de régression.";
        return "Comparaison import " + a.getImportId() + " (« " + nullSafe(a.getFileName()) + " ») vs import "
                + b.getImportId() + " (« " + nullSafe(b.getFileName()) + " »). " + regressionHint;
    }

    private String buildRecommendation(ImportAnalysisSummaryDto a,
                                       ImportAnalysisSummaryDto b,
                                       ImportComparisonMetricsDto metricsA,
                                       ImportComparisonMetricsDto metricsB,
                                       List<String> regressions,
                                       List<ImportUserDeltaDto> userDeltas) {
        if (b.getTotalErrors() > a.getTotalErrors()) {
            return "Priorité : diagnostiquer les utilisateurs en hausse d'erreurs, puis comparer leurs sessions.";
        }
        if (metricsB.getSlowLogCount() > metricsA.getSlowLogCount()) {
            return "Priorité : ouvrir l'onglet Expert, regrouper par userName et explorer l'accordéon « Lenteurs ».";
        }
        ImportUserDeltaDto top = userDeltas.isEmpty() ? null : userDeltas.get(0);
        if (top != null && top.getErrorDelta() > 0) {
            return "Cibler l'utilisateur « " + top.getUserName() + " » (+" + top.getErrorDelta() + " erreur(s)).";
        }
        return "Écarts modérés : comparez deux sessions sur le même parcours métier.";
    }

    private List<CountValueDto> mapCountRows(List<Object[]> rows, int limit) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .limit(limit)
                .map(row -> new CountValueDto(
                        row[0] == null ? "—" : String.valueOf(row[0]),
                        row[1] == null ? 0L : ((Number) row[1]).longValue()))
                .toList();
    }

    private String nullSafe(String v) {
        return v != null ? v : "—";
    }
}
