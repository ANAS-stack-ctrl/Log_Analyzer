package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AssistantChatRequestDto {

    private Long importId;
    private List<Long> importIds;
    private String groupBy;
    private String groupKey;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private String userMessage;
    private List<ChatMessageDto> history = new ArrayList<>();
    private String scopeProcess;
    private String scopeAction;
    private String scopeFilter;
    private String scopeNodeType;
    private String scopeNodeLabel;

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }

    public List<Long> getImportIds() { return importIds; }
    public void setImportIds(List<Long> importIds) { this.importIds = importIds; }

    public String getGroupBy() { return groupBy; }
    public void setGroupBy(String groupBy) { this.groupBy = groupBy; }

    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }

    public LocalDateTime getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDateTime dateFrom) { this.dateFrom = dateFrom; }

    public LocalDateTime getDateTo() { return dateTo; }
    public void setDateTo(LocalDateTime dateTo) { this.dateTo = dateTo; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

    public List<ChatMessageDto> getHistory() { return history; }
    public void setHistory(List<ChatMessageDto> history) { this.history = history; }

    public String getScopeProcess() { return scopeProcess; }
    public void setScopeProcess(String scopeProcess) { this.scopeProcess = scopeProcess; }

    public String getScopeAction() { return scopeAction; }
    public void setScopeAction(String scopeAction) { this.scopeAction = scopeAction; }

    public String getScopeFilter() { return scopeFilter; }
    public void setScopeFilter(String scopeFilter) { this.scopeFilter = scopeFilter; }

    public String getScopeNodeType() { return scopeNodeType; }
    public void setScopeNodeType(String scopeNodeType) { this.scopeNodeType = scopeNodeType; }

    public String getScopeNodeLabel() { return scopeNodeLabel; }
    public void setScopeNodeLabel(String scopeNodeLabel) { this.scopeNodeLabel = scopeNodeLabel; }
}
