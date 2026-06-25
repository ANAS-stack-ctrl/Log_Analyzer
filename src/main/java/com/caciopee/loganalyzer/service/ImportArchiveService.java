package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.config.ImportArchiveProperties;
import com.caciopee.loganalyzer.dto.ImportArchiveRunResultDto;
import com.caciopee.loganalyzer.dto.ImportArchiveStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ImportArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ImportArchiveService.class);

    private final ImportArchiveProperties properties;
    private final JdbcTemplate primaryJdbc;
    private final ObjectProvider<JdbcTemplate> archiveJdbcProvider;
    private final LogImportManagementService logImportManagementService;

    private volatile LocalDateTime lastRunAt;
    private volatile int lastArchivedCount;
    private volatile int lastPurgedCount;
    private volatile String lastRunMessage = "Aucune exécution pour le moment.";

    private volatile boolean archiveSchemaReady;

    public ImportArchiveService(ImportArchiveProperties properties,
                                JdbcTemplate primaryJdbc,
                                @Qualifier("archiveJdbcTemplate") ObjectProvider<JdbcTemplate> archiveJdbcProvider,
                                LogImportManagementService logImportManagementService) {
        this.properties = properties;
        this.primaryJdbc = primaryJdbc;
        this.archiveJdbcProvider = archiveJdbcProvider;
        this.logImportManagementService = logImportManagementService;
    }

    public ImportArchiveStatusDto getStatus() {
        ImportArchiveStatusDto dto = new ImportArchiveStatusDto();
        dto.setEnabled(properties.isEnabled());
        dto.setInactiveDays(properties.getInactiveDays());
        dto.setPurgeDays(properties.getPurgeDays());
        dto.setBatchSize(properties.getBatchSize());
        dto.setCron(properties.getCron());
        dto.setArchiveDatabaseUrl(properties.getDatasource().getUrl());
        dto.setLastRunAt(lastRunAt);
        dto.setLastArchivedCount(lastArchivedCount);
        dto.setLastPurgedCount(lastPurgedCount);
        dto.setLastRunMessage(lastRunMessage);
        return dto;
    }

    public ImportArchiveRunResultDto runMaintenance() {
        if (!properties.isEnabled()) {
            return new ImportArchiveRunResultDto(0, 0, "Archivage désactivé (app.archive.enabled=false).");
        }

        JdbcTemplate archiveJdbc = requireArchiveJdbc();
        ensureArchiveSchema(archiveJdbc);

        int archived = archiveInactiveImports(archiveJdbc);
        int purged = purgeExpiredArchives(archiveJdbc);

        lastRunAt = LocalDateTime.now();
        lastArchivedCount = archived;
        lastPurgedCount = purged;
        lastRunMessage = "Archivés : " + archived + ", purgés de l’archive : " + purged + ".";

        log.info("Import archive maintenance finished: archived={}, purged={}", archived, purged);
        return new ImportArchiveRunResultDto(archived, purged, lastRunMessage);
    }

    private int archiveInactiveImports(JdbcTemplate archiveJdbc) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.getInactiveDays());
        List<Long> importIds = primaryJdbc.queryForList("""
                        SELECT id
                        FROM log_imports
                        WHERE status IN ('SUCCESS', 'PARTIAL_SUCCESS')
                          AND COALESCE(last_accessed_at, created_at) < ?
                        ORDER BY COALESCE(last_accessed_at, created_at) ASC
                        LIMIT ?
                        """,
                Long.class,
                Timestamp.valueOf(cutoff),
                properties.getBatchSize());

        int archived = 0;
        for (Long importId : importIds) {
            try {
                copyImportToArchive(importId, archiveJdbc);
                logImportManagementService.deleteImport(importId);
                archived++;
                log.info("Import #{} archivé puis supprimé de la base active.", importId);
            } catch (Exception e) {
                log.error("Échec archivage import #{} : {}", importId, e.getMessage(), e);
            }
        }
        return archived;
    }

    private int purgeExpiredArchives(JdbcTemplate archiveJdbc) {
        LocalDateTime purgeCutoff = LocalDateTime.now().minusDays(properties.getPurgeDays());
        List<Long> importIds = archiveJdbc.queryForList("""
                        SELECT id
                        FROM log_imports
                        WHERE archived_at < ?
                        ORDER BY archived_at ASC
                        LIMIT ?
                        """,
                Long.class,
                Timestamp.valueOf(purgeCutoff),
                properties.getBatchSize());

        int purged = 0;
        for (Long importId : importIds) {
            archiveJdbc.update("DELETE FROM log_imports WHERE id = ?", importId);
            purged++;
            log.info("Import archivé #{} purgé définitivement.", importId);
        }
        return purged;
    }

    protected void copyImportToArchive(Long importId, JdbcTemplate archiveJdbc) {
        Map<String, Object> importRow = primaryJdbc.queryForMap(
                "SELECT * FROM log_imports WHERE id = ?", importId);

        LocalDateTime archivedAt = LocalDateTime.now();
        archiveJdbc.update("""
                        INSERT INTO log_imports (
                            id, file_name, original_file_name, file_hash, file_size,
                            started_at, finished_at, status, total_lines, parsed_lines, failed_lines,
                            total_errors, total_infos, total_warnings, source_server, source_environment,
                            source_app_version, summary, error_message, created_at, updated_at,
                            last_accessed_at, archived_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO NOTHING
                        """,
                importId,
                importRow.get("file_name"),
                importRow.get("original_file_name"),
                importRow.get("file_hash"),
                importRow.get("file_size"),
                importRow.get("started_at"),
                importRow.get("finished_at"),
                importRow.get("status"),
                importRow.get("total_lines"),
                importRow.get("parsed_lines"),
                importRow.get("failed_lines"),
                importRow.get("total_errors"),
                importRow.get("total_infos"),
                importRow.get("total_warnings"),
                importRow.get("source_server"),
                importRow.get("source_environment"),
                importRow.get("source_app_version"),
                importRow.get("summary"),
                importRow.get("error_message"),
                importRow.get("created_at"),
                importRow.get("updated_at"),
                importRow.get("last_accessed_at"),
                Timestamp.valueOf(archivedAt));

        copyLogEntries(importId, archiveJdbc);
        copyRawMessages(importId, archiveJdbc);
    }

    private void copyLogEntries(Long importId, JdbcTemplate archiveJdbc) {
        long lastId = 0L;
        final int chunk = 2000;

        while (true) {
            List<Map<String, Object>> rows = primaryJdbc.queryForList("""
                            SELECT *
                            FROM log_entries
                            WHERE import_id = ? AND id > ?
                            ORDER BY id
                            LIMIT ?
                            """,
                    importId, lastId, chunk);

            if (rows.isEmpty()) {
                break;
            }

            for (Map<String, Object> row : rows) {
                archiveJdbc.update("""
                                INSERT INTO log_entries (
                                    id, import_id, source_file_name, source_relative_path, log_timestamp,
                                    session_id, level, user_name, source_class, process_name, step_code,
                                    log_code, environment, server_name, app_version, user_correlation_id,
                                    message, raw_log, event_type, field_name, interface_field, field_class_code,
                                    parsed_type, parsed_value, relation_name, relation_key, business_key,
                                    mandatory_field, error_column, error_attribute, error_value, error_business_key,
                                    is_error, duration_ms, business_meaning, parse_quality, ambiguous_message, incomplete_line,
                                    created_at
                                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                ON CONFLICT (id) DO NOTHING
                                """,
                        row.get("id"),
                        row.get("import_id"),
                        row.get("source_file_name"),
                        row.get("source_relative_path"),
                        row.get("log_timestamp"),
                        row.get("session_id"),
                        row.get("level"),
                        row.get("user_name"),
                        row.get("source_class"),
                        row.get("process_name"),
                        row.get("step_code"),
                        row.get("log_code"),
                        row.get("environment"),
                        row.get("server_name"),
                        row.get("app_version"),
                        row.get("user_correlation_id"),
                        row.get("message"),
                        row.get("raw_log"),
                        row.get("event_type"),
                        row.get("field_name"),
                        row.get("interface_field"),
                        row.get("field_class_code"),
                        row.get("parsed_type"),
                        row.get("parsed_value"),
                        row.get("relation_name"),
                        row.get("relation_key"),
                        row.get("business_key"),
                        row.get("mandatory_field"),
                        row.get("error_column"),
                        row.get("error_attribute"),
                        row.get("error_value"),
                        row.get("error_business_key"),
                        row.get("is_error"),
                        row.get("duration_ms"),
                        row.get("business_meaning"),
                        row.get("parse_quality"),
                        row.get("ambiguous_message"),
                        row.get("incomplete_line"),
                        row.get("created_at"));
                lastId = ((Number) row.get("id")).longValue();
            }
        }
    }

    private void copyRawMessages(Long importId, JdbcTemplate archiveJdbc) {
        List<Map<String, Object>> rows = primaryJdbc.queryForList("""
                        SELECT rm.*
                        FROM log_raw_messages rm
                        JOIN log_entries e ON e.id = rm.log_entry_id
                        WHERE e.import_id = ?
                        """, importId);

        for (Map<String, Object> row : rows) {
            archiveJdbc.update("""
                            INSERT INTO log_raw_messages (id, log_entry_id, raw_message, message_tail, technical_id)
                            VALUES (?, ?, ?, ?, ?)
                            ON CONFLICT (id) DO NOTHING
                            """,
                    row.get("id"),
                    row.get("log_entry_id"),
                    row.get("raw_message"),
                    row.get("message_tail"),
                    row.get("technical_id"));
        }
    }

    private void ensureArchiveSchema(JdbcTemplate archiveJdbc) {
        if (archiveSchemaReady) {
            return;
        }
        synchronized (this) {
            if (archiveSchemaReady) {
                return;
            }
            Integer count = archiveJdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = 'log_imports'
                    """, Integer.class);

            if (count == null || count == 0) {
                try {
                    String sql = new ClassPathResource("archive-schema.sql")
                            .getContentAsString(StandardCharsets.UTF_8);
                    for (String statement : sql.split(";")) {
                        String trimmed = statement.trim();
                        if (!trimmed.isEmpty()) {
                            archiveJdbc.execute(trimmed);
                        }
                    }
                    log.info("Schéma archive initialisé dans {}", properties.getDatasource().getUrl());
                } catch (IOException e) {
                    throw new IllegalStateException("Impossible de charger archive-schema.sql", e);
                }
            }
            archiveSchemaReady = true;
        }
    }

    private JdbcTemplate requireArchiveJdbc() {
        JdbcTemplate archiveJdbc = archiveJdbcProvider.getIfAvailable();
        if (archiveJdbc == null) {
            throw new IllegalStateException("Archive datasource indisponible. Vérifiez app.archive.enabled et la connexion.");
        }
        return archiveJdbc;
    }
}
