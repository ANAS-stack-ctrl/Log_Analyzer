package com.caciopee.loganalyzer.analysis.dto;

import java.util.ArrayList;
import java.util.List;

public class UserFriendlyAnalysisDto {

    private Long importId;
    private String overallStatus;
    private String globalExplanation;
    private String keyConclusion;

    private Integer totalSegments;
    private Integer errorSegments;
    private Integer warningSegments;
    private Integer performanceSegments;

    private List<UserFriendlySegmentDto> segments = new ArrayList<>();

    public Long getImportId() {
        return importId;
    }

    public void setImportId(Long importId) {
        this.importId = importId;
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public String getGlobalExplanation() {
        return globalExplanation;
    }

    public void setGlobalExplanation(String globalExplanation) {
        this.globalExplanation = globalExplanation;
    }

    public String getKeyConclusion() {
        return keyConclusion;
    }

    public void setKeyConclusion(String keyConclusion) {
        this.keyConclusion = keyConclusion;
    }

    public Integer getTotalSegments() {
        return totalSegments;
    }

    public void setTotalSegments(Integer totalSegments) {
        this.totalSegments = totalSegments;
    }

    public Integer getErrorSegments() {
        return errorSegments;
    }

    public void setErrorSegments(Integer errorSegments) {
        this.errorSegments = errorSegments;
    }

    public Integer getWarningSegments() {
        return warningSegments;
    }

    public void setWarningSegments(Integer warningSegments) {
        this.warningSegments = warningSegments;
    }

    public Integer getPerformanceSegments() {
        return performanceSegments;
    }

    public void setPerformanceSegments(Integer performanceSegments) {
        this.performanceSegments = performanceSegments;
    }

    public List<UserFriendlySegmentDto> getSegments() {
        return segments;
    }

    public void setSegments(List<UserFriendlySegmentDto> segments) {
        this.segments = segments;
    }

    public static class UserFriendlySegmentDto {
        private String segmentKey;
        private String title;
        private String nature;
        private String status;
        private String simpleExplanation;
        private String probableCause;
        private String businessImpact;
        private String recommendation;
        private List<String> highlights = new ArrayList<>();

        public String getSegmentKey() {
            return segmentKey;
        }

        public void setSegmentKey(String segmentKey) {
            this.segmentKey = segmentKey;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getNature() {
            return nature;
        }

        public void setNature(String nature) {
            this.nature = nature;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getSimpleExplanation() {
            return simpleExplanation;
        }

        public void setSimpleExplanation(String simpleExplanation) {
            this.simpleExplanation = simpleExplanation;
        }

        public String getProbableCause() {
            return probableCause;
        }

        public void setProbableCause(String probableCause) {
            this.probableCause = probableCause;
        }

        public String getBusinessImpact() {
            return businessImpact;
        }

        public void setBusinessImpact(String businessImpact) {
            this.businessImpact = businessImpact;
        }

        public String getRecommendation() {
            return recommendation;
        }

        public void setRecommendation(String recommendation) {
            this.recommendation = recommendation;
        }

        public List<String> getHighlights() {
            return highlights;
        }

        public void setHighlights(List<String> highlights) {
            this.highlights = highlights;
        }
    }
}