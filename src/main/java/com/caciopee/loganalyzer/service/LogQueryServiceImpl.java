package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.LogEntryViewDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogEntrySpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogQueryServiceImpl implements LogQueryService {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("\\buuid\\s*\\[([^]]+)]", Pattern.CASE_INSENSITIVE);

    private final LogEntryRepository logEntryRepository;

    public LogQueryServiceImpl(LogEntryRepository logEntryRepository) {
        this.logEntryRepository = logEntryRepository;
    }

    @Override
    public List<LogEntryViewDto> searchLogs(Long importId,
                                            String fileName,
                                            Boolean errorOnly,
                                            String eventType,
                                            String processName,
                                            String userName,
                                            String sessionId,
                                            String uuid,
                                            LocalDateTime dateFrom,
                                            LocalDateTime dateTo,
                                            Integer limit) {

        int safeLimit = normalizeLimit(limit);

        Specification<LogEntry> spec = Specification.allOf(
                LogEntrySpecifications.hasImportId(importId),
                LogEntrySpecifications.hasFileName(fileName),
                LogEntrySpecifications.hasError(errorOnly),
                LogEntrySpecifications.hasEventType(eventType),
                LogEntrySpecifications.hasProcessName(processName),
                LogEntrySpecifications.hasUserName(userName),
                LogEntrySpecifications.hasSessionId(sessionId),
                LogEntrySpecifications.hasUuid(uuid),
                LogEntrySpecifications.hasTimestampBetween(dateFrom, dateTo)
        );

        return executeSearch(spec, safeLimit);
    }

    @Override
    public List<LogEntryViewDto> searchLogsMulti(List<Long> importIds,
                                                 List<String> fileNames,
                                                 Boolean errorOnly,
                                                 String eventType,
                                                 String processName,
                                                 String userName,
                                                 String sessionId,
                                                 String uuid,
                                                 LocalDateTime dateFrom,
                                                 LocalDateTime dateTo,
                                                 Integer limit) {

        int safeLimit = normalizeLimit(limit);

        Specification<LogEntry> spec = Specification.allOf(
                LogEntrySpecifications.hasImportIds(importIds),
                LogEntrySpecifications.hasFileNames(fileNames),
                LogEntrySpecifications.hasError(errorOnly),
                LogEntrySpecifications.hasEventType(eventType),
                LogEntrySpecifications.hasProcessName(processName),
                LogEntrySpecifications.hasUserName(userName),
                LogEntrySpecifications.hasSessionId(sessionId),
                LogEntrySpecifications.hasUuid(uuid),
                LogEntrySpecifications.hasTimestampBetween(dateFrom, dateTo)
        );

        return executeSearch(spec, safeLimit);
    }

    private List<LogEntryViewDto> executeSearch(Specification<LogEntry> spec, int limit) {
        Sort sort = Sort.by(Sort.Direction.ASC, "logTimestamp")
                .and(Sort.by(Sort.Direction.ASC, "id"));

        if (limit == 0) {
            return logEntryRepository.findAll(spec, sort)
                    .stream()
                    .map(this::toDto)
                    .toList();
        }

        return logEntryRepository.findAll(
                        spec,
                        PageRequest.of(0, limit, sort)
                )
                .stream()
                .map(this::toDto)
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) return 100;

        if (limit == 0) {
            return 0;
        }

        if (limit < 0) return 100;

        return Math.min(limit, 10000);
    }

    private LogEntryViewDto toDto(LogEntry log) {
        LogEntryViewDto dto = new LogEntryViewDto();

        dto.setId(log.getId());
        dto.setImportId(log.getLogImport() != null ? log.getLogImport().getId() : null);

        dto.setLogTimestamp(log.getLogTimestamp());
        dto.setLevel(log.getLevel());
        dto.setUserName(log.getUserName());
        dto.setProcessName(log.getProcessName());
        dto.setSourceClass(log.getSourceClass());
        dto.setMessage(log.getMessage());

        dto.setEventType(log.getEventType());
        dto.setBusinessMeaning(log.getBusinessMeaning());

        dto.setSessionId(log.getSessionId());
        dto.setCorrelationId(log.getUserCorrelationId());

        dto.setFileName(resolveDisplayedFileName(log));
        dto.setSourceFileName(log.getSourceFileName());
        dto.setSourceRelativePath(log.getSourceRelativePath());

        dto.setUuid(extractUuid(log));
        dto.setIsError(Boolean.TRUE.equals(log.getIsError()));

        return dto;
    }

    private String resolveDisplayedFileName(LogEntry log) {
        if (log.getSourceFileName() != null && !log.getSourceFileName().isBlank()) {
            return log.getSourceFileName();
        }

        return log.getLogImport() != null ? log.getLogImport().getFileName() : null;
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
}