package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.GraphLogSearchRequestDto;
import com.caciopee.loganalyzer.dto.GraphLogSearchResponseDto;

public interface GraphLogSearchService {
    GraphLogSearchResponseDto search(GraphLogSearchRequestDto request);
}
