package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.ImportComparisonRequestDto;
import com.caciopee.loganalyzer.dto.ImportComparisonResponseDto;

public interface ImportComparisonService {
    ImportComparisonResponseDto compare(ImportComparisonRequestDto request);
}
