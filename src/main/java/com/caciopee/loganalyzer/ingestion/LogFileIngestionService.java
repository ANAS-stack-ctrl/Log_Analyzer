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

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Transactional
public class LogFileIngestionService {

    private static final Logger log = LoggerFactory.getLogger(LogFileIngestionService.class);

    private static final int BATCH_SIZE = 1000;
    private static final int READER_BUFFER_SIZE = 64 * 1024;

    private static final String INSERT_LOG_ENTRY_SQL = """
            INSERT INTO public.log_entries (
                import_id,
                source_file_name,
                source_relative_path,
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
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
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
        return Arrays.stream(files == null ? new MultipartFile[0] : files)
                .filter(Objects::nonNull)
                .map(this::ingestOne)
                .toList();
    }

    private UploadResult ingestOne(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new UploadResult(null, safeName(file), false, "Fichier vide.");
        }

        File tempFile = null;
        Path unzipDir = null;
        LogImport logImport = null;

        try {
            String uploadedName = safeName(file);

            tempFile = File.createTempFile("upload-", "-" + uploadedName);
            file.transferTo(tempFile);

            String fileHash = computeSha256(tempFile);

            Optional<LogImport> existingImportOpt = logImportRepository.findByFileHash(fileHash);
            if (existingImportOpt.isPresent()) {
                LogImport existingImport = existingImportOpt.get();
                long persistedRows = countPersistedRows(existingImport.getId());

                if (persistedRows > 0) {
                    return new UploadResult(
                            existingImport.getId(),
                            uploadedName,
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
            }

            logImport = new LogImport();
            logImport.setFileName(uploadedName);
            logImport.setOriginalFileName(file.getOriginalFilename());
            logImport.setFileSize(file.getSize());
            logImport.setFileHash(fileHash);
            logImport.setStartedAt(LocalDateTime.now());
            logImport.setStatus(LogImportStatus.PROCESSING);
            logImport.setSummary("Import en cours...");
            logImport = logImportRepository.save(logImport);

            List<FileToImport> filesToImport;

            if (isZipFile(uploadedName)) {
                unzipDir = Files.createTempDirectory("loganalyzer-zip-");
                filesToImport = unzipLogFiles(tempFile.toPath(), unzipDir);

                if (filesToImport.isEmpty()) {
                    throw new IllegalArgumentException("Le fichier ZIP ne contient aucun fichier .log ou .txt exploitable.");
                }

                log.info("ZIP détecté importId={} zip={} fichiersLogs={}",
                        logImport.getId(), uploadedName, filesToImport.size());
            } else {
                if (!isSupportedLogFile(uploadedName)) {
                    throw new IllegalArgumentException("Format non supporté. Formats acceptés : .log, .txt, .zip");
                }

                filesToImport = List.of(new FileToImport(
                        tempFile.toPath(),
                        uploadedName,
                        uploadedName
                ));
            }

            ImportCounters counters = processFiles(logImport, filesToImport);

            long persistedCount = countPersistedRows(logImport.getId());

            logImport.setFinishedAt(LocalDateTime.now());
            logImport.setTotalLines(counters.totalLines);
            logImport.setParsedLines(counters.parsedOk);
            logImport.setFailedLines(counters.failed);
            logImport.setTotalErrors(counters.totalErrors);
            logImport.setTotalInfos(counters.totalInfos);
            logImport.setTotalWarnings(counters.totalWarnings);

            String validationError = validateImportCounts(
                    counters.parsedOk,
                    counters.failed,
                    counters.insertedRows,
                    persistedCount
            );

            if (validationError == null) {
                if (counters.failed == 0) {
                    logImport.setStatus(LogImportStatus.SUCCESS);
                } else if (counters.parsedOk > 0) {
                    logImport.setStatus(LogImportStatus.PARTIAL_SUCCESS);
                } else {
                    logImport.setStatus(LogImportStatus.FAILED);
                }
                logImport.setErrorMessage(null);
            } else {
                logImport.setStatus(LogImportStatus.FAILED);
                logImport.setErrorMessage(validationError);
            }

            logImport.setSummary(buildSummary(
                    counters.totalFiles,
                    counters.totalLines,
                    counters.parsedOk,
                    counters.failed,
                    counters.insertedRows,
                    persistedCount,
                    counters.totalErrors,
                    counters.totalInfos,
                    counters.totalWarnings
            ));

            logImportRepository.save(logImport);

            return new UploadResult(
                    logImport.getId(),
                    uploadedName,
                    validationError == null,
                    validationError == null
                            ? "Import terminé. Fichiers traités=" + counters.totalFiles
                              + ", lignes lues=" + counters.totalLines
                              + ", parsées=" + counters.parsedOk
                              + ", insérées=" + counters.insertedRows
                              + ", persistées=" + persistedCount
                              + ", erreurs parsing=" + counters.failed
                              + ", logs ERROR=" + counters.totalErrors
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

            if (unzipDir != null) {
                deleteDirectoryQuietly(unzipDir);
            }
        }
    }

    private ImportCounters processFiles(LogImport logImport, List<FileToImport> filesToImport) throws IOException {
        ImportCounters counters = new ImportCounters();
        counters.totalFiles = filesToImport.size();

        boolean sourceMetadataInitialized = false;
        List<LogEntryRow> batch = new ArrayList<>(BATCH_SIZE);

        for (FileToImport fileToImport : filesToImport) {
            log.info("Début traitement fichier interne importId={} file={}",
                    logImport.getId(), fileToImport.relativePath());

            try (BufferedReader br = new BufferedReader(new FileReader(fileToImport.path().toFile()), READER_BUFFER_SIZE)) {
                String line;

                while ((line = br.readLine()) != null) {
                    counters.totalLines++;

                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    try {
                        ParsedLogEntry parsed = logParserService.parseLine(line);

                        LogEntryRow row = mapToRow(
                                parsed,
                                logImport.getId(),
                                fileToImport.fileName(),
                                fileToImport.relativePath()
                        );

                        batch.add(row);
                        counters.parsedOk++;

                        if ("ERROR".equalsIgnoreCase(parsed.getLevel())) {
                            counters.totalErrors++;
                        } else if ("INFO".equalsIgnoreCase(parsed.getLevel())) {
                            counters.totalInfos++;
                        } else if ("WARN".equalsIgnoreCase(parsed.getLevel())
                                || "WARNING".equalsIgnoreCase(parsed.getLevel())) {
                            counters.totalWarnings++;
                        }

                        if (!sourceMetadataInitialized) {
                            logImport.setSourceServer(parsed.getServerName());
                            logImport.setSourceEnvironment(parsed.getEnvironment());
                            logImport.setSourceAppVersion(parsed.getAppVersion());
                            sourceMetadataInitialized = true;
                        }

                        if (batch.size() >= BATCH_SIZE) {
                            counters.insertedRows += insertBatch(batch);
                            batch.clear();
                        }

                    } catch (Exception e) {
                        counters.failed++;
                        log.warn("Ligne ignorée importId={} file={} lineNumber={} reason={}",
                                logImport.getId(),
                                fileToImport.relativePath(),
                                counters.totalLines,
                                e.getMessage());
                    }
                }
            }
        }

        if (!batch.isEmpty()) {
            counters.insertedRows += insertBatch(batch);
            batch.clear();
        }

        return counters;
    }

    private List<FileToImport> unzipLogFiles(Path zipPath, Path destinationDir) throws IOException {
        List<FileToImport> result = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipPath)))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                String entryName = normalizeZipEntryName(entry.getName());

                if (!isSupportedLogFile(entryName)) {
                    zis.closeEntry();
                    continue;
                }

                Path target = destinationDir.resolve(entryName).normalize();

                if (!target.startsWith(destinationDir.normalize())) {
                    throw new IOException("Entrée ZIP dangereuse détectée : " + entry.getName());
                }

                Files.createDirectories(target.getParent());

                try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(target))) {
                    zis.transferTo(os);
                }

                result.add(new FileToImport(
                        target,
                        target.getFileName().toString(),
                        entryName
                ));

                zis.closeEntry();
            }
        }

        result.sort(Comparator.comparing(FileToImport::relativePath));
        return result;
    }

    private int insertBatch(List<LogEntryRow> batch) {
        int[] results = jdbcTemplate.batchUpdate(INSERT_LOG_ENTRY_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                LogEntryRow row = batch.get(i);

                ps.setLong(1, row.importId());
                setString(ps, 2, row.sourceFileName());
                setString(ps, 3, row.sourceRelativePath());
                setTimestamp(ps, 4, row.logTimestamp());
                setString(ps, 5, row.sessionId());
                setString(ps, 6, row.level());
                setString(ps, 7, row.userName());
                setString(ps, 8, row.sourceClass());
                setString(ps, 9, row.processName());
                setString(ps, 10, row.stepCode());
                setInteger(ps, 11, row.logCode());
                setString(ps, 12, row.environment());
                setString(ps, 13, row.serverName());
                setString(ps, 14, row.appVersion());
                setString(ps, 15, row.userCorrelationId());
                setString(ps, 16, row.message());
                setString(ps, 17, row.rawLog());
                setString(ps, 18, row.eventType());
                setString(ps, 19, row.fieldName());
                setString(ps, 20, row.interfaceField());
                setString(ps, 21, row.fieldClassCode());
                setString(ps, 22, row.parsedType());
                setString(ps, 23, row.parsedValue());
                setString(ps, 24, row.relationName());
                setString(ps, 25, row.relationKey());
                setString(ps, 26, row.businessKey());
                setString(ps, 27, row.mandatoryField());
                setString(ps, 28, row.errorColumn());
                setString(ps, 29, row.errorAttribute());
                setString(ps, 30, row.errorValue());
                setString(ps, 31, row.errorBusinessKey());
                ps.setBoolean(32, row.isError());
                setString(ps, 33, row.businessMeaning());
                setString(ps, 34, row.parseQuality());
                ps.setBoolean(35, row.ambiguousMessage());
                ps.setBoolean(36, row.incompleteLine());
                setTimestamp(ps, 37, row.createdAt());
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

        Collections.reverse(entryIds);

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

    private LogEntryRow mapToRow(ParsedLogEntry parsed,
                                 Long importId,
                                 String sourceFileName,
                                 String sourceRelativePath) {
        return new LogEntryRow(
                importId,
                sourceFileName,
                sourceRelativePath,
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

    private String buildSummary(int totalFiles,
                                int totalLines,
                                int parsedOk,
                                int failed,
                                int insertedRows,
                                long persistedCount,
                                int totalErrors,
                                int totalInfos,
                                int totalWarnings) {
        return "Import terminé. "
                + "Fichiers traités=" + totalFiles
                + ", lignes lues=" + totalLines
                + ", parsées=" + parsedOk
                + ", erreurs parsing=" + failed
                + ", insérées=" + insertedRows
                + ", persistées=" + persistedCount
                + ", logs ERROR=" + totalErrors
                + ", logs INFO=" + totalInfos
                + ", logs WARN=" + totalWarnings;
    }

    private boolean isZipFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private boolean isSupportedLogFile(String fileName) {
        if (fileName == null) return false;

        String lower = fileName.toLowerCase(Locale.ROOT);

        return lower.endsWith(".log")
                || lower.endsWith(".txt")
                || lower.endsWith(".out");
    }

    private String normalizeZipEntryName(String name) {
        if (name == null) return "unknown.log";
        return name.replace("\\", "/").replaceAll("^/+", "");
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

    private void deleteDirectoryQuietly(Path dir) {
        try {
            if (dir == null || !Files.exists(dir)) return;

            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.warn("Impossible de supprimer {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("Impossible de supprimer le dossier temporaire {}", dir, e);
        }
    }

    private void setString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private void setTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    private String safeName(MultipartFile file) {
        String name = file == null ? null : file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "unknown.log" : Paths.get(name).getFileName().toString();
    }

    public record UploadResult(Long importId, String fileName, boolean success, String message) {}

    private record FileToImport(
            Path path,
            String fileName,
            String relativePath
    ) {}

    private static class ImportCounters {
        int totalFiles = 0;
        int totalLines = 0;
        int parsedOk = 0;
        int failed = 0;
        int totalErrors = 0;
        int totalInfos = 0;
        int totalWarnings = 0;
        int insertedRows = 0;
    }

    private record LogEntryRow(
            Long importId,
            String sourceFileName,
            String sourceRelativePath,
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