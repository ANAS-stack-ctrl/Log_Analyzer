package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.AssistantChatRequestDto;
import com.caciopee.loganalyzer.dto.AssistantChatResponseDto;

public interface AssistantChatService {
    AssistantChatResponseDto chat(AssistantChatRequestDto request);
}
