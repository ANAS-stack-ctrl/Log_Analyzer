package com.caciopee.loganalyzer.dto;

public class ImportComparisonRequestDto {

    private Long importIdA;
    private Long importIdB;

    public Long getImportIdA() { return importIdA; }
    public void setImportIdA(Long importIdA) { this.importIdA = importIdA; }

    public Long getImportIdB() { return importIdB; }
    public void setImportIdB(Long importIdB) { this.importIdB = importIdB; }
}
