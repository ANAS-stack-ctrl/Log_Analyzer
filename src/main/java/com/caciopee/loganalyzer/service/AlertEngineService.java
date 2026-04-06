package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.AutoIncidentDetectionResponse;
import com.caciopee.loganalyzer.entity.AlertNotification;
import com.caciopee.loganalyzer.entity.Incident;
import com.caciopee.loganalyzer.entity.LogTriageState;
import com.caciopee.loganalyzer.repository.IncidentRepository;
import com.caciopee.loganalyzer.repository.LogTriageStateRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertEngineService {

    private final IncidentRepository incidentRepository;
    private final LogTriageStateRepository logTriageStateRepository;
    private final AlertNotificationService alertNotificationService;
    private final AuditService auditService;

    public AlertEngineService(IncidentRepository incidentRepository,
                              LogTriageStateRepository logTriageStateRepository,
                              AlertNotificationService alertNotificationService,
                              AuditService auditService) {
        this.incidentRepository = incidentRepository;
        this.logTriageStateRepository = logTriageStateRepository;
        this.alertNotificationService = alertNotificationService;
        this.auditService = auditService;
    }

    public AutoIncidentDetectionResponse runAlertChecks() {
        AutoIncidentDetectionResponse response = new AutoIncidentDetectionResponse();
        int created = 0;
        int skipped = 0;

        List<Incident> incidents = incidentRepository.findAll();
        List<LogTriageState> states = logTriageStateRepository.findAll();

        // 1. Critical incidents still active
        for (Incident incident : incidents) {
            if (isActiveIncident(incident) && "CRITICAL".equalsIgnoreCase(safe(incident.getSeverity()))) {
                AlertNotification alert = alertNotificationService.createIfNotExists(
                        "CRITICAL_INCIDENT_ACTIVE",
                        "CRITICAL",
                        "Incident critique actif",
                        "Incident #" + incident.getId() + " [" + safe(incident.getTitle()) + "] est toujours actif.",
                        "INCIDENT",
                        incident.getId(),
                        "ALERT|CRIT_INCIDENT|" + incident.getId()
                );
                if (alert != null) created++; else skipped++;
            }
        }

        // 2. SLA breach on old active incidents (>24h)
        for (Incident incident : incidents) {
            if (isActiveIncident(incident) && incident.getCreatedAt() != null) {
                long hours = Duration.between(incident.getCreatedAt(), LocalDateTime.now()).toHours();
                if (hours >= 24) {
                    AlertNotification alert = alertNotificationService.createIfNotExists(
                            "INCIDENT_SLA_BREACH",
                            "HIGH",
                            "SLA incident dépassé",
                            "Incident #" + incident.getId() + " actif depuis " + hours + "h.",
                            "INCIDENT",
                            incident.getId(),
                            "ALERT|SLA_INCIDENT|" + incident.getId()
                    );
                    if (alert != null) created++; else skipped++;
                }
            }
        }

        // 3. Too many important active logs
        long importantActiveLogs = states.stream()
                .filter(state -> Boolean.TRUE.equals(state.getImportant()))
                .filter(state -> isActiveLog(state))
                .count();

        if (importantActiveLogs >= 10) {
            AlertNotification alert = alertNotificationService.createIfNotExists(
                    "IMPORTANT_ACTIVE_LOGS_SPIKE",
                    "HIGH",
                    "Trop de logs importants actifs",
                    "Le système détecte " + importantActiveLogs + " logs importants encore actifs.",
                    "TRIAGE",
                    null,
                    "ALERT|IMPORTANT_ACTIVE_LOGS_SPIKE"
            );
            if (alert != null) created++; else skipped++;
        }

        // 4. Too many unassigned active logs
        long unassignedActiveLogs = states.stream()
                .filter(this::isActiveLog)
                .filter(state -> !hasText(state.getAssignedTo()))
                .count();

        if (unassignedActiveLogs >= 15) {
            AlertNotification alert = alertNotificationService.createIfNotExists(
                    "UNASSIGNED_ACTIVE_LOGS",
                    "MEDIUM",
                    "Trop de logs actifs non assignés",
                    "Le système détecte " + unassignedActiveLogs + " logs actifs sans affectation.",
                    "TRIAGE",
                    null,
                    "ALERT|UNASSIGNED_ACTIVE_LOGS"
            );
            if (alert != null) created++; else skipped++;
        }

        response.setEvaluatedClusters(4);
        response.setCreatedIncidents(created);
        response.setSkippedClusters(skipped);
        response.getDetails().add("Vérification alerting terminée. Alerts créées=" + created + ", ignorées=" + skipped);

        auditService.log(
                "RUN_ALERT_ENGINE",
                "ALERT_ENGINE",
                null,
                "system-alert-engine",
                "created=" + created + ", skipped=" + skipped
        );

        return response;
    }

    private boolean isActiveIncident(Incident incident) {
        return incident != null && (
                "NEW".equalsIgnoreCase(safe(incident.getStatus())) ||
                        "TRIAGED".equalsIgnoreCase(safe(incident.getStatus())) ||
                        "INVESTIGATING".equalsIgnoreCase(safe(incident.getStatus()))
        );
    }

    private boolean isActiveLog(LogTriageState state) {
        return state != null && (
                "OPEN".equalsIgnoreCase(safe(state.getStatus())) ||
                        "IN_PROGRESS".equalsIgnoreCase(safe(state.getStatus()))
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}