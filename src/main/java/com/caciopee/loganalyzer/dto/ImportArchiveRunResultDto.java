package com.caciopee.loganalyzer.dto;

public class ImportArchiveRunResultDto {

    private int archivedImports;
    private int purgedImports;
    private String message;

    public ImportArchiveRunResultDto() {
    }

    public ImportArchiveRunResultDto(int archivedImports, int purgedImports, String message) {
        this.archivedImports = archivedImports;
        this.purgedImports = purgedImports;
        this.message = message;
    }

    public int getArchivedImports() {
        return archivedImports;
    }

    public void setArchivedImports(int archivedImports) {
        this.archivedImports = archivedImports;
    }

    public int getPurgedImports() {
        return purgedImports;
    }

    public void setPurgedImports(int purgedImports) {
        this.purgedImports = purgedImports;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
