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

        return new DetectionResult(LogEventType.UNKNOWN, null);
    }

    private void registerDefaults() {

        // ===== CALL / SOURCE =====
        addContains(
                LogEventType.CALL_RUN_RULES_WS,
                "call runrules ws from",
                "Un appel distant a demandé l’exécution des règles métier."
        );

        addContains(
                LogEventType.CALL_EXECUTE_FILTER_WS,
                "call executefilter ws from",
                "Un appel distant a demandé l’exécution d’un filtre métier."
        );

        addContains(
                LogEventType.CALL_SAVE_WS,
                "call [save] ws from",
                "Un appel distant a demandé une sauvegarde métier."
        );

        // ===== AUTOMATIC PROCESSING =====
        addPredicate(
                LogEventType.AUTOMATIC_PROCESSING_START,
                input -> input.messageLower().contains("[automaticprocessing]")
                        && input.messageLower().contains("[automatic launch]"),
                "Un traitement automatique a été lancé par le système."
        );

        // ===== RULES / ACTION PHASES =====
        addPredicate(
                LogEventType.BEFORE_LOAD_START,
                input -> input.messageLower().contains("start fire rules")
                        && input.messageLower().contains("before_load"),
                "Début d’exécution des règles métier avant chargement."
        );

        addPredicate(
                LogEventType.BEFORE_LOAD_END,
                input -> input.messageLower().contains("end fire rules")
                        && input.messageLower().contains("before_load"),
                "Fin d’exécution des règles métier avant chargement."
        );

        addPredicate(
                LogEventType.LOAD_START,
                input -> input.messageLower().contains("start fire rules")
                        && input.messageLower().contains("actionname [load]"),
                "Début d’exécution des règles métier de chargement."
        );

        addPredicate(
                LogEventType.LOAD_END,
                input -> input.messageLower().contains("end fire rules")
                        && input.messageLower().contains("actionname [load]"),
                "Fin d’exécution des règles métier de chargement."
        );

        addPredicate(
                LogEventType.SAVE_ACTION_START,
                input -> input.messageLower().contains("start fire rules")
                        && input.messageLower().contains("actionname [save]"),
                "Début d’exécution des règles métier de sauvegarde."
        );

        addPredicate(
                LogEventType.SAVE_ACTION_END,
                input -> input.messageLower().contains("end fire rules")
                        && input.messageLower().contains("actionname [save]"),
                "Fin d’exécution des règles métier de sauvegarde."
        );

        addContains(
                LogEventType.RULES_RUNNING,
                "start fire rules",
                "Début d’exécution des règles métier."
        );

        addContains(
                LogEventType.RULES_FINISHED,
                "end fire rules",
                "Fin d’exécution des règles métier."
        );

        addContains(
                LogEventType.RULE_NOT_FOUND,
                "no rule found for this params",
                "Aucune règle métier applicable n’a été trouvée pour ce contexte."
        );

        addPredicate(
                LogEventType.RULE_WARNING,
                input -> input.messageLower().contains("aucune")
                        && input.messageLower().contains("r")
                        && input.messageLower().contains("transition"),
                "Aucune règle métier n’a été affectée à cette transition."
        );

        addContains(
                LogEventType.TRIGGER_MISFIRED,
                "trigger",
                "Détection d’un événement lié à un déclencheur système."
        );

        // ===== REQUEST / SEARCH =====
        addContains(
                LogEventType.REQUEST_START,
                "begin executing request",
                "Début d’exécution d’une requête de recherche."
        );

        addContains(
                LogEventType.REQUEST_END,
                "executed request finished",
                "Fin d’exécution d’une requête de recherche."
        );

        addContains(
                LogEventType.SEARCH_BY_ROOT_START,
                "searchcomposantbyroot start execute",
                "Début d’une recherche métier par racine."
        );

        addPredicate(
                LogEventType.ZERO_ROW_FETCHED,
                input -> input.messageLower().contains("searchcomposantbyroot end execute")
                        && (input.messageLower().contains("[0] row fetched")
                        || input.messageLower().contains("0 row fetched")),
                "La recherche métier s’est terminée sans résultat."
        );

        addContains(
                LogEventType.SEARCH_BY_ROOT_END,
                "searchcomposantbyroot end execute",
                "Fin d’une recherche métier par racine."
        );

        addContains(
                LogEventType.PREPARE_SEARCH,
                "preparesearchbyroot",
                "Préparation d’une requête de recherche métier."
        );

        addContains(
                LogEventType.DO_SEARCH_START,
                "start dosearch",
                "Début du traitement détaillé de recherche."
        );

        addContains(
                LogEventType.DO_SEARCH_END,
                "end dosearch.finally",
                "Fin du traitement détaillé de recherche."
        );

        addContains(
                LogEventType.GLOBAL_SEARCH_COMPLETED,
                "global searchcomposantbyroot took",
                "Recherche globale terminée avec durée et nombre de résultats."
        );

        // ===== SERVICE / FILTER =====
        addRegex(
                LogEventType.SERVICE_START,
                Pattern.compile("start\\s+ws[_A-Z0-9]+", Pattern.CASE_INSENSITIVE),
                "Début d’exécution d’un service web."
        );

        addRegex(
                LogEventType.SERVICE_END,
                Pattern.compile("end\\s+ws[_A-Z0-9]+", Pattern.CASE_INSENSITIVE),
                "Fin d’exécution d’un service web."
        );

        addContains(
                LogEventType.FILTER_START,
                "start filtre",
                "Début d’exécution d’un filtre métier."
        );

        addContains(
                LogEventType.FILTER_END,
                "end filtre",
                "Fin d’exécution d’un filtre métier."
        );

        // ===== SQL / QUERY =====
        addPredicate(
                LogEventType.SQL_QUERY,
                input -> input.messageLower().contains("query:")
                        || input.messageLower().contains(" query: select"),
                "Une requête SQL a été exécutée."
        );

        addContains(
                LogEventType.PREPARED_STATEMENT_INFO,
                "preparedstatement disabled",
                "Information technique sur l’utilisation de PreparedStatement."
        );

        addContains(
                LogEventType.SQL_QUERY_FINISHED,
                "executed request finished",
                "La requête SQL s’est terminée."
        );

        // ===== TEMP QUERY / LOADING =====
        addContains(
                LogEventType.TEMP_QUERY_USED,
                "using tempquery",
                "Le système utilise une requête temporaire pour préparer le traitement."
        );

        addContains(
                LogEventType.TEMP_SAVE_DONE,
                "savetempcomposantsloaded",
                "Des données temporaires ont été enregistrées pour la suite du traitement."
        );

        addPredicate(
                LogEventType.SEARCH_ATTRIBUTES_START,
                input -> input.messageLower().contains("searchattributeslist")
                        && input.messageLower().contains("start loading data"),
                "Début du chargement des attributs métier."
        );

        addPredicate(
                LogEventType.SEARCH_ATTRIBUTES_QUERY_DONE,
                input -> input.messageLower().contains("searchattributeslist")
                        && input.messageLower().contains("[query]"),
                "Une requête de chargement des attributs a été exécutée."
        );

        addPredicate(
                LogEventType.SEARCH_TO_STRING_MAPPING,
                input -> input.messageLower().contains("searchtostringmapping")
                        && input.messageLower().contains("[query]"),
                "Le système a chargé le mapping textuel des données métier."
        );

        addPredicate(
                LogEventType.LOAD_LIST_CHILDS_QUERY_B2,
                input -> input.messageLower().contains("loadlistchilds")
                        && input.messageLower().contains("query b2"),
                "Deuxième requête de chargement des enfants exécutée."
        );

        addPredicate(
                LogEventType.LOAD_LIST_CHILDS_INSERT_TEMP,
                input -> input.messageLower().contains("loadlistchilds")
                        && input.messageLower().contains("inserttemp"),
                "Insertion temporaire utilisée pendant le chargement."
        );

        addPredicate(
                LogEventType.LOAD_LIST_CHILDS_QUERY_B1,
                input -> input.messageLower().contains("loadlistchilds")
                        && input.messageLower().contains("query b1"),
                "Première requête de chargement des enfants exécutée."
        );

        addPredicate(
                LogEventType.LOAD_LIST_CHILDS_START,
                input -> input.messageLower().contains("loadlistchilds")
                        && input.messageLower().contains("start filling data"),
                "Début du remplissage des données enfants."
        );

        addContains(
                LogEventType.LOAD_LIST_CHILDS_FILLING,
                "loadlistchilds [filling data]",
                "Le système remplit les données récupérées."
        );

        addContains(
                LogEventType.LOAD_LIST_CHILDS_TOTAL,
                "loadlistchilds [total]",
                "Fin du chargement complet des données enfants."
        );

        // ===== RESULTS =====
        addPredicate(
                LogEventType.ZERO_ROW_FETCHED,
                input -> input.messageLower().contains("0 row fetched")
                        || input.messageLower().contains("|0 row")
                        || input.messageLower().contains("[0] row fetched"),
                "La recherche n’a retourné aucun résultat."
        );

        addPredicate(
                LogEventType.SEARCH_RESULT_FETCHED,
                input -> input.messageLower().contains("row fetched")
                        && !input.messageLower().contains("0 row fetched")
                        && !input.messageLower().contains("[0] row fetched"),
                "Le système a terminé une recherche et récupéré des résultats."
        );

        addRegex(
                LogEventType.ZERO_ROW_FETCHED,
                Pattern.compile("\\|0\\s*row\\s*\\|", Pattern.CASE_INSENSITIVE),
                "La recherche n’a retourné aucune ligne."
        );

        // ===== SAVE / PROCESS =====
        addContains(
                LogEventType.SAVE_OR_UPDATE_START,
                "start saveorupdate ws",
                "Début d’une sauvegarde SaveOrUpdate."
        );

        addContains(
                LogEventType.SAVE_OR_UPDATE_END,
                "end saveorupdate ws",
                "Fin d’une sauvegarde SaveOrUpdate."
        );

        addContains(
                LogEventType.PROCESS_CONTENT_SAVE,
                "saveprocesscontent",
                "Le contenu du process a été sauvegardé."
        );

        addContains(
                LogEventType.SAVE_START,
                "start saveorupdate",
                "Début d’une opération de sauvegarde."
        );

        addContains(
                LogEventType.SAVE_END,
                "end saveorupdate",
                "Fin d’une opération de sauvegarde."
        );

        addContains(
                LogEventType.REMOVE_OBJECTS_TO_DELETE,
                "removeobjectstodelete",
                "Le système a nettoyé les objets marqués pour suppression."
        );

        addContains(
                LogEventType.RELATION_DELETE_QUERY,
                "deleteobjectrels",
                "Suppression technique de relations métier."
        );

        addContains(
                LogEventType.RELATION_CHILD_ARRAY_SAVED,
                "insertarrayrel works_rel_composant_child",
                "Sauvegarde en lot des relations enfants."
        );

        addContains(
                LogEventType.RELATION_ROOT_ARRAY_SAVED,
                "insertarrayrel works_rel_composant_root",
                "Sauvegarde en lot des relations racines."
        );

        addContains(
                LogEventType.REL_OPERATION_SAVED,
                "savereloperationscomposants",
                "Sauvegarde des opérations de relation."
        );

        addContains(
                LogEventType.SAVE_INSTANCE_OPERATION,
                "saveinstanceoperation",
                "Sauvegarde de l’instance métier principale."
        );

        addContains(
                LogEventType.SAVE_TOTAL_TIME,
                "total time save",
                "Temps total de sauvegarde calculé."
        );

        // ===== THREADING / PERFORMANCE =====
        addContains(
                LogEventType.THREADING_ENABLED,
                "using multithreading",
                "Traitement préparé ou exécuté en multithreading."
        );

        addContains(
                LogEventType.THREADING_SKIPPED,
                "ignore multithreading",
                "Multithreading ignoré car le volume est faible ou nul."
        );

        addPredicate(
                LogEventType.THREAD_COMPLETED,
                input -> input.messageLower().contains("the thread")
                        && input.messageLower().contains("is completed"),
                "Thread de traitement terminé."
        );

        addContains(
                LogEventType.SESSION_CLOSED,
                "session closed",
                "Session technique fermée."
        );

        addContains(
                LogEventType.MEMORY_USAGE,
                "memory usage (mo)",
                "Une mesure de consommation mémoire a été enregistrée."
        );

        addContains(
                LogEventType.CHECKPOINT_TRACE,
                "trcheckpoint",
                "Un checkpoint métier a été détecté dans le workflow."
        );

        addPredicate(
                LogEventType.SLOW_QUERY,
                input -> extractDurationMs(input.message()) != null
                        && extractDurationMs(input.message()) >= 2000
                        && input.messageLower().contains("query"),
                "Une requête semble lente."
        );

        addPredicate(
                LogEventType.SLOW_WORKFLOW_STEP,
                input -> extractDurationMs(input.message()) != null
                        && extractDurationMs(input.message()) >= 2000,
                "Une étape du workflow semble lente."
        );

        addPredicate(
                LogEventType.PERFORMANCE_MEASURE,
                input -> input.messageLower().contains("took [")
                        || input.messageLower().contains("in host took ["),
                "Une durée d’exécution a été mesurée."
        );

        // ===== WORKFLOW / STRUCTURE =====
        addContains(
                LogEventType.WORKFLOW_LOOP_START,
                "workflow loop",
                "Début d’une boucle de traitement dans le workflow."
        );

        addContains(
                LogEventType.WORKFLOW_NEW_INTERFACE_OBJECT,
                "new interface object",
                "Création d’un nouvel objet d’interface."
        );

        addContains(
                LogEventType.WORKFLOW_STRUCTURE_DATA_CALL,
                "structuredata",
                "Appel au moteur de structuration des données."
        );

        addContains(
                LogEventType.STRUCTURE_DATA_START,
                "start structure data",
                "Début de structuration des données."
        );

        addContains(
                LogEventType.STRUCTURE_DATA_END,
                "end structure data",
                "Fin de structuration des données."
        );

        addContains(
                LogEventType.STRUCTURED_OBJECT_ASSEMBLED,
                "structured object assembled",
                "Un objet métier structuré a été assemblé."
        );

        // ===== FIELD / MAPPING =====
        addContains(LogEventType.FIELD_MAPPING, "mapping field", "Une étape de mapping de champ a été détectée.");
        addContains(LogEventType.FIELD_MAPPING, "field mapping", "Une étape de mapping de champ a été détectée.");
        addContains(LogEventType.FIELD_VALUE_READ, "field value read", "Une valeur de champ a été lue.");
        addContains(LogEventType.FIELD_CLASS_CODE, "field class code", "Le code de classe du champ a été identifié.");
        addContains(LogEventType.TYPE_DETECTED, "type detected", "Le type d’une donnée a été détecté.");
        addContains(LogEventType.TYPE_CONVERSION_START, "type conversion start", "Début de conversion de type.");
        addContains(LogEventType.TYPE_CONVERSION_RESULT, "type conversion result", "Résultat intermédiaire de conversion de type.");
        addContains(LogEventType.TYPE_CONVERSION_SUCCESS, "type conversion success", "La conversion de type a réussi.");
        addContains(LogEventType.FIELD_INSERTED, "field inserted", "Un champ a été injecté dans l’objet métier.");

        addRegex(
                LogEventType.QUERY_PARAMETER_DETECTED,
                Pattern.compile("\\b[a-zA-Z0-9_]+\\s*=\\[[^\\]]+]", Pattern.CASE_INSENSITIVE),
                "Un paramètre métier ou technique de requête a été détecté."
        );

        // ===== RELATIONS =====
        addContains(LogEventType.RELATION_DETECTED, "relation detected", "Une relation métier a été détectée.");
        addContains(LogEventType.RELATION_KEY_CREATED, "relation key created", "Une clé de relation a été créée.");
        addContains(LogEventType.RELATION_ADDED, "relation added", "Une relation a été ajoutée.");
        addContains(LogEventType.RELATION_DATA_ATTACHED, "relation data attached", "Des données de relation ont été rattachées.");

        // ===== MANDATORY =====
        addContains(LogEventType.MANDATORY_CHECK_START, "mandatory check start", "Début de vérification des champs obligatoires.");
        addContains(LogEventType.MANDATORY_FIELDS_LIST, "mandatory fields list", "La liste des champs obligatoires a été identifiée.");
        addContains(LogEventType.MANDATORY_FIELD_CHECK, "mandatory field check", "Un champ obligatoire est en cours de vérification.");
        addContains(LogEventType.MANDATORY_CHECK_END, "mandatory check end", "Fin de vérification des champs obligatoires.");
        addContains(LogEventType.MANDATORY_CHECK_SUCCESS, "mandatory check success", "La vérification des champs obligatoires a réussi.");

        // ===== ERRORS =====
        addContains(LogEventType.ERROR_DETAILS, "error details", "Des détails d’erreur ont été détectés.");
        addContains(LogEventType.PM_MAPPING_ERROR, "pm mapping error", "Une erreur de mapping métier a été détectée.");
        addContains(LogEventType.GENERIC_ERROR, "generic error", "Une erreur générique a été détectée.");
        addContains(LogEventType.NULL_CONTINUE, "null continue", "Le traitement continue malgré une valeur nulle.");

        // ===== BUSINESS RESULT =====
        addContains(
                LogEventType.BUSINESS_RESULT,
                "aggregate data for",
                "Le système a agrégé les données métier finales."
        );
    }

    private void addContains(LogEventType eventType, String contains, String businessMeaning) {
        rules.add(new EventRule(
                eventType,
                businessMeaning,
                input -> input.messageLower().contains(contains.toLowerCase(Locale.ROOT))
        ));
    }

    private void addRegex(LogEventType eventType, Pattern pattern, String businessMeaning) {
        rules.add(new EventRule(
                eventType,
                businessMeaning,
                input -> {
                    Matcher matcher = pattern.matcher(input.message());
                    return matcher.find();
                }
        ));
    }

    private void addPredicate(LogEventType eventType, Predicate<DetectionInput> predicate, String businessMeaning) {
        rules.add(new EventRule(eventType, businessMeaning, predicate));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static Long extractDurationMs(String message) {
        if (message == null) return null;

        Pattern p = Pattern.compile("took \\[(\\d+)]\\s*ms", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(message);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    public record DetectionResult(LogEventType eventType, String businessMeaning) {
    }

    private record EventRule(
            LogEventType eventType,
            String businessMeaning,
            Predicate<DetectionInput> predicate
    ) {
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
    ) {
    }
}