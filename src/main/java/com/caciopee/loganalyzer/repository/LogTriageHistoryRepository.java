package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.LogTriageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LogTriageHistoryRepository extends JpaRepository<LogTriageHistory, Long> {

    List<LogTriageHistory> findByLogIdOrderByActionAtDesc(Long logId);
}