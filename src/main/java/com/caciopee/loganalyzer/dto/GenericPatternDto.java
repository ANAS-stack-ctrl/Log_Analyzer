package com.caciopee.loganalyzer.dto;

public class GenericPatternDto {
    private String pattern;
    private long count;
    private String example;
    private Long importId;

    public GenericPatternDto(String pattern, long count, String example, Long importId) {
        this.pattern = pattern;
        this.count = count;
        this.example = example;
        this.importId = importId;
    }

    public String getPattern() { return pattern; }
    public long getCount() { return count; }
    public String getExample() { return example; }
    public Long getImportId() { return importId; }
}