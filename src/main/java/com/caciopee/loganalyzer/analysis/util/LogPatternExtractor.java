package com.caciopee.loganalyzer.analysis.util;

import com.caciopee.loganalyzer.analysis.model.ExtractedLogContext;
import com.caciopee.loganalyzer.entity.LogEntry;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LogPatternExtractor {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("\\buuid\\s*\\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern TRANSACTION_PATTERN =
            Pattern.compile("transactionId\\s*[\\[(]([^\\])]+)[\\])]", Pattern.CASE_INSENSITIVE);

    private static final Pattern FILTER_CODE_PATTERN =
            Pattern.compile("filter code\\s*\\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern THREAD_PATTERN =
            Pattern.compile("thread name\\s*\\[?([^,\\]|]+)]?", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLASSNAME_PATTERN =
            Pattern.compile("className\\s*\\[?([A-Za-z0-9_]+)\\]?", Pattern.CASE_INSENSITIVE);

    private static final Pattern ROW_COUNT_PATTERN =
            Pattern.compile("(?:\\[(\\d{1,6})]\\s*row\\s*fetched|\\b(\\d{1,6})\\s*row\\b)", Pattern.CASE_INSENSITIVE);

    private static final Pattern TOOK_PATTERN =
            Pattern.compile("(?:took\\s*\\[(\\d{1,9})]\\s*ms|took\\s*(\\d{1,9})\\s*ms|took\\(ms\\)=(\\d{1,9}))", Pattern.CASE_INSENSITIVE);

    private static final Pattern MEMORY_PATTERN =
            Pattern.compile("memory usage \\(Mo\\).*?\\s(\\d{1,6})\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern TASK_PATTERN =
            Pattern.compile("taskName\\s*\\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern ACTION_PATTERN =
            Pattern.compile("actionName\\s*\\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern PROCESS_PATTERN =
            Pattern.compile("processName\\s*\\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern IP_PATTERN =
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    private static final Pattern TRIGGER_PATTERN =
            Pattern.compile("\\b(Trigger[A-Za-z0-9_ éèêàç\\-]+?)\\s+(was fired|is complete|starts)", Pattern.CASE_INSENSITIVE);

    private static final Pattern CHECKPOINT_PATTERN =
            Pattern.compile("(?:trCheckPoint\\s*:?\\s*|-+\\s*)?(ENTREE[_\\s][A-Z_\\s]+|SORTIE[_\\s][A-Z_\\s]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern BUSINESS_PAIR_PATTERN =
            Pattern.compile("\\b(noDUM|noUnite|noService|amp|ampe|refBad|refDemande|codeSecu|dateAction|orderState|sensActivite|nomService|statut|typeTr|notes)\\s*(?:=|:|::|-+)\\s*\\[?([^\\]|,\\r\\n]+)\\]?",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern QUERY_PARAM_PATTERN =
            Pattern.compile("\\b(wv[A-Za-z0-9_]+|w[A-Za-z0-9_]+|f[A-Za-z0-9_]+)\\s*=\\[([^\\]]+)]",
                    Pattern.CASE_INSENSITIVE);

    public ExtractedLogContext extract(LogEntry log) {
        ExtractedLogContext ctx = new ExtractedLogContext();

        String message = safe(log != null ? log.getMessage() : null);
        String processName = safe(log != null ? log.getProcessName() : null);
        String rawLog = safe(log != null ? log.getRawLog() : null);
        String all = message + " " + rawLog;

        ctx.setExtractedUuid(extractFirst(UUID_PATTERN, all));
        ctx.setExtractedTransactionId(extractFirst(TRANSACTION_PATTERN, all));
        ctx.setExtractedFilterCode(extractFirst(FILTER_CODE_PATTERN, all));
        ctx.setExtractedThreadName(extractFirst(THREAD_PATTERN, all));
        ctx.setExtractedClassName(extractFirst(CLASSNAME_PATTERN, all));

        ctx.setExtractedRowCount(extractInteger(ROW_COUNT_PATTERN, all));
        ctx.setExtractedDurationMs(extractLong(TOOK_PATTERN, all));
        ctx.setExtractedMemoryMo(extractInteger(MEMORY_PATTERN, message));

        ctx.setExtractedTaskName(extractFirst(TASK_PATTERN, all));
        ctx.setExtractedActionName(extractFirst(ACTION_PATTERN, all));
        ctx.setExtractedProcessName(extractFirst(PROCESS_PATTERN, all));
        ctx.setExtractedClientIp(extractFirst(IP_PATTERN, rawLog));

        ctx.setExtractedTriggerName(extractFirst(TRIGGER_PATTERN, all));
        ctx.setExtractedCheckpoint(cleanCheckpoint(extractFirst(CHECKPOINT_PATTERN, message)));

        Matcher businessMatcher = BUSINESS_PAIR_PATTERN.matcher(all);
        if (businessMatcher.find()) {
            ctx.setExtractedBusinessObjectKey(clean(businessMatcher.group(1)));
            ctx.setExtractedBusinessObjectValue(clean(businessMatcher.group(2)));
        }

        Matcher queryMatcher = QUERY_PARAM_PATTERN.matcher(all);
        if (queryMatcher.find()) {
            ctx.setExtractedQueryParameterName(clean(queryMatcher.group(1)));
            ctx.setExtractedQueryParameterValue(clean(queryMatcher.group(2)));
        }

        if (ctx.getExtractedFilterCode() == null && processName.contains("-")) {
            String[] parts = processName.split("-");
            if (parts.length >= 2) {
                ctx.setExtractedFilterCode(clean(parts[1]));
            }
        }

        if (ctx.getExtractedProcessName() == null && processName.contains("-")) {
            String[] parts = processName.split("-");
            if (parts.length >= 1) {
                ctx.setExtractedProcessName(clean(parts[0]));
            }
        }

        if (ctx.getExtractedTaskName() == null && processName.contains("-")) {
            String[] parts = processName.split("-");
            if (parts.length >= 3) {
                ctx.setExtractedTaskName(clean(parts[2]));
            } else if (parts.length >= 2) {
                ctx.setExtractedTaskName(clean(parts[1]));
            }
        }

        if (ctx.getExtractedActionName() == null && processName.contains("-")) {
            String[] parts = processName.split("-");
            if (parts.length >= 5) {
                ctx.setExtractedActionName(clean(parts[4]));
            } else if (parts.length >= 4) {
                ctx.setExtractedActionName(clean(parts[3]));
            }
        }

        return ctx;
    }

    private String extractFirst(Pattern pattern, String text) {
        if (pattern == null || text == null || text.isBlank()) {
            return null;
        }

        Matcher matcher = pattern.matcher(text);

        if (!matcher.find()) {
            return null;
        }

        String value = null;

        if (matcher.groupCount() >= 1) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                value = matcher.group(i);
                if (value != null && !value.isBlank()) {
                    break;
                }
            }
        }

        if (value == null || value.isBlank()) {
            value = matcher.group();
        }

        return clean(value);
    }

    private Integer extractInteger(Pattern pattern, String text) {
        String value = extractFirst(pattern, text);
        if (value == null) return null;

        try {
            long parsed = Long.parseLong(value);
            if (parsed > Integer.MAX_VALUE || parsed < Integer.MIN_VALUE) return null;
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long extractLong(Pattern pattern, String text) {
        String value = extractFirst(pattern, text);
        if (value == null) return null;

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String cleanCheckpoint(String value) {
        String cleaned = clean(value);
        if (cleaned == null) return null;

        cleaned = cleaned.replace("-", " ").replace("_", " ").replaceAll("\\s+", " ").trim();

        if (cleaned.isBlank() || "null".equalsIgnoreCase(cleaned)) {
            return null;
        }

        return cleaned;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}