package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class LogAnalysisGuidanceRequestDto {

    private List<Long> importIds = new ArrayList<>();

    public List<Long> getImportIds() {
        return importIds;
    }

    public void setImportIds(List<Long> importIds) {
        this.importIds = importIds != null ? importIds : new ArrayList<>();
    }
}
