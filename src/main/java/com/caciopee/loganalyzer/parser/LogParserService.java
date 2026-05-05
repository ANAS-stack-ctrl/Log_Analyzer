package com.caciopee.loganalyzer.parser;

import com.caciopee.loganalyzer.analysis.registry.CentralEventRegistry;
import com.caciopee.loganalyzer.entity.LogEventType;
import com.caciopee.loganalyzer.entity.LogParseQuality;
import com.caciopee.loganalyzer.service.LogBusinessExplanationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class LogParserService {
    private final LogBusinessExplanationService logBusinessExplanationService;

    private static final DateTimeFormatter TS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");

    private final CentralEventRegistry centralEventRegistry;

    public LogParserService(CentralEventRegistry centralEventRegistry,
                            LogBusinessExplanationService logBusinessExplanationService) {
        this.centralEventRegistry = centralEventRegistry;
        this.logBusinessExplanationService = logBusinessExplanationService;
    }

    public ParsedLogEntry parseLine(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Ligne vide");
        }

        ParsedLogEntry entry = new ParsedLogEntry();
        entry.setRawLog(line);

        String[] parts = line.split("\\|", -1);

        if (parts.length < 7) {
            entry.setParseQuality(LogParseQuality.BROKEN);
            entry.setIncomplete(true);
            entry.setMessage(line);
            entry.setRawMessage(line);
            entry.setEventType(LogEventType.UNKNOWN.name());
            entry.setDetectedEventType(LogEventType.UNKNOWN);
            entry.setBusinessMeaning("Format de ligne non reconnu ou incomplet.");
            return entry;
        }

        entry.setLogTimestamp(parseTimestamp(getPart(parts, 0)));
        entry.setExecutionId(blankToNull(getPart(parts, 1)));
        entry.setLevel(blankToNull(getPart(parts, 2)));
        entry.setUserName(blankToNull(getPart(parts, 3)));
        entry.setSourceClass(blankToNull(getPart(parts, 4)));
        entry.setProcessName(blankToNull(getPart(parts, 5)));

        int logCodeIndex = findLogCodeIndex(parts);

        String message = extractMessage(parts, logCodeIndex);
        String technicalId = extractTechnicalId(parts, logCodeIndex);

        entry.setTechnicalId(blankToNull(technicalId));
        entry.setRawMessage(blankToNull(message));
        entry.setMessage(blankToNull(message));
        entry.setMessageTail(null);

        entry.setLogCode(parseInteger(logCodeIndex >= 0 ? getPart(parts, logCodeIndex) : null));

        if (logCodeIndex >= 0) {
            entry.setEnvironment(blankToNull(getPart(parts, logCodeIndex + 3)));
            entry.setServerName(blankToNull(getPart(parts, logCodeIndex + 4)));
            entry.setAppVersion(blankToNull(getPart(parts, logCodeIndex + 5)));
            entry.setCorrelationId(blankToNull(getPart(parts, logCodeIndex + 6)));
        } else {
            entry.setEnvironment(blankToNull(findFromEnd(parts, 4)));
            entry.setServerName(blankToNull(findFromEnd(parts, 3)));
            entry.setAppVersion(blankToNull(findFromEnd(parts, 2)));
            entry.setCorrelationId(blankToNull(findFromEnd(parts, 1)));
        }

        boolean hasEmptyCritical = entry.getLevel() == null
                || entry.getMessage() == null
                || entry.getMessage().isBlank();

        if (hasEmptyCritical) {
            entry.setParseQuality(LogParseQuality.INCOMPLETE);
            entry.setIncomplete(true);
        } else if (entry.getMessage().contains("|")) {
            entry.setParseQuality(LogParseQuality.AMBIGUOUS_MESSAGE);
            entry.setAmbiguousMessage(true);
        } else {
            entry.setParseQuality(LogParseQuality.HEALTHY);
        }

        CentralEventRegistry.DetectionResult detection = centralEventRegistry.detect(
                entry.getMessage(),
                entry.getProcessName(),
                entry.getSourceClass(),
                entry.getLevel()
        );

        entry.setDetectedEventType(detection.eventType());
        entry.setEventType(detection.eventType() != null ? detection.eventType().name() : LogEventType.UNKNOWN.name());

        String defaultMeaning;

        if (detection.businessMeaning() != null && !detection.businessMeaning().isBlank()) {
            defaultMeaning = detection.businessMeaning();
        } else {
            defaultMeaning = buildFallbackBusinessMeaning(detection.eventType(), entry);
        }

        String detailedMeaning = logBusinessExplanationService.explain(
                detection.eventType(),
                entry.getMessage(),
                entry.getProcessName(),
                entry.getSourceClass(),
                entry.getLevel(),
                defaultMeaning
        );

        entry.setBusinessMeaning(detailedMeaning);

        enrichSpecialFields(entry);

        return entry;
    }

    /**
     * Structure générale observée :
     *
     * timestamp|session|level|user|source|process|...message...|logCode|||environment|server|appVersion|correlation|
     *
     * Le message peut commencer à parts[6], parts[7], parts[8] ou parts[9]
     * selon le type de fichier, et il peut contenir lui-même des séparateurs "|".
     */
    private String extractMessage(String[] parts, int logCodeIndex) {
        if (parts == null || parts.length == 0) {
            return "";
        }

        int start = 6;
        int end = logCodeIndex > start ? logCodeIndex : parts.length;

        while (start < end && isBlank(parts[start])) {
            start++;
        }

        String joined = joinParts(parts, start, end).trim();

        if (!joined.isBlank()) {
            return joined;
        }

        for (int i = 6; i < parts.length; i++) {
            String candidate = getPart(parts, i);
            if (!isBlank(candidate) && looksLikeMessage(candidate)) {
                return candidate.trim();
            }
        }

        for (int i = 6; i < parts.length; i++) {
            String candidate = getPart(parts, i);
            if (isBlank(candidate)) continue;

            String c = candidate.trim();

            if (!isInteger(c)
                    && !c.equalsIgnoreCase("DS:ML")
                    && !c.equalsIgnoreCase("DS:dataSourceCenter")
                    && !c.toLowerCase().startsWith("works-")
                    && !c.toLowerCase().startsWith("tomcat")) {
                return c;
            }
        }

        return "";
    }

    private int findLogCodeIndex(String[] parts) {
        if (parts == null || parts.length < 8) {
            return -1;
        }

        /*
         * Cas standard avec pipe final :
         * ... | logCode | | | DS:... | server | version | correlation |
         * split(..., -1) ajoute une dernière case vide.
         */
        int candidate = parts.length - 8;
        if (candidate >= 6 && isInteger(getPart(parts, candidate))) {
            return candidate;
        }

        /*
         * Cas sans pipe final ou petit décalage :
         * on cherche un entier suivi de deux colonnes vides puis d’un environnement DS:...
         */
        for (int i = 6; i < parts.length; i++) {
            if (!isInteger(getPart(parts, i))) continue;

            String p1 = getPart(parts, i + 1);
            String p2 = getPart(parts, i + 2);
            String env = getPart(parts, i + 3);

            if (isBlank(p1) && isBlank(p2) && env != null && env.trim().startsWith("DS:")) {
                return i;
            }
        }

        /*
         * Fallback : entier après le message, avec DS: proche après.
         */
        for (int i = 6; i < parts.length; i++) {
            if (!isInteger(getPart(parts, i))) continue;

            for (int j = i + 1; j < Math.min(parts.length, i + 6); j++) {
                String maybeEnv = getPart(parts, j);
                if (maybeEnv != null && maybeEnv.trim().startsWith("DS:")) {
                    return i;
                }
            }
        }

        return -1;
    }

    private String extractTechnicalId(String[] parts, int logCodeIndex) {
        int end = logCodeIndex > 6 ? logCodeIndex : parts.length;

        for (int i = 6; i < end; i++) {
            String p = getPart(parts, i);
            if (p == null) continue;

            String trimmed = p.trim();

            if (trimmed.matches("\\d+(\\.\\d+)?--.*")) {
                int idx = trimmed.indexOf("--");
                return idx > 0 ? trimmed.substring(0, idx).trim() : trimmed;
            }

            if (trimmed.matches("\\d+(\\.\\d+)?--")) {
                return trimmed.replace("--", "").trim();
            }
        }

        return null;
    }

    private boolean looksLikeMessage(String value) {
        if (value == null) return false;

        String p = value.trim().toLowerCase();

        return p.contains("filter code")
                || p.contains("uuid")
                || p.contains("start fire rules")
                || p.contains("end fire rules")
                || p.contains("aucune règle")
                || p.contains("aucune r")
                || p.contains("running rules")
                || p.contains("saveprocesscontent")
                || p.contains("preparedstatement")
                || p.contains("processname")
                || p.contains("taskname")
                || p.contains("actionname")
                || p.contains("strcturedatasinterface.jar")
                || p.contains("structuredatainterface")
                || p.contains("structuredata")
                || p.contains("structuring ne field")
                || p.contains("fieldclasscode")
                || p.contains("the value of the interface")
                || p.contains("parsing the value")
                || p.contains("finalstructuredobject")
                || p.contains("there is a relation")
                || p.contains("there is no relation")
                || p.contains("the pppm is null")
                || p.contains("the error details are as follows")
                || p.contains("mandatory fields")
                || p.contains("checkmandatoryfieldsdata")
                || p.contains("searchcomposantbyroot")
                || p.contains("preparesearchbyroot")
                || p.contains("searchattributeslist")
                || p.contains("searchtostringmapping")
                || p.contains("loadlistchilds")
                || p.contains("saveorupdate")
                || p.contains("memory usage")
                || p.contains("query:")
                || p.contains("row fetched")
                || p.contains("took [")
                || p.contains("trcheckpoint")
                || p.contains("trigger")
                || p.contains("session closed")
                || p.contains("multiThreading".toLowerCase())
                || p.contains("start filtre")
                || p.contains("end filtre")
                || p.contains("start ws_")
                || p.contains("end ws_")
                || p.contains("aggregate data")
                || p.contains("rendering result")
                || p.contains("insertion reussie")
                || p.contains("listvehicules size")
                || p.contains("listconducteurs size")
                || p.contains("tryptique reference");
    }

    private void enrichSpecialFields(ParsedLogEntry entry) {
        String msg = safe(entry.getMessage());
        String lower = msg.toLowerCase();

        if (lower.contains("0 row fetched") || lower.contains("|0 row") || lower.contains("[0] row fetched")) {
            entry.setParsedValue("0");
        }

        if (lower.contains("mandatory")) {
            entry.setMandatoryField("DETECTED_FROM_MESSAGE");
        }

        if (lower.contains("relation")) {
            entry.setRelationName("RELATION_DETECTED_FROM_MESSAGE");
        }

        if (lower.contains("structuring ne field works")) {
            entry.setFieldName(extractBetween(msg, "works = [", "]"));
            entry.setInterfaceField(extractBetween(msg, "interface = [", "]"));
        }

        if (lower.contains("{fieldclasscode}")) {
            entry.setFieldClassCode(extractBetween(msg, "{fieldClassCode} = [", "]"));
        }

        if (lower.contains("the value of the interface")) {
            entry.setParsedValue(extractBetween(msg, "= [", "]"));
        }

        if (lower.contains("parsing the value of the interface to type")) {
            entry.setParsedType(extractBetween(msg, "type = [", "]"));
        }

        if (lower.contains("put key") && lower.contains("finalstructuredobject")) {
            entry.setFieldName(extractBetween(msg, "put key [", "]"));
            entry.setParsedValue(extractBetween(msg, "value [", "]"));
        }

        if (lower.contains("the error details are as follows")) {
            entry.setErrorColumn(extractBetween(msg, "the column is => [", "]"));
            entry.setErrorAttribute(extractBetween(msg, "the attribute => [", "]"));
            entry.setErrorValue(extractBetween(msg, "the value => [", "]"));
            entry.setErrorBusinessKey(extractBetween(msg, "the BK => [", "]"));

            entry.setFieldName(entry.getErrorAttribute());
            entry.setParsedValue(entry.getErrorValue());
            entry.setBusinessKey(entry.getErrorBusinessKey());
            entry.setError(true);
        }

        if (lower.contains("the pppm is null")) {
            entry.setError(true);
        }
    }

    private String buildFallbackBusinessMeaning(LogEventType eventType, ParsedLogEntry entry) {
        if (eventType == null) {
            return "Événement non identifié.";
        }

        return switch (eventType) {
            case WORKFLOW_LOOP_START -> "Début du parcours des objets d’interface à structurer.";
            case WORKFLOW_NEW_INTERFACE_OBJECT -> "Un nouvel objet d’interface est en cours de traitement.";
            case STRUCTURE_DATA_START -> "Début de structuration d’un objet métier.";
            case STRUCTURE_DATA_END -> "Fin de structuration d’un objet métier.";
            case STRUCTURING_FIELD_START -> "Début du mapping d’un champ d’interface vers un champ métier WORKS.";
            case FIELD_TRIM_REQUIRED -> "Nettoyage de la valeur du champ avant conversion.";
            case FIELD_CLASS_CODE_DETECTED -> "Le code interne du champ métier a été identifié.";
            case FIELD_VALUE_READ -> "Lecture d’une valeur depuis l’interface.";
            case TYPE_CONVERSION_START -> "Conversion de la valeur de l’interface vers le type attendu.";
            case TYPE_CONVERSION_NULL -> "La conversion a retourné une valeur nulle.";
            case TYPE_CONVERSION_SUCCESS -> "Conversion de type réussie.";
            case FINAL_OBJECT_FIELD_PUT -> "Champ injecté dans l’objet métier final.";
            case STRUCTURING_RELATION_PRESENT -> "Une relation métier doit être construite.";
            case STRUCTURING_RELATION_ABSENT -> "Aucune relation métier à construire pour ce champ.";
            case STRUCTURING_RELATION_FIRST_ADDED -> "Première relation métier ajoutée.";
            case STRUCTURING_RELATION_KEY_CREATED -> "Clé de relation métier créée.";
            case STRUCTURING_RELATION_KEY_ATTACHED -> "Clé de relation rattachée à l’objet.";
            case STRUCTURING_RELATION_DATA_ATTACHED -> "Données de relation ajoutées à l’objet métier.";
            case BUSINESS_KEY_NULL -> "La clé métier de l’objet est nulle ou absente.";
            case MANDATORY_CHECK_START -> "Début de vérification des champs obligatoires.";
            case MANDATORY_FIELDS_LIST -> "Liste des champs obligatoires identifiée.";
            case MANDATORY_FIELD_CHECK -> "Vérification d’un champ obligatoire.";
            case MANDATORY_CHECK_SUCCESS -> "L’objet respecte les champs obligatoires.";
            case PM_MAPPING_ERROR -> "Erreur métier : une donnée PP/PM nécessaire au mapping est absente.";
            case ERROR_DETAILS -> "Détails de l’erreur métier détectés : colonne, attribut, valeur et clé métier impactée.";
            case RULES_RUNNING -> "Début d’exécution des règles métier.";
            case RULES_FINISHED -> "Fin d’exécution des règles métier.";
            case RULE_NOT_FOUND -> "Aucune règle métier applicable n’a été trouvée.";
            case RULE_WARNING -> "Avertissement lié aux règles métier.";
            case TRIGGER_MISFIRED -> "Un déclencheur système semble concerné.";
            case SERVICE_START -> "Début d’exécution d’un service web.";
            case SERVICE_END -> "Fin d’exécution d’un service web.";
            case FILTER_START -> "Début d’un filtre métier.";
            case FILTER_END -> "Fin d’un filtre métier.";
            case SQL_QUERY -> "Une requête SQL a été exécutée.";
            case SEARCH_RESULT_FETCHED -> "Des résultats ont été récupérés par le système.";
            case ZERO_ROW_FETCHED -> "La recherche n’a retourné aucune ligne.";
            case MEMORY_USAGE -> "Une mesure de mémoire a été enregistrée.";
            case PERFORMANCE_MEASURE -> "Une durée d’exécution a été mesurée.";
            case PROCESS_CONTENT_SAVE -> "Le contenu du traitement a été sauvegardé.";
            case FIELD_MAPPING -> "Une étape de mapping de champ a été détectée.";
            case RELATION_DETECTED -> "Une relation métier a été détectée.";
            case GENERIC_ERROR -> "Une erreur a été détectée.";
            case NULL_CONTINUE -> "Le traitement continue malgré une valeur nulle.";
            default -> "Événement détecté dans les logs.";
        };
    }

    private LocalDateTime parseTimestamp(String value) {
        try {
            return value == null || value.isBlank()
                    ? null
                    : LocalDateTime.parse(value.trim(), TS_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        try {
            return value == null || value.isBlank()
                    ? null
                    : Integer.parseInt(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String joinParts(String[] parts, int start, int end) {
        if (parts == null || start >= end || start >= parts.length) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        int realEnd = Math.min(end, parts.length);

        for (int i = Math.max(0, start); i < realEnd; i++) {
            if (i > start) {
                sb.append("|");
            }
            sb.append(parts[i] == null ? "" : parts[i]);
        }

        return sb.toString();
    }

    private String extractBetween(String text, String start, String end) {
        if (text == null || start == null || end == null) return null;

        int s = text.indexOf(start);
        if (s < 0) return null;

        int valueStart = s + start.length();
        int valueEnd = text.indexOf(end, valueStart);
        if (valueEnd < 0) return null;

        String value = text.substring(valueStart, valueEnd).trim();
        return value.isBlank() ? null : value;
    }

    private boolean isInteger(String value) {
        if (value == null || value.isBlank()) return false;

        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String findFromEnd(String[] parts, int offsetFromEnd) {
        if (parts == null || parts.length == 0) return null;
        int index = parts.length - offsetFromEnd;
        return getPart(parts, index);
    }

    private String getPart(String[] parts, int index) {
        return index >= 0 && index < parts.length ? parts[index] : null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}