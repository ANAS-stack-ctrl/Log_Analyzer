package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class ImportComparisonResponseDto {

    private ImportAnalysisSummaryDto summaryA;
    private ImportAnalysisSummaryDto summaryB;
    private ImportComparisonMetricsDto metricsA;
    private ImportComparisonMetricsDto metricsB;
    private List<ImportUserDeltaDto> userDeltas = new ArrayList<>();
    private List<String> regressions = new ArrayList<>();
    private List<String> differences = new ArrayList<>();
    private String summary;
    private String recommendation;

    public ImportAnalysisSummaryDto getSummaryA() { return summaryA; }
    public void setSummaryA(ImportAnalysisSummaryDto summaryA) { this.summaryA = summaryA; }

    public ImportAnalysisSummaryDto getSummaryB() { return summaryB; }
    public void setSummaryB(ImportAnalysisSummaryDto summaryB) { this.summaryB = summaryB; }

    public ImportComparisonMetricsDto getMetricsA() { return metricsA; }
    public void setMetricsA(ImportComparisonMetricsDto metricsA) { this.metricsA = metricsA; }

    public ImportComparisonMetricsDto getMetricsB() { return metricsB; }
    public void setMetricsB(ImportComparisonMetricsDto metricsB) { this.metricsB = metricsB; }

    public List<ImportUserDeltaDto> getUserDeltas() { return userDeltas; }
    public void setUserDeltas(List<ImportUserDeltaDto> userDeltas) { this.userDeltas = userDeltas; }

    public List<String> getRegressions() { return regressions; }
    public void setRegressions(List<String> regressions) { this.regressions = regressions; }

    public List<String> getDifferences() { return differences; }
    public void setDifferences(List<String> differences) { this.differences = differences; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
}
