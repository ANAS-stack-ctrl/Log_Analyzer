package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.latency.LatencyCauseAnalyzer;
import com.caciopee.loganalyzer.analysis.latency.WorksLatencyTimelineParser;
import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;
import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class LatencyInvestigationService {

    private static final Pattern UUID_IN_MESSAGE = Pattern.compile(
            "uuid\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final long SLOW_THRESHOLD_MS = 2000L;
    private static final int MIN_SCOPE_LOGS = 5;

    private final LogEntryRepository logEntryRepository;
    private final LogPatternExtractor logPatternExtractor;
    private final WorksLatencyTimelineParser timelineParser;
    private final LatencyCauseAnalyzer causeAnalyzer;

    public LatencyInvestigationService(LogEntryRepository logEntryRepository,
                                       LogPatternExtractor logPatternExtractor,
                                       WorksLatencyTimelineParser timelineParser,
                                       LatencyCauseAnalyzer causeAnalyzer) {
        this.logEntryRepository = logEntryRepository;
        this.logPatternExtractor = logPatternExtractor;
        this.timelineParser = timelineParser;
        this.causeAnalyzer = causeAnalyzer;
    }

    public LatencyOriginReportDto investigate(Long importId,
                                              Long evidenceLogId,
                                              String sessionId,
                                              String uuid,
                                              String filterCode,
                                              String processName) {
        if (importId == null) {
            throw new IllegalArgumentException("importId requis.");
        }

        LogEntry anchor = resolveAnchor(importId, evidenceLogId, sessionId, filterCode);
        if (anchor == null) {
            throw new IllegalArgumentException("Aucun log d'ancrage trouvé pour cette latence.");
        }

        GraphExtraction anchorExtraction = logPatternExtractor.extractGraph(anchor);
        String resolvedUuid = coalesce(uuid, anchorExtraction.getUuid(), extractUuidFromText(combine(anchor)));
        String resolvedSession = coalesce(sessionId, anchor.getSessionId());
        String resolvedFilter = coalesce(filterCode, anchorExtraction.getFilter(), extractFilter(combine(anchor)));
        String resolvedProcess = coalesce(processName, anchorExtraction.getProcess(), anchor.getProcessName());

        List<LogEntry> scopeLogs = loadScopeLogs(importId, resolvedSession, resolvedUuid, resolvedFilter);
        if (scopeLogs.isEmpty()) {
            scopeLogs = List.of(anchor);
        }

        int anchorIndex = indexOfLog(scopeLogs, anchor.getId());
        if (anchorIndex < 0) {
            anchorIndex = indexOfSlowestLog(scopeLogs);
        }

        List<LogEntry> windowLogs = extractAdaptiveWindow(scopeLogs, anchorIndex);
        List<LatencyTimelineStepDto> steps = timelineParser.parseTimeline(windowLogs);

        long maxDuration = steps.stream()
                .map(LatencyTimelineStepDto::getDurationMs)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(resolveDuration(anchor));

        LatencyOriginReportDto report = new LatencyOriginReportDto();
        report.setImportId(importId);
        report.setAnchorLogId(anchor.getId());
        report.setSessionId(resolvedSession);
        report.setUuid(resolvedUuid);
        report.setUserName(anchor.getUserName());
        report.setProcessName(resolvedProcess);
        report.setFilterCode(resolvedFilter);
        report.setMaxDurationMs(maxDuration);
        report.setScopeDescription(buildScopeDescription(resolvedSession, resolvedUuid, resolvedFilter));
        report.setLogsInScope(scopeLogs.size());
        report.setLogsInWindow(windowLogs.size());
        report.setTimelineSteps(steps);
        report.setChronologicalText(timelineParser.buildChronologicalText(windowLogs));

        causeAnalyzer.enrichReport(report, steps, windowLogs);
        return report;
    }

    private LogEntry resolveAnchor(Long importId, Long evidenceLogId, String sessionId, String filterCode) {
        List<LogEntry> candidates = loadCandidateLogs(importId, sessionId, filterCode);
        if (candidates.isEmpty()) {
            if (evidenceLogId != null) {
                return logEntryRepository.findById(evidenceLogId)
                        .filter(log -> belongsToImport(log, importId))
                        .orElse(null);
            }
            return null;
        }

        LogEntry slowest = candidates.stream()
                .filter(log -> resolveDuration(log) >= SLOW_THRESHOLD_MS)
                .max(Comparator.comparingLong(this::resolveDuration))
                .orElseGet(() -> candidates.stream()
                        .max(Comparator.comparingLong(this::resolveDuration))
                        .orElse(candidates.get(candidates.size() - 1)));

        if (evidenceLogId == null) {
            return slowest;
        }

        LogEntry evidence = logEntryRepository.findById(evidenceLogId)
                .filter(log -> belongsToImport(log, importId))
                .orElse(null);

        if (evidence == null) {
            return slowest;
        }

        long slowestMs = resolveDuration(slowest);
        long evidenceMs = resolveDuration(evidence);
        if (evidenceMs >= slowestMs * 0.85) {
            return evidence;
        }
        return slowest;
    }

    private List<LogEntry> loadCandidateLogs(Long importId, String sessionId, String filterCode) {
        List<LogEntry> logs;
        if (notBlank(sessionId)) {
            logs = logEntryRepository.findByLogImportIdAndSessionIdOrderByLogTimestampAsc(importId, sessionId);
        } else {
            logs = logEntryRepository.findByLogImportIdOrderByLogTimestampAscIdAsc(importId);
        }

        String filterNorm = normalize(filterCode);
        if (!notBlank(filterNorm)) {
            return logs;
        }

        return logs.stream()
                .filter(log -> matchesFilter(log, filterNorm))
                .toList();
    }

    private List<LogEntry> loadScopeLogs(Long importId, String sessionId, String uuid, String filterCode) {
        List<LogEntry> sessionLogs = notBlank(sessionId)
                ? logEntryRepository.findByLogImportIdAndSessionIdOrderByLogTimestampAsc(importId, sessionId)
                : List.of();

        List<LogEntry> scoped = narrowScope(sessionLogs, uuid, filterCode);
        if (scoped.size() >= MIN_SCOPE_LOGS) {
            return scoped;
        }

        List<LogEntry> importLogs = logEntryRepository.findByLogImportIdOrderByLogTimestampAscIdAsc(importId);
        List<LogEntry> importScoped = narrowScope(importLogs, uuid, filterCode);

        if (importScoped.size() > scoped.size()) {
            return importScoped;
        }
        return scoped.isEmpty() ? importScoped : scoped;
    }

    private List<LogEntry> narrowScope(List<LogEntry> logs, String uuid, String filterCode) {
        if (logs == null || logs.isEmpty()) {
            return List.of();
        }

        List<LogEntry> byFilter = filterByCode(logs, filterCode);
        List<LogEntry> byUuid = filterByUuid(logs, uuid);

        if (!byFilter.isEmpty() && !byUuid.isEmpty()) {
            List<LogEntry> intersection = intersectLogs(byFilter, byUuid);
            if (intersection.size() >= MIN_SCOPE_LOGS) {
                return intersection;
            }
            return byFilter.size() >= byUuid.size() ? byFilter : byUuid;
        }

        if (!byFilter.isEmpty()) {
            return byFilter;
        }

        if (!byUuid.isEmpty()) {
            return byUuid;
        }

        return logs;
    }

    private List<LogEntry> filterByUuid(List<LogEntry> logs, String uuid) {
        if (!notBlank(uuid)) {
            return List.of();
        }
        return logs.stream()
                .filter(log -> messageContainsUuid(combine(log), uuid))
                .toList();
    }

    private List<LogEntry> filterByCode(List<LogEntry> logs, String filterCode) {
        if (!notBlank(filterCode)) {
            return List.of();
        }
        String filterNorm = normalize(filterCode);
        return logs.stream()
                .filter(log -> matchesFilter(log, filterNorm))
                .toList();
    }

    private List<LogEntry> intersectLogs(List<LogEntry> a, List<LogEntry> b) {
        java.util.Set<Long> ids = a.stream()
                .map(LogEntry::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return b.stream()
                .filter(log -> log.getId() != null && ids.contains(log.getId()))
                .toList();
    }

    private boolean matchesFilter(LogEntry log, String filterNorm) {
        GraphExtraction ex = logPatternExtractor.extractGraph(log);
        String logFilter = coalesce(ex.getFilter(), extractFilter(combine(log)));
        if (notBlank(logFilter) && filterNorm.equals(normalize(logFilter))) {
            return true;
        }
        return combine(log).toUpperCase(Locale.ROOT).contains(filterNorm);
    }

    private static final String[] WINDOW_START_MARKERS = {
            "searchcomposantbyroot start execute",
            "begin executing request",
            "fetching filter",
            "start filtre",
            "start ws_",
            "running rules",
            "start dosearch",
            "start process"
    };

    private static final String[] WINDOW_END_MARKERS = {
            "global searchcomposantbyroot",
            "searchcomposantbyroot end execute",
            "end partitional searchcomposantbyroot",
            "rendering result",
            "in host took",
            "end filtre",
            "persist operation",
            "transit task"
    };

    private List<LogEntry> extractAdaptiveWindow(List<LogEntry> scopeLogs, int anchorIndex) {
        if (scopeLogs.isEmpty()) {
            return List.of();
        }

        int anchor = anchorIndex >= 0 ? anchorIndex : indexOfSlowestLog(scopeLogs);

        int start = findWindowStart(scopeLogs, anchor);
        int end = findWindowEnd(scopeLogs, anchor);

        if (end < start) {
            end = Math.min(scopeLogs.size() - 1, anchor + 100);
        }

        return new ArrayList<>(scopeLogs.subList(start, end + 1));
    }

    private int findWindowStart(List<LogEntry> scopeLogs, int anchor) {
        int start = anchor;
        for (int i = anchor; i >= 0; i--) {
            if (matchesAnyMarker(combine(scopeLogs.get(i)), WINDOW_START_MARKERS)) {
                start = i;
            }
            if (anchor - i > 500) {
                break;
            }
        }

        for (int i = start; i >= Math.max(0, start - 40); i--) {
            String lower = combine(scopeLogs.get(i)).toLowerCase(Locale.ROOT);
            if (lower.contains("fetching filter") || lower.contains("begin executing request")) {
                start = i;
                break;
            }
        }
        return start;
    }

    private int findWindowEnd(List<LogEntry> scopeLogs, int anchor) {
        int end = anchor;
        for (int i = anchor; i < scopeLogs.size(); i++) {
            if (matchesAnyMarker(combine(scopeLogs.get(i)), WINDOW_END_MARKERS)) {
                end = i;
            }
            if (i - anchor > 500) {
                break;
            }
        }
        return end;
    }

    private boolean matchesAnyMarker(String text, String[] markers) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String marker : markers) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private int indexOfSlowestLog(List<LogEntry> logs) {
        int best = 0;
        long bestMs = -1;
        for (int i = 0; i < logs.size(); i++) {
            long ms = resolveDuration(logs.get(i));
            if (ms > bestMs) {
                bestMs = ms;
                best = i;
            }
        }
        return best;
    }

    private long resolveDuration(LogEntry log) {
        if (log.getDurationMs() != null && log.getDurationMs() > 0) {
            return log.getDurationMs();
        }
        Long parsed = logPatternExtractor.extractDurationMsFromText(
                log.getMessage(), log.getRawLog(), log.getProcessName());
        return parsed != null ? parsed : 0L;
    }

    private int indexOfLog(List<LogEntry> logs, Long id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < logs.size(); i++) {
            if (Objects.equals(logs.get(i).getId(), id)) {
                return i;
            }
        }
        return -1;
    }

    private boolean belongsToImport(LogEntry log, Long importId) {
        return log.getLogImport() != null && Objects.equals(log.getLogImport().getId(), importId);
    }

    private boolean messageContainsUuid(String text, String uuid) {
        if (!notBlank(uuid) || !notBlank(text)) {
            return false;
        }
        String escaped = Pattern.quote(uuid.trim());
        return Pattern.compile("uuid\\s*\\[\\s*" + escaped + "\\s*]", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find();
    }

    private String extractUuidFromText(String text) {
        var matcher = UUID_IN_MESSAGE.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extractFilter(String text) {
        var matcher = Pattern.compile("filter code\\s*\\[\\s*([^\\]]+?)\\s*]",
                Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String buildScopeDescription(String sessionId, String uuid, String filterCode) {
        List<String> parts = new ArrayList<>();
        if (notBlank(sessionId)) {
            parts.add("session " + sessionId);
        }
        if (notBlank(uuid)) {
            parts.add("uuid " + uuid);
        }
        if (notBlank(filterCode)) {
            parts.add("filtre " + filterCode);
        }
        return parts.isEmpty() ? "périmètre import complet" : String.join(" + ", parts);
    }

    private String combine(LogEntry log) {
        return safe(log.getMessage()) + " " + safe(log.getRawLog());
    }

    private String normalize(String value) {
        return value != null ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String coalesce(String... values) {
        for (String value : values) {
            if (notBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
