package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.repository.LogImportRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/imports")
public class LogImportController {

    private final LogImportRepository logImportRepository;

    public LogImportController(LogImportRepository logImportRepository) {
        this.logImportRepository = logImportRepository;
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
}