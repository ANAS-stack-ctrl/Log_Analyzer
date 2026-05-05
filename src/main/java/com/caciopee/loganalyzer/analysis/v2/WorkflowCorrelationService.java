package com.caciopee.loganalyzer.analysis.v2;

import com.caciopee.loganalyzer.analysis.v2.model.WorkflowEventV2;
import com.caciopee.loganalyzer.analysis.v2.model.WorkflowGroupV2;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class WorkflowCorrelationService {

    public List<WorkflowGroupV2> groupEvents(List<WorkflowEventV2> events) {
        Map<String, WorkflowGroupV2> groups = new LinkedHashMap<>();

        for (WorkflowEventV2 event : events) {
            String key = buildGroupKey(event);

            WorkflowGroupV2 group = groups.computeIfAbsent(key, k -> {
                WorkflowGroupV2 g = new WorkflowGroupV2();
                g.setGroupKey(k);
                g.setUuid(blankToNull(event.getUuid()));
                g.setTransactionId(blankToNull(event.getTransactionId()));
                g.setFilterCode(blankToNull(event.getFilterCode()));
                g.setProcessName(blankToNull(event.getProcessName()));
                g.setClassName(blankToNull(event.getClassName()));
                return g;
            });

            fillIfMissing(group, event);
            group.getEvents().add(event);
        }

        List<WorkflowGroupV2> result = new ArrayList<>(groups.values());

        for (WorkflowGroupV2 group : result) {
            group.getEvents().sort(
                    Comparator.comparing(
                                    WorkflowEventV2::getTimestamp,
                                    Comparator.nullsLast(LocalDateTime::compareTo)
                            )
                            .thenComparing(
                                    WorkflowEventV2::getLogEntryId,
                                    Comparator.nullsLast(Long::compareTo)
                            )
            );
        }

        return result;
    }

    private String buildGroupKey(WorkflowEventV2 event) {
        if (notBlank(event.getSessionId())) {
            return "SESSION::" + event.getSessionId();
        }

        if (notBlank(event.getUuid())) {
            return "UUID::" + event.getUuid();
        }

        if (notBlank(event.getTransactionId())) {
            return "TX::" + event.getTransactionId();
        }

        if (notBlank(event.getCorrelationId())) {
            return "CORRELATION::" + event.getCorrelationId();
        }

        if (notBlank(event.getBusinessKey())) {
            return "BUSINESS_KEY::" + event.getBusinessKey();
        }

        String process = defaultValue(event.getProcessName(), "UNKNOWN_PROCESS");
        String filter = defaultValue(event.getFilterCode(), "UNKNOWN_FILTER");
        String clazz = defaultValue(event.getClassName(), "UNKNOWN_CLASS");
        String thread = defaultValue(event.getThreadName(), "UNKNOWN_THREAD");

        return process + "::" + filter + "::" + clazz + "::" + thread;
    }

    private void fillIfMissing(WorkflowGroupV2 group, WorkflowEventV2 event) {
        if (group.getUuid() == null && notBlank(event.getUuid())) {
            group.setUuid(event.getUuid());
        }
        if (group.getTransactionId() == null && notBlank(event.getTransactionId())) {
            group.setTransactionId(event.getTransactionId());
        }
        if (group.getFilterCode() == null && notBlank(event.getFilterCode())) {
            group.setFilterCode(event.getFilterCode());
        }
        if (group.getProcessName() == null && notBlank(event.getProcessName())) {
            group.setProcessName(event.getProcessName());
        }
        if (group.getClassName() == null && notBlank(event.getClassName())) {
            group.setClassName(event.getClassName());
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String blankToNull(String value) {
        return notBlank(value) ? value : null;
    }

    private String defaultValue(String value, String fallback) {
        return notBlank(value) ? value : fallback;
    }
}