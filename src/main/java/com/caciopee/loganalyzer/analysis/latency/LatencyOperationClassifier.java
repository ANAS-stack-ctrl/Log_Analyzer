package com.caciopee.loganalyzer.analysis.latency;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Classification générique des opérations WORKS à partir du nom d'opération et du message.
 * Indépendant du filtre ou du process métier.
 */
@Component
public class LatencyOperationClassifier {

    public static final String ROOT_QUERY = "ROOT_QUERY";
    public static final String GLOBAL_CONTAINER = "GLOBAL_SEARCH";
    public static final String PARTITIONAL = "PARTITIONAL_SEARCH";
    public static final String CHILD_LOAD = "LOAD_CHILDREN_TOTAL";
    public static final String CHILD_QUERY = "SEARCH_ATTRIBUTES";
    public static final String CHILD_LOAD_B2 = "LOAD_CHILDREN_B2";
    /** loadOperationById / loadOperationContext — chargement du contexte d'opération. */
    public static final String OPERATION_LOAD = "OPERATION_LOAD";
    public static final String RENDERING = "RENDERING";
    public static final String RULES_ENGINE = "RULES_ENGINE";
    public static final String SAVE_PERSIST = "SAVE_PERSIST";
    public static final String VALIDATION = "VALIDATION";
    public static final String WEBSERVICE = "WEBSERVICE";
    public static final String PRINT = "PRINT";
    public static final String AUTOSTART = "AUTOSTART";
    public static final String WORKFLOW = "WORKFLOW";
    public static final String BUSINESS_ACTION = "BUSINESS_ACTION";
    public static final String GENERIC = "GENERIC";
    public static final String SEARCH_START = "SEARCH_START";
    public static final String SEARCH_END = "SEARCH_END";
    public static final String MULTITHREAD_START = "MULTITHREAD_START";
    public static final String MULTITHREAD_CONFIG = "MULTITHREAD_CONFIG";
    public static final String SQL_QUERY_TEXT = "SQL_QUERY_TEXT";

    public String classify(String operationName, String fullMessage) {
        String lower = (safe(operationName) + " " + safe(fullMessage)).toLowerCase(Locale.ROOT);

        if (lower.contains("preparesearchbyroot")) {
            return ROOT_QUERY;
        }
        // AutoStart avant règles génériques (même ligne "running rules|processAutoStartRules|…").
        if (lower.contains("processautostartrules") || lower.contains("autostart")) {
            return AUTOSTART;
        }
        if (lower.contains("processwebservice") || lower.contains("ws_")
                || lower.contains("end saveorupdate") || lower.contains("saveorupdate ws")) {
            return WEBSERVICE;
        }
        if (lower.contains("printarchive") || lower.contains("printjob") || lower.contains("cups")
                || lower.contains("ast_print")
                || (lower.contains("imprimer") && (lower.contains("running rules") || lower.contains("doaction")))) {
            return PRINT;
        }
        if (lower.contains("running rules") || lower.contains("fire rules")
                || (lower.contains("in host") && lower.contains("rules"))) {
            return RULES_ENGINE;
        }
        if (lower.contains("doaction")) {
            return BUSINESS_ACTION;
        }
        if (lower.contains("transit task") || lower.contains("start transit")
                || lower.contains("persist operation") || lower.contains("jbpm")) {
            return WORKFLOW;
        }
        if (lower.contains("aggregate data for")) {
            return GENERIC;
        }
        if (lower.contains("total time save")) {
            return SAVE_PERSIST;
        }
        if (lower.contains("global searchcomposantbyroot") || lower.contains("global search")) {
            return GLOBAL_CONTAINER;
        }
        if (lower.contains("end partitional searchcomposantbyroot")) {
            return PARTITIONAL;
        }
        if (lower.contains("loadlistchilds") && lower.contains("query b2")) {
            return CHILD_LOAD_B2;
        }
        if (lower.contains("loadoperationbyid") || lower.contains("loadoperationcontext")) {
            return OPERATION_LOAD;
        }
        if (lower.contains("loadlistchilds") || lower.contains("loadfilsoperation")) {
            return CHILD_LOAD;
        }
        if (lower.contains("searchattributeslist")) {
            return CHILD_QUERY;
        }
        if (lower.contains("rendering result")) {
            return RENDERING;
        }
        if (lower.contains("saveoperation") || lower.contains("saveinstanceoperation")) {
            return SAVE_PERSIST;
        }
        if (lower.contains("validateoperation") || lower.contains("validateattributesoperation")) {
            return VALIDATION;
        }
        if (lower.contains("searchcomposantbyroot start execute")) {
            return SEARCH_START;
        }
        if (lower.contains("searchcomposantbyroot end execute")) {
            return SEARCH_END;
        }
        if (lower.contains("start dosearch")) {
            return MULTITHREAD_START;
        }
        if (lower.contains("ignore multithreading")) {
            return MULTITHREAD_CONFIG;
        }
        if (lower.contains("query:") && !lower.contains("preparesearchbyroot")) {
            return SQL_QUERY_TEXT;
        }
        return GENERIC;
    }

    public boolean isContainerType(String stepType) {
        return GLOBAL_CONTAINER.equals(stepType)
                || PARTITIONAL.equals(stepType)
                || RULES_ENGINE.equals(stepType)
                || WEBSERVICE.equals(stepType)
                || AUTOSTART.equals(stepType)
                || PRINT.equals(stepType);
    }

    public boolean isMeasurablePhase(String stepType) {
        return ROOT_QUERY.equals(stepType)
                || CHILD_LOAD.equals(stepType)
                || CHILD_QUERY.equals(stepType)
                || CHILD_LOAD_B2.equals(stepType)
                || OPERATION_LOAD.equals(stepType)
                || RENDERING.equals(stepType)
                || SAVE_PERSIST.equals(stepType)
                || VALIDATION.equals(stepType)
                || WEBSERVICE.equals(stepType)
                || PRINT.equals(stepType)
                || AUTOSTART.equals(stepType)
                || WORKFLOW.equals(stepType)
                || BUSINESS_ACTION.equals(stepType)
                || GENERIC.equals(stepType);
    }

    public String humanLabel(String stepType, String operationName) {
        if (operationName != null && !operationName.isBlank()) {
            return operationName;
        }
        return switch (safe(stepType)) {
            case ROOT_QUERY -> "Requête SQL racine";
            case GLOBAL_CONTAINER -> "Opération globale";
            case PARTITIONAL -> "Recherche partitionnée";
            case CHILD_LOAD -> "Chargement enfants";
            case CHILD_QUERY -> "Chargement attributs";
            case OPERATION_LOAD -> "Chargement opération";
            case RENDERING -> "Rendu résultat";
            case RULES_ENGINE -> "Moteur de règles";
            case SAVE_PERSIST -> "Sauvegarde";
            case VALIDATION -> "Validation";
            case WEBSERVICE -> "Webservice";
            case PRINT -> "Impression / PDF";
            case AUTOSTART -> "Règles AutoStart";
            case WORKFLOW -> "Workflow / BPM";
            case BUSINESS_ACTION -> "Action métier";
            default -> "Étape mesurée";
        };
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
