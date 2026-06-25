package com.caciopee.loganalyzer.dto;

public class SessionCompareDiffItemDto {

    private String category;
    private String name;
    private Long countA;
    private Long countB;
    private Long deltaCount;
    private Long maxDurationMsA;
    private Long maxDurationMsB;
    /** ONLY_A, ONLY_B, BOTH, CHANGED */
    private String status;
    private String note;

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getCountA() { return countA; }
    public void setCountA(Long countA) { this.countA = countA; }

    public Long getCountB() { return countB; }
    public void setCountB(Long countB) { this.countB = countB; }

    public Long getDeltaCount() { return deltaCount; }
    public void setDeltaCount(Long deltaCount) { this.deltaCount = deltaCount; }

    public Long getMaxDurationMsA() { return maxDurationMsA; }
    public void setMaxDurationMsA(Long maxDurationMsA) { this.maxDurationMsA = maxDurationMsA; }

    public Long getMaxDurationMsB() { return maxDurationMsB; }
    public void setMaxDurationMsB(Long maxDurationMsB) { this.maxDurationMsB = maxDurationMsB; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
