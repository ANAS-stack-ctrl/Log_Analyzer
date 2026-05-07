package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.repository.LogImportRepository;
import com.caciopee.loganalyzer.service.LogImportManagementService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/imports")
public class LogImportController {

    private final LogImportRepository logImportRepository;
    private final LogImportManagementService logImportManagementService;

    public LogImportController(LogImportRepository logImportRepository,
                               LogImportManagementService logImportManagementService) {
        this.logImportRepository = logImportRepository;
        this.logImportManagementService = logImportManagementService;
    }

    @GetMapping
    public List<LogImport> getAllImports() {
        return logImportRepository.findAllByOrderByStartedAtDesc();
    }

    @GetMapping("/{id}")
    public LogImport getImportById(@PathVariable Long id) {
        return logImportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Import introuvable: " + id));
    }

    @DeleteMapping("/{id}")
    public void deleteImport(@PathVariable Long id) {
        logImportManagementService.deleteImport(id);
    }
}