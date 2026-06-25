package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.repository.LogImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ImportAccessService {

    private final LogImportRepository logImportRepository;

    public ImportAccessService(LogImportRepository logImportRepository) {
        this.logImportRepository = logImportRepository;
    }

    @Transactional
    public void touch(Long importId) {
        if (importId == null) {
            return;
        }
        logImportRepository.findById(importId).ifPresent(imp -> {
            imp.setLastAccessedAt(LocalDateTime.now());
            logImportRepository.save(imp);
        });
    }

    @Transactional
    public void touchAll(Iterable<Long> importIds) {
        if (importIds == null) {
            return;
        }
        for (Long importId : importIds) {
            touch(importId);
        }
    }
}
