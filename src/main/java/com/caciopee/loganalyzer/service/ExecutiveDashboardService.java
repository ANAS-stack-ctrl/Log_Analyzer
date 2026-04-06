package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.ExecutiveDashboardResponse;
import com.caciopee.loganalyzer.entity.Incident;
import com.caciopee.loganalyzer.entity.LogTriageState;
import com.caciopee.loganalyzer.repository.IncidentRepository;
import com.caciopee.loganalyzer.repository.LogTriageStateRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExecutiveDashboardService {

    private final IncidentRepository incidentRepository;
    private final LogTriageStateRepository logTriageStateRepository;

    public ExecutiveDashboardService(IncidentRepository incidentRepository,
                                     LogTriageStateRepository logTriageStateRepository) {
        this.incidentRepository = incidentRepository;
        this.logTriageStateRepository = logTriageStateRepository;
    }

    public ExecutiveDashboardResponse buildExecutiveDashboard() {
        List<Incident> incidents = incidentRepository.findAll();
        List<LogTriageState> triageStates = logTriageStateRepository.findAll();

        ExecutiveDashboardResponse response = new ExecutiveDashboardResponse();

        response.setTotalIncidents(incidents.size());
        response.setOpenIncidents(countIncidentStatuses(incidents, Set.of("NEW")));
        response.setInProgressIncidents(countIncidentStatuses(incidents, Set.of("TRIAGED", "INVESTIGATING")));
        response.setResolvedIncidents(countIncidentStatuses(incidents, Set.of("RESOLVED")));
        response.setClosedIncidents(countIncidentStatuses(incidents, Set.of("CLOSED")));

        response.setActiveLogs(countLogStatuses(triageStates, Set.of("OPEN", "IN_PROGRESS")));
        response.setResolvedLogs(countLogStatuses(triageStates, Set.of("RESOLVED")));
        response.setIgnoredLogs(countLogStatuses(triageStates, Set.of("IGNORED")));
        response.setImportantActiveLogs(
                triageStates.stream()
                        .filter(state -> Boolean.TRUE.equals(state.getImportant()))
                        .filter(state -> isIn(state.getStatus(), Set.of("OPEN", "IN_PROGRESS")))
                        .count()
        );

        response.setAvgTriageMinutes(avgMinutes(incidents, incident ->
                incident.getTriagedAt() != null ? Duration.between(incident.getCreatedAt(), incident.getTriagedAt()) : null
        ));

        response.setAvgInvestigationMinutes(avgMinutes(incidents, incident ->
                incident.getInvestigatingAt() != null && incident.getTriagedAt() != null
                        ? Duration.between(incident.getTriagedAt(), incident.getInvestigatingAt())
                        : null
        ));

        response.setAvgResolutionMinutes(avgMinutes(incidents, incident ->
                incident.getResolvedAt() != null
                        ? Duration.between(incident.getCreatedAt(), incident.getResolvedAt())
                        : null
        ));

        response.setAvgClosureMinutes(avgMinutes(incidents, incident ->
                incident.getClosedAt() != null
                        ? Duration.between(incident.getCreatedAt(), incident.getClosedAt())
                        : null
        ));

        response.setIncidentsBySeverity(groupCount(
                incidents.stream()
                        .map(Incident::getSeverity)
                        .filter(this::hasText)
                        .toList()
        ));

        response.setIncidentsByStatus(groupCount(
                incidents.stream()
                        .map(Incident::getStatus)
                        .filter(this::hasText)
                        .toList()
        ));

        response.setActiveLogsByAssignee(groupCount(
                triageStates.stream()
                        .filter(state -> isIn(state.getStatus(), Set.of("OPEN", "IN_PROGRESS")))
                        .map(state -> hasText(state.getAssignedTo()) ? state.getAssignedTo() : "UNASSIGNED")
                        .toList()
        ));

        response.setExecutiveHighlights(buildExecutiveHighlights(response, incidents, triageStates));
        return response;
    }

    private List<String> buildExecutiveHighlights(ExecutiveDashboardResponse response,
                                                  List<Incident> incidents,
                                                  List<LogTriageState> triageStates) {
        List<String> highlights = new ArrayList<>();

        Optional<Map.Entry<String, Long>> topSeverity = response.getIncidentsBySeverity() == null
                ? Optional.empty()
                : response.getIncidentsBySeverity().entrySet().stream()
                  .max(Map.Entry.comparingByValue());

        topSeverity.ifPresent(entry ->
                highlights.add("Sévérité dominante des incidents : " + entry.getKey() + " (" + entry.getValue() + ").")
        );

        if (response.getOpenIncidents() > 0) {
            highlights.add("Incidents encore nouveaux/non triés : " + response.getOpenIncidents() + ".");
        }

        if (response.getInProgressIncidents() > 0) {
            highlights.add("Incidents en traitement (triage ou investigation) : " + response.getInProgressIncidents() + ".");
        }

        if (response.getImportantActiveLogs() > 0) {
            highlights.add("Logs actifs marqués importants : " + response.getImportantActiveLogs() + ".");
        }

        long unassignedActiveLogs = triageStates.stream()
                .filter(state -> isIn(state.getStatus(), Set.of("OPEN", "IN_PROGRESS")))
                .filter(state -> !hasText(state.getAssignedTo()))
                .count();

        if (unassignedActiveLogs > 0) {
            highlights.add("Logs actifs non assignés : " + unassignedActiveLogs + ".");
        }

        Optional<Incident> oldestOpenIncident = incidents.stream()
                .filter(incident -> isIn(incident.getStatus(), Set.of("NEW", "TRIAGED", "INVESTIGATING")))
                .min(Comparator.comparing(Incident::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        oldestOpenIncident.ifPresent(incident -> {
            long ageHours = incident.getCreatedAt() != null
                    ? Duration.between(incident.getCreatedAt(), LocalDateTime.now()).toHours()
                    : 0;
            highlights.add("Plus ancien incident encore actif : #" + incident.getId() + " (" + ageHours + "h).");
        });

        if (highlights.isEmpty()) {
            highlights.add("Aucun signal critique à remonter pour le moment.");
        }

        return highlights;
    }

    private long countIncidentStatuses(List<Incident> incidents, Set<String> statuses) {
        return incidents.stream()
                .filter(incident -> isIn(incident.getStatus(), statuses))
                .count();
    }

    private long countLogStatuses(List<LogTriageState> states, Set<String> statuses) {
        return states.stream()
                .filter(state -> isIn(state.getStatus(), statuses))
                .count();
    }

    private Map<String, Long> groupCount(List<String> values) {
        return values.stream()
                .collect(Collectors.groupingBy(
                        Function.identity(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    private double avgMinutes(List<Incident> incidents,
                              Function<Incident, Duration> extractor) {
        List<Long> minutes = incidents.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .map(Duration::toMinutes)
                .toList();

        if (minutes.isEmpty()) {
            return 0.0;
        }

        double avg = minutes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        return Math.round(avg * 100.0) / 100.0;
    }

    private boolean isIn(String value, Set<String> statuses) {
        return value != null && statuses.stream().anyMatch(status -> status.equalsIgnoreCase(value));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}