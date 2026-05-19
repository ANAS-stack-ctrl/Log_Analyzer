package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.GroupAnalysisRequestDto;
import com.caciopee.loganalyzer.dto.GroupAnalysisResponseDto;

public interface LogGroupAnalysisService {
    GroupAnalysisResponseDto analyzeGroup(GroupAnalysisRequestDto request);
}