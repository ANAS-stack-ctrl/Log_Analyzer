package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.List;

public class AssistantUserTopSessionsRequestDto {

    private List<Long> importIds;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private String userName;
    private Integer maxSessions = 5;

    public List<Long> getImportIds() { return importIds; }
    public void setImportIds(List<Long> importIds) { this.importIds = importIds; }

    public LocalDateTime getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDateTime dateFrom) { this.dateFrom = dateFrom; }

    public LocalDateTime getDateTo() { return dateTo; }
    public void setDateTo(LocalDateTime dateTo) { this.dateTo = dateTo; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public Integer getMaxSessions() { return maxSessions; }
    public void setMaxSessions(Integer maxSessions) { this.maxSessions = maxSessions; }
}

