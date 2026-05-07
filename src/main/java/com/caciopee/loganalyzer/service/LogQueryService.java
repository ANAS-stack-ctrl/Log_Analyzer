package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.LogEntryViewDto;

import java.util.List;

public interface LogQueryService {

    List<LogEntryViewDto> searchLogs(Long importId,
                                     String fileName,
                                     Boolean errorOnly,
                                     String eventType,
                                     String processName,
                                     String sessionId,
                                     String uuid,
                                     Integer limit);

    List<LogEntryViewDto> searchLogsMulti(List<Long> importIds,
                                          List<String> fileNames,
                                          Boolean errorOnly,
                                          String eventType,
                                          String processName,
                                          String sessionId,
                                          String uuid,
                                          Integer limit);
}