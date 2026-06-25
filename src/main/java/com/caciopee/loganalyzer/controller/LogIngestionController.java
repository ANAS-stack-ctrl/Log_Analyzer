package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.ImportProgressDto;
import com.caciopee.loganalyzer.dto.LocalFolderIngestRequestDto;
import com.caciopee.loganalyzer.ingestion.LogFileIngestionService;
import com.caciopee.loganalyzer.service.ImportProgressService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/ingest")
public class LogIngestionController {

    private final LogFileIngestionService logFileIngestionService;
    private final ImportProgressService importProgressService;

    public LogIngestionController(LogFileIngestionService logFileIngestionService,
                                  ImportProgressService importProgressService) {
        this.logFileIngestionService = logFileIngestionService;
        this.importProgressService = importProgressService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<LogFileIngestionService.UploadResult> uploadLogFiles(@RequestParam("files") MultipartFile[] files) {
        return logFileIngestionService.ingestFiles(files);
    }

    @PostMapping("/local-folder")
    @Profile("dev")
    public LogFileIngestionService.UploadResult ingestLocalFolder(@RequestBody LocalFolderIngestRequestDto request) {
        boolean recursive = request == null || request.isRecursive();
        String path = request != null ? request.getFolderPath() : null;
        return logFileIngestionService.ingestFromDirectory(path, recursive);
    }

    @GetMapping("/progress/{importId}")
    public ImportProgressDto getImportProgress(@PathVariable Long importId) {
        return importProgressService.getProgress(importId);
    }
}