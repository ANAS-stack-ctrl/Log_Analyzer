package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.GroupAnalysisItemDto;
import com.caciopee.loganalyzer.dto.GroupAnalysisRequestDto;
import com.caciopee.loganalyzer.dto.GroupAnalysisResponseDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogEntrySpecifications;
import com.caciopee.loganalyzer.util.LogDisplayTextUtil;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogGroupAnalysisServiceImpl implements LogGroupAnalysisService {

    private final LogEntryRepository logEntryRepository;
    private final LogPatternExtractor logPatternExtractor;

    private static final Pattern ROW_PATTERN =
            Pattern.compile("(\\d+)\\s*row", Pattern.CASE_INSENSITIVE);

    public LogGroupAnalysisServiceImpl(LogEntryRepository logEntryRepository,
                                       LogPatternExtractor logPatternExtractor) {
        this.logEntryRepository = logEntryRepository;
        this.logPatternExtractor = logPatternExtractor;
    }

    @Override
    public GroupAnalysisResponseDto analyzeGroup(GroupAnalysisRequestDto request) {
        List<LogEntry> logs = loadLogs(request);

        GroupAnalysisResponseDto response = new GroupAnalysisResponseDto();
        response.setGroupBy(request.getGroupBy());
        response.setGroupKey(request.getGroupKey());
        response.setTotalLogs((long) logs.size());

        if (logs.isEmpty()) {
            response.setNarrative("Aucun log trouvé pour ce groupe.");
            response.setConclusion("Impossible d’analyser ce groupe car aucun log ne correspond aux filtres.");
            response.setRecommendation("Vérifier le groupBy, le groupKey, l’importId ou la période.");
            return response;
        }

        response.setFirstTimestamp(logs.stream().map(LogEntry::getLogTimestamp).filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null));
        response.setLastTimestamp(logs.stream().map(LogEntry::getLogTimestamp).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null));

        response.setErrorCount(logs.stream().filter(l -> Boolean.TRUE.equals(l.getIsError()) || eq(l.getLevel(), "ERROR")).count());
        response.setWarningCount(logs.stream().filter(l -> eq(l.getLevel(), "WARN") || eq(l.getLevel(), "WARNING") || contains(l.getMessage(), "no rule found") || contains(l.getMessage(), "aucune")).count());
        response.setZeroResultCount(logs.stream().filter(l -> contains(l.getMessage(), "0 row") || contains(l.getMessage(), "[0] row fetched")).count());

        response.setProcesses(itemsFromExtractor(logs, "PROCESS", this::extractProcess));
        response.setActions(itemsFromExtractor(logs, "ACTION", this::extractAction));
        response.setFilters(itemsFromExtractor(logs, "FILTER", this::extractFilter));
        response.setBusinessObjects(itemsFromExtractor(logs, "BUSINESS_OBJECT", this::extractBusinessObject));

        response.setWarnings(buildWarnings(logs));
        response.setZeroResults(buildZeroResults(logs));
        response.setPerformanceSignals(buildPerformanceSignals(logs));

        response.setTimeline(buildTimeline(logs));
        response.setNarrative(buildNarrative(response, logs));
        response.setConclusion(buildConclusion(response));
        response.setRecommendation(buildRecommendation(response));

        return response;
    }

    private List<LogEntry> loadLogs(GroupAnalysisRequestDto request) {
        Specification<LogEntry> spec = Specification
                .where(LogEntrySpecifications.hasImportIds(request.getImportIds()))
                .and(LogEntrySpecifications.hasTimestampBetween(request.getDateFrom(), request.getDateTo()));

        String groupBy = request.getGroupBy();
        String groupKey = request.getGroupKey();

        if (groupBy != null && groupKey != null && !groupKey.isBlank() && !"NON_RENSEIGNE".equalsIgnoreCase(groupKey)) {
            spec = spec.and(groupSpec(groupBy, groupKey));
        }

        return logEntryRepository.findAll(
                spec,
                Sort.by(Sort.Direction.ASC, "logTimestamp").and(Sort.by(Sort.Direction.ASC, "id"))
        );
    }

    private Specification<LogEntry> groupSpec(String groupBy, String groupKey) {
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

    private List<GroupAnalysisItemDto> itemsFromExtractor(List<LogEntry> logs, String type, Extractor extractor) {
        Map<String, ItemAccumulator> map = new LinkedHashMap<>();

        for (LogEntry log : logs) {
            String name = extractor.extract(log);
            if (name == null || name.isBlank() || "null".equalsIgnoreCase(name)) continue;

            map.computeIfAbsent(name, k -> new ItemAccumulator(type, k)).add(log);
        }

        return map.values().stream()
                .map(ItemAccumulator::toDto)
                .sorted(Comparator.comparing(GroupAnalysisItemDto::getCount, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    private List<GroupAnalysisItemDto> buildWarnings(List<LogEntry> logs) {
        Map<String, ItemAccumulator> map = new LinkedHashMap<>();
        for (LogEntry log : logs) {
            if (!isWarningLog(log)) continue;
            String key = warningGroupKey(log);
            map.computeIfAbsent(key, k -> new ItemAccumulator("WARNING", k)).add(log);
        }
        return map.values().stream()
                .map(acc -> {
                    GroupAnalysisItemDto dto = acc.toDto();
                    dto.setDiagnostic("Transition sans règle ou avertissement workflow — à vérifier si une règle métier était attendue.");
                    return dto;
                })
                .sorted(Comparator.comparing(GroupAnalysisItemDto::getCount, Comparator.nullsLast(Long::compareTo)).reversed())
                .limit(20)
                .toList();
    }

    private List<GroupAnalysisItemDto> buildZeroResults(List<LogEntry> logs) {
        Map<String, ItemAccumulator> map = new LinkedHashMap<>();
        for (LogEntry log : logs) {
            if (!contains(log.getMessage(), "0 row") && !contains(log.getMessage(), "[0] row fetched")) continue;
            String filter = extractFilter(log);
            String name = filter != null && !filter.isBlank() ? filter : "Recherche sans filtre identifié";
            map.computeIfAbsent(name, k -> new ItemAccumulator("ZERO_RESULT", k)).add(log);
        }
        return map.values().stream()
                .map(acc -> {
                    GroupAnalysisItemDto dto = acc.toDto();
                    dto.setDiagnostic("Recherche sans résultat — à confirmer avec le métier si un objet était attendu.");
                    return dto;
                })
                .sorted(Comparator.comparing(GroupAnalysisItemDto::getCount, Comparator.nullsLast(Long::compareTo)).reversed())
                .limit(20)
                .toList();
    }

    private List<GroupAnalysisItemDto> buildPerformanceSignals(List<LogEntry> logs) {
        Map<String, PerfAccumulator> slow = new LinkedHashMap<>();
        Map<Integer, Long> memoryCounts = new HashMap<>();

        for (LogEntry log : logs) {
            GraphExtraction ex = logPatternExtractor.extractGraph(log);
            Long duration = ex.getDurationMs();
            Integer memory = ex.getMemoryMo();

            if (duration != null && duration >= 1000) {
                String filter = nullSafe(ex.getFilter(), "sans filtre");
                String process = firstNonBlank(ex.getProcess(), log.getProcessName(), "process inconnu");
                String key = process + "|" + filter;
                slow.computeIfAbsent(key, k -> new PerfAccumulator(process, filter)).observeDuration(duration);
            }
            if (memory != null && memory >= 8000) {
                memoryCounts.merge(memory, 1L, Long::sum);
            }
        }

        List<GroupAnalysisItemDto> result = new ArrayList<>();
        slow.values().stream()
                .sorted(Comparator.comparingLong((PerfAccumulator p) -> p.maxDurationMs).reversed())
                .limit(12)
                .forEach(p -> {
                    GroupAnalysisItemDto dto = new GroupAnalysisItemDto();
                    dto.setType("PERFORMANCE");
                    dto.setName(LogDisplayTextUtil.sanitize(
                            "Lenteur — " + p.process + " / " + p.filter + " (max " + p.maxDurationMs + " ms)"));
                    dto.setCount(p.count);
                    dto.setDiagnostic(p.count > 1
                            ? p.count + " mesures, durée max " + p.maxDurationMs + " ms."
                            : "Durée élevée : " + p.maxDurationMs + " ms.");
                    result.add(dto);
                });

        memoryCounts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(6)
                .forEach(e -> {
                    GroupAnalysisItemDto dto = new GroupAnalysisItemDto();
                    dto.setType("MEMORY");
                    dto.setName("Mémoire ~" + e.getKey() + " Mo");
                    dto.setCount(e.getValue());
                    dto.setDiagnostic(e.getValue() + " mesure(s) autour de " + e.getKey() + " Mo.");
                    result.add(dto);
                });
        return result;
    }

    private boolean isWarningLog(LogEntry log) {
        return contains(log.getMessage(), "no rule found")
                || contains(log.getMessage(), "aucune r")
                || eq(log.getLevel(), "WARN")
                || eq(log.getLevel(), "WARNING");
    }

    private static final Pattern BRACKET_FIELD =
            Pattern.compile("(PROCESS_NAME|ACTION_NAME|TASK_NAME)\\s*\\[([^\\]]*)\\]", Pattern.CASE_INSENSITIVE);

    private String warningGroupKey(LogEntry log) {
        String msg = safe(log.getMessage());
        GraphExtraction ex = logPatternExtractor.extractGraph(log);
        String process = sanitizeLabel(firstNonBlank(
                ex.getProcess(), log.getProcessName(), extractBracketField(msg, "PROCESS_NAME"), "process inconnu"));
        String action = sanitizeLabel(firstNonBlank(
                ex.getAction(), extractBracketField(msg, "ACTION_NAME"), "action ?"));
        String task = sanitizeLabel(firstNonBlank(
                ex.getTask(), extractBracketField(msg, "TASK_NAME"), "tâche ?"));
        if (contains(msg, "no rule found") || contains(msg, "aucune r")) {
            return "Règle absente — " + process + " / " + action + " / " + task;
        }
        return "Avertissement — " + process;
    }

    private String extractBracketField(String msg, String field) {
        if (msg == null || field == null) return null;
        Matcher m = BRACKET_FIELD.matcher(msg);
        while (m.find()) {
            if (!field.equalsIgnoreCase(m.group(1))) continue;
            String value = m.group(2).trim();
            if (value.isBlank() || "null".equalsIgnoreCase(value)) continue;
            return value;
        }
        return null;
    }

    private String sanitizeLabel(String value) {
        return LogDisplayTextUtil.sanitize(nullSafe(value, "—"));
    }

    private String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank() && !"null".equalsIgnoreCase(a)) return a;
        if (b != null && !b.isBlank() && !"null".equalsIgnoreCase(b)) return b;
        return fallback;
    }

    private String firstNonBlank(String a, String b, String c, String fallback) {
        if (a != null && !a.isBlank() && !"null".equalsIgnoreCase(a)) return a;
        if (b != null && !b.isBlank() && !"null".equalsIgnoreCase(b)) return b;
        if (c != null && !c.isBlank() && !"null".equalsIgnoreCase(c)) return c;
        return fallback;
    }

    private static final class PerfAccumulator {
        final String process;
        final String filter;
        long count = 0;
        long maxDurationMs = 0;

        PerfAccumulator(String process, String filter) {
            this.process = process;
            this.filter = filter;
        }

        void observeDuration(long ms) {
            count++;
            maxDurationMs = Math.max(maxDurationMs, ms);
        }
    }

    private List<String> buildTimeline(List<LogEntry> logs) {
        List<String> timeline = new ArrayList<>();

        for (LogEntry log : logs) {
            String msg = safe(log.getMessage());

            if (isImportant(msg)) {
                timeline.add(LogDisplayTextUtil.sanitize(formatTime(log) + " — " + humanize(log)));
            }

            if (timeline.size() >= 80) break;
        }

        return timeline;
    }

    private String buildNarrative(GroupAnalysisResponseDto r, List<LogEntry> logs) {
        String user = "userName".equals(r.getGroupBy()) ? r.getGroupKey() : mostFrequent(logs, LogEntry::getUserName);
        String mainProcess = firstName(r.getProcesses());
        StringBuilder sb = new StringBuilder();

        if ("userName".equals(r.getGroupBy()) && r.getGroupKey() != null) {
            sb.append("Sur la période analysée, l'utilisateur ").append(r.getGroupKey());
        } else {
            sb.append("L'utilisateur ").append(nullSafe(user, "non identifié"));
        }

        if (mainProcess != null) {
            sb.append(" a principalement utilisé le processus ").append(mainProcess).append(".");
        } else {
            sb.append(" a exécuté une suite d’actions applicatives.");
        }

        sb.append("\n\n");

        if (!r.getFilters().isEmpty()) {
            sb.append("Le système charge et utilise plusieurs filtres métier, notamment ");
            sb.append(joinTopNames(r.getFilters(), 5)).append(".");
            sb.append("\n\n");
        }

        if (!r.getActions().isEmpty()) {
            sb.append("Les actions principales détectées sont ");
            sb.append(joinTopNames(r.getActions(), 5)).append(".");
            sb.append("\n\n");
        }

        if (!r.getBusinessObjects().isEmpty()) {
            sb.append("Les objets métier manipulés sont principalement ");
            sb.append(joinTopNames(r.getBusinessObjects(), 5)).append(".");
            sb.append("\n\n");
        }

        if (r.getZeroResultCount() != null && r.getZeroResultCount() > 0) {
            sb.append("De nombreuses recherches (").append(r.getZeroResultCount())
                    .append(") ne retournent aucune ligne : cela peut être normal si aucun objet n’était attendu, mais cela mérite une vérification métier.");
            sb.append("\n\n");
        }

        long perfSignals = r.getPerformanceSignals() != null ? r.getPerformanceSignals().size() : 0;
        if (perfSignals > 0) {
            sb.append("Des signaux de lenteur ou de consommation mémoire ont été observés sur ")
                    .append(perfSignals).append(" étape(s).");
            sb.append("\n\n");
        }

        if (r.getErrorCount() != null && r.getErrorCount() > 0) {
            sb.append("Des erreurs critiques sont présentes dans ce groupe et doivent être inspectées en priorité.");
        } else {
            sb.append("Aucune erreur critique visible dans ce groupe.");
        }

        return sb.toString();
    }

    private String buildConclusion(GroupAnalysisResponseDto r) {
        if (r.getErrorCount() != null && r.getErrorCount() > 0) {
            return "Groupe à analyser en priorité : erreurs critiques détectées.";
        }

        if (r.getZeroResultCount() != null && r.getZeroResultCount() > 0) {
            return "Le workflow semble continuer, mais certaines recherches retournent 0 résultat.";
        }

        if (!r.getPerformanceSignals().isEmpty()) {
            return "Le workflow semble fonctionnel, mais des signaux de mémoire ou performance sont visibles.";
        }

        return "Le groupe semble s’être déroulé sans erreur critique visible.";
    }

    private String buildRecommendation(GroupAnalysisResponseDto r) {
        if (r.getErrorCount() != null && r.getErrorCount() > 0) {
            return "Commencer par la première erreur chronologique, puis vérifier les logs juste avant et juste après.";
        }

        if (r.getZeroResultCount() != null && r.getZeroResultCount() > 0) {
            return "Vérifier les filtres avec 0 résultat et confirmer si les objets métier étaient attendus.";
        }

        if (!r.getPerformanceSignals().isEmpty()) {
            return "Vérifier les étapes lentes et les pics mémoire avant de conclure que le workflow est sain.";
        }

        return "Consulter les sections filtres/actions/process pour valider le déroulement métier.";
    }

    private String humanize(LogEntry log) {
        String msg = safe(log.getMessage());
        GraphExtraction ex = logPatternExtractor.extractGraph(log);

        String process = ex.getProcess();
        String action = ex.getAction();
        String filter = ex.getFilter();
        String object = ex.getObject();

        if (contains(msg, "START fire rules")) {
            return "début d’exécution des règles" + part("process", process) + part("action", action);
        }

        if (contains(msg, "END fire rules")) {
            return "fin d’exécution des règles" + part("process", process) + part("action", action);
        }

        if (contains(msg, "no rule found") || contains(msg, "aucune r")) {
            return "aucune règle configurée détectée" + part("process", process) + part("action", action);
        }

        if (contains(msg, "fetching filter")) {
            return "chargement d’un filtre métier : " + extractBetween(msg, "filter [", "]");
        }

        if (contains(msg, "searchComposantByRoot start execute")) {
            return "début de recherche" + part("filtre", filter);
        }

        if (contains(msg, "searchComposantByRoot end execute")) {
            return "fin de recherche" + part("filtre", filter) + resultPart(msg);
        }

        if (contains(msg, "Query:")) {
            return "requête SQL exécutée" + part("filtre", filter) + part("objet", object);
        }

        if (contains(msg, "Insertion reussie")) {
            return "insertion métier réussie.";
        }

        if (contains(msg, "start transitTask")) {
            return "début de transition utilisateur.";
        }

        if (contains(msg, "Transit task")) {
            return "transition utilisateur terminée.";
        }

        if (contains(msg, "SAVE") || contains(msg, "save")) {
            return "opération de sauvegarde détectée" + part("objet", object);
        }

        if (contains(msg, "memory usage")) {
            Integer mo = ex.getMemoryMo();
            if (mo == null) {
                Long moVal = extractLong(Pattern.compile("(\\d{3,5})\\s*$"), msg);
                if (moVal == null) {
                    moVal = extractLong(Pattern.compile("\\]\\s*(\\d{3,5})\\s*$"), msg);
                }
                mo = moVal != null ? moVal.intValue() : null;
            }
            String f = filter != null ? filter : extractBetween(msg, "filter code [", "]");
            return "mémoire observée" + (mo != null ? " : " + mo + " Mo" : "") + part("filtre", f);
        }

        if (contains(msg, "loadListChilds") || contains(msg, "prepareSearchByRoot")) {
            return "parcours hiérarchique" + part("filtre", filter) + part("objet", object);
        }

        if (contains(msg, "0 row") || contains(msg, "[0] row fetched")) {
            return "recherche sans résultat" + part("filtre", filter) + part("objet", object);
        }

        return shortMessage(msg);
    }

    private boolean isImportant(String msg) {
        return contains(msg, "START fire rules")
                || contains(msg, "END fire rules")
                || contains(msg, "no rule found")
                || contains(msg, "aucune r")
                || contains(msg, "fetching filter")
                || contains(msg, "searchComposantByRoot start execute")
                || contains(msg, "searchComposantByRoot end execute")
                || contains(msg, "Query:")
                || contains(msg, "0 row")
                || contains(msg, "Insertion reussie")
                || contains(msg, "transitTask")
                || contains(msg, "Transit task")
                || contains(msg, "memory usage")
                || contains(msg, "SAVE")
                || contains(msg, "save");
    }

    private String extractProcess(LogEntry log) {
        return logPatternExtractor.extractGraph(log).getProcess();
    }

    private String extractAction(LogEntry log) {
        return logPatternExtractor.extractGraph(log).getAction();
    }

    private String extractFilter(LogEntry log) {
        return logPatternExtractor.extractGraph(log).getFilter();
    }

    private String extractBusinessObject(LogEntry log) {
        return logPatternExtractor.extractGraph(log).getObject();
    }

    private String warningDiagnostic(LogEntry log) {
        if (contains(log.getMessage(), "no rule found") || contains(log.getMessage(), "aucune r")) {
            return "Transition sans règle configurée. À vérifier seulement si une règle métier était attendue.";
        }
        return "Warning détecté dans le workflow.";
    }

    private GroupAnalysisItemDto singleItem(String type, String name, LogEntry log, String diagnostic) {
        GroupAnalysisItemDto dto = new GroupAnalysisItemDto();
        dto.setType(type);
        dto.setName(LogDisplayTextUtil.sanitize(name));
        dto.setCount(1L);
        dto.setFirstTimestamp(log.getLogTimestamp());
        dto.setLastTimestamp(log.getLogTimestamp());
        dto.setDiagnostic(diagnostic);
        dto.setExamples(List.of(LogDisplayTextUtil.sanitize(shortMessage(log.getMessage()))));
        return dto;
    }

    private String extract(Pattern pattern, String text) {
        if (text == null) return null;
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private Long extractLong(Pattern pattern, String text) {
        String v = extract(pattern, text);
        if (v == null) return null;
        try { return Long.parseLong(v); } catch (Exception e) { return null; }
    }

    private String extractBetween(String text, String start, String end) {
        if (text == null) return null;
        int s = text.indexOf(start);
        if (s < 0) return null;
        int e = text.indexOf(end, s + start.length());
        if (e < 0) return null;
        return text.substring(s + start.length(), e).trim();
    }

    private String resultPart(String msg) {
        Matcher m = ROW_PATTERN.matcher(msg);
        if (m.find()) return " — résultat : " + m.group(1) + " ligne(s)";
        return "";
    }

    private String part(String label, String value) {
        return value == null || value.isBlank() ? "" : " — " + label + " : " + value;
    }

    private String formatTime(LogEntry log) {
        return log.getLogTimestamp() == null ? "temps inconnu" : log.getLogTimestamp().toString().replace("T", " ");
    }

    private String shortMessage(String msg) {
        if (msg == null) return "";
        String cleaned = msg.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 220 ? cleaned : cleaned.substring(0, 217) + "...";
    }

    private String joinTopNames(List<GroupAnalysisItemDto> items, int max) {
        return items.stream().limit(max).map(GroupAnalysisItemDto::getName).filter(Objects::nonNull).reduce((a, b) -> a + ", " + b).orElse("aucun");
    }

    private String firstName(List<GroupAnalysisItemDto> items) {
        return items.isEmpty() ? null : items.get(0).getName();
    }

    private String mostFrequent(List<LogEntry> logs, java.util.function.Function<LogEntry, String> fn) {
        Map<String, Long> map = new HashMap<>();
        for (LogEntry log : logs) {
            String v = fn.apply(log);
            if (v != null && !v.isBlank()) map.merge(v, 1L, Long::sum);
        }
        return map.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private boolean contains(String value, String part) {
        return value != null && part != null && value.toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT));
    }

    private boolean eq(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private String safe(String s) {
        return s == null ? "" : s;
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

    private static class ItemAccumulator {
        private final String type;
        private final String name;
        private long count = 0;
        private LocalDateTime first;
        private LocalDateTime last;
        private final List<String> examples = new ArrayList<>();

        ItemAccumulator(String type, String name) {
            this.type = type;
            this.name = name;
        }

        void add(LogEntry log) {
            count++;

            LocalDateTime ts = log.getLogTimestamp();
            if (ts != null) {
                if (first == null || ts.isBefore(first)) first = ts;
                if (last == null || ts.isAfter(last)) last = ts;
            }

            if (examples.size() < 3 && log.getMessage() != null) {
                examples.add(log.getMessage());
            }
        }

        GroupAnalysisItemDto toDto() {
            GroupAnalysisItemDto dto = new GroupAnalysisItemDto();
            dto.setType(type);
            dto.setName(LogDisplayTextUtil.sanitize(name));
            dto.setCount(count);
            dto.setFirstTimestamp(first);
            dto.setLastTimestamp(last);
            dto.setDiagnostic(defaultDiagnostic(type, name));
            dto.setExamples(examples.stream().map(LogDisplayTextUtil::sanitize).limit(3).toList());
            return dto;
        }

        private String defaultDiagnostic(String type, String name) {
            return switch (type) {
                case "PROCESS" -> "Process détecté dans le groupe. Il représente probablement le conteneur métier principal.";
                case "ACTION" -> "Action ou transition détectée dans le workflow.";
                case "FILTER" -> "Filtre métier utilisé pendant le traitement.";
                case "BUSINESS_OBJECT" -> "Objet métier manipulé ou recherché.";
                default -> "Élément détecté.";
            };
        }
    }
}