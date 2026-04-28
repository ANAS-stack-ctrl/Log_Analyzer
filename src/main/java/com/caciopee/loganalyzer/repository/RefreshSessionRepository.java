package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.RefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {

    Optional<RefreshSession> findByTokenHashAndRevokedFalse(String tokenHash);

    List<RefreshSession> findByUsernameAndRevokedFalse(String username);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}