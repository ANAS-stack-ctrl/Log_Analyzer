package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.catalog.CatalogUserLabels;
import com.caciopee.loganalyzer.analysis.catalog.WorksMessageCatalogService;
import com.caciopee.loganalyzer.analysis.catalog.WorksMessageFamily;
import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.*;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogEntrySpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class WorkflowGraphServiceImpl implements WorkflowGraphService {

    private final LogEntryRepository logEntryRepository;
    private final LogPatternExtractor logPatternExtractor;
    private final WorksMessageCatalogService messageCatalogService;

    public WorkflowGraphServiceImpl(LogEntryRepository logEntryRepository,
                                   LogPatternExtractor logPatternExtractor,
                                   WorksMessageCatalogService messageCatalogService) {
        this.logEntryRepository = logEntryRepository;
        this.logPatternExtractor = logPatternExtractor;
        this.messageCatalogService = messageCatalogService;
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
            GraphExtraction ex = logPatternExtractor.extractGraph(log);

            String process = clean(ex.getProcess());
            String task = clean(ex.getTask());
            String action = clean(ex.getAction());
            String filter = clean(ex.getFilter());
            String object = clean(ex.getObject());

            String processId = null;
            String taskId = null;
            String actionId = null;
            String filterId = null;
            String objectId = null;

            if (notBlank(process)) {
                processId = nodeId("PROCESS", process);
                addNode(nodes, processId, "PROCESS", process, "INFO",
                        "Process métier ou technique détecté dans le groupe.");
                addExample(nodes, processId, log.getMessage());
                addOccurrence(nodes, processId, log, process, task, action, filter, object);

                if (userNodeId != null) {
                    addEdge(edges, userNodeId, processId, "travaille_sur", log, process, task, action, filter, object);
                }
            }

            if (notBlank(task)) {
                taskId = nodeId("TASK", task);
                addNode(nodes, taskId, "TASK", task, "INFO",
                        "Tâche ou écran métier (taskName) exécuté dans le workflow.");
                addExample(nodes, taskId, log.getMessage());
                addOccurrence(nodes, taskId, log, process, task, action, filter, object);

                if (processId != null) {
                    addEdge(edges, processId, taskId, "contient_tache", log, process, task, action, filter, object);
                }
            }

            if (notBlank(action)) {
                actionId = nodeId("ACTION", action);
                addNode(nodes, actionId, "ACTION", action, "INFO",
                        "Action, transition ou étape exécutée dans le workflow.");
                addExample(nodes, actionId, log.getMessage());
                addOccurrence(nodes, actionId, log, process, task, action, filter, object);

                if (taskId != null) {
                    addEdge(edges, taskId, actionId, "contient_action", log, process, task, action, filter, object);
                } else if (processId != null) {
                    addEdge(edges, processId, actionId, "contient_action", log, process, task, action, filter, object);
                }
            }

            if (notBlank(filter) && !isRedundantProcessFilter(filter, process)) {
                filterId = nodeId("FILTER", filter);
                addNode(nodes, filterId, "FILTER", filter, ex.isZeroResult() ? "WARNING" : "INFO",
                        "Filtre métier utilisé pour charger ou rechercher des objets.");
                addExample(nodes, filterId, log.getMessage());
                addOccurrence(nodes, filterId, log, process, task, action, filter, object);

                if (actionId != null) {
                    addEdge(edges, actionId, filterId, "déclenche_filtre", log, process, task, action, filter, object);
                } else if (taskId != null) {
                    addEdge(edges, taskId, filterId, "utilise_filtre", log, process, task, action, filter, object);
                } else if (processId != null) {
                    addEdge(edges, processId, filterId, "utilise_filtre", log, process, task, action, filter, object);
                }
            }

            if (notBlank(object)) {
                objectId = nodeId("OBJECT", object);
                addNode(nodes, objectId, "OBJECT", object, "INFO",
                        "Objet métier manipulé ou recherché.");
                addExample(nodes, objectId, log.getMessage());
                addOccurrence(nodes, objectId, log, process, task, action, filter, object);

                if (filterId != null) {
                    addEdge(edges, filterId, objectId, "recherche_objet", log, process, task, action, filter, object);
                } else if (actionId != null) {
                    addEdge(edges, actionId, objectId, "manipule_objet", log, process, task, action, filter, object);
                } else if (processId != null) {
                    addEdge(edges, processId, objectId, "manipule_objet", log, process, task, action, filter, object);
                }
            }

            attachFamilyNode(nodes, edges, log, process, task, action, filter, object,
                    filterId, objectId, processId, taskId, actionId);

            if (ex.isZeroResult()) {
                String zeroId = nodeId("ZERO_RESULT", "Recherches sans résultat");
                addNode(nodes, zeroId, "ZERO_RESULT", "Recherches sans résultat", "SUSPECT",
                        "Une ou plusieurs recherches retournent 0 résultat.");
                addExample(nodes, zeroId, log.getMessage());
                addOccurrence(nodes, zeroId, log, process, task, action, filter, object);

                if (filterId != null) {
                    addEdge(edges, filterId, zeroId, "retourne", log, process, task, action, filter, object);
                } else {
                    linkSignal(edges, zeroId, "contient", log, process, task, action, filter, object,
                            processId, taskId, actionId, filterId);
                }
            }

            if (ex.isError()) {
                String errorId = nodeId("ERROR", "Erreurs critiques");
                addNode(nodes, errorId, "ERROR", "Erreurs critiques", "CRITICAL",
                        "Erreurs applicatives ou techniques critiques.");
                addExample(nodes, errorId, log.getMessage());
                addOccurrence(nodes, errorId, log, process, task, action, filter, object);

                if (processId != null) {
                    addEdge(edges, processId, errorId, "produit_erreur", log, process, task, action, filter, object);
                }
            }

            if (ex.isSave()) {
                String saveId = nodeId("SAVE", "Sauvegardes / persistance");
                addNode(nodes, saveId, "SAVE", "Sauvegardes / persistance", "INFO",
                        "Opérations de sauvegarde, insertion ou mise à jour.");
                addExample(nodes, saveId, log.getMessage());
                addOccurrence(nodes, saveId, log, process, task, action, filter, object);

                linkSignal(edges, saveId, "déclenche_save", log, process, task, action, filter, object,
                        processId, taskId, actionId, filterId);
            }
        }

        detectPerformanceAnomalies(logs, nodes, edges);
        detectMemoryAnomalies(logs, nodes, edges);

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

    private void attachFamilyNode(Map<String, NodeAcc> nodes,
                                  Map<String, EdgeAcc> edges,
                                  LogEntry log,
                                  String process,
                                  String task,
                                  String action,
                                  String filter,
                                  String object,
                                  String filterId,
                                  String objectId,
                                  String processId,
                                  String taskId,
                                  String actionId) {
        messageCatalogService.classifyForGraph(log).ifPresent(family -> {
            String parentId = firstNonBlank(filterId, objectId, actionId, taskId, processId);
            if (parentId == null) {
                return;
            }

            String familyNodeId = nodeId("FAMILY", family.getId());
            String label = family.getDisplayLabel();
            addNode(nodes, familyNodeId, "FAMILY", label, "INFO",
                    family.getGroupDisplayLabel() + " — " + CatalogUserLabels.graphDescription(family),
                    parentId, 2, family.getId(), family.getGroup());
            addExample(nodes, familyNodeId, log.getMessage());
            addOccurrence(nodes, familyNodeId, log, process, task, action, filter, object);
            addEdge(edges, parentId, familyNodeId, "famille_message", log, process, task, action, filter, object);
        });
    }

    private void linkSignal(Map<String, EdgeAcc> edges,
                            String signalId,
                            String relation,
                            LogEntry log,
                            String process,
                            String task,
                            String action,
                            String filter,
                            String object,
                            String processId,
                            String taskId,
                            String actionId,
                            String filterId) {
        if (actionId != null) {
            addEdge(edges, actionId, signalId, relation, log, process, task, action, filter, object);
        } else if (taskId != null) {
            addEdge(edges, taskId, signalId, relation, log, process, task, action, filter, object);
        } else if (filterId != null) {
            addEdge(edges, filterId, signalId, relation, log, process, task, action, filter, object);
        } else if (processId != null) {
            addEdge(edges, processId, signalId, relation, log, process, task, action, filter, object);
        }
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

    private void detectPerformanceAnomalies(List<LogEntry> logs,
                                            Map<String, NodeAcc> nodes,
                                            Map<String, EdgeAcc> edges) {
        Map<String, List<DurationPoint>> grouped = new LinkedHashMap<>();

        for (LogEntry log : logs) {
            GraphExtraction ex = logPatternExtractor.extractGraph(log);
            Long duration = ex.getDurationMs();
            if (duration == null || duration <= 0) {
                continue;
            }

            String process = clean(ex.getProcess());
            String action = clean(ex.getAction());
            String filter = clean(ex.getFilter());
            String object = clean(ex.getObject());

            String nature = firstNonBlank(filter, firstNonBlank(action, firstNonBlank(process, "GENERAL")));

            grouped.computeIfAbsent(nature, k -> new ArrayList<>())
                    .add(new DurationPoint(log, duration, process, ex.getTask(), action, filter, object));
        }

        for (Map.Entry<String, List<DurationPoint>> entry : grouped.entrySet()) {
            String nature = entry.getKey();
            List<DurationPoint> points = entry.getValue();

            if (points.size() < 5) {
                continue;
            }

            double avg = points.stream().mapToLong(p -> p.durationMs).average().orElse(0);

            List<Long> sorted = points.stream().map(p -> p.durationMs).sorted().toList();
            long median = sorted.get(sorted.size() / 2);

            long threshold = Math.max(1000, Math.max((long) (avg * 3), median * 4));

            for (DurationPoint p : points) {
                if (p.durationMs < threshold) {
                    continue;
                }

                String anomalyLabel = "Durée anormale : " + nature + " (" + p.durationMs + " ms)";
                String anomalyId = nodeId("PERFORMANCE_ANOMALY", anomalyLabel);

                addNode(nodes, anomalyId, "PERFORMANCE_ANOMALY", anomalyLabel, "SUSPECT",
                        "Durée de traitement supérieure aux autres traitements de même nature. Moyenne observée : "
                                + Math.round(avg) + " ms, médiane : " + median + " ms, durée détectée : "
                                + p.durationMs + " ms.");

                addExample(nodes, anomalyId, p.log.getMessage());
                addOccurrence(nodes, anomalyId, p.log, p.process, p.task, p.action, p.filter, p.object);

                String processId = notBlank(p.process) ? nodeId("PROCESS", p.process) : null;
                String taskId = notBlank(p.task) ? nodeId("TASK", p.task) : null;
                String actionId = notBlank(p.action) ? nodeId("ACTION", p.action) : null;
                String filterId = notBlank(p.filter) ? nodeId("FILTER", p.filter) : null;

                if (filterId != null) {
                    addEdge(edges, filterId, anomalyId, "durée_anormale", p.log, p.process, p.task, p.action, p.filter, p.object);
                } else if (actionId != null) {
                    addEdge(edges, actionId, anomalyId, "durée_anormale", p.log, p.process, p.task, p.action, p.filter, p.object);
                } else if (taskId != null) {
                    addEdge(edges, taskId, anomalyId, "durée_anormale", p.log, p.process, p.task, p.action, p.filter, p.object);
                } else if (processId != null) {
                    addEdge(edges, processId, anomalyId, "durée_anormale", p.log, p.process, p.task, p.action, p.filter, p.object);
                }
            }
        }
    }

    private static final int MEMORY_ABSOLUTE_THRESHOLD_MO = 8000;
    private static final int MEMORY_STATISTICAL_MIN_SAMPLES = 5;
    private static final int MEMORY_STATISTICAL_FLOOR_MO = 3000;

    private void detectMemoryAnomalies(List<LogEntry> logs,
                                       Map<String, NodeAcc> nodes,
                                       Map<String, EdgeAcc> edges) {
        Map<String, List<MemoryPoint>> grouped = new LinkedHashMap<>();

        for (LogEntry log : logs) {
            GraphExtraction ex = logPatternExtractor.extractGraph(log);
            Integer memoryMo = ex.getMemoryMo();
            if (memoryMo == null || memoryMo <= 0) {
                continue;
            }

            String process = clean(ex.getProcess());
            String action = clean(ex.getAction());
            String filter = clean(ex.getFilter());
            String object = clean(ex.getObject());

            String nature = firstNonBlank(filter, firstNonBlank(action, firstNonBlank(process, "GENERAL")));

            grouped.computeIfAbsent(nature, k -> new ArrayList<>())
                    .add(new MemoryPoint(log, memoryMo, process, ex.getTask(), action, filter, object));
        }

        for (Map.Entry<String, List<MemoryPoint>> entry : grouped.entrySet()) {
            String nature = entry.getKey();
            List<MemoryPoint> points = entry.getValue();

            double avg = points.stream().mapToInt(p -> p.memoryMo).average().orElse(0);
            List<Integer> sorted = points.stream().map(p -> p.memoryMo).sorted().toList();
            int median = sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2);

            int statisticalThreshold = MEMORY_STATISTICAL_FLOOR_MO;
            if (points.size() >= MEMORY_STATISTICAL_MIN_SAMPLES) {
                statisticalThreshold = Math.max(
                        MEMORY_STATISTICAL_FLOOR_MO,
                        Math.max((int) (avg * 2.5), median * 3)
                );
            }

            for (MemoryPoint p : points) {
                boolean absoluteHigh = p.memoryMo >= MEMORY_ABSOLUTE_THRESHOLD_MO;
                boolean statisticalHigh = points.size() >= MEMORY_STATISTICAL_MIN_SAMPLES
                        && p.memoryMo >= statisticalThreshold;

                if (!absoluteHigh && !statisticalHigh) {
                    continue;
                }

                String reason = absoluteHigh
                        ? "seuil absolu " + MEMORY_ABSOLUTE_THRESHOLD_MO + " Mo"
                        : "au-dessus de la normale du groupe (seuil ~" + statisticalThreshold + " Mo)";

                String anomalyLabel = "Mémoire anormale : " + nature + " (" + p.memoryMo + " Mo)";
                String anomalyId = nodeId("MEMORY_ANOMALY", anomalyLabel);

                addNode(nodes, anomalyId, "MEMORY_ANOMALY", anomalyLabel, "SUSPECT",
                        "Consommation mémoire élevée détectée (" + reason + "). "
                                + "Moyenne du groupe : " + Math.round(avg) + " Mo, médiane : " + median
                                + " Mo, valeur observée : " + p.memoryMo + " Mo.");

                addExample(nodes, anomalyId, p.log.getMessage());
                addOccurrence(nodes, anomalyId, p.log, p.process, p.task, p.action, p.filter, p.object);

                linkMemoryAnomalyEdge(edges, anomalyId, p);
            }
        }
    }

    private void linkMemoryAnomalyEdge(Map<String, EdgeAcc> edges, String anomalyId, MemoryPoint p) {
        String processId = notBlank(p.process) ? nodeId("PROCESS", p.process) : null;
        String taskId = notBlank(p.task) ? nodeId("TASK", p.task) : null;
        String actionId = notBlank(p.action) ? nodeId("ACTION", p.action) : null;
        String filterId = notBlank(p.filter) ? nodeId("FILTER", p.filter) : null;
        String objectId = notBlank(p.object) ? nodeId("OBJECT", p.object) : null;

        if (filterId != null) {
            addEdge(edges, filterId, anomalyId, "mémoire_anormale", p.log, p.process, p.task, p.action, p.filter, p.object);
        } else if (objectId != null) {
            addEdge(edges, objectId, anomalyId, "mémoire_anormale", p.log, p.process, p.task, p.action, p.filter, p.object);
        } else if (actionId != null) {
            addEdge(edges, actionId, anomalyId, "mémoire_anormale", p.log, p.process, p.task, p.action, p.filter, p.object);
        } else if (taskId != null) {
            addEdge(edges, taskId, anomalyId, "mémoire_anormale", p.log, p.process, p.task, p.action, p.filter, p.object);
        } else if (processId != null) {
            addEdge(edges, processId, anomalyId, "mémoire_anormale", p.log, p.process, p.task, p.action, p.filter, p.object);
        }
    }

    private void addNode(Map<String, NodeAcc> nodes, String id, String type, String label, String severity, String description) {
        addNode(nodes, id, type, label, severity, description, null, 1, null, null);
    }

    private void addNode(Map<String, NodeAcc> nodes, String id, String type, String label, String severity, String description,
                         String parentId, int level, String familyId, String familyGroup) {
        nodes.computeIfAbsent(id, k -> new NodeAcc(id, type, label, severity, description, parentId, level, familyId, familyGroup)).count++;
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
                               String task,
                               String action,
                               String filter,
                               String object) {
        NodeAcc acc = nodes.get(id);
        if (acc == null) {
            return;
        }

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
                         String task,
                         String action,
                         String filter,
                         String object) {
        if (!notBlank(from) || !notBlank(to)) {
            return;
        }

        String key = from + "->" + to + "::" + relation;
        EdgeAcc acc = edges.computeIfAbsent(key, k -> new EdgeAcc(from, to, relation));
        acc.count++;

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

    private String nodeId(String type, String label) {
        return type + "::" + normalize(label);
    }

    /**
     * Évite un nœud FILTRE doublon quand l'extraction a recopié le nom du process
     * (ex. {@code process.AMPE Contrôle}) sans {@code filter code [...]} dans le log.
     */
    private boolean isRedundantProcessFilter(String filter, String process) {
        if (!notBlank(filter) || !notBlank(process)) {
            return false;
        }
        String f = filter.trim();
        String p = process.trim();
        if (f.equalsIgnoreCase(p)) {
            return true;
        }
        if (f.regionMatches(true, 0, "process.", 0, 8)) {
            return f.substring(8).trim().equalsIgnoreCase(p);
        }
        return false;
    }

    private String normalize(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9A-ZÀ-ÿ._ -]", "_");
    }

    private String clean(String value) {
        if (!notBlank(value)) {
            return null;
        }
        String v = value.trim();
        if ("null".equalsIgnoreCase(v)) {
            return null;
        }
        return v;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (notBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String shorten(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replaceAll("\\s+", " ").trim();
        return v.length() <= 260 ? v : v.substring(0, 257) + "...";
    }

    private static class DurationPoint {
        LogEntry log;
        long durationMs;
        String process;
        String task;
        String action;
        String filter;
        String object;

        DurationPoint(LogEntry log, long durationMs, String process, String task, String action, String filter, String object) {
            this.log = log;
            this.durationMs = durationMs;
            this.process = process;
            this.task = task;
            this.action = action;
            this.filter = filter;
            this.object = object;
        }
    }

    private static class MemoryPoint {
        LogEntry log;
        int memoryMo;
        String process;
        String task;
        String action;
        String filter;
        String object;

        MemoryPoint(LogEntry log, int memoryMo, String process, String task, String action, String filter, String object) {
            this.log = log;
            this.memoryMo = memoryMo;
            this.process = process;
            this.task = task;
            this.action = action;
            this.filter = filter;
            this.object = object;
        }
    }

    private static class NodeAcc {
        String id;
        String type;
        String label;
        String severity;
        String description;
        String parentId;
        int level;
        String familyId;
        String familyGroup;
        long count = 0;
        List<String> examples = new ArrayList<>();
        List<GraphOccurrenceDto> occurrences = new ArrayList<>();

        NodeAcc(String id, String type, String label, String severity, String description,
                String parentId, int level, String familyId, String familyGroup) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.severity = severity;
            this.description = description;
            this.parentId = parentId;
            this.level = level;
            this.familyId = familyId;
            this.familyGroup = familyGroup;
        }

        GraphNodeDto toDto() {
            GraphNodeDto dto = new GraphNodeDto();
            dto.setId(id);
            dto.setType(type);
            dto.setLabel(label);
            dto.setSeverity(severity);
            dto.setDescription(description);
            dto.setParentId(parentId);
            dto.setLevel(level);
            dto.setFamilyId(familyId);
            dto.setFamilyGroup(familyGroup);
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
