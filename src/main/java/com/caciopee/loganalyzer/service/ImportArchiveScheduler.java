package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.config.ImportArchiveProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ImportArchiveScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImportArchiveScheduler.class);

    private final ImportArchiveProperties properties;
    private final ImportArchiveService importArchiveService;

    public ImportArchiveScheduler(ImportArchiveProperties properties,
                                  ImportArchiveService importArchiveService) {
        this.properties = properties;
        this.importArchiveService = importArchiveService;
    }

    @Scheduled(cron = "${app.archive.cron:0 0 3 * * *}")
    public void runScheduledMaintenance() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            importArchiveService.runMaintenance();
        } catch (Exception e) {
            log.error("Échec maintenance archive planifiée : {}", e.getMessage(), e);
        }
    }
}
