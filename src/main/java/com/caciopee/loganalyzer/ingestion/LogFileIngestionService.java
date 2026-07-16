package com.caciopee.loganalyzer.ingestion;

import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.entity.LogImportStatus;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.parser.LogParserService;
import com.caciopee.loganalyzer.parser.ParsedLogEntry;
import com.caciopee.loganalyzer.repository.LogImportRepository;
import com.caciopee.loganalyzer.service.ImportBackgroundService;
import com.caciopee.loganalyzer.service.ImportProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Transactional
public class LogFileIngestionService {

    private static final Logger log = LoggerFactory.getLogger(LogFileIngestionService.class);

    /** Lots JDBC plus petits → pic mémoire bas (import multi-fichiers sur PC 16 Go). */
    private static final int BATCH_SIZE = 400;
    private static final int READER_BUFFER_SIZE = 64 * 1024;
    private static final int PROGRESS_LINE_STEP = 10000;

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
                duration_ms,
                business_meaning,
                parse_quality,
                ambiguous_message,
                incomplete_line,
                created_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
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
    private final LogPatternExtractor logPatternExtractor;
    private final ImportProgressService importProgressService;
    private final ImportBackgroundService importBackgroundService;
    private final LogFileIngestionService self;

    /** Sessions d'upload découpé (navigateur envoie 20 fichiers par paquets de ~4). */
    private final ConcurrentHashMap<String, UploadSession> uploadSessions = new ConcurrentHashMap<>();

    public LogFileIngestionService(LogParserService logParserService,
                                   LogImportRepository logImportRepository,
                                   JdbcTemplate jdbcTemplate,
                                   LogPatternExtractor logPatternExtractor,
                                   ImportProgressService importProgressService,
                                   ImportBackgroundService importBackgroundService,
                                   @Lazy LogFileIngestionService self) {
        this.logParserService = logParserService;
        this.logImportRepository = logImportRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.logPatternExtractor = logPatternExtractor;
        this.importProgressService = importProgressService;
        this.importBackgroundService = importBackgroundService;
        this.self = self;
    }

    public List<UploadResult> ingestFiles(MultipartFile[] files) {
        List<MultipartFile> list = Arrays.stream(files == null ? new MultipartFile[0] : files)
                .filter(Objects::nonNull)
                .filter(f -> !f.isEmpty())
                .toList();

        if (list.isEmpty()) {
            return List.of(new UploadResult(null, "", false, "Aucun fichier sélectionné."));
        }

        if (list.size() == 1) {
            return List.of(ingestOne(list.get(0)));
        }

        boolean anyZip = list.stream().anyMatch(f -> isZipFile(safeName(f)));
        if (anyZip) {
            return list.stream().map(this::ingestOne).toList();
        }

        return List.of(ingestMultipleAsOneImport(list));
    }

    /**
     * Démarre une session d'upload découpé : le navigateur envoie les fichiers
     * par petits paquets HTTP, puis {@link #commitUploadSession(String)} lance
     * un seul import — évite le pic mémoire d'un multipart géant.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UploadSessionDto startUploadSession() throws IOException {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        Path dir = Files.createTempDirectory("loganalyzer-session-" + sessionId + "-");
        uploadSessions.put(sessionId, new UploadSession(dir));
        return new UploadSessionDto(sessionId, 0);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UploadSessionDto addFilesToUploadSession(String sessionId, MultipartFile[] files) throws IOException {
        UploadSession session = requireSession(sessionId);
        List<MultipartFile> list = Arrays.stream(files == null ? new MultipartFile[0] : files)
                .filter(Objects::nonNull)
                .filter(f -> !f.isEmpty())
                .toList();
        if (list.isEmpty()) {
            return new UploadSessionDto(sessionId, session.files.size());
        }
        for (MultipartFile file : list) {
            String uploadedName = safeName(file);
            if (!isSupportedLogFile(uploadedName)) {
                throw new IllegalArgumentException(
                        "Format non supporté : " + uploadedName + ". Formats : .log, .txt, .out");
            }
            String uniqueName = uniqueFileName(session.dir, sanitizeFileName(uploadedName));
            Path target = session.dir.resolve(uniqueName).normalize();
            if (!target.startsWith(session.dir.normalize())) {
                throw new IOException("Nom de fichier invalide : " + uploadedName);
            }
            file.transferTo(target);
            String relative = file.getOriginalFilename();
            if (relative == null || relative.isBlank()) {
                relative = uploadedName;
            }
            relative = relative.replace("\\", "/");
            session.files.add(new FileToImport(target, uploadedName, relative));
            session.totalSize += Math.max(0L, file.getSize());
        }
        return new UploadSessionDto(sessionId, session.files.size());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public UploadResult commitUploadSession(String sessionId) {
        UploadSession session = uploadSessions.remove(sessionId);
        if (session == null) {
            return new UploadResult(null, "session", false, "Session d'upload introuvable ou déjà terminée.");
        }
        if (session.files.isEmpty()) {
            deleteDirectoryQuietly(session.dir);
            return new UploadResult(null, "session", false, "Aucun fichier reçu dans la session.");
        }
        try {
            List<FileToImport> filesToImport = new ArrayList<>(session.files);
            filesToImport.sort(Comparator.comparing(FileToImport::relativePath));
            String displayName = "lot-" + filesToImport.size() + "-fichiers";
            String fileHash = computeAggregateHash(filesToImport);
            final Path cleanupDir = session.dir;
            return runImportJob(
                    displayName,
                    displayName,
                    session.totalSize,
                    fileHash,
                    filesToImport,
                    "Session upload découpé",
                    () -> deleteDirectoryQuietly(cleanupDir)
            );
        } catch (Exception e) {
            log.error("Erreur commit session upload {}", sessionId, e);
            deleteDirectoryQuietly(session.dir);
            return new UploadResult(null, "session", false, "Erreur: " + e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void abortUploadSession(String sessionId) {
        UploadSession session = uploadSessions.remove(sessionId);
        if (session != null) {
            deleteDirectoryQuietly(session.dir);
        }
    }

    private UploadSession requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId requis.");
        }
        UploadSession session = uploadSessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session d'upload introuvable : " + sessionId);
        }
        return session;
    }

    private static String uniqueFileName(Path dir, String baseName) {
        Path candidate = dir.resolve(baseName);
        if (!Files.exists(candidate)) {
            return baseName;
        }
        String stem = baseName;
        String ext = "";
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            stem = baseName.substring(0, dot);
            ext = baseName.substring(dot);
        }
        for (int i = 2; i < 10_000; i++) {
            String name = stem + "-" + i + ext;
            if (!Files.exists(dir.resolve(name))) {
                return name;
            }
        }
        return stem + "-" + UUID.randomUUID() + ext;
    }

    public UploadResult ingestFromDirectory(String directoryPath, boolean recursive) {
        if (directoryPath == null || directoryPath.isBlank()) {
            return new UploadResult(null, "", false, "Chemin de dossier requis.");
        }

        Path dir;
        try {
            dir = Paths.get(directoryPath.trim()).normalize().toAbsolutePath();
        } catch (Exception e) {
            return new UploadResult(null, directoryPath, false, "Chemin invalide : " + e.getMessage());
        }

        if (!Files.isDirectory(dir)) {
            return new UploadResult(null, dir.toString(), false, "Dossier introuvable : " + dir);
        }

        try {
            List<FileToImport> filesToImport = collectSupportedLogFiles(dir, recursive);
            if (filesToImport.isEmpty()) {
                return new UploadResult(
                        null,
                        dir.getFileName().toString(),
                        false,
                        "Aucun fichier .log, .txt ou .out trouvé dans ce dossier."
                );
            }

            long totalSize = filesToImport.stream()
                    .mapToLong(f -> {
                        try {
                            return Files.size(f.path());
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();

            String displayName = dir.getFileName() + " (" + filesToImport.size() + " fichiers)";
            String fileHash = computeAggregateHash(filesToImport);

            return runImportJob(
                    displayName,
                    dir.toString(),
                    totalSize,
                    fileHash,
                    filesToImport,
                    "Dossier local importé : " + dir
            );
        } catch (Exception e) {
            log.error("Erreur import dossier {}", dir, e);
            return new UploadResult(null, dir.toString(), false, "Erreur: " + e.getMessage());
        }
    }

    private UploadResult ingestMultipleAsOneImport(List<MultipartFile> files) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("loganalyzer-batch-");
            List<FileToImport> filesToImport = new ArrayList<>();
            long totalSize = 0L;

            for (MultipartFile file : files) {
                String uploadedName = safeName(file);
                if (!isSupportedLogFile(uploadedName)) {
                    return new UploadResult(null, uploadedName, false,
                            "Format non supporté dans le lot : " + uploadedName + ". Formats : .log, .txt, .out");
                }

                Path target = tempDir.resolve(sanitizeFileName(uploadedName)).normalize();
                if (!target.startsWith(tempDir.normalize())) {
                    throw new IOException("Nom de fichier invalide : " + uploadedName);
                }

                file.transferTo(target);
                totalSize += file.getSize();

                String relative = file.getOriginalFilename();
                if (relative == null || relative.isBlank()) {
                    relative = uploadedName;
                }
                relative = relative.replace("\\", "/");

                filesToImport.add(new FileToImport(target, uploadedName, relative));
            }

            filesToImport.sort(Comparator.comparing(FileToImport::relativePath));
            String displayName = "lot-" + filesToImport.size() + "-fichiers";
            String fileHash = computeAggregateHash(filesToImport);
            final Path cleanupDir = tempDir;

            return runImportJob(
                    displayName,
                    displayName,
                    totalSize,
                    fileHash,
                    filesToImport,
                    null,
                    () -> {
                        if (cleanupDir != null) {
                            deleteDirectoryQuietly(cleanupDir);
                        }
                    }
            );
        } catch (Exception e) {
            log.error("Erreur import lot de fichiers", e);
            if (tempDir != null) {
                deleteDirectoryQuietly(tempDir);
            }
            return new UploadResult(null, "lot", false, "Erreur: " + e.getMessage());
        }
    }

    private static final int MAX_FOLDER_FILES = 500;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markImportFailed(Long importId, String message) {
        if (importId == null) return;
        logImportRepository.findById(importId).ifPresent(logImport -> {
            logImport.setFinishedAt(LocalDateTime.now());
            logImport.setStatus(LogImportStatus.FAILED);
            logImport.setErrorMessage(message);
            logImport.setSummary("Import échoué.");
            logImport.setProcessingPercent(0);
            logImportRepository.save(logImport);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeImportJob(Long importId, List<FileToImport> filesToImport, String displayName) {
        try {
            LogImport logImport = logImportRepository.findById(importId)
                    .orElseThrow(() -> new IllegalArgumentException("Import introuvable: " + importId));
            importProgressService.initProgress(importId, filesToImport.size());
            ImportCounters counters = processFiles(logImport, filesToImport);
            finalizeImport(logImport, displayName, counters);
        } catch (Exception e) {
            log.error("Erreur import asynchrone importId={}", importId, e);
            self.markImportFailed(importId, e.getMessage());
        }
    }

    private UploadResult runImportJob(String fileName,
                                      String originalFileName,
                                      long fileSize,
                                      String fileHash,
                                      List<FileToImport> filesToImport,
                                      String logPrefix,
                                      Runnable cleanup) {
        LogImport logImport = null;

        try {
            Optional<LogImport> existingImportOpt = logImportRepository.findByFileHash(fileHash);
            if (existingImportOpt.isPresent()) {
                LogImport existingImport = existingImportOpt.get();
                long persistedRows = countPersistedRows(existingImport.getId());

                if (persistedRows > 0) {
                    return new UploadResult(
                            existingImport.getId(),
                            fileName,
                            false,
                            "Contenu déjà importé. Import ID existant: "
                                    + existingImport.getId() + ", lignes persistées=" + persistedRows
                    );
                }

                if (existingImport.getStatus() == LogImportStatus.PROCESSING) {
                    log.warn("Import précédent bloqué en PROCESSING sans données — marqué en échec importId={}",
                            existingImport.getId());
                }

                existingImport.setStatus(LogImportStatus.FAILED);
                existingImport.setFinishedAt(LocalDateTime.now());
                existingImport.setErrorMessage("Import précédent incomplet : aucune ligne persistée dans public.log_entries.");
                existingImport.setSummary("Import invalide détecté automatiquement avant réimport.");
                logImportRepository.save(existingImport);
            }

            logImport = new LogImport();
            logImport.setFileName(fileName);
            logImport.setOriginalFileName(originalFileName);
            logImport.setFileSize(fileSize);
            logImport.setFileHash(fileHash);
            logImport.setStartedAt(LocalDateTime.now());
            logImport.setStatus(LogImportStatus.PROCESSING);
            logImport.setSummary("Import en cours...");
            logImport.setProcessingPercent(0);
            logImport.setProcessingFileCount(filesToImport.size());
            logImport.setProcessingFileIndex(0);
            logImport.setProcessingCurrentFile("Démarrage…");
            logImport = logImportRepository.save(logImport);

            if (logPrefix != null) {
                log.info("{} importId={} fichiers={}", logPrefix, logImport.getId(), filesToImport.size());
            } else {
                log.info("Lot multi-fichiers importId={} fichiers={}", logImport.getId(), filesToImport.size());
            }

            Long importId = logImport.getId();
            List<FileToImport> filesSnapshot = List.copyOf(filesToImport);
            Runnable backgroundJob = () -> {
                try {
                    self.executeImportJob(importId, filesSnapshot, fileName);
                } finally {
                    if (cleanup != null) {
                        cleanup.run();
                    }
                }
            };
            scheduleImportAfterCommit(backgroundJob);

            return new UploadResult(
                    importId,
                    fileName,
                    true,
                    "Import démarré — suivez la progression ci-dessous.",
                    true
            );
        } catch (Exception e) {
            log.error("Erreur globale pendant l'import {}", fileName, e);

            if (logImport != null) {
                logImport.setFinishedAt(LocalDateTime.now());
                logImport.setStatus(LogImportStatus.FAILED);
                logImport.setErrorMessage(e.getMessage());
                logImport.setSummary("Import échoué.");
                logImportRepository.save(logImport);
            }

            if (cleanup != null) {
                cleanup.run();
            }

            return new UploadResult(
                    logImport != null ? logImport.getId() : null,
                    fileName,
                    false,
                    "Erreur: " + e.getMessage()
            );
        }
    }

    private UploadResult runImportJob(String fileName,
                                      String originalFileName,
                                      long fileSize,
                                      String fileHash,
                                      List<FileToImport> filesToImport,
                                      String logPrefix) {
        return runImportJob(fileName, originalFileName, fileSize, fileHash, filesToImport, logPrefix, null);
    }

    private void scheduleImportAfterCommit(Runnable backgroundJob) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    importBackgroundService.schedule(backgroundJob);
                }
            });
            return;
        }
        importBackgroundService.schedule(backgroundJob);
    }

    private UploadResult finalizeImport(LogImport logImport, String uploadedName, ImportCounters counters) {
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

        logImport.setLastAccessedAt(LocalDateTime.now());
        logImport.setProcessingPercent(100);
        logImport.setProcessingCurrentFile("Terminé");
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
    }

    private List<FileToImport> collectSupportedLogFiles(Path root, boolean recursive) throws IOException {
        List<FileToImport> result = new ArrayList<>();

        try (Stream<Path> stream = recursive ? Files.walk(root) : Files.list(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> isSupportedLogFile(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> root.relativize(p).toString()))
                    .limit(MAX_FOLDER_FILES)
                    .forEach(p -> {
                        String relative = root.relativize(p).toString().replace("\\", "/");
                        result.add(new FileToImport(
                                p,
                                p.getFileName().toString(),
                                relative
                        ));
                    });
        }

        return result;
    }

    private String computeAggregateHash(List<FileToImport> files) {
        try {
            List<FileToImport> sorted = files.stream()
                    .sorted(Comparator.comparing(FileToImport::relativePath))
                    .toList();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (FileToImport file : sorted) {
                digest.update(file.relativePath().getBytes(StandardCharsets.UTF_8));
                digest.update(computeSha256(file.path().toFile()).getBytes(StandardCharsets.UTF_8));
            }

            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de calculer l'empreinte du lot", e);
        }
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "unknown.log";
        }
        return Paths.get(name).getFileName().toString();
    }

    private UploadResult ingestOne(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new UploadResult(null, safeName(file), false, "Fichier vide.");
        }

        File tempFile = null;
        Path unzipDir = null;

        try {
            String uploadedName = safeName(file);

            tempFile = File.createTempFile("upload-", "-" + uploadedName);
            file.transferTo(tempFile);

            String fileHash = computeSha256(tempFile);
            List<FileToImport> filesToImport;

            if (isZipFile(uploadedName)) {
                unzipDir = Files.createTempDirectory("loganalyzer-zip-");
                filesToImport = unzipLogFiles(tempFile.toPath(), unzipDir);

                if (filesToImport.isEmpty()) {
                    throw new IllegalArgumentException("Le fichier ZIP ne contient aucun fichier .log ou .txt exploitable.");
                }
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

            File cleanupTempFile = tempFile;
            Path cleanupUnzipDir = unzipDir;

            return runImportJob(
                    uploadedName,
                    file.getOriginalFilename(),
                    file.getSize(),
                    fileHash,
                    filesToImport,
                    isZipFile(uploadedName) ? "ZIP importé" : null,
                    () -> {
                        if (cleanupTempFile != null && cleanupTempFile.exists() && !cleanupTempFile.delete()) {
                            log.warn("Impossible de supprimer le fichier temporaire {}", cleanupTempFile.getAbsolutePath());
                        }
                        if (cleanupUnzipDir != null) {
                            deleteDirectoryQuietly(cleanupUnzipDir);
                        }
                    }
            );

        } catch (Exception e) {
            log.error("Erreur globale pendant l'import du fichier {}", safeName(file), e);
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("Impossible de supprimer le fichier temporaire {}", tempFile.getAbsolutePath());
            }
            if (unzipDir != null) {
                deleteDirectoryQuietly(unzipDir);
            }
            return new UploadResult(
                    null,
                    safeName(file),
                    false,
                    "Erreur: " + e.getMessage()
            );
        }
    }

    private ImportCounters processFiles(LogImport logImport, List<FileToImport> filesToImport) throws IOException {
        ImportCounters counters = new ImportCounters();
        counters.totalFiles = filesToImport.size();

        boolean sourceMetadataInitialized = false;
        List<LogEntryRow> batch = new ArrayList<>(BATCH_SIZE);
        int fileIndex = 0;
        long linesInCurrentFile = 0L;

        for (FileToImport fileToImport : filesToImport) {
            fileIndex++;
            linesInCurrentFile = 0L;
            importProgressService.updateProgress(
                    logImport.getId(),
                    fileIndex,
                    filesToImport.size(),
                    fileToImport.relativePath(),
                    counters.totalLines,
                    linesInCurrentFile
            );

            log.info("Début traitement fichier interne importId={} file={}",
                    logImport.getId(), fileToImport.relativePath());

            try (BufferedReader br = new BufferedReader(new FileReader(fileToImport.path().toFile()), READER_BUFFER_SIZE)) {
                String line;

                while ((line = br.readLine()) != null) {
                    counters.totalLines++;
                    linesInCurrentFile++;

                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    if (linesInCurrentFile % PROGRESS_LINE_STEP == 0) {
                        importProgressService.updateLines(
                                logImport.getId(),
                                fileIndex,
                                filesToImport.size(),
                                fileToImport.relativePath(),
                                counters.totalLines,
                                counters.parsedOk,
                                linesInCurrentFile
                        );
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
                            counters.insertedRows += flushBatchSafely(batch, counters);
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
                importProgressService.updateProgress(
                        logImport.getId(),
                        fileIndex,
                        filesToImport.size(),
                        fileToImport.relativePath(),
                        counters.totalLines,
                        linesInCurrentFile
                );
            }
        }

        if (!batch.isEmpty()) {
            counters.insertedRows += flushBatchSafely(batch, counters);
            batch.clear();
        }

        return counters;
    }

    /**
     * Insère un lot dans une transaction séparée : si une ligne échoue, l'import
     * principal n'est pas avorté (évite le blocage "transaction is aborted").
     */
    private int flushBatchSafely(List<LogEntryRow> batch, ImportCounters counters) {
        if (batch.isEmpty()) return 0;
        List<LogEntryRow> copy = new ArrayList<>(batch);
        try {
            return self.insertBatchInNewTx(copy);
        } catch (Exception e) {
            log.warn("Échec lot ({} lignes), repli ligne-à-ligne : {}", copy.size(), e.getMessage());
            int ok = 0;
            for (LogEntryRow row : copy) {
                try {
                    ok += self.insertBatchInNewTx(List.of(row));
                } catch (Exception rowEx) {
                    counters.failed++;
                    log.warn("Ligne lot ignorée importId={} reason={}", row.importId(), rowEx.getMessage());
                }
            }
            return ok;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int insertBatchInNewTx(List<LogEntryRow> batch) {
        return insertBatch(batch);
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
        if (batch.isEmpty()) {
            return 0;
        }

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
                setLong(ps, 33, row.durationMs());
                setString(ps, 34, row.businessMeaning());
                setString(ps, 35, row.parseQuality());
                ps.setBoolean(36, row.ambiguousMessage());
                ps.setBoolean(37, row.incompleteLine());
                setTimestamp(ps, 38, row.createdAt());
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

        if (entryIds.size() != batch.size()) {
            throw new IllegalStateException(
                    "IDs log_entries incohérents après insertion : attendu=" + batch.size()
                            + ", obtenu=" + entryIds.size());
        }

        Collections.reverse(entryIds);

        jdbcTemplate.batchUpdate(INSERT_LOG_RAW_MESSAGE_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                LogEntryRow row = batch.get(i);
                ps.setLong(1, entryIds.get(i));
                setString(ps, 2, row.rawMessage());
                setString(ps, 3, row.messageTail());
                setString(ps, 4, row.technicalId());
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });

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
        Long durationMs = logPatternExtractor.extractDurationMsFromText(
                parsed.getMessage(),
                parsed.getRawLog(),
                parsed.getProcessName());

        return new LogEntryRow(
                importId,
                clip(sourceFileName, 500),
                clip(sourceRelativePath, 1000),
                parsed.getLogTimestamp(),
                clip(parsed.getExecutionId(), 255),
                clip(parsed.getLevel(), 20),
                clip(parsed.getUserName(), 255),
                clip(parsed.getSourceClass(), 500),
                clip(parsed.getProcessName(), 500),
                clip(parsed.getProcessName(), 500), // step_code (stocke le process WORKS)
                parsed.getLogCode(),
                clip(parsed.getEnvironment(), 255),
                clip(parsed.getServerName(), 255),
                clip(parsed.getAppVersion(), 255),
                clip(parsed.getCorrelationId(), 255),
                parsed.getMessage(),
                parsed.getRawLog(),
                clip(parsed.getEventType(), 255),
                clip(parsed.getFieldName(), 255),
                clip(parsed.getInterfaceField(), 255),
                clip(parsed.getFieldClassCode(), 255),
                clip(parsed.getParsedType(), 255),
                parsed.getParsedValue(),
                clip(parsed.getRelationName(), 255),
                clip(parsed.getRelationKey(), 255),
                clip(parsed.getBusinessKey(), 255),
                clip(parsed.getMandatoryField(), 255),
                clip(parsed.getErrorColumn(), 255),
                clip(parsed.getErrorAttribute(), 255),
                parsed.getErrorValue(),
                clip(parsed.getErrorBusinessKey(), 255),
                Boolean.TRUE.equals(parsed.getError()),
                durationMs,
                parsed.getBusinessMeaning(),
                clip(parsed.getParseQuality() != null ? parsed.getParseQuality().name() : null, 50),
                parsed.isAmbiguousMessage(),
                parsed.isIncomplete(),
                LocalDateTime.now(),
                parsed.getRawMessage(),
                parsed.getMessageTail(),
                parsed.getTechnicalId()
        );
    }

    /** Tronque pour respecter les colonnes VARCHAR et éviter d'avorter toute la transaction. */
    private static String clip(String value, int max) {
        if (value == null) return null;
        if (value.length() <= max) return value;
        return value.substring(0, max);
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

    private void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
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

    public record UploadResult(Long importId, String fileName, boolean success, String message, boolean processing) {
        public UploadResult(Long importId, String fileName, boolean success, String message) {
            this(importId, fileName, success, message, false);
        }
    }

    public record UploadSessionDto(String sessionId, int fileCount) {}

    private static final class UploadSession {
        private final Path dir;
        private final List<FileToImport> files = Collections.synchronizedList(new ArrayList<>());
        private long totalSize;

        private UploadSession(Path dir) {
            this.dir = dir;
        }
    }

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
            Long durationMs,
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