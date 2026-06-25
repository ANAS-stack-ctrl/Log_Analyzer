package com.caciopee.loganalyzer.analysis.catalog;

import com.caciopee.loganalyzer.dto.WorksCatalogOverviewDto;
import com.caciopee.loganalyzer.dto.WorksMessageFamilySummaryDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class WorksMessageCatalogService {

    private final ObjectMapper objectMapper;
    private List<WorksMessageFamily> families = List.of();
    private JsonNode catalogRoot;
    private JsonNode lineStructure;

    public WorksMessageCatalogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadCatalog() {
        try (InputStream in = new ClassPathResource("works-message-families.json").getInputStream()) {
            catalogRoot = objectMapper.readTree(in);
            JsonNode arr = catalogRoot.path("families");
            List<WorksMessageFamily> loaded = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    WorksMessageFamily family = objectMapper.treeToValue(node, WorksMessageFamily.class);
                    if (family.getId() != null) {
                        loaded.add(family);
                    }
                }
            }
            this.families = Collections.unmodifiableList(loaded);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de charger works-message-families.json", e);
        }

        try (InputStream in = new ClassPathResource("works-log-line-structure.json").getInputStream()) {
            lineStructure = objectMapper.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de charger works-log-line-structure.json", e);
        }
    }

    public List<WorksMessageFamily> getFamilies() {
        return families;
    }

    public int familyCount() {
        return families.size();
    }

    public JsonNode getLineStructure() {
        return lineStructure;
    }

    public WorksCatalogOverviewDto getCatalogOverview() {
        WorksCatalogOverviewDto dto = new WorksCatalogOverviewDto();
        if (catalogRoot != null) {
            dto.setCatalogVersion(catalogRoot.path("version").asText(""));
            dto.setDescription(catalogRoot.path("description").asText(""));
        }
        dto.setFamilyCount(families.size());
        dto.setLineStructure(lineStructure);

        List<WorksMessageFamilySummaryDto> summaries = new ArrayList<>();
        for (WorksMessageFamily family : families) {
            WorksMessageFamilySummaryDto summary = new WorksMessageFamilySummaryDto();
            summary.setId(family.getId());
            summary.setGroup(family.getGroup());
            summary.setLabel(family.getLabel());
            summary.setDisplayLabel(family.getDisplayLabel());
            summary.setPatterns(family.getPatterns() != null ? family.getPatterns() : List.of());
            summary.setExtractable(family.getExtractable() != null ? family.getExtractable() : List.of());
            summary.setStatus(family.getStatus());
            summary.setColumnOnly(family.isColumnOnly());
            summary.setAttachToGraph(family.isAttachToGraph());
            summaries.add(summary);
        }
        dto.setFamilies(summaries);
        return dto;
    }

    /**
     * Première famille reconnue (audit catalogue, inclut motifs génériques uuid/took).
     */
    public Optional<WorksMessageFamily> classify(LogEntry log) {
        return classifyInternal(log, false);
    }

    /**
     * Famille utile pour le graphe L2 — ignore les motifs trop génériques (uuid seul, took seul…).
     */
    public Optional<WorksMessageFamily> classifyForGraph(LogEntry log) {
        return classifyInternal(log, true);
    }

    private Optional<WorksMessageFamily> classifyInternal(LogEntry log, boolean graphOnly) {
        if (log == null) {
            return Optional.empty();
        }
        String message = safe(log.getMessage());
        String processColumn = safe(log.getProcessName());

        if (message.isBlank() && processColumn.isBlank()) {
            return Optional.empty();
        }

        for (WorksMessageFamily family : families) {
            if (graphOnly && !family.isAttachToGraph()) {
                continue;
            }
            if (family.matchesMessage(message)) {
                return Optional.of(family);
            }
        }
        for (WorksMessageFamily family : families) {
            if (graphOnly && !family.isAttachToGraph()) {
                continue;
            }
            if (family.matchesColumn(processColumn)) {
                return Optional.of(family);
            }
        }
        return Optional.empty();
    }

    public boolean isKnown(LogEntry log) {
        return classify(log).isPresent();
    }

    public boolean isKnownForGraph(LogEntry log) {
        return classifyForGraph(log).isPresent();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
