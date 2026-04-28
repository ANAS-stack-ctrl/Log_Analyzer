package com.caciopee.loganalyzer.analysis;

import com.caciopee.loganalyzer.analysis.dto.WorkflowAnalysisDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowAnalysisResponseDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowDiagnosticDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowStepDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowSummaryDto;
import com.caciopee.loganalyzer.analysis.model.ExtractedLogContext;
import com.caciopee.loganalyzer.analysis.model.WorkflowSegment;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class WorkflowAnalyzerServiceImpl implements WorkflowAnalyzerService {

    private final LogEntryRepository logEntryRepository;
    private final LogPatternExtractor logPatternExtractor;
    private final WorkflowSegmentationService workflowSegmentationService;

    public WorkflowAnalyzerServiceImpl(LogEntryRepository logEntryRepository,
                                       LogPatternExtractor logPatternExtractor,
                                       WorkflowSegmentationService workflowSegmentationService) {
        this.logEntryRepository = logEntryRepository;
        this.logPatternExtractor = logPatternExtractor;
        this.workflowSegmentationService = workflowSegmentationService;
    }

    @Override
    public WorkflowAnalysisResponseDto analyzeImport(Long importId) {
        List<LogEntry> logs = logEntryRepository.findByLogImportIdOrderByLogTimestampAscIdAsc(importId);
        List<WorkflowSegment> segments = workflowSegmentationService.splitImportIntoSegments(logs);

        List<WorkflowAnalysisDto> workflows = segments.stream()
                .map(this::analyzeSegment)
                .sorted(Comparator
                        .comparing(WorkflowAnalysisDto::getSeverityScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WorkflowAnalysisDto::getStartedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                .collect(Collectors.toList());

        WorkflowAnalysisResponseDto response = new WorkflowAnalysisResponseDto();
        response.setImportId(importId);
        response.setWorkflows(workflows);
        response.setTotalWorkflows(workflows.size());
        response.setWorkflowsWithErrors((int) workflows.stream().filter(WorkflowAnalysisDto::isHasErrors).count());
        response.setWorkflowsWithWarnings((int) workflows.stream().filter(WorkflowAnalysisDto::isHasWarnings).count());
        response.setWorkflowsWithPerformanceIssues((int) workflows.stream().filter(WorkflowAnalysisDto::isHasPerformanceIssue).count());

        return response;
    }

    @Override
    public WorkflowSummaryDto summarizeImport(Long importId) {
        WorkflowAnalysisResponseDto response = analyzeImport(importId);

        WorkflowSummaryDto summary = new WorkflowSummaryDto();
        summary.setImportId(importId);
        summary.setTotalWorkflows(response.getTotalWorkflows());
        summary.setTotalErrors(response.getWorkflows().stream().mapToInt(w -> safeInt(w.getErrorCount())).sum());
        summary.setTotalWarnings(response.getWorkflows().stream().mapToInt(w -> safeInt(w.getWarningCount())).sum());
        summary.setPerformanceIssues((int) response.getWorkflows().stream().filter(WorkflowAnalysisDto::isHasPerformanceIssue).count());
        summary.setNoRuleIssues((int) response.getWorkflows().stream().filter(WorkflowAnalysisDto::isHasNoRuleIssue).count());
        summary.setZeroRowIssues((int) response.getWorkflows().stream().filter(WorkflowAnalysisDto::isHasZeroRowIssue).count());
        summary.setMaxMemoryObserved(response.getWorkflows().stream()
                .map(WorkflowAnalysisDto::getMaxMemoryMo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null));
        summary.setMaxDurationObserved(response.getWorkflows().stream()
                .map(WorkflowAnalysisDto::getDurationMs)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(null));
        summary.setTopProblemWorkflowKeys(
                response.getWorkflows().stream()
                        .sorted(Comparator.comparing(WorkflowAnalysisDto::getSeverityScore, Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(5)
                        .map(WorkflowAnalysisDto::getWorkflowKey)
                        .collect(Collectors.toList())
        );

        return summary;
    }

    private WorkflowAnalysisDto analyzeSegment(WorkflowSegment segment) {
        List<LogEntry> logs = segment.getLogs();

        WorkflowAnalysisDto dto = new WorkflowAnalysisDto();
        dto.setWorkflowKey(segment.getSegmentKey());

        LogEntry first = logs.get(0);
        LogEntry last = logs.get(logs.size() - 1);

        dto.setCorrelationId(segment.getCorrelationId());
        dto.setSessionId(segment.getSessionId());
        dto.setExtractedUuid(segment.getUuid());
        dto.setDominantTransactionId(segment.getTransactionId());
        dto.setDominantFilterCode(segment.getFilterCode());
        dto.setDominantThreadName(mostFrequent(
                logs.stream()
                        .map(log -> logPatternExtractor.extract(log).getExtractedThreadName())
                        .collect(Collectors.toList())
        ));

        dto.setProcessName(defaultString(
                segment.getProcessName(),
                mostFrequent(logs.stream().map(LogEntry::getProcessName).collect(Collectors.toList()))
        ));
        dto.setDominantSourceClass(mostFrequent(logs.stream().map(LogEntry::getSourceClass).collect(Collectors.toList())));
        dto.setStartedAt(first.getLogTimestamp());
        dto.setEndedAt(last.getLogTimestamp());
        dto.setDurationMs(computeDuration(first.getLogTimestamp(), last.getLogTimestamp()));
        dto.setTotalLogs(logs.size());

        dto.setErrorCount((int) logs.stream().filter(this::isError).count());
        dto.setWarningCount((int) logs.stream().filter(this::isWarning).count());
        dto.setQueryCount((int) logs.stream().filter(this::isQueryStep).count());
        dto.setSaveCount((int) logs.stream().filter(this::isSaveStep).count());

        List<WorkflowStepDto> steps = logs.stream()
                .map(this::toStepDto)
                .collect(Collectors.toList());
        dto.setSteps(steps);

        dto.setMaxMemoryMo(steps.stream()
                .map(WorkflowStepDto::getExtractedMemoryMo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null));

        dto.setMaxRowCount(steps.stream()
                .map(WorkflowStepDto::getExtractedRowCount)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null));

        dto.setHasErrors(dto.getErrorCount() > 0);
        dto.setHasWarnings(dto.getWarningCount() > 0);
        dto.setHasNoRuleIssue(logs.stream().anyMatch(this::containsNoRuleFoundOrNoAssignedRule));
        dto.setHasZeroRowIssue(logs.stream().anyMatch(this::containsZeroRow));
        dto.setHasPerformanceIssue(hasPerformanceIssue(dto, logs));
        dto.setNature(detectNature(dto, logs));
        dto.setStatus(detectStatus(dto));
        dto.setSeverityScore(computeSeverityScore(dto));
        dto.setDiagnostics(buildDiagnostics(dto, logs));
        dto.setSummary(buildSummary(dto, logs));

        return dto;
    }

    private WorkflowStepDto toStepDto(LogEntry log) {
        ExtractedLogContext ctx = logPatternExtractor.extract(log);

        WorkflowStepDto dto = new WorkflowStepDto();
        dto.setLogEntryId(log.getId());
        dto.setTimestamp(log.getLogTimestamp());
        dto.setLevel(log.getLevel());
        dto.setProcessName(log.getProcessName());
        dto.setSourceClass(log.getSourceClass());
        dto.setMessage(log.getMessage());
        dto.setBusinessMeaning(log.getBusinessMeaning());

        dto.setExtractedUuid(ctx.getExtractedUuid());
        dto.setExtractedTransactionId(ctx.getExtractedTransactionId());
        dto.setExtractedFilterCode(ctx.getExtractedFilterCode());
        dto.setExtractedThreadName(ctx.getExtractedThreadName());
        dto.setExtractedClassName(ctx.getExtractedClassName());
        dto.setExtractedRowCount(ctx.getExtractedRowCount());
        dto.setExtractedDurationMs(ctx.getExtractedDurationMs());
        dto.setExtractedMemoryMo(ctx.getExtractedMemoryMo());

        dto.setEventType(log.getEventType());
        dto.setStepCode(log.getStepCode());
        dto.setFieldName(log.getFieldName());
        dto.setInterfaceField(log.getInterfaceField());
        dto.setFieldClassCode(log.getFieldClassCode());
        dto.setParsedType(log.getParsedType());
        dto.setParsedValue(log.getParsedValue());
        dto.setBusinessKey(log.getBusinessKey());
        dto.setRelationName(log.getRelationName());
        dto.setRelationKey(log.getRelationKey());
        dto.setMandatoryField(log.getMandatoryField());
        dto.setErrorColumn(log.getErrorColumn());
        dto.setErrorAttribute(log.getErrorAttribute());
        dto.setErrorValue(log.getErrorValue());
        dto.setErrorBusinessKey(log.getErrorBusinessKey());

        dto.setStepType(detectStepType(log, ctx));
        return dto;
    }

    private WorkflowStepType detectStepType(LogEntry log, ExtractedLogContext ctx) {
        String eventType = upper(log.getEventType());
        String msg = lower(log.getMessage());

        if ("WORKFLOW_LOOP_START".equals(eventType)) return WorkflowStepType.WORKFLOW_START;
        if ("WORKFLOW_NEW_INTERFACE_OBJECT".equals(eventType)) return WorkflowStepType.OBJECT_START;
        if ("WORKFLOW_STRUCTURE_DATA_CALL".equals(eventType) || "STRUCTURE_DATA_START".equals(eventType)) {
            return WorkflowStepType.STRUCTURE_START;
        }
        if ("STRUCTURE_DATA_END".equals(eventType)) return WorkflowStepType.STRUCTURE_END;

        if ("RULES_RUNNING".equals(eventType)) return WorkflowStepType.RULES_START;
        if ("PROCESS_CONTENT_SAVE".equals(eventType)) return WorkflowStepType.SAVE_END;

        if ("SQL_QUERY".equals(eventType)) return WorkflowStepType.QUERY_EXECUTION;
        if ("MEMORY_USAGE".equals(eventType)) return WorkflowStepType.MEMORY_CHECK;
        if ("CHECKPOINT_TRACE".equals(eventType)) return WorkflowStepType.CHECKPOINT;
        if ("NULL_CONTINUE".equals(eventType)) return WorkflowStepType.NULL_CONTINUE;

        if ("FIELD_MAPPING".equals(eventType)) return WorkflowStepType.FIELD_MAPPING;
        if ("FIELD_VALUE_READ".equals(eventType)) return WorkflowStepType.FIELD_VALUE_READ;
        if ("TYPE_DETECTED".equals(eventType) || "FIELD_CLASS_CODE".equals(eventType)) {
            return WorkflowStepType.FIELD_TYPE_DETECTED;
        }
        if ("TYPE_CONVERSION_START".equals(eventType)
                || "TYPE_CONVERSION_RESULT".equals(eventType)
                || "TYPE_CONVERSION_SUCCESS".equals(eventType)) {
            return WorkflowStepType.FIELD_TYPE_CONVERTED;
        }
        if ("FIELD_INSERTED".equals(eventType)) return WorkflowStepType.FIELD_INSERTED;

        if ("RELATION_DETECTED".equals(eventType)) return WorkflowStepType.RELATION_DETECTED;
        if ("RELATION_KEY_CREATED".equals(eventType)) return WorkflowStepType.RELATION_KEY_CREATED;
        if ("RELATION_ADDED".equals(eventType)
                || "RELATION_DATA_ATTACHED".equals(eventType)
                || "STRUCTURED_OBJECT_ASSEMBLED".equals(eventType)) {
            return WorkflowStepType.RELATION_ATTACHED;
        }

        if ("MANDATORY_CHECK_START".equals(eventType)) return WorkflowStepType.MANDATORY_CHECK_START;
        if ("MANDATORY_FIELDS_LIST".equals(eventType) || "MANDATORY_FIELD_CHECK".equals(eventType)) {
            return WorkflowStepType.MANDATORY_CHECK_ITEM;
        }
        if ("MANDATORY_CHECK_END".equals(eventType) || "MANDATORY_CHECK_SUCCESS".equals(eventType)) {
            return WorkflowStepType.MANDATORY_CHECK_END;
        }

        if ("ERROR_DETAILS".equals(eventType)
                || "PM_MAPPING_ERROR".equals(eventType)
                || "GENERIC_ERROR".equals(eventType)) {
            return WorkflowStepType.ERROR;
        }

        if ("RULE_NOT_FOUND".equals(eventType) || "TRIGGER_MISFIRED".equals(eventType)) {
            return WorkflowStepType.WARNING;
        }

        if (msg.contains("start fire rules")) return WorkflowStepType.RULES_START;
        if (msg.contains("end fire rules")) return WorkflowStepType.RULES_END;
        if (msg.contains("preparesearchbyroot")) return WorkflowStepType.QUERY_PREPARATION;
        if (msg.contains("using tempquery") || msg.contains("savetempcomposantsloaded")) {
            return WorkflowStepType.TEMP_TABLE_USAGE;
        }
        if (msg.contains("searchattributeslist") || msg.contains("loadlistchilds")) {
            return WorkflowStepType.DATA_LOADING;
        }
        if (ctx.getExtractedDurationMs() != null) return WorkflowStepType.PERFORMANCE_CHECK;
        if (isError(log)) return WorkflowStepType.ERROR;
        if (isWarning(log)) return WorkflowStepType.WARNING;

        return WorkflowStepType.UNKNOWN;
    }

    private WorkflowNature detectNature(WorkflowAnalysisDto dto, List<LogEntry> logs) {
        String proc = lower(dto.getProcessName());
        String filter = lower(dto.getDominantFilterCode());
        String messages = lower(logs.stream().map(LogEntry::getMessage).collect(Collectors.joining(" || ")));
        String eventTypes = upper(logs.stream()
                .map(LogEntry::getEventType)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" | ")));

        if (proc.contains("rfid") || messages.contains("rfid")) return WorkflowNature.RFID;
        if (messages.contains("douane") || messages.contains("dum") || messages.contains("mainlevee")) {
            return WorkflowNature.CUSTOMS;
        }
        if (filter.contains("bad") || messages.contains("ws_save_bad") || messages.contains("bad_reg")) {
            return WorkflowNature.BAD_MANAGEMENT;
        }
        if (messages.contains("tracabilite")
                || filter.contains("lastpoint_reg")
                || filter.contains("find_tracabilite_ws_reg")
                || eventTypes.contains("CHECKPOINT_TRACE")) {
            return WorkflowNature.TRACKING;
        }
        if (dto.getSaveCount() != null && dto.getSaveCount() > 0) return WorkflowNature.SAVE;
        if (dto.getQueryCount() != null && dto.getQueryCount() > 0) return WorkflowNature.SEARCH;
        if (filter.contains("reg") || filter.contains("scan")) return WorkflowNature.FILTER_CHAIN;

        return WorkflowNature.UNKNOWN;
    }

    private WorkflowStatus detectStatus(WorkflowAnalysisDto dto) {
        if (dto.isHasErrors()) return WorkflowStatus.ERROR;
        if (dto.isHasWarnings() || dto.isHasNoRuleIssue() || dto.isHasPerformanceIssue()) {
            return WorkflowStatus.WARNING;
        }
        if (dto.isHasZeroRowIssue()) return WorkflowStatus.PARTIAL;
        return WorkflowStatus.SUCCESS;
    }

    private int computeSeverityScore(WorkflowAnalysisDto dto) {
        int score = 0;

        score += safeInt(dto.getErrorCount()) * 20;
        score += safeInt(dto.getWarningCount()) * 5;

        if (dto.isHasNoRuleIssue()) score += 15;
        if (dto.isHasPerformanceIssue()) score += 15;
        if (dto.isHasZeroRowIssue()) score += 5;

        if (dto.getMaxMemoryMo() != null) {
            if (dto.getMaxMemoryMo() >= 5000) score += 20;
            else if (dto.getMaxMemoryMo() >= 3000) score += 10;
        }

        if (dto.getDurationMs() != null) {
            if (dto.getDurationMs() >= 10000) score += 15;
            else if (dto.getDurationMs() >= 5000) score += 8;
        }

        return score;
    }

    private List<WorkflowDiagnosticDto> buildDiagnostics(WorkflowAnalysisDto dto, List<LogEntry> logs) {
        List<WorkflowDiagnosticDto> diagnostics = new ArrayList<>();

        if (dto.isHasErrors()) {
            diagnostics.add(new WorkflowDiagnosticDto(
                    DiagnosticSeverity.CRITICAL,
                    "ERROR_PRESENT",
                    "Erreur détectée",
                    "Le segment contient des logs d'erreur techniques ou métier."
            ));
        }

        if (dto.isHasNoRuleIssue()) {
            diagnostics.add(new WorkflowDiagnosticDto(
                    DiagnosticSeverity.WARNING,
                    "NO_RULE_FOUND",
                    "Règle absente",
                    "Le segment contient une absence de règle applicable."
            ));
        }

        if (dto.isHasZeroRowIssue()) {
            diagnostics.add(new WorkflowDiagnosticDto(
                    DiagnosticSeverity.INFO,
                    "ZERO_ROW_FETCHED",
                    "Aucun résultat",
                    "Une recherche du segment a retourné 0 ligne."
            ));
        }

        if (dto.isHasPerformanceIssue()) {
            diagnostics.add(new WorkflowDiagnosticDto(
                    DiagnosticSeverity.WARNING,
                    "PERFORMANCE_ISSUE",
                    "Performance dégradée",
                    "Le segment montre une durée élevée, une requête lente ou une mémoire importante."
            ));
        }

        long mappingSteps = logs.stream().filter(this::isMappingStep).count();
        if (mappingSteps > 0) {
            diagnostics.add(new WorkflowDiagnosticDto(
                    DiagnosticSeverity.INFO,
                    "MAPPING_FLOW",
                    "Flux de mapping détecté",
                    "Le segment contient " + mappingSteps + " étape(s) de mapping / conversion / injection."
            ));
        }

        long relationSteps = logs.stream().filter(this::isRelationStep).count();
        if (relationSteps > 0) {
            diagnostics.add(new WorkflowDiagnosticDto(
                    DiagnosticSeverity.INFO,
                    "RELATION_FLOW",
                    "Construction de relations détectée",
                    "Le segment contient " + relationSteps + " étape(s) liées aux relations métier."
            ));
        }

        long mandatorySteps = logs.stream().filter(this::isMandatoryStep).count();
        if (mandatorySteps > 0) {
            diagnostics.add(new WorkflowDiagnosticDto(
                    DiagnosticSeverity.INFO,
                    "MANDATORY_FLOW",
                    "Contrôle de champs obligatoires détecté",
                    "Le segment contient " + mandatorySteps + " étape(s) de vérification obligatoire."
            ));
        }

        if (diagnostics.isEmpty()) {
            diagnostics.add(new WorkflowDiagnosticDto(
                    DiagnosticSeverity.INFO,
                    "OK",
                    "Segment sain",
                    "Aucune anomalie majeure détectée."
            ));
        }

        return diagnostics;
    }

    private String buildSummary(WorkflowAnalysisDto dto, List<LogEntry> logs) {
        long mappingSteps = logs.stream().filter(this::isMappingStep).count();
        long relationSteps = logs.stream().filter(this::isRelationStep).count();
        long mandatorySteps = logs.stream().filter(this::isMandatoryStep).count();

        return "Segment " + dto.getWorkflowKey()
                + " | nature=" + dto.getNature()
                + " | status=" + dto.getStatus()
                + " | logs=" + dto.getTotalLogs()
                + " | errors=" + dto.getErrorCount()
                + " | warnings=" + dto.getWarningCount()
                + " | queries=" + dto.getQueryCount()
                + " | saves=" + dto.getSaveCount()
                + " | mappingSteps=" + mappingSteps
                + " | relationSteps=" + relationSteps
                + " | mandatorySteps=" + mandatorySteps
                + " | durationMs=" + dto.getDurationMs()
                + " | maxMemoryMo=" + dto.getMaxMemoryMo();
    }

    private boolean hasPerformanceIssue(WorkflowAnalysisDto dto, List<LogEntry> logs) {
        if (dto.getDurationMs() != null && dto.getDurationMs() >= 5000) return true;
        if (dto.getMaxMemoryMo() != null && dto.getMaxMemoryMo() >= 3000) return true;

        return logs.stream().anyMatch(log -> {
            ExtractedLogContext ctx = logPatternExtractor.extract(log);
            return ctx.getExtractedDurationMs() != null && ctx.getExtractedDurationMs() >= 1000;
        });
    }

    private boolean isError(LogEntry log) {
        String eventType = upper(log.getEventType());
        return "ERROR".equalsIgnoreCase(defaultString(log.getLevel(), ""))
                || "ERROR_DETAILS".equals(eventType)
                || "PM_MAPPING_ERROR".equals(eventType)
                || "GENERIC_ERROR".equals(eventType);
    }

    private boolean isWarning(LogEntry log) {
        String eventType = upper(log.getEventType());
        String msg = lower(log.getMessage());
        return "WARN".equalsIgnoreCase(defaultString(log.getLevel(), ""))
                || msg.contains("warning")
                || "RULE_NOT_FOUND".equals(eventType)
                || "TRIGGER_MISFIRED".equals(eventType);
    }

    private boolean isQueryStep(LogEntry log) {
        String eventType = upper(log.getEventType());
        String msg = lower(log.getMessage());
        return "SQL_QUERY".equals(eventType)
                || msg.contains("query:")
                || msg.contains("preparesearchbyroot")
                || msg.contains("searchattributeslist")
                || msg.contains("searchcomposantbyroot");
    }

    private boolean isSaveStep(LogEntry log) {
        String eventType = upper(log.getEventType());
        String msg = lower(log.getMessage());
        String proc = lower(log.getProcessName());
        return "PROCESS_CONTENT_SAVE".equals(eventType)
                || msg.contains("saveorupdate")
                || msg.contains("saveprocesscontent")
                || msg.contains("start ws_insert")
                || proc.contains("-save");
    }

    private boolean containsNoRuleFoundOrNoAssignedRule(LogEntry log) {
        String msg = lower(log.getMessage());
        return "RULE_NOT_FOUND".equalsIgnoreCase(log.getEventType())
                || msg.contains("no rule found for this params")
                || msg.contains("aucune règle n'a été affectée");
    }

    private boolean containsZeroRow(LogEntry log) {
        String msg = lower(log.getMessage());
        return msg.contains("0 row fetched") || msg.contains("|0 row |");
    }

    private boolean isMappingStep(LogEntry log) {
        String eventType = upper(log.getEventType());
        return "FIELD_MAPPING".equals(eventType)
                || "FIELD_CLASS_CODE".equals(eventType)
                || "FIELD_VALUE_READ".equals(eventType)
                || "TYPE_DETECTED".equals(eventType)
                || "TYPE_CONVERSION_START".equals(eventType)
                || "TYPE_CONVERSION_RESULT".equals(eventType)
                || "TYPE_CONVERSION_SUCCESS".equals(eventType)
                || "FIELD_INSERTED".equals(eventType);
    }

    private boolean isRelationStep(LogEntry log) {
        String eventType = upper(log.getEventType());
        return "RELATION_DETECTED".equals(eventType)
                || "RELATION_ADDED".equals(eventType)
                || "RELATION_KEY_CREATED".equals(eventType)
                || "RELATION_DATA_ATTACHED".equals(eventType)
                || "STRUCTURED_OBJECT_ASSEMBLED".equals(eventType);
    }

    private boolean isMandatoryStep(LogEntry log) {
        String eventType = upper(log.getEventType());
        return "MANDATORY_CHECK_START".equals(eventType)
                || "MANDATORY_FIELDS_LIST".equals(eventType)
                || "MANDATORY_FIELD_CHECK".equals(eventType)
                || "MANDATORY_CHECK_END".equals(eventType)
                || "MANDATORY_CHECK_SUCCESS".equals(eventType);
    }

    private Long computeDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return null;
        return Duration.between(start, end).toMillis();
    }

    private String mostFrequent(List<String> values) {
        Map<String, Long> counts = values.stream()
                .filter(this::isNotBlank)
                .collect(Collectors.groupingBy(v -> v, LinkedHashMap::new, Collectors.counting()));

        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String lower(String value) {
        return defaultString(value, "").toLowerCase(Locale.ROOT);
    }

    private String upper(String value) {
        return defaultString(value, "").toUpperCase(Locale.ROOT);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}