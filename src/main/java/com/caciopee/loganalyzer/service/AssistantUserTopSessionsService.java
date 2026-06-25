package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.AssistantUserTopSessionsRequestDto;
import com.caciopee.loganalyzer.dto.AssistantUserTopSessionsResponseDto;

public interface AssistantUserTopSessionsService {
    AssistantUserTopSessionsResponseDto findTopSessions(AssistantUserTopSessionsRequestDto request);
}

