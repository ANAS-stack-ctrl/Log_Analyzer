package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class DashboardSummaryDto {

    private long totalImports;
    private long totalLogs;
    private long totalErrors;
    private long totalWarnings;
    private long totalInfos;

    private long totalQueryExecutionFailures;
    private long totalRuleNotFoundSignals;

    private List<CountValueDto> recentImportStatuses = new ArrayList<>();

    public long getTotalImports() {
        return totalImports;
    }

    public void setTotalImports(long totalImports) {
        this.totalImports = totalImports;
    }

    public long getTotalLogs() {
        return totalLogs;
    }

    public void setTotalLogs(long totalLogs) {
        this.totalLogs = totalLogs;
    }

    public long getTotalErrors() {
        return totalErrors;
    }

    public void setTotalErrors(long totalErrors) {
        this.totalErrors = totalErrors;
    }

    public long getTotalWarnings() {
        return totalWarnings;
    }

    public void setTotalWarnings(long totalWarnings) {
        this.totalWarnings = totalWarnings;
    }

    public long getTotalInfos() {
        return totalInfos;
    }

    public void setTotalInfos(long totalInfos) {
        this.totalInfos = totalInfos;
    }

    public long getTotalQueryExecutionFailures() {
        return totalQueryExecutionFailures;
    }

    public void setTotalQueryExecutionFailures(long totalQueryExecutionFailures) {
        this.totalQueryExecutionFailures = totalQueryExecutionFailures;
    }

    public long getTotalRuleNotFoundSignals() {
        return totalRuleNotFoundSignals;
    }

    public void setTotalRuleNotFoundSignals(long totalRuleNotFoundSignals) {
        this.totalRuleNotFoundSignals = totalRuleNotFoundSignals;
    }

    public List<CountValueDto> getRecentImportStatuses() {
        return recentImportStatuses;
    }

    public void setRecentImportStatuses(List<CountValueDto> recentImportStatuses) {
        this.recentImportStatuses = recentImportStatuses;
    }
}