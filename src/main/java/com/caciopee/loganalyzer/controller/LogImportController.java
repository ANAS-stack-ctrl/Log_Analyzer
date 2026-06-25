package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.ImportDetailDto;
import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.repository.LogImportRepository;
import com.caciopee.loganalyzer.service.ImportAccessService;
import com.caciopee.loganalyzer.service.ImportDetailService;
import com.caciopee.loganalyzer.service.LogImportManagementService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/imports")
public class LogImportController {

    private final LogImportRepository logImportRepository;
    private final LogImportManagementService logImportManagementService;
    private final ImportAccessService importAccessService;
    private final ImportDetailService importDetailService;

    public LogImportController(LogImportRepository logImportRepository,
                               LogImportManagementService logImportManagementService,
                               ImportAccessService importAccessService,
                               ImportDetailService importDetailService) {
        this.logImportRepository = logImportRepository;
        this.logImportManagementService = logImportManagementService;
        this.importAccessService = importAccessService;
        this.importDetailService = importDetailService;
    }

    @GetMapping
    public List<LogImport> getAllImports() {
        return logImportRepository.findAllByOrderByStartedAtDesc();
    }

    @GetMapping("/{id}")
    public LogImport getImportById(@PathVariable Long id) {
        importAccessService.touch(id);
        return logImportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Import introuvable: " + id));
    }

    @GetMapping("/{id}/detail")
    public ImportDetailDto getImportDetail(@PathVariable Long id) {
        importAccessService.touch(id);
        return importDetailService.getImportDetail(id);
    }

    @DeleteMapping("/{id}")
    public void deleteImport(@PathVariable Long id) {
        logImportManagementService.deleteImport(id);
    }
}