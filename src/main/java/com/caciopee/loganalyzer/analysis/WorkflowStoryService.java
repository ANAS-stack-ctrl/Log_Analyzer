package com.caciopee.loganalyzer.analysis;

import com.caciopee.loganalyzer.analysis.dto.WorkflowStoryResponseDto;

public interface WorkflowStoryService {
    WorkflowStoryResponseDto buildImportStory(Long importId);
}