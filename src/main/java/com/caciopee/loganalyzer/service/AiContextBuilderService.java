package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.AiContextRequestDto;
import com.caciopee.loganalyzer.dto.AiContextResponseDto;

public interface AiContextBuilderService {
    AiContextResponseDto buildContext(AiContextRequestDto request);
}