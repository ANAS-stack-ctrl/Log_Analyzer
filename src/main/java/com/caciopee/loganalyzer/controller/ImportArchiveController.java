package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.ImportArchiveRunResultDto;
import com.caciopee.loganalyzer.dto.ImportArchiveStatusDto;
import com.caciopee.loganalyzer.service.ImportArchiveService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/imports/archive")
public class ImportArchiveController {

    private final ImportArchiveService importArchiveService;

    public ImportArchiveController(ImportArchiveService importArchiveService) {
        this.importArchiveService = importArchiveService;
    }

    @GetMapping("/status")
    public ImportArchiveStatusDto status() {
        return importArchiveService.getStatus();
    }

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ImportArchiveRunResultDto runNow() {
        return importArchiveService.runMaintenance();
    }
}
