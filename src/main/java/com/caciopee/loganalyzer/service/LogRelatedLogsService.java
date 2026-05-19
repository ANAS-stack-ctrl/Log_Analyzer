package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.RelatedContextRequestDto;
import com.caciopee.loganalyzer.dto.RelatedLogsRequestDto;
import com.caciopee.loganalyzer.dto.RelatedLogsResponseDto;

public interface LogRelatedLogsService {
    RelatedLogsResponseDto getRelatedLogs(RelatedLogsRequestDto request);

    RelatedLogsResponseDto getContextAround(RelatedContextRequestDto request);
}