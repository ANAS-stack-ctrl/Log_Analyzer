package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;
import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private static final Pattern RULES_HOST_MS = Pattern.compile(
            "running rules.*?in host took\\s*\\[(\\d+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern POST_FILTER_RULES = Pattern.compile(
            "running rules.*?processFilter.*?took\\s*\\[(\\d+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern PARTITIONAL_END = Pattern.compile(
            "end partitional searchComposantByRoot", Pattern.CASE_INSENSITIVE);

    private static final Pattern THREAD_PROGRESS = Pattern.compile(
            "-->\\s*(\\d+)\\s*/\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLASSNAME = Pattern.compile(
            "className\\s+([A-Za-z][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern ROW_PIPE = Pattern.compile(
            "took\\s*\\[\\d+]\\s*ms\\|(\\d{1,6})\\s*row", Pattern.CASE_INSENSITIVE);

    public WorksLatencyFacts extract(LatencyOriginReportDto report,
                                     List<LogEntry> windowLogs,
                                     List<LatencyTimelineStepDto> steps) {
        WorksLatencyFacts facts = new WorksLatencyFacts();
        if (report != null) {
            facts.setUserName(report.getUserName());
            facts.setProcessName(report.getProcessName());
            facts.setFilterCode(report.getFilterCode());
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
        int maxJoins = 0;
        int partitionalEnds = 0;
        int maxThreadTotal = 0;
        Integer memoryPeak = facts.getMemoryPeakMo();
        Long renderingMs = facts.getRenderingMs();
        Long rulesMs = facts.getRulesEngineMs();
        Long postRulesMs = facts.getPostSearchRulesMs();
        String className = facts.getClassName();

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

            Long rend = extractLong(RENDERING_MS, text).orElse(null);
            if (rend != null) {
                renderingMs = rend;
            }

            Long rules = extractLong(RULES_HOST_MS, text).orElse(null);
            if (rules != null) {
                rulesMs = rules;
            }

            Long post = extractLong(POST_FILTER_RULES, text).orElse(null);
            if (post != null) {
                postRulesMs = post;
            }

            if (className == null) {
                className = extractString(CLASSNAME, text).orElse(null);
            }

            if (facts.getRootRowCount() == null && lower.contains("preparesearchbyroot")) {
                extractInt(ROW_PIPE, text).ifPresent(facts::setRootRowCount);
            }
        }

        facts.setOperationStart(opStart);
        facts.setOperationEnd(opEnd);
        facts.setSqlJoinCount(maxJoins);
        facts.setClassName(className);
        facts.setMemoryPeakMo(memoryPeak);
        facts.setRenderingMs(renderingMs);
        facts.setRulesEngineMs(rulesMs);
        facts.setPostSearchRulesMs(postRulesMs);
        facts.setPartitionalEndCount(partitionalEnds);
        if (maxThreadTotal > 0) {
            facts.setMaxThreadProgressTotal(maxThreadTotal);
        }

        applyDurationStats(facts, loadTotals, loadB2s, searchAttrs);
        computeBatchCount(facts);

        long downstreamMs = (renderingMs != null ? renderingMs : 0)
                + (postRulesMs != null ? postRulesMs : 0);
        long globalMs = facts.getGlobalMs() != null ? facts.getGlobalMs() : 0;
        facts.setFastDownstream(globalMs > 0 && downstreamMs > 0 && downstreamMs < 3000);

        return facts;
    }

    private void mergeFromSteps(WorksLatencyFacts facts, List<LatencyTimelineStepDto> steps) {
        if (steps == null) {
            return;
        }
        for (LatencyTimelineStepDto step : steps) {
            String type = step.getStepType();
            if (LatencyOperationClassifier.ROOT_QUERY.equals(type)) {
                facts.setRootQueryMs(step.getDurationMs());
                if (step.getRowCount() != null) {
                    facts.setRootRowCount(step.getRowCount());
                }
                if (step.getMemoryMo() != null) {
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
                facts.setRenderingMs(step.getDurationMs());
            } else if (LatencyOperationClassifier.RULES_ENGINE.equals(type)) {
                facts.setRulesEngineMs(step.getDurationMs());
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
