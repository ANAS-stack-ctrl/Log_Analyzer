package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.dto.LatencyQueryGroupDto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Faits mesurés extraits des logs WORKS — base factuelle pour le récit client (sans LLM).
 */
public class WorksLatencyFacts {

    private String userName;
    private String processName;
    private String filterCode;
    private String actionName;
    private String screenName;
    private String sessionId;
    private String uuid;
    private String className;

    private LocalDateTime operationStart;
    private LocalDateTime operationEnd;

    private Long rootQueryMs;
    private Integer rootRowCount;
    private Integer rootMemoryMo;
    private int sqlJoinCount;

    private Long globalMs;
    private Integer globalRowCount;
    private boolean multithreaded;

    private Integer elementCount;
    private Integer partitionSize;
    private Integer threadPoolSize;
    private Integer batchCount;

    private int loadChildSampleCount;
    private Long loadChildMinMs;
    private Long loadChildMaxMs;
    private Long loadChildAvgMs;

    private int loadB2SampleCount;
    private Long loadB2AvgMs;

    private int searchAttrSampleCount;
    private Long searchAttrAvgMs;

    private int partitionalEndCount;
    private Integer maxThreadProgressTotal;

    private Integer memoryPeakMo;
    private Long renderingMs;
    private Long rulesEngineMs;
    private Long postSearchRulesMs;
    /** Durée Aggregate data / End aggregate data (ms), si journalisée. */
    private Long aggregateMs;

    private boolean fastDownstream;

    /** Libellé métier (ex. « Rapprocher Tout ») issu de doAction. */
    private String businessActionLabel;
    /** Tâche WORKS (ex. Match) extraite de running rules. */
    private String taskName;
    /** Filtre chargé juste avant fire rules (contexte volume). */
    private String preRulesFilterCode;
    private Long preRulesSearchMs;
    private Integer preRulesRowCount;
    /** Effets de bord dans la fenêtre START→END fire rules. */
    private int majInsertTotal;
    private String majInsertDominantType;
    private int majInsertDominantCount;
    private Integer insertRelChildCount;
    private Long insertRelChildMs;
    private Integer insertRelRootCount;
    private Long insertRelRootMs;
    private Long savePersistMs;
    private String savePersistKey;

    /**
     * Noms de règles Drools détectés dans la fenêtre (souvent via
     * {@code filter code [Rule_xxx_0.java]}), triés par fréquence décroissante.
     */
    private List<String> droolsRuleNames = new ArrayList<>();
    /** Occurrences par nom de règle Drools (clé = nom tel que dans les logs). */
    private List<LatencyQueryGroupDto> droolsRuleHits = new ArrayList<>();

    /** Famille métier affinée (WS / PRINT / AUTOSTART…) pour le récit. */
    private String latencyFamily;

    /** Groupes de requêtes SQL répétées (même filtre/règle) triés par temps cumulé décroissant. */
    private List<LatencyQueryGroupDto> repeatedQueryGroups = new ArrayList<>();

    public List<LatencyQueryGroupDto> getRepeatedQueryGroups() { return repeatedQueryGroups; }
    public void setRepeatedQueryGroups(List<LatencyQueryGroupDto> repeatedQueryGroups) {
        this.repeatedQueryGroups = repeatedQueryGroups != null ? repeatedQueryGroups : new ArrayList<>();
    }

    /**
     * Groupe répété dominant : le contrôle métier qui rejoue la même requête SQL le
     * plus longtemps au total (au moins 2 exécutions). C'est la "source du problème"
     * pour une opération dominée par le moteur de règles.
     */
    public LatencyQueryGroupDto dominantRepeatedGroup() {
        LatencyQueryGroupDto best = null;
        for (LatencyQueryGroupDto group : repeatedQueryGroups) {
            if (group.getCount() < 2) {
                continue;
            }
            if (best == null || group.getTotalMs() > best.getTotalMs()) {
                best = group;
            }
        }
        return best;
    }

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

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public LocalDateTime getOperationStart() { return operationStart; }
    public void setOperationStart(LocalDateTime operationStart) { this.operationStart = operationStart; }

    public LocalDateTime getOperationEnd() { return operationEnd; }
    public void setOperationEnd(LocalDateTime operationEnd) { this.operationEnd = operationEnd; }

    public Long getRootQueryMs() { return rootQueryMs; }
    public void setRootQueryMs(Long rootQueryMs) { this.rootQueryMs = rootQueryMs; }

    public Integer getRootRowCount() { return rootRowCount; }
    public void setRootRowCount(Integer rootRowCount) { this.rootRowCount = rootRowCount; }

    public Integer getRootMemoryMo() { return rootMemoryMo; }
    public void setRootMemoryMo(Integer rootMemoryMo) { this.rootMemoryMo = rootMemoryMo; }

    public int getSqlJoinCount() { return sqlJoinCount; }
    public void setSqlJoinCount(int sqlJoinCount) { this.sqlJoinCount = sqlJoinCount; }

    public Long getGlobalMs() { return globalMs; }
    public void setGlobalMs(Long globalMs) { this.globalMs = globalMs; }

    public Integer getGlobalRowCount() { return globalRowCount; }
    public void setGlobalRowCount(Integer globalRowCount) { this.globalRowCount = globalRowCount; }

    public boolean isMultithreaded() { return multithreaded; }
    public void setMultithreaded(boolean multithreaded) { this.multithreaded = multithreaded; }

    public Integer getElementCount() { return elementCount; }
    public void setElementCount(Integer elementCount) { this.elementCount = elementCount; }

    public Integer getPartitionSize() { return partitionSize; }
    public void setPartitionSize(Integer partitionSize) { this.partitionSize = partitionSize; }

    public Integer getThreadPoolSize() { return threadPoolSize; }
    public void setThreadPoolSize(Integer threadPoolSize) { this.threadPoolSize = threadPoolSize; }

    public Integer getBatchCount() { return batchCount; }
    public void setBatchCount(Integer batchCount) { this.batchCount = batchCount; }

    public int getLoadChildSampleCount() { return loadChildSampleCount; }
    public void setLoadChildSampleCount(int loadChildSampleCount) { this.loadChildSampleCount = loadChildSampleCount; }

    public Long getLoadChildMinMs() { return loadChildMinMs; }
    public void setLoadChildMinMs(Long loadChildMinMs) { this.loadChildMinMs = loadChildMinMs; }

    public Long getLoadChildMaxMs() { return loadChildMaxMs; }
    public void setLoadChildMaxMs(Long loadChildMaxMs) { this.loadChildMaxMs = loadChildMaxMs; }

    public Long getLoadChildAvgMs() { return loadChildAvgMs; }
    public void setLoadChildAvgMs(Long loadChildAvgMs) { this.loadChildAvgMs = loadChildAvgMs; }

    public int getLoadB2SampleCount() { return loadB2SampleCount; }
    public void setLoadB2SampleCount(int loadB2SampleCount) { this.loadB2SampleCount = loadB2SampleCount; }

    public Long getLoadB2AvgMs() { return loadB2AvgMs; }
    public void setLoadB2AvgMs(Long loadB2AvgMs) { this.loadB2AvgMs = loadB2AvgMs; }

    public int getSearchAttrSampleCount() { return searchAttrSampleCount; }
    public void setSearchAttrSampleCount(int searchAttrSampleCount) { this.searchAttrSampleCount = searchAttrSampleCount; }

    public Long getSearchAttrAvgMs() { return searchAttrAvgMs; }
    public void setSearchAttrAvgMs(Long searchAttrAvgMs) { this.searchAttrAvgMs = searchAttrAvgMs; }

    public int getPartitionalEndCount() { return partitionalEndCount; }
    public void setPartitionalEndCount(int partitionalEndCount) { this.partitionalEndCount = partitionalEndCount; }

    public Integer getMaxThreadProgressTotal() { return maxThreadProgressTotal; }
    public void setMaxThreadProgressTotal(Integer maxThreadProgressTotal) { this.maxThreadProgressTotal = maxThreadProgressTotal; }

    public Integer getMemoryPeakMo() { return memoryPeakMo; }
    public void setMemoryPeakMo(Integer memoryPeakMo) { this.memoryPeakMo = memoryPeakMo; }

    public Long getRenderingMs() { return renderingMs; }
    public void setRenderingMs(Long renderingMs) { this.renderingMs = renderingMs; }

    public Long getAggregateMs() { return aggregateMs; }
    public void setAggregateMs(Long aggregateMs) { this.aggregateMs = aggregateMs; }

    public Long getRulesEngineMs() { return rulesEngineMs; }
    public void setRulesEngineMs(Long rulesEngineMs) { this.rulesEngineMs = rulesEngineMs; }

    public Long getPostSearchRulesMs() { return postSearchRulesMs; }
    public void setPostSearchRulesMs(Long postSearchRulesMs) { this.postSearchRulesMs = postSearchRulesMs; }

    public boolean isFastDownstream() { return fastDownstream; }
    public void setFastDownstream(boolean fastDownstream) { this.fastDownstream = fastDownstream; }

    public String getBusinessActionLabel() { return businessActionLabel; }
    public void setBusinessActionLabel(String businessActionLabel) { this.businessActionLabel = businessActionLabel; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getPreRulesFilterCode() { return preRulesFilterCode; }
    public void setPreRulesFilterCode(String preRulesFilterCode) { this.preRulesFilterCode = preRulesFilterCode; }

    public Long getPreRulesSearchMs() { return preRulesSearchMs; }
    public void setPreRulesSearchMs(Long preRulesSearchMs) { this.preRulesSearchMs = preRulesSearchMs; }

    public Integer getPreRulesRowCount() { return preRulesRowCount; }
    public void setPreRulesRowCount(Integer preRulesRowCount) { this.preRulesRowCount = preRulesRowCount; }

    public int getMajInsertTotal() { return majInsertTotal; }
    public void setMajInsertTotal(int majInsertTotal) { this.majInsertTotal = majInsertTotal; }

    public String getMajInsertDominantType() { return majInsertDominantType; }
    public void setMajInsertDominantType(String majInsertDominantType) { this.majInsertDominantType = majInsertDominantType; }

    public int getMajInsertDominantCount() { return majInsertDominantCount; }
    public void setMajInsertDominantCount(int majInsertDominantCount) { this.majInsertDominantCount = majInsertDominantCount; }

    public Integer getInsertRelChildCount() { return insertRelChildCount; }
    public void setInsertRelChildCount(Integer insertRelChildCount) { this.insertRelChildCount = insertRelChildCount; }

    public Long getInsertRelChildMs() { return insertRelChildMs; }
    public void setInsertRelChildMs(Long insertRelChildMs) { this.insertRelChildMs = insertRelChildMs; }

    public Integer getInsertRelRootCount() { return insertRelRootCount; }
    public void setInsertRelRootCount(Integer insertRelRootCount) { this.insertRelRootCount = insertRelRootCount; }

    public Long getInsertRelRootMs() { return insertRelRootMs; }
    public void setInsertRelRootMs(Long insertRelRootMs) { this.insertRelRootMs = insertRelRootMs; }

    public Long getSavePersistMs() { return savePersistMs; }
    public void setSavePersistMs(Long savePersistMs) { this.savePersistMs = savePersistMs; }

    public String getSavePersistKey() { return savePersistKey; }
    public void setSavePersistKey(String savePersistKey) { this.savePersistKey = savePersistKey; }

    public List<String> getDroolsRuleNames() { return droolsRuleNames; }
    public void setDroolsRuleNames(List<String> droolsRuleNames) {
        this.droolsRuleNames = droolsRuleNames != null ? droolsRuleNames : new ArrayList<>();
    }

    public List<LatencyQueryGroupDto> getDroolsRuleHits() { return droolsRuleHits; }
    public void setDroolsRuleHits(List<LatencyQueryGroupDto> droolsRuleHits) {
        this.droolsRuleHits = droolsRuleHits != null ? droolsRuleHits : new ArrayList<>();
    }

    /** Règle Drools la plus fréquente dans la fenêtre (ou null). */
    public String dominantDroolsRule() {
        if (droolsRuleHits != null && !droolsRuleHits.isEmpty()) {
            return droolsRuleHits.get(0).getFilterCode();
        }
        if (droolsRuleNames != null && !droolsRuleNames.isEmpty()) {
            return droolsRuleNames.get(0);
        }
        // Fallback : groupe SQL répété qui porte un nom Rule_*.
        LatencyQueryGroupDto dominant = dominantRepeatedGroup();
        if (dominant != null && dominant.getFilterCode() != null
                && dominant.getFilterCode().toLowerCase().startsWith("rule_")) {
            return dominant.getFilterCode();
        }
        return null;
    }

    public String getLatencyFamily() { return latencyFamily; }
    public void setLatencyFamily(String latencyFamily) { this.latencyFamily = latencyFamily; }

    public boolean hasRulesSideEffects() {
        return majInsertTotal > 0
                || (insertRelChildCount != null && insertRelChildCount > 0)
                || (insertRelRootCount != null && insertRelRootCount > 0)
                || (preRulesRowCount != null && preRulesRowCount > 10);
    }

    public long parallelPhaseMs() {
        if (globalMs == null) {
            return 0;
        }
        long root = rootQueryMs != null ? rootQueryMs : 0;
        return Math.max(0, globalMs - root);
    }

    /**
     * Estimation du parcours utilisateur (phases séquentielles majeures).
     * Le {@code global search} inclut en général la requête racine → on ne cumule pas
     * root + global. Le {@code rendering result} vient ensuite → on l'ajoute s'il est mesuré.
     */
    public long observedPipelineMs() {
        long load = 0L;
        if (globalMs != null) {
            load = Math.max(load, globalMs);
        } else if (rootQueryMs != null) {
            load = Math.max(load, rootQueryMs);
        }
        long render = renderingMs != null ? renderingMs : 0L;
        long heavyRules = (rulesEngineMs != null && rulesEngineMs >= 5_000L) ? rulesEngineMs : 0L;
        return load + render + heavyRules;
    }

    /** Second goulot mesuré après un chargement dominant (souvent rendering result). */
    public boolean hasSignificantSecondaryRender() {
        if (renderingMs == null || renderingMs < 5_000L) {
            return false;
        }
        long load = globalMs != null ? globalMs : (rootQueryMs != null ? rootQueryMs : 0L);
        if (load <= 0) {
            return renderingMs >= 5_000L;
        }
        return renderingMs * 5 >= load || renderingMs >= 10_000L;
    }
}
