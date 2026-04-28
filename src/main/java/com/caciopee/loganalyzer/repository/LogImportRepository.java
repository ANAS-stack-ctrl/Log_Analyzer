package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.entity.LogImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LogImportRepository extends JpaRepository<LogImport, Long> {

    Optional<LogImport> findByFileHash(String fileHash);

    List<LogImport> findByStatusOrderByStartedAtDesc(LogImportStatus status);

    List<LogImport> findAllByOrderByStartedAtDesc();
}