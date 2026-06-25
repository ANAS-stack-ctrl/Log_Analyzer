package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.List;

public class GraphLogSearchRequestDto {

    private List<Long> importIds;
    private String groupBy;
    private String groupKey;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private String query;
    /** 0 ou absent = tous les résultats (pas de plafond côté recherche graphe). */
    private Integer limit = 0;

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

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
