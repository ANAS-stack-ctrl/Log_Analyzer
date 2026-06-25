package com.caciopee.loganalyzer.analysis.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class WorksMessageFamily {

    private String id;
    private String group;
    private String label;
    /** Libellé court pour l'interface (prioritaire sur label si renseigné). */
    private String userLabel;
    private List<String> patterns = new ArrayList<>();
    private List<String> extractable = new ArrayList<>();
    private String status;
    private boolean columnOnly;
    /** Si false : compte pour l'audit catalogue mais pas de nœud FAMILY dans le graphe. */
    private boolean attachToGraph = true;

    private transient List<Pattern> compiledPatterns;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getUserLabel() { return userLabel; }
    public void setUserLabel(String userLabel) { this.userLabel = userLabel; }

    /** Libellé affiché dans le graphe et l'audit (sans code MET-/PROC-). */
    public String getDisplayLabel() {
        return CatalogUserLabels.displayLabel(this);
    }

    public String getGroupDisplayLabel() {
        return CatalogUserLabels.groupLabel(group);
    }

    public List<String> getPatterns() { return patterns; }
    public void setPatterns(List<String> patterns) { this.patterns = patterns; }

    public List<String> getExtractable() { return extractable; }
    public void setExtractable(List<String> extractable) { this.extractable = extractable; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isColumnOnly() { return columnOnly; }
    public void setColumnOnly(boolean columnOnly) { this.columnOnly = columnOnly; }

    public boolean isAttachToGraph() { return attachToGraph; }
    public void setAttachToGraph(boolean attachToGraph) { this.attachToGraph = attachToGraph; }

    public List<Pattern> compiledPatterns() {
        if (compiledPatterns == null) {
            compiledPatterns = new ArrayList<>();
            for (String p : patterns) {
                if (p != null && !p.isBlank()) {
                    compiledPatterns.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE));
                }
            }
        }
        return compiledPatterns;
    }

    public boolean matchesMessage(String message) {
        if (columnOnly || message == null || message.isBlank()) {
            return false;
        }
        for (Pattern pattern : compiledPatterns()) {
            if (pattern.matcher(message).find()) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesColumn(String processColumn) {
        if (processColumn == null || processColumn.isBlank()) {
            return false;
        }
        for (Pattern pattern : compiledPatterns()) {
            if (pattern.matcher(processColumn.trim()).find()) {
                return true;
            }
        }
        return false;
    }
}
