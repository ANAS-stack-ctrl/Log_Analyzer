package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.ErrorClusterResponse;
import com.caciopee.loganalyzer.dto.LogListItemResponse;
import com.caciopee.loganalyzer.dto.PagedResponse;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.entity.LogTriageState;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogTriageStateRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LogEntryService {

    private final LogEntryRepository logEntryRepository;
    private final LogTriageStateRepository logTriageStateRepository;
    private final LogTriageService logTriageService;

    public LogEntryService(LogEntryRepository logEntryRepository,
                           LogTriageStateRepository logTriageStateRepository,
                           LogTriageService logTriageService) {
        this.logEntryRepository = logEntryRepository;
        this.logTriageStateRepository = logTriageStateRepository;
        this.logTriageService = logTriageService;
    }

    public LogEntry saveLog(LogEntry logEntry) {
        return logEntryRepository.save(logEntry);
    }

    public long countLogs() {
        return logEntryRepository.count();
    }

    public long countByLevel(String level) {
        return logEntryRepository.countByLevelIgnoreCase(level);
    }

    public void deleteAllLogs() {
        logEntryRepository.deleteAll();
    }

    public PagedResponse<LogListItemResponse> getLogs(String level,
                                                      String eventType,
                                                      String businessKey,
                                                      String fieldName,
                                                      String correlationId,
                                                      String executionId,
                                                      Boolean error,
                                                      Long importId,
                                                      String fileName,
                                                      String triageStatus,
                                                      Boolean important,
                                                      Boolean onlyActive,
                                                      String assignedTo,
                                                      int page,
                                                      int size) {

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 50 : Math.min(size, 500);

        List<LogListItemResponse> filtered = logEntryRepository.findAll().stream()
                .filter(log -> !hasText(level) || equalsIgnoreCase(log.getLevel(), level))
                .filter(log -> !hasText(eventType) || equalsIgnoreCase(log.getEventType(), eventType))
                .filter(log -> !hasText(businessKey)
                        || equalsText(log.getBusinessKey(), businessKey)
                        || equalsText(log.getErrorBusinessKey(), businessKey))
                .filter(log -> !hasText(fieldName)
                        || equalsIgnoreCase(log.getFieldName(), fieldName)
                        || equalsIgnoreCase(log.getErrorAttribute(), fieldName))
                .filter(log -> !hasText(correlationId) || equalsText(log.getCorrelationId(), correlationId))
                .filter(log -> !hasText(executionId) || equalsText(log.getExecutionId(), executionId))
                .filter(log -> error == null || error.equals(Boolean.TRUE.equals(log.getError())))
                .filter(log -> importId == null || (log.getLogImport() != null && importId.equals(log.getLogImport().getId())))
                .filter(log -> !hasText(fileName)
                        || (log.getLogImport() != null && equalsIgnoreCase(log.getLogImport().getOriginalFileName(), fileName)))
                .map(this::enrichWithTriage)
                .filter(item -> !hasText(triageStatus) || equalsIgnoreCase(item.getTriageStatus(), triageStatus))
                .filter(item -> important == null || important.equals(Boolean.TRUE.equals(item.getImportant())))
                .filter(item -> !hasText(assignedTo) || equalsIgnoreCase(item.getAssignedTo(), assignedTo))
                .filter(item -> onlyActive == null || !onlyActive || isActiveStatus(item.getTriageStatus()))
                .sorted(Comparator
                        .comparing(LogListItemResponse::getLogTimestamp, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(LogListItemResponse::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        long totalElements = filtered.size();
        int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / safeSize);

        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());

        List<LogListItemResponse> content = filtered.subList(fromIndex, toIndex);

        PagedResponse<LogListItemResponse> response = new PagedResponse<>();
        response.setContent(content);
        response.setPage(safePage);
        response.setSize(safeSize);
        response.setTotalElements(totalElements);
        response.setTotalPages(totalPages);
        response.setFirst(safePage == 0);
        response.setLast(safePage >= totalPages - 1);

        return response;
    }

    public List<ErrorClusterResponse> getErrorClusters(Boolean onlyActive, int limit) {
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 100);

        Map<String, List<LogListItemResponse>> grouped = logEntryRepository.findAll().stream()
                .filter(log -> Boolean.TRUE.equals(log.getError()))
                .map(this::enrichWithTriage)
                .filter(item -> onlyActive == null || !onlyActive || isActiveStatus(item.getTriageStatus()))
                .collect(Collectors.groupingBy(
                        this::buildClusterKey,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return grouped.entrySet().stream()
                .map(entry -> buildCluster(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparing(ErrorClusterResponse::getTotalLogs, Comparator.reverseOrder())
                        .thenComparing(ErrorClusterResponse::getOpenLogs, Comparator.reverseOrder()))
                .limit(safeLimit)
                .toList();
    }

    private ErrorClusterResponse buildCluster(String clusterKey, List<LogListItemResponse> items) {
        LogListItemResponse first = items.get(0);

        ErrorClusterResponse cluster = new ErrorClusterResponse();
        cluster.setClusterKey(clusterKey);
        cluster.setEventType(first.getEventType());
        cluster.setFieldName(first.getFieldName());
        cluster.setSampleMessage(first.getMessage());
        cluster.setTotalLogs(items.size());
        cluster.setOpenLogs(items.stream().filter(item -> isActiveStatus(item.getTriageStatus())).count());
        cluster.setSeverity(computeClusterSeverity(items.size(), cluster.getOpenLogs()));

        cluster.setImportIds(items.stream()
                .map(LogListItemResponse::getImportId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(10)
                .toList());

        cluster.setBusinessKeys(items.stream()
                .map(item -> firstNonEmpty(item.getBusinessKey(), item.getErrorBusinessKey()))
                .filter(this::hasText)
                .distinct()
                .limit(10)
                .toList());

        return cluster;
    }

    private String buildClusterKey(LogListItemResponse item) {
        String normalizedMessage = normalizeMessage(item.getMessage());
        return safe(item.getEventType()) + "|" + safe(item.getFieldName()) + "|" + normalizedMessage;
    }

    private String normalizeMessage(String message) {
        if (!hasText(message)) {
            return "NO_MESSAGE";
        }

        String normalized = message
                .replaceAll("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?", "<ts>")
                .replaceAll("\\b\\d+\\b", "<n>")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.length() > 120) {
            return normalized.substring(0, 120);
        }

        return normalized;
    }

    private String computeClusterSeverity(long totalLogs, long openLogs) {
        if (openLogs >= 20 || totalLogs >= 30) return "CRITICAL";
        if (openLogs >= 10 || totalLogs >= 15) return "HIGH";
        if (openLogs >= 3 || totalLogs >= 5) return "MEDIUM";
        return "LOW";
    }

    private LogListItemResponse enrichWithTriage(LogEntry log) {
        LogTriageState state = logTriageStateRepository.findByLogId(log.getId())
                .orElseGet(() -> logTriageService.getOrCreateStateEntity(log.getId()));

        LogListItemResponse response = new LogListItemResponse();
        response.setId(log.getId());
        response.setImportId(log.getLogImport() != null ? log.getLogImport().getId() : null);
        response.setOriginalFileName(log.getLogImport() != null ? log.getLogImport().getOriginalFileName() : null);
        response.setLogTimestamp(log.getLogTimestamp());
        response.setLevel(log.getLevel());
        response.setEventType(log.getEventType());
        response.setFieldName(firstNonEmpty(log.getFieldName(), log.getErrorAttribute()));
        response.setBusinessKey(log.getBusinessKey());
        response.setErrorBusinessKey(log.getErrorBusinessKey());
        response.setMessage(log.getMessage());

        response.setTriageStatus(state.getStatus());
        response.setImportant(state.getImportant());
        response.setAssignedTo(state.getAssignedTo());
        response.setLastComment(state.getLastComment());
        response.setLastActionBy(state.getLastActionBy());
        response.setLastActionAt(state.getLastActionAt());

        return response;
    }

    private boolean isActiveStatus(String status) {
        return "OPEN".equalsIgnoreCase(safe(status))
                || "IN_PROGRESS".equalsIgnoreCase(safe(status));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return safe(a).equalsIgnoreCase(safe(b));
    }

    private boolean equalsText(String a, String b) {
        return safe(a).equals(safe(b));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonEmpty(String a, String b) {
        if (hasText(a)) return a;
        if (hasText(b)) return b;
        return "";
    }
}