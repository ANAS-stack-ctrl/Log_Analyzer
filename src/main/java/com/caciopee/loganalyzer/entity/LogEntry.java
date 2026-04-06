package com.caciopee.loganalyzer.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "logs")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_timestamp")
    private LocalDateTime logTimestamp;

    private String executionId;

    private String level;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "source_class")
    private String sourceClass;

    @Column(name = "process_name")
    private String processName;

    @Column(name = "step_code")
    private String stepCode;

    @Column(name = "log_code")
    private Integer logCode;

    private String environment;

    @Column(name = "server_name")
    private String serverName;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String rawLog;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "interface_field")
    private String interfaceField;

    @Column(name = "field_class_code")
    private String fieldClassCode;

    @Column(name = "parsed_type")
    private String parsedType;

    @Column(name = "parsed_value", columnDefinition = "TEXT")
    private String parsedValue;

    @Column(name = "relation_name")
    private String relationName;

    @Column(name = "relation_key")
    private String relationKey;

    @Column(name = "business_key")
    private String businessKey;

    @Column(name = "mandatory_field")
    private String mandatoryField;

    @Column(name = "error_column")
    private String errorColumn;

    @Column(name = "error_attribute")
    private String errorAttribute;

    @Column(name = "error_value", columnDefinition = "TEXT")
    private String errorValue;

    @Column(name = "error_business_key")
    private String errorBusinessKey;

    @Column(name = "is_error")
    private Boolean error = false;

    @Column(name = "business_meaning", columnDefinition = "TEXT")
    private String businessMeaning;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private LogImport logImport;

    public LogEntry() {
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getLogTimestamp() {
        return logTimestamp;
    }

    public void setLogTimestamp(LocalDateTime logTimestamp) {
        this.logTimestamp = logTimestamp;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
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

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
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

    public Boolean getError() {
        return error;
    }

    public void setError(Boolean error) {
        this.error = error;
    }

    public String getBusinessMeaning() {
        return businessMeaning;
    }

    public void setBusinessMeaning(String businessMeaning) {
        this.businessMeaning = businessMeaning;
    }

    public LogImport getLogImport() {
        return logImport;
    }

    public void setLogImport(LogImport logImport) {
        this.logImport = logImport;
    }
}