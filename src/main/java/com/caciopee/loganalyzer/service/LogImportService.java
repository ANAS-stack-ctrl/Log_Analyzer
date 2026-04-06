package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.repository.LogImportRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class LogImportService {

    private final LogImportRepository logImportRepository;

    public LogImportService(LogImportRepository logImportRepository) {
        this.logImportRepository = logImportRepository;
    }

    public LogImport createStartedImport(String tempFileName,
                                         String originalFileName,
                                         String fileHash,
                                         Long fileSizeBytes) {
        LogImport logImport = new LogImport();
        logImport.setFileName(tempFileName);
        logImport.setOriginalFileName(originalFileName);
        logImport.setFileHash(fileHash);
        logImport.setFileSizeBytes(fileSizeBytes);
        logImport.setStartedAt(LocalDateTime.now());
        logImport.setStatus("RUNNING");
        logImport.setTotalLines(0);
        logImport.setProcessedLines(0);
        logImport.setFailedLines(0);
        return logImportRepository.save(logImport);
    }

    public Optional<LogImport> findExistingImportedLog(String fileHash, String originalFileName) {
        Optional<LogImport> byHash = logImportRepository.findFirstByFileHashAndStatus(fileHash, "DONE");
        if (byHash.isPresent()) {
            return byHash;
        }

        return logImportRepository.findFirstByOriginalFileNameIgnoreCaseAndStatus(originalFileName, "DONE");
    }

    public LogImport markImportSuccess(Long importId, int totalLines, int processedLines, int failedLines) {
        LogImport logImport = getById(importId);
        logImport.setFinishedAt(LocalDateTime.now());
        logImport.setStatus("DONE");
        logImport.setTotalLines(totalLines);
        logImport.setProcessedLines(processedLines);
        logImport.setFailedLines(failedLines);
        return logImportRepository.save(logImport);
    }

    public LogImport markImportFailed(Long importId, int totalLines, int processedLines, int failedLines, String errorMessage) {
        LogImport logImport = getById(importId);
        logImport.setFinishedAt(LocalDateTime.now());
        logImport.setStatus("FAILED");
        logImport.setTotalLines(totalLines);
        logImport.setProcessedLines(processedLines);
        logImport.setFailedLines(failedLines);
        logImport.setErrorMessage(errorMessage);
        return logImportRepository.save(logImport);
    }

    public LogImport getById(Long id) {
        return logImportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Import not found with id: " + id));
    }

    public List<LogImport> getAllImports() {
        return logImportRepository.findAll().stream()
                .sorted(
                        Comparator.comparing(
                                        LogImport::getStartedAt,
                                        Comparator.nullsLast(Comparator.reverseOrder())
                                )
                                .thenComparing(
                                        LogImport::getId,
                                        Comparator.nullsLast(Comparator.reverseOrder())
                                )
                )
                .toList();
    }
}