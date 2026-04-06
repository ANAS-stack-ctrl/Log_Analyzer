package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.*;
import com.caciopee.loganalyzer.entity.Incident;
import com.caciopee.loganalyzer.entity.IncidentComment;
import com.caciopee.loganalyzer.entity.IncidentLogLink;
import com.caciopee.loganalyzer.repository.IncidentCommentRepository;
import com.caciopee.loganalyzer.repository.IncidentLogLinkRepository;
import com.caciopee.loganalyzer.repository.IncidentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentLogLinkRepository incidentLogLinkRepository;
    private final IncidentCommentRepository incidentCommentRepository;
    private final AuditService auditService;

    public IncidentService(IncidentRepository incidentRepository,
                           IncidentLogLinkRepository incidentLogLinkRepository,
                           IncidentCommentRepository incidentCommentRepository,
                           AuditService auditService) {
        this.incidentRepository = incidentRepository;
        this.incidentLogLinkRepository = incidentLogLinkRepository;
        this.incidentCommentRepository = incidentCommentRepository;
        this.auditService = auditService;
    }

    public IncidentResponse createIncident(IncidentCreateRequest request) {
        if (!hasText(request.getTitle())) {
            throw new IllegalArgumentException("Incident title is required.");
        }

        Incident incident = new Incident();
        incident.setTitle(request.getTitle());
        incident.setDescription(request.getDescription());
        incident.setSeverity(firstNonEmpty(request.getSeverity(), "MEDIUM"));
        incident.setCreatedBy(request.getCreatedBy());
        incident.setAssignedTo(request.getAssignedTo());
        incident.setLastUpdatedBy(request.getCreatedBy());
        incident.setSourceType("MANUAL");

        Incident saved = incidentRepository.save(incident);

        linkLogsIfAny(saved.getId(), request.getLogIds());

        auditService.log(
                "CREATE_INCIDENT",
                "INCIDENT",
                saved.getId(),
                request.getCreatedBy(),
                "Title=" + saved.getTitle() + ", severity=" + saved.getSeverity()
        );

        return toResponse(saved);
    }

    public IncidentResponse createAutoIncident(String title,
                                               String description,
                                               String severity,
                                               String dedupKey,
                                               List<Long> logIds) {
        Incident incident = new Incident();
        incident.setTitle(title);
        incident.setDescription(description);
        incident.setSeverity(firstNonEmpty(severity, "HIGH"));
        incident.setCreatedBy("system-auto-detector");
        incident.setLastUpdatedBy("system-auto-detector");
        incident.setSourceType("AUTO");
        incident.setDedupKey(dedupKey);

        Incident saved = incidentRepository.save(incident);
        linkLogsIfAny(saved.getId(), logIds);

        auditService.log(
                "AUTO_CREATE_INCIDENT",
                "INCIDENT",
                saved.getId(),
                "system-auto-detector",
                "Auto incident created with dedupKey=" + dedupKey
        );

        return toResponse(saved);
    }

    public boolean existsActiveIncidentForDedupKey(String dedupKey) {
        return incidentRepository.findFirstByDedupKeyAndStatusIn(
                dedupKey,
                List.of("NEW", "TRIAGED", "INVESTIGATING")
        ).isPresent();
    }

    public List<IncidentResponse> getAllIncidents() {
        return incidentRepository.findAll().stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .map(this::toResponse)
                .toList();
    }

    public IncidentResponse getIncident(Long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + id));
        return toResponse(incident);
    }

    public IncidentResponse updateIncident(Long id, IncidentUpdateRequest request) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + id));

        String oldStatus = incident.getStatus();
        String oldSeverity = incident.getSeverity();
        String oldAssignedTo = incident.getAssignedTo();

        if (hasText(request.getStatus())) {
            incident.setStatus(request.getStatus());

            if ("TRIAGED".equalsIgnoreCase(request.getStatus()) && incident.getTriagedAt() == null) {
                incident.setTriagedAt(LocalDateTime.now());
            }
            if ("INVESTIGATING".equalsIgnoreCase(request.getStatus()) && incident.getInvestigatingAt() == null) {
                incident.setInvestigatingAt(LocalDateTime.now());
            }
            if ("RESOLVED".equalsIgnoreCase(request.getStatus()) && incident.getResolvedAt() == null) {
                incident.setResolvedAt(LocalDateTime.now());
            }
            if ("CLOSED".equalsIgnoreCase(request.getStatus()) && incident.getClosedAt() == null) {
                incident.setClosedAt(LocalDateTime.now());
            }
        }

        if (hasText(request.getSeverity())) {
            incident.setSeverity(request.getSeverity());
        }
        if (request.getAssignedTo() != null) {
            incident.setAssignedTo(request.getAssignedTo());
        }

        incident.setLastUpdatedBy(request.getUpdatedBy());

        Incident saved = incidentRepository.save(incident);

        auditService.log(
                "UPDATE_INCIDENT",
                "INCIDENT",
                saved.getId(),
                request.getUpdatedBy(),
                "status: " + oldStatus + " -> " + saved.getStatus()
                        + ", severity: " + oldSeverity + " -> " + saved.getSeverity()
                        + ", assignedTo: " + safe(oldAssignedTo) + " -> " + safe(saved.getAssignedTo())
        );

        return toResponse(saved);
    }

    public IncidentResponse linkLog(Long incidentId, Long logId, String updatedBy) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        if (!incidentLogLinkRepository.existsByIncidentIdAndLogId(incidentId, logId)) {
            IncidentLogLink link = new IncidentLogLink();
            link.setIncidentId(incidentId);
            link.setLogId(logId);
            incidentLogLinkRepository.save(link);
        }

        incident.setLastUpdatedBy(updatedBy);
        incidentRepository.save(incident);

        auditService.log(
                "LINK_LOG_TO_INCIDENT",
                "INCIDENT",
                incidentId,
                updatedBy,
                "Linked logId=" + logId
        );

        return toResponse(incident);
    }

    public IncidentCommentResponse addComment(Long incidentId, IncidentCommentRequest request) {
        incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

        if (!hasText(request.getComment())) {
            throw new IllegalArgumentException("Comment is required.");
        }

        IncidentComment comment = new IncidentComment();
        comment.setIncidentId(incidentId);
        comment.setComment(request.getComment());
        comment.setCommentBy(request.getCommentBy());

        IncidentComment saved = incidentCommentRepository.save(comment);

        Incident incident = incidentRepository.findById(incidentId).orElseThrow();
        incident.setLastUpdatedBy(request.getCommentBy());
        incidentRepository.save(incident);

        auditService.log(
                "COMMENT_INCIDENT",
                "INCIDENT",
                incidentId,
                request.getCommentBy(),
                request.getComment()
        );

        IncidentCommentResponse response = new IncidentCommentResponse();
        response.setComment(saved.getComment());
        response.setCommentBy(saved.getCommentBy());
        response.setCommentAt(saved.getCommentAt());
        return response;
    }

    public List<IncidentCommentResponse> getComments(Long incidentId) {
        return incidentCommentRepository.findByIncidentIdOrderByCommentAtDesc(incidentId).stream()
                .map(comment -> {
                    IncidentCommentResponse response = new IncidentCommentResponse();
                    response.setComment(comment.getComment());
                    response.setCommentBy(comment.getCommentBy());
                    response.setCommentAt(comment.getCommentAt());
                    return response;
                })
                .toList();
    }

    private void linkLogsIfAny(Long incidentId, List<Long> logIds) {
        if (logIds == null) {
            return;
        }

        for (Long logId : logIds) {
            if (logId != null && !incidentLogLinkRepository.existsByIncidentIdAndLogId(incidentId, logId)) {
                IncidentLogLink link = new IncidentLogLink();
                link.setIncidentId(incidentId);
                link.setLogId(logId);
                incidentLogLinkRepository.save(link);
            }
        }
    }

    private IncidentResponse toResponse(Incident incident) {
        List<Long> linkedLogIds = incidentLogLinkRepository.findByIncidentId(incident.getId()).stream()
                .map(IncidentLogLink::getLogId)
                .toList();

        IncidentResponse response = new IncidentResponse();
        response.setId(incident.getId());
        response.setTitle(incident.getTitle());
        response.setDescription(incident.getDescription());
        response.setStatus(incident.getStatus());
        response.setSeverity(incident.getSeverity());
        response.setAssignedTo(incident.getAssignedTo());
        response.setCreatedBy(incident.getCreatedBy());
        response.setLastUpdatedBy(incident.getLastUpdatedBy());
        response.setCreatedAt(incident.getCreatedAt());
        response.setUpdatedAt(incident.getUpdatedAt());
        response.setLinkedLogsCount(linkedLogIds.size());
        response.setLinkedLogIds(linkedLogIds);

        return response;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstNonEmpty(String a, String b) {
        if (hasText(a)) return a;
        return b;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}