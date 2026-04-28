package com.caciopee.loganalyzer.analysis;

import com.caciopee.loganalyzer.analysis.dto.WorkflowAnalysisDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowAnalysisResponseDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowStepDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowStoryItemDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowStoryResponseDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class WorkflowStoryServiceImpl implements WorkflowStoryService {

    private final WorkflowAnalyzerService workflowAnalyzerService;

    public WorkflowStoryServiceImpl(WorkflowAnalyzerService workflowAnalyzerService) {
        this.workflowAnalyzerService = workflowAnalyzerService;
    }

    @Override
    public WorkflowStoryResponseDto buildImportStory(Long importId) {
        WorkflowAnalysisResponseDto analysis = workflowAnalyzerService.analyzeImport(importId);

        WorkflowStoryResponseDto response = new WorkflowStoryResponseDto();
        response.setImportId(importId);

        List<WorkflowStoryItemDto> items = new ArrayList<>();
        for (WorkflowAnalysisDto workflow : analysis.getWorkflows()) {
            WorkflowStoryItemDto item = new WorkflowStoryItemDto();
            item.setSegmentKey(workflow.getWorkflowKey());
            item.setSeverityScore(workflow.getSeverityScore());
            item.setStatus(workflow.getStatus() != null ? workflow.getStatus().name() : null);
            item.setNature(workflow.getNature() != null ? workflow.getNature().name() : null);
            item.setTitle(buildTitle(workflow));
            item.setStory(buildStory(workflow));
            items.add(item);
        }

        response.setItems(items);
        response.setGlobalStory(buildGlobalStory(analysis));

        return response;
    }

    private String buildTitle(WorkflowAnalysisDto workflow) {
        return "Segment " + workflow.getWorkflowKey()
                + " - " + safe(workflow.getNature())
                + " - " + safe(workflow.getStatus());
    }

    private String buildStory(WorkflowAnalysisDto workflow) {
        List<String> lines = new ArrayList<>();

        lines.add("Ce segment contient " + safeInt(workflow.getTotalLogs()) + " logs.");
        if (workflow.getProcessName() != null) {
            lines.add("Process principal : " + workflow.getProcessName() + ".");
        }
        if (workflow.getDominantFilterCode() != null) {
            lines.add("Filtre dominant : " + workflow.getDominantFilterCode() + ".");
        }
        if (workflow.getDurationMs() != null) {
            lines.add("Durée estimée : " + workflow.getDurationMs() + " ms.");
        }
        if (workflow.getMaxMemoryMo() != null) {
            lines.add("Mémoire maximale observée : " + workflow.getMaxMemoryMo() + " Mo.");
        }

        List<WorkflowStepDto> importantSteps = workflow.getSteps().stream()
                .filter(this::isNarrativelyUseful)
                .limit(8)
                .collect(Collectors.toList());

        if (!importantSteps.isEmpty()) {
            lines.add("Étapes importantes détectées :");
            for (WorkflowStepDto step : importantSteps) {
                lines.add("- " + buildStepSentence(step));
            }
        }

        if (workflow.isHasNoRuleIssue()) {
            lines.add("Une anomalie a été détectée : aucune règle applicable n'a été trouvée.");
        }
        if (workflow.isHasZeroRowIssue()) {
            lines.add("Le segment contient une recherche sans résultat.");
        }
        if (workflow.isHasPerformanceIssue()) {
            lines.add("Le segment présente un signal de performance dégradée.");
        }
        if (workflow.isHasErrors()) {
            lines.add("Le segment contient au moins une erreur technique ou métier.");
        }

        return String.join(" ", lines);
    }

    private boolean isNarrativelyUseful(WorkflowStepDto step) {
        if (step == null) return false;
        if (step.getStepType() == null) return false;

        return step.getBusinessMeaning() != null
                || step.getStepType() == WorkflowStepType.ERROR
                || step.getStepType() == WorkflowStepType.WARNING
                || step.getStepType() == WorkflowStepType.QUERY_EXECUTION
                || step.getStepType() == WorkflowStepType.FIELD_MAPPING
                || step.getStepType() == WorkflowStepType.FIELD_INSERTED
                || step.getStepType() == WorkflowStepType.RELATION_ATTACHED
                || step.getStepType() == WorkflowStepType.MANDATORY_CHECK_ITEM
                || step.getStepType() == WorkflowStepType.CHECKPOINT;
    }

    private String buildStepSentence(WorkflowStepDto step) {
        if (step.getBusinessMeaning() != null && !step.getBusinessMeaning().isBlank()) {
            StringBuilder sb = new StringBuilder(step.getBusinessMeaning());

            if (step.getFieldName() != null) {
                sb.append(" Champ=").append(step.getFieldName()).append(".");
            } else if (step.getMandatoryField() != null) {
                sb.append(" Champ obligatoire=").append(step.getMandatoryField()).append(".");
            } else if (step.getRelationName() != null) {
                sb.append(" Relation=").append(step.getRelationName()).append(".");
            } else if (step.getBusinessKey() != null) {
                sb.append(" BK=").append(step.getBusinessKey()).append(".");
            } else if (step.getErrorAttribute() != null) {
                sb.append(" Attribut en erreur=").append(step.getErrorAttribute()).append(".");
            }

            return sb.toString();
        }

        return "Étape " + safe(step.getStepType()) + ".";
    }

    private String buildGlobalStory(WorkflowAnalysisResponseDto analysis) {
        return "L'import contient "
                + safeInt(analysis.getTotalWorkflows())
                + " segments analysés, dont "
                + safeInt(analysis.getWorkflowsWithErrors())
                + " avec erreurs, "
                + safeInt(analysis.getWorkflowsWithWarnings())
                + " avec warnings, et "
                + safeInt(analysis.getWorkflowsWithPerformanceIssues())
                + " avec problème de performance.";
    }

    private String safe(Object value) {
        return value == null ? "UNKNOWN" : value.toString();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}