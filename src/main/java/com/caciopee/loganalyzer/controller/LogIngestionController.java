package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.LogImportResult;
import com.caciopee.loganalyzer.ingestion.LogFileIngestionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@RestController
@RequestMapping("/ingest")
public class LogIngestionController {

    private final LogFileIngestionService logFileIngestionService;

    public LogIngestionController(LogFileIngestionService logFileIngestionService) {
        this.logFileIngestionService = logFileIngestionService;
    }

    @PostMapping
    public String ingestLogs(@RequestParam String filePath) {
        try {
            LogImportResult result = logFileIngestionService.ingestFile(filePath, filePath);
            return result.getMessage();
        } catch (Exception e) {
            return "Error while ingesting file: " + e.getMessage();
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadLogFile(@RequestParam("file") MultipartFile file) {
        File tempFile = null;

        try {
            if (file.isEmpty()) {
                return "Error: file is empty.";
            }

            tempFile = File.createTempFile("upload-", "-" + file.getOriginalFilename());
            file.transferTo(tempFile);

            LogImportResult result = logFileIngestionService.ingestFile(
                    tempFile.getAbsolutePath(),
                    file.getOriginalFilename()
            );

            return result.getMessage();

        } catch (Exception e) {
            return "Error while uploading and processing file: " + e.getMessage();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}