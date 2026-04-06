package com.caciopee.loganalyzer.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "auto_incident_rules")
public class AutoIncidentRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, unique = true)
    private String ruleName;

    @Column(name = "event_type_pattern")
    private String eventTypePattern;

    @Column(name = "field_name_pattern")
    private String fieldNamePattern;

    @Column(name = "min_open_logs", nullable = false)
    private Integer minOpenLogs = 3;

    @Column(name = "severity_if_matched", nullable = false)
    private String severityIfMatched = "HIGH";

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
        if (minOpenLogs == null) {
            minOpenLogs = 3;
        }
        if (severityIfMatched == null || severityIfMatched.isBlank()) {
            severityIfMatched = "HIGH";
        }
    }

    public Long getId() {
        return id;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getEventTypePattern() {
        return eventTypePattern;
    }

    public void setEventTypePattern(String eventTypePattern) {
        this.eventTypePattern = eventTypePattern;
    }

    public String getFieldNamePattern() {
        return fieldNamePattern;
    }

    public void setFieldNamePattern(String fieldNamePattern) {
        this.fieldNamePattern = fieldNamePattern;
    }

    public Integer getMinOpenLogs() {
        return minOpenLogs;
    }

    public void setMinOpenLogs(Integer minOpenLogs) {
        this.minOpenLogs = minOpenLogs;
    }

    public String getSeverityIfMatched() {
        return severityIfMatched;
    }

    public void setSeverityIfMatched(String severityIfMatched) {
        this.severityIfMatched = severityIfMatched;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}