package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.ingestion.LogFileIngestionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/ingest")
public class LogIngestionController {

    private final LogFileIngestionService logFileIngestionService;

    public LogIngestionController(LogFileIngestionService logFileIngestionService) {
        this.logFileIngestionService = logFileIngestionService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<LogFileIngestionService.UploadResult> uploadLogFiles(@RequestParam("files") MultipartFile[] files) {
        return logFileIngestionService.ingestFiles(files);
    }
}