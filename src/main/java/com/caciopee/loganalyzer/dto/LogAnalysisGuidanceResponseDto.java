package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LogAnalysisGuidanceResponseDto {

    private int importCount;
    private String dominantTypeId;
    private String dominantTypeLabel;
    private String suggestedGroupBy;
    private String suggestedGroupByLabel;
    private String summary;

    private Map<String, Integer> typeCounts = new LinkedHashMap<>();
    private List<String> extractableInfos = new ArrayList<>();
    private List<String> avoidGroupByHints = new ArrayList<>();
    private List<String> tips = new ArrayList<>();
    private List<ImportLogTypeSummaryDto> imports = new ArrayList<>();

    public int getImportCount() { return importCount; }
    public void setImportCount(int importCount) { this.importCount = importCount; }

    public String getDominantTypeId() { return dominantTypeId; }
    public void setDominantTypeId(String dominantTypeId) { this.dominantTypeId = dominantTypeId; }

    public String getDominantTypeLabel() { return dominantTypeLabel; }
    public void setDominantTypeLabel(String dominantTypeLabel) { this.dominantTypeLabel = dominantTypeLabel; }

    public String getSuggestedGroupBy() { return suggestedGroupBy; }
    public void setSuggestedGroupBy(String suggestedGroupBy) { this.suggestedGroupBy = suggestedGroupBy; }

    public String getSuggestedGroupByLabel() { return suggestedGroupByLabel; }
    public void setSuggestedGroupByLabel(String suggestedGroupByLabel) { this.suggestedGroupByLabel = suggestedGroupByLabel; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Map<String, Integer> getTypeCounts() { return typeCounts; }
    public void setTypeCounts(Map<String, Integer> typeCounts) {
        this.typeCounts = typeCounts != null ? typeCounts : new LinkedHashMap<>();
    }

    public List<String> getExtractableInfos() { return extractableInfos; }
    public void setExtractableInfos(List<String> extractableInfos) {
        this.extractableInfos = extractableInfos != null ? extractableInfos : new ArrayList<>();
    }

    public List<String> getAvoidGroupByHints() { return avoidGroupByHints; }
    public void setAvoidGroupByHints(List<String> avoidGroupByHints) {
        this.avoidGroupByHints = avoidGroupByHints != null ? avoidGroupByHints : new ArrayList<>();
    }

    public List<String> getTips() { return tips; }
    public void setTips(List<String> tips) {
        this.tips = tips != null ? tips : new ArrayList<>();
    }

    public List<ImportLogTypeSummaryDto> getImports() { return imports; }
    public void setImports(List<ImportLogTypeSummaryDto> imports) {
        this.imports = imports != null ? imports : new ArrayList<>();
    }
}
