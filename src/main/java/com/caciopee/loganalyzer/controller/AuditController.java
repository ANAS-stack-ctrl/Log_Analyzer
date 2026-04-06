package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.entity.AuditLog;
import com.caciopee.loganalyzer.repository.AuditLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<AuditLog> getAuditLogs() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc();
    }
}