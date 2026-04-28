package com.caciopee.loganalyzer.analysis;

import com.caciopee.loganalyzer.analysis.dto.UserFriendlyAnalysisDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowAnalysisDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowAnalysisResponseDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserFriendlyAnalysisServiceImpl implements UserFriendlyAnalysisService {

    private final WorkflowAnalyzerService workflowAnalyzerService;

    public UserFriendlyAnalysisServiceImpl(WorkflowAnalyzerService workflowAnalyzerService) {
        this.workflowAnalyzerService = workflowAnalyzerService;
    }

    @Override
    public UserFriendlyAnalysisDto explainImportForHuman(Long importId) {
        WorkflowAnalysisResponseDto analysis = workflowAnalyzerService.analyzeImport(importId);

        UserFriendlyAnalysisDto dto = new UserFriendlyAnalysisDto();
        dto.setImportId(importId);
        dto.setTotalSegments(analysis.getTotalWorkflows());
        dto.setErrorSegments(analysis.getWorkflowsWithErrors());
        dto.setWarningSegments(analysis.getWorkflowsWithWarnings());
        dto.setPerformanceSegments(analysis.getWorkflowsWithPerformanceIssues());

        dto.setOverallStatus(buildOverallStatus(analysis));
        dto.setGlobalExplanation(buildGlobalExplanation(analysis));
        dto.setKeyConclusion(buildKeyConclusion(analysis));

        List<UserFriendlyAnalysisDto.UserFriendlySegmentDto> segments = new ArrayList<>();
        for (WorkflowAnalysisDto workflow : analysis.getWorkflows()) {
            segments.add(toHumanSegment(workflow));
        }
        dto.setSegments(segments);

        return dto;
    }

    private UserFriendlyAnalysisDto.UserFriendlySegmentDto toHumanSegment(WorkflowAnalysisDto workflow) {
        UserFriendlyAnalysisDto.UserFriendlySegmentDto dto =
                new UserFriendlyAnalysisDto.UserFriendlySegmentDto();

        dto.setSegmentKey(workflow.getWorkflowKey());
        dto.setTitle(buildTitle(workflow));
        dto.setNature(workflow.getNature() != null ? workflow.getNature().name() : "UNKNOWN");
        dto.setStatus(workflow.getStatus() != null ? workflow.getStatus().name() : "UNKNOWN");

        dto.setSimpleExplanation(buildSimpleExplanation(workflow));
        dto.setProbableCause(buildProbableCause(workflow));
        dto.setBusinessImpact(buildBusinessImpact(workflow));
        dto.setRecommendation(buildRecommendation(workflow));
        dto.setHighlights(buildHighlights(workflow));

        return dto;
    }

    private String buildOverallStatus(WorkflowAnalysisResponseDto analysis) {
        if (safeInt(analysis.getWorkflowsWithErrors()) > 0) {
            return "CRITIQUE";
        }
        if (safeInt(analysis.getWorkflowsWithWarnings()) > 0) {
            return "A_SURVEILLER";
        }
        return "SAIN";
    }

    private String buildGlobalExplanation(WorkflowAnalysisResponseDto analysis) {
        int total = safeInt(analysis.getTotalWorkflows());
        int errors = safeInt(analysis.getWorkflowsWithErrors());
        int warnings = safeInt(analysis.getWorkflowsWithWarnings());
        int perf = safeInt(analysis.getWorkflowsWithPerformanceIssues());

        if (total == 0) {
            return "Aucun segment exploitable n’a été détecté dans cet import.";
        }

        if (errors > 0) {
            return "L’import contient plusieurs segments analysés, et au moins un problème important a été détecté. Une vérification humaine est recommandée.";
        }

        if (warnings > 0 || perf > 0) {
            return "L’import a été analysé avec succès, mais certains segments présentent des signaux d’attention, comme des avertissements ou des lenteurs.";
        }

        return "L’import semble globalement cohérent. Aucun problème majeur n’a été détecté dans les segments analysés.";
    }

    private String buildKeyConclusion(WorkflowAnalysisResponseDto analysis) {
        int errors = safeInt(analysis.getWorkflowsWithErrors());
        int warnings = safeInt(analysis.getWorkflowsWithWarnings());
        int perf = safeInt(analysis.getWorkflowsWithPerformanceIssues());

        if (errors > 0) {
            return "Conclusion principale : l’import contient au moins un segment en erreur.";
        }
        if (warnings > 0) {
            return "Conclusion principale : l’import est exploitable, mais contient des anomalies ou incohérences à surveiller.";
        }
        if (perf > 0) {
            return "Conclusion principale : l’import est techniquement correct, mais certaines opérations semblent lentes.";
        }
        return "Conclusion principale : l’import paraît sain et lisible.";
    }

    private String buildTitle(WorkflowAnalysisDto workflow) {
        String nature = workflow.getNature() != null ? workflow.getNature().name() : "UNKNOWN";
        return "Segment " + workflow.getWorkflowKey() + " - " + nature;
    }

    private String buildSimpleExplanation(WorkflowAnalysisDto workflow) {
        if (workflow.isHasErrors()) {
            return "Ce segment contient une erreur technique ou métier. Le traitement ne s’est probablement pas déroulé comme prévu.";
        }

        if (workflow.isHasNoRuleIssue()) {
            return "Ce segment montre que le système a essayé d’appliquer des règles, mais aucune règle adaptée n’a été trouvée.";
        }

        if (workflow.isHasZeroRowIssue()) {
            return "Ce segment correspond à une recherche qui n’a retourné aucun résultat.";
        }

        if (workflow.isHasPerformanceIssue()) {
            return "Ce segment s’est exécuté, mais avec des signes de lenteur ou de consommation importante.";
        }

        if (workflow.getQueryCount() != null && workflow.getQueryCount() > 0) {
            return "Ce segment semble correspondre à une opération de recherche ou de lecture de données.";
        }

        if (workflow.getSaveCount() != null && workflow.getSaveCount() > 0) {
            return "Ce segment semble correspondre à une opération d’enregistrement ou de sauvegarde.";
        }

        return "Ce segment semble correspondre à un traitement normal, sans anomalie majeure visible.";
    }

    private String buildProbableCause(WorkflowAnalysisDto workflow) {
        if (workflow.isHasNoRuleIssue()) {
            return "Cause probable : configuration de règles incomplète ou contexte d’exécution non couvert par les règles métier.";
        }

        if (workflow.isHasZeroRowIssue()) {
            return "Cause probable : les données recherchées n’existent pas, ou les critères de recherche ne correspondent à aucun enregistrement.";
        }

        if (workflow.isHasPerformanceIssue()) {
            return "Cause probable : volume important, requête lente, ou traitement coûteux en mémoire.";
        }

        if (workflow.isHasErrors()) {
            return "Cause probable : erreur métier, donnée invalide, ou étape de transformation incomplète.";
        }

        return "Cause probable : aucune cause anormale claire n’a été détectée.";
    }

    private String buildBusinessImpact(WorkflowAnalysisDto workflow) {
        if (workflow.isHasErrors()) {
            return "Impact possible : le workflow métier associé peut être incomplet, bloqué ou produire un résultat incorrect.";
        }

        if (workflow.isHasZeroRowIssue()) {
            return "Impact possible : l’utilisateur final peut ne voir aucun résultat, même si une recherche a bien été lancée.";
        }

        if (workflow.isHasNoRuleIssue()) {
            return "Impact possible : une partie du traitement attendu peut ne jamais être exécutée.";
        }

        if (workflow.isHasPerformanceIssue()) {
            return "Impact possible : temps de réponse plus long et expérience utilisateur dégradée.";
        }

        return "Impact possible : aucun impact majeur évident.";
    }

    private String buildRecommendation(WorkflowAnalysisDto workflow) {
        if (workflow.isHasErrors()) {
            return "Recommandation : examiner les erreurs métier, les attributs en cause et les données d’entrée associées.";
        }

        if (workflow.isHasNoRuleIssue()) {
            return "Recommandation : vérifier la configuration des règles et les conditions d’activation du workflow.";
        }

        if (workflow.isHasZeroRowIssue()) {
            return "Recommandation : vérifier les critères de recherche et confirmer que les données attendues existent réellement.";
        }

        if (workflow.isHasPerformanceIssue()) {
            return "Recommandation : vérifier la requête, les index, et le volume de données traité.";
        }

        return "Recommandation : aucune action urgente. Ce segment peut être conservé comme comportement normal de référence.";
    }

    private List<String> buildHighlights(WorkflowAnalysisDto workflow) {
        List<String> highlights = new ArrayList<>();

        if (workflow.getNature() != null) {
            highlights.add("Type de segment : " + workflow.getNature().name());
        }

        if (workflow.getDurationMs() != null) {
            highlights.add("Durée estimée : " + workflow.getDurationMs() + " ms");
        }

        if (workflow.getMaxMemoryMo() != null) {
            highlights.add("Mémoire maximale observée : " + workflow.getMaxMemoryMo() + " Mo");
        }

        if (workflow.getProcessName() != null && !workflow.getProcessName().isBlank()) {
            highlights.add("Process principal : " + workflow.getProcessName());
        }

        if (workflow.getDominantFilterCode() != null && !workflow.getDominantFilterCode().isBlank()) {
            highlights.add("Filtre dominant : " + workflow.getDominantFilterCode());
        }

        if (workflow.getErrorCount() != null && workflow.getErrorCount() > 0) {
            highlights.add("Nombre d’erreurs : " + workflow.getErrorCount());
        }

        if (workflow.getWarningCount() != null && workflow.getWarningCount() > 0) {
            highlights.add("Nombre d’avertissements : " + workflow.getWarningCount());
        }

        if (workflow.getQueryCount() != null && workflow.getQueryCount() > 0) {
            highlights.add("Étapes de recherche détectées : " + workflow.getQueryCount());
        }

        if (workflow.getSaveCount() != null && workflow.getSaveCount() > 0) {
            highlights.add("Étapes de sauvegarde détectées : " + workflow.getSaveCount());
        }

        return highlights;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}