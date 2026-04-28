package com.caciopee.loganalyzer.repository;

import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.entity.LogParseQuality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LogEntryRepository extends JpaRepository<LogEntry, Long>, JpaSpecificationExecutor<LogEntry> {

    List<LogEntry> findByLogImportIdOrderByLogTimestampAscIdAsc(Long importId);

    List<LogEntry> findByLogImportIdAndIsErrorTrueOrderByLogTimestampAsc(Long importId);

    List<LogEntry> findBySessionIdOrderByLogTimestampAsc(String sessionId);

    List<LogEntry> findByUserCorrelationIdOrderByLogTimestampAsc(String userCorrelationId);

    List<LogEntry> findByLevelIgnoreCaseOrderByLogTimestampAsc(String level);

    List<LogEntry> findByEventTypeOrderByLogTimestampAsc(String eventType);

    List<LogEntry> findByLogImportIdAndSessionIdOrderByLogTimestampAsc(Long importId, String sessionId);

    List<LogEntry> findByLogImportIdAndBusinessKeyOrderByLogTimestampAsc(Long importId, String businessKey);

    List<LogEntry> findByBusinessKeyOrderByLogTimestampAsc(String businessKey);

    List<LogEntry> findByUserCorrelationIdOrderByLogTimestampAscIdAsc(String userCorrelationId);

    List<LogEntry> findBySessionIdOrderByLogTimestampAscIdAsc(String sessionId);

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
}