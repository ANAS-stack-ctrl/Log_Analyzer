package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.AlertNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertNotificationRepository extends JpaRepository<AlertNotification, Long> {

    List<AlertNotification> findTop100ByOrderByCreatedAtDesc();

    List<AlertNotification> findByStatusOrderByCreatedAtDesc(String status);

    Optional<AlertNotification> findFirstByDedupKeyAndStatusIn(String dedupKey, List<String> statuses);
}