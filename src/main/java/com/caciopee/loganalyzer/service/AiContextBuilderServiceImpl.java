package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.AiContextRequestDto;
import com.caciopee.loganalyzer.dto.AiContextResponseDto;
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
public class AiContextBuilderServiceImpl implements AiContextBuilderService {

    private final LogEntryRepository logEntryRepository;

    private static final Pattern FILTER_PATTERN =
            Pattern.compile("filter code \\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLASS_PATTERN =
            Pattern.compile("className \\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern ACTION_PATTERN =
            Pattern.compile("actionName \\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern PROCESS_PATTERN =
            Pattern.compile("processName \\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern TOOK_PATTERN =
            Pattern.compile("took \\[?(\\d+)]?\\s*ms", Pattern.CASE_INSENSITIVE);

    private static final Pattern MEMORY_PATTERN =
            Pattern.compile("memory usage \\(Mo\\).*?]\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    public AiContextBuilderServiceImpl(LogEntryRepository logEntryRepository) {
        this.logEntryRepository = logEntryRepository;
    }

    @Override
    public AiContextResponseDto buildContext(AiContextRequestDto request) {
        List<LogEntry> logs = loadLogs(request);

        AiContextResponseDto response = new AiContextResponseDto();
        response.setTotalLogsUsed(logs.size());

        if (logs.isEmpty()) {
            response.setContext("Aucun log trouvé pour construire le contexte IA.");
            return response;
        }

        StringBuilder ctx = new StringBuilder();

        ctx.append("CONTEXTE D’ANALYSE POUR IA\n");
        ctx.append("==========================\n\n");

        ctx.append("1. PÉRIMÈTRE\n");
        ctx.append("- GroupBy : ").append(nullSafe(request.getGroupBy(), "non précisé")).append("\n");
        ctx.append("- GroupKey : ").append(nullSafe(request.getGroupKey(), "non précisé")).append("\n");
        ctx.append("- Nombre total de logs analysés : ").append(logs.size()).append("\n");
        ctx.append("- Période : ").append(firstTimestamp(logs)).append(" → ").append(lastTimestamp(logs)).append("\n\n");

        ctx.append("2. SYNTHÈSE TECHNIQUE\n");
        ctx.append("- Erreurs critiques : ").append(countErrors(logs)).append("\n");
        ctx.append("- Warnings / règles absentes : ").append(countWarnings(logs)).append("\n");
        ctx.append("- Recherches sans résultat : ").append(countZeroRows(logs)).append("\n");
        ctx.append("- Signaux performance/mémoire : ").append(countPerformanceSignals(logs)).append("\n\n");

        ctx.append("3. STRUCTURE MÉTIER DÉTECTÉE\n");
        ctx.append("- Process principaux : ").append(topValues(logs, this::extractProcess, 8)).append("\n");
        ctx.append("- Actions principales : ").append(topValues(logs, this::extractAction, 8)).append("\n");
        ctx.append("- Filtres principaux : ").append(topValues(logs, this::extractFilter, 12)).append("\n");
        ctx.append("- Objets métier principaux : ").append(topValues(logs, this::extractBusinessObject, 10)).append("\n\n");

        ctx.append("4. HISTOIRE CHRONOLOGIQUE COMPACTE\n");
        ctx.append(buildBusinessStory(logs)).append("\n\n");

        ctx.append("5. POINTS DE DIAGNOSTIC À EXAMINER\n");
        ctx.append(buildDiagnosticSignals(logs)).append("\n\n");

        ctx.append("6. PREUVES REPRÉSENTATIVES\n");
        ctx.append(buildEvidence(logs, normalizeEvidenceLimit(request.getMaxEvidenceLogs()))).append("\n");

        response.setContext(ctx.toString());
        return response;
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
                    + durationPart(msg);
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
                    + durationPart(msg);
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
                    + durationPart(msg);
        }

        if (lower.contains("memory usage")) {
            return "mesure de consommation mémoire observée" + memoryPart(msg);
        }

        if (lower.contains("took")) {
            return "durée d’exécution observée" + durationPart(msg);
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
                .map(LogEntry::getMessage)
                .filter(Objects::nonNull)
                .filter(m -> extractLong(MEMORY_PATTERN, m) != null && extractLong(MEMORY_PATTERN, m) >= 8000)
                .limit(5)
                .toList();

        if (!highMemory.isEmpty()) {
            sb.append("- Performance : mémoire élevée détectée dans plusieurs étapes.\n");
        }

        List<String> slow = logs.stream()
                .map(LogEntry::getMessage)
                .filter(Objects::nonNull)
                .filter(m -> extractLong(TOOK_PATTERN, m) != null && extractLong(TOOK_PATTERN, m) >= 1000)
                .limit(5)
                .toList();

        if (!slow.isEmpty()) {
            sb.append("- Performance : certaines étapes dépassent 1000 ms.\n");
        }

        return sb.toString();
    }

    private String buildEvidence(List<LogEntry> logs, int max) {
        List<LogEntry> important = logs.stream()
                .filter(this::isEvidenceImportant)
                .limit(max)
                .toList();

        if (important.isEmpty()) {
            important = logs.stream().limit(max).toList();
        }

        StringBuilder sb = new StringBuilder();

        for (LogEntry log : important) {
            sb.append("- [")
                    .append(formatTime(log))
                    .append("] ")
                    .append("level=").append(nullSafe(log.getLevel(), "N/A"))
                    .append(", process=").append(nullSafe(log.getProcessName(), "N/A"))
                    .append(", message=").append(shortMessage(log.getMessage()))
                    .append("\n");
        }

        return sb.toString();
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
                    String msg = safe(l.getMessage());
                    Long duration = extractLong(TOOK_PATTERN, msg);
                    Long memory = extractLong(MEMORY_PATTERN, msg);
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
        String fromMsg = extract(PROCESS_PATTERN, log.getMessage());
        return firstNonBlank(fromMsg, log.getProcessName());
    }

    private String extractAction(LogEntry log) {
        String msg = safe(log.getMessage());

        String action = extract(ACTION_PATTERN, msg);
        if (action != null) return action;

        if (msg.contains("transition=")) {
            Matcher m = Pattern.compile("transition=([^,}\\s]+)", Pattern.CASE_INSENSITIVE).matcher(msg);
            if (m.find()) return m.group(1);
        }

        if (msg.contains("|")) {
            String[] p = msg.split("\\|");
            if (p.length >= 4 && isLikelyAction(p[3])) {
                return p[3].trim();
            }
        }

        return null;
    }

    private boolean isLikelyAction(String value) {
        if (value == null) return false;
        String v = value.trim();
        if (v.isBlank()) return false;
        if (v.length() > 80) return false;

        String upper = v.toUpperCase(Locale.ROOT);
        return upper.equals("LOAD")
                || upper.equals("BEFORE_LOAD")
                || upper.equals("SAVE")
                || upper.equals("ACTION_NAME_SAVE")
                || upper.equals("VALIDER")
                || upper.equals("ANNULER")
                || upper.equals("IMPRIMER")
                || upper.contains("LOAD")
                || upper.contains("SAVE");
    }

    private String extractFilter(LogEntry log) {
        String msg = safe(log.getMessage());

        String value = extract(FILTER_PATTERN, msg);
        if (value != null) return value;

        String fetched = extractBetween(msg, "fetching filter [", "]");
        if (fetched != null) return fetched;

        return null;
    }

    private String extractBusinessObject(LogEntry log) {
        return extract(CLASS_PATTERN, log.getMessage());
    }

    private String extract(Pattern pattern, String text) {
        if (text == null) return null;
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private Long extractLong(Pattern pattern, String text) {
        String value = extract(pattern, text);
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return null;
        }
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

    private String durationPart(String msg) {
        Long value = extractLong(TOOK_PATTERN, msg);
        return value == null ? "" : " en " + value + " ms";
    }

    private String memoryPart(String msg) {
        Long value = extractLong(MEMORY_PATTERN, msg);
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