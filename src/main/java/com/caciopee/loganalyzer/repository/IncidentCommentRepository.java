package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.IncidentComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentCommentRepository extends JpaRepository<IncidentComment, Long> {

    List<IncidentComment> findByIncidentIdOrderByCommentAtDesc(Long incidentId);
}