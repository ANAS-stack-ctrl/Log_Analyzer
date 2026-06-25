package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.catalog.WorksMessageCatalogService;
import com.caciopee.loganalyzer.analysis.catalog.WorksMessageFamily;
import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.ImportExtractionAuditDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ImportExtractionAuditService {

    private static final int MAX_SAMPLE = 3000;
    private static final int MAX_UNKNOWN_SAMPLES = 12;
    private static final int MAX_SAMPLE_MESSAGE_LEN = 220;

    private final LogEntryRepository logEntryRepository;
    private final LogPatternExtractor logPatternExtractor;
    private final WorksMessageCatalogService messageCatalogService;

    public ImportExtractionAuditService(LogEntryRepository logEntryRepository,
                                        LogPatternExtractor logPatternExtractor,
                                        WorksMessageCatalogService messageCatalogService) {
        this.logEntryRepository = logEntryRepository;
        this.logPatternExtractor = logPatternExtractor;
        this.messageCatalogService = messageCatalogService;
    }

    public ImportExtractionAuditDto audit(Long importId) {
        if (importId == null) {
            throw new IllegalArgumentException("importId requis.");
        }

        long total = logEntryRepository.countByLogImportId(importId);
        int sampleSize = (int) Math.min(total, MAX_SAMPLE);
        List<LogEntry> all = logEntryRepository.findByLogImportIdOrderByLogTimestampAscIdAsc(importId);
        List<LogEntry> sample = all.size() <= sampleSize ? all : all.subList(0, sampleSize);

        long withProcess = 0;
        long withTaskOrAction = 0;
        long withFilter = 0;
        long withObject = 0;
        long workflowRelevant = 0;
        long catalogMatched = 0;
        long graphFamilyMatched = 0;
        Map<String, Long> familyHits = new LinkedHashMap<>();
        List<String> unknownSamples = new ArrayList<>();

        for (LogEntry log : sample) {
            GraphExtraction ex = logPatternExtractor.extractGraph(log);
            boolean process = notBlank(ex.getProcess());
            boolean taskOrAction = notBlank(ex.getTask()) || notBlank(ex.getAction());
            boolean filter = notBlank(ex.getFilter());
            boolean object = notBlank(ex.getObject());

            if (process) withProcess++;
            if (taskOrAction) withTaskOrAction++;
            if (filter) withFilter++;
            if (object) withObject++;
            if (process || taskOrAction || filter) workflowRelevant++;

            Optional<WorksMessageFamily> family = messageCatalogService.classify(log);
            if (family.isPresent()) {
                catalogMatched++;
            } else if (hasContent(log) && unknownSamples.size() < MAX_UNKNOWN_SAMPLES) {
                unknownSamples.add(buildUnknownSample(log));
            }

            Optional<WorksMessageFamily> graphFamily = messageCatalogService.classifyForGraph(log);
            if (graphFamily.isPresent()) {
                graphFamilyMatched++;
                String key = graphFamily.get().getDisplayLabel();
                familyHits.merge(key, 1L, Long::sum);
            }
        }

        long catalogUnknown = sample.size() - catalogMatched;
        double workflowPct = sample.isEmpty() ? 0.0 : round2(workflowRelevant * 100.0 / sample.size());
        double catalogPct = sample.isEmpty() ? 0.0 : round2(catalogMatched * 100.0 / sample.size());
        double graphFamilyPct = sample.isEmpty() ? 0.0 : round2(graphFamilyMatched * 100.0 / sample.size());

        ImportExtractionAuditDto dto = new ImportExtractionAuditDto();
        dto.setImportId(importId);
        dto.setTotalLogs(total);
        dto.setSampledLogs(sample.size());
        dto.setWithProcess(withProcess);
        dto.setWithTaskOrAction(withTaskOrAction);
        dto.setWithFilter(withFilter);
        dto.setWithObject(withObject);
        dto.setWorkflowCoveragePercent(workflowPct);
        dto.setCatalogCoveragePercent(catalogPct);
        dto.setCatalogMatched(catalogMatched);
        dto.setCatalogUnknown(catalogUnknown);
        dto.setCatalogFamilyCount(messageCatalogService.familyCount());
        dto.setFamilyHits(familyHits);
        dto.setGraphFamilyCoveragePercent(graphFamilyPct);
        dto.setUnknownSamples(unknownSamples);
        dto.setNote(buildNote(total, sample.size(), workflowPct, catalogPct, graphFamilyPct, catalogUnknown));
        return dto;
    }

    private boolean hasContent(LogEntry log) {
        return notBlank(log.getMessage()) || notBlank(log.getProcessName());
    }

    private String buildUnknownSample(LogEntry log) {
        String process = log.getProcessName() == null ? "" : log.getProcessName().trim();
        String message = log.getMessage() == null ? "" : log.getMessage().trim();
        String combined = (process.isEmpty() ? "" : "[" + truncate(process, 60) + "] ")
                + truncate(message, MAX_SAMPLE_MESSAGE_LEN);
        return combined.isBlank() ? "(ligne vide)" : combined;
    }

    private String buildNote(long total, long sampled, double workflowPct, double catalogPct,
                             double graphFamilyPct, long unknown) {
        if (total == 0) {
            return "Aucun log en base pour cet import.";
        }
        String base = "Catalogue : " + catalogPct + "% des lignes reconnues (" + messageCatalogService.familyCount()
                + " familles). Familles utiles graphe : " + graphFamilyPct + "%. Workflow extrait : ~" + workflowPct + "%.";
        if (unknown > 0) {
            base += " " + unknown + " ligne(s) inconnue(s) dans l'échantillon — ajoutez-les dans works-message-families.json puis LogPatternExtractor.";
        }
        if (sampled < total) {
            base += " Audit sur " + sampled + " / " + total + " lignes.";
        }
        return base;
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max) + "…";
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
