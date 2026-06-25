package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.*;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogEntrySpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiContextBuilderServiceImpl implements AiContextBuilderService {

    private final LogEntryRepository logEntryRepository;
    private final LogPatternExtractor logPatternExtractor;
    private final LogGroupAnalysisService logGroupAnalysisService;
    private final LogAnalysisService logAnalysisService;
    private final AiContextCacheService contextCache;

    public AiContextBuilderServiceImpl(LogEntryRepository logEntryRepository,
                                       LogPatternExtractor logPatternExtractor,
                                       LogGroupAnalysisService logGroupAnalysisService,
                                       LogAnalysisService logAnalysisService,
                                       AiContextCacheService contextCache) {
        this.logEntryRepository = logEntryRepository;
        this.logPatternExtractor = logPatternExtractor;
        this.logGroupAnalysisService = logGroupAnalysisService;
        this.logAnalysisService = logAnalysisService;
        this.contextCache = contextCache;
    }

    @Override
    public AiContextResponseDto buildContext(AiContextRequestDto request) {
        String cacheKey = AiContextCacheService.buildKey(request);
        AiContextCacheService.CacheEntry cached = contextCache.get(cacheKey);

        List<LogEntry> logs;
        GroupAnalysisResponseDto groupAnalysis;
        String baseContext;

        if (cached != null) {
            logs = cached.logs();
            groupAnalysis = cached.groupAnalysis();
            baseContext = cached.baseContext();
        } else {
            logs = loadLogs(request);
            groupAnalysis = resolveGroupAnalysis(request);
            List<IncidentCandidateDto> incidents = resolveIncidents(request, groupAnalysis);
            baseContext = buildBaseContext(request, logs, groupAnalysis, incidents);
            contextCache.put(cacheKey, new AiContextCacheService.CacheEntry(
                    baseContext,
                    logs,
                    groupAnalysis,
                    Instant.now().plus(15, ChronoUnit.MINUTES)
            ));
        }

        AiContextResponseDto response = new AiContextResponseDto();
        response.setTotalLogsUsed(logs.size());

        if (logs.isEmpty()) {
            response.setContext("Aucun log trouvé pour construire le contexte IA.");
            response.setContextSummary(response.getContext());
            return response;
        }

        StringBuilder full = new StringBuilder(baseContext);

        List<String> focusTerms = resolveFocusTerms(request.getFocusQuery(), logs, request);
        List<LogEntry> focusedLogs = resolveFocusedLogs(logs, request, focusTerms);
        if (focusedLogs.isEmpty() && isGeneralOverviewQuestion(request, focusTerms)) {
            focusedLogs = logs;
        }

        if (hasFocusScope(request, focusTerms, focusedLogs)) {
            full.append("\n\nFOCUS SUR LA QUESTION\n");
            full.append("=====================\n\n");
            full.append(buildFocusSection(request, focusTerms, logs, focusedLogs));
        }

        String budgeted = AiContextBudget.apply(full.toString(), 48000);
        response.setContext(budgeted);
        response.setContextSummary(AiContextBudget.extractSummary(budgeted, 2800));
        return response;
    }

    private GroupAnalysisResponseDto resolveGroupAnalysis(AiContextRequestDto request) {
        if (request.getImportIds() == null || request.getImportIds().isEmpty()) {
            return new GroupAnalysisResponseDto();
        }
        if (request.getGroupKey() == null || request.getGroupKey().isBlank()) {
            return new GroupAnalysisResponseDto();
        }
        GroupAnalysisRequestDto gaReq = new GroupAnalysisRequestDto();
        gaReq.setImportIds(request.getImportIds());
        gaReq.setGroupBy(request.getGroupBy());
        gaReq.setGroupKey(request.getGroupKey());
        gaReq.setDateFrom(request.getDateFrom());
        gaReq.setDateTo(request.getDateTo());
        return logGroupAnalysisService.analyzeGroup(gaReq);
    }

    private List<IncidentCandidateDto> resolveIncidents(AiContextRequestDto request,
                                                        GroupAnalysisResponseDto groupAnalysis) {
        if (request.getImportIds() == null || request.getImportIds().isEmpty()) {
            return List.of();
        }
        Long importId = request.getImportIds().get(0);
        String groupBy = request.getGroupBy();
        String groupKey = request.getGroupKey();
        return logAnalysisService.detectImportIncidents(importId).stream()
                .filter(i -> matchesIncidentGroup(groupBy, groupKey, i))
                .limit(12)
                .toList();
    }

    private boolean matchesIncidentGroup(String groupBy, String groupKey, IncidentCandidateDto incident) {
        if (groupBy == null || groupKey == null) return true;
        return switch (groupBy) {
            case "userName" -> groupKey.equalsIgnoreCase(incident.getUserName());
            case "sessionId" -> groupKey.equals(incident.getSessionId());
            case "processName" -> groupKey.equalsIgnoreCase(incident.getProcessName());
            default -> true;
        };
    }

    private String buildBaseContext(AiContextRequestDto request,
                                    List<LogEntry> logs,
                                    GroupAnalysisResponseDto groupAnalysis,
                                    List<IncidentCandidateDto> incidents) {
        StringBuilder ctx = new StringBuilder();

        ctx.append("CONTEXTE D'ANALYSE WORKS\n");
        ctx.append("========================\n\n");

        ctx.append("SYNTHÈSE MÉTIER\n");
        ctx.append("---------------\n");
        ctx.append(EmployeeDiagnosticReportBuilder.buildSummary(groupAnalysis, incidents, null)).append("\n\n");

        ctx.append("PÉRIMÈTRE\n");
        ctx.append("---------\n");
        ctx.append("- Regroupement : ").append(nullSafe(request.getGroupBy(), "non précisé")).append("\n");
        ctx.append("- Clé : ").append(nullSafe(request.getGroupKey(), "non précisé")).append("\n");
        if ("userName".equalsIgnoreCase(request.getGroupBy())) {
            ctx.append("- Note : tous les logs appartiennent à cet utilisateur.\n");
        }
        ctx.append("- Logs analysés : ").append(logs.size()).append("\n");
        ctx.append("- Période : ").append(firstTimestamp(logs)).append(" → ").append(lastTimestamp(logs)).append("\n\n");

        ctx.append("CHIFFRES CLÉS\n");
        ctx.append("-------------\n");
        ctx.append("- Erreurs critiques : ").append(countErrors(logs)).append("\n");
        ctx.append("- Avertissements / règles absentes : ").append(countWarnings(logs)).append("\n");
        ctx.append("- Recherches sans résultat : ").append(countZeroRows(logs)).append("\n");
        ctx.append("- Signaux performance/mémoire : ").append(countPerformanceSignals(logs)).append("\n\n");

        if (!incidents.isEmpty()) {
            ctx.append("INCIDENTS DE LATENCE\n");
            ctx.append("--------------------\n");
            incidents.forEach(inc -> {
                ctx.append("- ").append(nullSafe(inc.getTitle(), "Incident"));
                if (inc.getMaxDurationMs() != null) ctx.append(" — ").append(inc.getMaxDurationMs()).append(" ms");
                if (inc.getProcessName() != null) ctx.append(" — process ").append(inc.getProcessName());
                if (inc.getFilterCode() != null) ctx.append(" — filtre ").append(inc.getFilterCode());
                ctx.append("\n");
                String detail = inc.getExplanation() != null ? inc.getExplanation() : inc.getProbableCause();
                if (detail != null && !detail.isBlank()) ctx.append("  ").append(detail.trim()).append("\n");
            });
            ctx.append("\n");
        }

        ctx.append("ACTIVITÉ MÉTIER\n");
        ctx.append("---------------\n");
        ctx.append("- Process principaux : ").append(topValues(logs, this::extractProcess, 8)).append("\n");
        ctx.append("- Actions principales : ").append(topValues(logs, this::extractAction, 8)).append("\n");
        ctx.append("- Filtres principaux : ").append(topValues(logs, this::extractFilter, 12)).append("\n");
        ctx.append("- Objets métier principaux : ").append(topValues(logs, this::extractBusinessObject, 10)).append("\n\n");

        ctx.append("RECHERCHES SANS RÉSULTAT PAR FILTRE\n");
        ctx.append("-----------------------------------\n");
        ctx.append(buildZeroRowByFilter(logs)).append("\n\n");

        ctx.append("CHRONOLOGIE MÉTIER\n");
        ctx.append("------------------\n");
        ctx.append(buildAdaptiveBusinessStory(logs)).append("\n\n");

        ctx.append("POINTS DE DIAGNOSTIC\n");
        ctx.append("--------------------\n");
        ctx.append(buildDiagnosticSignals(logs)).append("\n\n");

        ctx.append("ERREURS CONSTATÉES (horodatages ISO)\n");
        ctx.append("------------------------------------\n");
        ctx.append(buildExactErrorsSection(logs)).append("\n\n");

        ctx.append("LENTEURS DÉTECTÉES (≥ 1000 ms)\n");
        ctx.append("------------------------------\n");
        ctx.append(buildSlowOperationsSection(logs, 40)).append("\n\n");

        ctx.append("PREUVES REPRÉSENTATIVES\n");
        ctx.append("-----------------------\n");
        ctx.append(buildEvidence(logs, logs, normalizeEvidenceLimit(request.getMaxEvidenceLogs())));

        return ctx.toString();
    }

    private String buildZeroRowByFilter(List<LogEntry> logs) {
        Map<String, Long> byFilter = new HashMap<>();
        for (LogEntry log : logs) {
            String msg = safe(log.getMessage()).toLowerCase(Locale.ROOT);
            if (!msg.contains("0 row") && !msg.contains("[0] row fetched")) continue;
            String filter = extractFilter(log);
            String key = filter != null && !filter.isBlank() ? filter : "Recherche sans filtre identifié";
            byFilter.merge(key, 1L, Long::sum);
        }
        if (byFilter.isEmpty()) {
            return "Aucune recherche à 0 résultat.\n";
        }
        StringBuilder sb = new StringBuilder();
        byFilter.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
                .forEach(e -> sb.append("- ").append(e.getKey()).append(" — ").append(e.getValue()).append(" fois\n"));
        return sb.toString();
    }

    private String buildAdaptiveBusinessStory(List<LogEntry> logs) {
        if (logs.size() > 5000) {
            List<LogEntry> sample = sampleLogsEvenly(logs, 800);
            String aggregated = buildAggregatedTimeline(sample);
            return aggregated + "\n- Timeline agrégée sur " + logs.size() + " logs (échantillon représentatif).\n";
        }
        if (logs.size() > 800) {
            return buildAggregatedTimeline(logs) + "\n- Timeline agrégée (" + logs.size() + " logs).\n";
        }
        return buildBusinessStory(logs);
    }

    private List<LogEntry> loadLogs(AiContextRequestDto request) {
        Specification<LogEntry> spec = Specification
                .where(LogEntrySpecifications.hasImportIds(request.getImportIds()))
                .and(LogEntrySpecifications.hasTimestampBetween(request.getDateFrom(), request.getDateTo()))
                .and(groupSpec(request.getGroupBy(), request.getGroupKey()));

        return logEntryRepository.findAll(
                spec,
                Sort.by(Sort.Direction.ASC, "logTimestamp")
                        .and(Sort.by(Sort.Direction.ASC, "id"))
        );
    }

    private Specification<LogEntry> groupSpec(String groupBy, String groupKey) {
        if (groupBy == null || groupKey == null || groupKey.isBlank() || "NON_RENSEIGNE".equalsIgnoreCase(groupKey)) {
            return null;
        }

        return switch (groupBy) {
            case "userName" -> LogEntrySpecifications.hasUserName(groupKey);
            case "processName" -> LogEntrySpecifications.hasProcessName(groupKey);
            case "sessionId" -> LogEntrySpecifications.hasSessionId(groupKey);
            case "eventType" -> LogEntrySpecifications.hasEventType(groupKey);
            case "sourceFileName", "fileName" -> LogEntrySpecifications.hasFileName(groupKey);
            case "uuid", "businessKey", "correlationId" -> LogEntrySpecifications.hasUuid(groupKey);
            default -> null;
        };
    }

    private String buildBusinessStory(List<LogEntry> logs) {
        List<String> steps = new ArrayList<>();
        String lastStep = "";

        for (LogEntry log : logs) {
            String step = toBusinessStep(log);

            if (step == null || step.isBlank()) continue;

            if (!step.equals(lastStep)) {
                steps.add("- " + formatTime(log) + " : " + step);
                lastStep = step;
            }

            if (steps.size() >= 35) break;
        }

        if (steps.isEmpty()) {
            return "Aucune étape métier claire n’a été reconstruite. Les logs sont surtout techniques.";
        }

        return String.join("\n", steps);
    }

    private String toBusinessStep(LogEntry log) {
        String msg = safe(log.getMessage());
        String lower = msg.toLowerCase(Locale.ROOT);

        String process = extractProcess(log);
        String action = extractAction(log);
        String filter = extractFilter(log);
        String object = extractBusinessObject(log);

        if (lower.contains("start transit")) {
            return "l’utilisateur déclenche une transition/action dans le workflow"
                    + part("process", process)
                    + part("action", action);
        }

        if (lower.contains("transit task")) {
            return "la transition utilisateur se termine"
                    + part("process", process)
                    + part("action", action)
                    + durationPart(log);
        }

        if (lower.contains("start fire rules")) {
            return "le moteur de règles démarre l’évaluation"
                    + part("process", process)
                    + part("action", action);
        }

        if (lower.contains("no rule found") || lower.contains("aucune r")) {
            return "aucune règle métier n’est trouvée pour cette transition"
                    + part("process", process)
                    + part("action", action);
        }

        if (lower.contains("fetching filter")) {
            String loadedFilter = extractBetween(msg, "filter [", "]");
            return "chargement du filtre métier" + part("filtre", loadedFilter);
        }

        if (lower.contains("searchcomposantbyroot start execute")) {
            return "début d’une recherche métier"
                    + part("filtre", filter);
        }

        if (lower.contains("preparesearchbyroot")) {
            return "préparation de la recherche"
                    + part("filtre", filter)
                    + part("objet", object)
                    + resultPart(msg)
                    + durationPart(log);
        }

        if (lower.contains("query:")) {
            return "exécution d’une requête SQL pour récupérer les objets métier"
                    + part("filtre", filter)
                    + part("objet", object);
        }

        if (lower.contains("searchcomposantbyroot end execute")) {
            return "fin de la recherche métier"
                    + part("filtre", filter)
                    + fetchedPart(msg);
        }

        if (lower.contains("0 row")) {
            return "la recherche ne retourne aucun résultat"
                    + part("filtre", filter)
                    + part("objet", object);
        }

        if (lower.contains("insertion reussie") || lower.contains("insertion réussie")) {
            return "une insertion métier est réussie.";
        }

        if (lower.contains("saveprocesscontent")
                || lower.contains("saveinstanceoperation")
                || lower.contains("total time save")
                || lower.contains("insertarray")
                || lower.contains("removeobjectstodelete")) {
            return "opération de sauvegarde ou de mise à jour des données"
                    + part("objet", object)
                    + durationPart(log);
        }

        if (lower.contains("memory usage")) {
            return "mesure de consommation mémoire observée" + memoryPart(log);
        }

        if (lower.contains("took")) {
            return "durée d’exécution observée" + durationPart(log);
        }

        return null;
    }

    private String buildDiagnosticSignals(List<LogEntry> logs) {
        StringBuilder sb = new StringBuilder();

        long errors = countErrors(logs);
        long warnings = countWarnings(logs);
        long zeroRows = countZeroRows(logs);

        if (errors > 0) {
            sb.append("- CRITIQUE : des erreurs ERROR sont présentes. Commencer par la première erreur chronologique.\n");
        } else {
            sb.append("- Aucune erreur ERROR critique visible dans ce périmètre.\n");
        }

        if (zeroRows > 0) {
            sb.append("- À vérifier : ").append(zeroRows)
                    .append(" recherche(s) retournent 0 résultat. Cela peut être normal si aucun objet n’était attendu, mais suspect si l’utilisateur attendait un résultat.\n");
        }

        if (warnings > 0) {
            sb.append("- À vérifier : ").append(warnings)
                    .append(" warning(s) ou transitions sans règle configurée. Vérifier si les règles métier étaient obligatoires pour ces transitions.\n");
        }

        List<String> highMemory = logs.stream()
                .filter(l -> {
                    Integer memory = logPatternExtractor.extractGraph(l).getMemoryMo();
                    return memory != null && memory >= 8000;
                })
                .map(LogEntry::getMessage)
                .filter(Objects::nonNull)
                .limit(5)
                .toList();

        if (!highMemory.isEmpty()) {
            sb.append("- Performance : mémoire élevée détectée dans plusieurs étapes.\n");
        }

        List<String> slow = logs.stream()
                .filter(l -> {
                    Long duration = logPatternExtractor.extractGraph(l).getDurationMs();
                    return duration != null && duration >= 1000;
                })
                .map(LogEntry::getMessage)
                .filter(Objects::nonNull)
                .limit(5)
                .toList();

        if (!slow.isEmpty()) {
            sb.append("- Performance : certaines étapes dépassent 1000 ms.\n");
        }

        return sb.toString();
    }

    private String buildEvidence(List<LogEntry> logs, List<LogEntry> focusedLogs, int max) {
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<LogEntry> important = new ArrayList<>();

        for (LogEntry log : focusedLogs) {
            if (isEvidenceImportant(log) && seen.add(log.getId())) {
                important.add(log);
            }
            if (important.size() >= max) break;
        }

        if (important.size() < max) {
            for (LogEntry log : focusedLogs) {
                if (seen.add(log.getId())) {
                    important.add(log);
                }
                if (important.size() >= max) break;
            }
        }

        if (important.size() < max) {
            for (LogEntry log : logs) {
                if (isEvidenceImportant(log) && seen.add(log.getId())) {
                    important.add(log);
                }
                if (important.size() >= max) break;
            }
        }

        if (important.isEmpty()) {
            important = logs.stream().limit(max).toList();
        }

        StringBuilder sb = new StringBuilder();

        for (LogEntry log : important) {
            sb.append(formatEvidenceLine(log));
        }

        return sb.toString();
    }

    private String formatEvidenceLine(LogEntry log) {
        GraphExtraction ex = logPatternExtractor.extractGraph(log);
        String process = firstNonBlank(ex.getProcess(), log.getProcessName());
        return "- ["
                + formatTime(log)
                + "] niveau=" + nullSafe(log.getLevel(), "N/A")
                + ", processus=" + nullSafe(process, "N/A")
                + durationPart(log)
                + memoryPart(log)
                + ", message=" + shortMessage(log.getMessage())
                + "\n";
    }

    private boolean hasFocusScope(AiContextRequestDto request, List<String> focusTerms, List<LogEntry> focusedLogs) {
        if (focusedLogs != null && !focusedLogs.isEmpty()) return true;
        if (focusTerms != null && !focusTerms.isEmpty()) return true;
        if (request.getFocusQuery() != null && !request.getFocusQuery().isBlank()) return true;
        return notBlank(request.getScopeProcess()) || notBlank(request.getScopeAction()) || notBlank(request.getScopeFilter())
                || (notBlank(request.getScopeNodeType()) && notBlank(request.getScopeNodeLabel()));
    }

    private String buildFocusSection(AiContextRequestDto request, List<String> focusTerms, List<LogEntry> allLogs, List<LogEntry> focusedLogs) {
        String question = request.getFocusQuery() != null ? request.getFocusQuery().trim() : "";
        StringBuilder sb = new StringBuilder();
        sb.append("- Question : ").append(question.isBlank() ? "(navigation graphe)" : question).append("\n");

        if (notBlank(request.getScopeProcess())) {
            sb.append("- Processus sélectionné dans le graphe : ").append(request.getScopeProcess()).append("\n");
        }
        if (notBlank(request.getScopeAction())) {
            sb.append("- Action sélectionnée dans le graphe : ").append(request.getScopeAction()).append("\n");
        }
        if (notBlank(request.getScopeFilter())) {
            sb.append("- Filtre sélectionné dans le graphe : ").append(request.getScopeFilter()).append("\n");
        }
        if (notBlank(request.getScopeNodeType()) && notBlank(request.getScopeNodeLabel())) {
            sb.append("- Élément sélectionné dans le graphe : ")
                    .append(request.getScopeNodeType()).append(" = ").append(request.getScopeNodeLabel()).append("\n");
        }

        if (!focusTerms.isEmpty()) {
            sb.append("- Termes ciblés : ").append(String.join(", ", focusTerms)).append("\n");
        }

        sb.append("- Logs correspondants : ").append(focusedLogs.size()).append(" / ").append(allLogs.size()).append("\n");

        if (focusedLogs.isEmpty()) {
            sb.append("- AUCUN log ne correspond à ce périmètre dans les filtres actuels.\n");
            sb.append("- Processus connus : ").append(topValues(allLogs, this::extractProcess, 12)).append("\n");
            return sb.toString();
        }

        sb.append("- Période focus : ").append(firstTimestamp(focusedLogs)).append(" → ").append(lastTimestamp(focusedLogs)).append("\n");
        sb.append("- Processus : ").append(topValues(focusedLogs, this::extractProcess, 6)).append("\n");
        sb.append("- Actions : ").append(topValues(focusedLogs, this::extractAction, 12)).append("\n");
        sb.append("- Filtres : ").append(topValues(focusedLogs, this::extractFilter, 12)).append("\n");
        sb.append("- Erreurs : ").append(countErrors(focusedLogs)).append("\n");
        sb.append("- 0 row : ").append(countZeroRows(focusedLogs)).append("\n");
        sb.append("- Warnings : ").append(countWarnings(focusedLogs)).append("\n\n");

        sb.append("TIMELINE (début → fin) :\n");
        if (focusedLogs.size() > 800) {
            sb.append(buildAggregatedTimeline(sampleLogsEvenly(focusedLogs, 600))).append("\n");
            sb.append("- Timeline agrégée sur échantillon représentatif (")
                    .append(focusedLogs.size()).append(" logs au total).\n");
            sb.append("- Les erreurs exactes sont dans la section « ERREURS CONSTATÉES ».\n\n");
        } else {
            sb.append(buildAggregatedTimeline(focusedLogs)).append("\n\n");
        }

        sb.append("ÉVÉNEMENTS MARQUANTS (erreurs, warnings, 0 row, lenteurs) :\n");
        sb.append(buildMarkedEvents(focusedLogs)).append("\n\n");

        sb.append("ÉCHANTILLON BRUT (début, fin, et points clés) :\n");
        for (LogEntry log : pickFocusEvidenceSample(focusedLogs, Math.min(60, focusedLogs.size()))) {
            sb.append(formatEvidenceLine(log));
        }

        return sb.toString();
    }

    private List<LogEntry> resolveFocusedLogs(List<LogEntry> logs, AiContextRequestDto request, List<String> focusTerms) {
        List<LogEntry> scoped = logs;

        if (notBlank(request.getScopeNodeType()) && notBlank(request.getScopeNodeLabel())) {
            scoped = filterByFocusTarget(scoped, new FocusTarget(request.getScopeNodeType(), request.getScopeNodeLabel()));
        }
        if (notBlank(request.getScopeProcess())) {
            scoped = filterByProcess(scoped, request.getScopeProcess());
        }
        if (notBlank(request.getScopeAction())) {
            scoped = filterByField(scoped, request.getScopeAction(), this::extractAction);
        }
        if (notBlank(request.getScopeFilter())) {
            scoped = filterByField(scoped, request.getScopeFilter(), this::extractFilter);
        }

        if (scoped.size() == logs.size()) {
            FocusTarget queryFocus = extractQueryFocus(request.getFocusQuery(), logs);
            if (queryFocus != null) {
                List<LogEntry> byQuery = filterByFocusTarget(logs, queryFocus);
                if (!byQuery.isEmpty()) {
                    scoped = byQuery;
                }
            }
        }

        if (!focusTerms.isEmpty() && scoped.size() == logs.size()) {
            List<LogEntry> byTerms = filterByFocusAllTerms(logs, focusTerms, request);
            if (!byTerms.isEmpty()) {
                scoped = byTerms;
            }
        } else if (!focusTerms.isEmpty() && scoped.size() < logs.size()) {
            scoped = filterByFocusAllTerms(scoped, focusTerms, request);
        }

        return scoped;
    }

    private record FocusTarget(String type, String label) {}

    private List<LogEntry> filterByFocusTarget(List<LogEntry> logs, FocusTarget target) {
        if (target == null || target.label() == null || target.label().isBlank()) {
            return logs;
        }

        String type = target.type() == null ? "" : target.type().trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "PROCESS" -> filterByProcess(logs, target.label());
            case "ACTION" -> filterByField(logs, target.label(), this::extractAction);
            case "FILTER" -> filterByField(logs, target.label(), this::extractFilter);
            case "TASK" -> filterByField(logs, target.label(), this::extractTask);
            case "OBJECT" -> filterByField(logs, target.label(), this::extractBusinessObject);
            case "FAMILY" -> filterByMessageContains(logs, target.label());
            case "WARNING", "ERROR", "ZERO_RESULT", "SAVE" -> filterByMessageContains(logs, target.label());
            case "PERFORMANCE_ANOMALY" -> filterByPerformanceAnomaly(logs, target.label());
            case "MEMORY_ANOMALY" -> filterByMemoryAnomaly(logs, target.label());
            default -> filterByMessageContains(logs, target.label());
        };
    }

    private List<LogEntry> filterByMessageContains(List<LogEntry> logs, String needle) {
        String n = needle.trim().toLowerCase(Locale.ROOT);
        return logs.stream()
                .filter(log -> safe(log.getMessage()).toLowerCase(Locale.ROOT).contains(n)
                        || safe(log.getRawLog()).toLowerCase(Locale.ROOT).contains(n))
                .toList();
    }

    private List<LogEntry> filterByPerformanceAnomaly(List<LogEntry> logs, String label) {
        String nature = extractAnomalyNature(label, "Durée anormale");
        return logs.stream()
                .filter(log -> {
                    Long duration = logPatternExtractor.extractGraph(log).getDurationMs();
                    if (duration == null || duration <= 0) return false;
                    if (nature != null && !matchesAnomalyNature(log, nature)) return false;
                    return duration >= 1000;
                })
                .toList();
    }

    private List<LogEntry> filterByMemoryAnomaly(List<LogEntry> logs, String label) {
        String nature = extractAnomalyNature(label, "Mémoire anormale");
        return logs.stream()
                .filter(log -> {
                    Integer memory = logPatternExtractor.extractGraph(log).getMemoryMo();
                    if (memory == null || memory <= 0) return false;
                    if (nature != null && !matchesAnomalyNature(log, nature)) return false;
                    return memory >= 3000;
                })
                .toList();
    }

    private String extractAnomalyNature(String label, String prefix) {
        if (label == null) return null;
        String cleaned = label;
        if (cleaned.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
            cleaned = cleaned.substring(prefix.length()).trim();
            if (cleaned.startsWith(":")) cleaned = cleaned.substring(1).trim();
        }
        int paren = cleaned.lastIndexOf('(');
        if (paren > 0) cleaned = cleaned.substring(0, paren).trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private boolean matchesAnomalyNature(LogEntry log, String nature) {
        String n = nature.toLowerCase(Locale.ROOT);
        GraphExtraction ex = logPatternExtractor.extractGraph(log);
        return safe(ex.getFilter()).toLowerCase(Locale.ROOT).contains(n)
                || safe(ex.getAction()).toLowerCase(Locale.ROOT).contains(n)
                || safe(ex.getProcess()).toLowerCase(Locale.ROOT).contains(n)
                || safe(log.getMessage()).toLowerCase(Locale.ROOT).contains(n);
    }

    private FocusTarget extractQueryFocus(String query, List<LogEntry> logs) {
        if (query == null || query.isBlank()) return null;

        List<FocusPattern> patterns = List.of(
                new FocusPattern("PROCESS", "(?:processus|process)\\s+(.+)"),
                new FocusPattern("ACTION", "(?:action|actions)\\s+(.+)"),
                new FocusPattern("FILTER", "(?:filtre|filtres|filter)\\s+(.+)"),
                new FocusPattern("TASK", "(?:tache|tâche|task)\\s+(.+)"),
                new FocusPattern("OBJECT", "(?:objet|objets|object)\\s+(.+)")
        );

        for (FocusPattern pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern.regex(), Pattern.CASE_INSENSITIVE).matcher(query);
            if (matcher.find()) {
                String label = cleanFocusLabel(matcher.group(1));
                if (notBlank(label)) {
                    return new FocusTarget(pattern.type(), label);
                }
            }
        }

        return matchLongestKnownLabel(query, logs);
    }

    private record FocusPattern(String type, String regex) {}

    private FocusTarget matchLongestKnownLabel(String query, List<LogEntry> logs) {
        String qLower = query.toLowerCase(Locale.ROOT);

        List<FocusTarget> candidates = new ArrayList<>();
        for (String process : collectProcesses(logs)) {
            if (process.length() >= 3 && qLower.contains(process.toLowerCase(Locale.ROOT))) {
                candidates.add(new FocusTarget("PROCESS", process));
            }
        }
        for (String action : collectActions(logs)) {
            if (action.length() >= 3 && qLower.contains(action.toLowerCase(Locale.ROOT))) {
                candidates.add(new FocusTarget("ACTION", action));
            }
        }
        for (String filter : collectFilters(logs)) {
            if (filter.length() >= 3 && qLower.contains(filter.toLowerCase(Locale.ROOT))) {
                candidates.add(new FocusTarget("FILTER", filter));
            }
        }
        for (String object : collectObjects(logs)) {
            if (object.length() >= 3 && qLower.contains(object.toLowerCase(Locale.ROOT))) {
                candidates.add(new FocusTarget("OBJECT", object));
            }
        }

        return candidates.stream()
                .max(Comparator.comparingInt(t -> t.label().length()))
                .orElse(null);
    }

    private String cleanFocusLabel(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim()
                .replaceAll("(?i)\\s+(du début|jusqu'à la fin|jusqu'a la fin|dès le début|depuis le début).*$", "")
                .replaceAll("(?i)\\s+(pour cet utilisateur|de cet utilisateur).*$", "")
                .replaceAll("[?.!,;]+$", "")
                .trim();
        return cleaned;
    }

    private Set<String> collectProcesses(List<LogEntry> logs) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (LogEntry log : logs) {
            addIfPresent(names, log.getProcessName());
            addIfPresent(names, extractProcess(log));
        }
        return names;
    }

    private Set<String> collectActions(List<LogEntry> logs) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (LogEntry log : logs) addIfPresent(names, extractAction(log));
        return names;
    }

    private Set<String> collectFilters(List<LogEntry> logs) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (LogEntry log : logs) addIfPresent(names, extractFilter(log));
        return names;
    }

    private Set<String> collectObjects(List<LogEntry> logs) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (LogEntry log : logs) addIfPresent(names, extractBusinessObject(log));
        return names;
    }

    private List<LogEntry> filterByProcess(List<LogEntry> logs, String processName) {
        String needle = processName.trim().toLowerCase(Locale.ROOT);
        return logs.stream()
                .filter(log -> matchesProcess(log, needle))
                .toList();
    }

    private boolean matchesProcess(LogEntry log, String needle) {
        if (safe(log.getProcessName()).toLowerCase(Locale.ROOT).contains(needle)) return true;
        return safe(extractProcess(log)).toLowerCase(Locale.ROOT).contains(needle);
    }

    private List<LogEntry> filterByField(List<LogEntry> logs, String value, Extractor extractor) {
        String needle = value.trim().toLowerCase(Locale.ROOT);
        return logs.stream()
                .filter(log -> safe(extractor.extract(log)).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    private String buildAggregatedTimeline(List<LogEntry> logs) {
        if (logs.isEmpty()) {
            return "Aucune étape.";
        }

        List<String> lines = new ArrayList<>();
        String prevKey = null;
        int run = 0;
        String startTime = null;
        String endTime = null;
        String stepLabel = null;

        for (LogEntry log : logs) {
            if (isEvidenceImportant(log)) {
                flushTimelineRun(lines, startTime, endTime, stepLabel, run);
                prevKey = null;
                run = 0;
                lines.add("- [" + formatTime(log) + "] ⚠ " + formatTimelineStep(log));
                continue;
            }

            String key = timelineKey(log);
            String step = formatTimelineStep(log);
            String time = formatTime(log);

            if (key.equals(prevKey)) {
                run++;
                endTime = time;
            } else {
                flushTimelineRun(lines, startTime, endTime, stepLabel, run);
                prevKey = key;
                run = 1;
                startTime = endTime = time;
                stepLabel = step;
            }
        }
        flushTimelineRun(lines, startTime, endTime, stepLabel, run);

        return lines.isEmpty()
                ? "Aucune étape reconstruite."
                : String.join("\n", lines);
    }

    private void flushTimelineRun(List<String> lines, String startTime, String endTime, String stepLabel, int run) {
        if (run <= 0 || stepLabel == null) return;
        if (run == 1) {
            lines.add("- [" + startTime + "] " + stepLabel);
        } else {
            lines.add("- [" + startTime + " → " + endTime + "] " + stepLabel + " (×" + run + ")");
        }
    }

    private String timelineKey(LogEntry log) {
        GraphExtraction ex = logPatternExtractor.extractGraph(log);
        return nullSafe(ex.getAction(), "-") + "|" + nullSafe(ex.getFilter(), "-");
    }

    private String formatTimelineStep(LogEntry log) {
        GraphExtraction ex = logPatternExtractor.extractGraph(log);
        String process = firstNonBlank(ex.getProcess(), log.getProcessName());
        StringBuilder sb = new StringBuilder();
        sb.append("process=").append(nullSafe(process, "N/A"));
        sb.append(", action=").append(nullSafe(ex.getAction(), "N/A"));
        if (notBlank(ex.getFilter())) sb.append(", filtre=").append(ex.getFilter());
        if (notBlank(ex.getObject())) sb.append(", objet=").append(ex.getObject());
        Long duration = ex.getDurationMs();
        if (duration != null && duration > 0) sb.append(", ").append(duration).append(" ms");
        Integer memory = ex.getMemoryMo();
        if (memory != null && memory > 0) sb.append(", ").append(memory).append(" Mo");
        return sb.toString();
    }

    private String buildExactErrorsSection(List<LogEntry> logs) {
        List<LogEntry> errors = logs.stream()
                .filter(l -> Boolean.TRUE.equals(l.getIsError())
                        || "ERROR".equalsIgnoreCase(safe(l.getLevel())))
                .toList();

        if (errors.isEmpty()) {
            return "Aucune erreur ERROR enregistrée dans ce périmètre.\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Nombre d'erreurs : ").append(errors.size()).append("\n");
        for (LogEntry log : errors) {
            sb.append(formatEvidenceLine(log));
        }
        return sb.toString();
    }

    private String buildSlowOperationsSection(List<LogEntry> logs, int max) {
        List<LogEntry> slow = logs.stream()
                .filter(l -> {
                    Long duration = logPatternExtractor.extractGraph(l).getDurationMs();
                    return duration != null && duration >= 1000;
                })
                .sorted((a, b) -> Long.compare(
                        Optional.ofNullable(logPatternExtractor.extractGraph(b).getDurationMs()).orElse(0L),
                        Optional.ofNullable(logPatternExtractor.extractGraph(a).getDurationMs()).orElse(0L)
                ))
                .limit(max)
                .toList();

        if (slow.isEmpty()) {
            return "Aucune lenteur ≥ 1000 ms détectée dans ce périmètre.\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Nombre de lenteurs ≥ 1000 ms : ")
                .append(logs.stream().filter(l -> {
                    Long d = logPatternExtractor.extractGraph(l).getDurationMs();
                    return d != null && d >= 1000;
                }).count())
                .append(" (top ").append(slow.size()).append(" ci-dessous)\n");
        for (LogEntry log : slow) {
            sb.append(formatEvidenceLine(log));
        }
        return sb.toString();
    }

    private List<LogEntry> sampleLogsEvenly(List<LogEntry> logs, int max) {
        if (logs.size() <= max) return logs;
        List<LogEntry> sample = new ArrayList<>();
        int stride = Math.max(1, logs.size() / max);
        for (int i = 0; i < logs.size() && sample.size() < max; i += stride) {
            sample.add(logs.get(i));
        }
        LogEntry last = logs.get(logs.size() - 1);
        if (!sample.contains(last)) {
            sample.add(last);
        }
        return sample;
    }

    private boolean isGeneralOverviewQuestion(AiContextRequestDto request, List<String> focusTerms) {
        if (request == null || request.getFocusQuery() == null || request.getFocusQuery().isBlank()) {
            return false;
        }
        if (notBlank(request.getScopeNodeType()) || notBlank(request.getScopeProcess())
                || notBlank(request.getScopeAction()) || notBlank(request.getScopeFilter())) {
            return false;
        }
        if (focusTerms != null && !focusTerms.isEmpty()) {
            return false;
        }
        String q = request.getFocusQuery().toLowerCase(Locale.ROOT);
        return q.contains("log") || q.contains("passe") || q.contains("fait") || q.contains("résume")
                || q.contains("resume") || q.contains("parcours") || q.contains("comprendre")
                || q.contains("expliqu") || q.contains("qu'est") || q.contains("que se");
    }

    private String buildMarkedEvents(List<LogEntry> logs) {
        List<String> lines = logs.stream()
                .filter(this::isEvidenceImportant)
                .map(log -> "- [" + formatTime(log) + "] " + formatTimelineStep(log) + " — " + shortMessage(log.getMessage()))
                .toList();

        return lines.isEmpty() ? "Aucun événement marquant détecté." : String.join("\n", lines);
    }

    private List<LogEntry> pickFocusEvidenceSample(List<LogEntry> focusedLogs, int max) {
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<LogEntry> sample = new ArrayList<>();

        for (LogEntry log : focusedLogs) {
            if (isEvidenceImportant(log) && seen.add(log.getId())) {
                sample.add(log);
            }
            if (sample.size() >= max) return sample;
        }

        int stride = Math.max(1, focusedLogs.size() / Math.max(1, max - sample.size()));
        for (int i = 0; i < focusedLogs.size() && sample.size() < max; i += stride) {
            LogEntry log = focusedLogs.get(i);
            if (seen.add(log.getId())) {
                sample.add(log);
            }
        }

        for (LogEntry log : focusedLogs) {
            if (seen.add(log.getId())) {
                sample.add(log);
            }
            if (sample.size() >= max) break;
        }

        return sample;
    }

    private List<String> resolveFocusTerms(String query, List<LogEntry> logs, AiContextRequestDto request) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String qLower = query.toLowerCase(Locale.ROOT);
        String groupKey = request != null ? safe(request.getGroupKey()).toLowerCase(Locale.ROOT) : "";
        Set<String> known = collectFocusCandidates(logs);
        List<String> matched = new ArrayList<>();

        Matcher explicit = Pattern.compile(
                "(?:processus|process|filtre|action|tache|tâche)\\s+([A-Za-z0-9_.]+)",
                Pattern.CASE_INSENSITIVE
        ).matcher(query);
        while (explicit.find()) {
            addFocusTerm(matched, explicit.group(1), known);
        }

        for (String name : known) {
            if (name.length() >= 4 && qLower.contains(name.toLowerCase(Locale.ROOT))) {
                if (!isGroupKeyTerm(name, groupKey)) {
                    matched.add(name);
                }
            }
        }

        if (matched.isEmpty()) {
            Matcher token = Pattern.compile("\\b[A-Za-z][A-Za-z0-9_.]{3,}\\b").matcher(query);
            while (token.find()) {
                String candidate = token.group();
                if (!isFocusStopWord(candidate) && !isGroupKeyTerm(candidate, groupKey)) {
                    addFocusTerm(matched, candidate, known);
                }
            }
        }

        return matched.stream().distinct().limit(5).toList();
    }

    private boolean isGroupKeyTerm(String term, String groupKey) {
        if (!notBlank(groupKey) || !notBlank(term)) return false;
        String t = term.toLowerCase(Locale.ROOT);
        return groupKey.equals(t) || groupKey.contains(t) || t.contains(groupKey);
    }

    private void addFocusTerm(List<String> matched, String candidate, Set<String> known) {
        if (candidate == null || candidate.isBlank()) return;

        for (String name : known) {
            if (name.equalsIgnoreCase(candidate)) {
                matched.add(name);
                return;
            }
        }

        for (String name : known) {
            if (name.toLowerCase(Locale.ROOT).contains(candidate.toLowerCase(Locale.ROOT))
                    || candidate.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
                matched.add(name);
                return;
            }
        }

        matched.add(candidate);
    }

    private Set<String> collectFocusCandidates(List<LogEntry> logs) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (LogEntry log : logs) {
            addIfPresent(names, log.getProcessName());
            GraphExtraction ex = logPatternExtractor.extractGraph(log);
            addIfPresent(names, ex.getProcess());
            addIfPresent(names, ex.getAction());
            addIfPresent(names, ex.getFilter());
            addIfPresent(names, ex.getTask());
            addIfPresent(names, ex.getObject());
        }
        return names;
    }

    private void addIfPresent(Set<String> names, String value) {
        if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
            names.add(value.trim());
        }
    }

    private boolean isFocusStopWord(String token) {
        return Set.of(
                "dans", "pour", "avec", "sans", "cette", "celui", "celle", "quoi",
                "comment", "pourquoi", "processus", "utilisateur", "session",
                "erreur", "erreurs", "lenteur", "lenteurs", "regle", "règle", "filtre",
                "dis", "dit", "faire", "fait", "depuis", "jusqu", "debut", "début", "fin"
        ).contains(token.toLowerCase(Locale.ROOT));
    }

    private List<LogEntry> filterByFocusAllTerms(List<LogEntry> logs, List<String> focusTerms, AiContextRequestDto request) {
        if (focusTerms == null || focusTerms.isEmpty()) {
            return List.of();
        }

        FocusTarget queryFocus = extractQueryFocus(request != null ? request.getFocusQuery() : null, logs);
        if (queryFocus != null) {
            List<LogEntry> byFocus = filterByFocusTarget(logs, queryFocus);
            if (!byFocus.isEmpty()) {
                return byFocus;
            }
        }

        return logs.stream()
                .filter(log -> focusTerms.stream().allMatch(term -> matchesFocus(log, term)))
                .toList();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private boolean matchesFocus(LogEntry log, String term) {
        if (term == null || term.isBlank()) return false;
        String t = term.toLowerCase(Locale.ROOT);

        if (safe(log.getProcessName()).toLowerCase(Locale.ROOT).contains(t)) return true;

        String msg = safe(log.getMessage()).toLowerCase(Locale.ROOT);
        if (msg.contains(t)) return true;

        GraphExtraction ex = logPatternExtractor.extractGraph(log);
        return safe(ex.getProcess()).toLowerCase(Locale.ROOT).contains(t)
                || safe(ex.getAction()).toLowerCase(Locale.ROOT).contains(t)
                || safe(ex.getFilter()).toLowerCase(Locale.ROOT).contains(t)
                || safe(ex.getTask()).toLowerCase(Locale.ROOT).contains(t)
                || safe(ex.getObject()).toLowerCase(Locale.ROOT).contains(t);
    }

    private boolean isEvidenceImportant(LogEntry log) {
        String msg = safe(log.getMessage()).toLowerCase(Locale.ROOT);

        return Boolean.TRUE.equals(log.getIsError())
                || "ERROR".equalsIgnoreCase(safe(log.getLevel()))
                || msg.contains("no rule found")
                || msg.contains("aucune r")
                || msg.contains("0 row")
                || msg.contains("searchcomposantbyroot start execute")
                || msg.contains("searchcomposantbyroot end execute")
                || msg.contains("start fire rules")
                || msg.contains("transit task")
                || msg.contains("memory usage");
    }

    private long countErrors(List<LogEntry> logs) {
        return logs.stream()
                .filter(l -> Boolean.TRUE.equals(l.getIsError()) || "ERROR".equalsIgnoreCase(safe(l.getLevel())))
                .count();
    }

    private long countWarnings(List<LogEntry> logs) {
        return logs.stream()
                .filter(l -> {
                    String msg = safe(l.getMessage()).toLowerCase(Locale.ROOT);
                    return "WARN".equalsIgnoreCase(safe(l.getLevel()))
                            || msg.contains("no rule found")
                            || msg.contains("aucune r");
                })
                .count();
    }

    private long countZeroRows(List<LogEntry> logs) {
        return logs.stream()
                .filter(l -> {
                    String msg = safe(l.getMessage()).toLowerCase(Locale.ROOT);
                    return msg.contains("0 row") || msg.contains("[0] row fetched");
                })
                .count();
    }

    private long countPerformanceSignals(List<LogEntry> logs) {
        return logs.stream()
                .filter(l -> {
                    GraphExtraction ex = logPatternExtractor.extractGraph(l);
                    Long duration = ex.getDurationMs();
                    Integer memory = ex.getMemoryMo();
                    return (duration != null && duration >= 1000) || (memory != null && memory >= 8000);
                })
                .count();
    }

    private String topValues(List<LogEntry> logs, Extractor extractor, int max) {
        Map<String, Long> map = new HashMap<>();

        for (LogEntry log : logs) {
            String value = extractor.extract(log);
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                map.merge(value, 1L, Long::sum);
            }
        }

        if (map.isEmpty()) return "aucun";

        return map.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(max)
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("aucun");
    }

    private String extractProcess(LogEntry log) {
        return logPatternExtractor.extractGraph(log).getProcess();
    }

    private String extractAction(LogEntry log) {
        return logPatternExtractor.extractGraph(log).getAction();
    }

    private String extractTask(LogEntry log) {
        return logPatternExtractor.extractGraph(log).getTask();
    }

    private String extractFilter(LogEntry log) {
        return logPatternExtractor.extractGraph(log).getFilter();
    }

    private String extractBusinessObject(LogEntry log) {
        return logPatternExtractor.extractGraph(log).getObject();
    }

    private String resultPart(String msg) {
        Matcher m = Pattern.compile("(\\d+)\\s*row", Pattern.CASE_INSENSITIVE).matcher(safe(msg));
        if (m.find()) return " avec " + m.group(1) + " ligne(s)";
        return "";
    }

    private String fetchedPart(String msg) {
        Matcher m = Pattern.compile("\\[(\\d+)]\\s*row fetched", Pattern.CASE_INSENSITIVE).matcher(safe(msg));
        if (m.find()) return " avec " + m.group(1) + " ligne(s) récupérée(s)";
        return resultPart(msg);
    }

    private String durationPart(LogEntry log) {
        Long value = logPatternExtractor.extractGraph(log).getDurationMs();
        return value == null ? "" : " en " + value + " ms";
    }

    private String memoryPart(LogEntry log) {
        Integer value = logPatternExtractor.extractGraph(log).getMemoryMo();
        return value == null ? "" : " : " + value + " Mo";
    }

    private String part(String label, String value) {
        return value == null || value.isBlank() ? "" : " — " + label + " : " + value;
    }

    private String firstTimestamp(List<LogEntry> logs) {
        return logs.stream()
                .map(LogEntry::getLogTimestamp)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .map(LocalDateTime::toString)
                .orElse("inconnu");
    }

    private String lastTimestamp(List<LogEntry> logs) {
        return logs.stream()
                .map(LogEntry::getLogTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .map(LocalDateTime::toString)
                .orElse("inconnu");
    }

    private String formatTime(LogEntry log) {
        return log.getLogTimestamp() == null ? "temps inconnu" : log.getLogTimestamp().toString().replace("T", " ");
    }

    private String shortMessage(String msg) {
        if (msg == null) return "";
        String cleaned = msg.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 260 ? cleaned : cleaned.substring(0, 257) + "...";
    }

    private int normalizeEvidenceLimit(Integer value) {
        if (value == null || value <= 0) return 20;
        return Math.min(value, 50);
    }

    private String extractBetween(String text, String start, String end) {
        if (text == null) return null;

        int s = text.indexOf(start);
        if (s < 0) return null;

        int e = text.indexOf(end, s + start.length());
        if (e < 0) return null;

        return text.substring(s + start.length(), e).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String nullSafe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private interface Extractor {
        String extract(LogEntry log);
    }
}