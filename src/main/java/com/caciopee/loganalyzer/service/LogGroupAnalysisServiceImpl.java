package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.GroupAnalysisItemDto;
import com.caciopee.loganalyzer.dto.GroupAnalysisRequestDto;
import com.caciopee.loganalyzer.dto.GroupAnalysisResponseDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogEntrySpecifications;
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

    private static final Pattern FILTER_PATTERN =
            Pattern.compile("filter code \\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLASS_PATTERN =
            Pattern.compile("className \\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern PROCESS_PATTERN =
            Pattern.compile("processName \\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern TASK_PATTERN =
            Pattern.compile("taskName \\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern ACTION_PATTERN =
            Pattern.compile("actionName \\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern ROW_PATTERN =
            Pattern.compile("(\\d+)\\s*row", Pattern.CASE_INSENSITIVE);

    private static final Pattern TOOK_PATTERN =
            Pattern.compile("took \\[?(\\d+)]?\\s*ms", Pattern.CASE_INSENSITIVE);

    private static final Pattern MEMORY_PATTERN =
            Pattern.compile("memory usage \\(Mo\\).*?]\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    public LogGroupAnalysisServiceImpl(LogEntryRepository logEntryRepository) {
        this.logEntryRepository = logEntryRepository;
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
        return logs.stream()
                .filter(l -> contains(l.getMessage(), "no rule found") || contains(l.getMessage(), "aucune r") || eq(l.getLevel(), "WARN"))
                .map(l -> singleItem("WARNING", shortMessage(l.getMessage()), l, warningDiagnostic(l)))
                .limit(100)
                .toList();
    }

    private List<GroupAnalysisItemDto> buildZeroResults(List<LogEntry> logs) {
        return logs.stream()
                .filter(l -> contains(l.getMessage(), "0 row") || contains(l.getMessage(), "[0] row fetched"))
                .map(l -> {
                    String filter = extractFilter(l);
                    String name = filter != null ? filter : "Recherche sans résultat";
                    return singleItem("ZERO_RESULT", name, l, "Cette recherche n’a retourné aucun résultat. À vérifier si l’objet métier était attendu.");
                })
                .limit(100)
                .toList();
    }

    private List<GroupAnalysisItemDto> buildPerformanceSignals(List<LogEntry> logs) {
        List<GroupAnalysisItemDto> result = new ArrayList<>();

        for (LogEntry log : logs) {
            String msg = safe(log.getMessage());

            Long duration = extractLong(TOOK_PATTERN, msg);
            Long memory = extractLong(MEMORY_PATTERN, msg);

            if (duration != null && duration >= 1000) {
                result.add(singleItem("PERFORMANCE", "Étape lente " + duration + " ms", log, "Durée élevée détectée."));
            }

            if (memory != null && memory >= 8000) {
                result.add(singleItem("MEMORY", "Mémoire élevée " + memory + " Mo", log, "Consommation mémoire élevée observée."));
            }
        }

        return result.stream().limit(100).toList();
    }

    private List<String> buildTimeline(List<LogEntry> logs) {
        List<String> timeline = new ArrayList<>();

        for (LogEntry log : logs) {
            String msg = safe(log.getMessage());

            if (isImportant(msg)) {
                timeline.add(formatTime(log) + " — " + humanize(log));
            }

            if (timeline.size() >= 80) break;
        }

        return timeline;
    }

    private String buildNarrative(GroupAnalysisResponseDto r, List<LogEntry> logs) {
        String user = "userName".equals(r.getGroupBy()) ? r.getGroupKey() : mostFrequent(logs, LogEntry::getUserName);
        String mainProcess = firstName(r.getProcesses());
        StringBuilder sb = new StringBuilder();

        sb.append("L’utilisateur ").append(nullSafe(user, "non identifié"));

        if (mainProcess != null) {
            sb.append(" travaille principalement sur le process ").append(mainProcess).append(".");
        } else {
            sb.append(" exécute une suite d’actions applicatives.");
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
            sb.append("Certaines recherches retournent 0 résultat. Ce n’est pas forcément bloquant, mais ce sont des points à vérifier dans le diagnostic.");
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

        String process = extractProcess(log);
        String action = extractAction(log);
        String filter = extractFilter(log);
        String object = extractBusinessObject(log);

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
        String fromMsg = extract(PROCESS_PATTERN, log.getMessage());
        return firstNonBlank(fromMsg, log.getProcessName());
    }

    private String extractAction(LogEntry log) {
        String msg = safe(log.getMessage());
        String action = extract(ACTION_PATTERN, msg);

        if (action != null) return action;

        if (msg.contains("|")) {
            String[] p = msg.split("\\|");
            if (p.length >= 4 && !p[3].isBlank()) return p[3].trim();
        }

        return null;
    }

    private String extractFilter(LogEntry log) {
        return extract(FILTER_PATTERN, log.getMessage());
    }

    private String extractBusinessObject(LogEntry log) {
        return extract(CLASS_PATTERN, log.getMessage());
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
        dto.setName(name);
        dto.setCount(1L);
        dto.setFirstTimestamp(log.getLogTimestamp());
        dto.setLastTimestamp(log.getLogTimestamp());
        dto.setDiagnostic(diagnostic);
        dto.setExamples(List.of(shortMessage(log.getMessage())));
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
            dto.setName(name);
            dto.setCount(count);
            dto.setFirstTimestamp(first);
            dto.setLastTimestamp(last);
            dto.setDiagnostic(defaultDiagnostic(type, name));
            dto.setExamples(examples);
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