package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.ImportProgressDto;
import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.entity.LogImportStatus;
import com.caciopee.loganalyzer.repository.LogImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ImportProgressService {

    private static final long LINES_PER_SLICE_HINT = 25_000L;

    private final LogImportRepository logImportRepository;

    public ImportProgressService(LogImportRepository logImportRepository) {
        this.logImportRepository = logImportRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initProgress(Long importId, int fileCount) {
        LogImport imp = requireImport(importId);
        imp.setProcessingFileCount(fileCount);
        imp.setProcessingFileIndex(0);
        imp.setProcessingPercent(0);
        imp.setProcessingCurrentFile("Préparation…");
        logImportRepository.save(imp);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long importId, int fileIndex, int fileCount, String currentFile,
                               long linesRead, long linesInCurrentFile) {
        LogImport imp = requireImport(importId);
        imp.setProcessingFileCount(fileCount);
        imp.setProcessingFileIndex(fileIndex);
        imp.setProcessingCurrentFile(truncate(currentFile, 480));
        imp.setProcessingPercent(computePercent(fileIndex, fileCount, linesInCurrentFile));
        imp.setTotalLines(safeInt(linesRead));
        logImportRepository.save(imp);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateLines(Long importId, int fileIndex, int fileCount, String currentFile,
                            long linesRead, int parsedLines, long linesInCurrentFile) {
        LogImport imp = requireImport(importId);
        imp.setProcessingFileCount(fileCount);
        imp.setProcessingFileIndex(fileIndex);
        imp.setProcessingCurrentFile(truncate(currentFile, 480));
        imp.setTotalLines(safeInt(linesRead));
        imp.setParsedLines(parsedLines);
        imp.setProcessingPercent(computePercent(fileIndex, fileCount, linesInCurrentFile));
        logImportRepository.save(imp);
    }

    @Transactional(readOnly = true)
    public ImportProgressDto getProgress(Long importId) {
        LogImport imp = logImportRepository.findById(importId)
                .orElseThrow(() -> new IllegalArgumentException("Import introuvable: " + importId));

        boolean finished = imp.getStatus() != LogImportStatus.PROCESSING;
        ImportProgressDto dto = new ImportProgressDto();
        dto.setImportId(imp.getId());
        dto.setStatus(imp.getStatus() != null ? imp.getStatus().name() : "UNKNOWN");
        dto.setPercent(imp.getProcessingPercent() != null ? imp.getProcessingPercent() : (finished ? 100 : 0));
        dto.setFileIndex(imp.getProcessingFileIndex());
        dto.setFileCount(imp.getProcessingFileCount());
        dto.setCurrentFile(imp.getProcessingCurrentFile());
        dto.setLinesRead(imp.getTotalLines() != null ? imp.getTotalLines().longValue() : 0L);
        dto.setParsedLines(imp.getParsedLines());
        dto.setUpdatedAt(imp.getUpdatedAt());
        dto.setFinished(finished);
        dto.setSuccess(finished && imp.getStatus() != LogImportStatus.FAILED);
        dto.setMessage(finished ? firstNonBlank(imp.getSummary(), imp.getErrorMessage()) : liveMessage(imp));
        return dto;
    }

    private LogImport requireImport(Long importId) {
        return logImportRepository.findById(importId)
                .orElseThrow(() -> new IllegalArgumentException("Import introuvable: " + importId));
    }

    private int computePercent(int fileIndex, int fileCount, long linesInCurrentFile) {
        if (fileCount <= 0) return 0;
        if (fileIndex <= 0) return 1;

        int slice = Math.max(1, 100 / fileCount);
        int completedBase = ((fileIndex - 1) * 100) / fileCount;
        long intra = linesInCurrentFile <= 0 ? 0 : Math.min(slice - 1, (linesInCurrentFile * (slice - 1)) / LINES_PER_SLICE_HINT);
        return Math.min(99, completedBase + (int) intra);
    }

    private int safeInt(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    private String liveMessage(LogImport imp) {
        int idx = imp.getProcessingFileIndex() != null ? imp.getProcessingFileIndex() : 0;
        int total = imp.getProcessingFileCount() != null ? imp.getProcessingFileCount() : 0;
        long lines = imp.getTotalLines() != null ? imp.getTotalLines() : 0L;
        int parsed = imp.getParsedLines() != null ? imp.getParsedLines() : 0;
        String file = imp.getProcessingCurrentFile() != null ? imp.getProcessingCurrentFile() : "—";
        if (total > 0) {
            return "Fichier " + idx + "/" + total + " — " + file
                    + " — " + lines + " lignes lues (" + parsed + " parsées)"
                    + " — le % avance surtout entre chaque fichier";
        }
        return "Analyse en cours — " + lines + " lignes lues";
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return "";
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
