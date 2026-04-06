package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findFirstByDedupKeyAndStatusIn(String dedupKey, java.util.List<String> statuses);
}