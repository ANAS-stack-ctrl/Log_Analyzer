package com.caciopee.loganalyzer.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "log_resolution")
public class LogResolution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long logId;

    private String status;
    // OPEN, RESOLVED, IGNORED, IN_PROGRESS

    private Boolean important;

    private String comment;

    private String actionBy;

    private LocalDateTime actionAt;

    private String actionType;
    // RESOLVE, IGNORE, MARK_IMPORTANT, UNDO
}