package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.LogEntry;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class LogEntrySpecifications {

    private LogEntrySpecifications() {
    }

    public static Specification<LogEntry> hasImportId(Long importId) {
        return (root, query, cb) ->
                importId == null ? null : cb.equal(root.get("logImport").get("id"), importId);
    }

    public static Specification<LogEntry> hasFileName(String fileName) {
        return (root, query, cb) -> {
            if (fileName == null || fileName.isBlank()) return null;
            return cb.like(
                    cb.lower(root.join("logImport", JoinType.LEFT).get("fileName")),
                    "%" + fileName.toLowerCase() + "%"
            );
        };
    }

    public static Specification<LogEntry> hasError(Boolean errorOnly) {
        return (root, query, cb) -> {
            if (errorOnly == null || !errorOnly) return null;
            return cb.isTrue(root.get("isError"));
        };
    }

    public static Specification<LogEntry> hasEventType(String eventType) {
        return (root, query, cb) -> {
            if (eventType == null || eventType.isBlank()) return null;
            return cb.equal(cb.upper(root.get("eventType")), eventType.trim().toUpperCase());
        };
    }

    public static Specification<LogEntry> hasProcessName(String processName) {
        return (root, query, cb) -> {
            if (processName == null || processName.isBlank()) return null;
            return cb.like(cb.lower(root.get("processName")), "%" + processName.toLowerCase() + "%");
        };
    }

    public static Specification<LogEntry> hasSessionId(String sessionId) {
        return (root, query, cb) -> {
            if (sessionId == null || sessionId.isBlank()) return null;
            return cb.equal(root.get("sessionId"), sessionId.trim());
        };
    }

    public static Specification<LogEntry> hasUuid(String uuid) {
        return (root, query, cb) -> {
            if (uuid == null || uuid.isBlank()) return null;

            return cb.or(
                    cb.equal(root.get("businessKey"), uuid.trim()),
                    cb.like(cb.lower(root.get("message")), "%" + uuid.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("rawLog")), "%" + uuid.toLowerCase() + "%")
            );
        };
    }
}