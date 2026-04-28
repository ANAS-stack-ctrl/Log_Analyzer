package com.caciopee.loganalyzer.analysis.v2;

import com.caciopee.loganalyzer.analysis.v2.dto.WorkflowV2ResponseDto;

public interface WorkflowAnalyzerV2Service {
    WorkflowV2ResponseDto analyzeImportV2(Long importId);
}