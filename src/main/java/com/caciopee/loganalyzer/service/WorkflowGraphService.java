package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.GroupAnalysisRequestDto;
import com.caciopee.loganalyzer.dto.WorkflowGraphResponseDto;

public interface WorkflowGraphService {
    WorkflowGraphResponseDto buildGraph(GroupAnalysisRequestDto request);
}