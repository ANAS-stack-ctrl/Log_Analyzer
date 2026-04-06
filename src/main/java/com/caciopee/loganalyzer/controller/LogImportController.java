package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.service.LogImportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/imports")
public class LogImportController {

    private final LogImportService logImportService;

    public LogImportController(LogImportService logImportService) {
        this.logImportService = logImportService;
    }

    @GetMapping
    public List<LogImport> getAllImports() {
        return logImportService.getAllImports();
    }

    @GetMapping("/{id}")
    public LogImport getImportById(@PathVariable Long id) {
        return logImportService.getById(id);
    }
}