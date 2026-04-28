package com.caciopee.loganalyzer.parser;

import com.caciopee.loganalyzer.analysis.registry.CentralEventRegistry;
import com.caciopee.loganalyzer.entity.LogEventType;
import com.caciopee.loganalyzer.entity.LogParseQuality;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class LogParserService {

    private static final DateTimeFormatter TS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");

    private final CentralEventRegistry centralEventRegistry;

    public LogParserService(CentralEventRegistry centralEventRegistry) {
        this.centralEventRegistry = centralEventRegistry;
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
            entry.setEventType(LogEventType.UNKNOWN.name());
            entry.setDetectedEventType(LogEventType.UNKNOWN);
            entry.setBusinessMeaning("Format de ligne non reconnu ou incomplet.");
            return entry;
        }

        // ===== Extraction technique de base =====
        entry.setLogTimestamp(parseTimestamp(parts[0]));
        entry.setExecutionId(blankToNull(getPart(parts, 1)));
        entry.setLevel(blankToNull(getPart(parts, 2)));
        entry.setUserName(blankToNull(getPart(parts, 3)));
        entry.setSourceClass(blankToNull(getPart(parts, 4)));
        entry.setProcessName(blankToNull(getPart(parts, 5)));

        // Message central : adapte-le si ton format réel est plus riche
        String rawMessage = getPart(parts, 6);
        String cleanedMiddle = rawMessage;
        String message = cleanedMiddle;
        String technicalId = null;
        String messageTail = null;

        entry.setTechnicalId(blankToNull(technicalId));
        entry.setRawMessage(blankToNull(cleanedMiddle));
        entry.setMessage(blankToNull(message));
        entry.setMessageTail(blankToNull(messageTail));

        entry.setLogCode(parseInteger(getPart(parts, 8)));
        entry.setEnvironment(blankToNull(getPart(parts, 11)));
        entry.setServerName(blankToNull(getPart(parts, 12)));
        entry.setAppVersion(blankToNull(getPart(parts, 13)));
        entry.setCorrelationId(blankToNull(getPart(parts, 14)));

        // ===== Qualité de parsing =====
        boolean hasEmptyCritical = entry.getLevel() == null
                || entry.getProcessName() == null
                || entry.getMessage() == null;

        if (hasEmptyCritical) {
            entry.setParseQuality(LogParseQuality.INCOMPLETE);
            entry.setIncomplete(true);
        } else {
            entry.setParseQuality(LogParseQuality.HEALTHY);
        }

        if (entry.getMessage() != null && entry.getMessage().contains("|")) {
            entry.setAmbiguousMessage(true);
            if (entry.getParseQuality() == LogParseQuality.HEALTHY) {
                entry.setParseQuality(LogParseQuality.AMBIGUOUS_MESSAGE);
            }
        }

        // ===== Détection centralisée =====
        CentralEventRegistry.DetectionResult detection = centralEventRegistry.detect(
                entry.getMessage(),
                entry.getProcessName(),
                entry.getSourceClass(),
                entry.getLevel()
        );

        entry.setDetectedEventType(detection.eventType());

        if (detection.businessMeaning() != null && !detection.businessMeaning().isBlank()) {
            entry.setBusinessMeaning(detection.businessMeaning());
        } else {
            entry.setBusinessMeaning(buildFallbackBusinessMeaning(detection.eventType(), entry));
        }

        // ===== Fallback parser existant enrichi =====
        enrichSpecialFields(entry);

        return entry;
    }

    private void enrichSpecialFields(ParsedLogEntry entry) {
        String msg = safe(entry.getMessage()).toLowerCase();

        if (msg.contains("0 row fetched")) {
            entry.setParsedValue("0");
        }

        if (msg.contains("mandatory")) {
            entry.setMandatoryField("DETECTED_FROM_MESSAGE");
        }

        if (msg.contains("relation")) {
            entry.setRelationName("RELATION_DETECTED_FROM_MESSAGE");
        }

        if (msg.contains("mapping")) {
            entry.setFieldName("FIELD_DETECTED_FROM_MESSAGE");
        }
    }

    private String buildFallbackBusinessMeaning(LogEventType eventType, ParsedLogEntry entry) {
        if (eventType == null) {
            return "Événement non identifié.";
        }

        return switch (eventType) {
            case RULES_RUNNING -> "Début d’exécution des règles métier.";
            case RULE_NOT_FOUND -> "Aucune règle métier applicable n’a été trouvée.";
            case TRIGGER_MISFIRED -> "Un déclencheur système semble concerné.";
            case SERVICE_START -> "Début d’exécution d’un service web.";
            case SERVICE_END -> "Fin d’exécution d’un service web.";
            case FILTER_START -> "Début d’un filtre métier.";
            case FILTER_END -> "Fin d’un filtre métier.";
            case SQL_QUERY -> "Une requête SQL a été exécutée.";
            case SEARCH_RESULT_FETCHED -> "Des résultats ont été récupérés par le système.";
            case ZERO_ROW_FETCHED -> "La recherche n’a retourné aucune ligne.";
            case MEMORY_USAGE -> "Une mesure de mémoire a été enregistrée.";
            case PROCESS_CONTENT_SAVE -> "Le contenu du traitement a été sauvegardé.";
            case FIELD_MAPPING -> "Une étape de mapping de champ a été détectée.";
            case RELATION_DETECTED -> "Une relation métier a été détectée.";
            case MANDATORY_FIELDS_LIST -> "Des champs obligatoires ont été identifiés.";
            case ERROR_DETAILS, PM_MAPPING_ERROR, GENERIC_ERROR -> "Une erreur a été détectée.";
            case NULL_CONTINUE -> "Le traitement continue malgré une valeur nulle.";
            default -> "Événement détecté dans les logs.";
        };
    }

    private LocalDateTime parseTimestamp(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDateTime.parse(value.trim(), TS_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String getPart(String[] parts, int index) {
        return index >= 0 && index < parts.length ? parts[index] : null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}