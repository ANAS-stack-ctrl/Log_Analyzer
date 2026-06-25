package com.caciopee.loganalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.archive")
public class ImportArchiveProperties {

    private boolean enabled = false;
    private int inactiveDays = 60;
    private int purgeDays = 365;
    private int batchSize = 1;
    private String cron = "0 0 3 * * *";
    private final Datasource datasource = new Datasource();

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

    public Datasource getDatasource() {
        return datasource;
    }

    public static class Datasource {
        private String url = "jdbc:postgresql://localhost:5432/logs_archive_db";
        private String username = "postgres";
        private String password = "admin";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
