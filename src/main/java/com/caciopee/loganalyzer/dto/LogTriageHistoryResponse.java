package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;

public class LogTriageHistoryResponse {

    private String actionType;
    private String oldStatus;
    private String newStatus;
    private Boolean oldImportant;
    private Boolean newImportant;
    private String oldAssignedTo;
    private String newAssignedTo;
    private String comment;
    private String actionBy;
    private LocalDateTime actionAt;

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