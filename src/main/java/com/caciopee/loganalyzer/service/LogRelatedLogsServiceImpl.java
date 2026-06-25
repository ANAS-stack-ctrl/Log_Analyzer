package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.RelatedContextRequestDto;
import com.caciopee.loganalyzer.dto.RelatedLogDto;
import com.caciopee.loganalyzer.dto.RelatedLogsRequestDto;
import com.caciopee.loganalyzer.dto.RelatedLogsResponseDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogEntrySpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class LogRelatedLogsServiceImpl implements LogRelatedLogsService {

    private final LogEntryRepository logEntryRepository;

    public LogRelatedLogsServiceImpl(LogEntryRepository logEntryRepository) {
        this.logEntryRepository = logEntryRepository;
    }

    @Override
    public RelatedLogsResponseDto getRelatedLogs(RelatedLogsRequestDto request) {
        int limit = normalizeLimit(request.getLimit());

        Specification<LogEntry> spec = Specification
                .where(LogEntrySpecifications.hasImportIds(request.getImportIds()))
                .and(LogEntrySpecifications.hasTimestampBetween(request.getDateFrom(), request.getDateTo()))
                .and(groupSpec(request.getGroupBy(), request.getGroupKey()))
                .and(itemSpec(request.getItemType(), request.getItemName()));

        Sort sort = Sort.by(Sort.Direction.ASC, "logTimestamp")
                .and(Sort.by(Sort.Direction.ASC, "id"));

        List<LogEntry> logs;

        if (limit == 0) {
            logs = logEntryRepository.findAll(spec, sort);
        } else {
            logs = logEntryRepository.findAll(spec, PageRequest.of(0, limit, sort)).getContent();
        }

        RelatedLogsResponseDto response = new RelatedLogsResponseDto();
        response.setTitle(buildTitle(request));
        response.setTotal((long) logs.size());
        response.setSummary(buildSummary(request, logs));
        response.setLogs(logs.stream().map(log -> toDto(log, request)).toList());

        return response;
    }

    @Override
    public RelatedLogsResponseDto getContextAround(RelatedContextRequestDto request) {
        if (request == null || request.getCenterLogId() == null) {
            throw new IllegalArgumentException("Log central obligatoire.");
        }

        LogEntry center = logEntryRepository.findById(request.getCenterLogId())
                .orElseThrow(() -> new IllegalArgumentException("Log central introuvable."));

        Specification<LogEntry> spec = Specification
                .where(LogEntrySpecifications.hasImportIds(request.getImportIds()))
                .and(groupSpec(request.getGroupBy(), request.getGroupKey()));

        Sort sort = Sort.by(Sort.Direction.ASC, "logTimestamp")
                .and(Sort.by(Sort.Direction.ASC, "id"));

        List<LogEntry> all = logEntryRepository.findAll(spec, sort);

        int index = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(center.getId())) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            throw new IllegalArgumentException("Le log central n’appartient pas au groupe demandé.");
        }

        int before = request.getBefore() == null ? 20 : Math.max(0, Math.min(request.getBefore(), 100));
        int after = request.getAfter() == null ? 20 : Math.max(0, Math.min(request.getAfter(), 100));

        int from = Math.max(0, index - before);
        int to = Math.min(all.size(), index + after + 1);

        List<LogEntry> contextLogs = all.subList(from, to);

        RelatedLogsResponseDto response = new RelatedLogsResponseDto();
        response.setTitle("Contexte avant/après le log " + center.getId());
        response.setTotal((long) contextLogs.size());
        response.setSummary("Affichage de " + before + " log(s) avant et " + after + " log(s) après le log sélectionné.");
        response.setChronologicalExplanation(buildChronologicalExplanationFromContext(center, contextLogs));
        response.setLogs(contextLogs.stream().map(log -> toDto(log, null)).toList());

        return response;
    }

    private Specification<LogEntry> groupSpec(String groupBy, String groupKey) {
        if (groupBy == null || groupKey == null || groupKey.isBlank() || "NON_RENSEIGNE".equalsIgnoreCase(groupKey)) {
            return null;
        }

        return switch (groupBy) {
            case "userName" -> LogEntrySpecifications.hasUserName(groupKey);
            case "processName" -> LogEntrySpecifications.hasProcessName(groupKey);
            case "sessionId" -> LogEntrySpecifications.hasSessionId(groupKey);
            case "eventType" -> LogEntrySpecifications.hasEventType(groupKey);
            case "sourceFileName", "fileName" -> LogEntrySpecifications.hasFileName(groupKey);
            case "uuid", "businessKey", "correlationId" -> LogEntrySpecifications.hasUuid(groupKey);
            default -> null;
        };
    }

    private Specification<LogEntry> itemSpec(String itemType, String itemName) {
        return (root, query, cb) -> {
            if (itemType == null || itemName == null || itemName.isBlank()) {
                return null;
            }

            String type = itemType.trim().toUpperCase(Locale.ROOT);
            String value = itemName.trim().toLowerCase(Locale.ROOT);
            String like = "%" + value + "%";

            var msg = cb.lower(root.get("message"));
            var raw = cb.lower(root.get("rawLog"));
            var process = cb.lower(root.get("processName"));
            var eventType = cb.lower(root.get("eventType"));

            return switch (type) {
                case "PROCESS" -> cb.or(
                        cb.like(process, like),
                        cb.like(msg, "%processname [" + value + "]%"),
                        cb.like(msg, like)
                );

                case "FILTER" -> cb.or(
                        cb.like(msg, "%filter code [" + value + "]%"),
                        cb.like(msg, "%filter [" + value + "]%"),
                        cb.like(msg, like)
                );

                case "ACTION" -> cb.or(
                        cb.like(msg, "%actionname [" + value + "]%"),
                        cb.like(msg, "%transition=" + value + "%"),
                        cb.like(msg, "%|" + value + "|%"),
                        cb.like(msg, like)
                );

                case "BUSINESS_OBJECT", "OBJECT" -> cb.or(
                        cb.like(msg, "%classname [" + value + "]%"),
                        cb.like(msg, like)
                );

                case "WARNING" -> cb.or(
                        cb.like(msg, "%no rule found%"),
                        cb.like(msg, "%aucune r%"),
                        cb.like(eventType, "%warn%"),
                        cb.like(msg, like)
                );

                case "ZERO_RESULT" -> cb.or(
                        cb.like(msg, "%0 row%"),
                        cb.like(msg, "%[0] row fetched%"),
                        cb.like(msg, "%0] row%")
                );

                case "PERFORMANCE", "MEMORY", "PERFORMANCE_ANOMALY" -> cb.or(
                        cb.like(msg, "%memory usage%"),
                        cb.like(msg, "%took%"),
                        cb.like(msg, like)
                );

                case "MEMORY_ANOMALY" -> cb.like(msg, "%memory usage%");

                default -> cb.or(
                        cb.like(msg, like),
                        cb.like(raw, like)
                );
            };
        };
    }

    private RelatedLogDto toDto(LogEntry log, RelatedLogsRequestDto request) {
        RelatedLogDto dto = new RelatedLogDto();

        dto.setId(log.getId());
        dto.setTimestamp(log.getLogTimestamp());
        dto.setLevel(log.getLevel());
        dto.setUserName(log.getUserName());
        dto.setProcessName(log.getProcessName());
        dto.setSourceFileName(resolveFileName(log));
        dto.setEventType(log.getEventType());
        dto.setMessage(log.getMessage());
        dto.setBusinessMeaning(log.getBusinessMeaning());
        dto.setDetailedExplanation(buildDetailedExplanation(log, request));

        return dto;
    }

    private String buildDetailedExplanation(LogEntry log, RelatedLogsRequestDto request) {
        String msg = safe(log.getMessage()).toLowerCase(Locale.ROOT);

        String itemType = "";
        String itemName = "";

        if (request != null) {
            itemType = safe(request.getItemType()).toUpperCase(Locale.ROOT);
            itemName = safe(request.getItemName());
        }

        StringBuilder sb = new StringBuilder();

        if (request != null) {
            sb.append("Ce log appartient à l’analyse de ")
                    .append(itemType.isBlank() ? "cet élément" : itemType)
                    .append(itemName.isBlank() ? "" : " [" + itemName + "]")
                    .append(". ");
        } else {
            sb.append("Ce log fait partie du contexte chronologique autour du moment sélectionné. ");
        }

        if (log.getBusinessMeaning() != null && !log.getBusinessMeaning().isBlank()) {
            sb.append(log.getBusinessMeaning()).append(" ");
        }

        if (msg.contains("searchcomposantbyroot start execute")) {
            sb.append("Il indique le début d’une recherche métier. Le système prépare l’exécution du filtre associé.");
        } else if (msg.contains("preparesearchbyroot")) {
            sb.append("Le système prépare la requête de recherche et identifie l’objet métier concerné.");
        } else if (msg.contains("query:")) {
            sb.append("Le système exécute une requête SQL pour retrouver les objets métier correspondant aux critères.");
        } else if (msg.contains("0 row")) {
            sb.append("La recherche ne retourne aucun résultat. Ce point doit être vérifié si un objet était attendu.");
        } else if (msg.contains("row fetched")) {
            sb.append("La recherche retourne des résultats, ce qui signifie que l’objet ou les objets métier ont été retrouvés.");
        } else if (msg.contains("no rule found") || msg.contains("aucune r")) {
            sb.append("Aucune règle n’est configurée pour cette transition. Ce n’est pas forcément bloquant, mais c’est un point à vérifier côté règles métier.");
        } else if (msg.contains("start fire rules")) {
            sb.append("Le moteur de règles démarre l’évaluation des règles liées au process, à la task ou à l’action.");
        } else if (msg.contains("end fire rules")) {
            sb.append("Le moteur de règles termine son exécution.");
        } else if (msg.contains("save") || msg.contains("insertarray") || msg.contains("removeobjectstodelete")) {
            sb.append("Le système effectue une opération de sauvegarde ou de mise à jour des données métier.");
        } else if (msg.contains("memory usage")) {
            sb.append("Ce log indique une mesure de mémoire. Une valeur élevée peut être utile pour le diagnostic performance.");
        } else if (msg.contains("took")) {
            sb.append("Ce log contient une durée d’exécution. Il peut aider à identifier une étape lente.");
        } else {
            sb.append("Ce log fournit un détail technique lié au déroulement du traitement.");
        }

        return sb.toString();
    }

    private String buildChronologicalExplanationFromContext(LogEntry center, List<LogEntry> logs) {
        StringBuilder sb = new StringBuilder();

        sb.append("Contexte chronologique autour du log sélectionné :\n\n");

        for (LogEntry log : logs) {
            String marker = log.getId().equals(center.getId()) ? " >>> MOMENT SÉLECTIONNÉ : " : " - ";
            sb.append(marker)
                    .append(log.getLogTimestamp())
                    .append(" — ")
                    .append(shortMessage(log.getMessage()))
                    .append("\n");
        }

        sb.append("\nLecture analyste : le log sélectionné doit être interprété avec les événements juste avant et juste après. ");
        sb.append("Cela permet de comprendre ce qui a déclenché cette partie, puis ce que le système a fait ensuite.");

        return sb.toString();
    }

    private String buildTitle(RelatedLogsRequestDto request) {
        return "Logs liés à "
                + safe(request.getItemType())
                + " : "
                + safe(request.getItemName());
    }

    private String buildSummary(RelatedLogsRequestDto request, List<LogEntry> logs) {
        if (logs.isEmpty()) {
            return "Aucun log lié trouvé pour cet élément.";
        }

        long errors = logs.stream()
                .filter(l -> Boolean.TRUE.equals(l.getIsError()) || "ERROR".equalsIgnoreCase(safe(l.getLevel())))
                .count();

        long warnings = logs.stream()
                .filter(l -> "WARN".equalsIgnoreCase(safe(l.getLevel()))
                        || safe(l.getMessage()).toLowerCase(Locale.ROOT).contains("no rule found")
                        || safe(l.getMessage()).toLowerCase(Locale.ROOT).contains("aucune r"))
                .count();

        long zeroRows = logs.stream()
                .filter(l -> safe(l.getMessage()).toLowerCase(Locale.ROOT).contains("0 row"))
                .count();

        return "Total logs liés : " + logs.size()
                + ". Erreurs : " + errors
                + ". Warnings/règles absentes : " + warnings
                + ". Recherches sans résultat : " + zeroRows
                + ".";
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) return 300;
        if (limit == 0) return 0;
        if (limit < 0) return 300;
        return Math.min(limit, 5000);
    }

    private String resolveFileName(LogEntry log) {
        if (log.getSourceFileName() != null && !log.getSourceFileName().isBlank()) {
            return log.getSourceFileName();
        }
        return log.getLogImport() != null ? log.getLogImport().getFileName() : null;
    }

    private String shortMessage(String value) {
        if (value == null) return "";
        String v = value.replaceAll("\\s+", " ").trim();
        return v.length() <= 220 ? v : v.substring(0, 217) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}