package com.caciopee.loganalyzer.analysis.registry;

import com.caciopee.loganalyzer.entity.LogEventType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CentralEventRegistry {

    private final List<EventRule> rules = new ArrayList<>();

    public CentralEventRegistry() {
        registerDefaults();
    }

    public DetectionResult detect(String message, String processName, String sourceClass, String level) {
        String msg = safe(message);
        String msgLower = msg.toLowerCase(Locale.ROOT);
        String process = safe(processName).toLowerCase(Locale.ROOT);
        String source = safe(sourceClass).toLowerCase(Locale.ROOT);
        String lvl = safe(level).toUpperCase(Locale.ROOT);

        DetectionInput input = new DetectionInput(msg, msgLower, process, source, lvl);

        for (EventRule rule : rules) {
            if (rule.matches(input)) {
                return new DetectionResult(rule.eventType(), rule.businessMeaning());
            }
        }

        if ("ERROR".equals(lvl)) {
            return new DetectionResult(LogEventType.GENERIC_ERROR, "Une erreur a été détectée dans ce traitement.");
        }

        if ("WARN".equals(lvl) || "WARNING".equals(lvl)) {
            return new DetectionResult(LogEventType.RULE_WARNING, "Un avertissement a été détecté dans ce traitement.");
        }

        return new DetectionResult(LogEventType.UNKNOWN, null);
    }

    private void registerDefaults() {

        // ===== WORKS-TOMCAT / STRUCTURE DATA =====
        addContains(LogEventType.WORKFLOW_LOOP_START,
                "start looping over interface objects",
                "Début du parcours des objets d’interface à structurer.");

        addContains(LogEventType.WORKFLOW_NEW_INTERFACE_OBJECT,
                "new interface object",
                "Un nouvel objet d’interface est en cours de traitement.");

        addPredicate(LogEventType.STRUCTURE_DATA_START,
                i -> i.messageLower().contains("start [strcturedatasinterface.jar")
                        && i.messageLower().contains("structuredata"),
                "Début de structuration d’un objet métier.");

        addPredicate(LogEventType.STRUCTURE_DATA_END,
                i -> i.messageLower().contains("end [strcturedatasinterface.jar")
                        && i.messageLower().contains("structuredata"),
                "Fin de structuration d’un objet métier.");

        addContains(LogEventType.STRUCTURING_FIELD_START,
                "structuring ne field works",
                "Début du mapping d’un champ d’interface vers un champ métier WORKS.");

        addContains(LogEventType.FIELD_TRIM_REQUIRED,
                "need to be trim",
                "Le système nettoie la valeur du champ avant conversion.");

        addContains(LogEventType.FIELD_CLASS_CODE_DETECTED,
                "{fieldclasscode}",
                "Le code interne du champ métier a été identifié.");

        addContains(LogEventType.FIELD_VALUE_READ,
                "the value of the interface",
                "Le système lit la valeur reçue depuis l’interface.");

        addContains(LogEventType.TYPE_CONVERSION_START,
                "parsing the value of the interface to type",
                "Le système convertit la valeur de l’interface vers le type attendu.");

        addContains(LogEventType.TYPE_CONVERSION_NULL,
                "return null",
                "La conversion de type a retourné une valeur nulle.");

        addContains(LogEventType.TYPE_CONVERSION_SUCCESS,
                "parsing the value of the interface successful",
                "La conversion de la valeur a réussi.");

        addContains(LogEventType.TYPE_CONVERSION_SUCCESS,
                "returning [string] object",
                "La valeur a été convertie en chaîne de caractères.");

        addContains(LogEventType.TYPE_CONVERSION_SUCCESS,
                "returning [double] object",
                "La valeur a été convertie en nombre.");

        addContains(LogEventType.TYPE_CONVERSION_SUCCESS,
                "returning [date] object",
                "La valeur a été convertie en date.");

        addContains(LogEventType.TYPE_CONVERSION_SUCCESS,
                "returning [pp as string] object",
                "La valeur PP a été convertie en chaîne exploitable.");

        addContains(LogEventType.TYPE_CONVERSION_SUCCESS,
                "returning [pm as string] object",
                "La valeur PM a été convertie en chaîne exploitable.");

        addPredicate(LogEventType.FINAL_OBJECT_FIELD_PUT,
                i -> i.messageLower().contains("put key")
                        && i.messageLower().contains("in finalstructuredobject"),
                "Le système injecte le champ converti dans l’objet métier final.");

        addContains(LogEventType.STRUCTURING_RELATION_PRESENT,
                "there is a relation to structure",
                "Le champ traité implique une relation métier à construire.");

        addContains(LogEventType.STRUCTURING_RELATION_ABSENT,
                "there is no relation to structure",
                "Le champ traité ne nécessite pas de relation métier.");

        addContains(LogEventType.STRUCTURING_RELATION_FIRST_ADDED,
                "adding the first relation",
                "Le système initialise une première relation métier.");

        addContains(LogEventType.STRUCTURING_RELATION_KEY_CREATED,
                "structured relkey",
                "Une clé de relation métier a été construite.");

        addContains(LogEventType.STRUCTURING_RELATION_KEY_ATTACHED,
                "put new relkey inside relkeys",
                "La clé de relation a été rattachée à la liste des relations.");

        addContains(LogEventType.STRUCTURING_RELATION_DATA_ATTACHED,
                "put reldata by",
                "Des données de relation ont été rattachées à l’objet métier.");

        addContains(LogEventType.MANDATORY_CHECK_START,
                "checkmandatoryfieldsdata",
                "Début de vérification des champs obligatoires.");

        addContains(LogEventType.MANDATORY_FIELDS_LIST,
                "the mandatory fields are",
                "La liste des champs obligatoires attendus a été identifiée.");

        addContains(LogEventType.BUSINESS_KEY_NULL,
                "business key value is => [null]",
                "La clé métier de l’objet est nulle ou absente.");

        addContains(LogEventType.MANDATORY_FIELD_CHECK,
                "{mandatorymapping}",
                "Un champ obligatoire est en cours de vérification.");

        addContains(LogEventType.MANDATORY_CHECK_SUCCESS,
                "the interface respect the mandatory fields",
                "L’objet respecte les champs obligatoires.");

        addContains(LogEventType.PM_MAPPING_ERROR,
                "the pppm is null",
                "Erreur métier : une donnée PP/PM nécessaire au mapping est absente.");

        addPredicate(LogEventType.ERROR_DETAILS,
                i -> i.messageLower().contains("the error details are as follows")
                        && i.messageLower().contains("the column is")
                        && i.messageLower().contains("the attribute")
                        && i.messageLower().contains("the value")
                        && i.messageLower().contains("the bk"),
                "Détails de l’erreur métier : colonne, attribut, valeur et clé métier impactée.");

        // ===== CALL / SOURCE =====
        addContains(LogEventType.CALL_RUN_RULES_WS, "call runrules ws from",
                "Un appel distant a demandé l’exécution des règles métier.");
        addContains(LogEventType.CALL_EXECUTE_FILTER_WS, "call executefilter ws from",
                "Un appel distant a demandé l’exécution d’un filtre métier.");
        addContains(LogEventType.CALL_SAVE_WS, "call [save] ws from",
                "Un appel distant a demandé une sauvegarde métier.");

        // ===== AUTOMATIC / RULES =====
        addPredicate(LogEventType.AUTOMATIC_PROCESSING_START,
                i -> i.messageLower().contains("[automaticprocessing]")
                        && i.messageLower().contains("[automatic launch]"),
                "Un traitement automatique a été lancé par le système.");

        addPredicate(LogEventType.BEFORE_LOAD_START,
                i -> i.messageLower().contains("start fire rules")
                        && i.messageLower().contains("before_load"),
                "Début d’exécution des règles métier avant chargement.");

        addPredicate(LogEventType.BEFORE_LOAD_END,
                i -> i.messageLower().contains("end fire rules")
                        && i.messageLower().contains("before_load"),
                "Fin d’exécution des règles métier avant chargement.");

        addPredicate(LogEventType.LOAD_START,
                i -> i.messageLower().contains("start fire rules")
                        && i.messageLower().contains("actionname [load]"),
                "Début d’exécution des règles métier de chargement.");

        addPredicate(LogEventType.LOAD_END,
                i -> i.messageLower().contains("end fire rules")
                        && i.messageLower().contains("actionname [load]"),
                "Fin d’exécution des règles métier de chargement.");

        addPredicate(LogEventType.SAVE_ACTION_START,
                i -> i.messageLower().contains("start fire rules")
                        && i.messageLower().contains("actionname [save]"),
                "Début d’exécution des règles métier de sauvegarde.");

        addPredicate(LogEventType.SAVE_ACTION_END,
                i -> i.messageLower().contains("end fire rules")
                        && i.messageLower().contains("actionname [save]"),
                "Fin d’exécution des règles métier de sauvegarde.");

        addContains(LogEventType.RULES_RUNNING, "start fire rules",
                "Début d’exécution des règles métier.");
        addContains(LogEventType.RULES_FINISHED, "end fire rules",
                "Fin d’exécution des règles métier.");
        addContains(LogEventType.RULE_NOT_FOUND, "no rule found for this params",
                "Aucune règle métier applicable n’a été trouvée pour ce contexte.");

        addPredicate(LogEventType.RULE_WARNING,
                i -> i.messageLower().contains("aucune")
                        && i.messageLower().contains("transition"),
                "Aucune règle métier n’a été affectée à cette transition.");

        addContains(LogEventType.TRIGGER_MISFIRED, "trigger",
                "Détection d’un événement lié à un déclencheur système.");

        // ===== REQUEST / SEARCH / SQL =====
        addContains(LogEventType.REQUEST_START, "begin executing request",
                "Début d’exécution d’une requête de recherche.");
        addContains(LogEventType.REQUEST_END, "executed request finished",
                "Fin d’exécution d’une requête de recherche.");
        addContains(LogEventType.SEARCH_BY_ROOT_START, "searchcomposantbyroot start execute",
                "Début d’une recherche métier par racine.");
        addContains(LogEventType.SEARCH_BY_ROOT_END, "searchcomposantbyroot end execute",
                "Fin d’une recherche métier par racine.");
        addContains(LogEventType.PREPARE_SEARCH, "preparesearchbyroot",
                "Préparation d’une requête de recherche métier.");
        addContains(LogEventType.DO_SEARCH_START, "start dosearch",
                "Début du traitement détaillé de recherche.");
        addContains(LogEventType.DO_SEARCH_END, "end dosearch.finally",
                "Fin du traitement détaillé de recherche.");
        addContains(LogEventType.GLOBAL_SEARCH_COMPLETED, "global searchcomposantbyroot took",
                "Recherche globale terminée avec durée et nombre de résultats.");

        addPredicate(LogEventType.SQL_QUERY,
                i -> i.messageLower().contains("query:")
                        || i.messageLower().contains(" query: select"),
                "Une requête SQL a été exécutée.");

        addContains(LogEventType.PREPARED_STATEMENT_INFO, "preparedstatement disabled",
                "Information technique sur l’utilisation de PreparedStatement.");
        addContains(LogEventType.SQL_QUERY_FINISHED, "executed request finished",
                "La requête SQL s’est terminée.");

        // ===== TEMP / LOAD =====
        addContains(LogEventType.TEMP_QUERY_USED, "using tempquery",
                "Le système utilise une requête temporaire pour préparer le traitement.");
        addContains(LogEventType.TEMP_SAVE_DONE, "savetempcomposantsloaded",
                "Des données temporaires ont été enregistrées.");

        addPredicate(LogEventType.SEARCH_ATTRIBUTES_START,
                i -> i.messageLower().contains("searchattributeslist")
                        && i.messageLower().contains("start loading data"),
                "Début du chargement des attributs métier.");

        addPredicate(LogEventType.SEARCH_ATTRIBUTES_QUERY_DONE,
                i -> i.messageLower().contains("searchattributeslist")
                        && i.messageLower().contains("[query]"),
                "Une requête de chargement des attributs a été exécutée.");

        addPredicate(LogEventType.SEARCH_TO_STRING_MAPPING,
                i -> i.messageLower().contains("searchtostringmapping")
                        && i.messageLower().contains("[query]"),
                "Le système a chargé le mapping textuel des données métier.");

        addPredicate(LogEventType.LOAD_LIST_CHILDS_QUERY_B2,
                i -> i.messageLower().contains("loadlistchilds")
                        && i.messageLower().contains("query b2"),
                "Deuxième requête de chargement des enfants exécutée.");

        addPredicate(LogEventType.LOAD_LIST_CHILDS_INSERT_TEMP,
                i -> i.messageLower().contains("loadlistchilds")
                        && i.messageLower().contains("inserttemp"),
                "Insertion temporaire utilisée pendant le chargement.");

        addPredicate(LogEventType.LOAD_LIST_CHILDS_QUERY_B1,
                i -> i.messageLower().contains("loadlistchilds")
                        && i.messageLower().contains("query b1"),
                "Première requête de chargement des enfants exécutée.");

        addPredicate(LogEventType.LOAD_LIST_CHILDS_START,
                i -> i.messageLower().contains("loadlistchilds")
                        && i.messageLower().contains("start filling data"),
                "Début du remplissage des données enfants.");

        addContains(LogEventType.LOAD_LIST_CHILDS_FILLING, "loadlistchilds [filling data]",
                "Le système remplit les données récupérées.");
        addContains(LogEventType.LOAD_LIST_CHILDS_TOTAL, "loadlistchilds [total]",
                "Fin du chargement complet des données enfants.");

        // ===== RESULTS =====
        addPredicate(LogEventType.ZERO_ROW_FETCHED,
                i -> i.messageLower().contains("0 row fetched")
                        || i.messageLower().contains("|0 row")
                        || i.messageLower().contains("[0] row fetched"),
                "La recherche n’a retourné aucun résultat.");

        addPredicate(LogEventType.SEARCH_RESULT_FETCHED,
                i -> i.messageLower().contains("row fetched")
                        && !i.messageLower().contains("0 row fetched")
                        && !i.messageLower().contains("[0] row fetched"),
                "Le système a terminé une recherche et récupéré des résultats.");

        addRegex(LogEventType.ZERO_ROW_FETCHED,
                Pattern.compile("\\|0\\s*row\\s*\\|", Pattern.CASE_INSENSITIVE),
                "La recherche n’a retourné aucune ligne.");

        // ===== SERVICE / FILTER =====
        addRegex(LogEventType.SERVICE_START,
                Pattern.compile("start\\s+ws[_A-Z0-9]+", Pattern.CASE_INSENSITIVE),
                "Début d’exécution d’un service web.");
        addRegex(LogEventType.SERVICE_END,
                Pattern.compile("end\\s+ws[_A-Z0-9]+", Pattern.CASE_INSENSITIVE),
                "Fin d’exécution d’un service web.");
        addContains(LogEventType.FILTER_START, "start filtre",
                "Début d’exécution d’un filtre métier.");
        addContains(LogEventType.FILTER_END, "end filtre",
                "Fin d’exécution d’un filtre métier.");

        // ===== SAVE =====
        addContains(LogEventType.SAVE_OR_UPDATE_START, "start saveorupdate ws",
                "Début d’une sauvegarde SaveOrUpdate.");
        addContains(LogEventType.SAVE_OR_UPDATE_END, "end saveorupdate ws",
                "Fin d’une sauvegarde SaveOrUpdate.");
        addContains(LogEventType.PROCESS_CONTENT_SAVE, "saveprocesscontent",
                "Le contenu du process a été sauvegardé.");
        addContains(LogEventType.SAVE_START, "start saveorupdate",
                "Début d’une opération de sauvegarde.");
        addContains(LogEventType.SAVE_END, "end saveorupdate",
                "Fin d’une opération de sauvegarde.");
        addContains(LogEventType.REMOVE_OBJECTS_TO_DELETE, "removeobjectstodelete",
                "Le système a nettoyé les objets marqués pour suppression.");
        addContains(LogEventType.RELATION_DELETE_QUERY, "deleteobjectrels",
                "Suppression technique de relations métier.");
        addContains(LogEventType.RELATION_CHILD_ARRAY_SAVED, "insertarrayrel works_rel_composant_child",
                "Sauvegarde en lot des relations enfants.");
        addContains(LogEventType.RELATION_ROOT_ARRAY_SAVED, "insertarrayrel works_rel_composant_root",
                "Sauvegarde en lot des relations racines.");
        addContains(LogEventType.REL_OPERATION_SAVED, "savereloperationscomposants",
                "Sauvegarde des opérations de relation.");
        addContains(LogEventType.SAVE_INSTANCE_OPERATION, "saveinstanceoperation",
                "Sauvegarde de l’instance métier principale.");
        addContains(LogEventType.SAVE_TOTAL_TIME, "total time save",
                "Temps total de sauvegarde calculé.");

        // ===== PERFORMANCE =====
        addContains(LogEventType.THREADING_ENABLED, "using multithreading",
                "Traitement préparé ou exécuté en multithreading.");
        addContains(LogEventType.THREADING_SKIPPED, "ignore multithreading",
                "Multithreading ignoré car le volume est faible ou nul.");
        addPredicate(LogEventType.THREAD_COMPLETED,
                i -> i.messageLower().contains("the thread")
                        && i.messageLower().contains("is completed"),
                "Thread de traitement terminé.");
        addContains(LogEventType.SESSION_CLOSED, "session closed",
                "Session technique fermée.");
        addContains(LogEventType.MEMORY_USAGE, "memory usage (mo)",
                "Une mesure de consommation mémoire a été enregistrée.");
        addContains(LogEventType.CHECKPOINT_TRACE, "trcheckpoint",
                "Un checkpoint métier a été détecté dans le workflow.");

        addPredicate(LogEventType.SLOW_QUERY,
                i -> extractDurationMs(i.message()) != null
                        && extractDurationMs(i.message()) >= 2000
                        && i.messageLower().contains("query"),
                "Une requête semble lente.");

        addPredicate(LogEventType.SLOW_WORKFLOW_STEP,
                i -> extractDurationMs(i.message()) != null
                        && extractDurationMs(i.message()) >= 2000,
                "Une étape du workflow semble lente.");

        addPredicate(LogEventType.PERFORMANCE_MEASURE,
                i -> i.messageLower().contains("took [")
                        || i.messageLower().contains("in host took ["),
                "Une durée d’exécution a été mesurée.");

        // ===== BUSINESS RESULT =====
        addContains(LogEventType.BUSINESS_RESULT, "aggregate data for",
                "Le système a agrégé les données métier finales.");
        addContains(LogEventType.BUSINESS_RESULT, "rendering result",
                "Le système prépare le résultat final à afficher.");
    }

    private void addContains(LogEventType eventType, String contains, String businessMeaning) {
        rules.add(new EventRule(eventType, businessMeaning,
                input -> input.messageLower().contains(contains.toLowerCase(Locale.ROOT))));
    }

    private void addRegex(LogEventType eventType, Pattern pattern, String businessMeaning) {
        rules.add(new EventRule(eventType, businessMeaning,
                input -> pattern.matcher(input.message()).find()));
    }

    private void addPredicate(LogEventType eventType, Predicate<DetectionInput> predicate, String businessMeaning) {
        rules.add(new EventRule(eventType, businessMeaning, predicate));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static Long extractDurationMs(String message) {
        if (message == null) return null;
        Matcher m = Pattern.compile("took \\[(\\d+)]\\s*ms", Pattern.CASE_INSENSITIVE).matcher(message);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public record DetectionResult(LogEventType eventType, String businessMeaning) {}

    private record EventRule(LogEventType eventType, String businessMeaning, Predicate<DetectionInput> predicate) {
        boolean matches(DetectionInput input) {
            return predicate.test(input);
        }
    }

    private record DetectionInput(
            String message,
            String messageLower,
            String processNameLower,
            String sourceClassLower,
            String levelUpper
    ) {}
}