package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LatencyOriginReportDto {

    private Long importId;
    private Long anchorLogId;
    private String sessionId;
    private String uuid;
    private String userName;
    private String processName;
    private String filterCode;
    private String actionName;
    private String screenName;
    private String className;
    private Long maxDurationMs;
    private String scopeDescription;
    private int logsInScope;
    private int logsInWindow;
    private String analysisConfidence;
    private String analysisConfidenceReason;
    private String primaryCause;
    private String narrativeSummary;
    private String chainExplanation;
    private String chronologicalText;
    private String clientSummary;
    private String rootCauseSummary;
    /** Terme de filtre pour naviguer vers les logs de la source (ex. MAJ_Insert_…). */
    private String rootCauseSearchTerm;
    /** Ce que signifie cette source (pourquoi elle est citée, sans sur-affirmer). */
    private String rootCauseMeaning;
    private String bottleneckSearchTerm;
    private String measurementWarning;
    private List<LatencyTimelineStepDto> timelineSteps = new ArrayList<>();
    private List<LatencyQueryGroupDto> repeatedQueryGroups = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();
    /**
     * Chaîne « pourquoi » en profondeur (niveau 1 = symptôme, niveau 2 = cause réelle,
     * niveau 3 = mécanisme sous-jacent). Ex. règles lentes → MAJ_Insert masse → volume amont.
     */
    private List<String> whyChain = new ArrayList<>();
    /** Preuves chiffrées cliquables (chiffre du récit → ligne de log). */
    private List<LatencyEvidenceLinkDto> evidenceLinks = new ArrayList<>();
    /** Famille d'explication (SQL, RULES, WS, PRINT, AUTOSTART…). */
    private String latencyFamily;

    // ─── Classification de cause racine (LatencyRootCauseClassifier) ───
    private List<Long> causeLogIds;
    private String rootSqlQuery;
    private Integer rootSqlJoinCount;
    private List<String> rootSqlTables;
    private Integer rootSqlRowCount;
    private String category;
    private Map<String, Object> evidence;

    // ─── Preuves fortes calculées (pour une explication de niveau expert) ───
    /** Plus grand intervalle SANS aucun log dans la fenêtre (le « trou silencieux »), en ms. */
    private Long silentGapMs;
    private String silentGapFrom;
    private String silentGapTo;
    /** Pic mémoire (Mo) observé dans la fenêtre — utile pour suspecter une pause GC. */
    private Integer heapPeakMo;
    /** Fichiers de logs traversés par l'opération (une bascule = attente/contention possible). */
    private List<String> windowFiles;
    /** Même opération exécutée ailleurs : comparaison SQL vs règles (preuve d'intermittence). */
    private List<LatencyExecutionSampleDto> executionSamples;

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }

    public Long getAnchorLogId() { return anchorLogId; }
    public void setAnchorLogId(Long anchorLogId) { this.anchorLogId = anchorLogId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public String getFilterCode() { return filterCode; }
    public void setFilterCode(String filterCode) { this.filterCode = filterCode; }

    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }

    public String getScreenName() { return screenName; }
    public void setScreenName(String screenName) { this.screenName = screenName; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public Long getMaxDurationMs() { return maxDurationMs; }
    public void setMaxDurationMs(Long maxDurationMs) { this.maxDurationMs = maxDurationMs; }

    public String getScopeDescription() { return scopeDescription; }
    public void setScopeDescription(String scopeDescription) { this.scopeDescription = scopeDescription; }

    public int getLogsInScope() { return logsInScope; }
    public void setLogsInScope(int logsInScope) { this.logsInScope = logsInScope; }

    public int getLogsInWindow() { return logsInWindow; }
    public void setLogsInWindow(int logsInWindow) { this.logsInWindow = logsInWindow; }

    public String getAnalysisConfidence() { return analysisConfidence; }
    public void setAnalysisConfidence(String analysisConfidence) { this.analysisConfidence = analysisConfidence; }

    public String getAnalysisConfidenceReason() { return analysisConfidenceReason; }
    public void setAnalysisConfidenceReason(String analysisConfidenceReason) { this.analysisConfidenceReason = analysisConfidenceReason; }

    public String getPrimaryCause() { return primaryCause; }
    public void setPrimaryCause(String primaryCause) { this.primaryCause = primaryCause; }

    public String getNarrativeSummary() { return narrativeSummary; }
    public void setNarrativeSummary(String narrativeSummary) { this.narrativeSummary = narrativeSummary; }

    public String getChainExplanation() { return chainExplanation; }
    public void setChainExplanation(String chainExplanation) { this.chainExplanation = chainExplanation; }

    public String getClientSummary() { return clientSummary; }
    public void setClientSummary(String clientSummary) { this.clientSummary = clientSummary; }

    public String getChronologicalText() { return chronologicalText; }
    public void setChronologicalText(String chronologicalText) { this.chronologicalText = chronologicalText; }

    public String getRootCauseSummary() { return rootCauseSummary; }
    public void setRootCauseSummary(String rootCauseSummary) { this.rootCauseSummary = rootCauseSummary; }

    public String getRootCauseSearchTerm() { return rootCauseSearchTerm; }
    public void setRootCauseSearchTerm(String rootCauseSearchTerm) { this.rootCauseSearchTerm = rootCauseSearchTerm; }

    public String getRootCauseMeaning() { return rootCauseMeaning; }
    public void setRootCauseMeaning(String rootCauseMeaning) { this.rootCauseMeaning = rootCauseMeaning; }

    public String getBottleneckSearchTerm() { return bottleneckSearchTerm; }
    public void setBottleneckSearchTerm(String bottleneckSearchTerm) { this.bottleneckSearchTerm = bottleneckSearchTerm; }

    public String getMeasurementWarning() { return measurementWarning; }
    public void setMeasurementWarning(String measurementWarning) { this.measurementWarning = measurementWarning; }

    public List<LatencyTimelineStepDto> getTimelineSteps() { return timelineSteps; }
    public void setTimelineSteps(List<LatencyTimelineStepDto> timelineSteps) { this.timelineSteps = timelineSteps; }

    public List<LatencyQueryGroupDto> getRepeatedQueryGroups() { return repeatedQueryGroups; }
    public void setRepeatedQueryGroups(List<LatencyQueryGroupDto> repeatedQueryGroups) { this.repeatedQueryGroups = repeatedQueryGroups; }

    public List<String> getRecommendations() { return recommendations; }
    public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }

    public List<String> getWhyChain() { return whyChain; }
    public void setWhyChain(List<String> whyChain) {
        this.whyChain = whyChain != null ? whyChain : new ArrayList<>();
    }

    public List<LatencyEvidenceLinkDto> getEvidenceLinks() { return evidenceLinks; }
    public void setEvidenceLinks(List<LatencyEvidenceLinkDto> evidenceLinks) {
        this.evidenceLinks = evidenceLinks != null ? evidenceLinks : new ArrayList<>();
    }

    public String getLatencyFamily() { return latencyFamily; }
    public void setLatencyFamily(String latencyFamily) { this.latencyFamily = latencyFamily; }

    public List<Long> getCauseLogIds() { return causeLogIds; }
    public void setCauseLogIds(List<Long> causeLogIds) { this.causeLogIds = causeLogIds; }

    public String getRootSqlQuery() { return rootSqlQuery; }
    public void setRootSqlQuery(String rootSqlQuery) { this.rootSqlQuery = rootSqlQuery; }

    public Integer getRootSqlJoinCount() { return rootSqlJoinCount; }
    public void setRootSqlJoinCount(Integer rootSqlJoinCount) { this.rootSqlJoinCount = rootSqlJoinCount; }

    public List<String> getRootSqlTables() { return rootSqlTables; }
    public void setRootSqlTables(List<String> rootSqlTables) { this.rootSqlTables = rootSqlTables; }

    public Integer getRootSqlRowCount() { return rootSqlRowCount; }
    public void setRootSqlRowCount(Integer rootSqlRowCount) { this.rootSqlRowCount = rootSqlRowCount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Map<String, Object> getEvidence() { return evidence; }
    public void setEvidence(Map<String, Object> evidence) { this.evidence = evidence; }

    public Long getSilentGapMs() { return silentGapMs; }
    public void setSilentGapMs(Long silentGapMs) { this.silentGapMs = silentGapMs; }

    public String getSilentGapFrom() { return silentGapFrom; }
    public void setSilentGapFrom(String silentGapFrom) { this.silentGapFrom = silentGapFrom; }

    public String getSilentGapTo() { return silentGapTo; }
    public void setSilentGapTo(String silentGapTo) { this.silentGapTo = silentGapTo; }

    public Integer getHeapPeakMo() { return heapPeakMo; }
    public void setHeapPeakMo(Integer heapPeakMo) { this.heapPeakMo = heapPeakMo; }

    public List<String> getWindowFiles() { return windowFiles; }
    public void setWindowFiles(List<String> windowFiles) { this.windowFiles = windowFiles; }

    public List<LatencyExecutionSampleDto> getExecutionSamples() { return executionSamples; }
    public void setExecutionSamples(List<LatencyExecutionSampleDto> executionSamples) { this.executionSamples = executionSamples; }
}
