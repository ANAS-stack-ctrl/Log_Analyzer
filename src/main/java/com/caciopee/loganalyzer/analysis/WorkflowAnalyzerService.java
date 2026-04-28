package com.caciopee.loganalyzer.analysis;

import com.caciopee.loganalyzer.analysis.dto.WorkflowAnalysisResponseDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowSummaryDto;

public interface WorkflowAnalyzerService {

    WorkflowAnalysisResponseDto analyzeImport(Long importId);

    WorkflowSummaryDto summarizeImport(Long importId);
}