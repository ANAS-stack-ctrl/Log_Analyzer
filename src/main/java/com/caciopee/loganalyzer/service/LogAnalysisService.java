package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.*;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LogAnalysisService {

    private final LogEntryRepository logEntryRepository;
    private final LogImportService logImportService;

    public LogAnalysisService(LogEntryRepository logEntryRepository,
                              LogImportService logImportService) {
        this.logEntryRepository = logEntryRepository;
        this.logImportService = logImportService;
    }

    public DashboardResponse buildDashboard() {
        DashboardResponse response = new DashboardResponse();

        List<LogEntry> allLogs = logEntryRepository.findAll();
        List<LogImport> allImports = logImportService.getAllImports();
        List<LogEntry> allErrors = allLogs.stream().filter(this::isErrorLog).toList();

        long totalImports = allImports.size();
        long totalLogs = allLogs.size();
        long totalErrors = allErrors.size();
        double errorRate = totalLogs == 0 ? 0 : ((double) totalErrors / totalLogs) * 100.0;

        long totalBusinessKeys = allLogs.stream()
                .flatMap(log -> Arrays.stream(new String[]{log.getBusinessKey(), log.getErrorBusinessKey()}))
                .filter(this::hasText)
                .distinct()
                .count();

        Map<String, Long> eventTypeDistribution = countValues(
                allLogs.stream()
                        .map(LogEntry::getEventType)
                        .filter(this::hasText)
                        .toList()
        );

        String topErrorType = allErrors.stream()
                .map(LogEntry::getEventType)
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("Aucune erreur dominante");

        List<String> topProblemFields = topN(
                allErrors.stream()
                        .map(log -> firstNonEmpty(log.getFieldName(), log.getErrorAttribute()))
                        .filter(this::hasText)
                        .toList(),
                5
        );

        String topProblemField = topProblemFields.isEmpty()
                ? "Aucun champ dominant"
                : topProblemFields.get(0);

        List<String> topErrorMessages = topN(
                allErrors.stream()
                        .map(LogEntry::getMessage)
                        .filter(this::hasText)
                        .toList(),
                5
        );

        List<ImportDiagnosticView> importViews = allImports.stream()
                .map(logImport -> {
                    List<LogEntry> importLogs = logEntryRepository.findByLogImportId(logImport.getId());
                    long importErrorCount = importLogs.stream().filter(this::isErrorLog).count();
                    double importErrorRate = importLogs.isEmpty()
                            ? 0.0
                            : ((double) importErrorCount / importLogs.size()) * 100.0;

                    String severity = computeSeverity(
                            safe(logImport.getStatus()),
                            importLogs,
                            importLogs.stream().filter(this::isErrorLog).toList(),
                            findDominantEventType(
                                    importLogs.stream().filter(this::isErrorLog).toList(),
                                    importLogs
                            ),
                            List.of()
                    );

                    return new ImportDiagnosticView(
                            logImport.getId(),
                            safe(logImport.getOriginalFileName()),
                            safe(logImport.getStatus()),
                            importLogs.size(),
                            importErrorCount,
                            round2(importErrorRate),
                            severity
                    );
                })
                .sorted(Comparator
                        .comparing(ImportDiagnosticView::severityRank).reversed()
                        .thenComparing(ImportDiagnosticView::errorRate, Comparator.reverseOrder())
                        .thenComparing(ImportDiagnosticView::errorCount, Comparator.reverseOrder()))
                .toList();

        List<String> criticalImports = importViews.stream()
                .filter(view -> "CRITICAL".equals(view.severity()) || "HIGH".equals(view.severity()))
                .limit(5)
                .map(view -> "Import #" + view.importId()
                        + " | " + view.fileName()
                        + " | " + view.status()
                        + " | " + view.errorRate() + "% d’erreurs"
                        + " | gravité " + view.severity())
                .toList();

        long criticalImportsCount = importViews.stream()
                .filter(view -> "CRITICAL".equals(view.severity()) || "HIGH".equals(view.severity()))
                .count();

        String mostCriticalImportLabel = importViews.isEmpty()
                ? "Aucun import critique"
                : "Import #" + importViews.get(0).importId()
                  + " (" + importViews.get(0).fileName() + ") - "
                  + importViews.get(0).errorRate() + "% - "
                  + importViews.get(0).severity();

        response.setTotalImports(totalImports);
        response.setTotalLogs(totalLogs);
        response.setTotalErrors(totalErrors);
        response.setErrorRate(round2(errorRate));

        response.setTotalBusinessKeys(totalBusinessKeys);
        response.setCriticalImportsCount(criticalImportsCount);
        response.setTopErrorType(topErrorType);
        response.setTopProblemField(topProblemField);
        response.setMostCriticalImportLabel(mostCriticalImportLabel);

        response.setEventTypeDistribution(eventTypeDistribution);
        response.setTopErrorMessages(topErrorMessages);
        response.setTopProblemFields(topProblemFields);
        response.setCriticalImports(criticalImports);

        return response;
    }

    public AnalysisSummaryResponse buildGlobalSummary() {
        List<LogEntry> logs = logEntryRepository.findAll();

        AnalysisSummaryResponse response = new AnalysisSummaryResponse();
        response.setTotalLogs(logs.size());
        response.setTotalErrors(logs.stream().filter(this::isErrorLog).count());
        response.setTotalInfos(logs.stream()
                .filter(l -> "INFO".equalsIgnoreCase(safe(l.getLevel())))
                .count());

        response.setCountByLevel(countValues(
                logs.stream().map(LogEntry::getLevel).filter(Objects::nonNull).toList()
        ));

        response.setCountByEventType(countValues(
                logs.stream().map(LogEntry::getEventType).filter(Objects::nonNull).toList()
        ));

        response.setTopFields(topN(
                logs.stream().map(LogEntry::getFieldName).filter(this::hasText).toList(), 10
        ));

        response.setTopBusinessKeys(topN(
                logs.stream()
                        .flatMap(log -> Arrays.stream(new String[]{log.getBusinessKey(), log.getErrorBusinessKey()}))
                        .filter(this::hasText)
                        .toList(), 10
        ));

        response.setTopErrorMessages(topN(
                logs.stream()
                        .filter(this::isErrorLog)
                        .map(LogEntry::getMessage)
                        .filter(this::hasText)
                        .toList(), 10
        ));

        response.setGlobalInterpretation(buildGlobalInterpretation(response, logs));
        return response;
    }

    public ImportSummaryResponse buildImportSummary(Long importId) {
        LogImport logImport = logImportService.getById(importId);
        List<LogEntry> logs = logEntryRepository.findByLogImportId(importId);

        ImportSummaryResponse response = new ImportSummaryResponse();
        response.setImportId(logImport.getId());
        response.setFileName(logImport.getFileName());
        response.setOriginalFileName(logImport.getOriginalFileName());
        response.setStartedAt(logImport.getStartedAt());
        response.setFinishedAt(logImport.getFinishedAt());
        response.setStatus(logImport.getStatus());
        response.setTotalLines(logImport.getTotalLines());
        response.setProcessedLines(logImport.getProcessedLines());
        response.setFailedLines(logImport.getFailedLines());

        response.setTotalLogsStored(logs.size());
        response.setTotalErrors(logs.stream().filter(this::isErrorLog).count());
        response.setTotalInfos(logs.stream()
                .filter(l -> "INFO".equalsIgnoreCase(safe(l.getLevel())))
                .count());

        response.setCountByLevel(countValues(
                logs.stream().map(LogEntry::getLevel).filter(Objects::nonNull).toList()
        ));

        response.setCountByEventType(countValues(
                logs.stream().map(LogEntry::getEventType).filter(Objects::nonNull).toList()
        ));

        response.setTopFields(topN(
                logs.stream().map(LogEntry::getFieldName).filter(this::hasText).toList(), 10
        ));

        response.setTopBusinessKeys(topN(
                logs.stream()
                        .flatMap(log -> Arrays.stream(new String[]{log.getBusinessKey(), log.getErrorBusinessKey()}))
                        .filter(this::hasText)
                        .toList(), 10
        ));

        response.setTopErrorMessages(topN(
                logs.stream()
                        .filter(this::isErrorLog)
                        .map(LogEntry::getMessage)
                        .filter(this::hasText)
                        .toList(), 10
        ));

        response.setSummary(buildImportSummaryText(response));
        return response;
    }

    public DiagnosticResult buildImportDiagnostic(Long importId) {
        LogImport logImport = logImportService.getById(importId);
        List<LogEntry> logs = sortLogsAscending(logEntryRepository.findByLogImportId(importId));
        return buildDiagnostic(
                "IMPORT",
                String.valueOf(importId),
                safe(logImport.getStatus()),
                logs
        );
    }

    public DiagnosticResult buildBusinessKeyDiagnostic(String businessKey) {
        List<LogEntry> logs = logEntryRepository.findAll().stream()
                .filter(log ->
                        businessKey.equals(safe(log.getBusinessKey())) ||
                                businessKey.equals(safe(log.getErrorBusinessKey())) ||
                                messageContains(log.getMessage(), businessKey))
                .sorted(Comparator.comparing(LogEntry::getLogTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return buildDiagnostic(
                "BUSINESS_KEY",
                businessKey,
                "N/A",
                logs
        );
    }

    public DiagnosticResult buildBusinessKeyDiagnosticForImport(Long importId, String businessKey) {
        List<LogEntry> logs = logEntryRepository.findByLogImportId(importId).stream()
                .filter(log ->
                        businessKey.equals(safe(log.getBusinessKey())) ||
                                businessKey.equals(safe(log.getErrorBusinessKey())) ||
                                messageContains(log.getMessage(), businessKey))
                .sorted(Comparator.comparing(LogEntry::getLogTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return buildDiagnostic(
                "IMPORT_BUSINESS_KEY",
                "import=" + importId + ", businessKey=" + businessKey,
                "N/A",
                logs
        );
    }

    public List<LogEntry> getImportErrors(Long importId) {
        return sortLogs(
                logEntryRepository.findByLogImportId(importId).stream()
                        .filter(this::isErrorLog)
                        .toList()
        );
    }

    public List<String> getImportBusinessKeys(Long importId) {
        return logEntryRepository.findByLogImportId(importId).stream()
                .flatMap(log -> Arrays.stream(new String[]{log.getBusinessKey(), log.getErrorBusinessKey()}))
                .filter(this::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    public BusinessKeyAnalysisResponse analyzeBusinessKey(String businessKey) {
        List<LogEntry> allLogs = logEntryRepository.findAll();

        List<LogEntry> relatedLogs = allLogs.stream()
                .filter(log ->
                        businessKey.equals(safe(log.getBusinessKey())) ||
                                businessKey.equals(safe(log.getErrorBusinessKey())) ||
                                messageContains(log.getMessage(), businessKey))
                .sorted(Comparator.comparing(LogEntry::getLogTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return buildBusinessKeyAnalysisResponse(businessKey, relatedLogs);
    }

    public BusinessKeyAnalysisResponse analyzeBusinessKeyForImport(Long importId, String businessKey) {
        List<LogEntry> relatedLogs = logEntryRepository.findByLogImportId(importId).stream()
                .filter(log ->
                        businessKey.equals(safe(log.getBusinessKey())) ||
                                businessKey.equals(safe(log.getErrorBusinessKey())) ||
                                messageContains(log.getMessage(), businessKey))
                .sorted(Comparator.comparing(LogEntry::getLogTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return buildBusinessKeyAnalysisResponse(businessKey, relatedLogs);
    }

    public WorkflowStoryResponse buildImportStory(Long importId) {
        LogImport logImport = logImportService.getById(importId);
        List<LogEntry> logs = sortLogsAscending(logEntryRepository.findByLogImportId(importId));

        return buildStory(
                "IMPORT",
                String.valueOf(importId),
                logs,
                "Import du fichier [" + safe(logImport.getOriginalFileName()) + "]"
        );
    }

    public WorkflowStoryResponse buildBusinessKeyStory(String businessKey) {
        List<LogEntry> logs = logEntryRepository.findAll().stream()
                .filter(log ->
                        businessKey.equals(safe(log.getBusinessKey())) ||
                                businessKey.equals(safe(log.getErrorBusinessKey())) ||
                                messageContains(log.getMessage(), businessKey))
                .sorted(Comparator.comparing(LogEntry::getLogTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return buildStory(
                "BUSINESS_KEY",
                businessKey,
                logs,
                "Analyse narrative de la business key [" + businessKey + "]"
        );
    }

    public WorkflowStoryResponse buildBusinessKeyStoryForImport(Long importId, String businessKey) {
        List<LogEntry> logs = logEntryRepository.findByLogImportId(importId).stream()
                .filter(log ->
                        businessKey.equals(safe(log.getBusinessKey())) ||
                                businessKey.equals(safe(log.getErrorBusinessKey())) ||
                                messageContains(log.getMessage(), businessKey))
                .sorted(Comparator.comparing(LogEntry::getLogTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return buildStory(
                "IMPORT_BUSINESS_KEY",
                "import=" + importId + ", businessKey=" + businessKey,
                logs,
                "Analyse narrative de la business key [" + businessKey + "] dans l'import [" + importId + "]"
        );
    }

    public LogExplanationResponse explainLog(Long id) {
        LogEntry log = logEntryRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Log not found with id: " + id));

        LogExplanationResponse response = mapToExplanation(log);
        response.setHumanExplanation(buildHumanExplanation(log));
        return response;
    }

    public List<LogExplanationResponse> explainTopErrors(int limit) {
        return logEntryRepository.findAll().stream()
                .filter(this::isErrorLog)
                .sorted(Comparator.comparing(LogEntry::getLogTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(log -> {
                    LogExplanationResponse response = mapToExplanation(log);
                    response.setHumanExplanation(buildHumanExplanation(log));
                    return response;
                })
                .toList();
    }

    private DiagnosticResult buildDiagnostic(String scope,
                                             String reference,
                                             String status,
                                             List<LogEntry> logs) {
        DiagnosticResult result = new DiagnosticResult();
        result.setScope(scope);
        result.setReference(reference);
        result.setStatus(status);
        result.setTotalLogs(logs.size());

        List<LogEntry> errors = logs.stream()
                .filter(this::isErrorLog)
                .toList();

        long errorCount = errors.size();
        result.setErrorCount(errorCount);

        double errorRate = logs.isEmpty() ? 0.0 : ((double) errorCount / logs.size()) * 100.0;
        result.setErrorRate(round2(errorRate));

        String dominantEventType = findDominantEventType(errors, logs);
        result.setDominantEventType(dominantEventType);
        result.setMainCause(inferMainCause(errors, logs, dominantEventType));
        result.setFailureStep(inferFailureStep(errors, logs, dominantEventType));

        LogEntry firstCriticalError = errors.stream()
                .sorted(Comparator.comparing(LogEntry::getLogTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst()
                .orElse(null);

        if (firstCriticalError != null) {
            result.setFirstCriticalLogId(firstCriticalError.getId());
            result.setFirstCriticalErrorMessage(firstNonEmpty(
                    buildErrorSentence(firstCriticalError),
                    firstCriticalError.getMessage()
            ));
        }

        result.setAffectedFields(topDistinctValues(
                errors.stream()
                        .map(log -> firstNonEmpty(log.getFieldName(), log.getErrorAttribute()))
                        .toList(), 10
        ));

        result.setAffectedBusinessKeys(topDistinctValues(
                errors.stream()
                        .map(log -> firstNonEmpty(log.getErrorBusinessKey(), log.getBusinessKey()))
                        .toList(), 10
        ));

        List<String> anomalies = detectAdvancedAnomalies(logs, errors, status);
        result.setAnomalies(anomalies);

        String severity = computeSeverity(status, logs, errors, dominantEventType, result.getAffectedBusinessKeys());
        result.setSeverity(severity);

        String confidence = computeConfidence(logs, errors, dominantEventType);
        result.setConfidence(confidence);

        String impact = inferImpact(dominantEventType, result.getAffectedFields(), result.getAffectedBusinessKeys(), status, errorRate);
        result.setImpact(impact);

        List<String> recommendations = buildRecommendations(
                dominantEventType,
                result.getAffectedFields(),
                result.getAffectedBusinessKeys(),
                firstCriticalError
        );
        result.setRecommendations(recommendations);

        result.setExecutiveSummary(buildExecutiveSummary(result));

        return result;
    }

    private WorkflowStoryResponse buildStory(String scope, String reference, List<LogEntry> logs, String summaryBase) {
        WorkflowStoryResponse response = new WorkflowStoryResponse();
        response.setScope(scope);
        response.setReference(reference);
        response.setTotalLogs(logs.size());

        List<LogEntry> importantLogs = logs.stream()
                .filter(this::isImportantForStory)
                .toList();

        response.setTotalImportantSteps(importantLogs.size());
        response.setTotalErrors(logs.stream().filter(this::isErrorLog).count());
        response.setDetectedAnomalies(detectAnomalies(logs));
        response.setSummary(buildStorySummary(summaryBase, logs, importantLogs));
        response.setConclusion(buildStoryConclusion(logs));

        List<StoryStepResponse> steps = importantLogs.stream()
                .map(this::mapToStoryStep)
                .toList();

        response.setSteps(steps);
        return response;
    }

    private StoryStepResponse mapToStoryStep(LogEntry log) {
        StoryStepResponse step = new StoryStepResponse();
        step.setLogId(log.getId());
        step.setTimestamp(log.getLogTimestamp());
        step.setLevel(log.getLevel());
        step.setEventType(log.getEventType());
        step.setFieldName(log.getFieldName());
        step.setInterfaceField(log.getInterfaceField());
        step.setRelationName(log.getRelationName());
        step.setBusinessKey(firstNonEmpty(log.getBusinessKey(), log.getErrorBusinessKey()));
        step.setOriginalMessage(log.getMessage());
        step.setHumanExplanation(buildHumanExplanation(log));
        return step;
    }

    private List<String> detectAnomalies(List<LogEntry> logs) {
        List<String> anomalies = new ArrayList<>();

        long errorCount = logs.stream().filter(this::isErrorLog).count();
        if (errorCount > 0) {
            anomalies.add("Des erreurs ont été détectées pendant le traitement (" + errorCount + ").");
        }

        long pmErrors = logs.stream()
                .filter(log -> "PM_MAPPING_ERROR".equalsIgnoreCase(safe(log.getEventType())))
                .count();
        if (pmErrors > 0) {
            anomalies.add("Des erreurs de mapping PM/PPPM ont été détectées (" + pmErrors + ").");
        }

        long nullContinues = logs.stream()
                .filter(log -> "NULL_CONTINUE".equalsIgnoreCase(safe(log.getEventType())))
                .count();
        if (nullContinues > 0) {
            anomalies.add("Certaines valeurs étaient nulles mais le traitement a continué (" + nullContinues + ").");
        }

        boolean missingMandatorySuccess = logs.stream()
                .anyMatch(log -> "MANDATORY_CHECK_START".equalsIgnoreCase(safe(log.getEventType())))
                && logs.stream().noneMatch(log -> "MANDATORY_CHECK_SUCCESS".equalsIgnoreCase(safe(log.getEventType())));
        if (missingMandatorySuccess) {
            anomalies.add("Le contrôle des champs obligatoires a démarré sans confirmation explicite de succès.");
        }

        if (anomalies.isEmpty()) {
            anomalies.add("Aucune anomalie majeure détectée dans ce périmètre.");
        }

        return anomalies;
    }

    private List<String> detectAdvancedAnomalies(List<LogEntry> logs,
                                                 List<LogEntry> errors,
                                                 String status) {
        List<String> anomalies = new ArrayList<>();

        double errorRate = logs.isEmpty() ? 0.0 : ((double) errors.size() / logs.size()) * 100.0;
        if (errorRate > 20.0) {
            anomalies.add("Taux d'erreur anormalement élevé (" + round2(errorRate) + "%).");
        }

        if ("DONE".equalsIgnoreCase(safe(status)) && !errors.isEmpty()) {
            anomalies.add("Import techniquement terminé (DONE) mais avec erreurs métier/applicatives.");
        }

        Map<String, Long> errorByField = errors.stream()
                .map(log -> firstNonEmpty(log.getFieldName(), log.getErrorAttribute()))
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        errorByField.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(entry -> {
                    if (entry.getValue() >= 3) {
                        anomalies.add("Forte concentration d'erreurs sur le champ [" + entry.getKey() + "] (" + entry.getValue() + ").");
                    }
                });

        Map<String, Long> errorByBk = errors.stream()
                .map(log -> firstNonEmpty(log.getErrorBusinessKey(), log.getBusinessKey()))
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        long failedBks = errorByBk.values().stream().filter(v -> v >= 2).count();
        if (failedBks > 0) {
            anomalies.add(failedBks + " business key(s) présentent plusieurs erreurs et semblent en échec fort.");
        }

        long fieldMappings = logs.stream()
                .filter(log -> "FIELD_MAPPING".equalsIgnoreCase(safe(log.getEventType())))
                .count();

        long fieldInserted = logs.stream()
                .filter(log -> "FIELD_INSERTED".equalsIgnoreCase(safe(log.getEventType())))
                .count();

        if (fieldMappings > 0 && fieldInserted == 0) {
            anomalies.add("Des mappings de champs existent sans insertion finale de valeur.");
        }

        if (fieldMappings > 5 && fieldInserted > 0 && fieldInserted < (fieldMappings / 3)) {
            anomalies.add("Beaucoup de mappings de champs mais peu d'insertions finales : structuration incomplète probable.");
        }

        long relationDetected = logs.stream()
                .filter(log -> "RELATION_DETECTED".equalsIgnoreCase(safe(log.getEventType())))
                .count();

        long relationAdded = logs.stream()
                .filter(log -> "RELATION_ADDED".equalsIgnoreCase(safe(log.getEventType())))
                .count();

        if (relationDetected > 0 && relationAdded == 0) {
            anomalies.add("Des relations ont été détectées mais aucune n'a été ajoutée.");
        }

        if (relationDetected > 3 && relationAdded > 0 && relationAdded < (relationDetected / 2)) {
            anomalies.add("Les relations détectées sont nettement moins nombreuses que les relations ajoutées attendues.");
        }

        long pmErrors = errors.stream()
                .filter(log -> "PM_MAPPING_ERROR".equalsIgnoreCase(safe(log.getEventType())))
                .count();
        if (pmErrors > 0) {
            anomalies.add("Erreur de mapping PM/PPPM détectée, potentiellement bloquante pour la structure métier.");
        }

        boolean missingMandatorySuccess = logs.stream()
                .anyMatch(log -> "MANDATORY_CHECK_START".equalsIgnoreCase(safe(log.getEventType())))
                && logs.stream().noneMatch(log -> "MANDATORY_CHECK_SUCCESS".equalsIgnoreCase(safe(log.getEventType())));
        if (missingMandatorySuccess) {
            anomalies.add("Le contrôle des champs obligatoires ne montre pas de succès explicite.");
        }

        long typeConversionNull = logs.stream()
                .filter(log -> "TYPE_CONVERSION_NULL".equalsIgnoreCase(safe(log.getEventType())))
                .count();
        if (typeConversionNull > 0) {
            anomalies.add("Certaines conversions de type retournent null, ce qui peut casser la structuration.");
        }

        if (anomalies.isEmpty()) {
            anomalies.add("Aucune anomalie avancée détectée.");
        }

        return anomalies.stream().distinct().toList();
    }

    private String buildStorySummary(String base, List<LogEntry> logs, List<LogEntry> importantLogs) {
        long mappingCount = logs.stream()
                .filter(log -> "FIELD_MAPPING".equalsIgnoreCase(safe(log.getEventType())))
                .count();

        long relationCount = logs.stream()
                .filter(log -> safe(log.getEventType()).startsWith("RELATION"))
                .count();

        long mandatoryChecks = logs.stream()
                .filter(log -> safe(log.getEventType()).startsWith("MANDATORY"))
                .count();

        return base + ". "
                + "Le système a produit " + logs.size() + " logs, dont "
                + importantLogs.size() + " étapes importantes retenues pour la narration. "
                + "On observe " + mappingCount + " étapes de mapping de champs, "
                + relationCount + " événements liés aux relations, "
                + "et " + mandatoryChecks + " événements liés au contrôle des champs obligatoires.";
    }

    private String buildStoryConclusion(List<LogEntry> logs) {
        long errors = logs.stream().filter(this::isErrorLog).count();

        if (logs.isEmpty()) {
            return "Aucune trace exploitable n'a été trouvée pour ce périmètre.";
        }

        if (errors == 0) {
            return "Le traitement semble s'être déroulé sans erreur explicite dans les logs analysés.";
        }

        String mainError = logs.stream()
                .filter(this::isErrorLog)
                .map(LogEntry::getEventType)
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("GENERIC_ERROR");

        String impact = inferImpact(mainError, List.of(), List.of(), "N/A", 0);

        return "Le traitement a rencontré des erreurs. La famille d'erreur dominante est ["
                + mainError + "]. "
                + impact + " "
                + "Il faut examiner les étapes précédant l'échec pour comprendre la cause racine.";
    }

    private boolean isImportantForStory(LogEntry log) {
        String eventType = safe(log.getEventType());

        return switch (eventType) {
            case "WORKFLOW_LOOP_START",
                 "WORKFLOW_NEW_INTERFACE_OBJECT",
                 "WORKFLOW_STRUCTURE_DATA_CALL",
                 "STRUCTURE_DATA_START",
                 "STRUCTURE_DATA_END",
                 "FIELD_MAPPING",
                 "TYPE_DETECTED",
                 "TYPE_CONVERSION_SUCCESS",
                 "FIELD_INSERTED",
                 "RELATION_DETECTED",
                 "RELATION_ADDED",
                 "RELATION_DATA_ATTACHED",
                 "STRUCTURED_OBJECT_ASSEMBLED",
                 "MANDATORY_CHECK_START",
                 "MANDATORY_FIELDS_LIST",
                 "MANDATORY_FIELD_CHECK",
                 "MANDATORY_CHECK_SUCCESS",
                 "PM_MAPPING_ERROR",
                 "ERROR_DETAILS",
                 "NULL_CONTINUE" -> true;
            default -> isErrorLog(log);
        };
    }

    private BusinessKeyAnalysisResponse buildBusinessKeyAnalysisResponse(String businessKey, List<LogEntry> relatedLogs) {
        BusinessKeyAnalysisResponse response = new BusinessKeyAnalysisResponse();
        response.setRequestedBusinessKey(businessKey);
        response.setTotalRelatedLogs(relatedLogs.size());

        response.setCountByLevel(countValues(
                relatedLogs.stream().map(LogEntry::getLevel).filter(Objects::nonNull).toList()
        ));

        response.setCountByEventType(countValues(
                relatedLogs.stream().map(LogEntry::getEventType).filter(Objects::nonNull).toList()
        ));

        response.setInvolvedFields(uniqueList(
                relatedLogs.stream().map(LogEntry::getFieldName).filter(this::hasText).toList()
        ));

        response.setInvolvedRelations(uniqueList(
                relatedLogs.stream()
                        .flatMap(log -> Arrays.stream(new String[]{log.getRelationName(), log.getRelationKey()}))
                        .filter(this::hasText)
                        .toList()
        ));

        response.setImportantMessages(uniqueList(
                relatedLogs.stream().map(LogEntry::getMessage).filter(this::hasText).limit(20).toList()
        ));

        response.setDetectedErrors(uniqueList(
                relatedLogs.stream()
                        .filter(this::isErrorLog)
                        .map(this::buildErrorSentence)
                        .filter(this::hasText)
                        .toList()
        ));

        response.setSummary(buildBusinessKeySummary(response, relatedLogs));
        return response;
    }

    private LogExplanationResponse mapToExplanation(LogEntry log) {
        LogExplanationResponse response = new LogExplanationResponse();
        response.setId(log.getId());
        response.setLevel(log.getLevel());
        response.setEventType(log.getEventType());
        response.setSourceClass(log.getSourceClass());
        response.setProcessName(log.getProcessName());
        response.setFieldName(log.getFieldName());
        response.setInterfaceField(log.getInterfaceField());
        response.setParsedType(log.getParsedType());
        response.setParsedValue(log.getParsedValue());
        response.setRelationName(log.getRelationName());
        response.setBusinessKey(log.getBusinessKey());
        response.setErrorColumn(log.getErrorColumn());
        response.setErrorAttribute(log.getErrorAttribute());
        response.setErrorValue(log.getErrorValue());
        response.setErrorBusinessKey(log.getErrorBusinessKey());
        response.setOriginalMessage(log.getMessage());
        response.setBusinessMeaning(log.getBusinessMeaning());
        return response;
    }

    private String buildGlobalInterpretation(AnalysisSummaryResponse response, List<LogEntry> logs) {
        String mostCommonEventType = response.getCountByEventType() == null || response.getCountByEventType().isEmpty()
                ? "UNKNOWN"
                : response.getCountByEventType().entrySet().stream()
                  .max(Map.Entry.comparingByValue())
                  .map(Map.Entry::getKey)
                  .orElse("UNKNOWN");

        long mandatoryChecks = logs.stream()
                .filter(log -> "MANDATORY_FIELDS_LIST".equalsIgnoreCase(safe(log.getEventType())))
                .count();

        long relationEvents = logs.stream()
                .filter(log -> safe(log.getEventType()).startsWith("RELATION"))
                .count();

        return "Le dataset contient " + response.getTotalLogs() + " logs, dont "
                + response.getTotalErrors() + " erreurs. "
                + "L'événement le plus fréquent est [" + mostCommonEventType + "]. "
                + "On observe " + mandatoryChecks + " étapes liées au contrôle des champs obligatoires "
                + "et " + relationEvents + " événements liés à la construction de relations métier.";
    }

    private String buildImportSummaryText(ImportSummaryResponse response) {
        return "Sur l'import #" + response.getImportId()
                + " (" + safe(response.getOriginalFileName()) + "), "
                + response.getTotalLines() + " lignes ont été lues, "
                + response.getProcessedLines() + " ont été traitées, "
                + response.getFailedLines() + " ont échoué. "
                + "Nombre total de logs stockés : " + response.getTotalLogsStored()
                + ", erreurs : " + response.getTotalErrors() + ".";
    }

    private String buildBusinessKeySummary(BusinessKeyAnalysisResponse response, List<LogEntry> relatedLogs) {
        if (relatedLogs.isEmpty()) {
            return "Aucun log relié à cette business key n'a été trouvé.";
        }

        long errorCount = relatedLogs.stream().filter(this::isErrorLog).count();
        String firstEvent = relatedLogs.stream()
                .map(LogEntry::getEventType)
                .filter(this::hasText)
                .findFirst()
                .orElse("UNKNOWN");

        String lastEvent = relatedLogs.stream()
                .map(LogEntry::getEventType)
                .filter(this::hasText)
                .reduce((first, second) -> second)
                .orElse("UNKNOWN");

        return "Pour la business key [" + response.getRequestedBusinessKey() + "], on a trouvé "
                + response.getTotalRelatedLogs() + " logs. "
                + "Le traitement commence principalement autour de [" + firstEvent + "] "
                + "et se termine autour de [" + lastEvent + "]. "
                + "Nombre d'erreurs détectées : " + errorCount + ".";
    }

    private String buildHumanExplanation(LogEntry log) {
        String eventType = safe(log.getEventType());

        switch (eventType) {
            case "FIELD_MAPPING":
                return "Le système mappe le champ interface [" + safe(log.getInterfaceField())
                        + "] vers l'attribut métier [" + safe(log.getFieldName()) + "].";
            case "FIELD_TRIM_REQUIRED":
                return "Le système nettoie la valeur brute avant transformation.";
            case "TYPE_DETECTED":
                return "Le système a détecté que la valeur doit être convertie vers le type [" + safe(log.getParsedType()) + "].";
            case "TYPE_CONVERSION_RESULT":
            case "TYPE_CONVERSION_SUCCESS":
                return "La conversion de la valeur s'est terminée avec succès.";
            case "TYPE_CONVERSION_NULL":
                return "La conversion de type retourne null, ce qui peut empêcher la valorisation correcte du champ.";
            case "FIELD_INSERTED":
                return "La valeur [" + safe(log.getParsedValue()) + "] a été injectée dans l'objet final pour le champ ["
                        + safe(log.getFieldName()) + "].";
            case "RELATION_DETECTED":
                return "Le système a détecté qu'une relation métier doit être construite.";
            case "RELATION_ADDED":
                return "La relation [" + safe(log.getRelationName()) + "] a été créée.";
            case "RELATION_DATA_ATTACHED":
                return "Les données de relation ont été attachées à l'objet final.";
            case "STRUCTURED_OBJECT_ASSEMBLED":
                return "Le système a assemblé l'objet structuré final avec ses relations et sa business key.";
            case "MANDATORY_CHECK_START":
                return "Le contrôle des champs obligatoires commence.";
            case "MANDATORY_FIELDS_LIST":
                return "Le système liste les champs obligatoires attendus avant validation.";
            case "MANDATORY_FIELD_CHECK":
                return "Le système vérifie un champ obligatoire : [" + safe(log.getMandatoryField()) + "].";
            case "MANDATORY_CHECK_SUCCESS":
                return "Le système confirme que les champs obligatoires sont respectés.";
            case "NULL_CONTINUE":
                return "Une valeur nulle a été rencontrée, mais le traitement continue car elle n'est pas bloquante.";
            case "PM_MAPPING_ERROR":
                return "Une erreur métier est survenue : le système n'a pas pu résoudre correctement une valeur de type PM/PPPM.";
            case "ERROR_DETAILS":
                return "Le détail de l'erreur indique : colonne [" + safe(log.getErrorColumn())
                        + "], attribut [" + safe(log.getErrorAttribute())
                        + "], valeur [" + safe(log.getErrorValue())
                        + "], business key [" + safe(log.getErrorBusinessKey()) + "].";
            default:
                if (isErrorLog(log)) {
                    return "Cette ligne correspond à une erreur applicative ou métier. Elle doit être analysée avec son contexte.";
                }
                return "Cette ligne correspond à une étape technique ou métier du traitement.";
        }
    }

    private String buildErrorSentence(LogEntry log) {
        if (!isErrorLog(log)) {
            return null;
        }

        if (hasText(log.getErrorColumn()) || hasText(log.getErrorAttribute()) || hasText(log.getErrorBusinessKey())) {
            return "Erreur sur colonne [" + safe(log.getErrorColumn()) + "], attribut ["
                    + safe(log.getErrorAttribute()) + "], valeur ["
                    + safe(log.getErrorValue()) + "], BK ["
                    + safe(log.getErrorBusinessKey()) + "].";
        }

        return safe(log.getMessage());
    }

    private String findDominantEventType(List<LogEntry> errors, List<LogEntry> logs) {
        List<LogEntry> base = errors.isEmpty() ? logs : errors;

        return base.stream()
                .map(LogEntry::getEventType)
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("UNKNOWN");
    }

    private String inferMainCause(List<LogEntry> errors, List<LogEntry> logs, String dominantEventType) {
        if (errors.isEmpty() && logs.isEmpty()) {
            return "Aucune donnée exploitable";
        }

        if ("PM_MAPPING_ERROR".equalsIgnoreCase(safe(dominantEventType))) {
            return "Erreur de mapping PM/PPPM";
        }

        if (Set.of("MANDATORY_FIELDS_LIST", "MANDATORY_FIELD_CHECK", "MANDATORY_CHECK_END", "MANDATORY_CHECK_START")
                .contains(safe(dominantEventType))) {
            return "Champs obligatoires manquants ou invalides";
        }

        if (safe(dominantEventType).startsWith("TYPE_CONVERSION")) {
            return "Conversion de type incorrecte ou valeur non compatible";
        }

        if ("ERROR_DETAILS".equalsIgnoreCase(safe(dominantEventType))) {
            return "Erreur détaillée sur colonne, attribut ou valeur";
        }

        long fieldMappings = logs.stream()
                .filter(log -> "FIELD_MAPPING".equalsIgnoreCase(safe(log.getEventType())))
                .count();
        long fieldInserted = logs.stream()
                .filter(log -> "FIELD_INSERTED".equalsIgnoreCase(safe(log.getEventType())))
                .count();

        if (fieldMappings > 0 && fieldInserted == 0) {
            return "Mapping de champs commencé mais structuration non finalisée";
        }

        if ("FIELD_MAPPING".equalsIgnoreCase(safe(dominantEventType))) {
            return "Problème de mapping entre données source et modèle métier";
        }

        long relationDetected = logs.stream()
                .filter(log -> "RELATION_DETECTED".equalsIgnoreCase(safe(log.getEventType())))
                .count();
        long relationAdded = logs.stream()
                .filter(log -> "RELATION_ADDED".equalsIgnoreCase(safe(log.getEventType())))
                .count();

        if (relationDetected > 0 && relationAdded == 0) {
            return "Relations détectées mais non construites";
        }

        if (!errors.isEmpty()) {
            return "Erreur applicative ou métier dominante à investiguer";
        }

        return "Aucune cause racine claire détectée";
    }

    private String inferFailureStep(List<LogEntry> errors, List<LogEntry> logs, String dominantEventType) {
        if (!errors.isEmpty() && hasText(dominantEventType)) {
            return dominantEventType;
        }

        long fieldMappings = logs.stream()
                .filter(log -> "FIELD_MAPPING".equalsIgnoreCase(safe(log.getEventType())))
                .count();
        long fieldInserted = logs.stream()
                .filter(log -> "FIELD_INSERTED".equalsIgnoreCase(safe(log.getEventType())))
                .count();

        if (fieldMappings > 0 && fieldInserted == 0) {
            return "FIELD_MAPPING";
        }

        long relationDetected = logs.stream()
                .filter(log -> "RELATION_DETECTED".equalsIgnoreCase(safe(log.getEventType())))
                .count();
        long relationAdded = logs.stream()
                .filter(log -> "RELATION_ADDED".equalsIgnoreCase(safe(log.getEventType())))
                .count();

        if (relationDetected > 0 && relationAdded == 0) {
            return "RELATION_DETECTED";
        }

        return hasText(dominantEventType) ? dominantEventType : "UNKNOWN";
    }

    private String inferImpact(String dominantEventType,
                               List<String> fields,
                               List<String> businessKeys,
                               String status,
                               double errorRate) {
        String mainField = fields.isEmpty() ? null : fields.get(0);
        String mainBk = businessKeys.isEmpty() ? null : businessKeys.get(0);

        StringBuilder impact = new StringBuilder();

        if ("PM_MAPPING_ERROR".equalsIgnoreCase(safe(dominantEventType))) {
            impact.append("Le mapping PM/PPPM empêche la construction correcte de la structure métier");
        } else if (safe(dominantEventType).startsWith("TYPE_CONVERSION")) {
            impact.append("Certaines valeurs ne peuvent pas être converties correctement vers le type attendu");
        } else if (Set.of("MANDATORY_FIELDS_LIST", "MANDATORY_FIELD_CHECK", "MANDATORY_CHECK_END", "MANDATORY_CHECK_START")
                .contains(safe(dominantEventType))) {
            impact.append("La validation métier échoue car les champs obligatoires ne sont pas correctement valorisés");
        } else if ("FIELD_MAPPING".equalsIgnoreCase(safe(dominantEventType))) {
            impact.append("Le mapping entre l'interface et le modèle métier reste incomplet ou incohérent");
        } else if ("ERROR_DETAILS".equalsIgnoreCase(safe(dominantEventType))) {
            impact.append("Une erreur précise sur une colonne ou un attribut empêche la bonne valorisation des données");
        } else if ("DONE".equalsIgnoreCase(safe(status)) && errorRate > 0) {
            impact.append("Le traitement est terminé techniquement mais dégradé métier");
        } else {
            impact.append("Le flux de traitement est perturbé et nécessite une analyse ciblée");
        }

        if (hasText(mainField)) {
            impact.append(". Le champ le plus touché est [").append(mainField).append("]");
        }

        if (hasText(mainBk)) {
            impact.append(". Une business key particulièrement impactée est [").append(mainBk).append("]");
        }

        impact.append(".");
        return impact.toString();
    }

    private String computeSeverity(String status,
                                   List<LogEntry> logs,
                                   List<LogEntry> errors,
                                   String dominantEventType,
                                   List<String> affectedBusinessKeys) {
        if (errors.isEmpty()) {
            return "LOW";
        }

        double rate = logs.isEmpty() ? 0.0 : ((double) errors.size() / logs.size()) * 100.0;
        boolean criticalType = "PM_MAPPING_ERROR".equalsIgnoreCase(safe(dominantEventType))
                || Set.of("MANDATORY_FIELDS_LIST", "MANDATORY_FIELD_CHECK", "ERROR_DETAILS").contains(safe(dominantEventType));

        if ("FAILED".equalsIgnoreCase(safe(status))) {
            return "CRITICAL";
        }

        if (criticalType && affectedBusinessKeys.size() >= 3) {
            return "CRITICAL";
        }

        if (rate >= 30.0) {
            return "CRITICAL";
        }

        if (rate >= 15.0 || criticalType || affectedBusinessKeys.size() >= 2) {
            return "HIGH";
        }

        if (rate >= 5.0) {
            return "MEDIUM";
        }

        return "LOW";
    }

    private String computeConfidence(List<LogEntry> logs,
                                     List<LogEntry> errors,
                                     String dominantEventType) {
        boolean hasLogs = !logs.isEmpty();
        boolean hasErrors = !errors.isEmpty();
        boolean hasDominant = hasText(dominantEventType) && !"UNKNOWN".equalsIgnoreCase(dominantEventType);

        if (hasLogs && hasErrors && hasDominant && logs.size() >= 5) {
            return "HIGH";
        }

        if ((hasLogs && hasErrors) || (hasLogs && hasDominant)) {
            return "MEDIUM";
        }

        return "LOW";
    }

    private List<String> buildRecommendations(String dominantEventType,
                                              List<String> affectedFields,
                                              List<String> affectedBusinessKeys,
                                              LogEntry firstCriticalError) {
        List<String> recommendations = new ArrayList<>();
        String firstField = affectedFields.isEmpty() ? null : affectedFields.get(0);
        String firstBk = affectedBusinessKeys.isEmpty() ? null : affectedBusinessKeys.get(0);

        if ("PM_MAPPING_ERROR".equalsIgnoreCase(safe(dominantEventType))) {
            recommendations.add("Vérifier la configuration de mapping PM/PPPM.");
            recommendations.add("Comparer la valeur source avec l'attribut métier attendu.");
            if (hasText(firstField)) {
                recommendations.add("Contrôler en priorité le champ [" + firstField + "].");
            }
            if (hasText(firstBk)) {
                recommendations.add("Rejouer ou examiner le cas de la business key [" + firstBk + "].");
            }
            return recommendations;
        }

        if (Set.of("MANDATORY_FIELDS_LIST", "MANDATORY_FIELD_CHECK", "MANDATORY_CHECK_END", "MANDATORY_CHECK_START")
                .contains(safe(dominantEventType))) {
            recommendations.add("Vérifier la présence et la conformité des champs obligatoires.");
            if (hasText(firstField)) {
                recommendations.add("Inspecter d'abord le champ obligatoire [" + firstField + "].");
            }
            if (hasText(firstBk)) {
                recommendations.add("Tester la business key [" + firstBk + "] pour reproduire l'échec.");
            }
            return recommendations;
        }

        if (safe(dominantEventType).startsWith("TYPE_CONVERSION")) {
            recommendations.add("Vérifier le type attendu côté cible.");
            recommendations.add("Comparer le format réel des valeurs entrantes.");
            if (hasText(firstField)) {
                recommendations.add("Inspecter les valeurs du champ [" + firstField + "].");
            }
            return recommendations;
        }

        if ("FIELD_MAPPING".equalsIgnoreCase(safe(dominantEventType))) {
            recommendations.add("Comparer le champ interface et le champ métier cible.");
            recommendations.add("Vérifier si le mapping produit bien une insertion finale.");
            if (hasText(firstField)) {
                recommendations.add("Analyser d'abord le champ [" + firstField + "].");
            }
            return recommendations;
        }

        if ("ERROR_DETAILS".equalsIgnoreCase(safe(dominantEventType))) {
            recommendations.add("Lire le détail colonne / attribut / valeur de l'erreur critique.");
            recommendations.add("Vérifier si la valeur source est conforme à l'attribut cible.");
            if (firstCriticalError != null) {
                recommendations.add("Commencer l'investigation par le log critique #" + firstCriticalError.getId() + ".");
            }
            return recommendations;
        }

        recommendations.add("Examiner la première erreur critique dans l'ordre chronologique.");
        recommendations.add("Comparer les champs touchés et les business keys impactées.");
        if (firstCriticalError != null) {
            recommendations.add("Commencer par le log critique #" + firstCriticalError.getId() + ".");
        }
        return recommendations;
    }

    private String buildExecutiveSummary(DiagnosticResult result) {
        if (result.getTotalLogs() == 0) {
            return "Aucun log exploitable n'a été trouvé pour ce périmètre.";
        }

        if (result.getErrorCount() == 0) {
            return "Aucune erreur explicite n'a été détectée. "
                    + "Le périmètre semble globalement sain, avec un niveau de confiance "
                    + result.getConfidence() + ".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Le périmètre [").append(result.getReference()).append("] présente ")
                .append(result.getErrorCount()).append(" erreur(s) sur ")
                .append(result.getTotalLogs()).append(" log(s), soit ")
                .append(result.getErrorRate()).append("% d'erreurs. ");

        if (hasText(result.getMainCause())) {
            sb.append("La cause principale probable est [").append(result.getMainCause()).append("]. ");
        }

        if (hasText(result.getFailureStep())) {
            sb.append("L'étape de rupture la plus probable est [").append(result.getFailureStep()).append("]. ");
        }

        if (hasText(result.getImpact())) {
            sb.append(result.getImpact()).append(" ");
        }

        sb.append("Gravité : ").append(result.getSeverity())
                .append(". Confiance : ").append(result.getConfidence()).append(".");

        return sb.toString().trim();
    }

    private Map<String, Long> countValues(List<String> values) {
        return values.stream()
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
    }

    private List<String> topN(List<String> values, int n) {
        return values.stream()
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(n)
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .toList();
    }

    private List<String> topDistinctValues(List<String> values, int n) {
        return values.stream()
                .filter(this::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<String> uniqueList(List<String> values) {
        return values.stream()
                .filter(this::hasText)
                .distinct()
                .limit(50)
                .toList();
    }

    private List<LogEntry> sortLogs(List<LogEntry> logs) {
        return logs.stream()
                .sorted(
                        Comparator.comparing(
                                        LogEntry::getLogTimestamp,
                                        Comparator.nullsLast(Comparator.reverseOrder())
                                )
                                .thenComparing(
                                        LogEntry::getId,
                                        Comparator.nullsLast(Comparator.reverseOrder())
                                )
                )
                .toList();
    }

    private List<LogEntry> sortLogsAscending(List<LogEntry> logs) {
        return logs.stream()
                .sorted(
                        Comparator.comparing(
                                        LogEntry::getLogTimestamp,
                                        Comparator.nullsLast(Comparator.naturalOrder())
                                )
                                .thenComparing(
                                        LogEntry::getId,
                                        Comparator.nullsLast(Comparator.naturalOrder())
                                )
                )
                .toList();
    }

    private boolean messageContains(String message, String token) {
        return hasText(message) && hasText(token) && message.contains(token);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonEmpty(String a, String b) {
        if (hasText(a)) return a;
        if (hasText(b)) return b;
        return "";
    }

    private boolean isErrorLog(LogEntry log) {
        return log != null && Boolean.TRUE.equals(log.getError());
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record ImportDiagnosticView(
            Long importId,
            String fileName,
            String status,
            long totalLogs,
            long errorCount,
            double errorRate,
            String severity
    ) {
        int severityRank() {
            return switch (severity) {
                case "CRITICAL" -> 4;
                case "HIGH" -> 3;
                case "MEDIUM" -> 2;
                default -> 1;
            };
        }
    }
}