package com.caciopee.loganalyzer.ingestion;

import com.caciopee.loganalyzer.dto.LogImportResult;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.parser.LogParserService;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.service.LogImportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class LogFileIngestionService {

    private static final int BATCH_SIZE = 500;

    private final LogParserService logParserService;
    private final LogEntryRepository logEntryRepository;
    private final LogImportService logImportService;

    public LogFileIngestionService(LogParserService logParserService,
                                   LogEntryRepository logEntryRepository,
                                   LogImportService logImportService) {
        this.logParserService = logParserService;
        this.logEntryRepository = logEntryRepository;
        this.logImportService = logImportService;
    }

    @Transactional
    public LogImportResult ingestFile(String filePath, String originalFileName) throws IOException {
        File file = new File(filePath);
        String fileHash = computeSha256(file);
        long fileSize = file.length();

        Optional<LogImport> existingImport = logImportService.findExistingImportedLog(fileHash, originalFileName);
        if (existingImport.isPresent()) {
            LogImport existing = existingImport.get();
            return new LogImportResult(
                    true,
                    "Fichier déjà importé. Import ID existant: " + existing.getId(),
                    existing
            );
        }

        int totalLines = 0;
        int processedLines = 0;
        int failedLines = 0;

        LogImport logImport = logImportService.createStartedImport(filePath, originalFileName, fileHash, fileSize);
        List<LogEntry> batch = new ArrayList<>(BATCH_SIZE);

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;

            while ((line = br.readLine()) != null) {
                totalLines++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    LogEntry logEntry = logParserService.parseLine(line);
                    logEntry.setLogImport(logImport);
                    batch.add(logEntry);

                    if (batch.size() >= BATCH_SIZE) {
                        logEntryRepository.saveAll(batch);
                        processedLines += batch.size();
                        batch.clear();
                    }
                } catch (Exception e) {
                    failedLines++;
                }
            }

            if (!batch.isEmpty()) {
                logEntryRepository.saveAll(batch);
                processedLines += batch.size();
                batch.clear();
            }

            LogImport savedImport = logImportService.markImportSuccess(
                    logImport.getId(),
                    totalLines,
                    processedLines,
                    failedLines
            );

            return new LogImportResult(
                    false,
                    "File uploaded and processed successfully. Import ID: " + savedImport.getId(),
                    savedImport
            );

        } catch (Exception e) {
            LogImport failedImport = logImportService.markImportFailed(
                    logImport.getId(),
                    totalLines,
                    processedLines,
                    failedLines,
                    e.getMessage()
            );

            return new LogImportResult(
                    false,
                    "Error while uploading and processing file: " + e.getMessage(),
                    failedImport
            );
        }
    }

    private String computeSha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = bis.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("Unable to compute file hash: " + e.getMessage(), e);
        }
    }
}