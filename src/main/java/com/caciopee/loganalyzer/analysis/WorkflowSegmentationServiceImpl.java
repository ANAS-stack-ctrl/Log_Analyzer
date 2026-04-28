package com.caciopee.loganalyzer.analysis;

import com.caciopee.loganalyzer.analysis.model.ExtractedLogContext;
import com.caciopee.loganalyzer.analysis.model.WorkflowSegment;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.entity.LogEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class WorkflowSegmentationServiceImpl implements WorkflowSegmentationService {

    private final LogPatternExtractor logPatternExtractor;

    public WorkflowSegmentationServiceImpl(LogPatternExtractor logPatternExtractor) {
        this.logPatternExtractor = logPatternExtractor;
    }

    @Override
    public List<WorkflowSegment> splitImportIntoSegments(List<LogEntry> logs) {
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }

        List<WorkflowSegment> segments = new ArrayList<>();
        WorkflowSegment current = null;
        int sequence = 1;

        for (LogEntry log : logs) {
            ExtractedLogContext ctx = logPatternExtractor.extract(log);

            boolean opensSegment = isStrongStart(log);
            boolean closesSegment = isStrongEnd(log);
            String candidateKey = buildNaturalKey(log, ctx);

            if (current == null) {
                current = createSegment(candidateKey, log, ctx, sequence++);
            } else {
                boolean sameSegment = belongsToCurrentSegment(current, log, ctx);

                if (opensSegment && !sameSegment) {
                    segments.add(current);
                    current = createSegment(candidateKey, log, ctx, sequence++);
                } else if (!sameSegment && isStrongIdentity(log, ctx)) {
                    segments.add(current);
                    current = createSegment(candidateKey, log, ctx, sequence++);
                }
            }

            current.addLog(log);
            enrichSegmentIdentity(current, log, ctx);

            if (closesSegment) {
                segments.add(current);
                current = null;
            }
        }

        if (current != null && !current.getLogs().isEmpty()) {
            segments.add(current);
        }

        return mergeTinySegments(segments);
    }

    private WorkflowSegment createSegment(String candidateKey, LogEntry log, ExtractedLogContext ctx, int sequence) {
        WorkflowSegment seg = new WorkflowSegment();
        seg.setSegmentKey("SEG-" + sequence + "::" + candidateKey);
        seg.setCorrelationId(log.getUserCorrelationId());
        seg.setSessionId(log.getSessionId());
        seg.setUuid(ctx.getExtractedUuid());
        seg.setTransactionId(ctx.getExtractedTransactionId());
        seg.setFilterCode(ctx.getExtractedFilterCode());
        seg.setProcessName(log.getProcessName());
        return seg;
    }

    private void enrichSegmentIdentity(WorkflowSegment seg, LogEntry log, ExtractedLogContext ctx) {
        if (isBlank(seg.getCorrelationId()) && isNotBlank(log.getUserCorrelationId())) {
            seg.setCorrelationId(log.getUserCorrelationId());
        }
        if (isBlank(seg.getSessionId()) && isNotBlank(log.getSessionId())) {
            seg.setSessionId(log.getSessionId());
        }
        if (isBlank(seg.getUuid()) && isNotBlank(ctx.getExtractedUuid())) {
            seg.setUuid(ctx.getExtractedUuid());
        }
        if (isBlank(seg.getTransactionId()) && isNotBlank(ctx.getExtractedTransactionId())) {
            seg.setTransactionId(ctx.getExtractedTransactionId());
        }
        if (isBlank(seg.getFilterCode()) && isNotBlank(ctx.getExtractedFilterCode())) {
            seg.setFilterCode(ctx.getExtractedFilterCode());
        }
        if (isBlank(seg.getProcessName()) && isNotBlank(log.getProcessName())) {
            seg.setProcessName(log.getProcessName());
        }
    }

    private boolean belongsToCurrentSegment(WorkflowSegment current, LogEntry log, ExtractedLogContext ctx) {
        if (same(current.getCorrelationId(), log.getUserCorrelationId())) return true;
        if (same(current.getSessionId(), log.getSessionId())) return true;
        if (same(current.getUuid(), ctx.getExtractedUuid())) return true;
        if (same(current.getTransactionId(), ctx.getExtractedTransactionId())) return true;
        if (same(current.getFilterCode(), ctx.getExtractedFilterCode())
                && same(current.getProcessName(), log.getProcessName())) return true;

        return false;
    }

    private boolean isStrongIdentity(LogEntry log, ExtractedLogContext ctx) {
        return isNotBlank(log.getUserCorrelationId())
                || isNotBlank(ctx.getExtractedUuid())
                || isNotBlank(ctx.getExtractedTransactionId());
    }

    private String buildNaturalKey(LogEntry log, ExtractedLogContext ctx) {
        if (isNotBlank(log.getUserCorrelationId())) return "CORR::" + log.getUserCorrelationId();
        if (isNotBlank(ctx.getExtractedUuid())) return "UUID::" + ctx.getExtractedUuid();
        if (isNotBlank(ctx.getExtractedTransactionId())) return "TX::" + ctx.getExtractedTransactionId();
        if (isNotBlank(log.getSessionId())) return "SESS::" + log.getSessionId();

        String process = defaultString(log.getProcessName(), "UNKNOWN_PROC");
        String filter = defaultString(ctx.getExtractedFilterCode(), "NO_FILTER");
        return "PROC_FILTER::" + process + "::" + filter;
    }

    private boolean isStrongStart(LogEntry log) {
        String msg = lower(log.getMessage());
        String proc = lower(log.getProcessName());
        String eventType = upper(log.getEventType());

        return "WORKFLOW_LOOP_START".equals(eventType)
                || "WORKFLOW_NEW_INTERFACE_OBJECT".equals(eventType)
                || "WORKFLOW_STRUCTURE_DATA_CALL".equals(eventType)
                || "STRUCTURE_DATA_START".equals(eventType)
                || "TYPE_CONVERSION_START".equals(eventType)
                || msg.contains("start fire rules")
                || msg.contains("begin executing request")
                || msg.contains("start saveorupdate ws")
                || msg.contains("start ws_insert")
                || proc.contains("-before_load")
                || proc.contains("-load");
    }

    private boolean isStrongEnd(LogEntry log) {
        String msg = lower(log.getMessage());
        String eventType = upper(log.getEventType());

        return "STRUCTURE_DATA_END".equals(eventType)
                || "MANDATORY_CHECK_END".equals(eventType)
                || "TYPE_CONVERSION_RESULT".equals(eventType)
                || "TYPE_CONVERSION_NULL".equals(eventType)
                || msg.contains("end fire rules")
                || msg.contains("executed request finished")
                || msg.contains("end saveorupdate ws")
                || msg.contains("end ws_insert")
                || msg.contains("update reussie");
    }

    private List<WorkflowSegment> mergeTinySegments(List<WorkflowSegment> segments) {
        if (segments.size() <= 1) {
            return segments;
        }

        List<WorkflowSegment> merged = new ArrayList<>();
        WorkflowSegment previous = null;

        for (WorkflowSegment current : segments) {
            if (previous == null) {
                previous = current;
                continue;
            }

            boolean tiny = current.getLogs().size() <= 2;
            boolean compatible = same(previous.getCorrelationId(), current.getCorrelationId())
                    || same(previous.getUuid(), current.getUuid())
                    || same(previous.getTransactionId(), current.getTransactionId())
                    || (same(previous.getProcessName(), current.getProcessName())
                    && same(previous.getFilterCode(), current.getFilterCode()));

            if (tiny && compatible) {
                previous.getLogs().addAll(current.getLogs());
            } else {
                merged.add(previous);
                previous = current;
            }
        }

        if (previous != null) {
            merged.add(previous);
        }

        return merged.stream()
                .filter(seg -> seg.getLogs() != null && !seg.getLogs().isEmpty())
                .collect(Collectors.toList());
    }

    private boolean same(String a, String b) {
        return isNotBlank(a) && isNotBlank(b) && a.equals(b);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }
}