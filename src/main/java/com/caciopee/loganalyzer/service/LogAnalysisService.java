package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.*;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.entity.LogEventType;
import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogImportRepository;
import com.caciopee.loganalyzer.util.LogDisplayTextUtil;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LogAnalysisService {

    private static final long SLOW_THRESHOLD_MS = 2000L;
    private static final Pattern FILTER_CODE_IN_MESSAGE = Pattern.compile(
            "filter code\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private final LogEntryRepository logEntryRepository;
    private final LogImportRepository logImportRepository;
    private final LogBusinessExplanationService logBusinessExplanationService;
    private final LogPatternExtractor logPatternExtractor;

    public LogAnalysisService(LogEntryRepository logEntryRepository,
                              LogImportRepository logImportRepository,
                              LogBusinessExplanationService logBusinessExplanationService,
                              LogPatternExtractor logPatternExtractor) {
        this.logEntryRepository = logEntryRepository;
        this.logImportRepository = logImportRepository;
        this.logBusinessExplanationService = logBusinessExplanationService;
        this.logPatternExtractor = logPatternExtractor;
    }

    public ImportAnalysisSummaryDto getImportSummary(Long importId) {
        LogImport logImport = logImportRepository.findById(importId)
                .orElseThrow(() -> new IllegalArgumentException("Import introuvable : " + importId));

        ImportAnalysisSummaryDto dto = new ImportAnalysisSummaryDto();
        dto.setImportId(logImport.getId());
        dto.setFileName(logImport.getFileName());
        dto.setOriginalFileName(logImport.getOriginalFileName());
        dto.setStatus(logImport.getStatus() != null ? logImport.getStatus().name() : null);
        dto.setStartedAt(logImport.getStartedAt());
        dto.setFinishedAt(logImport.getFinishedAt());
        dto.setTotalLines(nullSafeInt(logImport.getTotalLines()));
        dto.setParsedLines(nullSafeInt(logImport.getParsedLines()));
        dto.setFailedLines(nullSafeInt(logImport.getFailedLines()));
        dto.setErrorMessage(logImport.getErrorMessage());

        long totalLogs = logEntryRepository.countByLogImportId(importId);
        long totalErrors = logEntryRepository.countByLogImportIdAndIsErrorTrue(importId);
        long ambiguousCount = logEntryRepository.countByLogImportIdAndAmbiguousMessageTrue(importId);
        long incompleteCount = logEntryRepository.countByLogImportIdAndIncompleteLineTrue(importId);

        dto.setTotalLogs(totalLogs);
        dto.setTotalErrors(totalErrors);
        dto.setAmbiguousCount(ambiguousCount);
        dto.setIncompleteCount(incompleteCount);
        dto.setErrorRate(totalLogs == 0 ? 0.0 : round2((double) totalErrors * 100.0 / totalLogs));

        dto.setTopEventTypes(mapCountRows(logEntryRepository.countEventTypesByImport(importId), 10));
        dto.setTopSourceClasses(mapCountRows(logEntryRepository.countSourceClassesByImport(importId), 10));
        dto.setTopBusinessKeys(mapCountRows(logEntryRepository.countBusinessKeysByImport(importId), 10));
        dto.setTopErrorAttributes(mapCountRows(logEntryRepository.countErrorAttributesByImport(importId), 10));
        dto.setLevelDistribution(mapCountRows(logEntryRepository.countLevelsByImport(importId), 10));
        dto.setParseQualityDistribution(mapCountRows(logEntryRepository.countParseQualityByImport(importId), 10));

        Object[] timeRange = logEntryRepository.findTimeRangeByImport(importId);
        if (timeRange != null && timeRange.length == 2) {
            dto.setFirstLogTimestamp((LocalDateTime) timeRange[0]);
            dto.setLastLogTimestamp((LocalDateTime) timeRange[1]);
        }

        return dto;
    }

    public DashboardSummaryDto getDashboard() {
        DashboardSummaryDto dto = new DashboardSummaryDto();

        List<LogImport> imports = logImportRepository.findAllByOrderByStartedAtDesc();
        dto.setTotalImports(imports.size());

        long totalLogs = 0L;
        long totalErrors = 0L;

        Map<String, Long> statuses = new LinkedHashMap<>();

        for (LogImport logImport : imports) {
            totalLogs += logEntryRepository.countByLogImportId(logImport.getId());
            String status = logImport.getStatus() != null ? logImport.getStatus().name() : "UNKNOWN";
            statuses.put(status, statuses.getOrDefault(status, 0L) + 1L);
        }

        for (LogImport logImport : imports) {
            totalErrors += logEntryRepository.countByLogImportIdAndIsErrorTrue(logImport.getId());
        }

        dto.setTotalLogs(totalLogs);
        dto.setTotalErrors(totalErrors);
        dto.setTotalQueryExecutionFailures(logEntryRepository.countQueryExecutionFailuresGlobally());
        dto.setTotalRuleNotFoundSignals(logEntryRepository.countRuleNotFoundGlobally());

        dto.setRecentImportStatuses(statuses.entrySet().stream()
                .map(e -> new CountValueDto(e.getKey(), e.getValue()))
                .toList());

        return dto;
    }

    public ExecutionStoryDto getImportStory(Long importId) {
        List<LogEntry> logs = logEntryRepository.findByLogImportIdOrderByLogTimestampAscIdAsc(importId);
        if (logs.isEmpty()) {
            throw new IllegalArgumentException("Aucun log trouvé pour importId=" + importId);
        }
        return buildStory("IMPORT", importId, null, null, logs);
    }

    public ExecutionStoryDto getExecutionStory(String sessionId) {
        List<LogEntry> logs = logEntryRepository.findBySessionIdOrderByLogTimestampAsc(sessionId);
        if (logs.isEmpty()) {
            throw new IllegalArgumentException("Aucun log trouvé pour sessionId=" + sessionId);
        }
        return buildStory("SESSION", null, sessionId, null, logs);
    }

    public ExecutionStoryDto getBusinessKeyStory(String businessKey) {
        List<LogEntry> logs = logEntryRepository.findByBusinessKeyOrderByLogTimestampAsc(businessKey);
        if (logs.isEmpty()) {
            throw new IllegalArgumentException("Aucun log trouvé pour businessKey=" + businessKey);
        }
        return buildStory("BUSINESS_KEY", null, null, businessKey, logs);
    }

    public List<IncidentCandidateDto> detectImportIncidents(Long importId) {
        List<LogEntry> logs = logEntryRepository.findByLogImportIdOrderByLogTimestampAscIdAsc(importId);
        if (logs.isEmpty()) {
            return List.of();
        }

        Map<String, List<LogEntry>> grouped = new LinkedHashMap<>();
        for (LogEntry log : logs) {
            long duration = resolveLogDuration(log);
            if (duration <= SLOW_THRESHOLD_MS) {
                continue;
            }

            GraphExtraction extraction = logPatternExtractor.extractGraph(log);
            String process = coalesce(
                    extraction.getProcess(),
                    log.getProcessName(),
                    "INCONNU"
            );
            String filter = coalesce(
                    extraction.getFilter(),
                    extractFilterCodeFromMessageOrColumn(log)
            );
            String key = buildLatencyIncidentKey(log, process, filter);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(log);
        }

        List<IncidentCandidateDto> incidents = new ArrayList<>();
        for (List<LogEntry> group : grouped.values()) {
            IncidentCandidateDto incident = buildIncidentCandidate(importId, group);
            if (incident != null) {
                incidents.add(incident);
            }
        }

        incidents.sort(Comparator
                .comparing((IncidentCandidateDto i) -> i.getMaxDurationMs() != null ? i.getMaxDurationMs() : 0L,
                        Comparator.reverseOrder())
                .thenComparing((IncidentCandidateDto i) -> severityRank(i.getSeverity()))
                .thenComparing(IncidentCandidateDto::getFirstTimestamp, Comparator.nullsLast(Comparator.reverseOrder())));

        return incidents;
    }

    public List<LogEntryViewDto> getGenericExplanations(Long importId) {
        List<LogEntry> logs = logEntryRepository.findByLogImportIdOrderByLogTimestampAscIdAsc(importId);

        return logs.stream()
                .map(log -> {
                    String explanation = logBusinessExplanationService.explain(
                            toLogEventType(log.getEventType()),
                            log.getMessage(),
                            log.getProcessName(),
                            log.getSourceClass(),
                            log.getLevel(),
                            log.getBusinessMeaning()
                    );

                    if (!isGenericExplanation(explanation)) {
                        return null;
                    }

                    return toLogEntryViewDto(log, explanation);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private ExecutionStoryDto buildStory(String storyType,
                                         Long importId,
                                         String sessionId,
                                         String businessKey,
                                         List<LogEntry> logs) {
        ExecutionStoryDto dto = new ExecutionStoryDto();
        dto.setStoryType(storyType);
        dto.setImportId(importId);
        dto.setSessionId(sessionId);
        dto.setBusinessKey(businessKey);

        dto.setTotalLogs(logs.size());
        dto.setErrorCount(logs.stream().filter(this::isError).count());
        dto.setWarningCount(logs.stream()
                .filter(this::isWarning)
                .count());

        dto.setFirstTimestamp(logs.get(0).getLogTimestamp());
        dto.setLastTimestamp(logs.get(logs.size() - 1).getLogTimestamp());

        dto.setCorrelationId(firstNonBlank(logs.stream()
                .map(LogEntry::getUserCorrelationId)
                .toList()));

        dto.setTopEventTypes(topEventTypesFromLogs(logs, 10));
        dto.setSteps(logs.stream().limit(150).map(this::toStep).toList());

        fillRootCause(dto, logs);

        return dto;
    }

    private void fillRootCause(ExecutionStoryDto dto, List<LogEntry> logs) {
        boolean hasJdbcFailure = containsAny(logs,
                "GENERICJDBCEXCEPTION",
                "COULD NOT EXECUTE QUERY",
                "COMPLETED WITH ERROR",
                "DOSEARCH.CATCH",
                "LOADLISTCHILDS");

        boolean hasRuleNotFound = containsAny(logs,
                "NO RULE FOUND FOR THIS PARAMS",
                "AUCUNE RÈGLE N'A ÉTÉ AFFECTÉ",
                "AUCUNE RÃ¨GLE N'A Ã©TÃ© AFFECT");

        boolean hasNoResult = containsAny(logs, "0 ROW");
        boolean hasMisfire = containsAny(logs, "MISFIRED");
        boolean hasTrigger = containsAny(logs, "WAS FIRED", "IS COMPLETE");

        long maxDuration = extractMaxDuration(logs);

        if (hasJdbcFailure) {
            dto.setFinalStatus("FAILED");
            dto.setRootCauseCategory("QUERY_EXECUTION_FAILURE");
            dto.setRootCauseExplanation("Échec d'exécution de requête côté Hibernate/JDBC.");
            dto.setConfidence("HIGH");
            return;
        }

        if (maxDuration > 3000) {
            dto.setFinalStatus("WARNING");
            dto.setRootCauseCategory("HIGH_LATENCY");
            dto.setRootCauseExplanation("Latence élevée détectée sur une ou plusieurs étapes du workflow.");
            dto.setConfidence("HIGH");
            return;
        }

        if (maxDuration > 2000) {
            dto.setFinalStatus("WARNING");
            dto.setRootCauseCategory("PERFORMANCE_WARNING");
            dto.setRootCauseExplanation("Exécution lente détectée, probablement liée à une requête complexe ou à un volume de données important.");
            dto.setConfidence("MEDIUM");
            return;
        }

        if (hasMisfire) {
            dto.setFinalStatus("WARNING");
            dto.setRootCauseCategory("TRIGGER_MISFIRE");
            dto.setRootCauseExplanation("Le scheduler a raté ou retardé une exécution planifiée.");
            dto.setConfidence("MEDIUM");
            return;
        }

        if (hasTrigger) {
            dto.setFinalStatus("SUCCESS");
            dto.setRootCauseCategory("SCHEDULER_ACTIVITY");
            dto.setRootCauseExplanation("Activité normale du scheduler détectée.");
            dto.setConfidence("HIGH");
            return;
        }

        if (hasNoResult && hasRuleNotFound) {
            dto.setFinalStatus("SUCCESS_WITH_WARNING");
            dto.setRootCauseCategory("NO_RESULT_AND_RULE_NOT_FOUND");
            dto.setRootCauseExplanation("Aucun résultat trouvé et aucune règle applicable détectée, sans erreur système bloquante.");
            dto.setConfidence("MEDIUM");
            return;
        }

        if (hasNoResult) {
            dto.setFinalStatus("SUCCESS");
            dto.setRootCauseCategory("NO_RESULT_FOUND");
            dto.setRootCauseExplanation("La requête s'est exécutée correctement mais aucun enregistrement ne correspond aux critères.");
            dto.setConfidence("HIGH");
            return;
        }

        if (hasRuleNotFound) {
            dto.setFinalStatus("SUCCESS_WITH_WARNING");
            dto.setRootCauseCategory("RULE_NOT_FOUND_NON_BLOCKING");
            dto.setRootCauseExplanation("Aucune règle métier n'a été trouvée pour cette transition, sans impact bloquant visible.");
            dto.setConfidence("HIGH");
            return;
        }

        if (dto.getErrorCount() > 0) {
            dto.setFinalStatus("WARNING");
            dto.setRootCauseCategory("GENERIC_ERROR_SEQUENCE");
            dto.setRootCauseExplanation("Séquence contenant des erreurs sans signature forte de cause racine.");
            dto.setConfidence("LOW");
            return;
        }

        dto.setFinalStatus("SUCCESS");
        dto.setRootCauseCategory("NORMAL_WORKFLOW");
        dto.setRootCauseExplanation("Le workflow ressemble à un déroulement normal.");
        dto.setConfidence("HIGH");
    }

    private IncidentCandidateDto buildIncidentCandidate(Long importId, List<LogEntry> group) {
        if (group == null || group.isEmpty()) {
            return null;
        }

        long maxDuration = extractMaxDuration(group);
        if (maxDuration <= SLOW_THRESHOLD_MS) {
            return null;
        }

        GraphExtraction anchor = logPatternExtractor.extractGraph(
                group.stream().max(Comparator.comparingLong(this::resolveLogDuration)).orElse(group.get(0))
        );
        String process = LogDisplayTextUtil.sanitize(coalesce(
                anchor.getProcess(),
                group.get(0).getProcessName(),
                topProcessNameFromLogs(group)
        ));
        String filter = coalesce(
                anchor.getFilter(),
                extractFilterCodeFromMessageOrColumn(
                        group.stream().max(Comparator.comparingLong(this::resolveLogDuration)).orElse(group.get(0))
                ),
                topFilterCodeFromLogs(group)
        );

        IncidentCandidateDto dto = new IncidentCandidateDto();
        dto.setImportId(importId);
        dto.setSessionId(firstNonBlank(group.stream().map(LogEntry::getSessionId).toList()));
        dto.setBusinessKey(firstNonBlank(group.stream().map(LogEntry::getBusinessKey).toList()));
        dto.setCorrelationId(firstNonBlank(group.stream().map(LogEntry::getUserCorrelationId).toList()));
        dto.setUserName(firstNonBlank(group.stream().map(LogEntry::getUserName).toList()));
        dto.setProcessName(process);
        dto.setFilterCode(notBlank(filter) ? LogDisplayTextUtil.sanitize(filter) : null);
        dto.setLogCount(group.size());
        dto.setErrorCount((int) group.stream().filter(this::isError).count());
        dto.setMaxDurationMs(maxDuration > 0 ? maxDuration : null);
        dto.setFirstTimestamp(group.get(0).getLogTimestamp());
        dto.setLastTimestamp(group.get(group.size() - 1).getLogTimestamp());

        String scope = LogDisplayTextUtil.sanitize(buildLatencyScopeLabel(process, filter));
        if (maxDuration > 3000) {
            dto.setIncidentType("HIGH_LATENCY");
            dto.setSeverity("MEDIUM");
            dto.setConfidence("HIGH");
            dto.setTitle("Latence élevée — " + scope);
            dto.setExplanation("Une étape atteint " + maxDuration + " ms sur " + scope + ".");
            dto.setProbableCause("Requête complexe, volume de données, jointures multiples ou pipeline coûteux.");
        } else {
            dto.setIncidentType("PERFORMANCE_WARNING");
            dto.setSeverity("LOW");
            dto.setConfidence("MEDIUM");
            dto.setTitle("Exécution lente — " + scope);
            dto.setExplanation("Une étape atteint " + maxDuration + " ms sur " + scope + ".");
            dto.setProbableCause("Volume, complexité SQL ou enrichissement de données.");
        }

        dto.setEvidenceMessages(group.stream()
                .map(LogEntry::getMessage)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .limit(5)
                .toList());

        dto.setEvidenceLogIds(group.stream()
                .map(LogEntry::getId)
                .filter(Objects::nonNull)
                .limit(5)
                .toList());

        return dto;
    }

    private String buildLatencyIncidentKey(LogEntry log, String process, String filter) {
        String scope = notBlank(log.getSessionId())
                ? "SESSION:" + log.getSessionId()
                : notBlank(log.getBusinessKey())
                ? "BK:" + log.getBusinessKey()
                : notBlank(log.getUserCorrelationId())
                ? "CORR:" + log.getUserCorrelationId()
                : "LOG:" + (log.getId() != null ? log.getId() : UUID.randomUUID());
        return scope + "|PROC:" + normalizeKeyPart(process) + "|FILT:" + normalizeKeyPart(filter);
    }

    private String buildLatencyScopeLabel(String process, String filter) {
        if (notBlank(filter)) {
            return trim(process) + " / filtre " + trim(filter);
        }
        return trim(process);
    }

    private String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private long resolveLogDuration(LogEntry log) {
        if (log.getDurationMs() != null && log.getDurationMs() > 0) {
            return log.getDurationMs();
        }
        return extractDurationMs(safe(log.getMessage()).toUpperCase());
    }

    private String topFilterCodeFromLogs(List<LogEntry> logs) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LogEntry log : logs) {
            String fromGraph = cleanFilterCode(logPatternExtractor.extractGraph(log).getFilter());
            if (notBlank(fromGraph)) {
                counts.merge(fromGraph, 1L, Long::sum);
            }
            String fromRaw = cleanFilterCode(extractFilterCodeFromMessageOrColumn(log));
            if (notBlank(fromRaw)) {
                counts.merge(fromRaw, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String extractFilterCodeFromMessageOrColumn(LogEntry log) {
        if (log == null) {
            return null;
        }
        String message = safe(log.getMessage());
        Matcher matcher = FILTER_CODE_IN_MESSAGE.matcher(message);
        if (matcher.find()) {
            return cleanFilterCode(matcher.group(1));
        }
        String column = safe(log.getProcessName());
        if (column.toLowerCase(Locale.ROOT).startsWith("processfilter-")) {
            String[] parts = column.split("-");
            if (parts.length > 2) {
                String middle = parts[2].trim();
                if (notBlank(middle) && !middle.matches("\\d+")) {
                    return middle;
                }
            }
        }
        return null;
    }

    private String cleanFilterCode(String raw) {
        if (!notBlank(raw)) {
            return null;
        }
        String code = raw.trim();
        int pipe = code.indexOf('|');
        if (pipe > 0) {
            code = code.substring(0, pipe).trim();
        }
        return notBlank(code) ? code : null;
    }

    private StoryStepDto toStep(LogEntry entry) {
        StoryStepDto step = new StoryStepDto();
        step.setLogId(entry.getId());
        step.setTimestamp(entry.getLogTimestamp());
        step.setLevel(entry.getLevel());
        step.setSourceClass(entry.getSourceClass());
        step.setProcessName(entry.getProcessName());
        step.setEventType(entry.getEventType());
        step.setBusinessMeaning(entry.getBusinessMeaning());
        step.setMessage(entry.getMessage());
        step.setError(isError(entry));
        return step;
    }

    private List<CountValueDto> topEventTypesFromLogs(List<LogEntry> logs, int limit) {
        Map<String, Long> counts = logs.stream()
                .map(LogEntry::getEventType)
                .map(v -> v == null || v.isBlank() ? "UNKNOWN" : v)
                .collect(Collectors.groupingBy(v -> v, LinkedHashMap::new, Collectors.counting()));

        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> new CountValueDto(e.getKey(), e.getValue()))
                .toList();
    }

    private String topProcessNameFromLogs(List<LogEntry> logs) {
        return logs.stream()
                .map(LogEntry::getProcessName)
                .filter(this::notBlank)
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(firstNonBlank(logs.stream().map(LogEntry::getProcessName).toList()));
    }

    private List<CountValueDto> mapCountRows(List<Object[]> rows, int limit) {
        if (rows == null) {
            return List.of();
        }

        return rows.stream()
                .limit(limit)
                .map(row -> {
                    String value = row[0] == null ? "UNKNOWN" : String.valueOf(row[0]);
                    long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
                    return new CountValueDto(value, count);
                })
                .toList();
    }

    private boolean containsAny(List<LogEntry> logs, String... tokens) {
        for (LogEntry log : logs) {
            String combined = (
                    safe(log.getMessage()) + " " +
                            safe(log.getRawLog()) + " " +
                            safe(log.getBusinessMeaning())
            ).toUpperCase();

            for (String token : tokens) {
                if (combined.contains(token.toUpperCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private long extractMaxDuration(List<LogEntry> logs) {
        long max = -1L;
        for (LogEntry log : logs) {
            if (log.getDurationMs() != null && log.getDurationMs() > max) {
                max = log.getDurationMs();
            }
            long duration = extractDurationMs(safe(log.getMessage()).toUpperCase());
            if (duration > max) {
                max = duration;
            }
        }
        return max;
    }

    private long extractDurationMs(String message) {
        try {
            if (message == null) return -1L;

            int idx1 = message.indexOf("TOOK [");
            if (idx1 >= 0) {
                int start = idx1 + "TOOK [".length();
                int end = message.indexOf("]", start);
                if (end > start) {
                    return Long.parseLong(message.substring(start, end).trim());
                }
            }

            int idx2 = message.indexOf("TOOK ");
            int idxMs = message.indexOf("(MS)");
            if (idx2 >= 0 && idxMs > idx2) {
                String value = message.substring(idx2 + "TOOK ".length(), idxMs).trim();
                return Long.parseLong(value);
            }

            return -1L;
        } catch (Exception e) {
            return -1L;
        }
    }

    private boolean isError(LogEntry entry) {
        return Boolean.TRUE.equals(entry.getIsError()) || "ERROR".equalsIgnoreCase(trim(entry.getLevel()));
    }

    private boolean isWarning(LogEntry entry) {
        String level = trim(entry.getLevel());
        if ("WARN".equalsIgnoreCase(level) || "WARNING".equalsIgnoreCase(level)) {
            return true;
        }

        String combined = (safe(entry.getMessage()) + " " + safe(entry.getRawLog())).toUpperCase();
        return combined.contains("NO RULE FOUND FOR THIS PARAMS")
                || combined.contains("AUCUNE R")
                || combined.contains("0 ROW")
                || extractDurationMs(combined) > 2000;
    }

    private long severityRank(String severity) {
        if ("HIGH".equalsIgnoreCase(severity)) return 1;
        if ("MEDIUM".equalsIgnoreCase(severity)) return 2;
        return 3;
    }

    private String firstNonBlank(List<String> values) {
        for (String value : values) {
            if (notBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String coalesce(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (notBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isGenericExplanation(String explanation) {
        if (explanation == null || explanation.isBlank()) {
            return true;
        }

        String lower = explanation.toLowerCase();

        return lower.contains("message générique")
                || lower.contains("événement détecté dans les logs")
                || lower.contains("une étape technique ou métier a été mesurée")
                || lower.contains("le message doit être inspecté")
                || lower.contains("message vide ou non exploitable")
                || lower.contains("non exploitable");
    }

    private LogEntryViewDto toLogEntryViewDto(LogEntry log, String explanation) {
        LogEntryViewDto dto = new LogEntryViewDto();

        dto.setId(log.getId());
        dto.setImportId(log.getLogImport() != null ? log.getLogImport().getId() : null);
        dto.setLogTimestamp(log.getLogTimestamp());
        dto.setLevel(log.getLevel());
        dto.setProcessName(log.getProcessName());
        dto.setSourceClass(log.getSourceClass());
        dto.setMessage(log.getMessage());
        dto.setEventType(log.getEventType());
        dto.setBusinessMeaning(explanation);
        dto.setSessionId(log.getSessionId());
        dto.setCorrelationId(log.getUserCorrelationId());

        if (log.getLogImport() != null) {
            dto.setFileName(log.getLogImport().getFileName());
        }

        dto.setUuid(null);
        dto.setIsError(log.getIsError());

        return dto;
    }

    private LogEventType toLogEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }

        try {
            return LogEventType.valueOf(eventType.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private long nullSafeInt(Integer value) {
        return value == null ? 0L : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
    public List<LogEntryViewDto> getAllGenericExplanations(Long startId, Long endId) {
        List<LogEntry> logs = logEntryRepository.findByIdBetweenOrderByIdAsc(startId, endId);

        return logs.stream()
                .map(log -> {
                    String explanation = logBusinessExplanationService.explain(
                            toLogEventType(log.getEventType()),
                            log.getMessage(),
                            log.getProcessName(),
                            log.getSourceClass(),
                            log.getLevel(),
                            log.getBusinessMeaning()
                    );

                    if (!isGenericExplanation(explanation)) {
                        return null;
                    }

                    return toLogEntryViewDto(log, explanation);
                })
                .filter(Objects::nonNull)
                .toList();
    }
    public List<GenericPatternDto> getGroupedGenericExplanations(int pageSize, int maxPatterns) {
        Map<String, PatternAcc> grouped = new LinkedHashMap<>();

        int pageNumber = 0;
        Page<LogEntry> page;

        do {
            page = logEntryRepository.findAll(
                    PageRequest.of(pageNumber, pageSize, Sort.by("id").ascending())
            );

            for (LogEntry log : page.getContent()) {
                String explanation = logBusinessExplanationService.explain(
                        toLogEventType(log.getEventType()),
                        log.getMessage(),
                        log.getProcessName(),
                        log.getSourceClass(),
                        log.getLevel(),
                        log.getBusinessMeaning()
                );

                if (!isGenericExplanation(explanation)) {
                    continue;
                }

                String pattern = normalizePattern(log.getMessage());

                PatternAcc acc = grouped.computeIfAbsent(pattern, k ->
                        new PatternAcc(pattern, log.getMessage(), log.getLogImport() != null ? log.getLogImport().getId() : null)
                );

                acc.count++;
            }

            pageNumber++;

        } while (page.hasNext());

        return grouped.values().stream()
                .sorted((a, b) -> Long.compare(b.count, a.count))
                .limit(maxPatterns)
                .map(a -> new GenericPatternDto(a.pattern, a.count, a.example, a.importId))
                .toList();
    }

    private String normalizePattern(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        String normalized = message
                .replaceAll("\\d+", "X")
                .replaceAll("\\[.*?]", "[...]")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.length() > 180) {
            return normalized.substring(0, 180);
        }

        return normalized;
    }

    private static class PatternAcc {
        String pattern;
        long count;
        String example;
        Long importId;

        PatternAcc(String pattern, String example, Long importId) {
            this.pattern = pattern;
            this.example = example;
            this.importId = importId;
        }
    }
}