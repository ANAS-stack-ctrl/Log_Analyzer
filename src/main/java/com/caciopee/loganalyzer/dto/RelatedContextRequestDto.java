package com.caciopee.loganalyzer.dto;

import java.util.List;

public class RelatedContextRequestDto {
    private List<Long> importIds;
    private String groupBy;
    private String groupKey;
    private Long centerLogId;
    private Integer before = 20;
    private Integer after = 20;

    public List<Long> getImportIds() { return importIds; }
    public void setImportIds(List<Long> importIds) { this.importIds = importIds; }

    public String getGroupBy() { return groupBy; }
    public void setGroupBy(String groupBy) { this.groupBy = groupBy; }

    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }

    public Long getCenterLogId() { return centerLogId; }
    public void setCenterLogId(Long centerLogId) { this.centerLogId = centerLogId; }

    public Integer getBefore() { return before; }
    public void setBefore(Integer before) { this.before = before; }

    public Integer getAfter() { return after; }
    public void setAfter(Integer after) { this.after = after; }
}