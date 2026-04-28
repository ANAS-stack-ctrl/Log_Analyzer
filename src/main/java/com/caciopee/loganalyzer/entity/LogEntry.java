package com.caciopee.loganalyzer.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "log_entries",
        indexes = {
                @Index(name = "idx_log_entries_import_id", columnList = "import_id"),
                @Index(name = "idx_log_entries_log_timestamp", columnList = "log_timestamp"),
                @Index(name = "idx_log_entries_session_id", columnList = "session_id"),
                @Index(name = "idx_log_entries_user_correlation_id", columnList = "user_correlation_id"),
                @Index(name = "idx_log_entries_level", columnList = "level"),
                @Index(name = "idx_log_entries_event_type", columnList = "event_type"),
                @Index(name = "idx_log_entries_business_key", columnList = "business_key"),
                @Index(name = "idx_log_entries_is_error", columnList = "is_error"),
                @Index(name = "idx_log_entries_parse_quality", columnList = "parse_quality")
        })
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_id", nullable = false)
    private LogImport logImport;

    @Column(name = "log_timestamp")
    private LocalDateTime logTimestamp;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "level", length = 20)
    private String level;

    @Column(name = "user_name", length = 255)
    private String userName;

    @Column(name = "source_class", length = 500)
    private String sourceClass;

    @Column(name = "process_name", length = 500)
    private String processName;

    @Column(name = "step_code", length = 100)
    private String stepCode;

    @Column(name = "log_code")
    private Integer logCode;

    @Column(name = "environment", length = 100)
    private String environment;

    @Column(name = "server_name", length = 100)
    private String serverName;

    @Column(name = "app_version", length = 255)
    private String appVersion;

    @Column(name = "user_correlation_id", length = 100)
    private String userCorrelationId;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "raw_log", columnDefinition = "TEXT")
    private String rawLog;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "field_name", length = 255)
    private String fieldName;

    @Column(name = "interface_field", length = 255)
    private String interfaceField;

    @Column(name = "field_class_code", length = 255)
    private String fieldClassCode;

    @Column(name = "parsed_type", length = 100)
    private String parsedType;

    @Column(name = "parsed_value", columnDefinition = "TEXT")
    private String parsedValue;

    @Column(name = "relation_name", length = 255)
    private String relationName;

    @Column(name = "relation_key", length = 255)
    private String relationKey;

    @Column(name = "business_key", length = 255)
    private String businessKey;

    @Column(name = "mandatory_field", length = 255)
    private String mandatoryField;

    @Column(name = "error_column", length = 255)
    private String errorColumn;

    @Column(name = "error_attribute", length = 255)
    private String errorAttribute;

    @Column(name = "error_value", columnDefinition = "TEXT")
    private String errorValue;

    @Column(name = "error_business_key", length = 255)
    private String errorBusinessKey;

    @Column(name = "is_error", nullable = false)
    private Boolean isError = false;

    @Column(name = "business_meaning", columnDefinition = "TEXT")
    private String businessMeaning;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_quality", length = 50)
    private LogParseQuality parseQuality = LogParseQuality.HEALTHY;

    @Column(name = "ambiguous_message", nullable = false)
    private Boolean ambiguousMessage = false;

    @Column(name = "incomplete_line", nullable = false)
    private Boolean incompleteLine = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    @OneToOne(mappedBy = "logEntry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private LogRawMessage rawMessageEntity;

    public LogEntry() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isError == null) {
            isError = false;
        }
        if (ambiguousMessage == null) {
            ambiguousMessage = false;
        }
        if (incompleteLine == null) {
            incompleteLine = false;
        }
        if (parseQuality == null) {
            parseQuality = LogParseQuality.HEALTHY;
        }
    }

    public Long getId() {
        return id;
    }

    public LogImport getLogImport() {
        return logImport;
    }

    public void setLogImport(LogImport logImport) {
        this.logImport = logImport;
    }

    public LocalDateTime getLogTimestamp() {
        return logTimestamp;
    }

    public void setLogTimestamp(LocalDateTime logTimestamp) {
        this.logTimestamp = logTimestamp;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
        this.isError = level != null && "ERROR".equalsIgnoreCase(level.trim());
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getSourceClass() {
        return sourceClass;
    }

    public void setSourceClass(String sourceClass) {
        this.sourceClass = sourceClass;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getStepCode() {
        return stepCode;
    }

    public void setStepCode(String stepCode) {
        this.stepCode = stepCode;
    }

    public Integer getLogCode() {
        return logCode;
    }

    public void setLogCode(Integer logCode) {
        this.logCode = logCode;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getUserCorrelationId() {
        return userCorrelationId;
    }

    public void setUserCorrelationId(String userCorrelationId) {
        this.userCorrelationId = userCorrelationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRawLog() {
        return rawLog;
    }

    public void setRawLog(String rawLog) {
        this.rawLog = rawLog;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getInterfaceField() {
        return interfaceField;
    }

    public void setInterfaceField(String interfaceField) {
        this.interfaceField = interfaceField;
    }

    public String getFieldClassCode() {
        return fieldClassCode;
    }

    public void setFieldClassCode(String fieldClassCode) {
        this.fieldClassCode = fieldClassCode;
    }

    public String getParsedType() {
        return parsedType;
    }

    public void setParsedType(String parsedType) {
        this.parsedType = parsedType;
    }

    public String getParsedValue() {
        return parsedValue;
    }

    public void setParsedValue(String parsedValue) {
        this.parsedValue = parsedValue;
    }

    public String getRelationName() {
        return relationName;
    }

    public void setRelationName(String relationName) {
        this.relationName = relationName;
    }

    public String getRelationKey() {
        return relationKey;
    }

    public void setRelationKey(String relationKey) {
        this.relationKey = relationKey;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getMandatoryField() {
        return mandatoryField;
    }

    public void setMandatoryField(String mandatoryField) {
        this.mandatoryField = mandatoryField;
    }

    public String getErrorColumn() {
        return errorColumn;
    }

    public void setErrorColumn(String errorColumn) {
        this.errorColumn = errorColumn;
    }

    public String getErrorAttribute() {
        return errorAttribute;
    }

    public void setErrorAttribute(String errorAttribute) {
        this.errorAttribute = errorAttribute;
    }

    public String getErrorValue() {
        return errorValue;
    }

    public void setErrorValue(String errorValue) {
        this.errorValue = errorValue;
    }

    public String getErrorBusinessKey() {
        return errorBusinessKey;
    }

    public void setErrorBusinessKey(String errorBusinessKey) {
        this.errorBusinessKey = errorBusinessKey;
    }

    public Boolean getIsError() {
        return isError;
    }

    public Boolean isError() {
        return isError;
    }

    public void setIsError(Boolean isError) {
        this.isError = isError;
    }

    public String getBusinessMeaning() {
        return businessMeaning;
    }

    public void setBusinessMeaning(String businessMeaning) {
        this.businessMeaning = businessMeaning;
    }

    public LogParseQuality getParseQuality() {
        return parseQuality;
    }

    public void setParseQuality(LogParseQuality parseQuality) {
        this.parseQuality = parseQuality;
    }

    public Boolean getAmbiguousMessage() {
        return ambiguousMessage;
    }

    public void setAmbiguousMessage(Boolean ambiguousMessage) {
        this.ambiguousMessage = ambiguousMessage;
    }

    public Boolean getIncompleteLine() {
        return incompleteLine;
    }

    public void setIncompleteLine(Boolean incompleteLine) {
        this.incompleteLine = incompleteLine;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LogRawMessage getRawMessageEntity() {
        return rawMessageEntity;
    }

    public void setRawMessageEntity(LogRawMessage rawMessageEntity) {
        this.rawMessageEntity = rawMessageEntity;
        if (rawMessageEntity != null) {
            rawMessageEntity.setLogEntry(this);
        }
    }
}