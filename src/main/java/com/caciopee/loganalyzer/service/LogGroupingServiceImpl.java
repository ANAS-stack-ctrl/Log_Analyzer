package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.LogGroupDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogEntrySpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogGroupingServiceImpl implements LogGroupingService {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("\\buuid\\s*\\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private final LogEntryRepository logEntryRepository;

    public LogGroupingServiceImpl(LogEntryRepository logEntryRepository) {
        this.logEntryRepository = logEntryRepository;
    }

    @Override
    public List<LogGroupDto> groupLogs(List<Long> importIds,
                                       List<String> fileNames,
                                       Boolean errorOnly,
                                       String eventType,
                                       String processName,
                                       String userName,
                                       String sessionId,
                                       String uuid,
                                       LocalDateTime dateFrom,
                                       LocalDateTime dateTo,
                                       String groupBy,
                                       Integer limit) {

        String normalizedGroupBy = normalizeGroupBy(groupBy);
        int safeLimit = normalizeLimit(limit);

        Specification<LogEntry> spec = Specification
                .where(LogEntrySpecifications.hasImportIds(importIds))
                .and(LogEntrySpecifications.hasFileNames(fileNames))
                .and(LogEntrySpecifications.hasError(errorOnly))
                .and(LogEntrySpecifications.hasEventType(eventType))
                .and(LogEntrySpecifications.hasProcessName(processName))
                .and(LogEntrySpecifications.hasUserName(userName))
                .and(LogEntrySpecifications.hasSessionId(sessionId))
                .and(LogEntrySpecifications.hasUuid(uuid))
                .and(LogEntrySpecifications.hasTimestampBetween(dateFrom, dateTo));

        Sort sort = Sort.by(Sort.Direction.ASC, "logTimestamp")
                .and(Sort.by(Sort.Direction.ASC, "id"));

        List<LogEntry> logs;

        if (safeLimit == 0) {
            logs = logEntryRepository.findAll(spec, sort);
        } else {
            logs = logEntryRepository.findAll(
                    spec,
                    PageRequest.of(
                            0,
                            Math.min(safeLimit * 100, 100000),
                            sort
                    )
            ).getContent();
        }

        Map<String, GroupAccumulator> groups = new LinkedHashMap<>();

        for (LogEntry log : logs) {
            String key = resolveGroupKey(log, normalizedGroupBy);

            if (key == null || key.isBlank()) {
                key = "NON_RENSEIGNE";
            }

            GroupAccumulator acc = groups.computeIfAbsent(key, k -> new GroupAccumulator(normalizedGroupBy, k));
            acc.add(log);
        }

        return groups.values()
                .stream()
                .map(GroupAccumulator::toDto)
                .sorted(
                        Comparator.comparing(LogGroupDto::getErrorCount, Comparator.nullsLast(Long::compareTo)).reversed()
                                .thenComparing(LogGroupDto::getTotalLogs, Comparator.nullsLast(Long::compareTo)).reversed()
                )
                .limit(safeLimit == 0 ? Long.MAX_VALUE : safeLimit)
                .toList();
    }

    private String normalizeGroupBy(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return "sessionId";
        }

        String value = groupBy.trim();

        return switch (value) {
            case "sessionId", "userName", "processName", "eventType", "level",
                 "sourceClass", "sourceFileName", "fileName", "uuid", "businessKey",
                 "correlationId" -> value;
            default -> "sessionId";
        };
    }

    private String resolveGroupKey(LogEntry log, String groupBy) {
        return switch (groupBy) {
            case "sessionId" -> log.getSessionId();
            case "userName" -> log.getUserName();
            case "processName" -> log.getProcessName();
            case "eventType" -> log.getEventType();
            case "level" -> log.getLevel();
            case "sourceClass" -> log.getSourceClass();
            case "sourceFileName" -> firstNonBlank(log.getSourceFileName(), log.getLogImport() != null ? log.getLogImport().getFileName() : null);
            case "fileName" -> firstNonBlank(log.getSourceFileName(), log.getLogImport() != null ? log.getLogImport().getFileName() : null);
            case "uuid" -> extractUuid(log);
            case "businessKey" -> log.getBusinessKey();
            case "correlationId" -> log.getUserCorrelationId();
            default -> log.getSessionId();
        };
    }

    private String extractUuid(LogEntry log) {
        if (log.getMessage() != null) {
            Matcher matcher = UUID_PATTERN.matcher(log.getMessage());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        if (log.getRawLog() != null) {
            Matcher matcher = UUID_PATTERN.matcher(log.getRawLog());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return log.getBusinessKey();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) return 100;

        if (limit == 0) {
            return 0; // 0 = tous les groupes
        }

        if (limit < 0) return 100;

        return Math.min(limit, 5000);
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b;
    }

    private static class GroupAccumulator {
        private final String groupBy;
        private final String groupKey;

        private long totalLogs = 0;
        private long errorCount = 0;
        private long warningCount = 0;
        private long infoCount = 0;

        private LocalDateTime firstTimestamp;
        private LocalDateTime lastTimestamp;

        private final Map<String, Integer> processCount = new HashMap<>();
        private final Map<String, Integer> userCount = new HashMap<>();
        private final Map<String, Integer> fileCount = new HashMap<>();

        GroupAccumulator(String groupBy, String groupKey) {
            this.groupBy = groupBy;
            this.groupKey = groupKey;
        }

        void add(LogEntry log) {
            totalLogs++;

            String level = log.getLevel() == null ? "" : log.getLevel().trim().toUpperCase(Locale.ROOT);

            if (Boolean.TRUE.equals(log.getIsError()) || "ERROR".equals(level)) {
                errorCount++;
            } else if ("WARN".equals(level) || "WARNING".equals(level)) {
                warningCount++;
            } else if ("INFO".equals(level)) {
                infoCount++;
            }

            LocalDateTime ts = log.getLogTimestamp();
            if (ts != null) {
                if (firstTimestamp == null || ts.isBefore(firstTimestamp)) {
                    firstTimestamp = ts;
                }
                if (lastTimestamp == null || ts.isAfter(lastTimestamp)) {
                    lastTimestamp = ts;
                }
            }

            increment(processCount, log.getProcessName());
            increment(userCount, log.getUserName());

            String fileName = log.getSourceFileName();
            if ((fileName == null || fileName.isBlank()) && log.getLogImport() != null) {
                fileName = log.getLogImport().getFileName();
            }
            increment(fileCount, fileName);
        }

        LogGroupDto toDto() {
            LogGroupDto dto = new LogGroupDto();

            dto.setGroupBy(groupBy);
            dto.setGroupKey(groupKey);

            dto.setTotalLogs(totalLogs);
            dto.setErrorCount(errorCount);
            dto.setWarningCount(warningCount);
            dto.setInfoCount(infoCount);

            dto.setFirstTimestamp(firstTimestamp);
            dto.setLastTimestamp(lastTimestamp);

            dto.setMainProcessName(mostFrequent(processCount));
            dto.setMainUserName(mostFrequent(userCount));
            dto.setMainFileName(mostFrequent(fileCount));

            dto.setSummary(buildSummary());

            return dto;
        }

        private String buildSummary() {
            StringBuilder sb = new StringBuilder();

            sb.append("Groupe '").append(groupKey).append("' basé sur ").append(groupBy)
                    .append(" : ").append(totalLogs).append(" log(s)");

            if (errorCount > 0) {
                sb.append(", ").append(errorCount).append(" erreur(s)");
            }

            if (warningCount > 0) {
                sb.append(", ").append(warningCount).append(" avertissement(s)");
            }

            if (firstTimestamp != null && lastTimestamp != null) {
                sb.append(", période de ").append(firstTimestamp).append(" à ").append(lastTimestamp);
            }

            sb.append(".");

            return sb.toString();
        }

        private void increment(Map<String, Integer> map, String value) {
            if (value == null || value.isBlank()) return;
            map.merge(value, 1, Integer::sum);
        }

        private String mostFrequent(Map<String, Integer> map) {
            return map.entrySet()
                    .stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }
    }
}