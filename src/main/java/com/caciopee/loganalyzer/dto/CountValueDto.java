package com.caciopee.loganalyzer.dto;

public class CountValueDto {

    private String value;
    private long count;

    public CountValueDto() {
    }

    public CountValueDto(String value, long count) {
        this.value = value;
        this.count = count;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}