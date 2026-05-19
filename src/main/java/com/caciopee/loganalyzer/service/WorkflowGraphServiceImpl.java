package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.*;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogEntrySpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WorkflowGraphServiceImpl implements WorkflowGraphService {

    private final LogEntryRepository logEntryRepository;

    private static final Pattern FILTER_PATTERN =
            Pattern.compile("filter code \\[([^]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern FETCHING_FILTER_PATTERN =
            Pattern.compile("fetching filter \\[([^]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern CLASS_PATTERN =
            Pattern.compile("className \\[([^]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern PROCESS_PATTERN =
            Pattern.compile("processName \\[([^]]+)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern ACTION_PATTERN =
            Pattern.compile("actionName \\[([^]]+)]", Pattern.CASE_INSENSITIVE);

    public WorkflowGraphServiceImpl(LogEntryRepository logEntryRepository) {
        this.logEntryRepository = logEntryRepository;
    }

    @Override
    public WorkflowGraphResponseDto buildGraph(GroupAnalysisRequestDto request) {
        List<LogEntry> logs = loadLogs(request);

        Map<String, NodeAcc> nodes = new LinkedHashMap<>();
        Map<String, EdgeAcc> edges = new LinkedHashMap<>();

        String userNodeId = null;
        if ("userName".equals(request.getGroupBy()) && notBlank(request.getGroupKey())) {
            userNodeId = nodeId("USER", request.getGroupKey());
            addNode(nodes, userNodeId, "USER", request.getGroupKey(), "INFO",
                    "Utilisateur sélectionné pour l’analyse.");
        }

        for (LogEntry log : logs) {
            String process = clean(extractProcess(log));
            String action = clean(extractAction(log));
            String filter = clean(extractFilter(log));
            String object = clean(extractBusinessObject(log));

            boolean warning = isWarning(log);
            boolean zeroResult = isZeroResult(log);
            boolean error = isError(log);
            boolean save = isSave(log);

            String processId = null;
            String actionId = null;
            String filterId = null;
            String objectId;

            if (notBlank(process)) {
                processId = nodeId("PROCESS", process);
                addNode(nodes, processId, "PROCESS", process, "INFO",
                        "Process métier ou technique détecté dans le groupe.");
                addExample(nodes, processId, log.getMessage());
                addOccurrence(nodes, processId, log, process, action, filter, object);

                if (userNodeId != null) {
                    addEdge(edges, userNodeId, processId, "travaille_sur", log, process, action, filter, object);
                }
            }

            if (notBlank(action)) {
                actionId = nodeId("ACTION", action);
                addNode(nodes, actionId, "ACTION", action, "INFO",
                        "Action, transition ou étape exécutée dans le workflow.");
                addExample(nodes, actionId, log.getMessage());
                addOccurrence(nodes, actionId, log, process, action, filter, object);

                if (processId != null) {
                    addEdge(edges, processId, actionId, "contient_action", log, process, action, filter, object);
                }
            }

            if (notBlank(filter)) {
                filterId = nodeId("FILTER", filter);
                addNode(nodes, filterId, "FILTER", filter, zeroResult ? "WARNING" : "INFO",
                        "Filtre métier utilisé pour charger ou rechercher des objets.");
                addExample(nodes, filterId, log.getMessage());
                addOccurrence(nodes, filterId, log, process, action, filter, object);

                if (actionId != null) {
                    addEdge(edges, actionId, filterId, "déclenche_filtre", log, process, action, filter, object);
                } else if (processId != null) {
                    addEdge(edges, processId, filterId, "utilise_filtre", log, process, action, filter, object);
                }
            }

            if (notBlank(object)) {
                objectId = nodeId("OBJECT", object);
                addNode(nodes, objectId, "OBJECT", object, "INFO",
                        "Objet métier manipulé ou recherché.");
                addExample(nodes, objectId, log.getMessage());
                addOccurrence(nodes, objectId, log, process, action, filter, object);

                if (filterId != null) {
                    addEdge(edges, filterId, objectId, "recherche_objet", log, process, action, filter, object);
                } else if (processId != null) {
                    addEdge(edges, processId, objectId, "manipule_objet", log, process, action, filter, object);
                }
            }

            if (warning) {
                String warningId = nodeId("WARNING", "Règles absentes / warnings");
                addNode(nodes, warningId, "WARNING", "Règles absentes / warnings", "WARNING",
                        "Warnings ou transitions sans règle configurée.");
                addExample(nodes, warningId, log.getMessage());
                addOccurrence(nodes, warningId, log, process, action, filter, object);

                if (actionId != null) addEdge(edges, actionId, warningId, "produit_warning", log, process, action, filter, object);
                else if (processId != null) addEdge(edges, processId, warningId, "produit_warning", log, process, action, filter, object);
                else if (filterId != null) addEdge(edges, filterId, warningId, "produit_warning", log, process, action, filter, object);
            }

            if (zeroResult) {
                String zeroId = nodeId("ZERO_RESULT", "Recherches sans résultat");
                addNode(nodes, zeroId, "ZERO_RESULT", "Recherches sans résultat", "SUSPECT",
                        "Une ou plusieurs recherches retournent 0 résultat.");
                addExample(nodes, zeroId, log.getMessage());
                addOccurrence(nodes, zeroId, log, process, action, filter, object);

                if (filterId != null) addEdge(edges, filterId, zeroId, "retourne", log, process, action, filter, object);
                else if (processId != null) addEdge(edges, processId, zeroId, "contient", log, process, action, filter, object);
            }

            if (error) {
                String errorId = nodeId("ERROR", "Erreurs critiques");
                addNode(nodes, errorId, "ERROR", "Erreurs critiques", "CRITICAL",
                        "Erreurs applicatives ou techniques critiques.");
                addExample(nodes, errorId, log.getMessage());
                addOccurrence(nodes, errorId, log, process, action, filter, object);

                if (processId != null) addEdge(edges, processId, errorId, "produit_erreur", log, process, action, filter, object);
            }

            if (save) {
                String saveId = nodeId("SAVE", "Sauvegardes / persistance");
                addNode(nodes, saveId, "SAVE", "Sauvegardes / persistance", "INFO",
                        "Opérations de sauvegarde, insertion ou mise à jour.");
                addExample(nodes, saveId, log.getMessage());
                addOccurrence(nodes, saveId, log, process, action, filter, object);

                if (actionId != null) addEdge(edges, actionId, saveId, "déclenche_save", log, process, action, filter, object);
                else if (processId != null) addEdge(edges, processId, saveId, "déclenche_save", log, process, action, filter, object);
            }
        }

        WorkflowGraphResponseDto response = new WorkflowGraphResponseDto();
        response.setGroupBy(request.getGroupBy());
        response.setGroupKey(request.getGroupKey());
        response.setTotalLogs((long) logs.size());
        response.setNodes(nodes.values().stream().map(NodeAcc::toDto).toList());
        response.setEdges(edges.values().stream().map(EdgeAcc::toDto).toList());
        response.setDateFrom(request.getDateFrom() != null ? request.getDateFrom().toString() : null);
        response.setDateTo(request.getDateTo() != null ? request.getDateTo().toString() : null);

        return response;
    }

    private List<LogEntry> loadLogs(GroupAnalysisRequestDto request) {
        Specification<LogEntry> spec =
                LogEntrySpecifications.hasImportIds(request.getImportIds())
                        .and(LogEntrySpecifications.hasTimestampBetween(
                                request.getDateFrom(),
                                request.getDateTo()
                        ))
                        .and(groupSpec(request.getGroupBy(), request.getGroupKey()));

        return logEntryRepository.findAll(
                spec,
                Sort.by(Sort.Direction.ASC, "logTimestamp")
                        .and(Sort.by(Sort.Direction.ASC, "id"))
        );
    }

    private Specification<LogEntry> groupSpec(String groupBy, String groupKey) {
        if (!notBlank(groupBy) || !notBlank(groupKey) || "NON_RENSEIGNE".equalsIgnoreCase(groupKey)) {
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

    private void addNode(Map<String, NodeAcc> nodes, String id, String type, String label, String severity, String description) {
        nodes.computeIfAbsent(id, k -> new NodeAcc(id, type, label, severity, description)).count++;
    }

    private void addExample(Map<String, NodeAcc> nodes, String id, String message) {
        NodeAcc acc = nodes.get(id);
        if (acc != null && acc.examples.size() < 3 && message != null && !message.isBlank()) {
            acc.examples.add(shorten(message));
        }
    }

    private void addOccurrence(Map<String, NodeAcc> nodes,
                               String id,
                               LogEntry log,
                               String process,
                               String action,
                               String filter,
                               String object) {
        NodeAcc acc = nodes.get(id);
        if (acc == null) return;

        if (acc.occurrences.size() >= 200) return;

        GraphOccurrenceDto occ = new GraphOccurrenceDto();
        occ.setLogId(log.getId());
        occ.setTimestamp(log.getLogTimestamp());
        occ.setProcessName(process);
        occ.setActionName(action);
        occ.setFilterCode(filter);
        occ.setBusinessObject(object);
        occ.setMessagePreview(shorten(log.getMessage()));

        acc.occurrences.add(occ);
    }

    private void addEdge(Map<String, EdgeAcc> edges,
                         String from,
                         String to,
                         String relation,
                         LogEntry log,
                         String process,
                         String action,
                         String filter,
                         String object) {
        if (!notBlank(from) || !notBlank(to)) return;

        String key = from + "->" + to + "::" + relation;
        EdgeAcc acc = edges.computeIfAbsent(key, k -> new EdgeAcc(from, to, relation));
        acc.count++;

        if (acc.occurrences.size() < 300) {
            GraphOccurrenceDto occ = new GraphOccurrenceDto();
            occ.setLogId(log.getId());
            occ.setTimestamp(log.getLogTimestamp());
            occ.setProcessName(process);
            occ.setActionName(action);
            occ.setFilterCode(filter);
            occ.setBusinessObject(object);
            occ.setMessagePreview(shorten(log.getMessage()));
            acc.occurrences.add(occ);
        }
    }

    private String extractProcess(LogEntry log) {
        String fromMsg = extract(PROCESS_PATTERN, log.getMessage());
        return firstNonBlank(fromMsg, log.getProcessName());
    }

    private String extractAction(LogEntry log) {
        String msg = safe(log.getMessage());

        String action = extract(ACTION_PATTERN, msg);
        if (notBlank(action)) return action;

        if (msg.contains("transition=")) {
            Matcher m = Pattern.compile("transition=([^,}\\s]+)", Pattern.CASE_INSENSITIVE).matcher(msg);
            if (m.find()) return m.group(1);
        }

        if (msg.contains("|")) {
            String[] p = msg.split("\\|");
            if (p.length >= 4 && isLikelyAction(p[3])) return p[3].trim();
        }

        return null;
    }

    private String extractFilter(LogEntry log) {
        String msg = safe(log.getMessage());

        String f1 = extract(FILTER_PATTERN, msg);
        if (notBlank(f1)) return f1;

        String f2 = extract(FETCHING_FILTER_PATTERN, msg);
        if (notBlank(f2)) return f2;

        return null;
    }

    private String extractBusinessObject(LogEntry log) {
        return extract(CLASS_PATTERN, log.getMessage());
    }

    private boolean isWarning(LogEntry log) {
        String msg = safe(log.getMessage()).toLowerCase(Locale.ROOT);
        return "WARN".equalsIgnoreCase(safe(log.getLevel()))
                || msg.contains("no rule found")
                || msg.contains("aucune r");
    }

    private boolean isZeroResult(LogEntry log) {
        String msg = safe(log.getMessage()).toLowerCase(Locale.ROOT);
        return msg.contains("0 row") || msg.contains("[0] row fetched");
    }

    private boolean isError(LogEntry log) {
        return Boolean.TRUE.equals(log.getIsError())
                || "ERROR".equalsIgnoreCase(safe(log.getLevel()));
    }

    private boolean isSave(LogEntry log) {
        String msg = safe(log.getMessage()).toLowerCase(Locale.ROOT);
        return msg.contains("save")
                || msg.contains("insertarray")
                || msg.contains("removeobjectstodelete")
                || msg.contains("insertion reussie")
                || msg.contains("insertion réussie");
    }

    private boolean isLikelyAction(String value) {
        if (!notBlank(value)) return false;

        String v = value.trim();

        if (v.length() > 80) return false;

        String upper = v.toUpperCase(Locale.ROOT);

        return upper.equals("BEFORE_LOAD")
                || upper.equals("ACTION_NAME_SAVE")
                || upper.equals("VALIDER")
                || upper.equals("ANNULER")
                || upper.equals("IMPRIMER")
                || upper.equals("AFFECTATION")
                || upper.contains("LOAD")
                || upper.contains("SAVE");
    }

    private String nodeId(String type, String label) {
        return type + "::" + normalize(label);
    }

    private String normalize(String value) {
        return safe(value)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9A-ZÀ-ÿ._ -]", "_");
    }

    private String clean(String value) {
        if (!notBlank(value)) return null;
        String v = value.trim();
        if ("null".equalsIgnoreCase(v)) return null;
        return v;
    }

    private String extract(Pattern pattern, String text) {
        if (text == null) return null;
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String firstNonBlank(String a, String b) {
        return notBlank(a) ? a : b;
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String shorten(String value) {
        if (value == null) return "";
        String v = value.replaceAll("\\s+", " ").trim();
        return v.length() <= 260 ? v : v.substring(0, 257) + "...";
    }

    private static class NodeAcc {
        String id;
        String type;
        String label;
        String severity;
        String description;
        long count = 0;
        List<String> examples = new ArrayList<>();
        List<GraphOccurrenceDto> occurrences = new ArrayList<>();

        NodeAcc(String id, String type, String label, String severity, String description) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.severity = severity;
            this.description = description;
        }

        GraphNodeDto toDto() {
            GraphNodeDto dto = new GraphNodeDto();
            dto.setId(id);
            dto.setType(type);
            dto.setLabel(label);
            dto.setSeverity(severity);
            dto.setDescription(description);
            dto.setCount(count);
            dto.setExamples(examples);
            dto.setOccurrences(occurrences);
            return dto;
        }
    }

    private static class EdgeAcc {
        String from;
        String to;
        String relation;
        long count = 0;
        List<GraphOccurrenceDto> occurrences = new ArrayList<>();

        EdgeAcc(String from, String to, String relation) {
            this.from = from;
            this.to = to;
            this.relation = relation;
        }

        GraphEdgeDto toDto() {
            GraphEdgeDto dto = new GraphEdgeDto();
            dto.setFrom(from);
            dto.setTo(to);
            dto.setRelation(relation);
            dto.setCount(count);
            dto.setOccurrences(occurrences);
            return dto;
        }
    }
}