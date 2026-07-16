package com.caciopee.loganalyzer.analysis.latency;



import com.caciopee.loganalyzer.analysis.model.GraphExtraction;

import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;

import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;

import com.caciopee.loganalyzer.entity.LogEntry;

import org.springframework.stereotype.Component;



import java.util.ArrayList;

import java.util.Comparator;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Locale;

import java.util.Map;

import java.util.regex.Matcher;

import java.util.regex.Pattern;



@Component

public class WorksLatencyTimelineParser {



    private static final long GENERIC_MIN_DURATION_MS = 100L;



    private static final Pattern PREPARE_SEARCH_DETAIL = Pattern.compile(

            "prepareSearchByRoot\\s*(\\[[^\\]]+]|[^|]+?)(?:\\s*\\||\\s+took|$)",

            Pattern.CASE_INSENSITIVE);



    /**
     * Formats WORKS reconnus (alignés sur {@link LogPatternExtractor}) :
     * took [N] ms | took N ms | took N(ms) | took (ms) = N
     */
    private static final Pattern GENERIC_TOOK = Pattern.compile(

            "(.*?)\\s*:?\\s*took\\s*(?:\\[(\\d{1,9})]|(\\d{1,9}))\\s*(?:\\(\\s*ms\\s*\\)|ms)"

                    + "|(.*?)\\s*:?\\s*took\\s*\\(\\s*ms\\s*\\)\\s*=\\s*(\\d{1,9})",

            Pattern.CASE_INSENSITIVE);



    /** Format SAVE : « total time SAVE … ; N(ms) » — absent du took classique. */
    private static final Pattern TOTAL_SAVE_TIME = Pattern.compile(

            "(total time SAVE[^;]{0,200})\\s*;\\s*(\\d{1,9})\\s*\\(\\s*ms\\s*\\)",

            Pattern.CASE_INSENSITIVE);



    private static final Pattern MULTITHREAD_ELEMENTS = Pattern.compile(

            "(?:Start doSearch|doSearch)\\s*(?:\\(using multiThreading\\)\\s*)?for\\s+(\\d+)\\s+element",

            Pattern.CASE_INSENSITIVE);



    private static final Pattern THREAD_PROGRESS = Pattern.compile(

            "(?:-->\\s*|thread\\s+)(\\d+)\\s*/\\s*(\\d+)",

            Pattern.CASE_INSENSITIVE);



    private static final Pattern ROW_PIPE_PATTERN = Pattern.compile(

            "took\\s*\\[\\d{1,9}]\\s*ms\\|(\\d{1,6})\\s*row",

            Pattern.CASE_INSENSITIVE);



    private static final Pattern ROW_AFTER_PIPE = Pattern.compile(

            "\\|\\s*(\\d{1,6})\\s*row\\b",

            Pattern.CASE_INSENSITIVE);



    private static final Pattern ROW_FETCHED_PATTERN = Pattern.compile(

            "\\[(\\d{1,6})]\\s*row\\s*fetched",

            Pattern.CASE_INSENSITIVE);



    private static final Pattern MEMORY_OPERATION = Pattern.compile(

            "memory usage \\(Mo\\)\\s*\\[([^\\]]+)]\\s*(\\d{1,6})",

            Pattern.CASE_INSENSITIVE);



    private static final Pattern SEARCH_ATTR_DURATION = Pattern.compile(

            "searchAttributesList.*?\\[Query]\\s*took\\s*\\[(\\d{1,9})]",

            Pattern.CASE_INSENSITIVE);



    private static final Pattern LOAD_CHILDREN_B2 = Pattern.compile(

            "loadListChilds\\s*\\[Query B1 \\+ InsertTemp \\+\\s+Query B2].*?took\\s*\\[(\\d{1,9})]",

            Pattern.CASE_INSENSITIVE);



    private static final Pattern NOISE_OPERATION = Pattern.compile(

            "savetempcomposantsloaded|saveprocesscontent|insertarray",

            Pattern.CASE_INSENSITIVE);



    private final LogPatternExtractor logPatternExtractor;

    private final LatencyOperationClassifier classifier;



    public WorksLatencyTimelineParser(LogPatternExtractor logPatternExtractor,

                                      LatencyOperationClassifier classifier) {

        this.logPatternExtractor = logPatternExtractor;

        this.classifier = classifier;

    }



    public List<LatencyTimelineStepDto> parseTimeline(List<LogEntry> logs) {

        List<LatencyTimelineStepDto> raw = new ArrayList<>();

        Map<String, Integer> memoryByOperation = new LinkedHashMap<>();



        for (LogEntry log : logs) {

            collectMemoryMarkers(log, memoryByOperation);

            parseStep(log).ifPresent(raw::add);

        }



        attachMemory(raw, memoryByOperation);

        return deduplicateAndOrder(raw);

    }



    private java.util.Optional<LatencyTimelineStepDto> parseStep(LogEntry log) {

        String msg = combine(log);

        String lower = msg.toLowerCase(Locale.ROOT);

        GraphExtraction ex = logPatternExtractor.extractGraph(log);

        Integer rowCount = extractRowCount(msg, ex);



        java.util.Optional<LatencyTimelineStepDto> works = parseWorksStep(log, msg, lower, ex, rowCount);

        if (works.isPresent()) {

            return works;

        }

        return parseGenericTimedStep(log, msg, ex, rowCount);

    }



    private java.util.Optional<LatencyTimelineStepDto> parseWorksStep(LogEntry log, String msg,

                                                                      String lower, GraphExtraction ex,

                                                                      Integer rowCount) {

        if (lower.contains("preparesearchbyroot") && lower.contains("took")) {

            Long duration = resolveDuration(log, ex);

            if (duration <= 0) {

                return java.util.Optional.empty();

            }

            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.ROOT_QUERY,

                    resolvePrepareLabel(msg), duration, rowCount, null));

        }



        if (lower.contains("global searchcomposantbyroot") && lower.contains("took")) {

            Long duration = resolveDuration(log, ex);

            String label = lower.contains("multithreading")

                    ? "global searchComposantByRoot (Multithreading)"

                    : "global searchComposantByRoot";

            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.GLOBAL_CONTAINER,

                    label, duration, rowCount, null));

        }



        if (lower.contains("end partitional searchcomposantbyroot")) {

            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.PARTITIONAL,

                    "end partitional searchComposantByRoot", resolveDuration(log, ex), rowCount,

                    extractThreadProgress(msg)));

        }



        if (lower.contains("loadoperationbyid") && lower.contains("took")) {
            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.OPERATION_LOAD,
                    "loadOperationById", resolveDuration(log, ex), rowCount, null));
        }

        if (lower.contains("loadoperationcontext") && lower.contains("took")) {
            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.OPERATION_LOAD,
                    "loadOperationContext", resolveDuration(log, ex), rowCount, null));
        }

        if (lower.contains("loadlistchilds") && lower.contains("[total]") && lower.contains("took")) {

            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.CHILD_LOAD,

                    "loadListChilds [total]", resolveDuration(log, ex), rowCount,

                    extractThreadProgress(msg)));

        }



        if (lower.contains("searchattributeslist") && lower.contains("[query]") && lower.contains("took")) {

            Long duration = extractLong(SEARCH_ATTR_DURATION, msg);

            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.CHILD_QUERY,

                    "searchAttributesList [Query]", duration, rowCount, null));

        }



        if (lower.contains("loadlistchilds") && lower.contains("query b2") && lower.contains("took")) {

            Long duration = extractLong(LOAD_CHILDREN_B2, msg);

            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.CHILD_LOAD_B2,

                    "loadListChilds [Query B2]", duration, rowCount, extractThreadProgress(msg)));

        }



        if (lower.contains("start dosearch")) {

            String elements = extractFirst(MULTITHREAD_ELEMENTS, msg);

            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.MULTITHREAD_START,

                    "Start doSearch", null, parseInt(elements),

                    elements != null ? elements + " élément(s)" : null));

        }



        if (lower.contains("ignore multithreading")) {

            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.MULTITHREAD_CONFIG,

                    "multithreading ignoré (1 id)", null, 1, "pas de parallélisation"));

        }



        if (lower.contains("rendering result") && lower.contains("took")) {

            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.RENDERING,

                    "rendering result", resolveDuration(log, ex), rowCount, null));

        }



        if (lower.contains("query:") && !lower.contains("preparesearchbyroot")) {

            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.SQL_QUERY_TEXT,

                    "Requête SQL (texte)", null, null, "jointures détectées"));

        }



        if (lower.contains("searchcomposantbyroot start execute")) {

            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.SEARCH_START,

                    "searchComposantByRoot start", null, null, null));

        }



        if (lower.contains("searchcomposantbyroot end execute")) {

            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.SEARCH_END,

                    "searchComposantByRoot end", resolveDuration(log, ex), rowCount, null));

        }

        // doAction / règles / Transit : toujours des étapes (durée si took présent).
        // Ne pas dépendre uniquement de parseGenericTimedStep (casse sur rawLog avec |||).
        if (lower.contains("doaction")) {
            // durationMs DB compte même si le texte « took » est tronqué / hors message.
            Long duration = positiveOrNull(resolveDuration(log, ex));
            String label = resolveDoActionLabel(msg);
            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.BUSINESS_ACTION,
                    label, duration, rowCount, null));
        }

        if (lower.contains("running rules") || lower.contains("start fire rules")
                || lower.contains("end fire rules") || lower.contains("fire rules")) {
            Long duration = positiveOrNull(resolveDuration(log, ex));
            String label = lower.contains("start fire rules") ? "START fire rules"
                    : (lower.contains("end fire rules") ? "END fire rules"
                    : (lower.contains("running rules") ? "running rules" : "fire rules"));
            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.RULES_ENGINE,
                    label, duration, rowCount, null));
        }

        if (lower.contains("transit task") || lower.contains("start transit")
                || lower.contains("persist operation") || lower.contains("jbpm")) {
            Long duration = positiveOrNull(resolveDuration(log, ex));
            String label = lower.contains("transit task") ? "Transit task"
                    : (lower.contains("start transit") ? "Start transit"
                    : (lower.contains("persist") ? "persist operation" : "JBPM"));
            return java.util.Optional.of(buildStep(log, ex, LatencyOperationClassifier.WORKFLOW,
                    label, duration, rowCount, null));
        }

        return java.util.Optional.empty();

    }



    private java.util.Optional<LatencyTimelineStepDto> parseGenericTimedStep(LogEntry log, String msg,

                                                                             GraphExtraction ex,

                                                                             Integer rowCount) {

        String payload = extractTimedPayload(log, msg);

        // 1) Formats took classiques (brackets / N ms / N(ms) / took (ms) = N)
        Matcher matcher = GENERIC_TOOK.matcher(payload);
        String operationRaw = null;
        Long parsedDuration = null;
        if (matcher.find()) {
            operationRaw = firstNonBlank(matcher.group(1), matcher.group(4));
            parsedDuration = parseLongGroup(matcher, 2, 3, 5);
        }

        // 2) Format SAVE : « total time SAVE … ; N(ms) »
        if (parsedDuration == null) {
            Matcher save = TOTAL_SAVE_TIME.matcher(payload);
            if (save.find()) {
                operationRaw = save.group(1);
                parsedDuration = parseLongGroup(save, 2);
            }
        }

        if (parsedDuration == null) {
            // Dernier recours uniquement si le message évoque clairement une durée
            // (évite de créer une étape pour tout log ayant duration_ms en base).
            String lowerPayload = payload.toLowerCase(Locale.ROOT);
            boolean looksTimed = lowerPayload.contains("took")
                    || lowerPayload.contains("total time save")
                    || lowerPayload.contains("(ms)");
            if (!looksTimed) {
                return java.util.Optional.empty();
            }
            long fromDb = resolveDuration(log, ex);
            if (fromDb < GENERIC_MIN_DURATION_MS) {
                return java.util.Optional.empty();
            }
            operationRaw = shorten(payload, 120);
            parsedDuration = fromDb;
        }

        long duration = resolveDuration(log, ex);
        if (duration <= 0) {
            duration = parsedDuration;
        }

        if (duration < GENERIC_MIN_DURATION_MS) {
            return java.util.Optional.empty();
        }

        String operation = normalizeOperationName(operationRaw != null ? operationRaw : payload, payload);
        if (operation == null || operation.isBlank() || NOISE_OPERATION.matcher(operation).find()) {
            return java.util.Optional.empty();
        }

        String stepType = classifier.classify(operation, payload);
        if (LatencyOperationClassifier.SEARCH_START.equals(stepType)
                || LatencyOperationClassifier.SQL_QUERY_TEXT.equals(stepType)) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(buildStep(log, ex, stepType, operation, duration, rowCount, null));
    }

    private static Long parseLongGroup(Matcher matcher, int... groups) {
        for (int g : groups) {
            if (g <= matcher.groupCount()) {
                String v = matcher.group(g);
                if (v != null && !v.isBlank()) {
                    try {
                        return Long.parseLong(v.trim());
                    } catch (NumberFormatException ignored) {
                        // try next group
                    }
                }
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }



    /**
     * Extrait le texte utile pour un took générique.
     * Les lignes WORKS stockent souvent {@code message} = texte métier et {@code rawLog}
     * avec un suffixe {@code |||env|server…}. Prendre aveuglément l'après-{@code |||}
     * supprimait le took (doAction / running rules) → timeline vide.
     */
    private String extractTimedPayload(LogEntry log, String combined) {
        String message = log != null ? safe(log.getMessage()).trim() : "";
        if (!message.isBlank() && message.toLowerCase(Locale.ROOT).contains("took")) {
            return message;
        }
        return extractMessagePayload(combined);
    }

    private String extractMessagePayload(String msg) {
        if (msg == null || msg.isBlank()) {
            return "";
        }
        String trimmed = msg.trim();
        int pipeIdx = trimmed.lastIndexOf("|||");
        if (pipeIdx < 0) {
            return trimmed;
        }
        String after = trimmed.substring(pipeIdx + 3).trim();
        String before = trimmed.substring(0, pipeIdx).trim();
        String afterLower = after.toLowerCase(Locale.ROOT);
        if (afterLower.contains("took")
                || afterLower.contains("doaction")
                || afterLower.contains("running rules")
                || afterLower.contains("fire rules")
                || afterLower.contains("searchcomposant")
                || afterLower.contains("preparesearch")) {
            return after;
        }
        if (!before.isBlank()) {
            return before;
        }
        return after;
    }

    private String resolveDoActionLabel(String msg) {
        Matcher m = Pattern.compile(
                "doAction for - actionName\\s*:\\s*([^,]+)",
                Pattern.CASE_INSENSITIVE).matcher(safe(msg));
        if (m.find()) {
            String action = m.group(1).replaceAll("(?i)#icon:[^#]*#", "").trim();
            if (!action.isBlank()) {
                return "doAction " + action;
            }
        }
        return "doAction";
    }



    private String normalizeOperationName(String raw, String payload) {

        String cleaned = raw.replaceAll("\\s+", " ").trim();

        if (cleaned.endsWith(":")) {

            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();

        }



        String lower = (cleaned + " " + payload).toLowerCase(Locale.ROOT);

        if (lower.contains("running rules") && lower.contains("in host")) {

            return "running rules (in host)";

        }

        if (lower.contains("loadfilsoperation")) {

            return "loadFilsOperation";

        }

        if (lower.contains("loadoperationbyid")) {

            return "loadOperationById";

        }



        return cleaned.length() > 120 ? cleaned.substring(0, 117) + "..." : cleaned;

    }



    private LatencyTimelineStepDto buildStep(LogEntry log, GraphExtraction ex, String type,

                                             String operation, Long durationMs, Integer rowCount,

                                             String threadInfo) {

        LatencyTimelineStepDto step = new LatencyTimelineStepDto();

        step.setStepType(type);

        step.setOperationName(operation);

        Long effectiveDuration = durationMs != null && durationMs > 0 ? durationMs : null;

        step.setDurationMs(effectiveDuration);

        if (LatencyPlausibility.isImplausible(effectiveDuration)) {

            step.setSuspect(true);

            step.setSuspectReason(LatencyPlausibility.reason(effectiveDuration));

        }

        step.setRowCount(rowCount);

        step.setMemoryMo(ex.getMemoryMo());

        step.setThreadInfo(threadInfo);

        step.setLogId(log.getId());

        step.setTimestamp(log.getLogTimestamp());

        step.setDetail(shorten(combine(log), 280));

        return step;

    }



    private Integer extractRowCount(String msg, GraphExtraction ex) {

        if (ex.getRowCount() != null) {

            return ex.getRowCount();

        }

        Long pipe = extractLong(ROW_PIPE_PATTERN, msg);

        if (pipe != null) {

            return pipe.intValue();

        }

        Long afterPipe = extractLong(ROW_AFTER_PIPE, msg);

        if (afterPipe != null) {

            return afterPipe.intValue();

        }

        Long fetched = extractLong(ROW_FETCHED_PATTERN, msg);

        return fetched != null ? fetched.intValue() : null;

    }



    private void collectMemoryMarkers(LogEntry log, Map<String, Integer> memoryByOperation) {

        Matcher matcher = MEMORY_OPERATION.matcher(safe(log.getMessage()));

        if (matcher.find()) {

            memoryByOperation.put(normalizeMemoryKey(matcher.group(1)), Integer.parseInt(matcher.group(2)));

        }

    }



    private void attachMemory(List<LatencyTimelineStepDto> steps, Map<String, Integer> memoryByOperation) {

        for (LatencyTimelineStepDto step : steps) {

            if (step.getMemoryMo() != null) {

                continue;

            }

            String opKey = step.getOperationName() != null

                    ? step.getOperationName().toLowerCase(Locale.ROOT) : "";

            for (Map.Entry<String, Integer> entry : memoryByOperation.entrySet()) {

                String memKey = entry.getKey();

                if (memKey.contains("preparesearchbyroot") && opKey.contains("preparesearchbyroot")

                        || memKey.contains("searchattributeslist") && opKey.contains("searchattributeslist")

                        || memKey.contains("loadlistchilds") && opKey.contains("loadlistchilds")) {

                    step.setMemoryMo(entry.getValue());

                    break;

                }

            }

        }

    }



    private List<LatencyTimelineStepDto> deduplicateAndOrder(List<LatencyTimelineStepDto> raw) {

        Map<String, LatencyTimelineStepDto> bestByKey = new LinkedHashMap<>();

        for (LatencyTimelineStepDto step : raw) {

            String key = dedupeKey(step);

            LatencyTimelineStepDto existing = bestByKey.get(key);

            if (existing == null || better(step, existing)) {

                bestByKey.put(key, step);

            }

        }



        List<LatencyTimelineStepDto> ordered = new ArrayList<>(bestByKey.values());

        ordered.sort(Comparator

                .comparing(LatencyTimelineStepDto::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder()))

                .thenComparing(s -> s.getLogId() != null ? s.getLogId() : 0L));



        int order = 1;

        for (LatencyTimelineStepDto step : ordered) {

            step.setStepOrder(order++);

        }

        return ordered;

    }

    /**
     * Fusionne les doublons SAVE / validate / persist de même durée (souvent 3 lignes
     * quasi identiques pour le même SAVE ServicePortuaireCont).
     */
    private String dedupeKey(LatencyTimelineStepDto step) {
        if (step.getLogId() != null) {
            // Même log → même étape ; pour SAVE on regroupe aussi par durée + famille.
            String op = safe(step.getOperationName()).toLowerCase(Locale.ROOT);
            if (isSaveFamily(op) && step.getDurationMs() != null) {
                return "save-family|" + step.getDurationMs();
            }
            return "log:" + step.getLogId();
        }
        String op = safe(step.getOperationName()).toLowerCase(Locale.ROOT);
        if (isSaveFamily(op) && step.getDurationMs() != null) {
            return "save-family|" + step.getDurationMs();
        }
        return step.getStepType() + "|" + safe(step.getOperationName())
                + "|" + (step.getDurationMs() != null ? step.getDurationMs() : 0);
    }

    private static boolean isSaveFamily(String opLower) {
        return opLower.contains("total time save")
                || opLower.contains("saveorupdate")
                || (opLower.contains("persist") && opLower.contains("operation"))
                || (opLower.contains("validate") && opLower.contains("save"));
    }



    private boolean better(LatencyTimelineStepDto candidate, LatencyTimelineStepDto existing) {

        long cDur = candidate.getDurationMs() != null ? candidate.getDurationMs() : 0L;

        long eDur = existing.getDurationMs() != null ? existing.getDurationMs() : 0L;

        if (cDur != eDur) {

            return cDur > eDur;

        }

        int cMem = candidate.getMemoryMo() != null ? candidate.getMemoryMo() : 0;

        int eMem = existing.getMemoryMo() != null ? existing.getMemoryMo() : 0;

        return cMem > eMem;

    }



    private String resolvePrepareLabel(String msg) {

        Matcher matcher = PREPARE_SEARCH_DETAIL.matcher(msg);

        if (matcher.find()) {

            return "prepareSearchByRoot " + matcher.group(1).trim();

        }

        return "prepareSearchByRoot [Query Select]";

    }



    private long resolveDuration(LogEntry log, GraphExtraction ex) {

        if (ex.getDurationMs() != null && ex.getDurationMs() > 0) {

            return ex.getDurationMs();

        }

        if (log.getDurationMs() != null && log.getDurationMs() > 0) {

            return log.getDurationMs();

        }

        Long parsed = logPatternExtractor.extractDurationMsFromText(

                log.getMessage(), log.getRawLog(), log.getProcessName());

        return parsed != null ? parsed : 0L;

    }

    private static Long positiveOrNull(long durationMs) {
        return durationMs > 0 ? durationMs : null;
    }



    private Long extractLong(Pattern pattern, String text) {

        Matcher matcher = pattern.matcher(text);

        if (!matcher.find()) {

            return null;

        }

        for (int i = 1; i <= matcher.groupCount(); i++) {

            String group = matcher.group(i);

            if (group != null && !group.isBlank()) {

                try {

                    return Long.parseLong(group.trim());

                } catch (NumberFormatException ignored) {

                    // try next group

                }

            }

        }

        return null;

    }



    private String extractThreadProgress(String msg) {

        Matcher matcher = THREAD_PROGRESS.matcher(msg);

        return matcher.find() ? matcher.group(1) + " / " + matcher.group(2) : null;

    }



    private String normalizeMemoryKey(String value) {

        return safe(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");

    }



    private Integer parseInt(String value) {

        if (value == null || value.isBlank()) {

            return null;

        }

        try {

            return Integer.parseInt(value.trim());

        } catch (NumberFormatException e) {

            return null;

        }

    }



    private String extractFirst(Pattern pattern, String text) {

        Matcher matcher = pattern.matcher(text);

        return matcher.find() ? matcher.group(1) : null;

    }



    private String combine(LogEntry log) {

        return safe(log.getMessage()) + " " + safe(log.getRawLog());

    }



    private String safe(String value) {

        return value != null ? value : "";

    }



    private String shorten(String value, int max) {

        String cleaned = value.replaceAll("\\s+", " ").trim();

        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max - 3) + "...";

    }



    public String buildChronologicalText(List<LogEntry> logs) {

        StringBuilder sb = new StringBuilder();

        for (LogEntry log : logs) {

            String ts = log.getLogTimestamp() != null ? log.getLogTimestamp().toString() : "N/A";

            String msg = shorten(safe(log.getMessage()), 800);

            if (msg.isBlank()) {

                msg = shorten(safe(log.getRawLog()), 800);

            }

            sb.append(ts).append(" — ").append(msg).append('\n');

        }

        return sb.toString().trim();

    }

}


