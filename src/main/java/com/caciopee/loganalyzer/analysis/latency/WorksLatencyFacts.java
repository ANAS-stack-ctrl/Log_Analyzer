package com.caciopee.loganalyzer.analysis.latency;

import java.time.LocalDateTime;

/**
 * Faits mesurés extraits des logs WORKS — base factuelle pour le récit client (sans LLM).
 */
public class WorksLatencyFacts {

    private String userName;
    private String processName;
    private String filterCode;
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

    private boolean fastDownstream;

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public String getFilterCode() { return filterCode; }
    public void setFilterCode(String filterCode) { this.filterCode = filterCode; }

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

    public Long getRulesEngineMs() { return rulesEngineMs; }
    public void setRulesEngineMs(Long rulesEngineMs) { this.rulesEngineMs = rulesEngineMs; }

    public Long getPostSearchRulesMs() { return postSearchRulesMs; }
    public void setPostSearchRulesMs(Long postSearchRulesMs) { this.postSearchRulesMs = postSearchRulesMs; }

    public boolean isFastDownstream() { return fastDownstream; }
    public void setFastDownstream(boolean fastDownstream) { this.fastDownstream = fastDownstream; }

    public long parallelPhaseMs() {
        if (globalMs == null) {
            return 0;
        }
        long root = rootQueryMs != null ? rootQueryMs : 0;
        return Math.max(0, globalMs - root);
    }
}
