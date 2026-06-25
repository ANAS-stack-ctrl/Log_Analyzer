package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.SessionComparisonRequestDto;
import com.caciopee.loganalyzer.dto.SessionComparisonResponseDto;

public interface SessionComparisonService {
    SessionComparisonResponseDto compare(SessionComparisonRequestDto request);
}
