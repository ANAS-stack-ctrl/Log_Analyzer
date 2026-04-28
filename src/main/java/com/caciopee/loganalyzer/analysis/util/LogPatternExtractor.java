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
            Pattern.compile("thread name\\s*([^,|]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLASSNAME_PATTERN =
            Pattern.compile("className\\s*\\[?([A-Za-z0-9_]+)\\]?", Pattern.CASE_INSENSITIVE);

    /**
     * Exemples visés :
     * - 0 row
     * - 12 row
     * - 150 row
     *
     * On reste volontairement strict pour éviter d'attraper de gros IDs.
     */
    private static final Pattern ROW_COUNT_PATTERN =
            Pattern.compile("\\b(\\d{1,6})\\s*row\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Exemples visés :
     * - took [23] ms
     * - took [1005] ms
     */
    private static final Pattern TOOK_PATTERN =
            Pattern.compile("took\\s*\\[(\\d{1,9})]\\s*ms", Pattern.CASE_INSENSITIVE);

    /**
     * Exemples visés :
     * memory usage (Mo) [...] 4047
     *
     * On prend la valeur numérique en fin de ligne pour éviter
     * d'attraper un transactionId énorme présent plus tôt dans la ligne.
     */
    private static final Pattern MEMORY_PATTERN =
            Pattern.compile("memory usage \\(Mo\\).*?\\s(\\d{1,6})\\s*$", Pattern.CASE_INSENSITIVE);

    public ExtractedLogContext extract(LogEntry log) {
        ExtractedLogContext ctx = new ExtractedLogContext();

        String message = safe(log != null ? log.getMessage() : null);
        String processName = safe(log != null ? log.getProcessName() : null);

        ctx.setExtractedUuid(extractFirst(UUID_PATTERN, message));
        ctx.setExtractedTransactionId(extractFirst(TRANSACTION_PATTERN, message));
        ctx.setExtractedFilterCode(extractFirst(FILTER_CODE_PATTERN, message));
        ctx.setExtractedThreadName(extractFirst(THREAD_PATTERN, message));
        ctx.setExtractedClassName(extractFirst(CLASSNAME_PATTERN, message));

        ctx.setExtractedRowCount(extractInteger(ROW_COUNT_PATTERN, message));
        ctx.setExtractedDurationMs(extractLong(TOOK_PATTERN, message));
        ctx.setExtractedMemoryMo(extractInteger(MEMORY_PATTERN, message));

        if (ctx.getExtractedFilterCode() == null && processName.contains("-")) {
            String[] parts = processName.split("-");
            if (parts.length >= 2) {
                String candidate = safe(parts[1]).trim();
                if (!candidate.isEmpty()) {
                    ctx.setExtractedFilterCode(candidate);
                }
            }
        }

        return ctx;
    }

    private String extractFirst(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String value = matcher.group(1);
            return value == null ? null : value.trim();
        }
        return null;
    }

    private Integer extractInteger(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                long value = Long.parseLong(matcher.group(1));
                if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
                    return null;
                }
                return (int) value;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Long extractLong(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}