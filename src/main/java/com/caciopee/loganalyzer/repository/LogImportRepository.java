package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.LogImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LogImportRepository extends JpaRepository<LogImport, Long> {

    Optional<LogImport> findFirstByFileHashAndStatus(String fileHash, String status);

    Optional<LogImport> findFirstByOriginalFileNameIgnoreCaseAndStatus(String originalFileName, String status);
}