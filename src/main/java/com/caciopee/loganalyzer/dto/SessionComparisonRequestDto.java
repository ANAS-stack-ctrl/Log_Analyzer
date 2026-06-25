package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SessionComparisonRequestDto {

    private List<Long> importIds;
    private String sessionIdA;
    private String sessionIdB;
    /** Filtre optionnel : ne comparer que les logs dont le process extrait/colonne contient cette valeur */
    private String processName;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;

    public List<Long> getImportIds() { return importIds; }
    public void setImportIds(List<Long> importIds) { this.importIds = importIds; }

    public String getSessionIdA() { return sessionIdA; }
    public void setSessionIdA(String sessionIdA) { this.sessionIdA = sessionIdA; }

    public String getSessionIdB() { return sessionIdB; }
    public void setSessionIdB(String sessionIdB) { this.sessionIdB = sessionIdB; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public LocalDateTime getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDateTime dateFrom) { this.dateFrom = dateFrom; }

    public LocalDateTime getDateTo() { return dateTo; }
    public void setDateTo(LocalDateTime dateTo) { this.dateTo = dateTo; }
}
