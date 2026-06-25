package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.GraphLogSearchRequestDto;
import com.caciopee.loganalyzer.dto.GraphLogSearchResponseDto;
import com.caciopee.loganalyzer.dto.GraphOccurrenceDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogEntrySpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class GraphLogSearchServiceImpl implements GraphLogSearchService {

    private final LogEntryRepository logEntryRepository;
    private final LogPatternExtractor logPatternExtractor;

    public GraphLogSearchServiceImpl(LogEntryRepository logEntryRepository,
                                     LogPatternExtractor logPatternExtractor) {
        this.logEntryRepository = logEntryRepository;
        this.logPatternExtractor = logPatternExtractor;
    }

    @Override
    public GraphLogSearchResponseDto search(GraphLogSearchRequestDto request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("Terme de recherche requis.");
        }

        List<String> terms = parseTerms(request.getQuery());
        if (terms.isEmpty()) {
            throw new IllegalArgumentException("Terme de recherche requis.");
        }

        List<LogEntry> logs = loadLogs(request);
        List<LogEntry> matched = new ArrayList<>();

        for (LogEntry log : logs) {
            if (matchesAllTerms(log, terms)) {
                matched.add(log);
            }
        }

        List<GraphOccurrenceDto> hits = matched.stream()
                .map(this::toOccurrence)
                .toList();

        GraphLogSearchResponseDto response = new GraphLogSearchResponseDto();
        response.setQuery(request.getQuery().trim());
        response.setTotalLogs(logs.size());
        response.setMatchedCount(matched.size());
        response.setDisplayedCount(hits.size());
        response.setHits(hits);
        return response;
    }

    private List<LogEntry> loadLogs(GraphLogSearchRequestDto request) {
        Specification<LogEntry> spec = Specification
                .where(LogEntrySpecifications.hasImportIds(request.getImportIds()))
                .and(LogEntrySpecifications.hasTimestampBetween(request.getDateFrom(), request.getDateTo()))
                .and(groupSpec(request.getGroupBy(), request.getGroupKey()));

        return logEntryRepository.findAll(
                spec,
                Sort.by(Sort.Direction.ASC, "logTimestamp")
                        .and(Sort.by(Sort.Direction.ASC, "id"))
        );
    }

    private Specification<LogEntry> groupSpec(String groupBy, String groupKey) {
        if (!notBlank(groupBy) || !notBlank(groupKey) || "NON_RENSEIGNE".equalsIgnoreCase(groupKey)) {
            return null;
        }

        return switch (groupBy) {
            case "userName" -> LogEntrySpecifications.hasUserName(groupKey);
            case "processName" -> LogEntrySpecifications.hasProcessName(groupKey);
            case "sessionId" -> LogEntrySpecifications.hasSessionId(groupKey);
            case "eventType" -> LogEntrySpecifications.hasEventType(groupKey);
            case "sourceFileName", "fileName" -> LogEntrySpecifications.hasFileName(groupKey);
            case "uuid", "businessKey", "correlationId" -> LogEntrySpecifications.hasUuid(groupKey);
            default -> null;
        };
    }

    private List<String> parseTerms(String query) {
        List<String> terms = new ArrayList<>();
        for (String part : query.split(";")) {
            String term = part.trim().toLowerCase(Locale.ROOT);
            if (!term.isBlank()) {
                terms.add(term);
            }
        }
        return terms;
    }

    private boolean matchesAllTerms(LogEntry log, List<String> terms) {
        String haystack = buildSearchText(log);
        return terms.stream().allMatch(haystack::contains);
    }

    private String buildSearchText(LogEntry log) {
        GraphExtraction ex = logPatternExtractor.extractGraph(log);
        StringBuilder sb = new StringBuilder();

        if (log.getLogTimestamp() != null) {
            String ts = log.getLogTimestamp().toString();
            sb.append(ts).append(' ');
            sb.append(ts.replace('T', ' ')).append(' ');
        }

        append(sb, log.getLevel());
        append(sb, log.getProcessName());
        append(sb, log.getUserName());
        append(sb, log.getSessionId());
        append(sb, ex.getProcess());
        append(sb, ex.getAction());
        append(sb, ex.getFilter());
        append(sb, ex.getTask());
        append(sb, ex.getObject());
        append(sb, log.getMessage());
        append(sb, log.getRawLog());

        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private void append(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(value).append(' ');
        }
    }

    private GraphOccurrenceDto toOccurrence(LogEntry log) {
        GraphExtraction ex = logPatternExtractor.extractGraph(log);
        GraphOccurrenceDto dto = new GraphOccurrenceDto();
        dto.setLogId(log.getId());
        dto.setTimestamp(log.getLogTimestamp());
        dto.setColumnProcessName(log.getProcessName());
        dto.setProcessName(firstNonBlank(ex.getProcess(), log.getProcessName()));
        dto.setActionName(ex.getAction());
        dto.setFilterCode(ex.getFilter());
        dto.setBusinessObject(ex.getObject());
        dto.setMessagePreview(shorten(log.getMessage()));
        dto.setFullMessage(firstNonBlank(log.getRawLog(), log.getMessage()));
        return dto;
    }

    private String shorten(String message) {
        if (message == null) return "";
        String cleaned = message.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 320 ? cleaned : cleaned.substring(0, 317) + "...";
    }

    private String firstNonBlank(String a, String b) {
        return notBlank(a) ? a : b;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
