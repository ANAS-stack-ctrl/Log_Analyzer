package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.CrossSearchHitDto;
import com.caciopee.loganalyzer.dto.CrossSearchRequestDto;
import com.caciopee.loganalyzer.dto.CrossSearchResponseDto;
import com.caciopee.loganalyzer.dto.LogEntryViewDto;
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
import java.util.stream.Collectors;

@Service
public class CrossSearchServiceImpl implements CrossSearchService {

    private static final int MAX_LOG_SCAN = 5000;
    private static final int MAX_SAMPLE_LOGS = 40;

    private static final Pattern UUID_PATTERN =
            Pattern.compile("\\buuid\\s*\\[([^]]+)]", Pattern.CASE_INSENSITIVE);

    private final LogEntryRepository logEntryRepository;
    private final LogPatternExtractor logPatternExtractor;

    public CrossSearchServiceImpl(LogEntryRepository logEntryRepository,
                                  LogPatternExtractor logPatternExtractor) {
        this.logEntryRepository = logEntryRepository;
        this.logPatternExtractor = logPatternExtractor;
    }

    @Override
    public CrossSearchResponseDto search(CrossSearchRequestDto request) {
        validate(request);

        String searchType = normalizeType(request.getSearchType());
        String value = request.getValue() != null ? request.getValue().trim() : "";

        Specification<LogEntry> spec = buildSpec(request, searchType, value);
        int scanLimit = normalizeLimit(request.getLimit());

        Sort sort = Sort.by(Sort.Direction.ASC, "logTimestamp")
                .and(Sort.by(Sort.Direction.ASC, "id"));

        List<LogEntry> logs = logEntryRepository.findAll(
                spec,
                PageRequest.of(0, Math.min(scanLimit, MAX_LOG_SCAN), sort)
        ).getContent();

        boolean truncated = logs.size() >= Math.min(scanLimit, MAX_LOG_SCAN);

        CrossSearchResponseDto response = new CrossSearchResponseDto();
        response.setSearchType(searchType);
        response.setValue(value);
        response.setBusinessField(request.getBusinessField());
        response.setTotalMatchingLogs((long) logs.size());
        response.setTruncated(truncated);
        response.setSessionHits(buildSessionHits(logs));
        response.setSampleLogs(logs.stream().limit(MAX_SAMPLE_LOGS).map(this::toDto).toList());
        response.setSummary(buildSummary(response));
        return response;
    }

    private void validate(CrossSearchRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Requête vide.");
        }
        if (request.getImportIds() == null || request.getImportIds().isEmpty()) {
            throw new IllegalArgumentException("Au moins un importId est requis.");
        }
        if (request.getSearchType() == null || request.getSearchType().isBlank()) {
            throw new IllegalArgumentException("searchType est requis.");
        }
        if (request.getValue() == null || request.getValue().isBlank()) {
            throw new IllegalArgumentException("value est requis.");
        }
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
    }

    private Specification<LogEntry> buildSpec(CrossSearchRequestDto request, String searchType, String value) {
        Specification<LogEntry> base = Specification.allOf(
                LogEntrySpecifications.hasImportIds(request.getImportIds()),
                LogEntrySpecifications.hasTimestampBetween(request.getDateFrom(), request.getDateTo()),
                LogEntrySpecifications.hasError(request.getErrorsOnly())
        );

        Specification<LogEntry> criteria = switch (searchType) {
            case "UUID" -> LogEntrySpecifications.hasUuid(value);
            case "FILTER" -> LogEntrySpecifications.filterCodeContains(value);
            case "USER" -> LogEntrySpecifications.hasUserName(value);
            case "SESSION" -> LogEntrySpecifications.hasSessionId(value);
            case "PROCESS" -> LogEntrySpecifications.hasProcessName(value);
            case "BUSINESS_FIELD" -> LogEntrySpecifications.businessFieldContains(
                    firstNonBlank(request.getBusinessField(), value),
                    request.getBusinessField() != null && !request.getBusinessField().isBlank() ? value : null
            );
            case "MESSAGE" -> LogEntrySpecifications.messageContains(value);
            default -> throw new IllegalArgumentException("searchType inconnu : " + searchType);
        };

        return base.and(criteria);
    }

    private List<CrossSearchHitDto> buildSessionHits(List<LogEntry> logs) {
        Map<String, HitAccumulator> map = new LinkedHashMap<>();

        for (LogEntry log : logs) {
            String sessionId = nullSafe(log.getSessionId(), "NON_RENSEIGNE");
            Long importId = log.getLogImport() != null ? log.getLogImport().getId() : null;
            String key = importId + "::" + sessionId;

            map.computeIfAbsent(key, k -> new HitAccumulator(importId, sessionId))
                    .add(log);
        }

        return map.values().stream()
                .map(HitAccumulator::toDto)
                .sorted(Comparator.comparing(CrossSearchHitDto::getLogCount, Comparator.nullsLast(Long::compareTo)).reversed())
                .limit(100)
                .toList();
    }

    private String buildSummary(CrossSearchResponseDto response) {
        int sessions = response.getSessionHits().size();
        long errors = response.getSessionHits().stream()
                .mapToLong(h -> h.getErrorCount() != null ? h.getErrorCount() : 0)
                .sum();

        StringBuilder sb = new StringBuilder();
        sb.append(response.getTotalMatchingLogs()).append(" ligne(s) trouvée(s) dans ")
                .append(sessions).append(" session(s) répartie(s) sur ")
                .append(response.getSessionHits().stream().map(CrossSearchHitDto::getImportId).filter(Objects::nonNull).collect(Collectors.toSet()).size())
                .append(" import(s).");

        if (errors > 0) {
            sb.append(" ").append(errors).append(" erreur(s) au total dans ces sessions.");
        }

        if (response.isTruncated()) {
            sb.append(" Résultat tronqué : affinez la recherche ou réduisez la période.");
        }

        return sb.toString();
    }

    private LogEntryViewDto toDto(LogEntry log) {
        LogEntryViewDto dto = new LogEntryViewDto();
        dto.setId(log.getId());
        dto.setImportId(log.getLogImport() != null ? log.getLogImport().getId() : null);
        dto.setLogTimestamp(log.getLogTimestamp());
        dto.setLevel(log.getLevel());
        dto.setUserName(log.getUserName());
        dto.setProcessName(log.getProcessName());
        dto.setMessage(log.getMessage());
        dto.setSessionId(log.getSessionId());
        dto.setIsError(Boolean.TRUE.equals(log.getIsError()));
        dto.setUuid(extractUuid(log));
        return dto;
    }

    private String extractUuid(LogEntry log) {
        if (log.getMessage() != null) {
            Matcher m = UUID_PATTERN.matcher(log.getMessage());
            if (m.find()) return m.group(1);
        }
        return log.getBusinessKey();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) return 500;
        return Math.min(limit, MAX_LOG_SCAN);
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        return b != null ? b.trim() : "";
    }

    private String nullSafe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private final class HitAccumulator {
        private final Long importId;
        private final String sessionId;
        private long logCount;
        private long errorCount;
        private LocalDateTime firstTs;
        private LocalDateTime lastTs;
        private String sampleMessage;
        private Long anchorLogId;
        private String userName;
        private String importFileName;
        private final Map<String, Long> processCounts = new HashMap<>();

        HitAccumulator(Long importId, String sessionId) {
            this.importId = importId;
            this.sessionId = sessionId;
        }

        void add(LogEntry log) {
            logCount++;
            if (anchorLogId == null && log.getId() != null) {
                anchorLogId = log.getId();
            }
            if (Boolean.TRUE.equals(log.getIsError())) errorCount++;
            if (log.getLogTimestamp() != null) {
                if (firstTs == null || log.getLogTimestamp().isBefore(firstTs)) firstTs = log.getLogTimestamp();
                if (lastTs == null || log.getLogTimestamp().isAfter(lastTs)) lastTs = log.getLogTimestamp();
            }
            if (sampleMessage == null && log.getMessage() != null) {
                sampleMessage = truncate(log.getMessage(), 200);
            }
            if (userName == null && log.getUserName() != null) userName = log.getUserName();
            if (importFileName == null && log.getLogImport() != null) {
                importFileName = log.getLogImport().getFileName();
            }

            GraphExtraction ex = logPatternExtractor.extractGraph(log);
            String process = ex.getProcess();
            if (process != null && !process.isBlank()) {
                processCounts.merge(process, 1L, Long::sum);
            }
        }

        CrossSearchHitDto toDto() {
            CrossSearchHitDto dto = new CrossSearchHitDto();
            dto.setImportId(importId);
            dto.setImportFileName(importFileName);
            dto.setSessionId(sessionId);
            dto.setUserName(userName);
            dto.setLogCount(logCount);
            dto.setErrorCount(errorCount);
            dto.setFirstTimestamp(firstTs);
            dto.setLastTimestamp(lastTs);
            dto.setSampleMessage(sampleMessage);
            dto.setAnchorLogId(anchorLogId);
            dto.setDominantProcess(processCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null));
            return dto;
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
