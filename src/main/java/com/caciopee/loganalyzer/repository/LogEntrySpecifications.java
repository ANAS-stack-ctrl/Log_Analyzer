package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.LogEntry;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class LogEntrySpecifications {

    private LogEntrySpecifications() {}

    public static Specification<LogEntry> hasImportId(Long importId) {
        return (root, query, cb) ->
                importId == null ? null : cb.equal(root.get("logImport").get("id"), importId);
    }

    public static Specification<LogEntry> hasImportIds(Collection<Long> importIds) {
        return (root, query, cb) -> {
            if (importIds == null || importIds.isEmpty()) return null;
            return root.get("logImport").get("id").in(importIds);
        };
    }

    public static Specification<LogEntry> hasFileName(String fileName) {
        return (root, query, cb) -> {
            if (fileName == null || fileName.isBlank()) return null;

            String pattern = "%" + fileName.trim().toLowerCase() + "%";

            return cb.or(
                    cb.like(cb.lower(root.join("logImport", JoinType.LEFT).get("fileName")), pattern),
                    cb.like(cb.lower(root.get("sourceFileName")), pattern),
                    cb.like(cb.lower(root.get("sourceRelativePath")), pattern)
            );
        };
    }

    public static Specification<LogEntry> hasFileNames(Collection<String> fileNames) {
        return (root, query, cb) -> {
            if (fileNames == null || fileNames.isEmpty()) return null;

            List<Predicate> predicates = new ArrayList<>();

            for (String fileName : fileNames) {
                if (fileName == null || fileName.isBlank()) continue;

                String pattern = "%" + fileName.trim().toLowerCase() + "%";

                predicates.add(cb.or(
                        cb.like(cb.lower(root.join("logImport", JoinType.LEFT).get("fileName")), pattern),
                        cb.like(cb.lower(root.get("sourceFileName")), pattern),
                        cb.like(cb.lower(root.get("sourceRelativePath")), pattern)
                ));
            }

            if (predicates.isEmpty()) return null;

            return cb.or(predicates.toArray(new Predicate[0]));
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
            return cb.like(cb.lower(root.get("processName")), "%" + processName.trim().toLowerCase() + "%");
        };
    }

    public static Specification<LogEntry> hasUserName(String userName) {
        return (root, query, cb) -> {
            if (userName == null || userName.isBlank()) return null;
            return cb.like(cb.lower(root.get("userName")), "%" + userName.trim().toLowerCase() + "%");
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

            String pattern = "%" + uuid.trim().toLowerCase() + "%";

            return cb.or(
                    cb.equal(root.get("businessKey"), uuid.trim()),
                    cb.like(cb.lower(root.get("message")), pattern),
                    cb.like(cb.lower(root.get("rawLog")), pattern)
            );
        };
    }

    public static Specification<LogEntry> messageContains(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) return null;

            String pattern = "%" + text.trim().toLowerCase() + "%";

            return cb.or(
                    cb.like(cb.lower(root.get("message")), pattern),
                    cb.like(cb.lower(root.get("rawLog")), pattern)
            );
        };
    }

    public static Specification<LogEntry> hasBusinessKey(String businessKey) {
        return (root, query, cb) -> {
            if (businessKey == null || businessKey.isBlank()) return null;
            return cb.equal(root.get("businessKey"), businessKey.trim());
        };
    }

    public static Specification<LogEntry> businessFieldContains(String fieldName, String fieldValue) {
        return (root, query, cb) -> {
            if (fieldName == null || fieldName.isBlank()) return null;

            String field = fieldName.trim().toLowerCase();
            List<Predicate> predicates = new ArrayList<>();

            if (fieldValue != null && !fieldValue.isBlank()) {
                String value = fieldValue.trim().toLowerCase();
                String loose = "%" + field + "%" + value + "%";
                String bracket = "%" + field + "%[%" + value + "%]%";
                String equals = "%" + field + "%=%" + value + "%";

                predicates.add(cb.like(cb.lower(root.get("message")), loose));
                predicates.add(cb.like(cb.lower(root.get("rawLog")), loose));
                predicates.add(cb.like(cb.lower(root.get("message")), bracket));
                predicates.add(cb.like(cb.lower(root.get("rawLog")), bracket));
                predicates.add(cb.like(cb.lower(root.get("message")), equals));
                predicates.add(cb.like(cb.lower(root.get("rawLog")), equals));
            } else {
                String fieldPattern = "%" + field + "%";
                predicates.add(cb.like(cb.lower(root.get("message")), fieldPattern));
                predicates.add(cb.like(cb.lower(root.get("rawLog")), fieldPattern));
            }

            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<LogEntry> filterCodeContains(String filterFragment) {
        return (root, query, cb) -> {
            if (filterFragment == null || filterFragment.isBlank()) return null;

            String value = filterFragment.trim().toLowerCase();
            String inBracket = "%filter code [%" + value + "%]%";
            String loose = "%" + value + "%";

            return cb.or(
                    cb.like(cb.lower(root.get("message")), inBracket),
                    cb.like(cb.lower(root.get("rawLog")), inBracket),
                    cb.and(
                            cb.like(cb.lower(root.get("message")), "%filter%"),
                            cb.like(cb.lower(root.get("message")), loose)
                    )
            );
        };
    }

    public static Specification<LogEntry> hasTimestampBetween(LocalDateTime dateFrom, LocalDateTime dateTo) {
        return (root, query, cb) -> {
            if (dateFrom == null && dateTo == null) return null;

            if (dateFrom != null && dateTo != null) {
                return cb.between(root.get("logTimestamp"), dateFrom, dateTo);
            }

            if (dateFrom != null) {
                return cb.greaterThanOrEqualTo(root.get("logTimestamp"), dateFrom);
            }

            return cb.lessThanOrEqualTo(root.get("logTimestamp"), dateTo);
        };
    }
}