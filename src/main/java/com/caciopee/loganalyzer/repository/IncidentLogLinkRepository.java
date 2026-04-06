package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.IncidentLogLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentLogLinkRepository extends JpaRepository<IncidentLogLink, Long> {

    List<IncidentLogLink> findByIncidentId(Long incidentId);

    boolean existsByIncidentIdAndLogId(Long incidentId, Long logId);
}