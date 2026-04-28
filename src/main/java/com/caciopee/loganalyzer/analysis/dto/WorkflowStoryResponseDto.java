package com.caciopee.loganalyzer.analysis.dto;

import java.util.ArrayList;
import java.util.List;

public class WorkflowStoryResponseDto {

    private Long importId;
    private String globalStory;
    private List<WorkflowStoryItemDto> items = new ArrayList<>();

    public Long getImportId() {
        return importId;
    }

    public void setImportId(Long importId) {
        this.importId = importId;
    }

    public String getGlobalStory() {
        return globalStory;
    }

    public void setGlobalStory(String globalStory) {
        this.globalStory = globalStory;
    }

    public List<WorkflowStoryItemDto> getItems() {
        return items;
    }

    public void setItems(List<WorkflowStoryItemDto> items) {
        this.items = items;
    }
}