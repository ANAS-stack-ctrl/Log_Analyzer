package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.LogTriageState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LogTriageStateRepository extends JpaRepository<LogTriageState, Long> {

    Optional<LogTriageState> findByLogId(Long logId);
}