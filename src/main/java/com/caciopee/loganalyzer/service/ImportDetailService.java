package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.ImportAnalysisSummaryDto;
import com.caciopee.loganalyzer.dto.ImportDetailDto;
import com.caciopee.loganalyzer.dto.ImportFileStatsDto;
import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogImportRepository;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImportDetailService {

    private static final long SLOW_THRESHOLD_MS = 2000L;

    private final LogImportRepository logImportRepository;
    private final LogEntryRepository logEntryRepository;
    private final LogAnalysisService logAnalysisService;

    public ImportDetailService(LogImportRepository logImportRepository,
                               LogEntryRepository logEntryRepository,
                               LogAnalysisService logAnalysisService) {
        this.logImportRepository = logImportRepository;
        this.logEntryRepository = logEntryRepository;
        this.logAnalysisService = logAnalysisService;
    }

    public ImportDetailDto getImportDetail(Long importId) {
        LogImport logImport = logImportRepository.findById(importId)
                .orElseThrow(() -> new IllegalArgumentException("Import introuvable : " + importId));

        ImportAnalysisSummaryDto summary = logAnalysisService.getImportSummary(importId);

        ImportDetailDto dto = new ImportDetailDto();
        dto.setSummary(summary);
        dto.setFileSize(logImport.getFileSize());
        dto.setFileHash(logImport.getFileHash());
        dto.setLastAccessedAt(logImport.getLastAccessedAt());
        dto.setDistinctUsers(logEntryRepository.countDistinctUsersByImport(importId));
        dto.setSlowLogCount(logEntryRepository.countSlowLogsByImport(importId, SLOW_THRESHOLD_MS));
        Long maxDuration = logEntryRepository.maxDurationMsByImport(importId);
        dto.setMaxDurationMs(maxDuration == null ? 0L : maxDuration);
        dto.setSourceFiles(buildFileStats(importId));
        return dto;
    }

    private List<ImportFileStatsDto> buildFileStats(Long importId) {
        List<Object[]> rows = logEntryRepository.aggregateSourceFilesByImport(importId);
        List<ImportFileStatsDto> files = new ArrayList<>();

        for (Object[] row : rows) {
            ImportFileStatsDto file = new ImportFileStatsDto();
            file.setSourceFileName(row[0] != null ? String.valueOf(row[0]) : "—");
            file.setSourceRelativePath(row[1] != null ? String.valueOf(row[1]) : file.getSourceFileName());
            file.setLogCount(row[2] != null ? ((Number) row[2]).longValue() : 0L);
            file.setErrorCount(row[3] != null ? ((Number) row[3]).longValue() : 0L);
            file.setSlowLogCount(row[4] != null ? ((Number) row[4]).longValue() : 0L);
            file.setMaxDurationMs(row[5] != null ? ((Number) row[5]).longValue() : null);
            file.setFirstLogTimestamp(toLocalDateTime(row[6]));
            file.setLastLogTimestamp(toLocalDateTime(row[7]));
            files.add(file);
        }
        return files;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }
}
