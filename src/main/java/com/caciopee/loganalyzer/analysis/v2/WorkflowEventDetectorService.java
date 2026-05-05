package com.caciopee.loganalyzer.analysis.v2;

import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.analysis.v2.model.WorkflowEventV2;
import com.caciopee.loganalyzer.entity.LogEntry;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class WorkflowEventDetectorService {

    private final LogPatternExtractor logPatternExtractor;

    public WorkflowEventDetectorService(LogPatternExtractor logPatternExtractor) {
        this.logPatternExtractor = logPatternExtractor;
    }

    public WorkflowEventV2 toWorkflowEvent(LogEntry logEntry) {
        var extracted = logPatternExtractor.extract(logEntry);

        WorkflowEventV2 event = new WorkflowEventV2();
        event.setLogEntryId(logEntry.getId());
        event.setTimestamp(logEntry.getLogTimestamp());
        event.setLevel(logEntry.getLevel());
        event.setUserName(logEntry.getUserName());
        event.setProcessName(logEntry.getProcessName());
        event.setSourceClass(logEntry.getSourceClass());
        event.setMessage(logEntry.getMessage());
        event.setEventTypeFromParser(logEntry.getEventType());
        event.setCorrelationId(logEntry.getUserCorrelationId());
        event.setSessionId(logEntry.getSessionId());
        event.setBusinessKey(logEntry.getBusinessKey());

        event.setUuid(extracted.getExtractedUuid());
        event.setTransactionId(extracted.getExtractedTransactionId());
        event.setFilterCode(extracted.getExtractedFilterCode());
        event.setClassName(extracted.getExtractedClassName());
        event.setThreadName(extracted.getExtractedThreadName());
        event.setRowCount(extracted.getExtractedRowCount());
        event.setDurationMs(extracted.getExtractedDurationMs());
        event.setMemoryMo(extracted.getExtractedMemoryMo());

        event.setExtractedTaskName(extracted.getExtractedTaskName());
        event.setExtractedActionName(extracted.getExtractedActionName());
        event.setExtractedProcessName(extracted.getExtractedProcessName());
        event.setExtractedClientIp(extracted.getExtractedClientIp());
        event.setExtractedTriggerName(extracted.getExtractedTriggerName());
        event.setExtractedCheckpoint(extracted.getExtractedCheckpoint());
        event.setExtractedBusinessObjectKey(extracted.getExtractedBusinessObjectKey());
        event.setExtractedBusinessObjectValue(extracted.getExtractedBusinessObjectValue());
        event.setExtractedQueryParameterName(extracted.getExtractedQueryParameterName());
        event.setExtractedQueryParameterValue(extracted.getExtractedQueryParameterValue());

        event.setWorkflowEventType(detectType(logEntry));
        return event;
    }

    private WorkflowEventType detectType(LogEntry logEntry) {
        String msg = safeLower(logEntry.getMessage());
        String level = safeUpper(logEntry.getLevel());

        if ("ERROR".equals(level)) return WorkflowEventType.ERROR;

        // ===== WORKS-WORKS BUSINESS SPECIFIC =====
        if (msg.contains("ice n'existe pas") || msg.contains("ice nexiste pas")) {
            return WorkflowEventType.ICE_NOT_FOUND;
        }

        if (msg.contains("ws_insert_tracabilite_rfid")) {
            return WorkflowEventType.WS_INSERT_TRACABILITE_RFID;
        }

        if (msg.contains("ws_create_ampx")) {
            return WorkflowEventType.WS_CREATE_AMPX;
        }

        if (msg.contains("ws_save_amp")) {
            return WorkflowEventType.WS_SAVE_AMP;
        }

        if (msg.contains("ws_save_dum_douane") || msg.contains("ws_insert_dum_douane")) {
            return WorkflowEventType.DUM_DOUANE;
        }

        if (msg.contains("bad_reg") || msg.contains("ctrl_insertbad") || msg.contains("ws_insertbad")) {
            return WorkflowEventType.BAD_REG;
        }

        if (msg.contains("checkscan")) {
            return WorkflowEventType.CHECKSCAN;
        }

        if (msg.contains("ws_find_tracabilite_algesiras")
                || msg.contains("ws_findtracabilitealgesiras")
                || msg.contains("rule_ws_findtracabilitealgesiras")) {
            return WorkflowEventType.WS_FIND_TRACABILITE_ALGESIRAS;
        }

        if (msg.contains("start ws_search_reg_tracabilite")
                || msg.contains("end ws_search_reg_tracabilite")
                || msg.contains("ws_search_reg_tracabilite")) {
            return WorkflowEventType.WS_SEARCH_REG_TRACABILITE;
        }

        if (msg.contains("find_tracabilite_ws_reg")) {
            return WorkflowEventType.FIND_TRACABILITE_WS_REG;
        }

        if (msg.contains("filter code [ws_search_reg]") || msg.contains("ws_search_reg")) {
            return WorkflowEventType.WS_SEARCH_REG;
        }

        if (msg.contains("lastpoint_reg")) {
            return WorkflowEventType.LASTPOINT_REG;
        }

        if (msg.contains("scan_reg")) {
            return WorkflowEventType.SCAN_REG;
        }

        if (msg.contains("ebook_reg")) {
            return WorkflowEventType.EBOOK_REG;
        }

        if (msg.contains("ws_search_amp_f")) {
            return WorkflowEventType.WS_SEARCH_AMP_F;
        }

        if (msg.contains("ws_search_amp") || msg.contains("ws_find_amp")) {
            return WorkflowEventType.WS_SEARCH_AMP;
        }

        if (msg.contains("serviceportuaire")) {
            return WorkflowEventType.SERVICE_PORTUAIRE;
        }

        if (msg.contains("trace size")) {
            return WorkflowEventType.TRACE_SIZE;
        }

        if (msg.contains("dateaction")) {
            return WorkflowEventType.DATE_ACTION;
        }

        if (msg.contains(" amp ") || msg.contains("amp ----------------") || msg.contains("wvampegal")) {
            return WorkflowEventType.AMP_VALUE;
        }

        if (msg.contains("trcheckpoint")
                || msg.contains("entree_couloir_rfid")
                || msg.contains("entree_park_visite")
                || msg.contains("entree_scanner_export")
                || msg.contains("sortie_sas_export")
                || msg.contains("entree_terminal_export")
                || msg.contains("sortie sas import")
                || msg.contains("entree sas import")) {
            return WorkflowEventType.CHECKPOINT;
        }

        // ===== RULES =====
        if (msg.contains("no rule found for this params")
                || msg.contains("aucune règle")
                || msg.contains("aucune regle")) {
            return WorkflowEventType.RULE_NOT_FOUND;
        }

        if (msg.contains("start fire rules")) {
            return WorkflowEventType.RULE_START;
        }

        if (msg.contains("end fire rules")) {
            return WorkflowEventType.RULE_END;
        }

        if (msg.contains("running rules")) {
            return WorkflowEventType.RULE_DETAIL;
        }

        if (msg.contains("automaticprocessing") || msg.contains("automatic launch")) {
            return WorkflowEventType.AUTOMATIC_PROCESSING;
        }

        if (msg.contains("call runrules ws from")) {
            return WorkflowEventType.CALL_RUN_RULES;
        }

        // ===== SQL / SEARCH =====
        if (msg.contains("query:")) {
            return WorkflowEventType.SQL_QUERY;
        }

        if (msg.contains("executed request finished")) {
            return WorkflowEventType.REQUEST_END;
        }

        if (msg.contains("begin executing request")) {
            return WorkflowEventType.REQUEST_START;
        }

        if (msg.contains("0 row fetched") || msg.contains("[0] row fetched") || msg.contains("|0 row")) {
            return WorkflowEventType.ZERO_RESULT;
        }

        if (msg.contains("row fetched") || msg.matches(".*\\|\\s*\\d+\\s*row\\s*\\|.*")) {
            return WorkflowEventType.SQL_RESULT;
        }

        if (msg.contains("searchcomposantbyroot start execute")) {
            return WorkflowEventType.SEARCH_BY_ROOT_START;
        }

        if (msg.contains("searchcomposantbyroot end execute")) {
            return WorkflowEventType.SEARCH_BY_ROOT_END;
        }

        if (msg.contains("preparesearchbyroot")) {
            return WorkflowEventType.SEARCH_START;
        }

        if (msg.contains("start dosearch")) {
            return WorkflowEventType.SEARCH_START;
        }

        if (msg.contains("end dosearch")) {
            return WorkflowEventType.SEARCH_END;
        }

        if (msg.contains("loadlistchilds")) {
            return WorkflowEventType.LOAD_QUERY;
        }

        if (msg.contains("searchattributeslist")) {
            return WorkflowEventType.LOAD_FILLING;
        }

        if (msg.contains("searchtostringmapping")) {
            return WorkflowEventType.LOAD_FILLING;
        }

        if (msg.contains("using tempquery")) {
            return WorkflowEventType.TEMP_QUERY;
        }

        if (msg.contains("savetempcomposantsloaded")) {
            return WorkflowEventType.TEMP_SAVE;
        }

        if (msg.contains("deletetempcomposantsloaded")) {
            return WorkflowEventType.TEMP_DELETE;
        }

        // ===== PERFORMANCE =====
        if (msg.contains("memory usage")) {
            return WorkflowEventType.MEMORY_USAGE;
        }

        if (msg.contains("multithreading_default_args") || msg.contains("using multithreading")) {
            return WorkflowEventType.MULTITHREADING_ENABLED;
        }

        if (msg.contains("ignore multithreading")) {
            return WorkflowEventType.MULTITHREADING_SKIPPED;
        }

        if (msg.contains("the thread") && msg.contains("is completed")) {
            return WorkflowEventType.THREAD_COMPLETED;
        }

        if (msg.contains("session closed")) {
            return WorkflowEventType.SESSION_CLOSED;
        }

        if (msg.contains("took")) {
            return WorkflowEventType.PERFORMANCE;
        }

        // ===== SAVE =====
        if (msg.contains("saveprocesscontent")) {
            return WorkflowEventType.SAVE_PROCESS_CONTENT;
        }

        if (msg.contains("start saveorupdate")) {
            return WorkflowEventType.SAVE_OR_UPDATE_START;
        }

        if (msg.contains("end saveorupdate")) {
            return WorkflowEventType.SAVE_OR_UPDATE_END;
        }

        if (msg.contains("saveinstanceoperation")) {
            return WorkflowEventType.SAVE_INSTANCE_OPERATION;
        }

        if (msg.contains("savereloperationscomposants")) {
            return WorkflowEventType.SAVE_RELATION_OPERATION;
        }

        if (msg.contains("total time save")) {
            return WorkflowEventType.SAVE_TOTAL_TIME;
        }

        if (msg.contains("save documents")) {
            return WorkflowEventType.DOCUMENT_SAVE;
        }

        if (msg.contains("aggregate data")) {
            return WorkflowEventType.AGGREGATION_END;
        }

        if (msg.contains("rendering result")) {
            return WorkflowEventType.RENDERING_RESULT;
        }

        // ===== STRUCTURE DATA =====
        if (msg.contains("the pppm is null")) {
            return WorkflowEventType.PM_PP_NULL_ERROR;
        }

        if (msg.contains("the error details are as follows")) {
            return WorkflowEventType.ERROR_DETAILS_EXTRACTED;
        }

        if (msg.contains("start looping over interface objects")) {
            return WorkflowEventType.STRUCTURING_LOOP_START;
        }

        if (msg.contains("new interface object")) {
            return WorkflowEventType.NEW_INTERFACE_OBJECT;
        }

        if (msg.contains("structuredata") && msg.contains("start")) {
            return WorkflowEventType.STRUCTURE_DATA_START;
        }

        if (msg.contains("structuredata") && msg.contains("end")) {
            return WorkflowEventType.STRUCTURE_DATA_END;
        }

        if ("WARN".equals(level) || "WARNING".equals(level)) {
            return WorkflowEventType.WARNING;
        }

        return WorkflowEventType.UNKNOWN;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}