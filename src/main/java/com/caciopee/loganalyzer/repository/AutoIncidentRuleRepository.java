package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.AutoIncidentRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutoIncidentRuleRepository extends JpaRepository<AutoIncidentRule, Long> {

    List<AutoIncidentRule> findByActiveTrueOrderByIdAsc();
}