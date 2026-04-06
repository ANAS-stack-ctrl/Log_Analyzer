package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.entity.AlertNotification;
import com.caciopee.loganalyzer.repository.AlertNotificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertNotificationService {

    private final AlertNotificationRepository alertNotificationRepository;
    private final AuditService auditService;

    public AlertNotificationService(AlertNotificationRepository alertNotificationRepository,
                                    AuditService auditService) {
        this.alertNotificationRepository = alertNotificationRepository;
        this.auditService = auditService;
    }

    public AlertNotification createIfNotExists(String alertType,
                                               String severity,
                                               String title,
                                               String message,
                                               String sourceType,
                                               Long sourceId,
                                               String dedupKey) {
        if (dedupKey != null && !dedupKey.isBlank()) {
            boolean exists = alertNotificationRepository.findFirstByDedupKeyAndStatusIn(
                    dedupKey,
                    List.of("OPEN", "ACKNOWLEDGED")
            ).isPresent();
            if (exists) {
                return null;
            }
        }

        AlertNotification alert = new AlertNotification();
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setMessage(message);
        alert.setSourceType(sourceType);
        alert.setSourceId(sourceId);
        alert.setDedupKey(dedupKey);

        AlertNotification saved = alertNotificationRepository.save(alert);

        auditService.log(
                "CREATE_ALERT",
                "ALERT",
                saved.getId(),
                "system-alert-engine",
                "type=" + alertType + ", severity=" + severity + ", title=" + title
        );

        return saved;
    }

    public List<AlertNotification> getAll() {
        return alertNotificationRepository.findTop100ByOrderByCreatedAtDesc();
    }

    public List<AlertNotification> getByStatus(String status) {
        if (status == null || status.isBlank()) {
            return getAll();
        }
        return alertNotificationRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public AlertNotification acknowledge(Long id, String username) {
        AlertNotification alert = alertNotificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));

        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedBy(username);
        alert.setAcknowledgedAt(LocalDateTime.now());

        AlertNotification saved = alertNotificationRepository.save(alert);

        auditService.log(
                "ACK_ALERT",
                "ALERT",
                saved.getId(),
                username,
                "Alert acknowledged"
        );

        return saved;
    }

    public AlertNotification resolve(Long id, String username) {
        AlertNotification alert = alertNotificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));

        alert.setStatus("RESOLVED");
        alert.setResolvedBy(username);
        alert.setResolvedAt(LocalDateTime.now());

        AlertNotification saved = alertNotificationRepository.save(alert);

        auditService.log(
                "RESOLVE_ALERT",
                "ALERT",
                saved.getId(),
                username,
                "Alert resolved"
        );

        return saved;
    }
}