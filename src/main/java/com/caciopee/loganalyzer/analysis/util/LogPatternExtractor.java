package com.caciopee.loganalyzer.analysis.util;

import com.caciopee.loganalyzer.analysis.model.ExtractedLogContext;
import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.entity.LogEntry;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LogPatternExtractor {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("\\buuid\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern TRANSACTION_PATTERN =
            Pattern.compile("transactionId\\s*[\\[(]\\s*([^\\])]+?)\\s*[\\])]", Pattern.CASE_INSENSITIVE);

    private static final Pattern FILTER_CODE_PATTERN =
            Pattern.compile("filter code\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern FETCHING_FILTER_PATTERN =
            Pattern.compile("fetching filter\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern THREAD_PATTERN =
            Pattern.compile("thread name\\s*\\[?([^,\\]|]+?)]?", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLASSNAME_BRACKET_PATTERN =
            Pattern.compile("className\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLASSNAME_LOOSE_PATTERN =
            Pattern.compile("className\\s+([A-Za-z][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern ROW_COUNT_PATTERN =
            Pattern.compile("(?:\\[(\\d{1,6})]\\s*row\\s*fetched|\\b(\\d{1,6})\\s*row\\b)", Pattern.CASE_INSENSITIVE);

    private static final Pattern TOOK_PATTERN =
            Pattern.compile("(?:took\\s*\\[(\\d{1,9})]\\s*ms|took\\s*(\\d{1,9})\\s*ms|took\\s*\\(\\s*ms\\s*\\)\\s*=\\s*(\\d{1,9})|took\\s*(\\d{1,9})\\s*\\(\\s*ms\\s*\\))",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern START_FILTRE_PATTERN =
            Pattern.compile("Start Filtre\\s+([A-Za-z0-9_]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WS_BEFORE_START_FILTRE_PATTERN =
            Pattern.compile("([A-Za-z0-9_]+)\\s+Start Filtre\\s", Pattern.CASE_INSENSITIVE);

    private static final Pattern END_FILTRE_PATTERN =
            Pattern.compile("End Filtre\\s+([A-Za-z0-9_]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern START_WS_WORKFLOW_PATTERN =
            Pattern.compile("Start\\s+(WS_[A-Za-z0-9_]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern END_WS_TASK_PATTERN =
            Pattern.compile("End\\s+(WS_[A-Z0-9_]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WS_NAME_PATTERN =
            Pattern.compile("\\b(WS_[A-Z0-9_]+)\\b");

    private static final Pattern MESSAGE_END_VERB_PATTERN =
            Pattern.compile("memory usage \\(Mo\\)\\s*\\[end\\s+([a-zA-Z][a-zA-Z0-9_.]*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern MESSAGE_LEADING_VERB_PATTERN =
            Pattern.compile("^\\s*([a-zA-Z][a-zA-Z0-9_.]*)\\s*\\[", Pattern.CASE_INSENSITIVE);

    private static final Pattern END_OPERATION_PATTERN =
            Pattern.compile("\\bEnd\\s+([a-zA-Z][a-zA-Z0-9_.]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern GLOBAL_SEARCH_VERB_PATTERN =
            Pattern.compile("\\bglobal\\s+(search[A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern SEARCH_EXECUTE_VERB_PATTERN =
            Pattern.compile("\\b(search[A-Za-z][a-zA-Z0-9_]*)\\s+end\\s+execute", Pattern.CASE_INSENSITIVE);

    private static final Pattern EMBEDDED_OPERATION_PATTERN =
            Pattern.compile("\\b(prepareSearchByRoot|loadListChilds|doSearch|controlSelection|searchComposantByRoot|saveTempComposantsLoaded|saveRelOperationsComposants)\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern MESSAGE_ARROW_VERB_PATTERN =
            Pattern.compile("^\\s*([a-zA-Z][a-zA-Z0-9_]*)\\s*==>", Pattern.CASE_INSENSITIVE);

    private static final Pattern PROCESS_HYPHEN_COLUMN_PREFIX =
            Pattern.compile("^process[A-Za-z]+-\\d+-");

    private static final Pattern STRUCTURE_STEP_PATTERN =
            Pattern.compile("StructureDataInterface\\.([a-zA-Z]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WORKS_FIELD_PATTERN =
            Pattern.compile("works\\s*=\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern INTERFACE_FIELD_PATTERN =
            Pattern.compile("interface\\s*=\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern FIELD_NAME_PATTERN =
            Pattern.compile("field\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern PUT_KEY_PATTERN =
            Pattern.compile("put key\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern RELATION_NAME_PATTERN =
            Pattern.compile("relation\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern FIELD_CLASS_CODE_PATTERN =
            Pattern.compile("\\{fieldClassCode\\}\\s*=\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern STRUCTURE_METHOD_PATTERN =
            Pattern.compile("(?:START|END)\\s*\\[StrctureDatasInterface\\.jar/structureData/StructureDataInterface\\.([a-zA-Z]+)]",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern MEMORY_PATTERN =
            Pattern.compile("memory usage \\(Mo\\).*?\\s(\\d{1,6})\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern TASK_BRACKET_PATTERN =
            Pattern.compile("taskName\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern TASK_PARAM_PATTERN =
            Pattern.compile("taskName\\s*=\\s*([^,}\\s]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern ACTION_BRACKET_PATTERN =
            Pattern.compile("actionName\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern ACTION_NAME_BRACKET_PATTERN =
            Pattern.compile("ACTION_NAME\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern ACTION_COLON_PATTERN =
            Pattern.compile("actionName\\s*:\\s*([^,|]+?)(?=\\s*,\\s*-|\\s*\\||$)", Pattern.CASE_INSENSITIVE);

    private static final Pattern PROCESS_BRACKET_PATTERN =
            Pattern.compile("processName\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    private static final Pattern TRANSITION_PATTERN =
            Pattern.compile("transition\\s*[=:]\\s*([^,}\\s|]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern IP_PATTERN =
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    private static final Pattern TRIGGER_PATTERN =
            Pattern.compile("\\b(Trigger[A-Za-z0-9_ éèêàç\\-]+?)\\s+(was fired|is complete|starts)", Pattern.CASE_INSENSITIVE);

    private static final Pattern CHECKPOINT_PATTERN =
            Pattern.compile("(?:trCheckPoint\\s*:?\\s*|-+\\s*)?(ENTREE[_\\s][A-Z_\\s]+|SORTIE[_\\s][A-Z_\\s]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern BUSINESS_PAIR_PATTERN =
            Pattern.compile("\\b(noDUM|noUnite|noService|amp|ampe|refBad|refDemande|codeSecu|dateAction|orderState|sensActivite|nomService|statut|typeTr|notes|serviceCourant)\\s*(?:=|:|::|-+)\\s*\\[?([^\\]|,\\r\\n]+?)\\]?",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern QUERY_PARAM_PATTERN =
            Pattern.compile("\\b(wv[A-Za-z0-9_]+|w[A-Za-z0-9_]+|f[A-Za-z0-9_]+)\\s*=\\[([^\\]]+)]",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PROCESS_DOT_SUFFIX_PATTERN =
            Pattern.compile("^(.+?)(?:---|-\\d+.*)?$", Pattern.CASE_INSENSITIVE);

    public Long extractDurationMsFromText(String message, String rawLog, String processName) {
        String all = safe(message) + " " + safe(rawLog) + " " + safe(processName);
        return extractLong(TOOK_PATTERN, all);
    }

    public ExtractedLogContext extract(LogEntry log) {
        ExtractedLogContext ctx = new ExtractedLogContext();

        String message = safe(log != null ? log.getMessage() : null);
        String processColumn = safe(log != null ? log.getProcessName() : null);
        String rawLog = safe(log != null ? log.getRawLog() : null);
        String all = message + " " + rawLog + " " + processColumn;

        ctx.setExtractedUuid(extractFirst(UUID_PATTERN, all));
        ctx.setExtractedTransactionId(extractFirst(TRANSACTION_PATTERN, all));
        ctx.setExtractedFilterCode(extractFirst(FILTER_CODE_PATTERN, all));
        if (ctx.getExtractedFilterCode() == null) {
            ctx.setExtractedFilterCode(extractFirst(FETCHING_FILTER_PATTERN, all));
        }
        ctx.setExtractedThreadName(extractFirst(THREAD_PATTERN, all));
        ctx.setExtractedClassName(extractClassName(all));

        ctx.setExtractedRowCount(extractInteger(ROW_COUNT_PATTERN, all));
        ctx.setExtractedDurationMs(extractLong(TOOK_PATTERN, all));
        ctx.setExtractedMemoryMo(extractInteger(MEMORY_PATTERN, message));

        ctx.setExtractedTaskName(firstNonBlank(
                extractFirst(TASK_BRACKET_PATTERN, all),
                extractFirst(TASK_PARAM_PATTERN, all)
        ));
        ctx.setExtractedActionName(firstNonBlank(
                extractFirst(ACTION_BRACKET_PATTERN, all),
                extractFirst(ACTION_COLON_PATTERN, all),
                extractFirst(TRANSITION_PATTERN, all)
        ));
        ctx.setExtractedProcessName(extractFirst(PROCESS_BRACKET_PATTERN, all));
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

        parseProcessColumn(processColumn, ctx, message);
        parseMessagePipeSegments(message, ctx, processColumn);
        enrichFromProcessLogMessage(message, ctx);
        enrichFromTomcatStructureMessage(message, ctx);

        if (ctx.getExtractedProcessName() == null && notBlank(processColumn)) {
            if (isProcessHyphenColumn(processColumn)) {
                ctx.setExtractedProcessName(extractProcessHyphenRootName(processColumn));
            } else {
                ctx.setExtractedProcessName(clean(processColumn));
            }
        }

        return ctx;
    }

    public GraphExtraction extractGraph(LogEntry log) {
        ExtractedLogContext ctx = extract(log);
        GraphExtraction graph = new GraphExtraction();

        String message = safe(log != null ? log.getMessage() : null);
        String level = safe(log != null ? log.getLevel() : null);
        String processColumn = safe(log != null ? log.getProcessName() : null);

        graph.setProcess(resolveProcess(ctx, processColumn));
        graph.setTask(clean(ctx.getExtractedTaskName()));
        graph.setAction(resolveAction(ctx, message));
        graph.setFilter(resolveFilter(ctx, message, processColumn));
        graph.setObject(resolveObject(ctx, log));
        graph.setUuid(clean(ctx.getExtractedUuid()));
        graph.setTransactionId(clean(ctx.getExtractedTransactionId()));

        String msgLower = message.toLowerCase(Locale.ROOT);
        graph.setWarning("WARN".equalsIgnoreCase(level)
                || msgLower.contains("no rule found")
                || msgLower.contains("aucune r"));
        graph.setZeroResult(msgLower.contains("0 row") || msgLower.contains("[0] row fetched"));
        graph.setError(Boolean.TRUE.equals(log != null ? log.getIsError() : null)
                || "ERROR".equalsIgnoreCase(level));
        graph.setSave(msgLower.contains("saveprocesscontent")
                || msgLower.contains("insertarray")
                || msgLower.contains("removeobjectstodelete")
                || msgLower.contains("insertion reussie")
                || msgLower.contains("insertion réussie")
                || (msgLower.contains("save") && !msgLower.contains("saveprocesscontent=false")));

        graph.setDurationMs(ctx.getExtractedDurationMs());
        graph.setMemoryMo(ctx.getExtractedMemoryMo());
        graph.setRowCount(ctx.getExtractedRowCount());

        applyColumnAndContextFallbacks(graph, processColumn, message, ctx);
        sanitizeGraphExtraction(graph, processColumn, message);

        return graph;
    }

    /**
     * Dédoublonne process / filtre / tâche / action pour éviter les nœuds graphe confus
     * (ex. filtre = nom du process sans {@code filter code [...]} dans le log).
     */
    private void sanitizeGraphExtraction(GraphExtraction graph, String processColumn, String message) {
        if (graph == null) {
            return;
        }

        String process = clean(graph.getProcess());
        String filter = clean(graph.getFilter());
        String task = clean(graph.getTask());
        String action = clean(graph.getAction());

        if (notBlank(filter) && isRedundantWithProcess(filter, process)) {
            graph.setFilter(null);
            filter = null;
        }

        if (notBlank(filter) && !filterHasSemanticEvidence(filter, processColumn, message)) {
            graph.setFilter(null);
            filter = null;
        }

        if (notBlank(task) && isRedundantWithProcess(task, process)) {
            graph.setTask(null);
            task = null;
        }

        if (notBlank(task) && isLikelyActionToken(task) && isProcessFilterColumn(processColumn)) {
            graph.setTask(null);
            task = null;
        }

        if (notBlank(task) && notBlank(action) && namesEqual(task, action)) {
            graph.setTask(null);
            task = null;
        }

        if (notBlank(task) && notBlank(filter) && namesEqual(task, filter)) {
            if (isProcessFilterColumn(processColumn)) {
                String middle = middleSegmentForProcessFilter(processColumn);
                if (notBlank(middle) && namesEqual(task, middle)) {
                    graph.setTask(null);
                }
            } else if (!filter.toUpperCase(Locale.ROOT).startsWith("WS_")) {
                graph.setFilter(null);
                filter = clean(graph.getFilter());
            }
        }

        if (notBlank(action) && notBlank(process) && namesEqual(action, process)) {
            graph.setAction(null);
        }
    }

    private boolean filterHasSemanticEvidence(String filter, String processColumn, String message) {
        if (!notBlank(filter)) {
            return false;
        }
        if (notBlank(message)) {
            if (notBlank(extractFirst(FILTER_CODE_PATTERN, message))
                    || notBlank(extractFirst(FETCHING_FILTER_PATTERN, message))
                    || notBlank(extractFirst(START_FILTRE_PATTERN, message))
                    || notBlank(extractFirst(END_FILTRE_PATTERN, message))) {
                return true;
            }
            if (containsIgnoreCase(message, "structuredata")
                    || containsIgnoreCase(message, "strcturedatasinterface")) {
                return true;
            }
        }
        if (filter.toLowerCase(Locale.ROOT).startsWith("structuredata.")) {
            return true;
        }
        if (isProcessFilterColumn(processColumn)) {
            String middle = middleSegmentForProcessFilter(processColumn);
            return notBlank(middle) && namesEqual(middle, filter);
        }
        String upper = filter.toUpperCase(Locale.ROOT);
        return upper.startsWith("WS_") || upper.contains("RULE_") || upper.endsWith(".JAVA");
    }

    private String middleSegmentForProcessFilter(String processColumn) {
        if (!isProcessFilterColumn(processColumn) || !isProcessHyphenColumn(processColumn)) {
            return null;
        }
        String[] parts = processColumn.split("-");
        return parts.length > 2 ? clean(parts[2]) : null;
    }

    private boolean isProcessFilterColumn(String processColumn) {
        return notBlank(processColumn) && processColumn.trim().toLowerCase(Locale.ROOT).startsWith("processfilter");
    }

    private boolean isRedundantWithProcess(String value, String process) {
        if (!notBlank(value) || !notBlank(process)) {
            return false;
        }
        String v = value.trim();
        String p = process.trim();
        if (namesEqual(v, p)) {
            return true;
        }
        if (v.regionMatches(true, 0, "process.", 0, 8)) {
            return namesEqual(v.substring(8).trim(), p);
        }
        return false;
    }

    private boolean namesEqual(String a, String b) {
        return notBlank(a) && notBlank(b) && a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * Complète process / task / action / filter à partir de la colonne processName
     * et du contexte message lorsque les motifs message seuls sont insuffisants.
     */
    private void applyColumnAndContextFallbacks(GraphExtraction graph,
                                                String processColumn,
                                                String message,
                                                ExtractedLogContext ctx) {
        if (!notBlank(graph.getAction())) {
            graph.setAction(resolveActionFromColumn(processColumn));
        }
        if (!notBlank(graph.getAction())) {
            graph.setAction(resolveActionFromMessage(message, processColumn));
        }
        if (!notBlank(graph.getTask())) {
            graph.setTask(resolveTaskFromColumn(processColumn, message, ctx));
        }
        if (!notBlank(graph.getTask())) {
            graph.setTask(resolveTaskFromMessage(message));
        }
        if (!notBlank(graph.getTask())) {
            graph.setTask(resolveTaskFromProcessDotColumn(processColumn));
        }
        if (!notBlank(graph.getFilter())) {
            graph.setFilter(resolveFilterWithColumnFallback(ctx, message, processColumn, graph.getTask()));
        }
        if (!notBlank(graph.getObject())) {
            String className = extractClassName(safe(message));
            if (notBlank(className)) {
                graph.setObject(className);
            }
        }
        if (!notBlank(graph.getProcess()) && notBlank(processColumn)) {
            graph.setProcess(resolveProcess(ctx, processColumn));
        }
    }

    private String resolveActionFromColumn(String processColumn) {
        if (!notBlank(processColumn)) {
            return null;
        }
        String column = processColumn.trim();
        String embedded = inferActionTokenFromColumnText(column);
        if (notBlank(embedded)) {
            return embedded;
        }
        if (isProcessHyphenColumn(column)) {
            String[] parts = column.split("-");
            if (parts.length > 4) {
                String fromIndex = normalizeActionLabel(clean(parts[4]));
                if (notBlank(fromIndex)) {
                    return fromIndex;
                }
            }
            if (parts.length > 0) {
                String last = clean(parts[parts.length - 1]);
                if (isLikelyActionToken(last)) {
                    return normalizeActionLabel(last);
                }
            }
        }
        if (column.startsWith("process.")) {
            String[] parts = column.substring("process.".length()).split("-");
            for (int i = parts.length - 1; i >= 0; i--) {
                String token = clean(parts[i]);
                if (isLikelyActionToken(token)) {
                    return normalizeActionLabel(token);
                }
                String stripped = stripNumericPrefix(token);
                if (notBlank(stripped) && isLikelyActionToken(stripped)) {
                    return normalizeActionLabel(stripped);
                }
            }
        }
        if (column.matches("-?\\d+--\\d+-([A-Za-z_]+)")) {
            Matcher m = Pattern.compile("-?\\d+--\\d+-([A-Za-z_]+)").matcher(column);
            if (m.find()) {
                return normalizeActionLabel(m.group(1));
            }
        }
        if (column.startsWith("process.")) {
            return resolveProcessDotStepAction(column);
        }
        return null;
    }

    private String resolveProcessDotStepAction(String processColumn) {
        if (!notBlank(processColumn) || !processColumn.trim().startsWith("process.")) {
            return null;
        }
        String processName = extractProcessDotColumnName(processColumn.trim());
        String rest = processColumn.trim().substring("process.".length());
        String[] parts = rest.split("-");
        for (int i = parts.length - 1; i >= 0; i--) {
            String token = clean(parts[i]);
            if (!notBlank(token) || isNumericOnly(token)) {
                continue;
            }
            String stripped = stripNumericPrefix(token);
            if (isLikelyActionToken(token) || isLikelyActionToken(stripped)) {
                return normalizeActionLabel(firstNonBlank(stripped, token));
            }
            if (processName != null && processName.equalsIgnoreCase(token)) {
                continue;
            }
            if (token.length() <= 80 && !token.contains(" ")) {
                return normalizeActionLabel(token);
            }
        }
        return null;
    }

    private String resolveActionFromMessage(String message, String processColumn) {
        if (!notBlank(message)) {
            return null;
        }

        String fromPipe = extractActionFromRunningRulesPipe(message);
        if (notBlank(fromPipe)) {
            return fromPipe;
        }

        String endVerb = extractFirst(MESSAGE_END_VERB_PATTERN, message);
        if (notBlank(endVerb)) {
            return normalizeActionLabel(endVerb);
        }

        String leadingVerb = extractFirst(MESSAGE_LEADING_VERB_PATTERN, message.trim());
        if (notBlank(leadingVerb) && !"memory".equalsIgnoreCase(leadingVerb)) {
            return normalizeActionLabel(leadingVerb);
        }

        String globalSearch = extractFirst(GLOBAL_SEARCH_VERB_PATTERN, message);
        if (notBlank(globalSearch)) {
            return normalizeActionLabel(globalSearch);
        }

        String searchExecute = extractFirst(SEARCH_EXECUTE_VERB_PATTERN, message);
        if (notBlank(searchExecute)) {
            return normalizeActionLabel(searchExecute);
        }

        String endOp = extractFirst(END_OPERATION_PATTERN, message);
        if (notBlank(endOp)) {
            String normalized = endOp.replaceFirst("(?i)\\.finally$", "");
            if (!normalized.equalsIgnoreCase("WS") && normalized.length() <= 80) {
                return normalizeActionLabel(normalized);
            }
        }

        String embedded = extractFirst(EMBEDDED_OPERATION_PATTERN, message);
        if (notBlank(embedded)) {
            return normalizeActionLabel(embedded);
        }

        String arrowVerb = extractFirst(MESSAGE_ARROW_VERB_PATTERN, message);
        if (notBlank(arrowVerb)) {
            return normalizeActionLabel(arrowVerb);
        }

        if (containsIgnoreCase(message, "fire rules")) {
            String trimmed = message.trim();
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("START")) {
                return "Start";
            }
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("END")) {
                return "End";
            }
        }

        if (notBlank(processColumn) && processColumn.trim().startsWith("process.")) {
            return resolveProcessDotStepAction(processColumn.trim());
        }

        return null;
    }

    private String extractActionFromRunningRulesPipe(String message) {
        if (!containsIgnoreCase(message, "running rules|")) {
            return null;
        }
        String[] segments = message.split("\\|");
        if (segments.length < 4) {
            return null;
        }
        String raw = clean(segments[3]);
        if (!notBlank(raw)) {
            return null;
        }
        String stripped = raw.replaceFirst("^\\d+-", "");
        return normalizeActionLabel(notBlank(stripped) ? stripped : raw);
    }

    private String resolveTaskFromColumn(String processColumn, String message, ExtractedLogContext ctx) {
        String fromCtx = clean(ctx != null ? ctx.getExtractedTaskName() : null);
        if (notBlank(fromCtx)) {
            return fromCtx;
        }
        if (!notBlank(processColumn) || !isProcessHyphenColumn(processColumn)) {
            return null;
        }
        String[] parts = processColumn.split("-");
        if (parts.length <= 2) {
            return null;
        }
        String root = clean(parts[0]);
        String middle = parts.length > 2 ? clean(parts[2]) : null;
        if ("processWebService".equalsIgnoreCase(root)) {
            String wsTask = firstNonBlank(
                    notBlank(middle) && !isNumericOnly(middle) && !isLikelyActionToken(middle) ? middle : null,
                    findSegmentForWebService(parts));
            if (notBlank(wsTask)) {
                return wsTask;
            }
        }
        if ("processRunRules".equalsIgnoreCase(root) && notBlank(middle) && isTaskNameSegment(middle)) {
            return middle;
        }
        if ("processWebService".equalsIgnoreCase(root)) {
            String wsFromMessage = extractWebServiceTaskFromMessage(message);
            if (notBlank(wsFromMessage)) {
                return wsFromMessage;
            }
        }
        String msgTask = firstNonBlank(
                extractFirst(TASK_BRACKET_PATTERN, message),
                extractFirst(TASK_PARAM_PATTERN, message));
        if (notBlank(msgTask)) {
            return msgTask;
        }
        return null;
    }

    private String resolveTaskFromProcessDotColumn(String processColumn) {
        if (!notBlank(processColumn) || !processColumn.trim().startsWith("process.")) {
            return null;
        }
        String processName = extractProcessDotColumnName(processColumn.trim());
        String rest = processColumn.trim().substring("process.".length());
        String[] parts = rest.split("-");
        for (int i = 0; i < parts.length; i++) {
            String token = clean(parts[i]);
            if (!notBlank(token) || isNumericOnly(token) || isLikelyActionToken(token)) {
                continue;
            }
            if (processName != null && processName.equalsIgnoreCase(token)) {
                continue;
            }
            if (!looksLikeTaskName(token)) {
                continue;
            }
            return token;
        }
        return null;
    }

    private boolean looksLikeTaskName(String token) {
        if (!notBlank(token)) {
            return false;
        }
        String upper = token.toUpperCase(Locale.ROOT);
        return isTaskNameSegment(token)
                || upper.startsWith("WS_")
                || upper.startsWith("WORKS_")
                || upper.endsWith("PROCESS")
                || upper.contains("LOAD_TASK");
    }

    private String extractWebServiceTaskFromMessage(String message) {
        if (!notBlank(message)) {
            return null;
        }
        return firstNonBlank(
                extractFirst(TASK_BRACKET_PATTERN, message),
                extractFirst(TASK_PARAM_PATTERN, message),
                extractFirst(WS_BEFORE_START_FILTRE_PATTERN, message),
                extractFirst(START_WS_WORKFLOW_PATTERN, message),
                extractFirst(END_WS_TASK_PATTERN, message),
                extractLongestWsNameFromMessage(message),
                extractFirst(START_FILTRE_PATTERN, message)
        );
    }

    private String extractLongestWsNameFromMessage(String message) {
        if (!notBlank(message)) {
            return null;
        }
        Matcher wsMatcher = WS_NAME_PATTERN.matcher(message);
        String best = null;
        while (wsMatcher.find()) {
            String candidate = wsMatcher.group(1);
            if (best == null || candidate.length() > best.length()) {
                best = candidate;
            }
        }
        return best;
    }

    private String resolveFilterWithColumnFallback(ExtractedLogContext ctx,
                                                   String message,
                                                   String processColumn,
                                                   String task) {
        String fromCtx = clean(ctx != null ? ctx.getExtractedFilterCode() : null);
        if (notBlank(fromCtx)) {
            return fromCtx;
        }

        String fromMessage = extractFirst(FILTER_CODE_PATTERN, message);
        if (!notBlank(fromMessage)) {
            fromMessage = extractFirst(FETCHING_FILTER_PATTERN, message);
        }
        if (notBlank(fromMessage)) {
            return fromMessage;
        }

        String startFiltre = extractFirst(START_FILTRE_PATTERN, message);
        if (notBlank(startFiltre)) {
            return startFiltre;
        }

        if (isProcessHyphenColumn(processColumn)) {
            String[] parts = processColumn.split("-");
            String root = parts.length > 0 ? clean(parts[0]) : null;
            if (parts.length > 2) {
                String middle = clean(parts[2]);
                if (notBlank(middle) && !isNumericOnly(middle)) {
                    if ("processFilter".equalsIgnoreCase(root)) {
                        return middle;
                    }
                    if ("processWebService".equalsIgnoreCase(root) && notBlank(task)) {
                        return firstNonBlank(fromMessage, middle);
                    }
                }
            }
        }

        if (notBlank(task) && task.toUpperCase(Locale.ROOT).startsWith("WS_")) {
            return task;
        }

        return null;
    }

    private String middleColumnSegment(String processColumn) {
        if (!isProcessHyphenColumn(processColumn)) {
            return null;
        }
        String[] parts = processColumn.split("-");
        if (parts.length > 0 && "processWebService".equalsIgnoreCase(clean(parts[0]))) {
            return findSegmentForWebService(parts);
        }
        return parts.length > 2 ? clean(parts[2]) : null;
    }

    private String findSegmentForWebService(String[] parts) {
        if (parts == null || parts.length < 3) {
            return null;
        }
        for (int i = 2; i < parts.length; i++) {
            String seg = clean(parts[i]);
            if (notBlank(seg) && !isNumericOnly(seg) && !isLikelyActionToken(seg)) {
                return seg;
            }
        }
        return null;
    }

    private String stripNumericPrefix(String token) {
        if (!notBlank(token)) {
            return token;
        }
        return token.replaceFirst("^\\d+-", "");
    }

    private String inferActionTokenFromColumnText(String column) {
        if (!notBlank(column)) {
            return null;
        }
        String upper = column.toUpperCase(Locale.ROOT);
        String[] known = {
                "BEFORE_LOAD", "AFTER_LOAD", "ACTION_NAME_SAVE", "CONTROLSELECTION",
                "SAVE", "LOAD", "START", "VALIDER", "ANNULER", "IMPRIMER", "AFFECTATION"
        };
        for (String token : known) {
            if (upper.contains("-" + token) || upper.endsWith(token)) {
                return normalizeActionLabel(token);
            }
        }
        return null;
    }

    private String resolveTaskFromMessage(String message) {
        if (!notBlank(message)) {
            return null;
        }
        String bracket = firstNonBlank(
                extractFirst(TASK_BRACKET_PATTERN, message),
                extractFirst(TASK_PARAM_PATTERN, message));
        if (notBlank(bracket)) {
            return bracket;
        }
        return extractLongestWsNameFromMessage(message);
    }

    private String resolveProcess(ExtractedLogContext ctx, String processColumn) {
        String fromCtx = clean(ctx.getExtractedProcessName());
        if (notBlank(fromCtx) && !"null".equalsIgnoreCase(fromCtx)) {
            return fromCtx;
        }

        if (processColumn.startsWith("process.")) {
            return extractProcessDotColumnName(processColumn);
        }

        if (isProcessHyphenColumn(processColumn)) {
            return extractProcessHyphenRootName(processColumn);
        }

        return clean(processColumn);
    }

    private String resolveAction(ExtractedLogContext ctx, String message) {
        String action = clean(ctx.getExtractedActionName());
        if (notBlank(action) && !"null".equalsIgnoreCase(action)) {
            return normalizeActionLabel(action);
        }

        String actionBracket = firstNonBlank(
                extractFirst(ACTION_BRACKET_PATTERN, message),
                extractFirst(ACTION_NAME_BRACKET_PATTERN, message));
        if (notBlank(actionBracket)) {
            return normalizeActionLabel(actionBracket);
        }

        if (message.contains("doAction for")) {
            String transition = extractFirst(TRANSITION_PATTERN, message);
            if (notBlank(transition)) {
                return normalizeActionLabel(transition);
            }
        }

        return resolveActionFromMessage(message, null);
    }

    private String resolveFilter(ExtractedLogContext ctx, String message, String processColumn) {
        String filter = clean(ctx.getExtractedFilterCode());
        if (notBlank(filter)) {
            return filter;
        }

        if (isProcessHyphenColumn(processColumn)) {
            String root = extractProcessHyphenRootName(processColumn);
            if ("processFilter".equalsIgnoreCase(root)) {
                String middle = middleColumnSegment(processColumn);
                if (notBlank(middle) && !isNumericOnly(middle)) {
                    return middle;
                }
            }
        }

        String structureStep = extractFirst(STRUCTURE_STEP_PATTERN, message);
        if (notBlank(structureStep)) {
            return "structureData." + structureStep;
        }

        if (containsIgnoreCase(message, "structuredata")
                || containsIgnoreCase(message, "strcturedatasinterface")) {
            return "structureData";
        }

        return null;
    }

    private String resolveObject(ExtractedLogContext ctx, LogEntry log) {
        String className = clean(ctx.getExtractedClassName());
        if (notBlank(className)) {
            return simplifyClassName(className);
        }

        String tomcatObject = extractTomcatBusinessObject(log != null ? log.getMessage() : null);
        if (notBlank(tomcatObject)) {
            return tomcatObject;
        }

        if (log != null && notBlank(log.getBusinessKey())) {
            return clean(log.getBusinessKey());
        }

        String key = clean(ctx.getExtractedBusinessObjectKey());
        String value = clean(ctx.getExtractedBusinessObjectValue());
        if (notBlank(key) && notBlank(value)) {
            return key + "=" + truncate(value, 80);
        }

        if (notBlank(key)) {
            return key;
        }

        return null;
    }

    private void parseProcessColumn(String processColumn, ExtractedLogContext ctx, String message) {
        if (!notBlank(processColumn)) {
            return;
        }

        String column = processColumn.trim();

        if (isProcessHyphenColumn(column)) {
            parseProcessHyphenColumn(column, ctx, message);
            return;
        }

        if (column.startsWith("process.")) {
            String processName = extractProcessDotColumnName(column);
            if (ctx.getExtractedProcessName() == null) {
                ctx.setExtractedProcessName(processName);
            }

            String[] parts = column.substring("process.".length()).split("-");
            if (parts.length > 0) {
                String last = clean(parts[parts.length - 1]);
                if (isLikelyActionToken(last)) {
                    ctx.setExtractedActionName(firstNonBlank(ctx.getExtractedActionName(), last));
                }
            }
        }
    }

    private String extractProcessDotColumnName(String processColumn) {
        if (!processColumn.startsWith("process.")) {
            return null;
        }
        String rest = processColumn.substring("process.".length()).trim();
        Matcher m = PROCESS_DOT_SUFFIX_PATTERN.matcher(rest);
        if (m.find()) {
            return clean(m.group(1));
        }
        int idx = rest.indexOf('-');
        String name = idx > 0 ? clean(rest.substring(0, idx)) : clean(rest);
        if (name != null) {
            name = name.replaceAll("-+$", "").trim();
        }
        return name;
    }

    private void parseProcessHyphenColumn(String column, ExtractedLogContext ctx, String message) {
        String[] parts = column.split("-");
        if (parts.length > 0 && ctx.getExtractedProcessName() == null) {
            ctx.setExtractedProcessName(clean(parts[0]));
        }

        String rootProcess = parts.length > 0 ? clean(parts[0]) : null;

        if (parts.length > 2) {
            String middle = clean(parts[2]);
            String msgFilter = extractFirst(FILTER_CODE_PATTERN, message);
            String msgTask = firstNonBlank(
                    extractFirst(TASK_BRACKET_PATTERN, message),
                    extractFirst(TASK_PARAM_PATTERN, message));

            if (notBlank(middle) && !isNumericOnly(middle)) {
                if ("processFilter".equalsIgnoreCase(rootProcess)) {
                    ctx.setExtractedFilterCode(firstNonBlank(ctx.getExtractedFilterCode(), middle));
                    if (notBlank(msgTask)) {
                        ctx.setExtractedTaskName(firstNonBlank(ctx.getExtractedTaskName(), msgTask));
                    }
                } else if ("processWebService".equalsIgnoreCase(rootProcess)) {
                    String wsTask = firstNonBlank(
                            ctx.getExtractedTaskName(),
                            notBlank(middle) && !isNumericOnly(middle) && !isLikelyActionToken(middle) ? middle : null,
                            findSegmentForWebService(parts),
                            extractWebServiceTaskFromMessage(message));
                    ctx.setExtractedTaskName(wsTask);
                } else if ("processRunRules".equalsIgnoreCase(rootProcess)) {
                    if (looksLikeFilterCode(middle) && !isTaskNameSegment(middle)) {
                        ctx.setExtractedFilterCode(firstNonBlank(ctx.getExtractedFilterCode(), middle));
                    } else if (!isLikelyActionToken(middle)) {
                        ctx.setExtractedTaskName(firstNonBlank(ctx.getExtractedTaskName(), middle));
                    }
                } else if (isTaskNameSegment(middle)) {
                    ctx.setExtractedTaskName(firstNonBlank(ctx.getExtractedTaskName(), middle));
                } else if (notBlank(msgFilter) && !msgFilter.equalsIgnoreCase(middle)) {
                    if (looksLikeFilterCode(middle)) {
                        ctx.setExtractedFilterCode(firstNonBlank(ctx.getExtractedFilterCode(), middle));
                    } else {
                        ctx.setExtractedTaskName(firstNonBlank(ctx.getExtractedTaskName(), middle));
                    }
                } else if (looksLikeFilterCode(middle)) {
                    ctx.setExtractedFilterCode(firstNonBlank(ctx.getExtractedFilterCode(), middle));
                } else {
                    ctx.setExtractedTaskName(firstNonBlank(ctx.getExtractedTaskName(), middle));
                }
            }
        }

        if (parts.length > 4) {
            ctx.setExtractedActionName(firstNonBlank(ctx.getExtractedActionName(), clean(parts[4])));
        } else if (parts.length > 0) {
            String last = clean(parts[parts.length - 1]);
            if (isLikelyActionToken(last)) {
                ctx.setExtractedActionName(firstNonBlank(ctx.getExtractedActionName(), last));
            }
        }
    }

    private boolean isProcessHyphenColumn(String column) {
        return notBlank(column) && PROCESS_HYPHEN_COLUMN_PREFIX.matcher(column.trim()).lookingAt();
    }

    private String extractProcessHyphenRootName(String column) {
        if (!isProcessHyphenColumn(column)) {
            return clean(column);
        }
        String[] parts = column.split("-");
        return parts.length > 0 ? clean(parts[0]) : clean(column);
    }

    private void enrichFromProcessLogMessage(String message, ExtractedLogContext ctx) {
        if (!notBlank(message)) {
            return;
        }

        String startFiltre = extractFirst(START_FILTRE_PATTERN, message);
        if (notBlank(startFiltre)) {
            ctx.setExtractedFilterCode(firstNonBlank(ctx.getExtractedFilterCode(), startFiltre));
        }

        String endFiltre = extractFirst(END_FILTRE_PATTERN, message);
        if (notBlank(endFiltre)) {
            ctx.setExtractedFilterCode(firstNonBlank(ctx.getExtractedFilterCode(), endFiltre));
        }

        String wsWorkflow = extractFirst(START_WS_WORKFLOW_PATTERN, message);
        if (notBlank(wsWorkflow)) {
            ctx.setExtractedTaskName(firstNonBlank(ctx.getExtractedTaskName(), wsWorkflow));
        }

        if (ctx.getExtractedTaskName() == null) {
            String root = ctx.getExtractedProcessName();
            boolean wsContext = "processWebService".equalsIgnoreCase(root)
                    || containsIgnoreCase(message, "Start Filtre")
                    || containsIgnoreCase(message, "Start WS")
                    || containsIgnoreCase(message, "End Filtre")
                    || containsIgnoreCase(message, "End WS");
            if (wsContext) {
                ctx.setExtractedTaskName(extractWebServiceTaskFromMessage(message));
            }
        }
    }

    private void enrichFromTomcatStructureMessage(String message, ExtractedLogContext ctx) {
        if (!notBlank(message)) {
            return;
        }

        String step = extractFirst(STRUCTURE_STEP_PATTERN, message);
        if (notBlank(step) && ctx.getExtractedFilterCode() == null) {
            ctx.setExtractedFilterCode("structureData." + step);
        }

        String method = extractFirst(STRUCTURE_METHOD_PATTERN, message);
        if (notBlank(method)) {
            ctx.setExtractedActionName(firstNonBlank(ctx.getExtractedActionName(), method));
        }

        if (containsIgnoreCase(message, "new interface object")) {
            ctx.setExtractedBusinessObjectKey(firstNonBlank(ctx.getExtractedBusinessObjectKey(), "interfaceObject"));
        }
    }

    private String extractTomcatBusinessObject(String message) {
        if (!notBlank(message)) {
            return null;
        }

        String putKey = extractFirst(PUT_KEY_PATTERN, message);
        if (notBlank(putKey)) {
            return putKey;
        }

        String worksField = extractFirst(WORKS_FIELD_PATTERN, message);
        if (notBlank(worksField)) {
            return worksField;
        }

        String fieldName = extractFirst(FIELD_NAME_PATTERN, message);
        if (notBlank(fieldName)) {
            return fieldName;
        }

        String relation = extractFirst(RELATION_NAME_PATTERN, message);
        if (notBlank(relation)) {
            return relation;
        }

        String interfaceField = extractFirst(INTERFACE_FIELD_PATTERN, message);
        if (notBlank(interfaceField)) {
            return interfaceField;
        }

        String fieldClassCode = extractFirst(FIELD_CLASS_CODE_PATTERN, message);
        if (notBlank(fieldClassCode)) {
            return fieldClassCode;
        }

        return null;
    }

    private boolean isTaskNameSegment(String value) {
        if (!notBlank(value)) {
            return false;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return upper.endsWith("_TASK") || upper.contains("_TASK_") || "DATA_LOAD_TASK".equals(upper);
    }

    private boolean containsIgnoreCase(String text, String needle) {
        return text != null && needle != null && text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private void parseMessagePipeSegments(String message, ExtractedLogContext ctx, String processColumn) {
        if (!message.contains("|")) {
            return;
        }
        boolean hasFilterCode = containsIgnoreCase(message, "filter code [");
        boolean hasRunningRules = containsIgnoreCase(message, "running rules|");
        if (hasFilterCode && !hasRunningRules) {
            return;
        }

        String[] segments = message.split("\\|");
        if (hasRunningRules && segments.length >= 3) {
            String pipeSegment = clean(segments[2]);
            if (notBlank(pipeSegment)) {
                if (isProcessFilterColumn(processColumn)) {
                    ctx.setExtractedFilterCode(firstNonBlank(ctx.getExtractedFilterCode(), pipeSegment));
                } else if (looksLikeTaskName(pipeSegment)) {
                    ctx.setExtractedTaskName(firstNonBlank(ctx.getExtractedTaskName(), pipeSegment));
                }
            }
            if (segments.length >= 4) {
                String pipeAction = clean(segments[3]).replaceFirst("^\\d+-", "");
                if (isLikelyActionToken(pipeAction)) {
                    ctx.setExtractedActionName(firstNonBlank(ctx.getExtractedActionName(), pipeAction));
                }
            }
        }

        for (String segment : segments) {
            String token = clean(segment);
            if (!notBlank(token) || "null".equalsIgnoreCase(token)) {
                continue;
            }

            if (token.startsWith("process") && ctx.getExtractedProcessName() == null) {
                if ("processFilter".equalsIgnoreCase(token)) {
                    ctx.setExtractedProcessName("processFilter");
                } else if ("processWebService".equalsIgnoreCase(token)) {
                    ctx.setExtractedProcessName("processWebService");
                } else if ("processRunRules".equalsIgnoreCase(token)) {
                    ctx.setExtractedProcessName("processRunRules");
                }
            }

            if (isLikelyActionToken(token)) {
                ctx.setExtractedActionName(firstNonBlank(ctx.getExtractedActionName(), token));
            } else if (ctx.getExtractedTaskName() == null
                    && looksLikeTaskName(token)
                    && !"processFilter".equalsIgnoreCase(token)) {
                ctx.setExtractedTaskName(token);
            }
        }
    }

    private String extractClassName(String text) {
        String bracket = extractFirst(CLASSNAME_BRACKET_PATTERN, text);
        if (notBlank(bracket)) {
            return simplifyClassName(bracket);
        }
        return extractFirst(CLASSNAME_LOOSE_PATTERN, text);
    }

    private String simplifyClassName(String className) {
        if (!notBlank(className)) {
            return null;
        }
        String value = className.trim();
        if (value.length() > 2 && value.length() % 2 == 0) {
            String half = value.substring(0, value.length() / 2);
            if (value.equals(half + half)) {
                return half;
            }
        }
        return value;
    }

    private boolean looksLikeFilterCode(String value) {
        if (!notBlank(value) || isTaskNameSegment(value)) {
            return false;
        }
        String v = value.toUpperCase(Locale.ROOT);
        if (v.startsWith("WORKS_") || v.endsWith("_TASK") || v.contains("_AGENTS")) {
            return false;
        }
        return v.startsWith("WS_")
                || v.contains(".java")
                || v.contains("BY")
                || v.contains("SEARCH")
                || v.contains("RULE_")
                || v.contains("FIND_")
                || (v.contains("_") && v.length() > 12);
    }

    private boolean isLikelyActionToken(String value) {
        if (!notBlank(value)) {
            return false;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (upper.length() > 80) {
            return false;
        }
        return upper.equals("LOAD")
                || upper.equals("BEFORE_LOAD")
                || upper.equals("AFTER_LOAD")
                || upper.equals("SAVE")
                || upper.equals("ACTION_NAME_SAVE")
                || upper.equals("CONTROLSELECTION")
                || upper.contains("CONTROLSELECTION")
                || upper.contains("SELECTION")
                || upper.equals("VALIDER")
                || upper.equals("ANNULER")
                || upper.equals("IMPRIMER")
                || upper.equals("AFFECTATION")
                || upper.equals("START")
                || upper.equals("STARTPROCESS")
                || upper.contains("LOAD")
                || upper.contains("SAVE")
                || upper.startsWith("START");
    }

    private String normalizeActionLabel(String action) {
        String cleaned = clean(action);
        if (!notBlank(cleaned)) {
            return null;
        }
        if (cleaned.startsWith("start.process.")) {
            return "Start";
        }
        if (cleaned.contains(" ")) {
            String first = cleaned.split("\\s+")[0];
            if (isLikelyActionToken(first)) {
                return first;
            }
        }
        return cleaned;
    }

    private boolean isNumericOnly(String value) {
        return value != null && value.matches("\\d+");
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
        if (value == null) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed > Integer.MAX_VALUE || parsed < Integer.MIN_VALUE) {
                return null;
            }
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long extractLong(Pattern pattern, String text) {
        String value = extractFirst(pattern, text);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String cleanCheckpoint(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }
        cleaned = cleaned.replace("-", " ").replace("_", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.isBlank() || "null".equalsIgnoreCase(cleaned)) {
            return null;
        }
        return cleaned;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty() || "null".equalsIgnoreCase(cleaned)) {
            return null;
        }
        return cleaned;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (notBlank(value) && !"null".equalsIgnoreCase(value.trim())) {
                return clean(value);
            }
        }
        return null;
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
