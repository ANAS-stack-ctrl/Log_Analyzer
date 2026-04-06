package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.RefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {

    Optional<RefreshSession> findByTokenHashAndRevokedFalse(String tokenHash);
}