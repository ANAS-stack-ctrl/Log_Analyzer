package com.caciopee.loganalyzer.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "log_triage_history")
public class LogTriageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_id", nullable = false)
    private Long logId;

    @Column(name = "action_type", nullable = false, length = 40)
    private String actionType;

    @Column(name = "old_status", length = 30)
    private String oldStatus;

    @Column(name = "new_status", length = 30)
    private String newStatus;

    @Column(name = "old_important")
    private Boolean oldImportant;

    @Column(name = "new_important")
    private Boolean newImportant;

    @Column(name = "old_assigned_to")
    private String oldAssignedTo;

    @Column(name = "new_assigned_to")
    private String newAssignedTo;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "action_by")
    private String actionBy;

    @Column(name = "action_at", nullable = false)
    private LocalDateTime actionAt;

    public LogTriageHistory() {
    }

    @PrePersist
    public void prePersist() {
        if (this.actionAt == null) {
            this.actionAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(String oldStatus) {
        this.oldStatus = oldStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public Boolean getOldImportant() {
        return oldImportant;
    }

    public void setOldImportant(Boolean oldImportant) {
        this.oldImportant = oldImportant;
    }

    public Boolean getNewImportant() {
        return newImportant;
    }

    public void setNewImportant(Boolean newImportant) {
        this.newImportant = newImportant;
    }

    public String getOldAssignedTo() {
        return oldAssignedTo;
    }

    public void setOldAssignedTo(String oldAssignedTo) {
        this.oldAssignedTo = oldAssignedTo;
    }

    public String getNewAssignedTo() {
        return newAssignedTo;
    }

    public void setNewAssignedTo(String newAssignedTo) {
        this.newAssignedTo = newAssignedTo;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getActionBy() {
        return actionBy;
    }

    public void setActionBy(String actionBy) {
        this.actionBy = actionBy;
    }

    public LocalDateTime getActionAt() {
        return actionAt;
    }

    public void setActionAt(LocalDateTime actionAt) {
        this.actionAt = actionAt;
    }
}