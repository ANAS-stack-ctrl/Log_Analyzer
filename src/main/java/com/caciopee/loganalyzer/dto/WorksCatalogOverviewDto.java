package com.caciopee.loganalyzer.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class WorksCatalogOverviewDto {

    private String catalogVersion;
    private String description;
    private int familyCount;
    private List<WorksMessageFamilySummaryDto> families = new ArrayList<>();
    private JsonNode lineStructure;

    public String getCatalogVersion() { return catalogVersion; }
    public void setCatalogVersion(String catalogVersion) { this.catalogVersion = catalogVersion; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getFamilyCount() { return familyCount; }
    public void setFamilyCount(int familyCount) { this.familyCount = familyCount; }

    public List<WorksMessageFamilySummaryDto> getFamilies() { return families; }
    public void setFamilies(List<WorksMessageFamilySummaryDto> families) { this.families = families; }

    public JsonNode getLineStructure() { return lineStructure; }
    public void setLineStructure(JsonNode lineStructure) { this.lineStructure = lineStructure; }
}
