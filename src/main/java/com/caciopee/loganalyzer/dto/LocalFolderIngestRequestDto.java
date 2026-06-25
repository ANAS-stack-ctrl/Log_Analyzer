package com.caciopee.loganalyzer.dto;

public class LocalFolderIngestRequestDto {

    private String folderPath;
    private boolean recursive = true;

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }
}
