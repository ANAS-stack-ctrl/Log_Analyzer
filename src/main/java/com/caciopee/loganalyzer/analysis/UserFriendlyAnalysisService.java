package com.caciopee.loganalyzer.analysis;

import com.caciopee.loganalyzer.analysis.dto.UserFriendlyAnalysisDto;

public interface UserFriendlyAnalysisService {
    UserFriendlyAnalysisDto explainImportForHuman(Long importId);
}