package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.guidance.LogFileTypeDetector;
import com.caciopee.loganalyzer.analysis.guidance.LogFileTypeKind;
import com.caciopee.loganalyzer.dto.ImportLogTypeSummaryDto;
import com.caciopee.loganalyzer.dto.LogAnalysisGuidanceResponseDto;
import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogImportRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class LogAnalysisGuidanceService {

    private static final Map<String, String> GROUP_BY_LABELS = Map.ofEntries(
            Map.entry("sessionId", "Session technique (timestamp)"),
            Map.entry("userName", "Utilisateur"),
            Map.entry("processName", "Process Name"),
            Map.entry("eventType", "Event Type"),
            Map.entry("level", "Level"),
            Map.entry("sourceFileName", "Fichier source"),
            Map.entry("uuid", "UUID transaction"),
            Map.entry("businessKey", "Business Key"),
            Map.entry("correlationId", "Correlation ID")
    );

    private final LogImportRepository logImportRepository;
    private final LogEntryRepository logEntryRepository;

    public LogAnalysisGuidanceService(LogImportRepository logImportRepository,
                                      LogEntryRepository logEntryRepository) {
        this.logImportRepository = logImportRepository;
        this.logEntryRepository = logEntryRepository;
    }

    public LogAnalysisGuidanceResponseDto buildGuidance(List<Long> importIds) {
        if (importIds == null || importIds.isEmpty()) {
            return emptyResponse("Saisissez un ou plusieurs Import IDs pour obtenir une suggestion.");
        }

        List<ImportLogTypeSummaryDto> perImport = new ArrayList<>();
        Map<LogFileTypeKind, Integer> typeCounts = new EnumMap<>(LogFileTypeKind.class);

        for (Long importId : importIds) {
            if (importId == null) {
                continue;
            }
            logImportRepository.findById(importId).ifPresent(imp -> {
                ImportLogTypeSummaryDto summary = buildImportSummary(imp, importIds.size());
                perImport.add(summary);
                LogFileTypeKind kind = LogFileTypeKind.valueOf(summary.getTypeId());
                typeCounts.merge(kind, 1, Integer::sum);
            });
        }

        if (perImport.isEmpty()) {
            return emptyResponse("Aucun import trouvé pour les IDs fournis.");
        }

        LogFileTypeKind dominant = resolveDominantType(typeCounts);
        TypeProfile profile = profileFor(dominant);
        ImportStats aggregate = aggregateStats(perImport);

        int importCount = importIds.size();
        String suggestedGroupBy = resolveSuggestedGroupBy(dominant, importCount, aggregate, typeCounts);
        String suggestedLabel = labelForGroupBy(suggestedGroupBy);

        LogAnalysisGuidanceResponseDto response = new LogAnalysisGuidanceResponseDto();
        response.setImportCount(perImport.size());
        response.setDominantTypeId(dominant.name());
        response.setDominantTypeLabel(profile.label());
        response.setSuggestedGroupBy(suggestedGroupBy);
        response.setSuggestedGroupByLabel(suggestedLabel);
        response.setTypeCounts(toTypeCountMap(typeCounts));
        response.setExtractableInfos(mergeUnique(profile.extractableInfos(), perImport));
        response.setAvoidGroupByHints(mergeAvoidHints(dominant, importCount, aggregate, typeCounts));
        response.setTips(mergeUnique(profile.tips(), perImport));
        response.setImports(perImport);
        response.setSummary(buildSummary(dominant, profile, importCount, suggestedLabel, typeCounts));
        return response;
    }

    private ImportLogTypeSummaryDto buildImportSummary(LogImport imp, int totalImportCount) {
        long importId = imp.getId();
        String fileName = firstNonBlank(imp.getOriginalFileName(), imp.getFileName());
        long totalLogs = logEntryRepository.countByLogImportId(importId);

        LogFileTypeKind fromName = LogFileTypeDetector.fromFileName(fileName);
        String dominantLogger = dominantSourceClass(importId);
        LogFileTypeKind kind = LogFileTypeDetector.refineFromDominantLogger(fromName, dominantLogger);

        ImportStats stats = loadStats(importId, totalLogs);
        TypeProfile profile = profileFor(kind);
        String suggestedGroupBy = resolveSuggestedGroupBy(kind, totalImportCount, stats, Map.of(kind, 1));

        ImportLogTypeSummaryDto dto = new ImportLogTypeSummaryDto();
        dto.setImportId(importId);
        dto.setFileName(fileName);
        dto.setTypeId(kind.name());
        dto.setTypeLabel(profile.label());
        dto.setTypeDescription(profile.description());
        dto.setTotalLogs(totalLogs);
        dto.setSuggestedGroupBy(suggestedGroupBy);
        dto.setSuggestedGroupByLabel(labelForGroupBy(suggestedGroupBy));
        dto.setExtractableInfos(new ArrayList<>(profile.extractableInfos()));
        dto.setAvoidGroupByHints(avoidHintsFor(kind, totalImportCount, stats));
        dto.setTips(new ArrayList<>(profile.tips()));
        return dto;
    }

    private ImportStats loadStats(Long importId, long totalLogs) {
        List<Object[]> users = logEntryRepository.countUsersByImport(importId);
        int distinctUsers = users.size();
        int realUsers = 0;
        for (Object[] row : users) {
            String name = row[0] == null ? "" : row[0].toString();
            if (!LogFileTypeDetector.isServiceAccountUser(name)) {
                realUsers++;
            }
        }

        int sessionCount = logEntryRepository.countSessionsByImport(importId).size();
        int correlationCount = logEntryRepository.countCorrelationIdsByImport(importId).size();
        long uuidLines = logEntryRepository.countUuidTaggedLinesByImport(importId);
        double uuidRatio = totalLogs <= 0 ? 0.0 : (double) uuidLines / totalLogs;

        return new ImportStats(distinctUsers, realUsers, sessionCount, correlationCount, uuidRatio, totalLogs);
    }

    private String dominantSourceClass(Long importId) {
        List<Object[]> rows = logEntryRepository.countSourceClassesByImport(importId);
        if (rows.isEmpty() || rows.get(0)[0] == null) {
            return null;
        }
        return rows.get(0)[0].toString();
    }

    private String resolveSuggestedGroupBy(LogFileTypeKind kind,
                                           int importCount,
                                           ImportStats stats,
                                           Map<LogFileTypeKind, Integer> typeCounts) {
        boolean multiImport = importCount > 1;
        boolean mostlyTomcat = typeCounts.getOrDefault(LogFileTypeKind.TOMCAT, 0) >= Math.max(1, importCount / 2);

        return switch (kind) {
            case METIER -> stats.realUsers >= 2 ? "userName" : "sessionId";
            case TOMCAT -> {
                if (multiImport && mostlyTomcat) {
                    yield "sourceFileName";
                }
                if (stats.sessionCount <= 1) {
                    yield "level";
                }
                yield "sessionId";
            }
            case WEBSERVICE -> stats.uuidRatio >= 0.05 ? "uuid" : "sessionId";
            case PERF, MEM_PERF -> multiImport ? "sourceFileName" : "level";
            case SAVE_LOAD -> multiImport ? "sourceFileName" : "eventType";
            case SERVER -> "level";
            case MIXED -> multiImport ? "sourceFileName" : "sessionId";
            case UNKNOWN -> multiImport ? "sourceFileName" : "sessionId";
        };
    }

    private List<String> avoidHintsFor(LogFileTypeKind kind, int importCount, ImportStats stats) {
        List<String> hints = new ArrayList<>();
        if (kind == LogFileTypeKind.TOMCAT || kind == LogFileTypeKind.MEM_PERF) {
            hints.add("Utilisateur — souvent admin.ad ou anonymous sur Tomcat.");
        }
        if (importCount > 1 && (kind == LogFileTypeKind.TOMCAT || stats.sessionCount <= 2)) {
            hints.add("Session technique — peut être identique sur plusieurs fichiers Tomcat batch.");
            hints.add("Correlation ID — souvent identique entre imports du même lot.");
        }
        if (stats.uuidRatio < 0.02 && kind == LogFileTypeKind.TOMCAT) {
            hints.add("UUID transaction — rarement présent dans les logs de structuration pure.");
        }
        return hints;
    }

    private List<String> mergeAvoidHints(LogFileTypeKind dominant,
                                         int importCount,
                                         ImportStats aggregate,
                                         Map<LogFileTypeKind, Integer> typeCounts) {
        Set<String> merged = new LinkedHashSet<>(avoidHintsFor(dominant, importCount, aggregate));
        if (typeCounts.getOrDefault(LogFileTypeKind.TOMCAT, 0) > 0 && importCount > 1) {
            merged.add("Sur un lot Tomcat multi-fichiers, préférez Fichier source plutôt que Session ou Utilisateur.");
        }
        return new ArrayList<>(merged);
    }

    private TypeProfile profileFor(LogFileTypeKind kind) {
        return switch (kind) {
            case METIER -> new TypeProfile(
                    "Logs Métier (écrans WORKS)",
                    "Actions utilisateur sur les écrans : filtres, process métier, règles, recherches.",
                    "userName",
                    List.of(
                            "Utilisateur (qui a agi)",
                            "Session technique et UUID transaction",
                            "Process, tâche, action, code filtre / règle",
                            "Objets métier (className, noDUM, refDemande, amp…)",
                            "Durées, mémoire, 0 ligne, règles manquantes",
                            "Checkpoints parcours (ENTREE_/SORTIE_)"
                    ),
                    List.of(
                            "Regrouper par Utilisateur pour voir l'activité par personne.",
                            "Recherche transversale : UUID, filtre, champs métier."
                    )
            );
            case TOMCAT -> new TypeProfile(
                    "Logs Tomcat (structuration serveur)",
                    "Traitement batch côté serveur : mapping interface → objet WORKS (structureData).",
                    "sourceFileName",
                    List.of(
                            "Champs structurés (works =, interface =, put key, fieldClassCode)",
                            "Relations métier et erreurs de mapping / obligatoires",
                            "Étapes StructureDataInterface (START/END méthode)",
                            "Process batch (processRunRules, DATA_LOAD_TASK)",
                            "Filtres / règles Java si fichier mixte (filter code, transactionId)"
                    ),
                    List.of(
                            "Plusieurs imports Tomcat : regrouper par Fichier source.",
                            "Un seul fichier batch : regrouper par Level pour isoler les erreurs.",
                            "Lier au Métier via UUID / transactionId quand présents dans le message."
                    )
            );
            case WEBSERVICE -> new TypeProfile(
                    "Logs Web service",
                    "Appels WS WORKS : filtres distants, règles, traçabilité.",
                    "sessionId",
                    List.of(
                            "Services WS (WS_SEARCH_*, WS_FIND_*, etc.)",
                            "Start/End filtre WS, durées",
                            "Codes filtre / règles Java",
                            "UUID et transactionId",
                            "Alertes (aucune règle, warnings)"
                    ),
                    List.of(
                            "Regrouper par Session ou UUID selon la densité des uuid [...] dans les messages.",
                            "Graphe : process WS → tâche → filtre → objet."
                    )
            );
            case PERF -> new TypeProfile(
                    "Logs performance (perfs-*)",
                    "Mesures mémoire et durées d'exécution.",
                    "level",
                    List.of(
                            "Consommation mémoire (Mo)",
                            "Durées took [N] ms",
                            "Contexte filtre / className dans le message"
                    ),
                    List.of(
                            "Utile pour analyser lenteurs et pics mémoire, pas le parcours utilisateur complet."
                    )
            );
            case SAVE_LOAD -> new TypeProfile(
                    "Logs batch (saveLoadLogFile)",
                    "Jobs planifiés, triggers, sauvegarde de fichiers logs.",
                    "sourceFileName",
                    List.of(
                            "Triggers planifiés (Trigger… was fired)",
                            "Exécutions batch serveur"
                    ),
                    List.of(
                            "Hors graphe workflow principal — plutôt timeline infra."
                    )
            );
            case MEM_PERF -> new TypeProfile(
                    "Logs mémoire JVM (memPerfs)",
                    "Surveillance mémoire serveur, sans process métier détaillé.",
                    "sourceFileName",
                    List.of(
                            "Mémoire JVM / serveur",
                            "Périodes et niveaux"
                    ),
                    List.of(
                            "Pas d'utilisateur métier pertinent — regrouper par fichier ou level."
                    )
            );
            case SERVER -> new TypeProfile(
                    "Logs serveur (server.log)",
                    "Infrastructure WildFly/Tomcat — format souvent différent.",
                    "level",
                    List.of(
                            "Événements infra serveur",
                            "Niveaux et timestamps"
                    ),
                    List.of(
                            "Couverture d'extraction limitée — vérifier la qualité d'import."
                    )
            );
            case MIXED -> new TypeProfile(
                    "Fichier mixte (structuration + règles / trace)",
                    "Combine structuration Tomcat et traces de règles (traceSaveLogger, etc.).",
                    "sessionId",
                    List.of(
                            "Structuration de champs (works, put key)",
                            "Exécution de règles (filter code, transactionId, uuid)",
                            "Sessions techniques multiples possibles"
                    ),
                    List.of(
                            "Regrouper par Session si plusieurs parcours, sinon par UUID.",
                            "Utilisateur peu discriminant."
                    )
            );
            case UNKNOWN -> new TypeProfile(
                    "Type non reconnu",
                    "Nom de fichier non classé — analyse basée sur le contenu indexé.",
                    "sourceFileName",
                    List.of(
                            "Champs pipe standard (date, session, level, user, process, message)",
                            "Extraction automatique selon familles de messages détectées"
                    ),
                    List.of(
                            "Consultez la fiche qualité d'import pour les familles reconnues."
                    )
            );
        };
    }

    private LogFileTypeKind resolveDominantType(Map<LogFileTypeKind, Integer> typeCounts) {
        LogFileTypeKind best = LogFileTypeKind.UNKNOWN;
        int max = 0;
        for (Map.Entry<LogFileTypeKind, Integer> e : typeCounts.entrySet()) {
            if (e.getKey() == LogFileTypeKind.UNKNOWN) {
                continue;
            }
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }
        if (max == 0 && typeCounts.containsKey(LogFileTypeKind.UNKNOWN)) {
            return LogFileTypeKind.UNKNOWN;
        }
        if (typeCounts.size() > 1 && max < typeCounts.values().stream().mapToInt(Integer::intValue).sum()) {
            long distinct = typeCounts.entrySet().stream()
                    .filter(e -> e.getKey() != LogFileTypeKind.UNKNOWN && e.getValue() > 0)
                    .count();
            if (distinct >= 2) {
                return LogFileTypeKind.MIXED;
            }
        }
        return best;
    }

    private ImportStats aggregateStats(List<ImportLogTypeSummaryDto> imports) {
        int maxRealUsers = 0;
        int maxSessions = 0;
        int maxCorrelations = 0;
        double maxUuidRatio = 0;
        long totalLogs = 0;
        for (ImportLogTypeSummaryDto imp : imports) {
            totalLogs += imp.getTotalLogs();
            ImportStats s = loadStats(imp.getImportId(), imp.getTotalLogs());
            maxRealUsers = Math.max(maxRealUsers, s.realUsers);
            maxSessions = Math.max(maxSessions, s.sessionCount);
            maxCorrelations = Math.max(maxCorrelations, s.correlationCount);
            maxUuidRatio = Math.max(maxUuidRatio, s.uuidRatio);
        }
        return new ImportStats(maxRealUsers, maxRealUsers, maxSessions, maxCorrelations, maxUuidRatio, totalLogs);
    }

    private Map<String, Integer> toTypeCountMap(Map<LogFileTypeKind, Integer> typeCounts) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<LogFileTypeKind, Integer> e : typeCounts.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                out.put(e.getKey().name(), e.getValue());
            }
        }
        return out;
    }

    private List<String> mergeUnique(List<String> base, List<ImportLogTypeSummaryDto> imports) {
        Set<String> set = new LinkedHashSet<>(base);
        for (ImportLogTypeSummaryDto imp : imports) {
            set.addAll(imp.getTips());
        }
        return new ArrayList<>(set);
    }

    private String buildSummary(LogFileTypeKind dominant,
                                TypeProfile profile,
                                int importCount,
                                String suggestedLabel,
                                Map<LogFileTypeKind, Integer> typeCounts) {
        String types = typeCounts.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> profileFor(e.getKey()).label() + " ×" + e.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse(profile.label());

        if (dominant == LogFileTypeKind.MIXED) {
            return importCount + " import(s) — types mixtes (" + types + "). "
                    + "Regroupement suggéré : " + suggestedLabel + ".";
        }
        return importCount + " import(s) — type dominant : " + profile.label()
                + ". Regroupement suggéré : " + suggestedLabel + ".";
    }

    private LogAnalysisGuidanceResponseDto emptyResponse(String message) {
        LogAnalysisGuidanceResponseDto dto = new LogAnalysisGuidanceResponseDto();
        dto.setSummary(message);
        dto.setSuggestedGroupBy("sessionId");
        dto.setSuggestedGroupByLabel(labelForGroupBy("sessionId"));
        dto.setDominantTypeId(LogFileTypeKind.UNKNOWN.name());
        dto.setDominantTypeLabel("—");
        return dto;
    }

    private String labelForGroupBy(String groupBy) {
        return GROUP_BY_LABELS.getOrDefault(groupBy, groupBy);
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b != null ? b.trim() : "";
    }

    private record TypeProfile(
            String label,
            String description,
            String defaultGroupBy,
            List<String> extractableInfos,
            List<String> tips
    ) {
    }

    private record ImportStats(
            int distinctUsers,
            int realUsers,
            int sessionCount,
            int correlationCount,
            double uuidRatio,
            long totalLogs
    ) {
    }
}
