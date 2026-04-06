package com.caciopee.loganalyzer.parser;

import com.caciopee.loganalyzer.entity.LogEntry;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogParserService {

    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");

    private static final Pattern STEP_CODE_PATTERN =
            Pattern.compile("^(\\d+(?:\\.\\d+)?--)");
    private static final Pattern FIELD_MAPPING_PATTERN =
            Pattern.compile("STRUCTURING NE FIELD works = \\[(.*?)] interface = \\[(.*?)]");
    private static final Pattern FIELD_CLASS_CODE_PATTERN =
            Pattern.compile("\\{fieldClassCode} = \\[(.*?)]");
    private static final Pattern VALUE_PATTERN =
            Pattern.compile("the value of the interface = \\[(.*?)]");
    private static final Pattern TYPE_PATTERN =
            Pattern.compile("parsing the value of the interface to type = \\[(.*?)]");
    private static final Pattern PUT_KEY_PATTERN =
            Pattern.compile("put key \\[(.*?)] value \\[(.*?)] in finalStructuredObject");
    private static final Pattern RELATION_PATTERN =
            Pattern.compile("adding the first relation \\[(.*?)]");
    private static final Pattern RELKEY_PATTERN =
            Pattern.compile("structured RelKey \\(new relKey\\) \\[(.*?)]");
    private static final Pattern RELDATA_PATTERN =
            Pattern.compile("put relData by reClde \\[(.*?)]");
    private static final Pattern MANDATORY_FIELDS_PATTERN =
            Pattern.compile("the mandatory fields are : \\[\\[(.*?)]] the business key value is => \\[(.*?)]");
    private static final Pattern MANDATORY_MAPPING_PATTERN =
            Pattern.compile("\\{mandatoryMapping} : \\[(.*?)](?:,)? the business key value is => \\[(.*?)]");
    private static final Pattern ERROR_DETAILS_PATTERN =
            Pattern.compile("the column is => \\[(.*?)] the attribute => \\[(.*?)] the value => \\[(.*?)] the BK => \\[(.*?)]");

    public LogEntry parseLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty log line");
        }

        String[] parts = line.split("\\|", -1);
        if (parts.length < 15) {
            throw new IllegalArgumentException("Invalid log format: expected at least 15 columns but got " + parts.length);
        }

        LogEntry entry = new LogEntry();

        entry.setRawLog(line);
        entry.setLogTimestamp(parseTimestamp(parts[0]));
        entry.setExecutionId(clean(parts[1]));
        entry.setLevel(clean(parts[2]));
        entry.setUserName(clean(parts[3]));
        entry.setSourceClass(clean(parts[4]));
        entry.setProcessName(clean(parts[5]));
        entry.setMessage(clean(parts[7]));
        entry.setLogCode(parseInteger(parts[8]));
        entry.setEnvironment(clean(parts[11]));
        entry.setServerName(clean(parts[12]));
        entry.setAppVersion(clean(parts[13]));
        entry.setCorrelationId(clean(parts[14]));
        entry.setStepCode(extractStepCode(entry.getMessage()));

        enrichSemantics(entry);

        // Cohérence stricte : is_error dépend du level
        if ("ERROR".equalsIgnoreCase(entry.getLevel())) {
            entry.setError(true);
        } else {
            entry.setError(false);
        }

        return entry;
    }

    private void enrichSemantics(LogEntry entry) {
        String message = entry.getMessage();
        if (message == null) {
            entry.setEventType("UNKNOWN");
            entry.setBusinessMeaning("Message de log vide ou non reconnu.");
            return;
        }

        extractFieldMapping(entry, message);
        extractFieldClassCode(entry, message);
        extractValue(entry, message);
        extractParsedType(entry, message);
        extractPutKey(entry, message);
        extractRelation(entry, message);
        extractRelKey(entry, message);
        extractMandatoryFields(entry, message);
        extractMandatoryMapping(entry, message);
        extractErrorDetails(entry, message);
        extractRelData(entry, message);

        String eventType = detectEventType(message, entry);
        entry.setEventType(eventType);
        entry.setBusinessMeaning(buildBusinessMeaning(eventType, entry));
    }

    private void extractFieldMapping(LogEntry entry, String message) {
        Matcher m = FIELD_MAPPING_PATTERN.matcher(message);
        if (m.find()) {
            entry.setFieldName(clean(m.group(1)));
            entry.setInterfaceField(clean(m.group(2)));
        }
    }

    private void extractFieldClassCode(LogEntry entry, String message) {
        Matcher m = FIELD_CLASS_CODE_PATTERN.matcher(message);
        if (m.find()) {
            entry.setFieldClassCode(clean(m.group(1)));
        }
    }

    private void extractValue(LogEntry entry, String message) {
        Matcher m = VALUE_PATTERN.matcher(message);
        if (m.find()) {
            entry.setParsedValue(clean(m.group(1)));
        }
    }

    private void extractParsedType(LogEntry entry, String message) {
        Matcher m = TYPE_PATTERN.matcher(message);
        if (m.find()) {
            entry.setParsedType(clean(m.group(1)));
        }
    }

    private void extractPutKey(LogEntry entry, String message) {
        Matcher m = PUT_KEY_PATTERN.matcher(message);
        if (m.find()) {
            if (entry.getFieldName() == null) {
                entry.setFieldName(clean(m.group(1)));
            }
            entry.setParsedValue(clean(m.group(2)));
        }
    }

    private void extractRelation(LogEntry entry, String message) {
        Matcher m = RELATION_PATTERN.matcher(message);
        if (m.find()) {
            entry.setRelationName(clean(m.group(1)));
        }
    }

    private void extractRelKey(LogEntry entry, String message) {
        Matcher m = RELKEY_PATTERN.matcher(message);
        if (m.find()) {
            entry.setRelationKey(clean(m.group(1)));
        }
    }

    private void extractRelData(LogEntry entry, String message) {
        Matcher m = RELDATA_PATTERN.matcher(message);
        if (m.find()) {
            entry.setRelationKey(clean(m.group(1)));
        }
    }

    private void extractMandatoryFields(LogEntry entry, String message) {
        Matcher m = MANDATORY_FIELDS_PATTERN.matcher(message);
        if (m.find()) {
            entry.setMandatoryField(clean(m.group(1)));
            entry.setBusinessKey(clean(m.group(2)));
        }
    }

    private void extractMandatoryMapping(LogEntry entry, String message) {
        Matcher m = MANDATORY_MAPPING_PATTERN.matcher(message);
        if (m.find()) {
            entry.setMandatoryField(clean(m.group(1)));
            entry.setBusinessKey(clean(m.group(2)));
        }
    }

    private void extractErrorDetails(LogEntry entry, String message) {
        Matcher m = ERROR_DETAILS_PATTERN.matcher(message);
        if (m.find()) {
            entry.setErrorColumn(clean(m.group(1)));
            entry.setErrorAttribute(clean(m.group(2)));
            entry.setErrorValue(clean(m.group(3)));
            entry.setErrorBusinessKey(clean(m.group(4)));

            if (entry.getFieldName() == null) {
                entry.setFieldName(entry.getErrorAttribute());
            }
            if (entry.getBusinessKey() == null) {
                entry.setBusinessKey(entry.getErrorBusinessKey());
            }
        }
    }

    private String detectEventType(String message, LogEntry entry) {
        if (message.startsWith("START LOOPING OVER INTERFACE OBJECTS")) {
            return "WORKFLOW_LOOP_START";
        }
        if (message.contains("NEW INTERFACE OBJECT")) {
            return "WORKFLOW_NEW_INTERFACE_OBJECT";
        }
        if (message.contains("calling {this.structureData}")) {
            return "WORKFLOW_STRUCTURE_DATA_CALL";
        }
        if (message.startsWith("START [") && message.contains("structureData]")) {
            return "STRUCTURE_DATA_START";
        }
        if (message.startsWith("END [") && message.contains("structureData]")) {
            return "STRUCTURE_DATA_END";
        }
        if (message.startsWith("START [") && message.contains("getDataByType]")) {
            return "TYPE_CONVERSION_START";
        }
        if (message.startsWith("END [") && message.contains("getDataByType] return null")) {
            return "TYPE_CONVERSION_NULL";
        }
        if (message.startsWith("END [") && message.contains("getDataByType]")) {
            return "TYPE_CONVERSION_RESULT";
        }
        if (message.contains("STRUCTURING NE FIELD")) {
            return "FIELD_MAPPING";
        }
        if (message.contains("need to be trim")) {
            return "FIELD_TRIM_REQUIRED";
        }
        if (message.contains("{fieldClassCode}")) {
            return "FIELD_CLASS_CODE";
        }
        if (message.contains("the value of the interface =")) {
            return "FIELD_VALUE_READ";
        }
        if (message.contains("parsing the value of the interface to type =")) {
            return "TYPE_DETECTED";
        }
        if (message.contains("parsing the value of the interface successful")) {
            return "TYPE_CONVERSION_SUCCESS";
        }
        if (message.contains("put key [") && message.contains("in finalStructuredObject")) {
            return "FIELD_INSERTED";
        }
        if (message.contains("there is a relation to structure")) {
            return "RELATION_DETECTED";
        }
        if (message.contains("there is NO relation to structure")) {
            return "NO_RELATION";
        }
        if (message.contains("adding the first relation")) {
            return "RELATION_ADDED";
        }
        if (message.contains("structured RelKey")) {
            return "RELATION_KEY_CREATED";
        }
        if (message.contains("put relData by reClde")) {
            return "RELATION_DATA_ATTACHED";
        }
        if (message.contains("put {dataBkValue} {structuredData} {relDataByBkValue} inside return object structuredDataWithBk")) {
            return "STRUCTURED_OBJECT_ASSEMBLED";
        }
        if (message.startsWith("START [") && message.contains("checkMandatoryFieldsData]")) {
            return "MANDATORY_CHECK_START";
        }
        if (message.contains("the mandatory fields are")) {
            return "MANDATORY_FIELDS_LIST";
        }
        if (message.contains("{mandatoryMapping}")) {
            return "MANDATORY_FIELD_CHECK";
        }
        if (message.startsWith("END [") && message.contains("checkMandatoryFieldsData]")) {
            return "MANDATORY_CHECK_END";
        }
        if (message.contains("the interface respect the mandatory fields")) {
            return "MANDATORY_CHECK_SUCCESS";
        }
        if (message.contains("matching with EQUALS pattern")) {
            return "DETAIL_PARAMETER_EQUALS_MATCH";
        }
        if (message.contains("start looping over {detailParamsByPath.getBo(keySet)}")) {
            return "DETAIL_PARAMETER_LOOP_START";
        }
        if (message.contains("end looping over {detailParamsByPath.getBo(keySet)}")) {
            return "DETAIL_PARAMETER_LOOP_END";
        }
        if (message.contains("keySet [") && message.contains("is a simple attr")) {
            return "DETAIL_PARAMETER_SIMPLE_ATTRIBUTE";
        }
        if (message.contains("databyType IS NULL => CONTINUE")) {
            return "NULL_CONTINUE";
        }
        if (message.contains("The pppm is null")) {
            return "PM_MAPPING_ERROR";
        }
        if (message.contains("The error details are as follows")) {
            return "ERROR_DETAILS";
        }
        if ("ERROR".equalsIgnoreCase(entry.getLevel())) {
            return "GENERIC_ERROR";
        }
        return "GENERIC_INFO";
    }

    private String buildBusinessMeaning(String eventType, LogEntry entry) {
        return switch (eventType) {
            case "WORKFLOW_LOOP_START" ->
                    "Début du traitement d'une liste d'objets d'interface.";
            case "WORKFLOW_NEW_INTERFACE_OBJECT" ->
                    "Début du traitement d'un nouvel objet d'interface.";
            case "WORKFLOW_STRUCTURE_DATA_CALL" ->
                    "Appel du moteur principal de structuration pour un objet.";
            case "STRUCTURE_DATA_START" ->
                    "Début de la structuration détaillée d'un objet métier.";
            case "STRUCTURE_DATA_END" ->
                    "Fin de la structuration détaillée d'un objet métier.";
            case "FIELD_MAPPING" ->
                    "Mapping d'un champ d'interface vers un attribut métier.";
            case "FIELD_TRIM_REQUIRED" ->
                    "Le champ brut doit être nettoyé avant transformation.";
            case "FIELD_CLASS_CODE" ->
                    "Identification du chemin métier complet de l'attribut.";
            case "FIELD_VALUE_READ" ->
                    "Lecture de la valeur brute provenant de l'interface.";
            case "TYPE_DETECTED" ->
                    "Détection du type métier attendu pour la valeur.";
            case "TYPE_CONVERSION_START" ->
                    "Début de la conversion métier de la valeur.";
            case "TYPE_CONVERSION_RESULT" ->
                    "La conversion a produit un objet typé valide.";
            case "TYPE_CONVERSION_NULL" ->
                    "La conversion renvoie null, souvent pour un champ vide ou optionnel.";
            case "TYPE_CONVERSION_SUCCESS" ->
                    "La conversion de la valeur s'est terminée avec succès.";
            case "FIELD_INSERTED" ->
                    "Le champ transformé a été injecté dans l'objet métier final.";
            case "RELATION_DETECTED" ->
                    "Le système a détecté qu'une relation métier devait être construite.";
            case "NO_RELATION" ->
                    "Le champ courant ne nécessite pas de relation métier.";
            case "RELATION_ADDED" ->
                    "Une première relation métier a été créée pour l'objet courant.";
            case "RELATION_KEY_CREATED" ->
                    "Une clé de relation unique a été générée.";
            case "RELATION_DATA_ATTACHED" ->
                    "Les données liées ont été attachées à la structure finale.";
            case "STRUCTURED_OBJECT_ASSEMBLED" ->
                    "Assemblage final de l'objet structuré avec ses relations et sa BK.";
            case "MANDATORY_CHECK_START" ->
                    "Début du contrôle des champs obligatoires.";
            case "MANDATORY_FIELDS_LIST" ->
                    "Liste des champs obligatoires attendus pour l'objet courant.";
            case "MANDATORY_FIELD_CHECK" ->
                    "Vérification d'un champ obligatoire.";
            case "MANDATORY_CHECK_END" ->
                    "Fin du contrôle des champs obligatoires.";
            case "MANDATORY_CHECK_SUCCESS" ->
                    "L'objet respecte les contraintes de champs obligatoires.";
            case "DETAIL_PARAMETER_EQUALS_MATCH" ->
                    "Application d'une règle métier de matching de type EQUALS.";
            case "DETAIL_PARAMETER_LOOP_START" ->
                    "Début d'itération sur les paramètres de détail métier.";
            case "DETAIL_PARAMETER_LOOP_END" ->
                    "Fin d'itération sur les paramètres de détail métier.";
            case "DETAIL_PARAMETER_SIMPLE_ATTRIBUTE" ->
                    "Le système considère ce champ comme un attribut simple.";
            case "NULL_CONTINUE" ->
                    "Le champ est vide/null mais le traitement continue car il n'est pas bloquant.";
            case "PM_MAPPING_ERROR" ->
                    "Erreur métier : impossible de résoudre correctement une structure PM/PPPM.";
            case "ERROR_DETAILS" ->
                    "Détail complet d'une erreur métier, avec colonne, attribut, valeur et BK.";
            default ->
                    "Message générique de log métier ou technique.";
        };
    }

    private LocalDateTime parseTimestamp(String value) {
        try {
            return LocalDateTime.parse(clean(value), LOG_TIMESTAMP_FORMAT);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timestamp: " + value);
        }
    }

    private Integer parseInteger(String value) {
        try {
            String cleanValue = clean(value);
            return cleanValue == null ? null : Integer.parseInt(cleanValue);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractStepCode(String message) {
        if (message == null) {
            return null;
        }

        Matcher m = STEP_CODE_PATTERN.matcher(message);
        if (m.find()) {
            return m.group(1);
        }

        if (message.startsWith("START")) {
            return "START";
        }
        if (message.startsWith("END")) {
            return "END";
        }

        return null;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}