package com.caciopee.loganalyzer.dto;

public class IncidentCommentRequest {

    private String comment;
    private String commentBy;

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
}