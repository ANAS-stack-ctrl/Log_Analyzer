package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class ImportComparisonMetricsDto {

    private long distinctUsers;
    private long slowLogCount;
    private long maxDurationMs;
    private List<CountValueDto> topUsers = new ArrayList<>();
    private List<CountValueDto> topUsersByErrors = new ArrayList<>();

    public long getDistinctUsers() {
        return distinctUsers;
    }

    public void setDistinctUsers(long distinctUsers) {
        this.distinctUsers = distinctUsers;
    }

    public long getSlowLogCount() {
        return slowLogCount;
    }

    public void setSlowLogCount(long slowLogCount) {
        this.slowLogCount = slowLogCount;
    }

    public long getMaxDurationMs() {
        return maxDurationMs;
    }

    public void setMaxDurationMs(long maxDurationMs) {
        this.maxDurationMs = maxDurationMs;
    }

    public List<CountValueDto> getTopUsers() {
        return topUsers;
    }

    public void setTopUsers(List<CountValueDto> topUsers) {
        this.topUsers = topUsers;
    }

    public List<CountValueDto> getTopUsersByErrors() {
        return topUsersByErrors;
    }

    public void setTopUsersByErrors(List<CountValueDto> topUsersByErrors) {
        this.topUsersByErrors = topUsersByErrors;
    }
}
