package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.List;

public class AiContextRequestDto {
    private List<Long> importIds;
    private String groupBy;
    private String groupKey;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private Integer maxEvidenceLogs = 20;
    /** Question utilisateur (chat) : cible processus/filtres mentionnés dans la question. */
    private String focusQuery;
    /** Périmètre navigation graphe (optionnel). */
    private String scopeProcess;
    private String scopeAction;
    private String scopeFilter;
    private String scopeNodeType;
    private String scopeNodeLabel;

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

    public Integer getMaxEvidenceLogs() { return maxEvidenceLogs; }
    public void setMaxEvidenceLogs(Integer maxEvidenceLogs) { this.maxEvidenceLogs = maxEvidenceLogs; }

    public String getFocusQuery() { return focusQuery; }
    public void setFocusQuery(String focusQuery) { this.focusQuery = focusQuery; }

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