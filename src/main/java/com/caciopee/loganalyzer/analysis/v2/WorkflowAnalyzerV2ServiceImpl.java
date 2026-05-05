package com.caciopee.loganalyzer.analysis.v2;

import com.caciopee.loganalyzer.analysis.v2.dto.WorkflowV2LineDto;
import com.caciopee.loganalyzer.analysis.v2.dto.WorkflowV2ResponseDto;
import com.caciopee.loganalyzer.analysis.v2.dto.WorkflowV2SummaryDto;
import com.caciopee.loganalyzer.analysis.v2.model.WorkflowEventV2;
import com.caciopee.loganalyzer.analysis.v2.model.WorkflowGroupV2;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WorkflowAnalyzerV2ServiceImpl implements WorkflowAnalyzerV2Service {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogEntryRepository logEntryRepository;
    private final WorkflowEventDetectorService workflowEventDetectorService;
    private final WorkflowCorrelationService workflowCorrelationService;

    public WorkflowAnalyzerV2ServiceImpl(LogEntryRepository logEntryRepository,
                                         WorkflowEventDetectorService workflowEventDetectorService,
                                         WorkflowCorrelationService workflowCorrelationService) {
        this.logEntryRepository = logEntryRepository;
        this.workflowEventDetectorService = workflowEventDetectorService;
        this.workflowCorrelationService = workflowCorrelationService;
    }

    @Override
    public WorkflowV2ResponseDto analyzeImportV2(Long importId) {
        List<LogEntry> logs = logEntryRepository.findByLogImportIdOrderByLogTimestampAscIdAsc(importId);

        List<WorkflowEventV2> events = logs.stream()
                .map(workflowEventDetectorService::toWorkflowEvent)
                .toList();

        List<WorkflowGroupV2> groups = workflowCorrelationService.groupEvents(events);

        List<WorkflowV2SummaryDto> summaries = groups.stream()
                .map(this::analyzeGroup)
                .sorted(Comparator.comparing(
                        s -> firstTimelineTimestamp(s.getTimeline()),
                        Comparator.nullsLast(String::compareTo)
                ))
                .toList();

        WorkflowV2ResponseDto response = new WorkflowV2ResponseDto();
        response.setImportId(importId);
        response.setTotalWorkflows(summaries.size());
        response.setWorkflowsWithRuleProblems((int) summaries.stream().filter(s -> Boolean.TRUE.equals(s.getHasRuleProblem())).count());
        response.setWorkflowsWithZeroResults((int) summaries.stream().filter(s -> Boolean.TRUE.equals(s.getHasZeroResult())).count());
        response.setWorkflowsWithPerformanceProblems((int) summaries.stream().filter(s -> Boolean.TRUE.equals(s.getHasPerformanceProblem())).count());
        response.setWorkflows(summaries);

        return response;
    }

    private WorkflowV2SummaryDto analyzeGroup(WorkflowGroupV2 group) {
        WorkflowV2SummaryDto dto = new WorkflowV2SummaryDto();

        dto.setWorkflowKey(group.getGroupKey());
        dto.setGroupingStrategy(resolveGroupingStrategy(group.getGroupKey()));
        dto.setUuid(group.getUuid());
        dto.setTransactionId(group.getTransactionId());
        dto.setFilterCode(group.getFilterCode());
        dto.setProcessName(group.getProcessName());
        dto.setClassName(group.getClassName());

        List<WorkflowEventV2> events = group.getEvents();

        dto.setTitle(buildTitle(group));
        dto.setTotalEvents(events.size());
        dto.setSqlCount((int) events.stream().filter(this::isSqlEvent).count());
        dto.setWarningCount((int) events.stream().filter(this::isWarningEvent).count());
        dto.setErrorCount((int) events.stream().filter(this::isErrorEvent).count());
        dto.setRuleWarningCount((int) events.stream().filter(this::isRuleProblemEvent).count());
        dto.setResultRowCount(findLastKnownRowCount(events));

        dto.setMaxMemoryMo(events.stream()
                .map(WorkflowEventV2::getMemoryMo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null));

        dto.setTotalDurationMs(computeDuration(events));

        dto.setHasZeroResult(hasZeroResult(events, dto));
        dto.setHasRuleProblem(dto.getRuleWarningCount() != null && dto.getRuleWarningCount() > 0);
        dto.setHasPerformanceProblem(hasPerformanceProblem(events, dto));

        dto.setDetectedInputs(extractDetectedInputs(events));
        dto.setUserIntentSummary(buildUserIntentSummary(group, dto, events));
        dto.setWhatHappenedSummary(buildWhatHappenedSummary(events, dto));
        dto.setFinalOutcome(buildFinalOutcome(dto));
        dto.setProbableCause(buildProbableCause(dto, events));
        dto.setRecommendation(buildRecommendation(dto, events));
        dto.setTimeline(buildDetailedTimeline(events));
        dto.setLines(buildLineDtos(events));

        return dto;
    }

    private String resolveGroupingStrategy(String groupKey) {
        if (groupKey == null) return "UNKNOWN";
        if (groupKey.startsWith("SESSION::")) return "SESSION_ID";
        if (groupKey.startsWith("UUID::")) return "UUID";
        if (groupKey.startsWith("TX::")) return "TRANSACTION_ID";
        return "FALLBACK_CONTEXT";
    }

    private String buildTitle(WorkflowGroupV2 group) {
        String process = safe(group.getProcessName(), "Workflow");
        String filter = safe(group.getFilterCode(), "Sans filtre identifié");
        String clazz = safe(group.getClassName(), "");

        if (!clazz.isBlank()) {
            return process + " | " + filter + " | " + clazz;
        }

        return process + " | " + filter;
    }

    private boolean isSqlEvent(WorkflowEventV2 event) {
        return event.getWorkflowEventType() == WorkflowEventType.SQL_QUERY
                || event.getWorkflowEventType() == WorkflowEventType.SQL_EXECUTION_END
                || event.getWorkflowEventType() == WorkflowEventType.SQL_RESULT
                || event.getWorkflowEventType() == WorkflowEventType.ZERO_RESULT
                || event.getWorkflowEventType() == WorkflowEventType.REQUEST_START
                || event.getWorkflowEventType() == WorkflowEventType.REQUEST_END;
    }

    private boolean isWarningEvent(WorkflowEventV2 event) {
        return event.getWorkflowEventType() == WorkflowEventType.WARNING
                || event.getWorkflowEventType() == WorkflowEventType.RULE_WARNING
                || event.getWorkflowEventType() == WorkflowEventType.RULE_NOT_FOUND
                || event.getWorkflowEventType() == WorkflowEventType.ZERO_RESULT
                || event.getWorkflowEventType() == WorkflowEventType.HIGH_MEMORY_USAGE
                || event.getWorkflowEventType() == WorkflowEventType.SLOW_STEP;
    }

    private boolean isErrorEvent(WorkflowEventV2 event) {
        return event.getWorkflowEventType() == WorkflowEventType.ERROR;
    }

    private boolean isRuleProblemEvent(WorkflowEventV2 event) {
        return event.getWorkflowEventType() == WorkflowEventType.RULE_WARNING
                || event.getWorkflowEventType() == WorkflowEventType.RULE_NOT_FOUND;
    }

    private boolean hasZeroResult(List<WorkflowEventV2> events, WorkflowV2SummaryDto dto) {
        if (dto.getResultRowCount() != null && dto.getResultRowCount() == 0) {
            return true;
        }

        return events.stream().anyMatch(e ->
                e.getWorkflowEventType() == WorkflowEventType.ZERO_RESULT
                        || (e.getRowCount() != null && e.getRowCount() == 0)
                        || containsIgnoreCase(e.getMessage(), "0 row fetched")
                        || containsIgnoreCase(e.getMessage(), "[0] row fetched")
                        || containsIgnoreCase(e.getMessage(), "|0 row")
        );
    }

    private boolean hasPerformanceProblem(List<WorkflowEventV2> events, WorkflowV2SummaryDto dto) {
        if (dto.getTotalDurationMs() != null && dto.getTotalDurationMs() >= 2000) {
            return true;
        }

        if (dto.getMaxMemoryMo() != null && dto.getMaxMemoryMo() >= 3500) {
            return true;
        }

        return events.stream().anyMatch(e ->
                e.getWorkflowEventType() == WorkflowEventType.SLOW_STEP
                        || e.getWorkflowEventType() == WorkflowEventType.HIGH_MEMORY_USAGE
                        || (e.getDurationMs() != null && e.getDurationMs() >= 2000)
                        || (e.getMemoryMo() != null && e.getMemoryMo() >= 3500)
        );
    }

    private Integer findLastKnownRowCount(List<WorkflowEventV2> events) {
        Integer last = null;

        for (WorkflowEventV2 event : events) {
            if (event.getRowCount() != null) {
                last = event.getRowCount();
            }
        }

        return last;
    }

    private Long computeDuration(List<WorkflowEventV2> events) {
        if (events.isEmpty()) return null;

        var start = events.get(0).getTimestamp();
        var end = events.get(events.size() - 1).getTimestamp();

        if (start == null || end == null) return null;

        return Duration.between(start, end).toMillis();
    }

    private List<String> extractDetectedInputs(List<WorkflowEventV2> events) {
        LinkedHashSet<String> inputs = new LinkedHashSet<>();

        Pattern p = Pattern.compile("([A-Za-z0-9_]+)=\\[([^\\]]+)]");

        for (WorkflowEventV2 event : events) {
            String msg = event.getMessage();

            addInput(inputs, "user", event.getUserName());
            addInput(inputs, "clientIp", event.getExtractedClientIp());
            addInput(inputs, "taskName", event.getExtractedTaskName());
            addInput(inputs, "actionName", event.getExtractedActionName());
            addInput(inputs, "checkpoint", event.getExtractedCheckpoint());
            addInput(inputs, event.getExtractedBusinessObjectKey(), event.getExtractedBusinessObjectValue());
            addInput(inputs, event.getExtractedQueryParameterName(), event.getExtractedQueryParameterValue());

            if (msg == null) continue;

            Matcher m = p.matcher(msg);
            while (m.find()) {
                String key = m.group(1);
                String value = m.group(2);

                String keyLower = key.toLowerCase(Locale.ROOT);

                if (!keyLower.contains("uuid")
                        && !keyLower.contains("transaction")
                        && !keyLower.contains("thread")
                        && !keyLower.startsWith("fv")) {
                    inputs.add(key + " = " + value);
                }
            }
        }

        return new ArrayList<>(inputs).stream().limit(20).toList();
    }

    private void addInput(Set<String> inputs, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) return;
        inputs.add(key + " = " + value);
    }

    private String buildUserIntentSummary(WorkflowGroupV2 group, WorkflowV2SummaryDto dto, List<WorkflowEventV2> events) {
        String filter = safe(group.getFilterCode(), "");
        String clazz = safe(group.getClassName(), "");
        String process = safe(group.getProcessName(), "");

        Optional<WorkflowEventV2> richEvent = events.stream()
                .filter(e -> notBlank(e.getExtractedTaskName()) || notBlank(e.getExtractedActionName()))
                .findFirst();

        if (richEvent.isPresent()) {
            WorkflowEventV2 e = richEvent.get();
            String task = safe(e.getExtractedTaskName(), filter);
            String action = safe(e.getExtractedActionName(), "");
            String user = safe(e.getUserName(), "utilisateur non identifié");
            String ip = safe(e.getExtractedClientIp(), "");

            StringBuilder sb = new StringBuilder();
            sb.append("Le workflow semble avoir été déclenché par ").append(user);

            if (!ip.isBlank()) {
                sb.append(" depuis l’adresse ").append(ip);
            }

            if (!task.isBlank()) {
                sb.append(" sur la tâche ou le traitement '").append(task).append("'");
            }

            if (!action.isBlank()) {
                sb.append(" avec l’action '").append(action).append("'");
            }

            sb.append(".");

            return sb.toString();
        }

        if (events.stream().anyMatch(e -> e.getWorkflowEventType() == WorkflowEventType.CALL_SAVE
                || e.getWorkflowEventType() == WorkflowEventType.SAVE_OR_UPDATE_START
                || e.getWorkflowEventType() == WorkflowEventType.SAVE_START)) {
            if (!filter.isBlank()) {
                return "Ce workflow correspond probablement à une sauvegarde métier déclenchée sur le traitement '" + filter + "'.";
            }
            return "Ce workflow correspond probablement à une sauvegarde métier.";
        }

        if (events.stream().anyMatch(e -> e.getWorkflowEventType() == WorkflowEventType.CALL_EXECUTE_FILTER
                || e.getWorkflowEventType() == WorkflowEventType.FILTER_START)) {
            if (!filter.isBlank()) {
                return "Ce workflow correspond à l’exécution du filtre métier '" + filter + "'.";
            }
            return "Ce workflow correspond à l’exécution d’un filtre métier.";
        }

        if (events.stream().anyMatch(e -> e.getWorkflowEventType() == WorkflowEventType.CALL_RUN_RULES
                || e.getWorkflowEventType() == WorkflowEventType.AUTOMATIC_PROCESSING)) {
            if (!filter.isBlank()) {
                return "Ce workflow correspond à un lancement de règles métier sur le traitement '" + filter + "'.";
            }
            return "Ce workflow correspond à un lancement de règles métier.";
        }

        if (!filter.isBlank() && !clazz.isBlank()) {
            return "Ce workflow semble lancer un traitement sur le filtre '" + filter + "' pour manipuler ou rechercher des objets de type '" + clazz + "'.";
        }

        if (!filter.isBlank()) {
            return "Ce workflow semble lancer un traitement métier lié au filtre '" + filter + "'.";
        }

        if (!clazz.isBlank()) {
            return "Ce workflow semble manipuler des objets de type '" + clazz + "'.";
        }

        if (!process.isBlank()) {
            return "Ce workflow correspond à une suite d’actions exécutées par le processus '" + process + "'.";
        }

        return "Ce workflow correspond à une suite d’actions techniques et métier exécutées par l’application.";
    }

    private String buildWhatHappenedSummary(List<WorkflowEventV2> events, WorkflowV2SummaryDto dto) {
        List<String> parts = new ArrayList<>();

        if (hasEvent(events, WorkflowEventType.CALL_RUN_RULES)) {
            parts.add("un appel distant a demandé l’exécution des règles métier");
        }

        if (hasEvent(events, WorkflowEventType.CALL_EXECUTE_FILTER)) {
            parts.add("un appel distant a demandé l’exécution d’un filtre");
        }

        if (hasEvent(events, WorkflowEventType.CALL_SAVE)) {
            parts.add("un appel distant a demandé une sauvegarde");
        }

        if (hasEvent(events, WorkflowEventType.AUTOMATIC_PROCESSING)) {
            parts.add("un traitement automatique a été lancé");
        }

        if (hasAnyEvent(events, WorkflowEventType.TRIGGER_FIRED, WorkflowEventType.TRIGGER_COMPLETED, WorkflowEventType.CACHE_CLEANER)) {
            parts.add("un trigger système ou une tâche automatique a été exécuté");
        }

        if (hasAnyEvent(events, WorkflowEventType.BEFORE_LOAD_START, WorkflowEventType.BEFORE_LOAD_END)) {
            parts.add("une phase BEFORE_LOAD a été exécutée");
        }

        if (hasAnyEvent(events, WorkflowEventType.RULE_START, WorkflowEventType.RULE_END,
                WorkflowEventType.RULE_WARNING, WorkflowEventType.RULE_NOT_FOUND, WorkflowEventType.RULE_DETAIL)) {
            parts.add("une phase de règles métier a été traitée");
        }

        if (hasAnyEvent(events, WorkflowEventType.SEARCH_BY_ROOT_START, WorkflowEventType.SEARCH_BY_ROOT_END,
                WorkflowEventType.SEARCH_START, WorkflowEventType.SEARCH_END,
                WorkflowEventType.REQUEST_START, WorkflowEventType.REQUEST_END)) {
            parts.add("une recherche métier a été exécutée");
        }

        if (events.stream().anyMatch(this::isSqlEvent)) {
            parts.add("une ou plusieurs requêtes SQL ont été exécutées");
        }

        if (hasAnyEvent(events, WorkflowEventType.TEMP_QUERY, WorkflowEventType.TEMP_SAVE)) {
            parts.add("des données temporaires ont été utilisées pour le chargement");
        }

        if (hasAnyEvent(events, WorkflowEventType.LOAD_START, WorkflowEventType.LOAD_QUERY,
                WorkflowEventType.LOAD_FILLING, WorkflowEventType.LOAD_END)) {
            parts.add("les données ont été chargées puis remplies");
        }

        if (hasAnyEvent(events, WorkflowEventType.AGGREGATION_START,
                WorkflowEventType.AGGREGATION_END, WorkflowEventType.RENDERING_RESULT)) {
            parts.add("les données ont été agrégées puis préparées pour l’affichage");
        }

        if (hasAnyEvent(events, WorkflowEventType.SAVE_PROCESS_CONTENT, WorkflowEventType.SAVE_START,
                WorkflowEventType.SAVE_END, WorkflowEventType.SAVE_OR_UPDATE_START,
                WorkflowEventType.SAVE_OR_UPDATE_END, WorkflowEventType.SAVE_INSTANCE_OPERATION,
                WorkflowEventType.SAVE_RELATION_OPERATION, WorkflowEventType.SAVE_TOTAL_TIME,
                WorkflowEventType.DOCUMENT_SAVE)) {
            parts.add("une phase de sauvegarde a été détectée");
        }

        if (hasEvent(events, WorkflowEventType.CHECKPOINT)) {
            parts.add("des checkpoints métier ont été détectés");
        }
        if (hasAnyEvent(events,
                WorkflowEventType.STRUCTURING_LOOP_START,
                WorkflowEventType.NEW_INTERFACE_OBJECT,
                WorkflowEventType.STRUCTURE_DATA_START,
                WorkflowEventType.STRUCTURING_FIELD_START,
                WorkflowEventType.FINAL_OBJECT_FIELD_PUT)) {
            parts.add("le système a structuré des objets métier à partir des données d’interface");
        }

        if (hasAnyEvent(events,
                WorkflowEventType.RELATION_PRESENT,
                WorkflowEventType.RELATION_FIRST_ADDED,
                WorkflowEventType.RELATION_KEY_CREATED,
                WorkflowEventType.RELATION_DATA_ATTACHED)) {
            parts.add("des relations métier ont été construites entre les objets");
        }

        if (hasAnyEvent(events,
                WorkflowEventType.MANDATORY_CHECK_START,
                WorkflowEventType.MANDATORY_FIELDS_LIST,
                WorkflowEventType.MANDATORY_FIELD_CHECK,
                WorkflowEventType.MANDATORY_CHECK_SUCCESS)) {
            parts.add("les champs obligatoires ont été vérifiés");
        }

        if (hasAnyEvent(events,
                WorkflowEventType.PM_PP_NULL_ERROR,
                WorkflowEventType.ERROR_DETAILS_EXTRACTED,
                WorkflowEventType.BUSINESS_KEY_NULL)) {
            parts.add("une anomalie métier liée au mapping ou à la clé métier a été détectée");
        }
        StringBuilder sb = new StringBuilder();

        if (parts.isEmpty()) {
            sb.append("Le workflow contient des traces exploitables, mais peu d’étapes explicitement reconnues.");
        } else {
            sb.append(String.join(", puis ", parts)).append(".");
        }

        if (Boolean.TRUE.equals(dto.getHasRuleProblem())) {
            sb.append(" Une anomalie de règles métier est présente.");
        }

        if (Boolean.TRUE.equals(dto.getHasZeroResult())) {
            sb.append(" Une recherche associée n’a retourné aucun résultat.");
        }

        if (Boolean.TRUE.equals(dto.getHasPerformanceProblem())) {
            sb.append(" Un signal de lenteur ou de consommation élevée a été détecté.");
        }

        if (dto.getErrorCount() != null && dto.getErrorCount() > 0) {
            sb.append(" Une ou plusieurs erreurs sont présentes dans les lignes du workflow.");
        }

        return sb.toString();
    }

    private String buildFinalOutcome(WorkflowV2SummaryDto dto) {
        if (dto.getErrorCount() != null && dto.getErrorCount() > 0) {
            return "Le workflow s’est exécuté avec une ou plusieurs erreurs détectées.";
        }

        if (Boolean.TRUE.equals(dto.getHasRuleProblem()) && Boolean.TRUE.equals(dto.getHasZeroResult())) {
            return "Le workflow s’est exécuté, mais le résultat est incomplet : aucune règle métier applicable et aucun résultat de recherche.";
        }

        if (Boolean.TRUE.equals(dto.getHasRuleProblem())) {
            return "Le workflow s’est exécuté, mais une partie de la logique métier semble absente ou non configurée.";
        }

        if (Boolean.TRUE.equals(dto.getHasZeroResult())) {
            return "Le workflow a lancé une recherche, mais aucun objet métier correspondant n’a été trouvé.";
        }

        if (Boolean.TRUE.equals(dto.getHasPerformanceProblem())) {
            return "Le workflow semble fonctionnel, mais certaines étapes sont lentes ou coûteuses.";
        }

        return "Le workflow semble s’être déroulé normalement avec une séquence cohérente d’actions.";
    }

    private String buildProbableCause(WorkflowV2SummaryDto dto, List<WorkflowEventV2> events) {
        if (dto.getErrorCount() != null && dto.getErrorCount() > 0) {
            return "Cause probable : une erreur technique ou métier est présente dans les lignes du workflow. Il faut inspecter la première erreur chronologique.";
        }

        if (Boolean.TRUE.equals(dto.getHasRuleProblem())) {
            Optional<WorkflowEventV2> ruleIssue = events.stream()
                    .filter(this::isRuleProblemEvent)
                    .findFirst();

            if (ruleIssue.isPresent()) {
                WorkflowEventV2 e = ruleIssue.get();
                String task = safe(e.getExtractedTaskName(), safe(e.getFilterCode(), "tâche inconnue"));
                String action = safe(e.getExtractedActionName(), "action inconnue");
                return "Cause probable : aucune règle métier n’est configurée pour la tâche '" + task + "' avec l’action '" + action + "'.";
            }

            return "Cause probable : aucune règle métier n’est configurée pour cette transition, ce traitement ou cette action.";
        }

        if (Boolean.TRUE.equals(dto.getHasZeroResult())) {
            Optional<WorkflowEventV2> zero = events.stream()
                    .filter(e -> e.getWorkflowEventType() == WorkflowEventType.ZERO_RESULT)
                    .findFirst();

            if (zero.isPresent()) {
                WorkflowEventV2 e = zero.get();
                String filter = safe(e.getFilterCode(), "filtre inconnu");
                String param = "";
                if (notBlank(e.getExtractedQueryParameterName()) && notBlank(e.getExtractedQueryParameterValue())) {
                    param = " Paramètre détecté : " + e.getExtractedQueryParameterName() + " = " + e.getExtractedQueryParameterValue() + ".";
                }
                return "Cause probable : le filtre '" + filter + "' n’a trouvé aucune donnée correspondant aux critères de recherche." + param;
            }

            return "Cause probable : les critères de recherche ne correspondent à aucune donnée existante, ou les données attendues ne sont pas encore présentes.";
        }

        if (events.stream().anyMatch(e -> e.getWorkflowEventType() == WorkflowEventType.HIGH_MEMORY_USAGE)) {
            return "Cause probable : consommation mémoire très élevée pendant le chargement ou le traitement des données.";
        }

        if (events.stream().anyMatch(e -> e.getWorkflowEventType() == WorkflowEventType.SLOW_STEP)) {
            return "Cause probable : une étape lente a été détectée, probablement liée à une requête SQL, un chargement volumineux ou une phase de sauvegarde.";
        }

        if (Boolean.TRUE.equals(dto.getHasPerformanceProblem())) {
            return "Cause probable : requête coûteuse, chargement large, volume important ou étape de sauvegarde plus lente que prévu.";
        }

        return "Cause probable : aucun problème majeur évident à partir des logs de ce workflow.";
    }

    private String buildRecommendation(WorkflowV2SummaryDto dto, List<WorkflowEventV2> events) {
        if (dto.getErrorCount() != null && dto.getErrorCount() > 0) {
            return "Commencer par la première ligne en erreur du workflow, puis remonter aux lignes précédentes pour identifier l’action déclencheuse.";
        }

        if (Boolean.TRUE.equals(dto.getHasRuleProblem())) {
            return "Vérifier la configuration des règles métier pour le process, la tâche et l’action concernés.";
        }

        if (Boolean.TRUE.equals(dto.getHasZeroResult())) {
            return "Vérifier les paramètres d’entrée détectés, les critères SQL et la présence réelle des données attendues en base.";
        }

        if (Boolean.TRUE.equals(dto.getHasPerformanceProblem())) {
            return "Vérifier les requêtes SQL lentes, le nombre de lignes retournées, les étapes de chargement, la consommation mémoire et les sauvegardes.";
        }

        return "Aucune action urgente. Ce workflow peut servir de comportement de référence.";
    }

    private List<String> buildDetailedTimeline(List<WorkflowEventV2> events) {
        List<String> timeline = new ArrayList<>();

        for (WorkflowEventV2 event : events) {
            String ts = event.getTimestamp() != null ? event.getTimestamp().format(TS_FORMAT) : "timestamp inconnu";
            String sentence = humanSentence(event);

            if (sentence != null && !sentence.isBlank()) {
                timeline.add(ts + " → " + sentence);
            }
        }

        if (timeline.isEmpty()) {
            timeline.add("Aucune étape compréhensible n’a été reconstruite pour ce workflow.");
        }

        return timeline;
    }

    private String humanSentence(WorkflowEventV2 event) {
        if (event == null || event.getWorkflowEventType() == null) {
            return "Événement non identifié.";
        }

        return switch (event.getWorkflowEventType()) {
            case WORKFLOW_START -> "début du workflow.";
            case WORKFLOW_END -> "fin du workflow.";

            case STRUCTURING_LOOP_START -> "début du traitement des objets d’interface.";
            case NEW_INTERFACE_OBJECT -> "nouvel objet d’interface détecté.";
            case STRUCTURE_DATA_START -> "début de structuration d’un objet métier.";
            case STRUCTURE_DATA_END -> "fin de structuration d’un objet métier.";
            case STRUCTURING_FIELD_START -> "début du mapping d’un champ d’interface vers un champ métier.";
            case FIELD_TRIM_REQUIRED -> "nettoyage de la valeur du champ avant conversion.";
            case FIELD_CLASS_CODE_DETECTED -> "code interne du champ métier identifié.";
            case FIELD_VALUE_READ -> "lecture d’une valeur depuis l’interface.";
            case TYPE_CONVERSION_START -> "conversion de la valeur vers le type attendu.";
            case TYPE_CONVERSION_SUCCESS -> "conversion de type réussie.";
            case TYPE_CONVERSION_NULL -> "conversion impossible ou valeur convertie en null.";
            case FINAL_OBJECT_FIELD_PUT -> "champ injecté dans l’objet métier final.";

            case RELATION_PRESENT -> "relation métier à construire détectée.";
            case RELATION_ABSENT -> "aucune relation métier à construire pour ce champ.";
            case RELATION_FIRST_ADDED -> "première relation métier ajoutée.";
            case RELATION_KEY_CREATED -> "clé de relation métier créée.";
            case RELATION_KEY_ATTACHED -> "clé de relation rattachée à l’objet.";
            case RELATION_DATA_ATTACHED -> "données de relation ajoutées à l’objet métier.";
            case RELATION_DELETE -> "des relations métier ont été supprimées ou nettoyées.";
            case RELATION_SAVE -> "des relations métier ont été enregistrées.";

            case MANDATORY_CHECK_START -> "début de vérification des champs obligatoires.";
            case MANDATORY_FIELDS_LIST -> "liste des champs obligatoires détectée.";
            case MANDATORY_FIELD_CHECK -> "vérification d’un champ obligatoire.";
            case MANDATORY_CHECK_SUCCESS -> "les champs obligatoires sont respectés.";
            case BUSINESS_KEY_NULL -> "clé métier manquante ou nulle.";

            case PM_PP_NULL_ERROR -> "erreur métier : une donnée PP/PM nécessaire au mapping est absente.";
            case ERROR_DETAILS_EXTRACTED -> "détails de l’erreur métier extraits : colonne, attribut, valeur et clé métier impactée.";

            case CALL_RUN_RULES -> "un appel distant a demandé l’exécution des règles métier.";
            case CALL_EXECUTE_FILTER -> "un appel distant a demandé l’exécution d’un filtre métier.";
            case CALL_SAVE -> "un appel distant a demandé une sauvegarde métier.";
            case AUTOMATIC_PROCESSING -> "un traitement automatique a lancé les règles métier.";

            case SERVICE_START -> "début du service.";
            case SERVICE_END -> "fin du service.";
            case FILTER_START -> "début du filtre métier " + safe(event.getFilterCode(), "inconnu") + ".";
            case FILTER_END -> "fin du filtre métier " + safe(event.getFilterCode(), "inconnu") + ".";

            case BEFORE_LOAD_START -> "début de la phase BEFORE_LOAD.";
            case BEFORE_LOAD_END -> "fin de la phase BEFORE_LOAD.";

            case RULE_START -> "début de l’exécution des règles métier.";
            case RULE_END -> "fin de l’exécution des règles métier.";
            case RULE_WARNING, RULE_NOT_FOUND -> "aucune règle métier applicable n’a été trouvée pour cette transition.";
            case RULE_DETAIL -> "détail d’exécution d’une règle métier.";

            case REQUEST_START -> "début d’exécution d’une requête de recherche.";
            case REQUEST_END -> "fin d’exécution d’une requête de recherche.";
            case SEARCH_START, SEARCH_BY_ROOT_START -> "début d’une recherche métier par racine.";
            case SEARCH_END, SEARCH_BY_ROOT_END -> "fin d’une recherche métier par racine.";

            case SQL_QUERY -> "une requête SQL a été exécutée.";
            case SQL_EXECUTION_END -> "la requête SQL s’est terminée.";
            case SQL_RESULT -> "la recherche a retourné " + safeInt(event.getRowCount()) + " ligne(s).";
            case ZERO_RESULT -> "la recherche n’a retourné aucun résultat.";

            case TEMP_QUERY -> "une requête temporaire a été utilisée.";
            case TEMP_SAVE -> "des données temporaires ont été sauvegardées.";

            case LOAD_START -> "début du chargement des données.";
            case LOAD_QUERY -> "chargement des attributs via requête.";
            case LOAD_FILLING -> "remplissage des données récupérées.";
            case LOAD_END -> "fin du chargement des données.";

            case AGGREGATION_START -> "début de l’agrégation des données métier.";
            case AGGREGATION_END -> "fin de l’agrégation des données métier.";
            case RENDERING_RESULT -> "le système a préparé le résultat à afficher à l’utilisateur.";

            case MULTITHREADING_ENABLED -> "le traitement utilise le multithreading.";
            case MULTITHREADING_SKIPPED -> "le multithreading a été ignoré, probablement car le volume est faible.";
            case THREAD_COMPLETED -> "un thread de traitement s’est terminé.";
            case SESSION_CLOSED -> "une session technique a été fermée.";

            case MEMORY_USAGE -> "la mémoire observée est de " + safeInt(event.getMemoryMo()) + " Mo.";
            case HIGH_MEMORY_USAGE -> "consommation mémoire élevée détectée : " + safeInt(event.getMemoryMo()) + " Mo.";
            case PERFORMANCE -> "une durée mesurée de " + safeLong(event.getDurationMs()) + " ms a été observée.";
            case SLOW_STEP -> "une étape lente a été détectée : " + safeLong(event.getDurationMs()) + " ms.";

            case SAVE_PROCESS_CONTENT -> "le contenu du process a été sauvegardé.";
            case SAVE_START -> "début de la sauvegarde.";
            case SAVE_END -> "fin de la sauvegarde.";
            case SAVE_OR_UPDATE_START -> "début d’une opération SaveOrUpdate.";
            case SAVE_OR_UPDATE_END -> "fin d’une opération SaveOrUpdate.";
            case SAVE_INSTANCE_OPERATION -> "l’instance métier principale a été sauvegardée.";
            case SAVE_RELATION_OPERATION -> "des relations métier ont été sauvegardées.";
            case SAVE_TOTAL_TIME -> "le temps total de sauvegarde a été calculé.";
            case DOCUMENT_SAVE -> "des documents ont été sauvegardés.";

            case CHECKPOINT -> checkpointSentence(event);
            case TRIGGER_FIRED -> "un déclencheur automatique système a été lancé.";
            case TRIGGER_COMPLETED -> "un déclencheur automatique système s’est terminé.";
            case CACHE_CLEANER -> "un nettoyage automatique du cache a été exécuté.";
            case JBPM_ASSIGNMENT -> "une affectation JBPM a été détectée.";

            case ERROR -> "une erreur a été détectée.";
            case WARNING -> "un avertissement a été détecté.";
            case BUSINESS_INFO -> shortenMessage(event.getMessage());
            case TECHNICAL_INFO -> null;
            case UNKNOWN -> shortenMessage(event.getMessage());
            default -> shortenMessage(event.getMessage());
        };

    }

    private String checkpointSentence(WorkflowEventV2 event) {
        if (notBlank(event.getExtractedCheckpoint())) {
            return "checkpoint métier détecté : " + event.getExtractedCheckpoint() + ".";
        }
        return "un checkpoint métier a été atteint.";
    }

    private List<WorkflowV2LineDto> buildLineDtos(List<WorkflowEventV2> events) {
        List<WorkflowV2LineDto> lines = new ArrayList<>();

        for (WorkflowEventV2 event : events) {
            WorkflowV2LineDto line = new WorkflowV2LineDto();
            line.setLogEntryId(event.getLogEntryId());
            line.setTimestamp(event.getTimestamp() != null ? event.getTimestamp().format(TS_FORMAT) : null);
            line.setLevel(event.getLevel());
            line.setEventType(event.getWorkflowEventType() != null ? event.getWorkflowEventType().name() : null);
            line.setProcessName(event.getProcessName());
            line.setSourceClass(event.getSourceClass());
            line.setBusinessMeaning(humanSentence(event));
            line.setMessage(event.getMessage());
            lines.add(line);
        }

        return lines;
    }

    private boolean hasEvent(List<WorkflowEventV2> events, WorkflowEventType type) {
        return events.stream().anyMatch(e -> e.getWorkflowEventType() == type);
    }

    private boolean hasAnyEvent(List<WorkflowEventV2> events, WorkflowEventType... types) {
        Set<WorkflowEventType> set = EnumSet.noneOf(WorkflowEventType.class);
        Collections.addAll(set, types);

        return events.stream().anyMatch(e -> set.contains(e.getWorkflowEventType()));
    }

    private boolean containsIgnoreCase(String value, String expected) {
        if (value == null || expected == null) return false;
        return value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private String shortenMessage(String message) {
        if (message == null || message.isBlank()) return null;

        String cleaned = message.replaceAll("\\s+", " ").trim();

        if (cleaned.length() <= 180) return cleaned;

        return cleaned.substring(0, 177) + "...";
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String firstTimelineTimestamp(List<String> timeline) {
        if (timeline == null || timeline.isEmpty()) {
            return null;
        }

        String first = timeline.get(0);

        if (first == null || first.length() < 19) {
            return null;
        }

        return first.substring(0, 19);
    }
}