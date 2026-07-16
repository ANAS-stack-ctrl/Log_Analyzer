package com.caciopee.loganalyzer.rag;

import com.caciopee.loganalyzer.entity.LogEntry;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Découpe les logs d'un import en fenêtres (chunks) prêtes à être embarquées.
 *
 * <p>Stratégie : on groupe par session (les logs d'une même session forment une
 * histoire cohérente), on ordonne par temps, puis on fait glisser une fenêtre de
 * N lignes avec un léger chevauchement. Chaque fenêtre porte des métadonnées
 * (process, filtre, erreur, durée max) qui serviront aux filtres de recherche.
 */
@Service
public class LogChunkingService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // took [12345] ms | took 12345 ms | took 12345(ms)
    private static final Pattern TOOK = Pattern.compile(
            "took\\s*\\[?\\s*(\\d{3,})\\s*]?\\s*\\(?\\s*ms", Pattern.CASE_INSENSITIVE);
    // filter code [XXX]
    private static final Pattern FILTER_CODE = Pattern.compile(
            "filter\\s*code\\s*\\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private final RagProperties props;

    public LogChunkingService(RagProperties props) {
        this.props = props;
    }

    /**
     * @param logs logs d'un import, dans n'importe quel ordre (seront regroupés/triés ici)
     * @return la liste des chunks à indexer
     */
    public List<LogChunk> chunk(Long importId, List<LogEntry> logs) {
        // 1) grouper par session (ordre d'apparition préservé)
        Map<String, List<LogEntry>> bySession = new LinkedHashMap<>();
        for (LogEntry log : logs) {
            String session = log.getSessionId() != null && !log.getSessionId().isBlank()
                    ? log.getSessionId() : "SANS_SESSION";
            bySession.computeIfAbsent(session, k -> new ArrayList<>()).add(log);
        }

        List<LogChunk> chunks = new ArrayList<>();
        int step = Math.max(1, props.getChunkSize() - props.getChunkOverlap());

        for (Map.Entry<String, List<LogEntry>> entry : bySession.entrySet()) {
            List<LogEntry> sessionLogs = entry.getValue();
            // tri par temps puis id (comme le reste de l'app)
            sessionLogs.sort((a, b) -> {
                if (a.getLogTimestamp() != null && b.getLogTimestamp() != null) {
                    int c = a.getLogTimestamp().compareTo(b.getLogTimestamp());
                    if (c != 0) return c;
                }
                return Long.compare(nz(a.getId()), nz(b.getId()));
            });

            for (int start = 0; start < sessionLogs.size(); start += step) {
                int end = Math.min(start + props.getChunkSize(), sessionLogs.size());
                List<LogEntry> window = sessionLogs.subList(start, end);
                if (!window.isEmpty()) {
                    chunks.add(buildChunk(importId, entry.getKey(), window));
                }
                if (end == sessionLogs.size()) break;
            }
        }
        return chunks;
    }

    private LogChunk buildChunk(Long importId, String sessionId, List<LogEntry> window) {
        StringBuilder content = new StringBuilder(window.size() * 120);
        Map<String, Integer> processCounts = new LinkedHashMap<>();
        String filterCode = null;
        String userName = null;
        boolean hasError = false;
        long maxDuration = 0;

        for (LogEntry log : window) {
            content.append(formatLine(log)).append('\n');

            if (log.getProcessName() != null && !log.getProcessName().isBlank()) {
                processCounts.merge(log.getProcessName(), 1, Integer::sum);
            }
            if (userName == null && log.getUserName() != null && !log.getUserName().isBlank()) {
                userName = log.getUserName();
            }
            if (filterCode == null) {
                String fc = extractFilterCode(log);
                if (fc != null) filterCode = fc;
            }
            if (isError(log)) hasError = true;
            long d = resolveDuration(log);
            if (d > maxDuration) maxDuration = d;
        }

        String process = processCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        LogEntry first = window.get(0);
        LogEntry last = window.get(window.size() - 1);

        return new LogChunk(
                importId,
                sessionId,
                process,
                filterCode,
                userName,
                hasError,
                maxDuration > 0 ? maxDuration : null,
                first.getLogTimestamp(),
                last.getLogTimestamp(),
                first.getId(),
                last.getId(),
                window.size(),
                content.toString().trim()
        );
    }

    /** Ligne lisible et compacte pour le LLM : temps · NIVEAU · user · process · message. */
    private String formatLine(LogEntry log) {
        StringBuilder sb = new StringBuilder(120);
        sb.append(log.getLogTimestamp() != null ? TS.format(log.getLogTimestamp()) : "??");
        sb.append(" · ").append(nb(log.getLevel(), "INFO"));
        if (log.getUserName() != null && !log.getUserName().isBlank()) {
            sb.append(" · ").append(log.getUserName());
        }
        if (log.getProcessName() != null && !log.getProcessName().isBlank()) {
            sb.append(" · ").append(log.getProcessName());
        }
        String msg = log.getMessage();
        if (msg == null || msg.isBlank()) msg = log.getRawLog();
        sb.append(" · ").append(nb(msg, "").replaceAll("\\s+", " ").trim());
        return sb.toString();
    }

    private String extractFilterCode(LogEntry log) {
        if (log.getFieldClassCode() != null && !log.getFieldClassCode().isBlank()) {
            return log.getFieldClassCode().trim();
        }
        String msg = nb(log.getMessage(), "");
        Matcher m = FILTER_CODE.matcher(msg);
        return m.find() ? m.group(1).trim() : null;
    }

    private boolean isError(LogEntry log) {
        if (Boolean.TRUE.equals(log.getIsError())) return true;
        String lvl = nb(log.getLevel(), "");
        return lvl.equalsIgnoreCase("ERROR") || lvl.equalsIgnoreCase("FATAL");
    }

    private long resolveDuration(LogEntry log) {
        if (log.getDurationMs() != null && log.getDurationMs() > 0) {
            return log.getDurationMs();
        }
        String msg = nb(log.getMessage(), "");
        Matcher m = TOOK.matcher(msg);
        long max = 0;
        while (m.find()) {
            try {
                long v = Long.parseLong(m.group(1));
                if (v > max) max = v;
            } catch (NumberFormatException ignored) { }
        }
        return max;
    }

    private static long nz(Long v) { return v == null ? 0L : v; }
    private static String nb(String v, String def) { return v == null || v.isBlank() ? def : v; }
}
