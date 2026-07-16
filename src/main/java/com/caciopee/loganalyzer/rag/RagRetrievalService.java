package com.caciopee.loganalyzer.rag;

import com.caciopee.loganalyzer.rag.dto.RagAskRequest;
import com.caciopee.loganalyzer.rag.dto.RagSourceDto;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Retrouve les fenêtres de logs les plus pertinentes pour une question.
 * = le "R" de RAG (Retrieval). C'est cette étape qui garantit qu'on ne donne au
 * LLM que des faits réels, ciblés sur la question.
 */
@Service
public class RagRetrievalService {

    private final EmbeddingClient embeddingClient;
    private final RagIndexDao dao;
    private final RagProperties props;

    public RagRetrievalService(EmbeddingClient embeddingClient, RagIndexDao dao, RagProperties props) {
        this.embeddingClient = embeddingClient;
        this.dao = dao;
        this.props = props;
    }

    public List<RagSourceDto> retrieve(RagAskRequest request) {
        float[] qVec = embeddingClient.embedQuery(request.getQuestion());
        String literal = EmbeddingClient.toVectorLiteral(qVec);

        List<RagIndexDao.ScoredChunk> hits = dao.search(
                literal,
                request.getImportId(),
                Boolean.TRUE.equals(request.getOnlyErrors()),
                request.getMinDurationMs(),
                request.getSessionId(),
                props.getTopK()
        );

        List<RagSourceDto> sources = new ArrayList<>(hits.size());
        int i = 1;
        for (RagIndexDao.ScoredChunk h : hits) {
            RagSourceDto s = new RagSourceDto();
            s.setRef("S" + i++);
            s.setImportId(h.importId());
            s.setSessionId(h.sessionId());
            s.setProcessName(h.processName());
            s.setFilterCode(h.filterCode());
            s.setUserName(h.userName());
            s.setHasError(h.hasError());
            s.setMaxDurationMs(h.maxDurationMs());
            s.setFirstTs(fmt(h.firstTs()));
            s.setLastTs(fmt(h.lastTs()));
            s.setFirstLogId(h.firstLogId());
            s.setLastLogId(h.lastLogId());
            s.setLineCount(h.lineCount());
            s.setSimilarity(round(h.similarity()));
            s.setContent(h.content());
            sources.add(s);
        }
        return sources;
    }

    private static String fmt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime().toString().replace('T', ' ');
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
