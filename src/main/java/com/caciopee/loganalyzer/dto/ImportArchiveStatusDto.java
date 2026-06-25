package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;

public class ImportArchiveStatusDto {

    private boolean enabled;
    private int inactiveDays;
    private int purgeDays;
    private int batchSize;
    private String cron;
    private String archiveDatabaseUrl;
    private LocalDateTime lastRunAt;
    private int lastArchivedCount;
    private int lastPurgedCount;
    private String lastRunMessage;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getInactiveDays() {
        return inactiveDays;
    }

    public void setInactiveDays(int inactiveDays) {
        this.inactiveDays = inactiveDays;
    }

    public int getPurgeDays() {
        return purgeDays;
    }

    public void setPurgeDays(int purgeDays) {
        this.purgeDays = purgeDays;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getArchiveDatabaseUrl() {
        return archiveDatabaseUrl;
    }

    public void setArchiveDatabaseUrl(String archiveDatabaseUrl) {
        this.archiveDatabaseUrl = archiveDatabaseUrl;
    }

    public LocalDateTime getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(LocalDateTime lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public int getLastArchivedCount() {
        return lastArchivedCount;
    }

    public void setLastArchivedCount(int lastArchivedCount) {
        this.lastArchivedCount = lastArchivedCount;
    }

    public int getLastPurgedCount() {
        return lastPurgedCount;
    }

    public void setLastPurgedCount(int lastPurgedCount) {
        this.lastPurgedCount = lastPurgedCount;
    }

    public String getLastRunMessage() {
        return lastRunMessage;
    }

    public void setLastRunMessage(String lastRunMessage) {
        this.lastRunMessage = lastRunMessage;
    }
}
