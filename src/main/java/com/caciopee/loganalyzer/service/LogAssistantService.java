package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.AnalysisSummaryResponse;
import com.caciopee.loganalyzer.dto.AssistantAnswerResponse;
import com.caciopee.loganalyzer.dto.BusinessKeyAnalysisResponse;
import com.caciopee.loganalyzer.dto.DiagnosticResult;
import com.caciopee.loganalyzer.dto.ImportSummaryResponse;
import com.caciopee.loganalyzer.dto.LogExplanationResponse;
import com.caciopee.loganalyzer.dto.WorkflowStoryResponse;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LogAssistantService {

    private static final Pattern IMPORT_ID_PATTERN =
            Pattern.compile("\\bimport\\s*(?:id\\s*)?(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern LOG_ID_PATTERN =
            Pattern.compile("\\blog\\s*(?:id\\s*)?(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern BUSINESS_KEY_PATTERN =
            Pattern.compile("\\b(?:business\\s*key|bk)\\s*[:#-]?\\s*([\\w\\-]+)\\b", Pattern.CASE_INSENSITIVE);

    private final LogAnalysisService logAnalysisService;

    public LogAssistantService(LogAnalysisService logAnalysisService) {
        this.logAnalysisService = logAnalysisService;
    }

    public AssistantAnswerResponse ask(String question) {
        String normalized = normalize(question);

        if (!hasText(question)) {
            return buildSimpleResponse(
                    question,
                    "EMPTY",
                    "Je n'ai reçu aucune question exploitable.",
                    "LOW",
                    "HIGH",
                    List.of("Question vide ou non exploitable."),
                    List.of("Saisir une question plus précise."),
                    defaultHints()
            );
        }

        Long importId = extractImportId(question);
        Long logId = extractLogId(question);
        String businessKey = extractBusinessKey(question);

        try {
            if (isGlobalSummaryQuestion(normalized)) {
                return answerGlobalSummary(question);
            }

            if (logId != null && isLogExplanationQuestion(normalized)) {
                return answerLogExplanation(question, logId);
            }

            if (importId != null && isPrincipalProblemQuestion(normalized)) {
                return answerPrincipalProblem(question, importId);
            }

            if (importId != null && isFailureLocationQuestion(normalized)) {
                return answerFailureLocation(question, importId);
            }

            if (importId != null && isImportSuccessQuestion(normalized)) {
                return answerImportSuccessStatus(question, importId);
            }

            if (importId != null && isRecommendationsQuestion(normalized)) {
                return answerImportRecommendations(question, importId);
            }

            if (importId != null && isDiagnosticQuestion(normalized)) {
                return answerImportDiagnostic(question, importId);
            }

            if (importId != null && isErrorBusinessKeysQuestion(normalized)) {
                return answerImportErrorBusinessKeys(question, importId);
            }

            if (importId != null && isImportErrorsQuestion(normalized)) {
                return answerImportErrors(question, importId);
            }

            if (importId != null && isImportBusinessKeysQuestion(normalized)) {
                return answerImportBusinessKeys(question, importId);
            }

            if (isStoryQuestion(normalized) && importId != null && businessKey != null) {
                return answerBusinessKeyStoryInImport(question, importId, businessKey);
            }

            if (isStoryQuestion(normalized) && importId != null) {
                return answerImportStory(question, importId);
            }

            if (isStoryQuestion(normalized) && businessKey != null) {
                return answerBusinessKeyStory(question, businessKey);
            }

            if (businessKey != null && importId != null && isDiagnosticQuestion(normalized)) {
                return answerBusinessKeyDiagnosticInImport(question, importId, businessKey);
            }

            if (businessKey != null && isDiagnosticQuestion(normalized)) {
                return answerBusinessKeyDiagnostic(question, businessKey);
            }

            if (businessKey != null && isBusinessKeyAnalysisQuestion(normalized)) {
                return answerBusinessKeyAnalysis(question, businessKey);
            }

            if (importId != null && isImportSummaryQuestion(normalized)) {
                return answerImportSummary(question, importId);
            }

            if (containsAny(normalized, "resume", "résumé", "explique", "analyse", "details", "détails") && importId != null) {
                return answerImportSummary(question, importId);
            }

            return buildSimpleResponse(
                    question,
                    "UNKNOWN",
                    "Je n'ai pas compris précisément la demande. "
                            + "Tu peux me demander par exemple : "
                            + "\"quel est le problème principal de l'import 7\", "
                            + "\"où ça a échoué dans l'import 7\", "
                            + "\"est-ce que l'import 7 est réussi\", "
                            + "\"quelle BK est en erreur dans l'import 7\", "
                            + "\"donne-moi le diagnostic de l'import 7\".",
                    "LOW",
                    "MEDIUM",
                    List.of("Aucune intention fiable détectée."),
                    List.of("Formuler la question avec un import, une BK ou un log précis."),
                    defaultHints()
            );

        } catch (IllegalArgumentException | NoSuchElementException e) {
            return buildSimpleResponse(
                    question,
                    "ERROR",
                    "Je n'ai pas pu répondre correctement à la question : " + e.getMessage(),
                    "MEDIUM",
                    "HIGH",
                    List.of("Erreur métier ou donnée introuvable."),
                    List.of("Vérifier l'ID d'import, l'ID de log ou la business key utilisée."),
                    defaultHints()
            );
        } catch (Exception e) {
            return buildSimpleResponse(
                    question,
                    "ERROR",
                    "Une erreur inattendue s'est produite pendant l'analyse : " + e.getMessage(),
                    "MEDIUM",
                    "MEDIUM",
                    List.of("Erreur technique pendant le traitement de la question."),
                    List.of("Réessayer avec une question plus ciblée."),
                    defaultHints()
            );
        }
    }

    private AssistantAnswerResponse answerGlobalSummary(String question) {
        AnalysisSummaryResponse summary = logAnalysisService.buildGlobalSummary();

        List<String> findings = new ArrayList<>();
        findings.add("Total logs : " + summary.getTotalLogs());
        findings.add("Total erreurs : " + summary.getTotalErrors());
        findings.add("Total logs INFO : " + summary.getTotalInfos());

        if (summary.getCountByEventType() != null && !summary.getCountByEventType().isEmpty()) {
            String topEvent = topKey(summary.getCountByEventType());
            if (hasText(topEvent)) {
                findings.add("Événement dominant : " + topEvent);
            }
        }

        if (summary.getTopErrorMessages() != null && !summary.getTopErrorMessages().isEmpty()) {
            findings.add("Message d'erreur récurrent : " + summary.getTopErrorMessages().get(0));
        }

        List<String> recommendations = new ArrayList<>();
        recommendations.add("Identifier l'import le plus critique.");
        recommendations.add("Analyser les erreurs dominantes avant de lire les logs bruts.");
        recommendations.add("Étudier les BK les plus touchées si les erreurs sont concentrées.");

        String severity = computeSeverity(summary.getTotalLogs(), summary.getTotalErrors());
        String confidence = summary.getTotalLogs() > 0 ? "HIGH" : "MEDIUM";

        String shortAnswer = "Vue globale : "
                + summary.getTotalErrors() + " erreurs sur "
                + summary.getTotalLogs() + " logs.";

        String answer = buildStructuredAnswer(
                shortAnswer,
                findings,
                safe(summary.getGlobalInterpretation()),
                recommendations
        );

        return buildResponse(
                question,
                "GLOBAL_SUMMARY",
                shortAnswer,
                answer,
                severity,
                confidence,
                findings,
                recommendations,
                List.of(
                        "Quel est le problème principal de l'import 7 ?",
                        "Où ça a échoué dans l'import 7 ?",
                        "Quelle BK est en erreur dans l'import 7 ?",
                        "Donne-moi le diagnostic de l'import 7"
                )
        );
    }

    private AssistantAnswerResponse answerImportSummary(String question, Long importId) {
        ImportSummaryResponse summary = logAnalysisService.buildImportSummary(importId);
        DiagnosticResult diagnostic = logAnalysisService.buildImportDiagnostic(importId);

        List<String> findings = new ArrayList<>();
        findings.add("Statut : " + safe(summary.getStatus()));
        findings.add("Logs stockés : " + summary.getTotalLogsStored());
        findings.add("Erreurs : " + summary.getTotalErrors());
        findings.add("Lignes échouées pendant l'import : " + safeInt(summary.getFailedLines()));

        if (hasText(diagnostic.getMainCause())) {
            findings.add("Cause principale probable : " + diagnostic.getMainCause());
        }
        if (hasText(diagnostic.getFailureStep())) {
            findings.add("Étape de rupture : " + diagnostic.getFailureStep());
        }
        if (diagnostic.getAffectedFields() != null && !diagnostic.getAffectedFields().isEmpty()) {
            findings.add("Champ le plus touché : " + diagnostic.getAffectedFields().get(0));
        }
        if (diagnostic.getAffectedBusinessKeys() != null && !diagnostic.getAffectedBusinessKeys().isEmpty()) {
            findings.add("Business key la plus touchée : " + diagnostic.getAffectedBusinessKeys().get(0));
        }

        String shortAnswer = "Import " + importId + " : " + buildImportSummarySentence(summary, diagnostic);

        String interpretation = firstNonEmpty(
                diagnostic.getExecutiveSummary(),
                summary.getSummary()
        );

        String answer = buildStructuredAnswer(
                shortAnswer,
                findings,
                interpretation,
                safeList(diagnostic.getRecommendations())
        );

        return buildResponse(
                question,
                "IMPORT_SUMMARY",
                shortAnswer,
                answer,
                firstNonEmpty(diagnostic.getSeverity(), computeSeverity(summary.getTotalLogsStored(), summary.getTotalErrors())),
                firstNonEmpty(diagnostic.getConfidence(), "MEDIUM"),
                findings,
                safeList(diagnostic.getRecommendations()),
                dynamicHintsForImport(importId, firstBusinessKey(diagnostic))
        );
    }

    private AssistantAnswerResponse answerImportDiagnostic(String question, Long importId) {
        DiagnosticResult diagnostic = logAnalysisService.buildImportDiagnostic(importId);

        List<String> findings = new ArrayList<>();
        findings.add("Statut : " + safe(diagnostic.getStatus()));
        findings.add("Nombre total de logs : " + diagnostic.getTotalLogs());
        findings.add("Nombre total d'erreurs : " + diagnostic.getErrorCount());
        findings.add("Taux d'erreur : " + diagnostic.getErrorRate() + "%");

        if (hasText(diagnostic.getMainCause())) {
            findings.add("Cause principale : " + diagnostic.getMainCause());
        }
        if (hasText(diagnostic.getFailureStep())) {
            findings.add("Étape de rupture : " + diagnostic.getFailureStep());
        }
        if (hasText(diagnostic.getDominantEventType())) {
            findings.add("Signal dominant : " + diagnostic.getDominantEventType());
        }
        if (diagnostic.getFirstCriticalLogId() != null) {
            findings.add("Premier log critique : #" + diagnostic.getFirstCriticalLogId());
        }

        String shortAnswer = "Diagnostic de l'import " + importId + " : "
                + firstNonEmpty(
                diagnostic.getMainCause(),
                "aucune cause claire détectée"
        ) + ".";

        String interpretation = firstNonEmpty(
                diagnostic.getExecutiveSummary(),
                diagnostic.getImpact()
        );

        List<String> recommendations = safeList(diagnostic.getRecommendations());

        return buildResponse(
                question,
                "IMPORT_DIAGNOSTIC",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, recommendations),
                firstNonEmpty(diagnostic.getSeverity(), "MEDIUM"),
                firstNonEmpty(diagnostic.getConfidence(), "MEDIUM"),
                findings,
                recommendations,
                dynamicHintsForImport(importId, firstBusinessKey(diagnostic))
        );
    }

    private AssistantAnswerResponse answerPrincipalProblem(String question, Long importId) {
        DiagnosticResult diagnostic = logAnalysisService.buildImportDiagnostic(importId);

        List<String> findings = new ArrayList<>();
        findings.add("Cause principale : " + firstNonEmpty(diagnostic.getMainCause(), "non déterminée"));
        findings.add("Étape de rupture : " + firstNonEmpty(diagnostic.getFailureStep(), "non déterminée"));
        findings.add("Gravité : " + firstNonEmpty(diagnostic.getSeverity(), "N/A"));
        findings.add("Confiance : " + firstNonEmpty(diagnostic.getConfidence(), "N/A"));

        if (diagnostic.getAffectedFields() != null && !diagnostic.getAffectedFields().isEmpty()) {
            findings.add("Champ prioritaire : " + diagnostic.getAffectedFields().get(0));
        }
        if (diagnostic.getAffectedBusinessKeys() != null && !diagnostic.getAffectedBusinessKeys().isEmpty()) {
            findings.add("BK prioritaire : " + diagnostic.getAffectedBusinessKeys().get(0));
        }

        String shortAnswer;
        if (diagnostic.getErrorCount() == 0 && "DONE".equalsIgnoreCase(safe(diagnostic.getStatus()))) {
            shortAnswer = "Je ne détecte pas de problème principal bloquant sur l'import " + importId + ".";
        } else {
            shortAnswer = "Le problème principal de l'import " + importId + " est "
                    + firstNonEmpty(
                    lowerFirst(diagnostic.getMainCause()),
                    "une anomalie encore non déterminée"
            ) + ".";
        }

        String interpretation = firstNonEmpty(diagnostic.getExecutiveSummary(), diagnostic.getImpact());

        return buildResponse(
                question,
                "IMPORT_PRINCIPAL_PROBLEM",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, safeList(diagnostic.getRecommendations())),
                firstNonEmpty(diagnostic.getSeverity(), "MEDIUM"),
                firstNonEmpty(diagnostic.getConfidence(), "MEDIUM"),
                findings,
                safeList(diagnostic.getRecommendations()),
                dynamicHintsForImport(importId, firstBusinessKey(diagnostic))
        );
    }

    private AssistantAnswerResponse answerFailureLocation(String question, Long importId) {
        DiagnosticResult diagnostic = logAnalysisService.buildImportDiagnostic(importId);

        List<String> findings = new ArrayList<>();
        findings.add("Étape de rupture probable : " + firstNonEmpty(diagnostic.getFailureStep(), "non déterminée"));
        findings.add("Cause liée : " + firstNonEmpty(diagnostic.getMainCause(), "non déterminée"));

        if (diagnostic.getFirstCriticalLogId() != null) {
            findings.add("Premier log critique : #" + diagnostic.getFirstCriticalLogId());
        }
        if (hasText(diagnostic.getFirstCriticalErrorMessage())) {
            findings.add("Premier signal d'erreur : " + diagnostic.getFirstCriticalErrorMessage());
        }

        String shortAnswer;
        if (diagnostic.getErrorCount() == 0) {
            shortAnswer = "Je ne détecte pas de point de rupture explicite pour l'import " + importId + ".";
        } else {
            shortAnswer = "L'import " + importId + " semble échouer principalement à l'étape ["
                    + firstNonEmpty(diagnostic.getFailureStep(), "UNKNOWN") + "].";
        }

        String interpretation = firstNonEmpty(
                diagnostic.getImpact(),
                diagnostic.getExecutiveSummary()
        );

        return buildResponse(
                question,
                "IMPORT_FAILURE_LOCATION",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, safeList(diagnostic.getRecommendations())),
                firstNonEmpty(diagnostic.getSeverity(), "MEDIUM"),
                firstNonEmpty(diagnostic.getConfidence(), "MEDIUM"),
                findings,
                safeList(diagnostic.getRecommendations()),
                dynamicHintsForImport(importId, firstBusinessKey(diagnostic))
        );
    }

    private AssistantAnswerResponse answerImportSuccessStatus(String question, Long importId) {
        DiagnosticResult diagnostic = logAnalysisService.buildImportDiagnostic(importId);

        List<String> findings = new ArrayList<>();
        findings.add("Statut technique : " + safe(diagnostic.getStatus()));
        findings.add("Erreurs : " + diagnostic.getErrorCount());
        findings.add("Taux d'erreur : " + diagnostic.getErrorRate() + "%");
        findings.add("Gravité : " + firstNonEmpty(diagnostic.getSeverity(), "N/A"));

        String shortAnswer = buildImportSuccessSentence(importId, diagnostic);
        String interpretation = buildImportSuccessInterpretation(diagnostic);

        return buildResponse(
                question,
                "IMPORT_SUCCESS_STATUS",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, safeList(diagnostic.getRecommendations())),
                firstNonEmpty(diagnostic.getSeverity(), "MEDIUM"),
                firstNonEmpty(diagnostic.getConfidence(), "MEDIUM"),
                findings,
                safeList(diagnostic.getRecommendations()),
                dynamicHintsForImport(importId, firstBusinessKey(diagnostic))
        );
    }

    private AssistantAnswerResponse answerImportRecommendations(String question, Long importId) {
        DiagnosticResult diagnostic = logAnalysisService.buildImportDiagnostic(importId);

        List<String> findings = new ArrayList<>();
        findings.add("Cause principale : " + firstNonEmpty(diagnostic.getMainCause(), "non déterminée"));
        findings.add("Étape de rupture : " + firstNonEmpty(diagnostic.getFailureStep(), "non déterminée"));

        if (diagnostic.getAffectedFields() != null && !diagnostic.getAffectedFields().isEmpty()) {
            findings.add("Champ ciblé : " + diagnostic.getAffectedFields().get(0));
        }
        if (diagnostic.getAffectedBusinessKeys() != null && !diagnostic.getAffectedBusinessKeys().isEmpty()) {
            findings.add("BK ciblée : " + diagnostic.getAffectedBusinessKeys().get(0));
        }

        String shortAnswer = "Voici les actions prioritaires recommandées pour l'import " + importId + ".";
        String interpretation = firstNonEmpty(
                diagnostic.getExecutiveSummary(),
                diagnostic.getImpact()
        );

        return buildResponse(
                question,
                "IMPORT_RECOMMENDATIONS",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, safeList(diagnostic.getRecommendations())),
                firstNonEmpty(diagnostic.getSeverity(), "MEDIUM"),
                firstNonEmpty(diagnostic.getConfidence(), "MEDIUM"),
                findings,
                safeList(diagnostic.getRecommendations()),
                dynamicHintsForImport(importId, firstBusinessKey(diagnostic))
        );
    }

    private AssistantAnswerResponse answerImportErrors(String question, Long importId) {
        DiagnosticResult diagnostic = logAnalysisService.buildImportDiagnostic(importId);

        if (diagnostic.getErrorCount() == 0) {
            List<String> findings = List.of("Aucune erreur explicite trouvée pour cet import.");
            List<String> recommendations = List.of(
                    "Consulter la narration complète de l'import pour confirmer le comportement réel.",
                    "Vérifier s'il existe des anomalies métier même avec peu d'erreurs explicites."
            );

            String shortAnswer = "Je ne détecte pas d'erreur explicite pour l'import " + importId + ".";
            String answer = buildStructuredAnswer(
                    shortAnswer,
                    findings,
                    "L'import ne présente pas d'erreur explicite dans les logs récupérés, mais cela ne garantit pas qu'il est parfaitement sain métier.",
                    recommendations
            );

            return buildResponse(
                    question,
                    "IMPORT_ERRORS",
                    shortAnswer,
                    answer,
                    "LOW",
                    "HIGH",
                    findings,
                    recommendations,
                    dynamicHintsForImport(importId, null)
            );
        }

        List<String> findings = new ArrayList<>();
        findings.add("Nombre total d'erreurs : " + diagnostic.getErrorCount());
        findings.add("Erreur dominante : " + firstNonEmpty(diagnostic.getMainCause(), "non déterminée"));
        findings.add("Étape de rupture : " + firstNonEmpty(diagnostic.getFailureStep(), "non déterminée"));

        if (diagnostic.getAffectedFields() != null && !diagnostic.getAffectedFields().isEmpty()) {
            findings.add("Champ le plus touché : " + diagnostic.getAffectedFields().get(0));
        }
        if (diagnostic.getAffectedBusinessKeys() != null && !diagnostic.getAffectedBusinessKeys().isEmpty()) {
            findings.add("BK la plus touchée : " + diagnostic.getAffectedBusinessKeys().get(0));
        }
        if (hasText(diagnostic.getFirstCriticalErrorMessage())) {
            findings.add("Premier signal d'erreur : " + diagnostic.getFirstCriticalErrorMessage());
        }

        String shortAnswer = "L'import " + importId + " contient "
                + diagnostic.getErrorCount() + " erreur(s), dominées par "
                + lowerFirst(firstNonEmpty(diagnostic.getMainCause(), "une anomalie non déterminée")) + ".";

        String interpretation = firstNonEmpty(
                diagnostic.getExecutiveSummary(),
                diagnostic.getImpact()
        );

        return buildResponse(
                question,
                "IMPORT_ERRORS",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, safeList(diagnostic.getRecommendations())),
                firstNonEmpty(diagnostic.getSeverity(), "MEDIUM"),
                firstNonEmpty(diagnostic.getConfidence(), "MEDIUM"),
                findings,
                safeList(diagnostic.getRecommendations()),
                dynamicHintsForImport(importId, firstBusinessKey(diagnostic))
        );
    }

    private AssistantAnswerResponse answerImportBusinessKeys(String question, Long importId) {
        List<String> businessKeys = logAnalysisService.getImportBusinessKeys(importId);
        DiagnosticResult diagnostic = logAnalysisService.buildImportDiagnostic(importId);

        List<String> findings = new ArrayList<>();
        findings.add("Nombre de business keys distinctes : " + businessKeys.size());
        if (!businessKeys.isEmpty()) {
            findings.add("Exemples : " + joinList(businessKeys, 10));
        }
        if (diagnostic.getAffectedBusinessKeys() != null && !diagnostic.getAffectedBusinessKeys().isEmpty()) {
            findings.add("BK les plus impactées : " + joinList(diagnostic.getAffectedBusinessKeys(), 5));
        }

        List<String> recommendations = new ArrayList<>();
        recommendations.add("Analyser en priorité les BK qui portent des erreurs.");
        recommendations.add("Comparer une BK en erreur avec une BK saine si possible.");
        recommendations.add("Utiliser la story d'une BK pour comprendre le déroulement complet.");

        String shortAnswer = businessKeys.isEmpty()
                ? "Je n'ai trouvé aucune business key exploitable pour l'import " + importId + "."
                : "L'import " + importId + " contient " + businessKeys.size() + " business key(s) distincte(s).";

        String interpretation = businessKeys.isEmpty()
                ? "Soit les logs de cet import ne portent pas encore de BK exploitable, soit les patterns ne permettent pas de les extraire correctement."
                : "Les business keys permettent de zoomer rapidement sur les cas métier concrets sans relire tout l'import.";

        return buildResponse(
                question,
                "IMPORT_BUSINESS_KEYS",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, recommendations),
                businessKeys.isEmpty() ? "LOW" : "MEDIUM",
                "HIGH",
                findings,
                recommendations,
                dynamicHintsForImport(importId, businessKeys.isEmpty() ? null : businessKeys.get(0))
        );
    }

    private AssistantAnswerResponse answerImportErrorBusinessKeys(String question, Long importId) {
        DiagnosticResult diagnostic = logAnalysisService.buildImportDiagnostic(importId);

        List<String> findings = new ArrayList<>();
        findings.add("Nombre de BK en erreur identifiées : " + safeSize(diagnostic.getAffectedBusinessKeys()));

        if (diagnostic.getAffectedBusinessKeys() != null && !diagnostic.getAffectedBusinessKeys().isEmpty()) {
            findings.add("BK les plus touchées : " + joinList(diagnostic.getAffectedBusinessKeys(), 10));
        }

        String shortAnswer = (diagnostic.getAffectedBusinessKeys() == null || diagnostic.getAffectedBusinessKeys().isEmpty())
                ? "Je n'ai pas trouvé de business key explicitement marquée en erreur dans l'import " + importId + "."
                : "Les BK les plus touchées dans l'import " + importId + " sont : "
                  + joinList(diagnostic.getAffectedBusinessKeys(), 5) + ".";

        String interpretation = (diagnostic.getAffectedBusinessKeys() == null || diagnostic.getAffectedBusinessKeys().isEmpty())
                ? "Les erreurs existent peut-être sans être directement rattachées à une BK extraite."
                : "La concentration des erreurs par business key permet de cibler directement les cas métier prioritaires.";

        List<String> recommendations = new ArrayList<>();
        recommendations.add("Commencer par la BK avec le plus grand nombre d'erreurs.");
        recommendations.add("Consulter l'histoire complète d'une BK impactée.");
        recommendations.add("Vérifier si plusieurs BK échouent à la même étape.");

        return buildResponse(
                question,
                "IMPORT_ERROR_BUSINESS_KEYS",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, recommendations),
                firstNonEmpty(diagnostic.getSeverity(), "MEDIUM"),
                firstNonEmpty(diagnostic.getConfidence(), "MEDIUM"),
                findings,
                recommendations,
                dynamicHintsForImport(importId, firstBusinessKey(diagnostic))
        );
    }

    private AssistantAnswerResponse answerImportStory(String question, Long importId) {
        WorkflowStoryResponse story = logAnalysisService.buildImportStory(importId);
        DiagnosticResult diagnostic = logAnalysisService.buildImportDiagnostic(importId);

        List<String> findings = new ArrayList<>();
        findings.add("Nombre total de logs : " + story.getTotalLogs());
        findings.add("Étapes importantes retenues : " + story.getTotalImportantSteps());
        findings.add("Erreurs dans la story : " + story.getTotalErrors());

        if (story.getDetectedAnomalies() != null && !story.getDetectedAnomalies().isEmpty()) {
            findings.add("Anomalies principales : " + joinList(story.getDetectedAnomalies(), 5));
        }
        if (hasText(diagnostic.getMainCause())) {
            findings.add("Cause principale probable : " + diagnostic.getMainCause());
        }

        String shortAnswer = "J'ai reconstruit le déroulement principal de l'import " + importId + ".";
        String interpretation = safe(story.getSummary()) + " "
                + firstNonEmpty(diagnostic.getExecutiveSummary(), safe(story.getConclusion()));

        List<String> recommendations = safeList(diagnostic.getRecommendations());

        return buildResponse(
                question,
                "IMPORT_STORY",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, recommendations),
                firstNonEmpty(diagnostic.getSeverity(), story.getTotalErrors() > 0 ? "HIGH" : "MEDIUM"),
                firstNonEmpty(diagnostic.getConfidence(), story.getTotalImportantSteps() > 0 ? "HIGH" : "MEDIUM"),
                findings,
                recommendations,
                dynamicHintsForImport(importId, firstBusinessKey(diagnostic))
        );
    }

    private AssistantAnswerResponse answerBusinessKeyDiagnostic(String question, String businessKey) {
        DiagnosticResult diagnostic = logAnalysisService.buildBusinessKeyDiagnostic(businessKey);

        List<String> findings = new ArrayList<>();
        findings.add("Business key : " + businessKey);
        findings.add("Logs liés : " + diagnostic.getTotalLogs());
        findings.add("Erreurs : " + diagnostic.getErrorCount());

        if (hasText(diagnostic.getMainCause())) {
            findings.add("Cause principale : " + diagnostic.getMainCause());
        }
        if (hasText(diagnostic.getFailureStep())) {
            findings.add("Étape de rupture : " + diagnostic.getFailureStep());
        }

        String shortAnswer = "Diagnostic de la business key [" + businessKey + "] : "
                + firstNonEmpty(diagnostic.getMainCause(), "aucune cause claire détectée") + ".";

        String interpretation = firstNonEmpty(
                diagnostic.getExecutiveSummary(),
                diagnostic.getImpact()
        );

        return buildResponse(
                question,
                "BUSINESS_KEY_DIAGNOSTIC",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, safeList(diagnostic.getRecommendations())),
                firstNonEmpty(diagnostic.getSeverity(), "MEDIUM"),
                firstNonEmpty(diagnostic.getConfidence(), "MEDIUM"),
                findings,
                safeList(diagnostic.getRecommendations()),
                List.of(
                        "Raconte-moi ce qui s'est passé pour la business key " + businessKey,
                        "Pourquoi la business key " + businessKey + " est en erreur ?",
                        "Quels champs sont impliqués pour cette BK ?"
                )
        );
    }

    private AssistantAnswerResponse answerBusinessKeyDiagnosticInImport(String question, Long importId, String businessKey) {
        DiagnosticResult diagnostic = logAnalysisService.buildBusinessKeyDiagnosticForImport(importId, businessKey);

        List<String> findings = new ArrayList<>();
        findings.add("Import : " + importId);
        findings.add("Business key : " + businessKey);
        findings.add("Logs liés : " + diagnostic.getTotalLogs());
        findings.add("Erreurs : " + diagnostic.getErrorCount());

        if (hasText(diagnostic.getMainCause())) {
            findings.add("Cause principale : " + diagnostic.getMainCause());
        }
        if (hasText(diagnostic.getFailureStep())) {
            findings.add("Étape de rupture : " + diagnostic.getFailureStep());
        }

        String shortAnswer = "Diagnostic de la BK [" + businessKey + "] dans l'import " + importId + " : "
                + firstNonEmpty(diagnostic.getMainCause(), "aucune cause claire détectée") + ".";

        String interpretation = firstNonEmpty(
                diagnostic.getExecutiveSummary(),
                diagnostic.getImpact()
        );

        return buildResponse(
                question,
                "IMPORT_BUSINESS_KEY_DIAGNOSTIC",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, safeList(diagnostic.getRecommendations())),
                firstNonEmpty(diagnostic.getSeverity(), "MEDIUM"),
                firstNonEmpty(diagnostic.getConfidence(), "MEDIUM"),
                findings,
                safeList(diagnostic.getRecommendations()),
                List.of(
                        "Raconte-moi ce qui s'est passé pour la business key " + businessKey + " dans l'import " + importId,
                        "Pourquoi la BK " + businessKey + " est en erreur ?",
                        "Où ça a échoué pour cette BK ?"
                )
        );
    }

    private AssistantAnswerResponse answerBusinessKeyStory(String question, String businessKey) {
        WorkflowStoryResponse story = logAnalysisService.buildBusinessKeyStory(businessKey);
        DiagnosticResult diagnostic = logAnalysisService.buildBusinessKeyDiagnostic(businessKey);

        List<String> findings = new ArrayList<>();
        findings.add("Périmètre : business key [" + businessKey + "]");
        findings.add("Nombre total de logs : " + story.getTotalLogs());
        findings.add("Étapes importantes : " + story.getTotalImportantSteps());
        findings.add("Erreurs : " + story.getTotalErrors());

        if (story.getDetectedAnomalies() != null && !story.getDetectedAnomalies().isEmpty()) {
            findings.add("Anomalies : " + joinList(story.getDetectedAnomalies(), 5));
        }
        if (hasText(diagnostic.getMainCause())) {
            findings.add("Cause principale probable : " + diagnostic.getMainCause());
        }

        String shortAnswer = "J'ai reconstruit l'histoire principale de la business key [" + businessKey + "].";
        String interpretation = safe(story.getSummary()) + " "
                + firstNonEmpty(diagnostic.getExecutiveSummary(), safe(story.getConclusion()));

        return buildResponse(
                question,
                "BUSINESS_KEY_STORY",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, safeList(diagnostic.getRecommendations())),
                firstNonEmpty(diagnostic.getSeverity(), story.getTotalErrors() > 0 ? "HIGH" : "MEDIUM"),
                firstNonEmpty(diagnostic.getConfidence(), story.getTotalLogs() > 0 ? "HIGH" : "MEDIUM"),
                findings,
                safeList(diagnostic.getRecommendations()),
                List.of(
                        "Pourquoi la business key " + businessKey + " est en erreur ?",
                        "Donne-moi le diagnostic de la business key " + businessKey,
                        "Quels champs sont impliqués pour cette BK ?"
                )
        );
    }

    private AssistantAnswerResponse answerBusinessKeyStoryInImport(String question, Long importId, String businessKey) {
        WorkflowStoryResponse story = logAnalysisService.buildBusinessKeyStoryForImport(importId, businessKey);
        DiagnosticResult diagnostic = logAnalysisService.buildBusinessKeyDiagnosticForImport(importId, businessKey);

        List<String> findings = new ArrayList<>();
        findings.add("Import : " + importId);
        findings.add("Business key : " + businessKey);
        findings.add("Nombre total de logs : " + story.getTotalLogs());
        findings.add("Étapes importantes : " + story.getTotalImportantSteps());
        findings.add("Erreurs : " + story.getTotalErrors());

        if (story.getDetectedAnomalies() != null && !story.getDetectedAnomalies().isEmpty()) {
            findings.add("Anomalies : " + joinList(story.getDetectedAnomalies(), 5));
        }
        if (hasText(diagnostic.getMainCause())) {
            findings.add("Cause principale probable : " + diagnostic.getMainCause());
        }

        String shortAnswer = "J'ai reconstruit l'histoire de la BK [" + businessKey + "] dans l'import " + importId + ".";
        String interpretation = safe(story.getSummary()) + " "
                + firstNonEmpty(diagnostic.getExecutiveSummary(), safe(story.getConclusion()));

        return buildResponse(
                question,
                "IMPORT_BUSINESS_KEY_STORY",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, safeList(diagnostic.getRecommendations())),
                firstNonEmpty(diagnostic.getSeverity(), story.getTotalErrors() > 0 ? "HIGH" : "MEDIUM"),
                firstNonEmpty(diagnostic.getConfidence(), story.getTotalLogs() > 0 ? "HIGH" : "MEDIUM"),
                findings,
                safeList(diagnostic.getRecommendations()),
                List.of(
                        "Pourquoi la BK " + businessKey + " est en erreur ?",
                        "Donne-moi le diagnostic de la BK " + businessKey + " dans l'import " + importId,
                        "Où ça a échoué pour cette BK ?"
                )
        );
    }

    private AssistantAnswerResponse answerBusinessKeyAnalysis(String question, String businessKey) {
        BusinessKeyAnalysisResponse analysis = logAnalysisService.analyzeBusinessKey(businessKey);
        DiagnosticResult diagnostic = logAnalysisService.buildBusinessKeyDiagnostic(businessKey);

        List<String> findings = new ArrayList<>();
        findings.add("BK demandée : " + businessKey);
        findings.add("Logs liés : " + analysis.getTotalRelatedLogs());

        if (analysis.getDetectedErrors() != null && !analysis.getDetectedErrors().isEmpty()) {
            findings.add("Erreurs détectées : " + joinList(analysis.getDetectedErrors(), 5));
        }

        if (analysis.getInvolvedFields() != null && !analysis.getInvolvedFields().isEmpty()) {
            findings.add("Champs impliqués : " + joinList(analysis.getInvolvedFields(), 5));
        }

        if (hasText(diagnostic.getMainCause())) {
            findings.add("Cause principale probable : " + diagnostic.getMainCause());
        }

        String shortAnswer = "Analyse de la BK [" + businessKey + "] : "
                + analysis.getTotalRelatedLogs() + " logs liés trouvés.";

        String interpretation = firstNonEmpty(
                diagnostic.getExecutiveSummary(),
                analysis.getSummary()
        );

        return buildResponse(
                question,
                "BUSINESS_KEY_ANALYSIS",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, safeList(diagnostic.getRecommendations())),
                firstNonEmpty(diagnostic.getSeverity(), analysis.getDetectedErrors() != null && !analysis.getDetectedErrors().isEmpty() ? "HIGH" : "MEDIUM"),
                firstNonEmpty(diagnostic.getConfidence(), analysis.getTotalRelatedLogs() > 0 ? "HIGH" : "MEDIUM"),
                findings,
                safeList(diagnostic.getRecommendations()),
                List.of(
                        "Raconte-moi ce qui s'est passé pour la business key " + businessKey,
                        "Donne-moi le diagnostic de la business key " + businessKey,
                        "Quels champs sont impliqués pour cette BK ?"
                )
        );
    }

    private AssistantAnswerResponse answerLogExplanation(String question, Long logId) {
        LogExplanationResponse explanation = logAnalysisService.explainLog(logId);

        List<String> findings = new ArrayList<>();
        findings.add("Niveau : " + safe(explanation.getLevel()));
        findings.add("Event type : " + safe(explanation.getEventType()));

        if (hasText(explanation.getFieldName())) findings.add("Champ : " + explanation.getFieldName());
        if (hasText(explanation.getBusinessKey())) findings.add("Business key : " + explanation.getBusinessKey());
        if (hasText(explanation.getErrorBusinessKey())) findings.add("Error business key : " + explanation.getErrorBusinessKey());
        if (hasText(explanation.getSourceClass())) findings.add("Source class : " + explanation.getSourceClass());

        List<String> recommendations = new ArrayList<>();
        recommendations.add("Relire les logs voisins de ce log pour replacer l'événement dans la séquence.");
        recommendations.add("Analyser l'import ou la BK associée si ce log est critique.");
        recommendations.add("Vérifier si ce log appartient à une erreur dominante ou isolée.");

        String shortAnswer = "Le log " + logId + " signifie : " + safe(explanation.getHumanExplanation());

        String interpretation = "Message original : " + safe(explanation.getOriginalMessage());

        return buildResponse(
                question,
                "LOG_EXPLANATION",
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, interpretation, recommendations),
                "MEDIUM",
                "HIGH",
                findings,
                recommendations,
                List.of(
                        "Quel est le problème principal de l'import 7 ?",
                        "Donne-moi le diagnostic de l'import 7",
                        "Où ça a échoué dans l'import 7 ?"
                )
        );
    }

    private AssistantAnswerResponse buildResponse(String question,
                                                  String intent,
                                                  String shortAnswer,
                                                  String answer,
                                                  String severity,
                                                  String confidence,
                                                  List<String> findings,
                                                  List<String> recommendations,
                                                  List<String> hints) {
        AssistantAnswerResponse response = new AssistantAnswerResponse();
        response.setQuestion(question);
        response.setDetectedIntent(intent);
        response.setShortAnswer(shortAnswer);
        response.setAnswer(answer);
        response.setSeverity(severity);
        response.setConfidence(confidence);
        response.setFindings(deduplicate(findings));
        response.setRecommendations(deduplicate(recommendations));
        response.setHints(deduplicate(hints));
        return response;
    }

    private AssistantAnswerResponse buildSimpleResponse(String question,
                                                        String intent,
                                                        String shortAnswer,
                                                        String severity,
                                                        String confidence,
                                                        List<String> findings,
                                                        List<String> recommendations,
                                                        List<String> hints) {
        return buildResponse(
                question,
                intent,
                shortAnswer,
                buildStructuredAnswer(shortAnswer, findings, "", recommendations),
                severity,
                confidence,
                findings,
                recommendations,
                hints
        );
    }

    private String buildStructuredAnswer(String shortAnswer,
                                         List<String> findings,
                                         String interpretation,
                                         List<String> recommendations) {
        StringBuilder sb = new StringBuilder();

        sb.append("Réponse directe :\n");
        sb.append(shortAnswer).append("\n\n");

        if (findings != null && !findings.isEmpty()) {
            sb.append("Constats clés :\n");
            for (String finding : findings) {
                sb.append("- ").append(finding).append("\n");
            }
            sb.append("\n");
        }

        if (hasText(interpretation)) {
            sb.append("Interprétation :\n");
            sb.append(interpretation).append("\n\n");
        }

        if (recommendations != null && !recommendations.isEmpty()) {
            sb.append("Actions recommandées :\n");
            for (String recommendation : recommendations) {
                sb.append("- ").append(recommendation).append("\n");
            }
        }

        return sb.toString().trim();
    }

    private String buildImportSummarySentence(ImportSummaryResponse summary, DiagnosticResult diagnostic) {
        if ("FAILED".equalsIgnoreCase(safe(summary.getStatus()))) {
            return "statut FAILED, avec comme cause dominante probable : "
                    + lowerFirst(firstNonEmpty(diagnostic.getMainCause(), "une anomalie non déterminée"));
        }

        if ("RUNNING".equalsIgnoreCase(safe(summary.getStatus()))) {
            return "statut RUNNING, analyse encore provisoire";
        }

        if ("DONE".equalsIgnoreCase(safe(summary.getStatus())) && diagnostic.getErrorCount() == 0) {
            return "terminé sans erreur explicite dominante";
        }

        if ("DONE".equalsIgnoreCase(safe(summary.getStatus())) && diagnostic.getErrorCount() > 0) {
            return "terminé techniquement, mais dégradé par "
                    + lowerFirst(firstNonEmpty(diagnostic.getMainCause(), "des erreurs métier"));
        }

        return "statut " + safe(summary.getStatus());
    }

    private String buildImportSuccessSentence(Long importId, DiagnosticResult diagnostic) {
        if ("FAILED".equalsIgnoreCase(safe(diagnostic.getStatus()))) {
            return "Non, l'import " + importId + " n'est pas réussi : son statut est FAILED.";
        }
        if ("RUNNING".equalsIgnoreCase(safe(diagnostic.getStatus()))) {
            return "L'import " + importId + " est encore en cours, donc on ne peut pas conclure définitivement.";
        }
        if ("DONE".equalsIgnoreCase(safe(diagnostic.getStatus())) && diagnostic.getErrorCount() == 0) {
            return "Oui, l'import " + importId + " semble réussi, sans erreur explicite dominante.";
        }
        if ("DONE".equalsIgnoreCase(safe(diagnostic.getStatus())) && diagnostic.getErrorCount() > 0) {
            return "L'import " + importId + " est terminé techniquement, mais il n'est pas entièrement sain métier.";
        }
        return "Le statut de l'import " + importId + " est [" + safe(diagnostic.getStatus()) + "].";
    }

    private String buildImportSuccessInterpretation(DiagnosticResult diagnostic) {
        if ("FAILED".equalsIgnoreCase(safe(diagnostic.getStatus()))) {
            return firstNonEmpty(
                    diagnostic.getExecutiveSummary(),
                    "Le traitement a échoué techniquement. Il faut investiguer la cause dominante avant toute réexécution."
            );
        }
        if ("RUNNING".equalsIgnoreCase(safe(diagnostic.getStatus()))) {
            return "Le traitement n'est pas terminé. Une erreur peut encore apparaître ou se confirmer plus tard.";
        }
        if ("DONE".equalsIgnoreCase(safe(diagnostic.getStatus())) && diagnostic.getErrorCount() == 0) {
            return "L'import paraît propre à la fois techniquement et au niveau des erreurs explicites récupérées.";
        }
        if ("DONE".equalsIgnoreCase(safe(diagnostic.getStatus())) && diagnostic.getErrorCount() > 0) {
            return firstNonEmpty(
                    diagnostic.getExecutiveSummary(),
                    "Le traitement s'est bien terminé sur le plan technique, mais des défauts métier ou applicatifs subsistent dans les logs."
            );
        }
        return "Le statut existe, mais nécessite une lecture plus détaillée du contexte.";
    }

    private List<String> dynamicHintsForImport(Long importId, String dominantBk) {
        List<String> hints = new ArrayList<>();
        hints.add("Quel est le problème principal de l'import " + importId + " ?");
        hints.add("Où ça a échoué dans l'import " + importId + " ?");
        hints.add("Est-ce que l'import " + importId + " est réussi ?");
        hints.add("Quelles sont les erreurs principales de l'import " + importId + " ?");
        hints.add("Donne-moi le diagnostic de l'import " + importId);
        hints.add("Quelles actions recommandes-tu pour l'import " + importId + " ?");
        hints.add("Raconte-moi ce qui s'est passé pour l'import " + importId);

        if (hasText(dominantBk)) {
            hints.add("Raconte-moi ce qui s'est passé pour la business key " + dominantBk + " dans l'import " + importId);
            hints.add("Donne-moi le diagnostic de la business key " + dominantBk + " dans l'import " + importId);
        }

        return deduplicate(hints);
    }

    private String firstBusinessKey(DiagnosticResult diagnostic) {
        if (diagnostic == null || diagnostic.getAffectedBusinessKeys() == null || diagnostic.getAffectedBusinessKeys().isEmpty()) {
            return null;
        }
        return diagnostic.getAffectedBusinessKeys().get(0);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? new ArrayList<>() : values;
    }

    private int safeSize(List<String> values) {
        return values == null ? 0 : values.size();
    }

    private String topKey(java.util.Map<String, Long> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        return map.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private String lowerFirst(String text) {
        if (!hasText(text)) {
            return "";
        }
        return text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
    }

    private String firstNonEmpty(String... values) {
        return Arrays.stream(values)
                .filter(this::hasText)
                .findFirst()
                .orElse("");
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        String lowered = text.trim().toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(lowered, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private boolean isGlobalSummaryQuestion(String normalized) {
        return containsAny(normalized,
                "dashboard",
                "resume global",
                "résumé global",
                "vue globale",
                "global",
                "etat global",
                "état global");
    }

    private boolean isLogExplanationQuestion(String normalized) {
        return containsAny(normalized,
                "explique le log",
                "expliquer le log",
                "explique moi le log",
                "explication du log",
                "que veut dire ce log",
                "log ");
    }

    private boolean isPrincipalProblemQuestion(String normalized) {
        return containsAny(normalized,
                "probleme principal",
                "problème principal",
                "cause principale",
                "cause racine",
                "pourquoi ca a echoue",
                "pourquoi ça a échoué",
                "main problem",
                "root cause");
    }

    private boolean isFailureLocationQuestion(String normalized) {
        return containsAny(normalized,
                "ou ca a echoue",
                "où ça a échoué",
                "a quelle etape",
                "à quelle étape",
                "etape de rupture",
                "étape de rupture",
                "phase d'echec",
                "phase d'échec");
    }

    private boolean isImportSuccessQuestion(String normalized) {
        return containsAny(normalized,
                "est ce que l'import est reussi",
                "est ce que l'import est réussi",
                "import reussi",
                "import réussi",
                "statut de l'import",
                "status de l'import",
                "import a t il reussi",
                "import a-t-il réussi");
    }

    private boolean isRecommendationsQuestion(String normalized) {
        return containsAny(normalized,
                "que dois je faire",
                "que dois-je faire",
                "que recommandes tu",
                "que recommandes-tu",
                "actions recommandees",
                "actions recommandées",
                "quoi verifier",
                "quoi vérifier",
                "quoi faire maintenant",
                "next step",
                "prochaines actions");
    }

    private boolean isDiagnosticQuestion(String normalized) {
        return containsAny(normalized,
                "diagnostic",
                "diagnostiquer",
                "donne moi le diagnostic",
                "donne-moi le diagnostic",
                "analyse complete",
                "analyse complète",
                "vue diagnostique",
                "resume diagnostique",
                "résumé diagnostique");
    }

    private boolean isErrorBusinessKeysQuestion(String normalized) {
        return containsAny(normalized,
                "quelle bk est en erreur",
                "quelles bk sont en erreur",
                "quelle business key est en erreur",
                "quelles business keys sont en erreur",
                "bk en erreur",
                "business key en erreur",
                "business keys en erreur");
    }

    private boolean isImportErrorsQuestion(String normalized) {
        return containsAny(normalized,
                "erreurs principales",
                "erreurs import",
                "quelles sont les erreurs",
                "montre les erreurs",
                "liste les erreurs");
    }

    private boolean isImportBusinessKeysQuestion(String normalized) {
        return containsAny(normalized,
                "quelles sont les business keys",
                "liste les business keys",
                "lister les business keys",
                "quelles sont les bk",
                "liste les bk",
                "montre les business keys");
    }

    private boolean isStoryQuestion(String normalized) {
        return containsAny(normalized,
                "raconte",
                "story",
                "ce qui s'est passe",
                "ce qui s’est passé",
                "que s'est il passe",
                "que s’est il passé",
                "chronologie",
                "narration");
    }

    private boolean isBusinessKeyAnalysisQuestion(String normalized) {
        return containsAny(normalized,
                "business key",
                "bk",
                "pourquoi cette bk",
                "pourquoi cette business key");
    }

    private boolean isImportSummaryQuestion(String normalized) {
        return containsAny(normalized,
                "resume import",
                "résumé import",
                "explique import",
                "analyse import",
                "details import",
                "détails import");
    }

    private Long extractImportId(String question) {
        Matcher matcher = IMPORT_ID_PATTERN.matcher(question);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return null;
    }

    private Long extractLogId(String question) {
        Matcher matcher = LOG_ID_PATTERN.matcher(question);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return null;
    }

    private String extractBusinessKey(String question) {
        Matcher matcher = BUSINESS_KEY_PATTERN.matcher(question);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private List<String> defaultHints() {
        return List.of(
                "Quel est le problème principal de l'import 7 ?",
                "Où ça a échoué dans l'import 7 ?",
                "Est-ce que l'import 7 est réussi ?",
                "Quelle BK est en erreur dans l'import 7 ?",
                "Donne-moi le diagnostic de l'import 7",
                "Raconte-moi ce qui s'est passé pour l'import 7",
                "Explique le log 82"
        );
    }

    private String joinList(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return "aucune donnée";
        }

        return values.stream()
                .filter(this::hasText)
                .limit(limit)
                .collect(Collectors.joining(" | "));
    }

    private List<String> deduplicate(List<String> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(
                items.stream()
                        .filter(this::hasText)
                        .map(String::trim)
                        .collect(Collectors.toList())
        ));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeInt(Integer value) {
        return value == null ? "0" : String.valueOf(value);
    }

    private String computeSeverity(long totalLogs, long totalErrors) {
        if (totalErrors <= 0) {
            return "LOW";
        }

        if (totalLogs <= 0) {
            return totalErrors >= 10 ? "HIGH" : "MEDIUM";
        }

        double rate = ((double) totalErrors / totalLogs) * 100.0;

        if (rate >= 40.0) return "CRITICAL";
        if (rate >= 20.0) return "HIGH";
        if (rate >= 5.0) return "MEDIUM";
        return "LOW";
    }
}