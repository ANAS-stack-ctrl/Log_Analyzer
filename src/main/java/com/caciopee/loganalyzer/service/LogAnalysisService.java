package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.*;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.entity.LogEventType;
import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LogAnalysisService {

    private final LogEntryRepository logEntryRepository;
    private final LogImportRepository logImportRepository;
    private final LogBusinessExplanationService logBusinessExplanationService;

    public LogAnalysisService(LogEntryRepository logEntryRepository,
                              LogImportRepository logImportRepository,
                              LogBusinessExplanationService logBusinessExplanationService) {
        this.logEntryRepository = logEntryRepository;
        this.logImportRepository = logImportRepository;
        this.logBusinessExplanationService = logBusinessExplanationService;
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
            String key = buildIncidentKey(log);
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
                .comparing((IncidentCandidateDto i) -> severityRank(i.getSeverity()))
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

        boolean hasJdbcFailure = containsAny(group,
                "GENERICJDBCEXCEPTION",
                "COULD NOT EXECUTE QUERY",
                "COMPLETED WITH ERROR",
                "DOSEARCH.CATCH");

        boolean hasRuleNotFound = containsAny(group,
                "NO RULE FOUND FOR THIS PARAMS",
                "AUCUNE RÈGLE N'A ÉTÉ AFFECTÉ",
                "AUCUNE RÃ¨GLE N'A Ã©TÃ© AFFECT");

        boolean hasNoResult = containsAny(group, "0 ROW");
        boolean hasTrigger = containsAny(group, "WAS FIRED", "IS COMPLETE");
        long maxDuration = extractMaxDuration(group);
        boolean hasErrorLevel = group.stream().anyMatch(this::isError);

        if (!hasJdbcFailure && !hasRuleNotFound && !hasNoResult && !hasTrigger && maxDuration <= 2000 && !hasErrorLevel) {
            return null;
        }

        IncidentCandidateDto dto = new IncidentCandidateDto();
        dto.setImportId(importId);
        dto.setSessionId(firstNonBlank(group.stream().map(LogEntry::getSessionId).toList()));
        dto.setBusinessKey(firstNonBlank(group.stream().map(LogEntry::getBusinessKey).toList()));
        dto.setCorrelationId(firstNonBlank(group.stream().map(LogEntry::getUserCorrelationId).toList()));
        dto.setFirstTimestamp(group.get(0).getLogTimestamp());
        dto.setLastTimestamp(group.get(group.size() - 1).getLogTimestamp());

        if (hasJdbcFailure) {
            dto.setIncidentType("QUERY_EXECUTION_FAILURE");
            dto.setSeverity("HIGH");
            dto.setConfidence("HIGH");
            dto.setTitle("Échec d'exécution de requête");
            dto.setExplanation("Le groupe contient une signature forte d'échec Hibernate/JDBC.");
            dto.setProbableCause("Panne SQL/JDBC/Hibernate pendant l'exécution d'une recherche.");
        } else if (maxDuration > 3000) {
            dto.setIncidentType("HIGH_LATENCY");
            dto.setSeverity("MEDIUM");
            dto.setConfidence("HIGH");
            dto.setTitle("Latence élevée");
            dto.setExplanation("Une étape du groupe dépasse 3000 ms.");
            dto.setProbableCause("Requête complexe, volume de données, jointures multiples ou pipeline coûteux.");
        } else if (maxDuration > 2000) {
            dto.setIncidentType("PERFORMANCE_WARNING");
            dto.setSeverity("LOW");
            dto.setConfidence("MEDIUM");
            dto.setTitle("Exécution lente");
            dto.setExplanation("Une étape du groupe dépasse 2000 ms.");
            dto.setProbableCause("Volume, complexité SQL ou enrichissement de données.");
        } else if (hasNoResult && hasRuleNotFound) {
            dto.setIncidentType("NO_RESULT_AND_RULE_NOT_FOUND");
            dto.setSeverity("LOW");
            dto.setConfidence("MEDIUM");
            dto.setTitle("Aucun résultat et aucune règle applicable");
            dto.setExplanation("Le groupe ne montre pas de panne système, mais un cas métier sans résultat et sans règle.");
            dto.setProbableCause("Filtres trop restrictifs ou absence de règle métier configurée.");
        } else if (hasNoResult) {
            dto.setIncidentType("NO_RESULT_FOUND");
            dto.setSeverity("LOW");
            dto.setConfidence("HIGH");
            dto.setTitle("Aucun résultat");
            dto.setExplanation("La requête s'est exécutée mais n'a retourné aucune donnée.");
            dto.setProbableCause("Critères métier trop restrictifs.");
        } else if (hasRuleNotFound) {
            dto.setIncidentType("RULE_NOT_FOUND");
            dto.setSeverity("LOW");
            dto.setConfidence("HIGH");
            dto.setTitle("Aucune règle applicable trouvée");
            dto.setExplanation("Le groupe signale l'absence de règle métier pour cette transition.");
            dto.setProbableCause("Aucune règle configurée pour ce contexte.");
        } else if (hasTrigger) {
            dto.setIncidentType("SCHEDULER_ACTIVITY");
            dto.setSeverity("LOW");
            dto.setConfidence("HIGH");
            dto.setTitle("Activité scheduler");
            dto.setExplanation("Détection d'un déclenchement scheduler normal.");
            dto.setProbableCause("Exécution planifiée standard.");
        } else {
            dto.setIncidentType("GENERIC_SIGNAL");
            dto.setSeverity("LOW");
            dto.setConfidence("LOW");
            dto.setTitle("Signal générique");
            dto.setExplanation("Le groupe contient des signaux analytiques mais sans diagnostic fort.");
            dto.setProbableCause("À confirmer avec plus de contexte.");
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

    private String buildIncidentKey(LogEntry log) {
        if (notBlank(log.getSessionId())) {
            return "SESSION:" + log.getSessionId();
        }
        if (notBlank(log.getBusinessKey())) {
            return "BK:" + log.getBusinessKey();
        }
        if (notBlank(log.getUserCorrelationId())) {
            return "CORR:" + log.getUserCorrelationId();
        }
        return "FALLBACK:" + trim(log.getEventType()) + ":" + trim(log.getProcessName());
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
            String msg = safe(log.getMessage()).toUpperCase();
            long duration = extractDurationMs(msg);
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