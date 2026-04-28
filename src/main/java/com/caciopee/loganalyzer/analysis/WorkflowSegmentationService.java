package com.caciopee.loganalyzer.analysis;

import com.caciopee.loganalyzer.analysis.model.WorkflowSegment;
import com.caciopee.loganalyzer.entity.LogEntry;

import java.util.List;

public interface WorkflowSegmentationService {

    List<WorkflowSegment> splitImportIntoSegments(List<LogEntry> logs);
}