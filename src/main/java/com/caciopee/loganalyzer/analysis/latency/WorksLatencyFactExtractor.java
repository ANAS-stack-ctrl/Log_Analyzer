package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;
import com.caciopee.loganalyzer.dto.LatencyQueryGroupDto;
import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WorksLatencyFactExtractor {

    private static final Pattern JOIN_PATTERN = Pattern.compile(
            "\\b(inner\\s+join|join)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern MULTITHREAD_CONFIG = Pattern.compile(
            "using\\s+multiThreading\\s+for\\s+\\[(\\d+)]\\s+ids\\s*,\\s*partitionSize\\s+\\[(\\d+)]"
                    + "(?:\\s*,\\s*nbrThreadPool\\s+\\[(\\d+)])?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern START_DOSEARCH_ELEMENTS = Pattern.compile(
            "Start\\s+doSearch.*?for\\s+(\\d+)\\s+element", Pattern.CASE_INSENSITIVE);

    private static final Pattern IGNORE_MULTITHREAD = Pattern.compile(
            "ignore\\s+multiThreading\\s+for\\s+\\[(\\d+)]\\s+ids", Pattern.CASE_INSENSITIVE);

    private static final Pattern LOAD_TOTAL_MS = Pattern.compile(
            "loadListChilds\\s*\\[total]\\s*:?\\s*took\\s*\\[(\\d+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern LOAD_B2_MS = Pattern.compile(
            "loadListChilds\\s*\\[Query B1 \\+ InsertTemp \\+\\s*Query B2].*?took\\s*\\[(\\d+)]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SEARCH_ATTR_MS = Pattern.compile(
            "searchAttributesList.*?\\[Query]\\s*took\\s*\\[(\\d+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern MEMORY_MO = Pattern.compile(
            "memory usage \\(Mo\\).*?\\s(\\d{1,6})\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern RENDERING_MS = Pattern.compile(
            "rendering result.*?took\\s*\\[(\\d+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern AGGREGATE_ELEMENTS = Pattern.compile(
            "Start\\s+aggregate\\s+data\\s+for\\s+.+?-\\s*(\\d+)\\s+element",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AGGREGATE_MS = Pattern.compile(
            "(?:End\\s+)?aggregate\\s+data.*?took\\s*\\[(\\d+)]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SAVE_PROCESS_CONTENT_MS = Pattern.compile(
            "saveProcessContent.*?took\\s*\\[(\\d+)]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RULES_HOST_MS = Pattern.compile(
            "running rules.*?in host took\\s*\\[(\\d+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern RUNNING_RULES_TOOK = Pattern.compile(
            "running rules.*?took\\s*\\[(\\d+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern POST_FILTER_RULES = Pattern.compile(
            "running rules.*?processFilter.*?took\\s*\\[(\\d+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern PARTITIONAL_END = Pattern.compile(
            "end partitional searchComposantByRoot", Pattern.CASE_INSENSITIVE);

    private static final Pattern THREAD_PROGRESS = Pattern.compile(
            "-->\\s*(\\d+)\\s*/\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    // Gère les deux formes des logs WORKS : "className [DChargementCont]" (forme propre)
    // et "className DChargementContDChargementCont" (forme doublée sur les lignes "took").
    private static final Pattern CLASSNAME = Pattern.compile(
            "className\\s*\\[?\\s*([A-Za-z][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern ROW_PIPE = Pattern.compile(
            "took\\s*\\[\\d+]\\s*ms\\|(\\d{1,6})\\s*row", Pattern.CASE_INSENSITIVE);

    // prepareSearchByRoot ... took [N] ms ... filter code [X]  →  attribue chaque requête SQL à son filtre/règle.
    private static final Pattern PREPARE_TOOK_FILTER = Pattern.compile(
            "preparesearchbyroot.*?took\\s*\\[(\\d+)]\\s*ms.*?filter\\s*code\\s*\\[([^\\]]+)]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern RUNNING_RULES_PIPE = Pattern.compile(
            "running rules\\|([^|]+)\\|([^|]*)\\|([^|]*)\\|",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DO_ACTION = Pattern.compile(
            "doAction for - actionName\\s*:\\s*([^,]+),\\s*- transition\\s*:\\s*([^\\s]+)\\s+took\\s+(\\d+)\\s*ms",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MAJ_INSERT = Pattern.compile(
            "MAJ_Insert_([A-Za-z0-9_]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INSERT_REL = Pattern.compile(
            "insertArrayRel\\s+(works_rel_composant_(?:child|root)).*?for\\s*\\[(\\d+)]\\s*element.*?took\\s*\\[(\\d+)]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TOTAL_SAVE = Pattern.compile(
            "total time SAVE.*?for key\\s*\\[([^\\]]+)].*?;\\s*(\\d+)\\s*\\(ms\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern GLOBAL_SEARCH_ROWS = Pattern.compile(
            "global searchComposantByRoot.*?took\\s*\\[(\\d+)]\\s*ms\\|(\\d+)\\s*row.*?filter code\\s*\\[([^\\]]+)]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Noms de règles Drools WORKS : filter code [Rule_xxx_0.java] ou Rule_xxx dans le texte. */
    private static final Pattern DROOLS_RULE_FILTER = Pattern.compile(
            "filter\\s*code\\s*\\[\\s*(Rule_[^\\]]+?)\\s*]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DROOLS_RULE_BARE = Pattern.compile(
            "\\b(Rule_[A-Za-z0-9_.]+(?:\\.java)?)\\b",
            Pattern.CASE_INSENSITIVE);


    public WorksLatencyFacts extract(LatencyOriginReportDto report,
                                     List<LogEntry> windowLogs,
                                     List<LatencyTimelineStepDto> steps) {
        WorksLatencyFacts facts = new WorksLatencyFacts();
        if (report != null) {
            facts.setUserName(report.getUserName());
            facts.setProcessName(report.getProcessName());
            facts.setFilterCode(report.getFilterCode());
            facts.setActionName(report.getActionName());
            facts.setScreenName(report.getScreenName());
            facts.setSessionId(report.getSessionId());
            facts.setUuid(report.getUuid());
        }

        mergeFromSteps(facts, steps);

        if (windowLogs == null || windowLogs.isEmpty()) {
            computeBatchCount(facts);
            return facts;
        }

        List<Long> loadTotals = new ArrayList<>();
        List<Long> loadB2s = new ArrayList<>();
        List<Long> searchAttrs = new ArrayList<>();
        Map<String, long[]> queryByFilter = new LinkedHashMap<>();
        Map<String, Integer> majInsertByType = new LinkedHashMap<>();
        Map<String, Integer> droolsRuleCounts = new LinkedHashMap<>();
        int maxJoins = 0;
        int partitionalEnds = 0;
        int maxThreadTotal = 0;
        Integer memoryPeak = facts.getMemoryPeakMo();
        Long renderingMs = facts.getRenderingMs();
        Long aggregateMs = facts.getAggregateMs();
        Long rulesMs = facts.getRulesEngineMs();
        Long postRulesMs = facts.getPostSearchRulesMs();
        String className = facts.getClassName();
        String businessAction = facts.getBusinessActionLabel();
        String taskName = facts.getTaskName();
        String processFromRules = null;
        String actionFromRules = null;
        String preFilter = null;
        Long preSearchMs = null;
        Integer preSearchRows = null;
        Integer relChildCount = null;
        Long relChildMs = null;
        Integer relRootCount = null;
        Long relRootMs = null;
        Long saveMs = null;
        String saveKey = null;
        LocalDateTime opStart = facts.getOperationStart();
        LocalDateTime opEnd = facts.getOperationEnd();

        for (LogEntry log : windowLogs) {
            String text = combine(log);
            String lower = text.toLowerCase(Locale.ROOT);
            LocalDateTime ts = log.getLogTimestamp();
            if (ts != null) {
                if (opStart == null || ts.isBefore(opStart)) {
                    opStart = ts;
                }
                if (opEnd == null || ts.isAfter(opEnd)) {
                    opEnd = ts;
                }
            }

            Matcher runningPipe = RUNNING_RULES_PIPE.matcher(text);
            if (runningPipe.find()) {
                processFromRules = cleanToken(runningPipe.group(1));
                taskName = firstNonBlank(taskName, cleanToken(runningPipe.group(2)));
                actionFromRules = cleanToken(runningPipe.group(3));
            }

            Matcher doAction = DO_ACTION.matcher(text);
            if (doAction.find()) {
                businessAction = cleanToken(doAction.group(1));
                if (actionFromRules == null) {
                    actionFromRules = cleanToken(doAction.group(2));
                }
            }

            // Contexte volume AVANT / autour des règles (search qui alimente matchall).
            Matcher globalSearch = GLOBAL_SEARCH_ROWS.matcher(text);
            if (globalSearch.find()) {
                long ms = Long.parseLong(globalSearch.group(1));
                int rows = Integer.parseInt(globalSearch.group(2));
                String filter = cleanToken(globalSearch.group(3));
                if (preSearchMs == null || ms >= preSearchMs) {
                    preSearchMs = ms;
                    preSearchRows = rows;
                    preFilter = filter;
                }
                if (facts.getGlobalMs() == null || ms > facts.getGlobalMs()) {
                    facts.setGlobalMs(ms);
                    facts.setGlobalRowCount(rows);
                    facts.setMultithreaded(lower.contains("multithreading"));
                }
            }

            Matcher maj = MAJ_INSERT.matcher(text);
            while (maj.find()) {
                String type = maj.group(1);
                majInsertByType.merge(type, 1, Integer::sum);
            }

            // Noms de règles Drools (filter code [Rule_…] en priorité).
            Matcher droolsFilter = DROOLS_RULE_FILTER.matcher(text);
            boolean foundDroolsFilter = false;
            while (droolsFilter.find()) {
                foundDroolsFilter = true;
                String rule = normalizeDroolsRuleName(droolsFilter.group(1));
                if (rule != null) {
                    droolsRuleCounts.merge(rule, 1, Integer::sum);
                }
            }
            if (!foundDroolsFilter) {
                Matcher bare = DROOLS_RULE_BARE.matcher(text);
                while (bare.find()) {
                    String rule = normalizeDroolsRuleName(bare.group(1));
                    if (rule != null) {
                        droolsRuleCounts.merge(rule, 1, Integer::sum);
                    }
                }
            }

            Matcher rel = INSERT_REL.matcher(text);
            if (rel.find()) {
                String table = rel.group(1).toLowerCase(Locale.ROOT);
                int count = Integer.parseInt(rel.group(2));
                long ms = Long.parseLong(rel.group(3));
                if (table.contains("child")) {
                    relChildCount = count;
                    relChildMs = ms;
                } else if (table.contains("root")) {
                    relRootCount = count;
                    relRootMs = ms;
                }
            }

            Matcher totalSave = TOTAL_SAVE.matcher(text);
            if (totalSave.find()) {
                saveKey = cleanToken(totalSave.group(1));
                saveMs = Long.parseLong(totalSave.group(2));
            }

            if (lower.contains("query:")) {
                maxJoins = Math.max(maxJoins, countJoins(text));
            }

            parseMultithread(text).ifPresent(cfg -> {
                facts.setMultithreaded(true);
                facts.setElementCount(cfg.elements());
                facts.setPartitionSize(cfg.partitionSize());
                if (cfg.threadPool() != null) {
                    facts.setThreadPoolSize(cfg.threadPool());
                }
            });

            extractInt(START_DOSEARCH_ELEMENTS, text).ifPresent(count -> {
                facts.setMultithreaded(true);
                if (facts.getElementCount() == null) {
                    facts.setElementCount(count);
                }
            });

            extractInt(AGGREGATE_ELEMENTS, text).ifPresent(count -> {
                // Volume affiché avant rendering result (ex. Services Actifs AMP).
                if (facts.getElementCount() == null || count > facts.getElementCount()) {
                    facts.setElementCount(count);
                }
            });

            Long agg = extractLong(AGGREGATE_MS, text).orElse(null);
            if (agg != null) {
                aggregateMs = aggregateMs == null ? agg : Math.max(aggregateMs, agg);
            }

            Long saveProc = extractLong(SAVE_PROCESS_CONTENT_MS, text).orElse(null);
            if (saveProc != null) {
                saveMs = saveMs == null ? saveProc : Math.max(saveMs, saveProc);
            }

            extractInt(IGNORE_MULTITHREAD, text).ifPresent(count -> {
                facts.setMultithreaded(false);
                if (facts.getElementCount() == null) {
                    facts.setElementCount(count);
                }
            });

            extractLong(LOAD_TOTAL_MS, text).ifPresent(loadTotals::add);
            extractLong(LOAD_B2_MS, text).ifPresent(loadB2s::add);
            extractLong(SEARCH_ATTR_MS, text).ifPresent(searchAttrs::add);

            if (PARTITIONAL_END.matcher(text).find()) {
                partitionalEnds++;
            }

            Matcher progress = THREAD_PROGRESS.matcher(text);
            if (progress.find()) {
                maxThreadTotal = Math.max(maxThreadTotal, Integer.parseInt(progress.group(2)));
            }

            Optional<Integer> moVal = extractInt(MEMORY_MO, text);
            if (moVal.isPresent()) {
                int mo = moVal.get();
                memoryPeak = memoryPeak == null ? mo : Math.max(memoryPeak, mo);
            }

            // On retient TOUJOURS le maximum : une fenêtre peut contenir plusieurs lignes
            // "running rules ... in host" (parfois d'opérations voisines). Prendre la dernière
            // valeur écraserait le vrai goulot (ex. 159 026 ms remplacé à tort par 505 ms).
            Long rend = extractLong(RENDERING_MS, text).orElse(null);
            if (rend != null) {
                renderingMs = renderingMs == null ? rend : Math.max(renderingMs, rend);
            }

            Long rules = extractLong(RULES_HOST_MS, text).orElse(null);
            if (rules == null) {
                rules = extractLong(RUNNING_RULES_TOOK, text).orElse(null);
            }
            if (rules != null) {
                rulesMs = rulesMs == null ? rules : Math.max(rulesMs, rules);
            }

            Long post = extractLong(POST_FILTER_RULES, text).orElse(null);
            if (post != null) {
                postRulesMs = postRulesMs == null ? post : Math.max(postRulesMs, post);
            }

            if (className == null) {
                className = extractString(CLASSNAME, text).orElse(null);
            }

            if (facts.getRootRowCount() == null && lower.contains("preparesearchbyroot")) {
                extractInt(ROW_PIPE, text).ifPresent(facts::setRootRowCount);
            }

            accumulateQueryByFilter(queryByFilter, text, lower);
        }

        facts.setRepeatedQueryGroups(toQueryGroups(queryByFilter));

        facts.setOperationStart(opStart);
        facts.setOperationEnd(opEnd);
        facts.setSqlJoinCount(maxJoins);
        facts.setClassName(className);
        facts.setMemoryPeakMo(memoryPeak);
        facts.setRenderingMs(renderingMs);
        facts.setAggregateMs(aggregateMs);
        facts.setRulesEngineMs(rulesMs);
        facts.setPostSearchRulesMs(postRulesMs);
        facts.setPartitionalEndCount(partitionalEnds);
        if (maxThreadTotal > 0) {
            facts.setMaxThreadProgressTotal(maxThreadTotal);
        }

        if (processFromRules != null && !isPlaceholderProcess(processFromRules)) {
            facts.setProcessName(processFromRules);
        }
        if (actionFromRules != null) {
            facts.setActionName(actionFromRules);
        }
        facts.setBusinessActionLabel(businessAction);
        facts.setTaskName(taskName);
        facts.setPreRulesFilterCode(preFilter);
        facts.setPreRulesSearchMs(preSearchMs);
        facts.setPreRulesRowCount(preSearchRows);
        facts.setInsertRelChildCount(relChildCount);
        facts.setInsertRelChildMs(relChildMs);
        facts.setInsertRelRootCount(relRootCount);
        facts.setInsertRelRootMs(relRootMs);
        facts.setSavePersistMs(saveMs);
        facts.setSavePersistKey(saveKey);

        int majTotal = majInsertByType.values().stream().mapToInt(Integer::intValue).sum();
        facts.setMajInsertTotal(majTotal);
        majInsertByType.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(e -> {
                    facts.setMajInsertDominantType(e.getKey());
                    facts.setMajInsertDominantCount(e.getValue());
                });

        // Règles Drools : tri par fréquence, top noms exposés au récit.
        List<LatencyQueryGroupDto> droolsHits = new ArrayList<>();
        for (Map.Entry<String, Integer> e : droolsRuleCounts.entrySet()) {
            droolsHits.add(new LatencyQueryGroupDto(e.getKey(), e.getValue(), 0L, 0L));
        }
        droolsHits.sort(Comparator
                .comparingInt(LatencyQueryGroupDto::getCount).reversed()
                .thenComparing(LatencyQueryGroupDto::getFilterCode));
        facts.setDroolsRuleHits(droolsHits);
        facts.setDroolsRuleNames(droolsHits.stream()
                .map(LatencyQueryGroupDto::getFilterCode)
                .limit(12)
                .collect(java.util.stream.Collectors.toList()));

        // Si on a vu START fire rules sans inserts, les counts restent 0 (OK).

        applyDurationStats(facts, loadTotals, loadB2s, searchAttrs);
        computeBatchCount(facts);

        long downstreamMs = (renderingMs != null ? renderingMs : 0)
                + (postRulesMs != null ? postRulesMs : 0);
        long globalMs = facts.getGlobalMs() != null ? facts.getGlobalMs() : 0;
        facts.setFastDownstream(globalMs > 0 && downstreamMs > 0 && downstreamMs < 3000);

        return facts;
    }

    private static boolean isPlaceholderProcess(String process) {
        if (process == null || process.isBlank()) return true;
        String p = process.trim();
        return p.matches("-?\\d+--\\d+-[A-Za-z_]+") || p.equalsIgnoreCase("-0--0-SAVE");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static String cleanToken(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("(?i)#icon:[^#]*#", "").trim();
        if (cleaned.isEmpty() || "null".equalsIgnoreCase(cleaned)) return null;
        return cleaned;
    }

    /** Normalise un nom de règle Drools (conserve .java si présent pour le matching logs). */
    private static String normalizeDroolsRuleName(String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim();
        if (cleaned.isEmpty()) return null;
        // Évite de capturer des fragments non-règle.
        if (!cleaned.regionMatches(true, 0, "Rule_", 0, 5)) {
            return null;
        }
        return cleaned;
    }

    private void accumulateQueryByFilter(Map<String, long[]> acc, String text, String lower) {
        if (!lower.contains("preparesearchbyroot") || !lower.contains("filter code")) {
            return;
        }
        Matcher matcher = PREPARE_TOOK_FILTER.matcher(text);
        if (!matcher.find()) {
            return;
        }
        long ms;
        try {
            ms = Long.parseLong(matcher.group(1).trim());
        } catch (NumberFormatException e) {
            return;
        }
        String filter = matcher.group(2) != null ? matcher.group(2).trim() : null;
        if (filter == null || filter.isBlank()) {
            return;
        }
        long[] entry = acc.computeIfAbsent(filter, k -> new long[]{0L, 0L, 0L});
        entry[0] += 1;             // count
        entry[1] += ms;            // total
        entry[2] = Math.max(entry[2], ms); // max
    }

    private List<LatencyQueryGroupDto> toQueryGroups(Map<String, long[]> acc) {
        List<LatencyQueryGroupDto> groups = new ArrayList<>();
        for (Map.Entry<String, long[]> e : acc.entrySet()) {
            long[] v = e.getValue();
            groups.add(new LatencyQueryGroupDto(e.getKey(), (int) v[0], v[1], v[2]));
        }
        groups.sort(Comparator.comparingLong(LatencyQueryGroupDto::getTotalMs).reversed());
        return groups;
    }

    private void mergeFromSteps(WorksLatencyFacts facts, List<LatencyTimelineStepDto> steps) {
        if (steps == null) {
            return;
        }
        for (LatencyTimelineStepDto step : steps) {
            String type = step.getStepType();
            if (LatencyOperationClassifier.ROOT_QUERY.equals(type)) {
                // Conserver la requête racine LA PLUS LENTE (une fenêtre peut contenir
                // plusieurs prepareSearchByRoot : on aligne les faits sur le vrai goulot).
                if (facts.getRootQueryMs() == null
                        || (step.getDurationMs() != null && step.getDurationMs() > facts.getRootQueryMs())) {
                    facts.setRootQueryMs(step.getDurationMs());
                    facts.setRootRowCount(step.getRowCount());
                    facts.setRootMemoryMo(step.getMemoryMo());
                }
            } else if (LatencyOperationClassifier.GLOBAL_CONTAINER.equals(type)
                    || LatencyOperationClassifier.PARTITIONAL.equals(type)) {
                if (facts.getGlobalMs() == null
                        || (step.getDurationMs() != null && step.getDurationMs() > facts.getGlobalMs())) {
                    facts.setGlobalMs(step.getDurationMs());
                    facts.setGlobalRowCount(step.getRowCount());
                }
                if (step.getOperationName() != null
                        && step.getOperationName().toLowerCase(Locale.ROOT).contains("multithreading")) {
                    facts.setMultithreaded(true);
                }
            } else if (LatencyOperationClassifier.MULTITHREAD_START.equals(type)) {
                facts.setMultithreaded(true);
                if (step.getRowCount() != null) {
                    facts.setElementCount(step.getRowCount());
                }
            } else if (LatencyOperationClassifier.MULTITHREAD_CONFIG.equals(type)) {
                facts.setMultithreaded(false);
            } else if (LatencyOperationClassifier.RENDERING.equals(type)) {
                if (facts.getRenderingMs() == null
                        || (step.getDurationMs() != null && step.getDurationMs() > facts.getRenderingMs())) {
                    facts.setRenderingMs(step.getDurationMs());
                }
            } else if (LatencyOperationClassifier.RULES_ENGINE.equals(type)
                    || LatencyOperationClassifier.WEBSERVICE.equals(type)
                    || LatencyOperationClassifier.PRINT.equals(type)
                    || LatencyOperationClassifier.AUTOSTART.equals(type)) {
                if (facts.getRulesEngineMs() == null
                        || (step.getDurationMs() != null && step.getDurationMs() > facts.getRulesEngineMs())) {
                    facts.setRulesEngineMs(step.getDurationMs());
                }
            } else if (LatencyOperationClassifier.SAVE_PERSIST.equals(type)
                    || LatencyOperationClassifier.VALIDATION.equals(type)) {
                if (facts.getSavePersistMs() == null
                        || (step.getDurationMs() != null && step.getDurationMs() > facts.getSavePersistMs())) {
                    facts.setSavePersistMs(step.getDurationMs());
                }
            }
        }
    }

    private void applyDurationStats(WorksLatencyFacts facts,
                                    List<Long> loadTotals,
                                    List<Long> loadB2s,
                                    List<Long> searchAttrs) {
        if (!loadTotals.isEmpty()) {
            facts.setLoadChildSampleCount(loadTotals.size());
            facts.setLoadChildMinMs(loadTotals.stream().min(Long::compareTo).orElse(0L));
            facts.setLoadChildMaxMs(loadTotals.stream().max(Long::compareTo).orElse(0L));
            facts.setLoadChildAvgMs(Math.round(loadTotals.stream().mapToLong(Long::longValue).average().orElse(0)));
        }
        if (!loadB2s.isEmpty()) {
            facts.setLoadB2SampleCount(loadB2s.size());
            facts.setLoadB2AvgMs(Math.round(loadB2s.stream().mapToLong(Long::longValue).average().orElse(0)));
        }
        if (!searchAttrs.isEmpty()) {
            facts.setSearchAttrSampleCount(searchAttrs.size());
            facts.setSearchAttrAvgMs(Math.round(searchAttrs.stream().mapToLong(Long::longValue).average().orElse(0)));
        }
    }

    private void computeBatchCount(WorksLatencyFacts facts) {
        if (facts.getBatchCount() != null) {
            return;
        }
        Integer elements = facts.getElementCount();
        Integer partition = facts.getPartitionSize();
        if (elements != null && partition != null && partition > 0) {
            facts.setBatchCount((int) Math.ceil((double) elements / partition));
            return;
        }
        if (facts.getMaxThreadProgressTotal() != null && facts.getMaxThreadProgressTotal() > 0) {
            facts.setBatchCount(facts.getMaxThreadProgressTotal());
            return;
        }
        if (facts.getPartitionalEndCount() > 0) {
            facts.setBatchCount(facts.getPartitionalEndCount());
        }
    }

    private int countJoins(String text) {
        int joins = 0;
        Matcher matcher = JOIN_PATTERN.matcher(text);
        while (matcher.find()) {
            joins++;
        }
        return joins;
    }

    private String combine(LogEntry log) {
        String msg = log.getMessage() != null ? log.getMessage() : "";
        String raw = log.getRawLog() != null ? log.getRawLog() : "";
        return msg + " " + raw;
    }

    private Optional<Long> extractLong(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String g = matcher.group(i);
            if (g != null && !g.isBlank()) {
                return Optional.of(Long.parseLong(g.trim()));
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> extractInt(Pattern pattern, String text) {
        return extractLong(pattern, text).map(Long::intValue);
    }

    private Optional<String> extractString(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find() && matcher.groupCount() >= 1) {
            return Optional.ofNullable(matcher.group(1)).map(String::trim);
        }
        return Optional.empty();
    }

    private Optional<MultithreadConfig> parseMultithread(String text) {
        Matcher matcher = MULTITHREAD_CONFIG.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        Integer pool = matcher.groupCount() >= 3 && matcher.group(3) != null
                ? Integer.parseInt(matcher.group(3).trim()) : null;
        return Optional.of(new MultithreadConfig(
                Integer.parseInt(matcher.group(1).trim()),
                Integer.parseInt(matcher.group(2).trim()),
                pool));
    }

    private record MultithreadConfig(int elements, int partitionSize, Integer threadPool) {
    }
}
