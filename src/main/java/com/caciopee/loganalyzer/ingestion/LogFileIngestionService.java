package com.caciopee.loganalyzer.ingestion;

import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.entity.LogImportStatus;
import com.caciopee.loganalyzer.parser.LogParserService;
import com.caciopee.loganalyzer.parser.ParsedLogEntry;
import com.caciopee.loganalyzer.repository.LogImportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class LogFileIngestionService {

    private static final Logger log = LoggerFactory.getLogger(LogFileIngestionService.class);

    private static final int BATCH_SIZE = 1000;
    private static final int READER_BUFFER_SIZE = 64 * 1024;

    private static final String INSERT_LOG_ENTRY_SQL = """
            INSERT INTO public.log_entries (
                import_id,
                log_timestamp,
                session_id,
                level,
                user_name,
                source_class,
                process_name,
                step_code,
                log_code,
                environment,
                server_name,
                app_version,
                user_correlation_id,
                message,
                raw_log,
                event_type,
                field_name,
                interface_field,
                field_class_code,
                parsed_type,
                parsed_value,
                relation_name,
                relation_key,
                business_key,
                mandatory_field,
                error_column,
                error_attribute,
                error_value,
                error_business_key,
                is_error,
                business_meaning,
                parse_quality,
                ambiguous_message,
                incomplete_line,
                created_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;

    private static final String INSERT_LOG_RAW_MESSAGE_SQL = """
            INSERT INTO public.log_raw_messages (
                log_entry_id,
                raw_message,
                message_tail,
                technical_id
            ) VALUES (?, ?, ?, ?)
            """;

    private final LogParserService logParserService;
    private final LogImportRepository logImportRepository;
    private final JdbcTemplate jdbcTemplate;

    public LogFileIngestionService(LogParserService logParserService,
                                   LogImportRepository logImportRepository,
                                   JdbcTemplate jdbcTemplate) {
        this.logParserService = logParserService;
        this.logImportRepository = logImportRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UploadResult> ingestFiles(MultipartFile[] files) {
        return java.util.Arrays.stream(files == null ? new MultipartFile[0] : files)
                .filter(Objects::nonNull)
                .map(this::ingestOne)
                .toList();
    }

    private UploadResult ingestOne(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new UploadResult(null, safeName(file), false, "Fichier vide.");
        }

        File tempFile = null;
        LogImport logImport = null;

        try {
            tempFile = File.createTempFile("upload-", "-" + safeName(file));
            file.transferTo(tempFile);

            String fileHash = computeSha256(tempFile);

            var existingImportOpt = logImportRepository.findByFileHash(fileHash);
            if (existingImportOpt.isPresent()) {
                LogImport existingImport = existingImportOpt.get();
                long persistedRows = countPersistedRows(existingImport.getId());

                if (persistedRows > 0) {
                    return new UploadResult(
                            existingImport.getId(),
                            safeName(file),
                            false,
                            "Fichier déjà importé et réellement persisté. Import ID existant: "
                                    + existingImport.getId() + ", lignes persistées=" + persistedRows
                    );
                }

                existingImport.setStatus(LogImportStatus.FAILED);
                existingImport.setFinishedAt(LocalDateTime.now());
                existingImport.setErrorMessage("Import précédent incomplet : aucune ligne persistée dans public.log_entries.");
                existingImport.setSummary("Import invalide détecté automatiquement avant réimport.");
                logImportRepository.save(existingImport);

                log.warn("Ancien import incomplet détecté importId={} fileHash={} -> réimport autorisé",
                        existingImport.getId(), fileHash);
            }

            logImport = new LogImport();
            logImport.setFileName(safeName(file));
            logImport.setOriginalFileName(file.getOriginalFilename());
            logImport.setFileSize(file.getSize());
            logImport.setFileHash(fileHash);
            logImport.setStartedAt(LocalDateTime.now());
            logImport.setStatus(LogImportStatus.PROCESSING);
            logImport.setSummary("Import en cours...");
            logImport = logImportRepository.save(logImport);

            int totalLines = 0;
            int parsedOk = 0;
            int failed = 0;
            int totalErrors = 0;
            int totalInfos = 0;
            int totalWarnings = 0;
            int insertedRows = 0;

            boolean sourceMetadataInitialized = false;
            List<LogEntryRow> batch = new ArrayList<>(BATCH_SIZE);

            log.info("Début import fichier={} importId={}", safeName(file), logImport.getId());

            try (BufferedReader br = new BufferedReader(new FileReader(tempFile), READER_BUFFER_SIZE)) {
                String line;
                while ((line = br.readLine()) != null) {
                    totalLines++;

                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    try {
                        ParsedLogEntry parsed = logParserService.parseLine(line);

                        LogEntryRow row = mapToRow(parsed, logImport.getId());
                        batch.add(row);
                        parsedOk++;

                        if ("ERROR".equalsIgnoreCase(parsed.getLevel())) {
                            totalErrors++;
                        } else if ("INFO".equalsIgnoreCase(parsed.getLevel())) {
                            totalInfos++;
                        } else if ("WARN".equalsIgnoreCase(parsed.getLevel())
                                || "WARNING".equalsIgnoreCase(parsed.getLevel())) {
                            totalWarnings++;
                        }

                        if (!sourceMetadataInitialized) {
                            logImport.setSourceServer(parsed.getServerName());
                            logImport.setSourceEnvironment(parsed.getEnvironment());
                            logImport.setSourceAppVersion(parsed.getAppVersion());
                            sourceMetadataInitialized = true;
                        }

                        if (batch.size() >= BATCH_SIZE) {
                            insertedRows += insertBatch(batch);
                            batch.clear();
                        }

                    } catch (Exception e) {
                        failed++;
                        log.warn("Ligne ignorée importId={} lineNumber={} reason={}",
                                logImport.getId(), totalLines, e.getMessage());
                    }
                }
            }

            if (!batch.isEmpty()) {
                insertedRows += insertBatch(batch);
                batch.clear();
            }

            long persistedCount = countPersistedRows(logImport.getId());

            logImport.setFinishedAt(LocalDateTime.now());
            logImport.setTotalLines(totalLines);
            logImport.setParsedLines(parsedOk);
            logImport.setFailedLines(failed);
            logImport.setTotalErrors(totalErrors);
            logImport.setTotalInfos(totalInfos);
            logImport.setTotalWarnings(totalWarnings);

            String validationError = validateImportCounts(parsedOk, failed, insertedRows, persistedCount);

            if (validationError == null) {
                if (failed == 0) {
                    logImport.setStatus(LogImportStatus.SUCCESS);
                } else if (parsedOk > 0) {
                    logImport.setStatus(LogImportStatus.PARTIAL_SUCCESS);
                } else {
                    logImport.setStatus(LogImportStatus.FAILED);
                }
                logImport.setErrorMessage(null);
            } else {
                logImport.setStatus(LogImportStatus.FAILED);
                logImport.setErrorMessage(validationError);
                log.error("Import incohérent importId={} : {}", logImport.getId(), validationError);
            }

            logImport.setSummary(buildSummary(
                    totalLines,
                    parsedOk,
                    failed,
                    insertedRows,
                    persistedCount,
                    totalErrors,
                    totalInfos,
                    totalWarnings
            ));

            logImportRepository.save(logImport);

            return new UploadResult(
                    logImport.getId(),
                    safeName(file),
                    validationError == null,
                    validationError == null
                            ? "Import terminé. Lignes lues=" + totalLines
                              + ", parsées=" + parsedOk
                              + ", insérées=" + insertedRows
                              + ", persistées=" + persistedCount
                              + ", erreurs parsing=" + failed
                              + ", logs ERROR=" + totalErrors
                            : "Import échoué : " + validationError
            );

        } catch (Exception e) {
            log.error("Erreur globale pendant l'import du fichier {}", safeName(file), e);

            if (logImport != null) {
                logImport.setFinishedAt(LocalDateTime.now());
                logImport.setStatus(LogImportStatus.FAILED);
                logImport.setErrorMessage(e.getMessage());
                logImport.setSummary("Import échoué.");
                logImportRepository.save(logImport);
            }

            return new UploadResult(
                    logImport != null ? logImport.getId() : null,
                    safeName(file),
                    false,
                    "Erreur: " + e.getMessage()
            );

        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("Impossible de supprimer le fichier temporaire {}", tempFile.getAbsolutePath());
            }
        }
    }

    private int insertBatch(List<LogEntryRow> batch) {
        int[] results = jdbcTemplate.batchUpdate(INSERT_LOG_ENTRY_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                LogEntryRow row = batch.get(i);

                ps.setLong(1, row.importId());
                setTimestamp(ps, 2, row.logTimestamp());
                setString(ps, 3, row.sessionId());
                setString(ps, 4, row.level());
                setString(ps, 5, row.userName());
                setString(ps, 6, row.sourceClass());
                setString(ps, 7, row.processName());
                setString(ps, 8, row.stepCode());
                setInteger(ps, 9, row.logCode());
                setString(ps, 10, row.environment());
                setString(ps, 11, row.serverName());
                setString(ps, 12, row.appVersion());
                setString(ps, 13, row.userCorrelationId());
                setString(ps, 14, row.message());
                setString(ps, 15, row.rawLog());
                setString(ps, 16, row.eventType());
                setString(ps, 17, row.fieldName());
                setString(ps, 18, row.interfaceField());
                setString(ps, 19, row.fieldClassCode());
                setString(ps, 20, row.parsedType());
                setString(ps, 21, row.parsedValue());
                setString(ps, 22, row.relationName());
                setString(ps, 23, row.relationKey());
                setString(ps, 24, row.businessKey());
                setString(ps, 25, row.mandatoryField());
                setString(ps, 26, row.errorColumn());
                setString(ps, 27, row.errorAttribute());
                setString(ps, 28, row.errorValue());
                setString(ps, 29, row.errorBusinessKey());
                ps.setBoolean(30, row.isError());
                setString(ps, 31, row.businessMeaning());
                setString(ps, 32, row.parseQuality());
                ps.setBoolean(33, row.ambiguousMessage());
                ps.setBoolean(34, row.incompleteLine());
                setTimestamp(ps, 35, row.createdAt());
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });

        List<Long> entryIds = jdbcTemplate.queryForList("""
                SELECT id
                FROM public.log_entries
                WHERE import_id = ?
                ORDER BY id DESC
                LIMIT ?
                """, Long.class, batch.get(0).importId(), batch.size());

        java.util.Collections.reverse(entryIds);

        int rawCount = Math.min(entryIds.size(), batch.size());
        for (int i = 0; i < rawCount; i++) {
            LogEntryRow row = batch.get(i);
            Long entryId = entryIds.get(i);

            jdbcTemplate.update(
                    INSERT_LOG_RAW_MESSAGE_SQL,
                    entryId,
                    row.rawMessage(),
                    row.messageTail(),
                    row.technicalId()
            );
        }

        int inserted = 0;
        for (int r : results) {
            if (r > 0 || r == Statement.SUCCESS_NO_INFO) {
                inserted++;
            }
        }
        return inserted;
    }

    private long countPersistedRows(Long importId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.log_entries WHERE import_id = ?",
                Long.class,
                importId
        );
        return count == null ? 0L : count;
    }

    private String validateImportCounts(int parsedOk, int failed, int insertedRows, long persistedCount) {
        if (parsedOk == 0 && failed == 0) {
            return "Aucune ligne utile trouvée dans le fichier.";
        }
        if (parsedOk > 0 && insertedRows == 0) {
            return "Des lignes ont été parsées mais aucune ligne n'a été insérée en base.";
        }
        if (insertedRows != parsedOk) {
            return "Incohérence parsed/inserted : parsed=" + parsedOk + ", inserted=" + insertedRows;
        }
        if (persistedCount != insertedRows) {
            return "Incohérence inserted/persisted : inserted=" + insertedRows + ", persisted=" + persistedCount;
        }
        return null;
    }

    private LogEntryRow mapToRow(ParsedLogEntry parsed, Long importId) {
        return new LogEntryRow(
                importId,
                parsed.getLogTimestamp(),
                parsed.getExecutionId(),
                parsed.getLevel(),
                parsed.getUserName(),
                parsed.getSourceClass(),
                parsed.getProcessName(),
                parsed.getProcessName(),
                parsed.getLogCode(),
                parsed.getEnvironment(),
                parsed.getServerName(),
                parsed.getAppVersion(),
                parsed.getCorrelationId(),
                parsed.getMessage(),
                parsed.getRawLog(),
                parsed.getEventType(),
                parsed.getFieldName(),
                parsed.getInterfaceField(),
                parsed.getFieldClassCode(),
                parsed.getParsedType(),
                parsed.getParsedValue(),
                parsed.getRelationName(),
                parsed.getRelationKey(),
                parsed.getBusinessKey(),
                parsed.getMandatoryField(),
                parsed.getErrorColumn(),
                parsed.getErrorAttribute(),
                parsed.getErrorValue(),
                parsed.getErrorBusinessKey(),
                Boolean.TRUE.equals(parsed.getError()),
                parsed.getBusinessMeaning(),
                parsed.getParseQuality() != null ? parsed.getParseQuality().name() : null,
                parsed.isAmbiguousMessage(),
                parsed.isIncomplete(),
                LocalDateTime.now(),
                parsed.getRawMessage(),
                parsed.getMessageTail(),
                parsed.getTechnicalId()
        );
    }

    private String buildSummary(int totalLines,
                                int parsedOk,
                                int failed,
                                int insertedRows,
                                long persistedCount,
                                int totalErrors,
                                int totalInfos,
                                int totalWarnings) {
        return "Import terminé. "
                + "Lignes lues=" + totalLines
                + ", parsées=" + parsedOk
                + ", erreurs parsing=" + failed
                + ", insérées=" + insertedRows
                + ", persistées=" + persistedCount
                + ", logs ERROR=" + totalErrors
                + ", logs INFO=" + totalInfos
                + ", logs WARN=" + totalWarnings;
    }

    private String computeSha256(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;

            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }

            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de calculer le hash du fichier", e);
        }
    }

    private void setString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private void setTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    private String safeName(MultipartFile file) {
        String name = file == null ? null : file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "unknown.log" : name;
    }

    public record UploadResult(Long importId, String fileName, boolean success, String message) {}

    private record LogEntryRow(
            Long importId,
            LocalDateTime logTimestamp,
            String sessionId,
            String level,
            String userName,
            String sourceClass,
            String processName,
            String stepCode,
            Integer logCode,
            String environment,
            String serverName,
            String appVersion,
            String userCorrelationId,
            String message,
            String rawLog,
            String eventType,
            String fieldName,
            String interfaceField,
            String fieldClassCode,
            String parsedType,
            String parsedValue,
            String relationName,
            String relationKey,
            String businessKey,
            String mandatoryField,
            String errorColumn,
            String errorAttribute,
            String errorValue,
            String errorBusinessKey,
            boolean isError,
            String businessMeaning,
            String parseQuality,
            boolean ambiguousMessage,
            boolean incompleteLine,
            LocalDateTime createdAt,
            String rawMessage,
            String messageTail,
            String technicalId
    ) {}
}