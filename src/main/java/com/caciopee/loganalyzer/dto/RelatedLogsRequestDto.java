package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.List;

public class RelatedLogsRequestDto {

    private List<Long> importIds;
    private String groupBy;
    private String groupKey;

    private String itemType; // PROCESS, FILTER, ACTION, BUSINESS_OBJECT, WARNING, ZERO_RESULT, PERFORMANCE
    private String itemName;

    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;

    private Integer limit = 300;

    public List<Long> getImportIds() { return importIds; }
    public void setImportIds(List<Long> importIds) { this.importIds = importIds; }

    public String getGroupBy() { return groupBy; }
    public void setGroupBy(String groupBy) { this.groupBy = groupBy; }

    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public LocalDateTime getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDateTime dateFrom) { this.dateFrom = dateFrom; }

    public LocalDateTime getDateTo() { return dateTo; }
    public void setDateTo(LocalDateTime dateTo) { this.dateTo = dateTo; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}