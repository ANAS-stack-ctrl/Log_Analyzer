package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.LogRawMessage;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LogRawMessageRepository extends JpaRepository<LogRawMessage, Long> {
    @Modifying
    @Query("""
            delete from LogRawMessage m
            where m.logEntry.logImport.id = :importId
            """)
    int deleteByImportId(@Param("importId") Long importId);
}