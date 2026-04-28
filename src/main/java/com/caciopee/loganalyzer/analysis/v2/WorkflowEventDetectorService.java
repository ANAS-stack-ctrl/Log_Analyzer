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

        event.setWorkflowEventType(detectType(logEntry, event));
        return event;
    }

    public WorkflowEventType detectType(LogEntry logEntry, WorkflowEventV2 event) {
        String msg = safeLower(logEntry.getMessage());
        String parserType = safeUpper(logEntry.getEventType());
        String level = safeUpper(logEntry.getLevel());

        // ===== ERROR / WARNING LEVEL FIRST =====
        if ("ERROR".equals(level)) return WorkflowEventType.ERROR;
        if ("WARN".equals(level) || "WARNING".equals(level)) return WorkflowEventType.WARNING;

        // ===== CALL / SOURCE =====
        if (msg.contains("call runrules ws from")) {
            return WorkflowEventType.CALL_RUN_RULES;
        }

        if (msg.contains("call executefilter ws from")) {
            return WorkflowEventType.CALL_EXECUTE_FILTER;
        }

        if (msg.contains("call [save] ws from")) {
            return WorkflowEventType.CALL_SAVE;
        }

        // ===== AUTOMATIC PROCESSING =====
        if (msg.contains("[automaticprocessing]") && msg.contains("[automatic launch]")) {
            return WorkflowEventType.AUTOMATIC_PROCESSING;
        }

        // ===== RULES / ACTION PHASES =====
        if (msg.contains("start fire rules")) {
            if (msg.contains("before_load")) return WorkflowEventType.BEFORE_LOAD_START;
            if (msg.contains("actionname [load]") || msg.contains("actionname [load]")) return WorkflowEventType.LOAD_START;
            if (msg.contains("actionname [save]")) return WorkflowEventType.SAVE_START;
            return WorkflowEventType.RULE_START;
        }

        if (msg.contains("end fire rules")) {
            if (msg.contains("before_load")) return WorkflowEventType.BEFORE_LOAD_END;
            if (msg.contains("actionname [load]")) return WorkflowEventType.LOAD_END;
            if (msg.contains("actionname [save]")) return WorkflowEventType.SAVE_END;
            return WorkflowEventType.RULE_END;
        }

        if (msg.contains("no rule found for this params")) {
            return WorkflowEventType.RULE_NOT_FOUND;
        }

        if (msg.contains("aucune règle n'a été affectée")
                || msg.contains("aucune r")
                && msg.contains("transition")) {
            return WorkflowEventType.RULE_WARNING;
        }

        // ===== SERVICE / FILTER =====
        if (msg.contains("start ws_") || msg.contains(" start ws")) {
            return WorkflowEventType.SERVICE_START;
        }

        if (msg.contains("end ws_") || msg.contains(" end ws")) {
            return WorkflowEventType.SERVICE_END;
        }

        if (msg.contains("start filtre")) {
            return WorkflowEventType.FILTER_START;
        }

        if (msg.contains("end filtre")) {
            return WorkflowEventType.FILTER_END;
        }

        // ===== REQUEST / SEARCH =====
        if (msg.contains("begin executing request")) {
            return WorkflowEventType.REQUEST_START;
        }

        if (msg.contains("executed request finished")) {
            return WorkflowEventType.REQUEST_END;
        }

        if (msg.contains("searchcomposantbyroot start execute")) {
            return WorkflowEventType.SEARCH_BY_ROOT_START;
        }

        if (msg.contains("searchcomposantbyroot end execute")) {
            if (isZeroResult(msg, event)) {
                return WorkflowEventType.ZERO_RESULT;
            }
            return WorkflowEventType.SEARCH_BY_ROOT_END;
        }

        if (msg.contains("start dosearch")) {
            return WorkflowEventType.SEARCH_START;
        }

        if (msg.contains("end dosearch.finally")) {
            return WorkflowEventType.SEARCH_END;
        }

        // ===== SQL / RESULTS =====
        if (msg.contains(" query:") || msg.contains("query: select")) {
            return WorkflowEventType.SQL_QUERY;
        }

        if (isZeroResult(msg, event)) {
            return WorkflowEventType.ZERO_RESULT;
        }

        if (msg.contains("row fetched")) {
            return WorkflowEventType.SQL_RESULT;
        }

        // ===== TEMP QUERY / LOAD =====
        if (msg.contains("using tempquery")) {
            return WorkflowEventType.TEMP_QUERY;
        }

        if (msg.contains("savetempcomposantsloaded")) {
            return WorkflowEventType.TEMP_SAVE;
        }

        if (msg.contains("searchattributeslist") && msg.contains("start loading data")) {
            return WorkflowEventType.LOAD_START;
        }

        if (msg.contains("searchattributeslist") && msg.contains("[query]")) {
            return WorkflowEventType.LOAD_QUERY;
        }

        if (msg.contains("searchtostringmapping") && msg.contains("[query]")) {
            return WorkflowEventType.LOAD_QUERY;
        }

        if (msg.contains("loadlistchilds") && msg.contains("query b1")) {
            return WorkflowEventType.LOAD_QUERY;
        }

        if (msg.contains("loadlistchilds") && msg.contains("inserttemp")) {
            return WorkflowEventType.TEMP_SAVE;
        }

        if (msg.contains("loadlistchilds") && msg.contains("query b2")) {
            return WorkflowEventType.LOAD_QUERY;
        }

        if (msg.contains("loadlistchilds") && msg.contains("start filling data")) {
            return WorkflowEventType.LOAD_FILLING;
        }

        if (msg.contains("loadlistchilds [filling data]")) {
            return WorkflowEventType.LOAD_FILLING;
        }

        if (msg.contains("loadlistchilds [total]")) {
            return WorkflowEventType.LOAD_END;
        }

        // ===== SAVE =====
        if (msg.contains("start saveorupdate ws")) {
            return WorkflowEventType.SAVE_OR_UPDATE_START;
        }

        if (msg.contains("end saveorupdate ws")) {
            return WorkflowEventType.SAVE_OR_UPDATE_END;
        }

        if (msg.contains("saveprocesscontent")) {
            return WorkflowEventType.SAVE_PROCESS_CONTENT;
        }

        if (msg.contains("start saveorupdate")) {
            return WorkflowEventType.SAVE_START;
        }

        if (msg.contains("end saveorupdate")) {
            return WorkflowEventType.SAVE_END;
        }

        if (msg.contains("saveinstanceoperation")) {
            return WorkflowEventType.SAVE_INSTANCE_OPERATION;
        }

        if (msg.contains("savereloperationscomposants")) {
            return WorkflowEventType.SAVE_RELATION_OPERATION;
        }

        if (msg.contains("insertarrayrel works_rel_composant_child")
                || msg.contains("insertarrayrel works_rel_composant_root")) {
            return WorkflowEventType.RELATION_SAVE;
        }

        if (msg.contains("deleteobjectrels") || msg.contains("removeobjectstodelete")) {
            return WorkflowEventType.RELATION_DELETE;
        }

        if (msg.contains("total time save")) {
            return WorkflowEventType.SAVE_TOTAL_TIME;
        }

        // ===== THREADING =====
        if (msg.contains("using multithreading")) {
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

        // ===== OBSERVABILITY / PERFORMANCE =====
        if (msg.contains("memory usage")) {
            return WorkflowEventType.MEMORY_USAGE;
        }

        if (event.getDurationMs() != null && event.getDurationMs() >= 2000) {
            return WorkflowEventType.SLOW_STEP;
        }

        if (msg.contains("took [") || event.getDurationMs() != null) {
            return WorkflowEventType.PERFORMANCE;
        }

        if (msg.contains("trcheckpoint")) {
            return WorkflowEventType.CHECKPOINT;
        }

        // ===== PARSER EVENT TYPE FALLBACK =====
        if ("CALL_RUN_RULES_WS".equals(parserType)) return WorkflowEventType.CALL_RUN_RULES;
        if ("CALL_EXECUTE_FILTER_WS".equals(parserType)) return WorkflowEventType.CALL_EXECUTE_FILTER;
        if ("CALL_SAVE_WS".equals(parserType)) return WorkflowEventType.CALL_SAVE;

        if ("AUTOMATIC_PROCESSING_START".equals(parserType)) return WorkflowEventType.AUTOMATIC_PROCESSING;

        if ("BEFORE_LOAD_START".equals(parserType)) return WorkflowEventType.BEFORE_LOAD_START;
        if ("BEFORE_LOAD_END".equals(parserType)) return WorkflowEventType.BEFORE_LOAD_END;
        if ("LOAD_START".equals(parserType)) return WorkflowEventType.LOAD_START;
        if ("LOAD_END".equals(parserType)) return WorkflowEventType.LOAD_END;

        if ("RULES_RUNNING".equals(parserType)) return WorkflowEventType.RULE_START;
        if ("RULES_FINISHED".equals(parserType)) return WorkflowEventType.RULE_END;
        if ("RULE_WARNING".equals(parserType)) return WorkflowEventType.RULE_WARNING;
        if ("RULE_NOT_FOUND".equals(parserType)) return WorkflowEventType.RULE_NOT_FOUND;

        if ("REQUEST_START".equals(parserType)) return WorkflowEventType.REQUEST_START;
        if ("REQUEST_END".equals(parserType)) return WorkflowEventType.REQUEST_END;
        if ("SEARCH_BY_ROOT_START".equals(parserType)) return WorkflowEventType.SEARCH_BY_ROOT_START;
        if ("SEARCH_BY_ROOT_END".equals(parserType)) return WorkflowEventType.SEARCH_BY_ROOT_END;
        if ("DO_SEARCH_START".equals(parserType)) return WorkflowEventType.SEARCH_START;
        if ("DO_SEARCH_END".equals(parserType)) return WorkflowEventType.SEARCH_END;

        if ("SQL_QUERY".equals(parserType)) return WorkflowEventType.SQL_QUERY;
        if ("SQL_QUERY_FINISHED".equals(parserType)) return WorkflowEventType.SQL_EXECUTION_END;
        if ("SEARCH_RESULT_FETCHED".equals(parserType)) return WorkflowEventType.SQL_RESULT;
        if ("ZERO_ROW_FETCHED".equals(parserType)) return WorkflowEventType.ZERO_RESULT;

        if ("TEMP_QUERY_USED".equals(parserType)) return WorkflowEventType.TEMP_QUERY;
        if ("TEMP_SAVE_DONE".equals(parserType)) return WorkflowEventType.TEMP_SAVE;

        if ("SEARCH_ATTRIBUTES_START".equals(parserType)) return WorkflowEventType.LOAD_START;
        if ("SEARCH_ATTRIBUTES_QUERY_DONE".equals(parserType)) return WorkflowEventType.LOAD_QUERY;
        if ("SEARCH_TO_STRING_MAPPING".equals(parserType)) return WorkflowEventType.LOAD_QUERY;

        if ("LOAD_LIST_CHILDS_START".equals(parserType)) return WorkflowEventType.LOAD_FILLING;
        if ("LOAD_LIST_CHILDS_QUERY_B1".equals(parserType)) return WorkflowEventType.LOAD_QUERY;
        if ("LOAD_LIST_CHILDS_QUERY_B2".equals(parserType)) return WorkflowEventType.LOAD_QUERY;
        if ("LOAD_LIST_CHILDS_INSERT_TEMP".equals(parserType)) return WorkflowEventType.TEMP_SAVE;
        if ("LOAD_LIST_CHILDS_FILLING".equals(parserType)) return WorkflowEventType.LOAD_FILLING;
        if ("LOAD_LIST_CHILDS_TOTAL".equals(parserType)) return WorkflowEventType.LOAD_END;

        if ("PROCESS_CONTENT_SAVE".equals(parserType)) return WorkflowEventType.SAVE_PROCESS_CONTENT;
        if ("SAVE_START".equals(parserType)) return WorkflowEventType.SAVE_START;
        if ("SAVE_END".equals(parserType)) return WorkflowEventType.SAVE_END;
        if ("SAVE_OR_UPDATE_START".equals(parserType)) return WorkflowEventType.SAVE_OR_UPDATE_START;
        if ("SAVE_OR_UPDATE_END".equals(parserType)) return WorkflowEventType.SAVE_OR_UPDATE_END;
        if ("SAVE_INSTANCE_OPERATION".equals(parserType)) return WorkflowEventType.SAVE_INSTANCE_OPERATION;
        if ("REL_OPERATION_SAVED".equals(parserType)) return WorkflowEventType.SAVE_RELATION_OPERATION;
        if ("RELATION_CHILD_ARRAY_SAVED".equals(parserType)) return WorkflowEventType.RELATION_SAVE;
        if ("RELATION_ROOT_ARRAY_SAVED".equals(parserType)) return WorkflowEventType.RELATION_SAVE;
        if ("RELATION_DELETE_QUERY".equals(parserType)) return WorkflowEventType.RELATION_DELETE;
        if ("REMOVE_OBJECTS_TO_DELETE".equals(parserType)) return WorkflowEventType.RELATION_DELETE;
        if ("SAVE_TOTAL_TIME".equals(parserType)) return WorkflowEventType.SAVE_TOTAL_TIME;

        if ("THREADING_ENABLED".equals(parserType)) return WorkflowEventType.MULTITHREADING_ENABLED;
        if ("THREADING_SKIPPED".equals(parserType)) return WorkflowEventType.MULTITHREADING_SKIPPED;
        if ("THREAD_COMPLETED".equals(parserType)) return WorkflowEventType.THREAD_COMPLETED;
        if ("SESSION_CLOSED".equals(parserType)) return WorkflowEventType.SESSION_CLOSED;

        if ("SLOW_QUERY".equals(parserType)) return WorkflowEventType.SLOW_STEP;
        if ("SLOW_WORKFLOW_STEP".equals(parserType)) return WorkflowEventType.SLOW_STEP;
        if ("PERFORMANCE_MEASURE".equals(parserType)) return WorkflowEventType.PERFORMANCE;
        if ("MEMORY_USAGE".equals(parserType)) return WorkflowEventType.MEMORY_USAGE;
        if ("CHECKPOINT_TRACE".equals(parserType)) return WorkflowEventType.CHECKPOINT;

        if ("INFO".equals(level)) return WorkflowEventType.BUSINESS_INFO;
        if ("DEBUG".equals(level)) return WorkflowEventType.TECHNICAL_INFO;

        return WorkflowEventType.UNKNOWN;
    }

    private boolean isZeroResult(String msg, WorkflowEventV2 event) {
        return msg.contains("0 row fetched")
                || msg.contains("[0] row fetched")
                || msg.contains("|0 row")
                || (event.getRowCount() != null && event.getRowCount() == 0);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}