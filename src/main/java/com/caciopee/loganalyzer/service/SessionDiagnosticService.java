package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.GroupAnalysisRequestDto;
import com.caciopee.loganalyzer.dto.SessionDiagnosticResponseDto;

public interface SessionDiagnosticService {
    SessionDiagnosticResponseDto buildDiagnostic(GroupAnalysisRequestDto request);
}
