package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogImportRepository;
import com.caciopee.loganalyzer.repository.LogRawMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogImportManagementService {

    private final LogImportRepository logImportRepository;
    private final LogEntryRepository logEntryRepository;
    private final LogRawMessageRepository logRawMessageRepository;

    public LogImportManagementService(LogImportRepository logImportRepository,
                                     LogEntryRepository logEntryRepository,
                                     LogRawMessageRepository logRawMessageRepository) {
        this.logImportRepository = logImportRepository;
        this.logEntryRepository = logEntryRepository;
        this.logRawMessageRepository = logRawMessageRepository;
    }

    @Transactional
    public void deleteImport(Long importId) {
        if (importId == null) {
            throw new IllegalArgumentException("importId est requis.");
        }
        if (!logImportRepository.existsById(importId)) {
            throw new IllegalArgumentException("Import introuvable: " + importId);
        }

        // Order matters when DB constraints don't cascade.
        logRawMessageRepository.deleteByImportId(importId);
        logEntryRepository.deleteByImportId(importId);
        logImportRepository.deleteById(importId);
    }
}

