package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.LogRawMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogRawMessageRepository extends JpaRepository<LogRawMessage, Long> {
}