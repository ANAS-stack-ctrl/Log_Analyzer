package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.AssistantUserTopSessionsRequestDto;
import com.caciopee.loganalyzer.dto.AssistantUserTopSessionsResponseDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogEntrySpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AssistantUserTopSessionsServiceImpl implements AssistantUserTopSessionsService {

    private final LogEntryRepository logEntryRepository;

    public AssistantUserTopSessionsServiceImpl(LogEntryRepository logEntryRepository) {
        this.logEntryRepository = logEntryRepository;
    }

    @Override
    public AssistantUserTopSessionsResponseDto findTopSessions(AssistantUserTopSessionsRequestDto request) {
        if (request == null || request.getImportIds() == null || request.getImportIds().isEmpty()) {
            throw new IllegalArgumentException("Au moins un importId est requis.");
        }
        if (request.getUserName() == null || request.getUserName().isBlank()) {
            throw new IllegalArgumentException("userName requis.");
        }

        int max = request.getMaxSessions() != null ? request.getMaxSessions() : 5;
        if (max < 1) max = 5;
        if (max > 20) max = 20;

        Specification<LogEntry> spec = Specification
                .where(LogEntrySpecifications.hasImportIds(request.getImportIds()))
                .and(LogEntrySpecifications.hasTimestampBetween(request.getDateFrom(), request.getDateTo()))
                .and(LogEntrySpecifications.hasUserName(request.getUserName()));

        List<LogEntry> logs = logEntryRepository.findAll(
                spec,
                Sort.by(Sort.Direction.ASC, "logTimestamp").and(Sort.by(Sort.Direction.ASC, "id"))
        );

        Map<String, Acc> bySession = new HashMap<>();
        for (LogEntry e : logs) {
            String sid = safe(e.getSessionId());
            if (sid.isBlank() || "NON_RENSEIGNE".equalsIgnoreCase(sid)) continue;
            bySession.computeIfAbsent(sid, k -> new Acc()).add(e);
        }

        List<Map.Entry<String, Acc>> entries = new ArrayList<>(bySession.entrySet());
        entries.sort((a, b) -> {
            int cmpErr = Long.compare(b.getValue().errorCount, a.getValue().errorCount);
            if (cmpErr != 0) return cmpErr;
            int cmpWarn = Long.compare(b.getValue().warningCount, a.getValue().warningCount);
            if (cmpWarn != 0) return cmpWarn;
            int cmpZero = Long.compare(b.getValue().zeroRowCount, a.getValue().zeroRowCount);
            if (cmpZero != 0) return cmpZero;
            return Long.compare(b.getValue().totalLogs, a.getValue().totalLogs);
        });

        AssistantUserTopSessionsResponseDto response = new AssistantUserTopSessionsResponseDto();
        response.setUserName(request.getUserName());
        response.setTotalSessionsFound(bySession.size());

        List<AssistantUserTopSessionsResponseDto.UserSessionSummaryDto> top = new ArrayList<>();
        for (int i = 0; i < Math.min(max, entries.size()); i++) {
            Map.Entry<String, Acc> it = entries.get(i);
            AssistantUserTopSessionsResponseDto.UserSessionSummaryDto dto =
                    new AssistantUserTopSessionsResponseDto.UserSessionSummaryDto();
            dto.setSessionId(it.getKey());
            dto.setTotalLogs(it.getValue().totalLogs);
            dto.setErrorCount(it.getValue().errorCount);
            dto.setWarningCount(it.getValue().warningCount);
            dto.setZeroRowCount(it.getValue().zeroRowCount);
            dto.setFirstTimestamp(it.getValue().firstTs != null ? it.getValue().firstTs.toString() : null);
            dto.setLastTimestamp(it.getValue().lastTs != null ? it.getValue().lastTs.toString() : null);
            top.add(dto);
        }
        response.setTopSessions(top);
        return response;
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private static class Acc {
        long totalLogs = 0;
        long errorCount = 0;
        long warningCount = 0;
        long zeroRowCount = 0;
        LocalDateTime firstTs;
        LocalDateTime lastTs;

        void add(LogEntry e) {
            totalLogs++;
            if (Boolean.TRUE.equals(e.getIsError()) || "ERROR".equalsIgnoreCase(safe(e.getLevel()))) errorCount++;
            if ("WARN".equalsIgnoreCase(safe(e.getLevel())) || "WARNING".equalsIgnoreCase(safe(e.getLevel()))) warningCount++;
            String msg = safe(e.getMessage()).toLowerCase(Locale.ROOT);
            if (msg.contains("0 row") || msg.contains("[0] row fetched")) zeroRowCount++;

            LocalDateTime ts = e.getLogTimestamp();
            if (ts != null) {
                if (firstTs == null || ts.isBefore(firstTs)) firstTs = ts;
                if (lastTs == null || ts.isAfter(lastTs)) lastTs = ts;
            }
        }

        private String safe(String v) {
            return v == null ? "" : v.trim();
        }
    }
}

