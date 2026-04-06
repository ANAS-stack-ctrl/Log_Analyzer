package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;

public class IncidentCommentResponse {

    private String comment;
    private String commentBy;
    private LocalDateTime commentAt;

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getCommentBy() {
        return commentBy;
    }

    public void setCommentBy(String commentBy) {
        this.commentBy = commentBy;
    }

    public LocalDateTime getCommentAt() {
        return commentAt;
    }

    public void setCommentAt(LocalDateTime commentAt) {
        this.commentAt = commentAt;
    }
}