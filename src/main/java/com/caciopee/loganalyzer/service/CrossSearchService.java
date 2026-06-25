package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.CrossSearchRequestDto;
import com.caciopee.loganalyzer.dto.CrossSearchResponseDto;

public interface CrossSearchService {
    CrossSearchResponseDto search(CrossSearchRequestDto request);
}
