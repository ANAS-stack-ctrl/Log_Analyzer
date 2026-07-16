package com.caciopee.loganalyzer.rag;

import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.rag.dto.RagIndexStatusDto;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestre l'indexation d'un import : charge les logs, les découpe en chunks,
 * calcule les embeddings, et les stocke dans pgvector.
 *
 * <p>L'indexation tourne en tâche de fond (un seul thread : Ollama traite une
 * requête d'embedding à la fois sur petit GPU). La progression est consultable
 * via {@link #getStatus(Long)}.
 */
@Service
public class LogEmbeddingIndexService {

    private static final Logger log = LoggerFactory.getLogger(LogEmbeddingIndexService.class);

    private final RagProperties props;
    private final LogEntryRepository logEntryRepository;
    private final LogChunkingService chunkingService;
    private final EmbeddingClient embeddingClient;
    private final RagIndexDao dao;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rag-indexer");
        t.setDaemon(true);
        return t;
    });

    /** État d'indexation par import (en mémoire). */
    private final ConcurrentHashMap<Long, RagIndexStatusDto> statuses = new ConcurrentHashMap<>();

    public LogEmbeddingIndexService(RagProperties props,
                                    LogEntryRepository logEntryRepository,
                                    LogChunkingService chunkingService,
                                    EmbeddingClient embeddingClient,
                                    RagIndexDao dao) {
        this.props = props;
        this.logEntryRepository = logEntryRepository;
        this.chunkingService = chunkingService;
        this.embeddingClient = embeddingClient;
        this.dao = dao;
    }

    /** Lance (ou relance) l'indexation d'un import en tâche de fond. */
    public RagIndexStatusDto startIndexing(Long importId, boolean reindex) {
        RagIndexStatusDto existing = statuses.get(importId);
        if (existing != null && "RUNNING".equals(existing.getState())) {
            return existing; // déjà en cours
        }
        if (!reindex && dao.countByImport(importId) > 0) {
            RagIndexStatusDto done = new RagIndexStatusDto(importId, "DONE");
            done.setIndexedChunks((int) dao.countByImport(importId));
            done.setMessage("Déjà indexé. Utilise reindex=true pour reconstruire.");
            statuses.put(importId, done);
            return done;
        }

        RagIndexStatusDto status = new RagIndexStatusDto(importId, "RUNNING");
        statuses.put(importId, status);
        executor.submit(() -> runIndexing(importId, status));
        return status;
    }

    public RagIndexStatusDto getStatus(Long importId) {
        RagIndexStatusDto s = statuses.get(importId);
        if (s != null) return s;
        long count = dao.countByImport(importId);
        RagIndexStatusDto out = new RagIndexStatusDto(importId, count > 0 ? "DONE" : "NONE");
        out.setIndexedChunks((int) count);
        return out;
    }

    public boolean isIndexed(Long importId) {
        return dao.countByImport(importId) > 0;
    }

    public void deleteIndex(Long importId) {
        dao.deleteByImport(importId);
        statuses.remove(importId);
    }

    // ── coeur ─────────────────────────────────────────────────────────────────

    private void runIndexing(Long importId, RagIndexStatusDto status) {
        try {
            log.info("[RAG] Indexation import {} — chargement des logs…", importId);
            long total = logEntryRepository.countByLogImportId(importId);
            if (total == 0) {
                status.setState("DONE");
                status.setMessage("Aucun log pour cet import.");
                return;
            }
            if (total > 40_000) {
                status.setState("FAILED");
                status.setMessage("Import trop volumineux (" + total
                        + " lignes) pour l'indexation RAG en une fois. Importez un sous-ensemble (< 40k lignes).");
                return;
            }
            List<LogEntry> logs = logEntryRepository.findByLogImportIdOrderByLogTimestampAscIdAsc(
                    importId, org.springframework.data.domain.PageRequest.of(0, (int) total));
            if (logs.isEmpty()) {
                status.setState("DONE");
                status.setMessage("Aucun log pour cet import.");
                return;
            }

            dao.deleteByImport(importId); // repart propre

            List<LogChunk> allChunks = chunkingService.chunk(importId, logs);
            List<LogChunk> selected = selectChunks(allChunks);
            status.setTotalChunks(selected.size());
            log.info("[RAG] Import {} : {} fenêtres, {} sélectionnées à indexer.",
                    importId, allChunks.size(), selected.size());

            List<LogChunk> buffer = new ArrayList<>();
            List<String> vectors = new ArrayList<>();
            int done = 0;

            for (LogChunk chunk : selected) {
                float[] vec = embeddingClient.embedDocument(chunk.content());
                buffer.add(chunk);
                vectors.add(EmbeddingClient.toVectorLiteral(vec));

                if (buffer.size() >= props.getInsertBatchSize()) {
                    dao.insertBatch(buffer, vectors);
                    done += buffer.size();
                    status.setIndexedChunks(done);
                    buffer.clear();
                    vectors.clear();
                }
            }
            if (!buffer.isEmpty()) {
                dao.insertBatch(buffer, vectors);
                done += buffer.size();
                status.setIndexedChunks(done);
            }

            status.setState("DONE");
            status.setMessage("Indexation terminée : " + done + " fenêtres.");
            log.info("[RAG] Import {} indexé : {} fenêtres.", importId, done);
        } catch (Exception e) {
            log.error("[RAG] Échec indexation import {} : {}", importId, e.getMessage(), e);
            status.setState("FAILED");
            status.setMessage("Erreur : " + e.getMessage());
        }
    }

    /**
     * En mode "smart" : garde toutes les fenêtres intéressantes (erreur/lenteur)
     * + 1 fenêtre "normale" sur N. Respecte le plafond maxChunksPerImport.
     */
    private List<LogChunk> selectChunks(List<LogChunk> chunks) {
        boolean smart = "smart".equalsIgnoreCase(props.getIndexMode());
        List<LogChunk> out = new ArrayList<>();
        int normalCounter = 0;
        for (LogChunk c : chunks) {
            boolean keep;
            if (!smart) {
                keep = true;
            } else if (c.isInteresting(props.getSlowThresholdMs())) {
                keep = true;
            } else {
                keep = (normalCounter % Math.max(1, props.getKeepEveryNthNormal())) == 0;
                normalCounter++;
            }
            if (keep) {
                out.add(c);
                if (out.size() >= props.getMaxChunksPerImport()) break;
            }
        }
        return out;
    }
}
