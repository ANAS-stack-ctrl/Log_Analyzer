package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.LogEntryViewDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogEntrySpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogQueryServiceImpl implements LogQueryService {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("\\buuid\\s*\\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

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
                                            String sessionId,
                                            String uuid,
                                            Integer limit) {

        int safeLimit = (limit == null || limit <= 0) ? 100 : Math.min(limit, 1000);

        Specification<LogEntry> spec = Specification
                .where(LogEntrySpecifications.hasImportId(importId))
                .and(LogEntrySpecifications.hasFileName(fileName))
                .and(LogEntrySpecifications.hasError(errorOnly))
                .and(LogEntrySpecifications.hasEventType(eventType))
                .and(LogEntrySpecifications.hasProcessName(processName))
                .and(LogEntrySpecifications.hasSessionId(sessionId))
                .and(LogEntrySpecifications.hasUuid(uuid));

        return logEntryRepository.findAll(
                        spec,
                        PageRequest.of(
                                0,
                                safeLimit,
                                Sort.by(Sort.Direction.ASC, "logTimestamp").and(Sort.by(Sort.Direction.ASC, "id"))
                        )
                )
                .stream()
                .map(this::toDto)
                .toList();
    }

    private LogEntryViewDto toDto(LogEntry log) {
        LogEntryViewDto dto = new LogEntryViewDto();

        dto.setId(log.getId());
        dto.setImportId(log.getLogImport() != null ? log.getLogImport().getId() : null);

        dto.setLogTimestamp(log.getLogTimestamp());
        dto.setLevel(log.getLevel());
        dto.setProcessName(log.getProcessName());
        dto.setSourceClass(log.getSourceClass());
        dto.setMessage(log.getMessage());

        dto.setEventType(log.getEventType());
        dto.setBusinessMeaning(log.getBusinessMeaning());

        dto.setSessionId(log.getSessionId());
        dto.setCorrelationId(log.getUserCorrelationId());
        dto.setFileName(log.getLogImport() != null ? log.getLogImport().getFileName() : null);
        dto.setUuid(extractUuid(log));

        dto.setIsError(Boolean.TRUE.equals(log.getIsError()));

        return dto;
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