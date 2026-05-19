package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.LogGroupDto;

import java.time.LocalDateTime;
import java.util.List;

public interface LogGroupingService {

    List<LogGroupDto> groupLogs(
            List<Long> importIds,
            List<String> fileNames,
            Boolean errorOnly,
            String eventType,
            String processName,
            String userName,
            String sessionId,
            String uuid,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            String groupBy,
            Integer limit
    );
}