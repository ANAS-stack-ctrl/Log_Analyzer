package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.LogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LogEntryRepository extends JpaRepository<LogEntry, Long> {

    List<LogEntry> findByLevelIgnoreCase(String level);

    long countByLevelIgnoreCase(String level);

    List<LogEntry> findByEventTypeIgnoreCase(String eventType);

    List<LogEntry> findByBusinessKey(String businessKey);

    List<LogEntry> findByFieldNameIgnoreCase(String fieldName);

    List<LogEntry> findByCorrelationId(String correlationId);

    List<LogEntry> findByExecutionId(String executionId);

    List<LogEntry> findByErrorTrue();

    List<LogEntry> findByLogImportId(Long importId);

    List<LogEntry> findByLogImportOriginalFileNameIgnoreCase(String originalFileName);
}