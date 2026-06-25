package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.AssistantFullAnalysisResponseDto;
import com.caciopee.loganalyzer.dto.GroupAnalysisRequestDto;

public interface AssistantFullAnalysisService {
    AssistantFullAnalysisResponseDto fullAnalysis(GroupAnalysisRequestDto request);
}

