package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.AutoIncidentDetectionResponse;
import com.caciopee.loganalyzer.dto.ErrorClusterResponse;
import com.caciopee.loganalyzer.entity.AutoIncidentRule;
import com.caciopee.loganalyzer.repository.AutoIncidentRuleRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AutoIncidentService {

    private final LogEntryService logEntryService;
    private final IncidentService incidentService;
    private final AutoIncidentRuleRepository autoIncidentRuleRepository;
    private final AuditService auditService;

    public AutoIncidentService(LogEntryService logEntryService,
                               IncidentService incidentService,
                               AutoIncidentRuleRepository autoIncidentRuleRepository,
                               AuditService auditService) {
        this.logEntryService = logEntryService;
        this.incidentService = incidentService;
        this.autoIncidentRuleRepository = autoIncidentRuleRepository;
        this.auditService = auditService;
    }

    public AutoIncidentDetectionResponse detectAndCreate() {
        List<ErrorClusterResponse> clusters = logEntryService.getErrorClusters(true, 100);
        List<AutoIncidentRule> rules = autoIncidentRuleRepository.findByActiveTrueOrderByIdAsc();

        AutoIncidentDetectionResponse response = new AutoIncidentDetectionResponse();
        response.setEvaluatedClusters(clusters.size());

        int created = 0;
        int skipped = 0;
        List<String> details = new ArrayList<>();

        for (ErrorClusterResponse cluster : clusters) {
            AutoIncidentRule matchedRule = findMatchingRule(cluster, rules);

            if (matchedRule == null && cluster.getOpenLogs() < 5) {
                skipped++;
                details.add("Cluster ignoré (volume insuffisant) : " + cluster.getClusterKey());
                continue;
            }

            String severity = matchedRule != null
                    ? matchedRule.getSeverityIfMatched()
                    : defaultSeverityFromCluster(cluster);

            String dedupKey = "AUTO|" + cluster.getClusterKey();

            if (incidentService.existsActiveIncidentForDedupKey(dedupKey)) {
                skipped++;
                details.add("Incident déjà actif pour cluster : " + cluster.getClusterKey());
                continue;
            }

            String title = "Auto Incident - " + safe(cluster.getEventType())
                    + (hasText(cluster.getFieldName()) ? " / " + cluster.getFieldName() : "");

            String description = """
                    Incident détecté automatiquement à partir d’un cluster d’erreurs actif.

                    Cluster Key: %s
                    Event Type: %s
                    Champ: %s
                    Sévérité calculée: %s
                    Logs ouverts: %d
                    Logs totaux: %d
                    Imports touchés: %s
                    Business Keys touchées: %s
                    Message exemple: %s
                    Règle appliquée: %s
                    """.formatted(
                    safe(cluster.getClusterKey()),
                    safe(cluster.getEventType()),
                    safe(cluster.getFieldName()),
                    severity,
                    cluster.getOpenLogs(),
                    cluster.getTotalLogs(),
                    cluster.getImportIds(),
                    cluster.getBusinessKeys(),
                    safe(cluster.getSampleMessage()),
                    matchedRule != null ? matchedRule.getRuleName() : "DEFAULT_CLUSTER_RULE"
            );

            incidentService.createAutoIncident(
                    title,
                    description,
                    severity,
                    dedupKey,
                    List.of()
            );

            created++;
            details.add("Incident créé pour cluster : " + cluster.getClusterKey() + " [" + severity + "]");
        }

        response.setCreatedIncidents(created);
        response.setSkippedClusters(skipped);
        response.setDetails(details);

        auditService.log(
                "AUTO_INCIDENT_DETECTION_RUN",
                "AUTO_DETECTOR",
                null,
                "system-auto-detector",
                "evaluated=" + response.getEvaluatedClusters()
                        + ", created=" + created
                        + ", skipped=" + skipped
        );

        return response;
    }

    private AutoIncidentRule findMatchingRule(ErrorClusterResponse cluster, List<AutoIncidentRule> rules) {
        for (AutoIncidentRule rule : rules) {
            boolean eventMatch = !hasText(rule.getEventTypePattern())
                    || containsIgnoreCase(cluster.getEventType(), rule.getEventTypePattern());

            boolean fieldMatch = !hasText(rule.getFieldNamePattern())
                    || containsIgnoreCase(cluster.getFieldName(), rule.getFieldNamePattern());

            boolean volumeMatch = cluster.getOpenLogs() >= safeMin(rule.getMinOpenLogs());

            if (eventMatch && fieldMatch && volumeMatch) {
                return rule;
            }
        }
        return null;
    }

    private String defaultSeverityFromCluster(ErrorClusterResponse cluster) {
        if (cluster.getOpenLogs() >= 20 || cluster.getTotalLogs() >= 30) {
            return "CRITICAL";
        }
        if (cluster.getOpenLogs() >= 10 || cluster.getTotalLogs() >= 15) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private int safeMin(Integer value) {
        return value == null ? 3 : value;
    }

    private boolean containsIgnoreCase(String source, String token) {
        return source != null && token != null && source.toLowerCase().contains(token.toLowerCase());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}