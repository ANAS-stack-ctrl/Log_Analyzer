package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.SessionCompareDiffItemDto;
import com.caciopee.loganalyzer.dto.SessionComparisonRequestDto;
import com.caciopee.loganalyzer.dto.SessionComparisonResponseDto;
import com.caciopee.loganalyzer.dto.SessionSnapshotDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogEntrySpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SessionComparisonServiceImpl implements SessionComparisonService {

    private final LogEntryRepository logEntryRepository;
    private final LogPatternExtractor logPatternExtractor;

    public SessionComparisonServiceImpl(LogEntryRepository logEntryRepository,
                                        LogPatternExtractor logPatternExtractor) {
        this.logEntryRepository = logEntryRepository;
        this.logPatternExtractor = logPatternExtractor;
    }

    @Override
    public SessionComparisonResponseDto compare(SessionComparisonRequestDto request) {
        validate(request);

        List<LogEntry> logsA = loadSessionLogs(request, request.getSessionIdA());
        List<LogEntry> logsB = loadSessionLogs(request, request.getSessionIdB());

        SessionComparisonResponseDto response = new SessionComparisonResponseDto();
        response.setSessionIdA(request.getSessionIdA());
        response.setSessionIdB(request.getSessionIdB());
        response.setProcessFilter(request.getProcessName());
        response.setSnapshotA(buildSnapshot(request.getSessionIdA(), logsA));
        response.setSnapshotB(buildSnapshot(request.getSessionIdB(), logsB));
        response.setDifferences(buildDifferences(logsA, logsB));
        response.setSummary(buildSummary(response));
        response.setRecommendation(buildRecommendation(response));
        return response;
    }

    private void validate(SessionComparisonRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Requête vide.");
        }
        if (request.getImportIds() == null || request.getImportIds().isEmpty()) {
            throw new IllegalArgumentException("Au moins un importId est requis.");
        }
        if (request.getSessionIdA() == null || request.getSessionIdA().isBlank()) {
            throw new IllegalArgumentException("sessionIdA est requis.");
        }
        if (request.getSessionIdB() == null || request.getSessionIdB().isBlank()) {
            throw new IllegalArgumentException("sessionIdB est requis.");
        }
        if (request.getSessionIdA().trim().equalsIgnoreCase(request.getSessionIdB().trim())) {
            throw new IllegalArgumentException("Les deux sessionId doivent être différents.");
        }
    }

    private List<LogEntry> loadSessionLogs(SessionComparisonRequestDto request, String sessionId) {
        Specification<LogEntry> spec = Specification.allOf(
                LogEntrySpecifications.hasImportIds(request.getImportIds()),
                LogEntrySpecifications.hasSessionId(sessionId),
                LogEntrySpecifications.hasTimestampBetween(request.getDateFrom(), request.getDateTo())
        );

        List<LogEntry> logs = logEntryRepository.findAll(
                spec,
                Sort.by(Sort.Direction.ASC, "logTimestamp").and(Sort.by(Sort.Direction.ASC, "id"))
        );

        String processFilter = request.getProcessName();
        if (processFilter == null || processFilter.isBlank()) {
            return logs;
        }

        String needle = processFilter.trim().toLowerCase(Locale.ROOT);
        return logs.stream()
                .filter(log -> matchesProcessFilter(log, needle))
                .toList();
    }

    private boolean matchesProcessFilter(LogEntry log, String needle) {
        if (log.getProcessName() != null && log.getProcessName().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        GraphExtraction ex = logPatternExtractor.extractGraph(log);
        return ex.getProcess() != null && ex.getProcess().toLowerCase(Locale.ROOT).contains(needle);
    }

    private SessionSnapshotDto buildSnapshot(String sessionId, List<LogEntry> logs) {
        SessionSnapshotDto snap = new SessionSnapshotDto();
        snap.setSessionId(sessionId);
        snap.setTotalLogs((long) logs.size());

        if (logs.isEmpty()) {
            snap.setErrorCount(0L);
            snap.setWarningCount(0L);
            snap.setZeroResultCount(0L);
            return snap;
        }

        snap.setErrorCount(logs.stream().filter(l -> Boolean.TRUE.equals(l.getIsError())).count());
        snap.setWarningCount(logs.stream().filter(this::isWarning).count());
        snap.setZeroResultCount(logs.stream().filter(this::isZeroResult).count());
        snap.setFirstTimestamp(logs.stream().map(LogEntry::getLogTimestamp).filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null));
        snap.setLastTimestamp(logs.stream().map(LogEntry::getLogTimestamp).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null));
        snap.setDominantProcess(dominantProcess(logs));
        return snap;
    }

    private String dominantProcess(List<LogEntry> logs) {
        Map<String, Long> counts = new HashMap<>();
        for (LogEntry log : logs) {
            GraphExtraction ex = logPatternExtractor.extractGraph(log);
            if (ex.getProcess() != null && !ex.getProcess().isBlank()) {
                counts.merge(ex.getProcess(), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private List<SessionCompareDiffItemDto> buildDifferences(List<LogEntry> logsA, List<LogEntry> logsB) {
        Map<String, EntityAgg> aggA = aggregate(logsA);
        Map<String, EntityAgg> aggB = aggregate(logsB);

        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(aggA.keySet());
        allKeys.addAll(aggB.keySet());

        List<SessionCompareDiffItemDto> diffs = new ArrayList<>();

        for (String key : allKeys) {
            EntityAgg a = aggA.get(key);
            EntityAgg b = aggB.get(key);

            SessionCompareDiffItemDto item = new SessionCompareDiffItemDto();
            if (a != null) {
                item.setCategory(a.category);
                item.setName(a.name);
            } else {
                item.setCategory(b.category);
                item.setName(b.name);
            }

            long countA = a != null ? a.count : 0;
            long countB = b != null ? b.count : 0;
            item.setCountA(countA);
            item.setCountB(countB);
            item.setDeltaCount(countB - countA);
            item.setMaxDurationMsA(a != null ? a.maxDurationMs : null);
            item.setMaxDurationMsB(b != null ? b.maxDurationMs : null);
            item.setStatus(resolveStatus(a, b, countA, countB));
            item.setNote(buildNote(item));
            diffs.add(item);
        }

        return diffs.stream()
                .sorted(Comparator
                        .comparing(SessionCompareDiffItemDto::getCategory)
                        .thenComparing(d -> Math.abs(nullSafe(d.getDeltaCount())), Comparator.reverseOrder())
                        .thenComparing(SessionCompareDiffItemDto::getName))
                .toList();
    }

    private String resolveStatus(EntityAgg a, EntityAgg b, long countA, long countB) {
        if (a == null) return "ONLY_B";
        if (b == null) return "ONLY_A";
        if (countA == countB) {
            Long durA = a.maxDurationMs;
            Long durB = b.maxDurationMs;
            if (durA != null && durB != null && Math.abs(durA - durB) >= 500) {
                return "CHANGED";
            }
            return "BOTH";
        }
        return "CHANGED";
    }

    private String buildNote(SessionCompareDiffItemDto item) {
        if ("ONLY_A".equals(item.getStatus())) {
            return "Présent uniquement dans la session A.";
        }
        if ("ONLY_B".equals(item.getStatus())) {
            return "Présent uniquement dans la session B.";
        }
        if (item.getMaxDurationMsA() != null && item.getMaxDurationMsB() != null
                && item.getMaxDurationMsB() - item.getMaxDurationMsA() >= 1000) {
            return "Durée max plus élevée en session B (+" + (item.getMaxDurationMsB() - item.getMaxDurationMsA()) + " ms).";
        }
        if (item.getMaxDurationMsA() != null && item.getMaxDurationMsB() != null
                && item.getMaxDurationMsA() - item.getMaxDurationMsB() >= 1000) {
            return "Durée max plus élevée en session A (+" + (item.getMaxDurationMsA() - item.getMaxDurationMsB()) + " ms).";
        }
        if (item.getDeltaCount() != null && item.getDeltaCount() != 0) {
            return "Fréquence différente entre les deux sessions.";
        }
        return "Comportement similaire.";
    }

    private Map<String, EntityAgg> aggregate(List<LogEntry> logs) {
        Map<String, EntityAgg> map = new HashMap<>();

        for (LogEntry log : logs) {
            GraphExtraction ex = logPatternExtractor.extractGraph(log);
            addEntity(map, "PROCESS", ex.getProcess(), ex.getDurationMs());
            addEntity(map, "TASK", ex.getTask(), ex.getDurationMs());
            addEntity(map, "ACTION", ex.getAction(), ex.getDurationMs());
            addEntity(map, "FILTER", ex.getFilter(), ex.getDurationMs());
        }

        return map;
    }

    private void addEntity(Map<String, EntityAgg> map, String category, String name, Long durationMs) {
        if (name == null || name.isBlank()) return;

        String key = category + "::" + name;
        EntityAgg agg = map.computeIfAbsent(key, k -> new EntityAgg(category, name));
        agg.count++;
        if (durationMs != null) {
            agg.maxDurationMs = agg.maxDurationMs == null ? durationMs : Math.max(agg.maxDurationMs, durationMs);
        }
    }

    private String buildSummary(SessionComparisonResponseDto r) {
        SessionSnapshotDto a = r.getSnapshotA();
        SessionSnapshotDto b = r.getSnapshotB();

        long onlyA = r.getDifferences().stream().filter(d -> "ONLY_A".equals(d.getStatus())).count();
        long onlyB = r.getDifferences().stream().filter(d -> "ONLY_B".equals(d.getStatus())).count();
        long changed = r.getDifferences().stream().filter(d -> "CHANGED".equals(d.getStatus())).count();

        return "Session A (" + r.getSessionIdA() + ") : " + a.getTotalLogs() + " logs, "
                + a.getErrorCount() + " erreur(s), process dominant "
                + nullSafeStr(a.getDominantProcess(), "—")
                + ". Session B (" + r.getSessionIdB() + ") : " + b.getTotalLogs() + " logs, "
                + b.getErrorCount() + " erreur(s), process dominant "
                + nullSafeStr(b.getDominantProcess(), "—")
                + ". Différences : " + onlyA + " entité(s) seulement en A, "
                + onlyB + " seulement en B, " + changed + " avec variation de fréquence ou durée.";
    }

    private String buildRecommendation(SessionComparisonResponseDto r) {
        SessionSnapshotDto a = r.getSnapshotA();
        SessionSnapshotDto b = r.getSnapshotB();

        if (a.getTotalLogs() == 0 || b.getTotalLogs() == 0) {
            return "Une des sessions est vide pour les filtres choisis. Vérifiez les importIds, la période ou le filtre process.";
        }

        if (!Objects.equals(a.getDominantProcess(), b.getDominantProcess())) {
            return "Les process dominants diffèrent : comparer d’abord les filtres et actions communes, puis les entités ONLY_A / ONLY_B.";
        }

        long slowFilters = r.getDifferences().stream()
                .filter(d -> "FILTER".equals(d.getCategory()) && "CHANGED".equals(d.getStatus()))
                .filter(d -> d.getMaxDurationMsB() != null && d.getMaxDurationMsA() != null
                        && d.getMaxDurationMsB() - d.getMaxDurationMsA() >= 1000)
                .count();

        if (slowFilters > 0) {
            return "Des filtres sont nettement plus lents en session B : inspecter les requêtes associées et les 0 row / warnings.";
        }

        if (b.getErrorCount() > a.getErrorCount()) {
            return "La session B contient plus d’erreurs : prioriser l’analyse des logs ERROR et des warnings de règles.";
        }

        return "Écarts modérés : utiliser le graphe relationnel sur chaque session pour affiner le diagnostic.";
    }

    private boolean isWarning(LogEntry log) {
        String level = log.getLevel();
        if (level != null && (level.equalsIgnoreCase("WARN") || level.equalsIgnoreCase("WARNING"))) {
            return true;
        }
        String msg = log.getMessage();
        return msg != null && (msg.toLowerCase(Locale.ROOT).contains("no rule found")
                || msg.toLowerCase(Locale.ROOT).contains("aucune"));
    }

    private boolean isZeroResult(LogEntry log) {
        String msg = log.getMessage();
        return msg != null && (msg.contains("0 row") || msg.contains("[0] row fetched"));
    }

    private long nullSafe(Long value) {
        return value != null ? value : 0L;
    }

    private String nullSafeStr(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private static final class EntityAgg {
        final String category;
        final String name;
        long count;
        Long maxDurationMs;

        EntityAgg(String category, String name) {
            this.category = category;
            this.name = name;
        }
    }
}
