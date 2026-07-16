package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.entity.LogParseQuality;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LogEntryRepository extends JpaRepository<LogEntry, Long>, JpaSpecificationExecutor<LogEntry> {

    List<LogEntry> findByLogImportIdOrderByLogTimestampAscIdAsc(Long importId);

    /** Échantillon paginé — ne jamais charger tout un gros import en mémoire. */
    List<LogEntry> findByLogImportIdOrderByLogTimestampAscIdAsc(Long importId, Pageable pageable);

    List<LogEntry> findByLogImportIdAndIsErrorTrueOrderByLogTimestampAsc(Long importId);

    /**
     * Candidats latence uniquement (évite de charger tout l'import en mémoire).
     * Tri durée décroissante ; utiliser avec {@link Pageable} pour plafonner.
     */
    @Query("""
            select e from LogEntry e
            where e.logImport.id = :importId
              and e.durationMs is not null
              and e.durationMs >= :threshold
            order by e.durationMs desc, e.logTimestamp desc, e.id desc
            """)
    List<LogEntry> findSlowByImportId(@Param("importId") Long importId,
                                      @Param("threshold") long threshold,
                                      Pageable pageable);

    List<LogEntry> findBySessionIdOrderByLogTimestampAsc(String sessionId);

    List<LogEntry> findByUserCorrelationIdOrderByLogTimestampAsc(String userCorrelationId);

    List<LogEntry> findByLevelIgnoreCaseOrderByLogTimestampAsc(String level);

    List<LogEntry> findByEventTypeOrderByLogTimestampAsc(String eventType);

    List<LogEntry> findByLogImportIdAndSessionIdOrderByLogTimestampAsc(Long importId, String sessionId);

    List<LogEntry> findByLogImportIdAndBusinessKeyOrderByLogTimestampAsc(Long importId, String businessKey);

    List<LogEntry> findByBusinessKeyOrderByLogTimestampAsc(String businessKey);

    List<LogEntry> findByUserCorrelationIdOrderByLogTimestampAscIdAsc(String userCorrelationId);

    List<LogEntry> findBySessionIdOrderByLogTimestampAscIdAsc(String sessionId);

    List<LogEntry> findTop10000ByOrderByIdDesc();

    long countByLogImportId(Long importId);

    long countByLogImportIdAndIsErrorTrue(Long importId);

    long countByLogImportIdAndAmbiguousMessageTrue(Long importId);

    long countByLogImportIdAndIncompleteLineTrue(Long importId);

    long countByLogImportIdAndParseQuality(Long importId, LogParseQuality parseQuality);

    @Query("""
            select e.eventType, count(e)
            from LogEntry e
            where e.logImport.id = :importId
            group by e.eventType
            order by count(e) desc
            """)
    List<Object[]> countEventTypesByImport(@Param("importId") Long importId);

    @Query("""
            select e.sourceClass, count(e)
            from LogEntry e
            where e.logImport.id = :importId
              and e.sourceClass is not null
              and trim(e.sourceClass) <> ''
            group by e.sourceClass
            order by count(e) desc
            """)
    List<Object[]> countSourceClassesByImport(@Param("importId") Long importId);

    @Query("""
            select e.businessKey, count(e)
            from LogEntry e
            where e.logImport.id = :importId
              and e.businessKey is not null
              and trim(e.businessKey) <> ''
            group by e.businessKey
            order by count(e) desc
            """)
    List<Object[]> countBusinessKeysByImport(@Param("importId") Long importId);

    @Query("""
            select e.errorAttribute, count(e)
            from LogEntry e
            where e.logImport.id = :importId
              and e.errorAttribute is not null
              and trim(e.errorAttribute) <> ''
            group by e.errorAttribute
            order by count(e) desc
            """)
    List<Object[]> countErrorAttributesByImport(@Param("importId") Long importId);

    @Query("""
            select e.level, count(e)
            from LogEntry e
            where e.logImport.id = :importId
              and e.level is not null
              and trim(e.level) <> ''
            group by e.level
            order by count(e) desc
            """)
    List<Object[]> countLevelsByImport(@Param("importId") Long importId);

    @Query("""
            select e.parseQuality, count(e)
            from LogEntry e
            where e.logImport.id = :importId
            group by e.parseQuality
            order by count(e) desc
            """)
    List<Object[]> countParseQualityByImport(@Param("importId") Long importId);

    @Query("""
            select min(e.logTimestamp), max(e.logTimestamp)
            from LogEntry e
            where e.logImport.id = :importId
            """)
    Object[] findTimeRangeByImport(@Param("importId") Long importId);

    @Query("""
            select e.sessionId, count(e)
            from LogEntry e
            where e.logImport.id = :importId
              and e.sessionId is not null
              and trim(e.sessionId) <> ''
            group by e.sessionId
            order by count(e) desc
            """)
    List<Object[]> countSessionsByImport(@Param("importId") Long importId);

    @Query("""
            select e.userCorrelationId, count(e)
            from LogEntry e
            where e.logImport.id = :importId
              and e.userCorrelationId is not null
              and trim(e.userCorrelationId) <> ''
            group by e.userCorrelationId
            order by count(e) desc
            """)
    List<Object[]> countCorrelationIdsByImport(@Param("importId") Long importId);

    @Query("""
            select e.userName, count(e)
            from LogEntry e
            where e.logImport.id = :importId
              and e.userName is not null
              and trim(e.userName) <> ''
            group by e.userName
            order by count(e) desc
            """)
    List<Object[]> countUsersByImport(@Param("importId") Long importId);

    @Query("""
            select count(distinct e.userName)
            from LogEntry e
            where e.logImport.id = :importId
              and e.userName is not null
              and trim(e.userName) <> ''
            """)
    long countDistinctUsersByImport(@Param("importId") Long importId);

    @Query("""
            select e.userName, count(e)
            from LogEntry e
            where e.logImport.id = :importId
              and e.isError = true
              and e.userName is not null
              and trim(e.userName) <> ''
            group by e.userName
            order by count(e) desc
            """)
    List<Object[]> countErrorsByUserForImport(@Param("importId") Long importId);

    @Query(value = """
            SELECT COUNT(*)
            FROM log_entries
            WHERE import_id = :importId
              AND duration_ms >= :threshold
            """, nativeQuery = true)
    long countSlowLogsByImport(@Param("importId") Long importId, @Param("threshold") long threshold);

    @Query(value = """
            SELECT COALESCE(MAX(
                CASE
                    WHEN duration_ms IS NOT NULL THEN duration_ms
                    ELSE NULL
                END
            ), 0)
            FROM log_entries
            WHERE import_id = :importId
            """, nativeQuery = true)
    Long maxDurationMsByImport(@Param("importId") Long importId);

    @Query(value = """
            SELECT COALESCE(NULLIF(TRIM(source_file_name), ''), '—') AS source_file_name,
                   COALESCE(NULLIF(TRIM(source_relative_path), ''), COALESCE(NULLIF(TRIM(source_file_name), ''), '—')) AS source_relative_path,
                   COUNT(*) AS log_count,
                   SUM(CASE WHEN is_error THEN 1 ELSE 0 END) AS error_count,
                   SUM(CASE WHEN duration_ms >= 2000 THEN 1 ELSE 0 END) AS slow_count,
                   MAX(duration_ms) AS max_duration_ms,
                   MIN(log_timestamp) AS first_ts,
                   MAX(log_timestamp) AS last_ts
            FROM log_entries
            WHERE import_id = :importId
            GROUP BY 1, 2
            ORDER BY log_count DESC
            """, nativeQuery = true)
    List<Object[]> aggregateSourceFilesByImport(@Param("importId") Long importId);

    @Query("""
            select count(e)
            from LogEntry e
            where e.logImport.id = :importId
              and (
                    upper(coalesce(e.message, '')) like '%UUID [%'
                 or upper(coalesce(e.rawLog, '')) like '%UUID [%'
              )
            """)
    long countUuidTaggedLinesByImport(@Param("importId") Long importId);

    @Query("""
            select e
            from LogEntry e
            where e.logImport.id = :importId
              and (
                    upper(coalesce(e.message, '')) like upper(concat('%', :keyword, '%'))
                 or upper(coalesce(e.rawLog, '')) like upper(concat('%', :keyword, '%'))
                 or upper(coalesce(e.businessMeaning, '')) like upper(concat('%', :keyword, '%'))
              )
            order by e.logTimestamp asc, e.id asc
            """)
    List<LogEntry> searchByKeywordInImport(@Param("importId") Long importId, @Param("keyword") String keyword);

    @Query("""
            select distinct e.sessionId
            from LogEntry e
            where e.logImport.id = :importId
              and e.sessionId is not null
              and trim(e.sessionId) <> ''
              and (
                    upper(coalesce(e.message, '')) like upper(concat('%', :keyword, '%'))
                 or upper(coalesce(e.rawLog, '')) like upper(concat('%', :keyword, '%'))
                 or upper(coalesce(e.businessMeaning, '')) like upper(concat('%', :keyword, '%'))
              )
            """)
    List<String> findDistinctSessionIdsByImportAndKeyword(@Param("importId") Long importId,
                                                          @Param("keyword") String keyword);

    @Query("""
            select distinct e.businessKey
            from LogEntry e
            where e.logImport.id = :importId
              and e.businessKey is not null
              and trim(e.businessKey) <> ''
              and (
                    upper(coalesce(e.message, '')) like upper(concat('%', :keyword, '%'))
                 or upper(coalesce(e.rawLog, '')) like upper(concat('%', :keyword, '%'))
                 or upper(coalesce(e.businessMeaning, '')) like upper(concat('%', :keyword, '%'))
              )
            """)
    List<String> findDistinctBusinessKeysByImportAndKeyword(@Param("importId") Long importId,
                                                            @Param("keyword") String keyword);

    @Query("""
            select count(e)
            from LogEntry e
            where upper(coalesce(e.message, '')) like '%GENERICJDBCEXCEPTION%'
               or upper(coalesce(e.rawLog, '')) like '%GENERICJDBCEXCEPTION%'
               or upper(coalesce(e.message, '')) like '%COULD NOT EXECUTE QUERY%'
               or upper(coalesce(e.rawLog, '')) like '%COULD NOT EXECUTE QUERY%'
            """)
    long countQueryExecutionFailuresGlobally();

    @Query("""
            select count(e)
            from LogEntry e
            where upper(coalesce(e.message, '')) like '%NO RULE FOUND FOR THIS PARAMS%'
               or upper(coalesce(e.rawLog, '')) like '%NO RULE FOUND FOR THIS PARAMS%'
            """)
    long countRuleNotFoundGlobally();


    @Query("""
        select e
        from LogEntry e
        where e.id between :startId and :endId
        order by e.id asc
        """)
    List<LogEntry> findByIdBetweenOrderByIdAsc(@Param("startId") Long startId,
                                               @Param("endId") Long endId);

    @Modifying
    @Query("""
            delete from LogEntry e
            where e.logImport.id = :importId
            """)
    int deleteByImportId(@Param("importId") Long importId);

    /**
     * Récupère la fenêtre de logs ayant potentiellement causé une latence, dans le
     * scope de corrélation approprié (uuid &gt; txid &gt; filterCode &gt; sessionId).
     * Pour la performance sur gros volumes, prévoir les index composites décrits dans
     * le guide d'intégration (uuid_ts / filter_ts / session_ts).
     */
    @Query("""
            SELECT le FROM LogEntry le
            WHERE le.logImport.id = :importId
              AND le.logTimestamp >= :windowStart
              AND le.logTimestamp <= :anchorTs
              AND le.id <> :anchorId
              AND (
                    (:uuid IS NOT NULL AND le.userCorrelationId = :uuid)
                 OR (:uuid IS NULL AND :txid IS NOT NULL
                     AND COALESCE(le.userCorrelationId, '') = :txid)
                 OR (:uuid IS NULL AND :txid IS NULL AND :filterCode IS NOT NULL
                     AND COALESCE(le.sourceClass, '') LIKE CONCAT('%', :filterCode, '%'))
                 OR (:uuid IS NULL AND :txid IS NULL AND :filterCode IS NULL
                     AND COALESCE(le.sessionId, '') = :sessionId)
              )
            ORDER BY le.logTimestamp ASC
            """)
    List<LogEntry> findCauseWindow(
            @Param("importId") Long importId,
            @Param("anchorId") Long anchorId,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("anchorTs") LocalDateTime anchorTs,
            @Param("uuid") String uuid,
            @Param("txid") String txid,
            @Param("filterCode") String filterCode,
            @Param("sessionId") String sessionId
    );
}