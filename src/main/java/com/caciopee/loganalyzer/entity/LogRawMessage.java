package com.caciopee.loganalyzer.entity;
import jakarta.persistence.*;
@Entity
@Table(name = "log_raw_messages",
        indexes = {
        @Index(name = "idx_log_raw_messages_log_entry_id",
                columnList = "log_entry_id") })
public class LogRawMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "log_entry_id", nullable = false, unique = true)
    private LogEntry logEntry;
    @Column(name = "raw_message", columnDefinition = "TEXT")
    private String rawMessage;
    @Column(name = "message_tail", columnDefinition = "TEXT")
    private String messageTail;
    @Column(name = "technical_id", length = 100)
    private String technicalId;
    public LogRawMessage() { } public Long getId() { return id; } public LogEntry getLogEntry() { return logEntry; } public void setLogEntry(LogEntry logEntry) { this.logEntry = logEntry; } public String getRawMessage() { return rawMessage; } public void setRawMessage(String rawMessage) { this.rawMessage = rawMessage; } public String getMessageTail() { return messageTail; } public void setMessageTail(String messageTail) { this.messageTail = messageTail; } public String getTechnicalId() { return technicalId; } public void setTechnicalId(String technicalId) { this.technicalId = technicalId; } }