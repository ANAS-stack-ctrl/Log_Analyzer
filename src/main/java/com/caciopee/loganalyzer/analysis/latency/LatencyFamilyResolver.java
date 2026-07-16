package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;
import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;

import java.util.Locale;

/**
 * Affine le verdict générique (SQL / règles / …) vers la famille métier réelle
 * présente dans le dataset WORKS (WS, impression, AutoStart, SAVE, etc.).
 */
public final class LatencyFamilyResolver {

    private LatencyFamilyResolver() {
    }

    public static LatencyBottleneckAnalyzer.NarrativeKind refine(
            LatencyBottleneckAnalyzer.NarrativeKind base,
            WorksLatencyFacts facts,
            LatencyTimelineStepDto primary,
            LatencyOriginReportDto report) {

        if (base == null) {
            return LatencyBottleneckAnalyzer.NarrativeKind.COMPLEX;
        }

        String ctx = contextBlob(facts, primary, report);

        // Familles métier prioritaires (même si le goulot technique est RULES_ENGINE).
        if (looksLikeAutoStart(ctx)) {
            return LatencyBottleneckAnalyzer.NarrativeKind.AUTOSTART_DOMINATED;
        }
        if (looksLikeWebService(ctx)) {
            return LatencyBottleneckAnalyzer.NarrativeKind.WS_DOMINATED;
        }
        // Ne pas étiqueter « Impression/PDF » si le goulot mesuré est un chargement de données
        // (ex. process AMPE-Imprimer mais took = loadOperationById / loadListChilds).
        if (looksLikePrint(ctx, facts, primary) && !isDataLoadBottleneck(primary)) {
            return LatencyBottleneckAnalyzer.NarrativeKind.PRINT_DOMINATED;
        }

        if (base == LatencyBottleneckAnalyzer.NarrativeKind.OPERATION_LOAD_DOMINATED) {
            return LatencyBottleneckAnalyzer.NarrativeKind.OPERATION_LOAD_DOMINATED;
        }

        // COMPLEX multi-contributeurs : ne pas le reclasse en SQL/RULES « mono-goulot ».
        if (base == LatencyBottleneckAnalyzer.NarrativeKind.COMPLEX) {
            return LatencyBottleneckAnalyzer.NarrativeKind.COMPLEX;
        }

        if (base == LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED
                || base == LatencyBottleneckAnalyzer.NarrativeKind.SQL_DOMINATED
                || base == LatencyBottleneckAnalyzer.NarrativeKind.PARALLEL_LOAD_DOMINATED
                || base == LatencyBottleneckAnalyzer.NarrativeKind.RENDERING_DOMINATED) {
            return base;
        }

        // SAVE / validation dominante (pas masquée par des règles plus longues).
        if (looksLikeSaveDominated(facts, primary, base)) {
            return LatencyBottleneckAnalyzer.NarrativeKind.SAVE_DOMINATED;
        }

        // Transit / doAction + running rules chronométrées → règles, pas enveloppe workflow.
        if (rulesDominateWorkflowEnvelope(facts, primary)) {
            return LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED;
        }

        if (looksLikeWorkflow(ctx, primary) && !hasRulesEvidence(ctx, facts)) {
            return LatencyBottleneckAnalyzer.NarrativeKind.WORKFLOW_DOMINATED;
        }

        // Dernier filet : reclasse COMPLEX/SINGLE_STEP selon le type d'étape goulot.
        if (primary != null) {
            String type = primary.getStepType();
            if (LatencyOperationClassifier.RULES_ENGINE.equals(type)) {
                return LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED;
            }
            if (LatencyOperationClassifier.ROOT_QUERY.equals(type)) {
                return LatencyBottleneckAnalyzer.NarrativeKind.SQL_DOMINATED;
            }
            if (LatencyOperationClassifier.OPERATION_LOAD.equals(type)
                    || LatencyOperationClassifier.CHILD_LOAD.equals(type)
                    || LatencyOperationClassifier.CHILD_QUERY.equals(type)
                    || LatencyOperationClassifier.CHILD_LOAD_B2.equals(type)) {
                return LatencyBottleneckAnalyzer.NarrativeKind.OPERATION_LOAD_DOMINATED;
            }
            if (LatencyOperationClassifier.GLOBAL_CONTAINER.equals(type)) {
                return LatencyBottleneckAnalyzer.NarrativeKind.PARALLEL_LOAD_DOMINATED;
            }
            if (LatencyOperationClassifier.RENDERING.equals(type)) {
                return LatencyBottleneckAnalyzer.NarrativeKind.RENDERING_DOMINATED;
            }
            if (LatencyOperationClassifier.SAVE_PERSIST.equals(type)
                    || LatencyOperationClassifier.VALIDATION.equals(type)) {
                return LatencyBottleneckAnalyzer.NarrativeKind.SAVE_DOMINATED;
            }
            if (LatencyOperationClassifier.WEBSERVICE.equals(type)) {
                return LatencyBottleneckAnalyzer.NarrativeKind.WS_DOMINATED;
            }
            if (LatencyOperationClassifier.PRINT.equals(type)) {
                return LatencyBottleneckAnalyzer.NarrativeKind.PRINT_DOMINATED;
            }
            if (LatencyOperationClassifier.AUTOSTART.equals(type)) {
                return LatencyBottleneckAnalyzer.NarrativeKind.AUTOSTART_DOMINATED;
            }
            if (LatencyOperationClassifier.BUSINESS_ACTION.equals(type)) {
                // doAction matchall → règles seulement si traces règles / MAJ, sinon enveloppe d'action.
                if (hasRulesEvidence(ctx, facts) || isMatchallEnvelope(ctx, facts)) {
                    return LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED;
                }
                return LatencyBottleneckAnalyzer.NarrativeKind.WORKFLOW_DOMINATED;
            }
            if (LatencyOperationClassifier.WORKFLOW.equals(type)) {
                // Transit seul (sans took running/fire rules) → workflow opaque.
                // Avec preuves chronométrées de règles → déjà traité plus haut.
                return LatencyBottleneckAnalyzer.NarrativeKind.WORKFLOW_DOMINATED;
            }
        }

        if (looksLikeWorkflow(ctx, primary)) {
            return LatencyBottleneckAnalyzer.NarrativeKind.WORKFLOW_DOMINATED;
        }

        return base;
    }

    /**
     * Si les règles mesurées couvrent ≥ 70 % de Transit / doAction, la famille
     * réelle est RULES — l'enveloppe workflow n'est pas la cause.
     */
    private static boolean rulesDominateWorkflowEnvelope(WorksLatencyFacts facts,
                                                         LatencyTimelineStepDto primary) {
        if (facts == null || facts.getRulesEngineMs() == null || facts.getRulesEngineMs() < 2000) {
            return false;
        }
        long rulesMs = facts.getRulesEngineMs();
        if (primary != null && LatencyOperationClassifier.RULES_ENGINE.equals(primary.getStepType())) {
            return true;
        }
        if (primary != null && primary.getDurationMs() != null && primary.getDurationMs() > 0) {
            String type = primary.getStepType();
            String op = safe(primary.getOperationName()).toLowerCase(Locale.ROOT);
            boolean envelope = LatencyOperationClassifier.WORKFLOW.equals(type)
                    || LatencyOperationClassifier.BUSINESS_ACTION.equals(type)
                    || op.contains("transit") || op.contains("doaction");
            if (envelope) {
                return rulesMs >= primary.getDurationMs() * 0.7;
            }
        }
        return false;
    }

    private static boolean hasRulesEvidence(String ctx, WorksLatencyFacts facts) {
        if (ctx.contains("fire rules") || ctx.contains("running rules") || ctx.contains("maj_insert")) {
            return true;
        }
        return facts != null && (facts.getMajInsertTotal() > 0
                || (facts.getRulesEngineMs() != null && facts.getRulesEngineMs() >= 1000));
    }

    private static boolean isMatchallEnvelope(String ctx, WorksLatencyFacts facts) {
        if (ctx.contains("matchall") || ctx.contains("rapprocher")) {
            return true;
        }
        String action = facts != null ? facts.getActionName() : null;
        String label = facts != null ? facts.getBusinessActionLabel() : null;
        String blob = ((action != null ? action : "") + " " + (label != null ? label : ""))
                .toLowerCase(Locale.ROOT);
        return blob.contains("matchall") || blob.contains("rapprocher");
    }

    private static boolean looksLikeAutoStart(String ctx) {
        return ctx.contains("processautostartrules")
                || ctx.contains("autostart")
                || ctx.contains("trace_sessions");
    }

    private static boolean looksLikeWebService(String ctx) {
        return ctx.contains("processwebservice")
                || ctx.contains("ws_")
                || ctx.contains("saveorupdate ws")
                || ctx.contains("end saveorupdate");
    }

    private static boolean looksLikePrint(String ctx, WorksLatencyFacts facts, LatencyTimelineStepDto primary) {
        if (ctx.contains("ast_print") || ctx.contains("printarchive") || ctx.contains("printjob")
                || ctx.contains("cups") || ctx.contains("imprimer") || ctx.contains("|pdf|")
                || ctx.contains("action : pdf") || ctx.contains("pdf ")) {
            return true;
        }
        String action = safe(facts != null ? facts.getBusinessActionLabel() : null)
                + " " + safe(facts != null ? facts.getActionName() : null);
        String a = action.toLowerCase(Locale.ROOT);
        return a.contains("imprimer") || a.contains("pdf");
    }

    /**
     * Goulot = chargement de données / opération : ne pas le masquer derrière le nom
     * de process « …Imprimer ».
     */
    private static boolean isDataLoadBottleneck(LatencyTimelineStepDto primary) {
        if (primary == null) {
            return false;
        }
        String type = primary.getStepType();
        if (LatencyOperationClassifier.OPERATION_LOAD.equals(type)
                || LatencyOperationClassifier.CHILD_LOAD.equals(type)
                || LatencyOperationClassifier.CHILD_QUERY.equals(type)
                || LatencyOperationClassifier.CHILD_LOAD_B2.equals(type)) {
            return true;
        }
        String op = safe(primary.getOperationName()).toLowerCase(Locale.ROOT);
        return op.contains("loadoperation") || op.contains("loadlistchilds")
                || op.contains("searchattributes") || op.contains("loadfils");
    }

    private static boolean looksLikeSaveDominated(WorksLatencyFacts facts,
                                                  LatencyTimelineStepDto primary,
                                                  LatencyBottleneckAnalyzer.NarrativeKind base) {
        if (facts == null) {
            return false;
        }
        Long save = facts.getSavePersistMs();
        Long rules = facts.getRulesEngineMs();
        if (save == null || save < 2000) {
            return LatencyOperationClassifier.SAVE_PERSIST.equals(
                    primary != null ? primary.getStepType() : null)
                    || LatencyOperationClassifier.VALIDATION.equals(
                    primary != null ? primary.getStepType() : null);
        }
        if (rules != null && rules >= save * 0.8) {
            return false;
        }
        if (primary != null && primary.getDurationMs() != null
                && save >= primary.getDurationMs() * 0.7) {
            return true;
        }
        return base == LatencyBottleneckAnalyzer.NarrativeKind.COMPLEX
                || base == LatencyBottleneckAnalyzer.NarrativeKind.SINGLE_STEP
                || base == LatencyBottleneckAnalyzer.NarrativeKind.CONTAINER_DOMINATED;
    }

    private static boolean looksLikeWorkflow(String ctx, LatencyTimelineStepDto primary) {
        if (ctx.contains("transit task") || ctx.contains("persist operation")
                || ctx.contains("jbpm") || ctx.contains("doaction")) {
            return true;
        }
        return primary != null && LatencyOperationClassifier.WORKFLOW.equals(primary.getStepType());
    }

    private static String contextBlob(WorksLatencyFacts facts,
                                      LatencyTimelineStepDto primary,
                                      LatencyOriginReportDto report) {
        StringBuilder sb = new StringBuilder();
        if (facts != null) {
            sb.append(' ').append(safe(facts.getProcessName()));
            sb.append(' ').append(safe(facts.getTaskName()));
            sb.append(' ').append(safe(facts.getActionName()));
            sb.append(' ').append(safe(facts.getBusinessActionLabel()));
            sb.append(' ').append(safe(facts.getFilterCode()));
            sb.append(' ').append(safe(facts.getPreRulesFilterCode()));
        }
        if (report != null) {
            sb.append(' ').append(safe(report.getProcessName()));
            sb.append(' ').append(safe(report.getActionName()));
            sb.append(' ').append(safe(report.getFilterCode()));
        }
        if (primary != null) {
            sb.append(' ').append(safe(primary.getOperationName()));
            sb.append(' ').append(safe(primary.getDetail()));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static String safe(String v) {
        return v != null ? v : "";
    }
}
