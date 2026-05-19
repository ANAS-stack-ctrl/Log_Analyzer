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
}