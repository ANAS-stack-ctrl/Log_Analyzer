package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.latency.LatencyCauseAnalyzer;
import com.caciopee.loganalyzer.analysis.latency.LatencyPlausibility;
import com.caciopee.loganalyzer.analysis.latency.LatencyRootCauseClassifier;
import com.caciopee.loganalyzer.analysis.latency.LatencyRootCauseClassifier.Classification;
import com.caciopee.loganalyzer.analysis.latency.WorksLatencyTimelineParser;
import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.LatencyExecutionSampleDto;
import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;
import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LatencyInvestigationService {

    private static final Pattern UUID_IN_MESSAGE = Pattern.compile(
            "uuid\\s*\\[\\s*([^\\]]+?)\\s*]", Pattern.CASE_INSENSITIVE);

    /** Forme WORKS {@code [3541956776772717142] doAction for ...} (sans mot-clé uuid). */
    private static final Pattern LEADING_BRACKET_UUID = Pattern.compile(
            "(?:^|\\|)\\s*\\[(-?\\d{10,20})\\]\\s+(?=[A-Za-z_])");

    private static final long SLOW_THRESHOLD_MS = 2000L;
    private static final int MIN_SCOPE_LOGS = 5;

    private final LogEntryRepository logEntryRepository;
    private final LogPatternExtractor logPatternExtractor;
    private final WorksLatencyTimelineParser timelineParser;
    private final LatencyCauseAnalyzer causeAnalyzer;
    private final LatencyRootCauseClassifier rootCauseClassifier;

    public LatencyInvestigationService(LogEntryRepository logEntryRepository,
                                       LogPatternExtractor logPatternExtractor,
                                       WorksLatencyTimelineParser timelineParser,
                                       LatencyCauseAnalyzer causeAnalyzer,
                                       LatencyRootCauseClassifier rootCauseClassifier) {
        this.logEntryRepository = logEntryRepository;
        this.logPatternExtractor = logPatternExtractor;
        this.timelineParser = timelineParser;
        this.causeAnalyzer = causeAnalyzer;
        this.rootCauseClassifier = rootCauseClassifier;
    }

    public LatencyOriginReportDto investigate(Long importId,
                                              Long evidenceLogId,
                                              String sessionId,
                                              String uuid,
                                              String filterCode,
                                              String processName) {
        if (importId == null) {
            throw new IllegalArgumentException("importId requis.");
        }

        LogEntry anchor = resolveAnchor(importId, evidenceLogId, sessionId, filterCode);
        if (anchor == null) {
            throw new IllegalArgumentException("Aucun log d'ancrage trouvé pour cette latence.");
        }

        GraphExtraction anchorExtraction = logPatternExtractor.extractGraph(anchor);
        String resolvedUuid = coalesce(uuid, anchorExtraction.getUuid(), extractUuidFromText(combine(anchor)));
        String resolvedSession = coalesce(sessionId, anchor.getSessionId());
        String resolvedFilter = coalesce(filterCode, anchorExtraction.getFilter(), extractFilter(combine(anchor)));
        String resolvedProcess = preferBusinessProcess(
                processName,
                extractRulesPipeProcess(combine(anchor)),
                anchorExtraction.getProcess(),
                anchor.getProcessName());
        String resolvedAction = coalesce(
                extractDoActionLabel(combine(anchor)),
                extractRulesPipeAction(combine(anchor)),
                anchorExtraction.getAction(),
                extractAction(combine(anchor)));

        List<LogEntry> scopeLogs = loadScopeLogs(importId, resolvedSession, resolvedUuid, resolvedFilter);
        if (scopeLogs.isEmpty()) {
            scopeLogs = List.of(anchor);
        }

        int anchorIndex = indexOfLog(scopeLogs, anchor.getId());
        if (anchorIndex < 0) {
            anchorIndex = indexOfSlowestLog(scopeLogs);
        }

        List<LogEntry> windowLogs = extractAdaptiveWindow(scopeLogs, anchorIndex);
        List<LatencyTimelineStepDto> steps = timelineParser.parseTimeline(windowLogs);

        String resolvedScreen = sanitizeScreen(
                coalesce(extractScreenName(combine(anchor)), extractScreenNameFromLogs(windowLogs)));
        resolvedProcess = preferBusinessProcess(
                resolvedProcess, extractProcessFromLogs(windowLogs));
        if (!notBlank(resolvedAction)) {
            resolvedAction = coalesce(
                    extractDoActionFromLogs(windowLogs),
                    extractRulesPipeActionFromLogs(windowLogs),
                    extractActionFromLogs(windowLogs));
        }
        if (!notBlank(resolvedFilter)) {
            resolvedFilter = extractFilterFromLogs(windowLogs);
        }
        if (!notBlank(resolvedUuid)) {
            resolvedUuid = extractUuidFromLogs(windowLogs);
        }

        // Le chiffre mis en avant doit provenir d'une étape PLAUSIBLE (jamais d'un took
        // anomalique de plusieurs heures). On ne retombe sur la valeur brute de l'ancre
        // que si aucune étape fiable n'existe (et on le signalera via measurementWarning).
        long plausibleMax = steps.stream()
                .filter(s -> !s.isSuspect())
                .map(LatencyTimelineStepDto::getDurationMs)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L);
        long maxDuration = plausibleMax > 0
                ? plausibleMax
                : steps.stream()
                    .map(LatencyTimelineStepDto::getDurationMs)
                    .filter(Objects::nonNull)
                    .max(Long::compareTo)
                    .orElse(resolveDuration(anchor));

        LatencyOriginReportDto report = new LatencyOriginReportDto();
        report.setImportId(importId);
        report.setAnchorLogId(anchor.getId());
        report.setSessionId(resolvedSession);
        report.setUuid(resolvedUuid);
        report.setUserName(anchor.getUserName());
        report.setProcessName(resolvedProcess);
        report.setFilterCode(resolvedFilter);
        report.setActionName(resolvedAction);
        report.setScreenName(resolvedScreen);
        report.setMaxDurationMs(maxDuration);
        report.setScopeDescription(buildScopeDescription(resolvedSession, resolvedUuid, resolvedFilter));
        report.setLogsInScope(scopeLogs.size());
        report.setLogsInWindow(windowLogs.size());
        report.setTimelineSteps(steps);
        report.setChronologicalText(timelineParser.buildChronologicalText(windowLogs));
        report.setMeasurementWarning(buildMeasurementWarning(steps, anchor));

        causeAnalyzer.enrichReport(report, steps, windowLogs);

        // Enrichissement additif via le classifieur de cause racine : renseigne
        // category / evidence / requête SQL racine / logs de cause SANS écraser
        // le verdict, la cause principale ni la fiabilité déjà calculés (logique validée).
        applyRootCauseClassification(report, anchor, windowLogs, steps);

        // Preuves fortes (trou silencieux, pic mémoire, bascule de fichiers, comparaison
        // inter-exécutions) : ce sont elles qui rendent l'explication de niveau expert.
        applyEvidence(report, anchor, windowLogs, importId, resolvedProcess, resolvedAction);
        return report;
    }

    private static final java.time.format.DateTimeFormatter HMS =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern TOOK_MS = Pattern.compile(
            "took\\s*\\[?\\s*(\\d{1,10})\\s*]?\\s*ms", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW_COUNT = Pattern.compile(
            "\\[?\\s*(\\d{1,9})\\s*]?\\s*rows?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMORY_MO = Pattern.compile(
            "memory usage \\(mo\\).*?(\\d{2,6})", Pattern.CASE_INSENSITIVE);

    /**
     * Calcule les preuves « d'expert ». Entièrement défensif : toute exception est
     * avalée pour ne jamais compromettre l'analyse principale déjà produite.
     */
    private void applyEvidence(LatencyOriginReportDto report,
                               LogEntry anchor,
                               List<LogEntry> windowLogs,
                               Long importId,
                               String process,
                               String action) {
        try {
            // 1) Trou silencieux : plus grand intervalle sans aucun log dans la fenêtre.
            LocalDateTime prev = null;
            long maxGap = 0;
            LocalDateTime gapFrom = null, gapTo = null;
            for (LogEntry log : windowLogs) {
                LocalDateTime ts = log.getLogTimestamp();
                if (ts == null) continue;
                if (prev != null) {
                    long d = ChronoUnit.MILLIS.between(prev, ts);
                    if (d > maxGap) { maxGap = d; gapFrom = prev; gapTo = ts; }
                }
                prev = ts;
            }
            if (maxGap >= 3000 && gapFrom != null) {
                report.setSilentGapMs(maxGap);
                report.setSilentGapFrom(gapFrom.format(HMS));
                report.setSilentGapTo(gapTo.format(HMS));
            }

            // 2) Pic mémoire (Mo) dans la fenêtre.
            int heapPeak = 0;
            for (LogEntry log : windowLogs) {
                var m = MEMORY_MO.matcher(combine(log));
                while (m.find()) {
                    try { heapPeak = Math.max(heapPeak, Integer.parseInt(m.group(1))); }
                    catch (NumberFormatException ignore) { }
                }
            }
            if (heapPeak > 0) report.setHeapPeakMo(heapPeak);

            // 3) Bascule de fichiers de logs (signe d'attente/contention).
            java.util.LinkedHashSet<String> files = new java.util.LinkedHashSet<>();
            for (LogEntry log : windowLogs) {
                if (notBlank(log.getSourceFileName())) files.add(log.getSourceFileName());
            }
            if (!files.isEmpty()) report.setWindowFiles(new ArrayList<>(files));

            // 4) Comparaison inter-exécutions de la même opération (preuve d'intermittence).
            report.setExecutionSamples(buildExecutionSamples(importId, process, action, anchor));
        } catch (RuntimeException ex) {
            // Les preuves sont optionnelles : on n'interrompt jamais l'analyse.
        }
    }

    /**
     * Cherche dans l'import d'autres exécutions de la même opération (même processus + action),
     * et pour chacune récupère la durée du moteur de règles et de la recherche SQL voisine.
     * C'est la « preuve par comparaison » : SQL stable + règles très variables = cause intermittente.
     */
    private List<LatencyExecutionSampleDto> buildExecutionSamples(Long importId, String process,
                                                                  String action, LogEntry anchor) {
        if (!notBlank(process) || !notBlank(action)) {
            return null;
        }
        // Ne jamais charger tout l'import : 100k+ entités = OOM (même si < 500k).
        long total = logEntryRepository.countByLogImportId(importId);
        if (total == 0 || total > 30_000) {
            return null;
        }
        List<LogEntry> importLogs = logEntryRepository.findByLogImportIdOrderByLogTimestampAscIdAsc(
                importId, PageRequest.of(0, (int) total));
        if (importLogs.isEmpty()) {
            return null;
        }
        String p = process.toLowerCase(Locale.ROOT);
        String a = action.toLowerCase(Locale.ROOT);
        LocalDateTime anchorTs = anchor != null ? anchor.getLogTimestamp() : null;

        List<LatencyExecutionSampleDto> samples = new ArrayList<>();
        for (int i = 0; i < importLogs.size(); i++) {
            String text = combine(importLogs.get(i)).toLowerCase(Locale.ROOT);
            if (!text.contains("running rules") || !text.contains(a)) continue;
            if (!p.isBlank() && !text.contains(p) && !text.contains(strip(p))) continue;

            Long rulesMs = extractTook(combine(importLogs.get(i)));
            if (rulesMs == null) continue;

            LocalDateTime ts = importLogs.get(i).getLogTimestamp();

            // Recherche SQL associée : searchComposantByRoot le plus proche AVANT, sous 120 s.
            Long sqlMs = null;
            Integer rowCount = null;
            for (int j = i - 1; j >= 0 && i - j <= 500; j--) {
                LogEntry pj = importLogs.get(j);
                if (ts != null && pj.getLogTimestamp() != null
                        && ChronoUnit.SECONDS.between(pj.getLogTimestamp(), ts) > 120) break;
                String tj = combine(pj).toLowerCase(Locale.ROOT);
                if (tj.contains("searchcomposantbyroot")) {
                    Long s = extractTook(combine(pj));
                    if (s != null) { sqlMs = s; rowCount = extractRowCount(combine(pj)); break; }
                }
            }

            LatencyExecutionSampleDto sample = new LatencyExecutionSampleDto();
            sample.setTime(ts != null ? ts.format(HMS) : "?");
            sample.setRulesMs(rulesMs);
            sample.setSqlMs(sqlMs);
            sample.setRowCount(rowCount);
            sample.setCurrent(anchorTs != null && ts != null
                    && Math.abs(ChronoUnit.SECONDS.between(ts, anchorTs)) <= 5);
            samples.add(sample);
        }

        if (samples.size() < 2) {
            return samples.isEmpty() ? null : samples;
        }
        // Garde les plus parlants (règles les plus longues) puis re-trie chronologiquement.
        if (samples.size() > 8) {
            samples.sort(Comparator.comparingLong(
                    (LatencyExecutionSampleDto s) -> s.getRulesMs() != null ? s.getRulesMs() : 0L).reversed());
            List<LatencyExecutionSampleDto> top = new ArrayList<>(samples.subList(0, 8));
            samples = top;
        }
        samples.sort(Comparator.comparing(s -> s.getTime() != null ? s.getTime() : ""));
        return samples;
    }

    private Long extractTook(String text) {
        if (!notBlank(text)) return null;
        var m = TOOK_MS.matcher(text);
        Long last = null;
        while (m.find()) {
            try { last = Long.parseLong(m.group(1)); } catch (NumberFormatException ignore) { }
        }
        return last;
    }

    private Integer extractRowCount(String text) {
        if (!notBlank(text)) return null;
        var m = ROW_COUNT.matcher(text);
        return m.find() ? safeInt(m.group(1)) : null;
    }

    private Integer safeInt(String v) {
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return null; }
    }

    private String strip(String s) {
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    /**
     * Passe la fenêtre déjà construite au {@link LatencyRootCauseClassifier} et reporte
     * uniquement les métadonnées enrichies (catégorie, évidences, SQL racine, IDs de cause).
     * Défensif : toute erreur d'enrichissement ne doit jamais faire échouer l'analyse.
     */
    private void applyRootCauseClassification(LatencyOriginReportDto report,
                                              LogEntry anchor,
                                              List<LogEntry> windowLogs,
                                              List<LatencyTimelineStepDto> steps) {
        try {
            Classification classification = rootCauseClassifier.classify(anchor, windowLogs, steps);
            if (classification == null) {
                return;
            }
            if (classification.category != null) {
                report.setCategory(classification.category.name());
            }
            report.setEvidence(classification.evidence);
            report.setCauseLogIds(windowLogs.stream()
                    .map(LogEntry::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));

            if (classification.evidence != null) {
                Object sql = classification.evidence.get("sql_query");
                if (sql instanceof String s) {
                    report.setRootSqlQuery(s);
                }
                Object joins = classification.evidence.get("sql_join_count");
                if (joins instanceof Number n) {
                    report.setRootSqlJoinCount(n.intValue());
                }
                Object tables = classification.evidence.get("sql_tables");
                if (tables instanceof List<?> list) {
                    report.setRootSqlTables(list.stream()
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .collect(Collectors.toList()));
                }
                Object rows = classification.evidence.get("row_count");
                if (rows instanceof Number n) {
                    report.setRootSqlRowCount(n.intValue());
                }
            }
        } catch (RuntimeException ex) {
            // L'enrichissement est optionnel : on n'interrompt jamais l'analyse principale.
            report.setCategory(null);
        }
    }

    /**
     * Construit l'avertissement de mesure si la fenêtre contient au moins un « took »
     * anomalique (durée impossible). On l'expose au client pour expliquer pourquoi cette
     * valeur brute n'est pas retenue comme la vraie latence.
     */
    private String buildMeasurementWarning(List<LatencyTimelineStepDto> steps, LogEntry anchor) {
        LatencyTimelineStepDto worstSuspect = steps.stream()
                .filter(LatencyTimelineStepDto::isSuspect)
                .filter(s -> s.getDurationMs() != null)
                .max(Comparator.comparingLong(LatencyTimelineStepDto::getDurationMs))
                .orElse(null);
        if (worstSuspect != null) {
            return worstSuspect.getSuspectReason();
        }
        if (anchor != null && LatencyPlausibility.isImplausible(resolveDuration(anchor))) {
            return LatencyPlausibility.reason(resolveDuration(anchor));
        }
        return null;
    }

    private LogEntry resolveAnchor(Long importId, Long evidenceLogId, String sessionId, String filterCode) {
        // Ancrage : privilégier la session complète (les règles n'ont souvent pas le filter code
        // de la recherche amont). Le filtre ne sert qu'en secours si la session est vide.
        List<LogEntry> sessionCandidates = loadCandidateLogs(importId, sessionId, null);
        List<LogEntry> candidates = sessionCandidates.isEmpty()
                ? loadCandidateLogs(importId, sessionId, filterCode)
                : sessionCandidates;
        if (candidates.isEmpty()) {
            if (evidenceLogId != null) {
                return logEntryRepository.findById(evidenceLogId)
                        .filter(log -> belongsToImport(log, importId))
                        .orElse(null);
            }
            return null;
        }

        LogEntry slowest = candidates.stream()
                .filter(log -> resolveDuration(log) >= SLOW_THRESHOLD_MS)
                .max(Comparator.comparingLong(this::resolveDuration))
                .orElseGet(() -> candidates.stream()
                        .max(Comparator.comparingLong(this::resolveDuration))
                        .orElse(candidates.get(candidates.size() - 1)));

        if (evidenceLogId == null) {
            return slowest;
        }

        LogEntry evidence = logEntryRepository.findById(evidenceLogId)
                .filter(log -> belongsToImport(log, importId))
                .orElse(null);

        if (evidence == null) {
            return slowest;
        }

        long slowestMs = resolveDuration(slowest);
        long evidenceMs = resolveDuration(evidence);
        if (evidenceMs >= slowestMs * 0.85) {
            return evidence;
        }
        return slowest;
    }

    private List<LogEntry> loadCandidateLogs(Long importId, String sessionId, String filterCode) {
        List<LogEntry> logs;
        if (notBlank(sessionId)) {
            logs = logEntryRepository.findByLogImportIdAndSessionIdOrderByLogTimestampAsc(importId, sessionId);
        } else {
            // Jamais tout l'import : seulement les lignes lentes plafonnées.
            logs = logEntryRepository.findSlowByImportId(
                    importId, SLOW_THRESHOLD_MS, PageRequest.of(0, 2_000));
        }

        String filterNorm = normalize(filterCode);
        if (!notBlank(filterNorm)) {
            return logs;
        }

        return logs.stream()
                .filter(log -> matchesFilter(log, filterNorm))
                .toList();
    }

    private List<LogEntry> loadScopeLogs(Long importId, String sessionId, String uuid, String filterCode) {
        List<LogEntry> sessionLogs = notBlank(sessionId)
                ? logEntryRepository.findByLogImportIdAndSessionIdOrderByLogTimestampAsc(importId, sessionId)
                : List.of();

        List<LogEntry> scoped = narrowScope(sessionLogs, uuid, filterCode);
        // Filtre trop étroit (ex. AMPEZRE sur la recherche amont) : les logs running rules /
        // doAction n'ont souvent PAS ce filter code → élargir à la session complète.
        if (scoped.size() >= MIN_SCOPE_LOGS
                && !isRulesBlindFilterScope(scoped, sessionLogs)) {
            return scoped;
        }
        if (sessionLogs.size() >= MIN_SCOPE_LOGS
                && (scoped.size() < MIN_SCOPE_LOGS || isRulesBlindFilterScope(scoped, sessionLogs))) {
            return sessionLogs;
        }

        // Fallback léger : pas de findAll import (OOM).
        List<LogEntry> slow = logEntryRepository.findSlowByImportId(
                importId, SLOW_THRESHOLD_MS, PageRequest.of(0, 2_000));
        List<LogEntry> importScoped = narrowScope(slow, uuid, filterCode);

        if (importScoped.size() > scoped.size()) {
            return importScoped;
        }
        if (!sessionLogs.isEmpty()) {
            return sessionLogs;
        }
        return scoped.isEmpty() ? importScoped : scoped;
    }

    /**
     * True si le scope filtré a perdu les lignes « running rules / doAction » présentes
     * dans la session (typique : filtre de recherche amont ≠ process règles).
     */
    private boolean isRulesBlindFilterScope(List<LogEntry> scoped, List<LogEntry> sessionLogs) {
        if (scoped == null || sessionLogs == null || scoped.size() >= sessionLogs.size()) {
            return false;
        }
        boolean sessionHasRules = sessionLogs.stream().anyMatch(this::looksLikeRulesOrDoAction);
        if (!sessionHasRules) {
            return false;
        }
        boolean scopedHasRules = scoped.stream().anyMatch(this::looksLikeRulesOrDoAction);
        return !scopedHasRules;
    }

    private boolean looksLikeRulesOrDoAction(LogEntry log) {
        String lower = combine(log).toLowerCase(Locale.ROOT);
        return lower.contains("running rules")
                || lower.contains("start fire rules")
                || lower.contains("doaction for");
    }

    private List<LogEntry> narrowScope(List<LogEntry> logs, String uuid, String filterCode) {
        if (logs == null || logs.isEmpty()) {
            return List.of();
        }

        List<LogEntry> byFilter = filterByCode(logs, filterCode);
        List<LogEntry> byUuid = filterByUuid(logs, uuid);

        if (!byFilter.isEmpty() && !byUuid.isEmpty()) {
            List<LogEntry> intersection = intersectLogs(byFilter, byUuid);
            if (intersection.size() >= MIN_SCOPE_LOGS) {
                return intersection;
            }
            return byFilter.size() >= byUuid.size() ? byFilter : byUuid;
        }

        if (!byFilter.isEmpty()) {
            return byFilter;
        }

        if (!byUuid.isEmpty()) {
            return byUuid;
        }

        return logs;
    }

    private List<LogEntry> filterByUuid(List<LogEntry> logs, String uuid) {
        if (!notBlank(uuid)) {
            return List.of();
        }
        return logs.stream()
                .filter(log -> messageContainsUuid(combine(log), uuid))
                .toList();
    }

    private List<LogEntry> filterByCode(List<LogEntry> logs, String filterCode) {
        if (!notBlank(filterCode)) {
            return List.of();
        }
        String filterNorm = normalize(filterCode);
        return logs.stream()
                .filter(log -> matchesFilter(log, filterNorm))
                .toList();
    }

    private List<LogEntry> intersectLogs(List<LogEntry> a, List<LogEntry> b) {
        java.util.Set<Long> ids = a.stream()
                .map(LogEntry::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return b.stream()
                .filter(log -> log.getId() != null && ids.contains(log.getId()))
                .toList();
    }

    private boolean matchesFilter(LogEntry log, String filterNorm) {
        GraphExtraction ex = logPatternExtractor.extractGraph(log);
        String logFilter = coalesce(ex.getFilter(), extractFilter(combine(log)));
        if (notBlank(logFilter) && filterNorm.equals(normalize(logFilter))) {
            return true;
        }
        return combine(log).toUpperCase(Locale.ROOT).contains(filterNorm);
    }

    private static final String[] WINDOW_START_MARKERS = {
            "searchcomposantbyroot start execute",
            "begin executing request",
            "fetching filter",
            "start filtre",
            "start ws_",
            "ws_",
            "running rules",
            "start fire rules",
            "start dosearch",
            "start process",
            "doaction for",
            "start transit",
            "aggregate data for",
            "printarchive",
            "start save",
            "start saveorupdate",
            "validateattributesoperation",
            "saveoperations"
    };

    private static final String[] WINDOW_END_MARKERS = {
            "global searchcomposantbyroot",
            "searchcomposantbyroot end execute",
            "end partitional searchcomposantbyroot",
            "rendering result",
            "in host took",
            "end filtre",
            "persist operation",
            "transit task",
            "end fire rules",
            "end ws_",
            "ws_ end",
            "doaction for",
            "total time save",
            "end saveorupdate",
            "printarchive",
            "cups"
    };

    private List<LogEntry> extractAdaptiveWindow(List<LogEntry> scopeLogs, int anchorIndex) {
        if (scopeLogs.isEmpty()) {
            return List.of();
        }

        int anchor = anchorIndex >= 0 ? anchorIndex : indexOfSlowestLog(scopeLogs);

        int start = findWindowStart(scopeLogs, anchor);
        int end = findWindowEnd(scopeLogs, anchor);

        if (end < start) {
            end = Math.min(scopeLogs.size() - 1, anchor + 100);
        }

        List<LogEntry> window = new ArrayList<>(scopeLogs.subList(start, end + 1));
        // Transit / doAction : le lookback marqueur (800 lignes) rate souvent « running rules »
        // noyé sous des milliers de MAJ_Insert (matchall). Réinjecter depuis le scope par durée.
        window = mergeNestedOperationLogs(window, scopeLogs, scopeLogs.get(anchor));
        return clampToOperation(window, scopeLogs.get(anchor));
    }

    /**
     * Réinjecte les logs nestés (running rules, doAction, SAVE, SQL…) présents dans le
     * scope temporel de l'ancre mais absents de la fenêtre marqueur.
     */
    private List<LogEntry> mergeNestedOperationLogs(List<LogEntry> window,
                                                    List<LogEntry> scopeLogs,
                                                    LogEntry anchor) {
        if (anchor == null || scopeLogs == null || scopeLogs.isEmpty()) {
            return window;
        }
        String anchorText = combine(anchor).toLowerCase(Locale.ROOT);
        boolean nestAnchor = anchorText.contains("transit task")
                || anchorText.contains("doaction")
                || anchorText.contains("running rules")
                || anchorText.contains("fire rules")
                || anchorText.contains("start transit");
        if (!nestAnchor) {
            return window;
        }
        LocalDateTime anchorTs = anchor.getLogTimestamp();
        long durationMs = resolveDuration(anchor);
        if (anchorTs == null || durationMs <= 0) {
            return window;
        }
        long boundMs = LatencyPlausibility.isImplausible(durationMs) ? 900_000L : durationMs;
        LocalDateTime lower = anchorTs.minus(boundMs + 90_000, ChronoUnit.MILLIS);
        LocalDateTime upper = anchorTs.plus(8000, ChronoUnit.MILLIS);

        java.util.Set<Long> seen = window.stream()
                .map(LogEntry::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<LogEntry> merged = new ArrayList<>(window);
        for (LogEntry log : scopeLogs) {
            if (log.getId() != null && seen.contains(log.getId())) {
                continue;
            }
            LocalDateTime ts = log.getLogTimestamp();
            if (ts != null && (ts.isBefore(lower) || ts.isAfter(upper))) {
                continue;
            }
            if (isNestRelatedLog(combine(log).toLowerCase(Locale.ROOT))) {
                merged.add(log);
                if (log.getId() != null) {
                    seen.add(log.getId());
                }
            }
        }
        if (merged.size() == window.size()) {
            return window;
        }
        merged.sort(Comparator
                .comparing(LogEntry::getLogTimestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LogEntry::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return merged;
    }

    private boolean isNestRelatedLog(String lower) {
        return lower.contains("running rules")
                || lower.contains("fire rules")
                || lower.contains("doaction")
                || lower.contains("transit task")
                || lower.contains("start transit")
                || lower.contains("persist operation")
                || lower.contains("total time save")
                || lower.contains("maj_insert")
                || lower.contains("preparesearchbyroot")
                || lower.contains("global searchcomposantbyroot")
                || lower.contains("starttask")
                || lower.contains("endtask")
                || lower.contains("insertarrayrel");
    }

    /**
     * Borne la fenêtre à l'opération réellement concernée. Pour une ancre de type "took"
     * (opération terminée), les logs pertinents sont AVANT l'ancre, dans l'intervalle
     * [ancre - durée, ancre]. Sans cette borne, les marqueurs de fin happent les opérations
     * VOISINES qui suivent (ex. process.ED SUPERVISION) et polluent l'analyse (faux 505 ms,
     * groupes de règles parasites). On ne borne que si l'ancre porte une durée mesurée.
     */
    private List<LogEntry> clampToOperation(List<LogEntry> window, LogEntry anchor) {
        if (window.isEmpty() || anchor == null) {
            return window;
        }
        LocalDateTime anchorTs = anchor.getLogTimestamp();
        long durationMs = resolveDuration(anchor);
        if (anchorTs == null || durationMs <= 0) {
            return window;
        }
        // Un took anomalique (plusieurs heures) ne doit pas élargir la fenêtre à des
        // opérations voisines : on plafonne la borne basse à une durée réaliste.
        long boundMs = LatencyPlausibility.isImplausible(durationMs) ? 900_000L : durationMs;
        // Marge avant large : capturer la recherche pré-règles (volume) + START fire rules
        // (souvent 5–90 s avant le took "running rules ... in host").
        LocalDateTime lower = anchorTs.minus(boundMs + 90_000, ChronoUnit.MILLIS);
        LocalDateTime upper = anchorTs.plus(8000, ChronoUnit.MILLIS);

        List<LogEntry> clamped = window.stream()
                .filter(log -> {
                    LocalDateTime ts = log.getLogTimestamp();
                    return ts == null || (!ts.isBefore(lower) && !ts.isAfter(upper));
                })
                .collect(Collectors.toList());

        return clamped.size() >= MIN_SCOPE_LOGS ? clamped : window;
    }

    private int findWindowStart(List<LogEntry> scopeLogs, int anchor) {
        int start = anchor;
        for (int i = anchor; i >= 0; i--) {
            if (matchesAnyMarker(combine(scopeLogs.get(i)), WINDOW_START_MARKERS)) {
                start = i;
            }
            if (anchor - i > 4000) {
                break;
            }
        }

        for (int i = start; i >= Math.max(0, start - 60); i--) {
            String lower = combine(scopeLogs.get(i)).toLowerCase(Locale.ROOT);
            if (lower.contains("fetching filter") || lower.contains("begin executing request")
                    || lower.contains("start fire rules") || lower.contains("start ws_")
                    || lower.contains("doaction for")) {
                start = i;
                break;
            }
        }
        return start;
    }

    private int findWindowEnd(List<LogEntry> scopeLogs, int anchor) {
        int end = anchor;
        for (int i = anchor; i < scopeLogs.size(); i++) {
            if (matchesAnyMarker(combine(scopeLogs.get(i)), WINDOW_END_MARKERS)) {
                end = i;
            }
            if (i - anchor > 4000) {
                break;
            }
        }
        return end;
    }

    private boolean matchesAnyMarker(String text, String[] markers) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String marker : markers) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private int indexOfSlowestLog(List<LogEntry> logs) {
        int best = 0;
        long bestMs = -1;
        for (int i = 0; i < logs.size(); i++) {
            long ms = resolveDuration(logs.get(i));
            if (ms > bestMs) {
                bestMs = ms;
                best = i;
            }
        }
        return best;
    }

    private long resolveDuration(LogEntry log) {
        if (log.getDurationMs() != null && log.getDurationMs() > 0) {
            return log.getDurationMs();
        }
        Long parsed = logPatternExtractor.extractDurationMsFromText(
                log.getMessage(), log.getRawLog(), log.getProcessName());
        return parsed != null ? parsed : 0L;
    }

    private int indexOfLog(List<LogEntry> logs, Long id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < logs.size(); i++) {
            if (Objects.equals(logs.get(i).getId(), id)) {
                return i;
            }
        }
        return -1;
    }

    private boolean belongsToImport(LogEntry log, Long importId) {
        return log.getLogImport() != null && Objects.equals(log.getLogImport().getId(), importId);
    }

    private boolean messageContainsUuid(String text, String uuid) {
        if (!notBlank(uuid) || !notBlank(text)) {
            return false;
        }
        String escaped = Pattern.quote(uuid.trim());
        // Accepte uuid [N] et [N] doAction… (même valeur, formes WORKS différentes).
        return Pattern.compile("(?:uuid\\s*)?\\[\\s*" + escaped + "\\s*]", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find();
    }

    private String extractUuidFromText(String text) {
        if (!notBlank(text)) {
            return null;
        }
        var labeled = UUID_IN_MESSAGE.matcher(text);
        if (labeled.find()) {
            return labeled.group(1).trim();
        }
        var leading = LEADING_BRACKET_UUID.matcher(text);
        return leading.find() ? leading.group(1).trim() : null;
    }

    private String extractFilter(String text) {
        var matcher = Pattern.compile("filter code\\s*\\[\\s*([^\\]]+?)\\s*]",
                Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static final Pattern SCREEN_IN_MESSAGE = Pattern.compile(
            "(?:composer|dynascreen|view\\s*mode|écran|ecran|screen)\\s*\\[\\s*([^\\]]+?)\\s*]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RUNNING_RULES_PIPE = Pattern.compile(
            "running rules\\|([^|]+)\\|([^|]*)\\|([^|]*)\\|",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DO_ACTION_LABEL = Pattern.compile(
            "doAction for - actionName\\s*:\\s*([^,]+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Préfère un vrai process métier (process.MATCX_…) aux placeholders de colonne
     * du type {@code -0--0-SAVE} / {@code processFilter-0-…}.
     */
    private String preferBusinessProcess(String... candidates) {
        String fallback = null;
        for (String candidate : candidates) {
            if (!notBlank(candidate)) {
                continue;
            }
            String cleaned = cleanToken(candidate);
            if (cleaned == null) {
                continue;
            }
            if (!isPlaceholderProcess(cleaned)) {
                return cleaned;
            }
            if (fallback == null) {
                fallback = cleaned;
            }
        }
        return fallback;
    }

    private static boolean isPlaceholderProcess(String process) {
        if (process == null || process.isBlank()) {
            return true;
        }
        String p = process.trim();
        if (p.equalsIgnoreCase("-0--0-SAVE") || p.equalsIgnoreCase("SAVE")) {
            return true;
        }
        // Colonne technique : -0--0-SAVE, processFilter-0-ZRE22-0-LOAD, etc.
        return p.matches("(?i)(?:processFilter)?-?\\d+--?\\d+-?[A-Za-z_]*")
                || p.matches("(?i)processFilter-.*");
    }

    private String extractRulesPipeProcess(String text) {
        if (!notBlank(text)) {
            return null;
        }
        var m = RUNNING_RULES_PIPE.matcher(text);
        return m.find() ? cleanToken(m.group(1)) : null;
    }

    private String extractRulesPipeAction(String text) {
        if (!notBlank(text)) {
            return null;
        }
        var m = RUNNING_RULES_PIPE.matcher(text);
        if (!m.find()) {
            return null;
        }
        // Préférer l'action métier (matchall) à la tâche (Match) si les deux existent.
        String action = cleanToken(m.group(3));
        String task = cleanToken(m.group(2));
        return coalesce(action, task);
    }

    private String extractDoActionLabel(String text) {
        if (!notBlank(text)) {
            return null;
        }
        var m = DO_ACTION_LABEL.matcher(text);
        return m.find() ? cleanToken(m.group(1)) : null;
    }

    private String extractProcessFromLogs(List<LogEntry> logs) {
        if (logs == null) {
            return null;
        }
        for (LogEntry log : logs) {
            String fromPipe = extractRulesPipeProcess(combine(log));
            if (notBlank(fromPipe) && !isPlaceholderProcess(fromPipe)) {
                return fromPipe;
            }
        }
        return null;
    }

    private String extractDoActionFromLogs(List<LogEntry> logs) {
        if (logs == null) {
            return null;
        }
        for (LogEntry log : logs) {
            String label = extractDoActionLabel(combine(log));
            if (notBlank(label)) {
                return label;
            }
        }
        return null;
    }

    private String extractRulesPipeActionFromLogs(List<LogEntry> logs) {
        if (logs == null) {
            return null;
        }
        for (LogEntry log : logs) {
            String action = extractRulesPipeAction(combine(log));
            if (notBlank(action)) {
                return action;
            }
        }
        return null;
    }

    private String extractFilterFromLogs(List<LogEntry> logs) {
        if (logs == null) {
            return null;
        }
        for (LogEntry log : logs) {
            String filter = extractFilter(combine(log));
            if (notBlank(filter)) {
                return filter;
            }
        }
        return null;
    }

    private String extractUuidFromLogs(List<LogEntry> logs) {
        if (logs == null) {
            return null;
        }
        for (LogEntry log : logs) {
            String u = extractUuidFromText(combine(log));
            if (notBlank(u)) {
                return u;
            }
        }
        return null;
    }

    private String extractAction(String text) {
        if (!notBlank(text)) {
            return null;
        }
        var matcher = Pattern.compile("actionName\\s*\\[\\s*([^\\]]+?)\\s*]",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if (matcher.find()) {
            return cleanToken(matcher.group(1));
        }
        var bracket = Pattern.compile("ACTION_NAME\\s*\\[\\s*([^\\]]+?)\\s*]",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if (bracket.find()) {
            return cleanToken(bracket.group(1));
        }
        return null;
    }

    private String extractActionFromLogs(List<LogEntry> logs) {
        if (logs == null) {
            return null;
        }
        for (LogEntry log : logs) {
            String action = extractAction(combine(log));
            if (notBlank(action)) {
                return action;
            }
        }
        return null;
    }

    private String extractScreenName(String text) {
        if (!notBlank(text)) {
            return null;
        }
        var matcher = SCREEN_IN_MESSAGE.matcher(text);
        return matcher.find() ? cleanToken(matcher.group(1)) : null;
    }

    private String extractScreenNameFromLogs(List<LogEntry> logs) {
        if (logs == null) {
            return null;
        }
        for (LogEntry log : logs) {
            String screen = extractScreenName(combine(log));
            if (notBlank(screen)) {
                return screen;
            }
        }
        return null;
    }

    /**
     * Un vrai nom d'écran est une étiquette métier. Une référence d'objet Java
     * (ex. "com.caciopee...GenieScreenViewerVM@55883b39") ou un nom de classe complet
     * n'est PAS un nom d'écran exploitable par le client → on renvoie null (affiché "n/a").
     */
    private String sanitizeScreen(String screen) {
        if (!notBlank(screen)) {
            return null;
        }
        String s = screen.trim();
        if (s.matches(".*@[0-9a-fA-F]{4,}$") || s.matches("(?:[a-zA-Z0-9_]+\\.){2,}[A-Za-z0-9_$]+")) {
            return null;
        }
        return s;
    }

    private String cleanToken(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.isEmpty() || "null".equalsIgnoreCase(cleaned)) {
            return null;
        }
        return cleaned;
    }

    private String buildScopeDescription(String sessionId, String uuid, String filterCode) {
        List<String> parts = new ArrayList<>();
        if (notBlank(sessionId)) {
            parts.add("session " + sessionId);
        }
        if (notBlank(uuid)) {
            parts.add("uuid " + uuid);
        }
        if (notBlank(filterCode)) {
            parts.add("filtre " + filterCode);
        }
        return parts.isEmpty() ? "périmètre import complet" : String.join(" + ", parts);
    }

    private String combine(LogEntry log) {
        return safe(log.getMessage()) + " " + safe(log.getRawLog());
    }

    private String normalize(String value) {
        return value != null ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String coalesce(String... values) {
        for (String value : values) {
            if (notBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
