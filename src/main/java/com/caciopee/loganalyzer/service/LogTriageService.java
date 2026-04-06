package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.LogTriageHistoryResponse;
import com.caciopee.loganalyzer.dto.LogTriageStateResponse;
import com.caciopee.loganalyzer.entity.LogTriageHistory;
import com.caciopee.loganalyzer.entity.LogTriageState;
import com.caciopee.loganalyzer.repository.LogTriageHistoryRepository;
import com.caciopee.loganalyzer.repository.LogTriageStateRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LogTriageService {

    private final LogTriageStateRepository stateRepository;
    private final LogTriageHistoryRepository historyRepository;
    private final AuditService auditService;

    public LogTriageService(LogTriageStateRepository stateRepository,
                            LogTriageHistoryRepository historyRepository,
                            AuditService auditService) {
        this.stateRepository = stateRepository;
        this.historyRepository = historyRepository;
        this.auditService = auditService;
    }

    public LogTriageState getOrCreateStateEntity(Long logId) {
        return stateRepository.findByLogId(logId)
                .orElseGet(() -> {
                    LogTriageState state = new LogTriageState();
                    state.setLogId(logId);
                    state.setStatus("OPEN");
                    state.setImportant(false);
                    return stateRepository.save(state);
                });
    }

    public LogTriageStateResponse getOrCreateState(Long logId) {
        return mapState(getOrCreateStateEntity(logId));
    }

    public LogTriageStateResponse startProgress(Long logId, String comment, String actionBy) {
        return changeStatus(logId, "IN_PROGRESS", "START_PROGRESS", comment, actionBy);
    }

    public LogTriageStateResponse resolve(Long logId, String comment, String actionBy) {
        return changeStatus(logId, "RESOLVED", "RESOLVE", comment, actionBy);
    }

    public LogTriageStateResponse ignore(Long logId, String comment, String actionBy) {
        if (!hasText(comment)) {
            throw new IllegalArgumentException("Ignore requires a comment.");
        }
        return changeStatus(logId, "IGNORED", "IGNORE", comment, actionBy);
    }

    public LogTriageStateResponse reopen(Long logId, String comment, String actionBy) {
        if (!hasText(comment)) {
            throw new IllegalArgumentException("Reopen requires a comment.");
        }
        return changeStatus(logId, "OPEN", "REOPEN", comment, actionBy);
    }

    public LogTriageStateResponse markImportant(Long logId, String actionBy) {
        LogTriageState state = getOrCreateStateEntity(logId);
        LogTriageState snapshot = cloneState(state);

        state.setImportant(true);
        state.setLastActionBy(actionBy);
        state.setLastActionAt(LocalDateTime.now());

        LogTriageState saved = stateRepository.save(state);
        saveHistory(snapshot, saved, "MARK_IMPORTANT", null, actionBy);

        auditService.log(
                "MARK_IMPORTANT_LOG",
                "LOG",
                logId,
                actionBy,
                "Log marked as important"
        );

        return mapState(saved);
    }

    public LogTriageStateResponse unmarkImportant(Long logId, String actionBy) {
        LogTriageState state = getOrCreateStateEntity(logId);
        LogTriageState snapshot = cloneState(state);

        state.setImportant(false);
        state.setLastActionBy(actionBy);
        state.setLastActionAt(LocalDateTime.now());

        LogTriageState saved = stateRepository.save(state);
        saveHistory(snapshot, saved, "UNMARK_IMPORTANT", null, actionBy);

        auditService.log(
                "UNMARK_IMPORTANT_LOG",
                "LOG",
                logId,
                actionBy,
                "Important mark removed"
        );

        return mapState(saved);
    }

    public LogTriageStateResponse assign(Long logId, String assignedTo, String comment, String actionBy) {
        LogTriageState state = getOrCreateStateEntity(logId);
        LogTriageState snapshot = cloneState(state);

        state.setAssignedTo(assignedTo);
        state.setLastComment(comment);
        state.setLastActionBy(actionBy);
        state.setLastActionAt(LocalDateTime.now());

        LogTriageState saved = stateRepository.save(state);
        saveHistory(snapshot, saved, "ASSIGN", comment, actionBy);

        auditService.log(
                "ASSIGN_LOG",
                "LOG",
                logId,
                actionBy,
                "Assigned to: " + safe(assignedTo)
        );

        return mapState(saved);
    }

    public LogTriageStateResponse unassign(Long logId, String comment, String actionBy) {
        LogTriageState state = getOrCreateStateEntity(logId);
        LogTriageState snapshot = cloneState(state);

        state.setAssignedTo(null);
        state.setLastComment(comment);
        state.setLastActionBy(actionBy);
        state.setLastActionAt(LocalDateTime.now());

        LogTriageState saved = stateRepository.save(state);
        saveHistory(snapshot, saved, "UNASSIGN", comment, actionBy);

        auditService.log(
                "UNASSIGN_LOG",
                "LOG",
                logId,
                actionBy,
                "Log unassigned"
        );

        return mapState(saved);
    }

    public LogTriageStateResponse addComment(Long logId, String comment, String actionBy) {
        LogTriageState state = getOrCreateStateEntity(logId);
        LogTriageState snapshot = cloneState(state);

        state.setLastComment(comment);
        state.setLastActionBy(actionBy);
        state.setLastActionAt(LocalDateTime.now());

        LogTriageState saved = stateRepository.save(state);
        saveHistory(snapshot, saved, "COMMENT", comment, actionBy);

        auditService.log(
                "COMMENT_LOG",
                "LOG",
                logId,
                actionBy,
                comment
        );

        return mapState(saved);
    }

    public List<LogTriageHistoryResponse> getHistory(Long logId) {
        return historyRepository.findByLogIdOrderByActionAtDesc(logId).stream()
                .map(this::mapHistory)
                .toList();
    }

    private LogTriageStateResponse changeStatus(Long logId,
                                                String newStatus,
                                                String actionType,
                                                String comment,
                                                String actionBy) {
        LogTriageState state = getOrCreateStateEntity(logId);
        LogTriageState snapshot = cloneState(state);

        state.setStatus(newStatus);
        state.setLastComment(comment);
        state.setLastActionBy(actionBy);
        state.setLastActionAt(LocalDateTime.now());

        LogTriageState saved = stateRepository.save(state);
        saveHistory(snapshot, saved, actionType, comment, actionBy);

        auditService.log(
                actionType,
                "LOG",
                logId,
                actionBy,
                hasText(comment) ? comment : ("Status changed to " + newStatus)
        );

        return mapState(saved);
    }

    private void saveHistory(LogTriageState oldState,
                             LogTriageState newState,
                             String actionType,
                             String comment,
                             String actionBy) {
        LogTriageHistory history = new LogTriageHistory();
        history.setLogId(newState.getLogId());
        history.setActionType(actionType);

        if (oldState != null) {
            history.setOldStatus(oldState.getStatus());
            history.setOldImportant(oldState.getImportant());
            history.setOldAssignedTo(oldState.getAssignedTo());
        }

        history.setNewStatus(newState.getStatus());
        history.setNewImportant(newState.getImportant());
        history.setNewAssignedTo(newState.getAssignedTo());
        history.setComment(comment);
        history.setActionBy(actionBy);

        historyRepository.save(history);
    }

    private LogTriageState cloneState(LogTriageState source) {
        LogTriageState clone = new LogTriageState();
        clone.setLogId(source.getLogId());
        clone.setStatus(source.getStatus());
        clone.setImportant(source.getImportant());
        clone.setAssignedTo(source.getAssignedTo());
        clone.setLastComment(source.getLastComment());
        clone.setLastActionBy(source.getLastActionBy());
        clone.setLastActionAt(source.getLastActionAt());
        return clone;
    }

    private LogTriageStateResponse mapState(LogTriageState state) {
        LogTriageStateResponse response = new LogTriageStateResponse();
        response.setLogId(state.getLogId());
        response.setStatus(state.getStatus());
        response.setImportant(state.getImportant());
        response.setAssignedTo(state.getAssignedTo());
        response.setLastComment(state.getLastComment());
        response.setLastActionBy(state.getLastActionBy());
        response.setLastActionAt(state.getLastActionAt());
        return response;
    }

    private LogTriageHistoryResponse mapHistory(LogTriageHistory history) {
        LogTriageHistoryResponse response = new LogTriageHistoryResponse();
        response.setActionType(history.getActionType());
        response.setOldStatus(history.getOldStatus());
        response.setNewStatus(history.getNewStatus());
        response.setOldImportant(history.getOldImportant());
        response.setNewImportant(history.getNewImportant());
        response.setOldAssignedTo(history.getOldAssignedTo());
        response.setNewAssignedTo(history.getNewAssignedTo());
        response.setComment(history.getComment());
        response.setActionBy(history.getActionBy());
        response.setActionAt(history.getActionAt());
        return response;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}