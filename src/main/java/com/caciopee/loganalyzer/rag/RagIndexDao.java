package com.caciopee.loganalyzer.rag;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès base pour la table {@code log_chunk_embedding} (pgvector).
 *
 * <p>On utilise JdbcTemplate + SQL natif car pgvector n'est pas un type JPA standard.
 * Le vecteur est passé sous forme de littéral {@code '[...]'::vector}.
 */
@Repository
public class RagIndexDao {

    private final JdbcTemplate jdbc;

    public RagIndexDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Résultat d'une recherche : le chunk + son score de similarité (0..1, plus haut = plus proche). */
    public record ScoredChunk(
            long id, long importId, String sessionId, String processName, String filterCode,
            String userName, boolean hasError, Long maxDurationMs,
            Timestamp firstTs, Timestamp lastTs, Long firstLogId, Long lastLogId,
            int lineCount, String content, double similarity) { }

    public long countByImport(Long importId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM log_chunk_embedding WHERE import_id = ?",
                Long.class, importId);
        return n == null ? 0 : n;
    }

    public void deleteByImport(Long importId) {
        jdbc.update("DELETE FROM log_chunk_embedding WHERE import_id = ?", importId);
    }

    /** Insère un lot de chunks avec leurs vecteurs. */
    public void insertBatch(List<LogChunk> chunks, List<String> vectorLiterals) {
        String sql = """
                INSERT INTO log_chunk_embedding
                  (import_id, session_id, process_name, filter_code, user_name,
                   has_error, max_duration_ms, first_ts, last_ts, first_log_id, last_log_id,
                   line_count, content, embedding)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, ?::vector)
                """;
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                LogChunk c = chunks.get(i);
                ps.setLong(1, c.importId());
                ps.setString(2, c.sessionId());
                ps.setString(3, c.processName());
                ps.setString(4, c.filterCode());
                ps.setString(5, c.userName());
                ps.setBoolean(6, c.hasError());
                if (c.maxDurationMs() != null) ps.setLong(7, c.maxDurationMs()); else ps.setNull(7, java.sql.Types.BIGINT);
                ps.setTimestamp(8, c.firstTs() != null ? Timestamp.valueOf(c.firstTs()) : null);
                ps.setTimestamp(9, c.lastTs() != null ? Timestamp.valueOf(c.lastTs()) : null);
                if (c.firstLogId() != null) ps.setLong(10, c.firstLogId()); else ps.setNull(10, java.sql.Types.BIGINT);
                if (c.lastLogId() != null) ps.setLong(11, c.lastLogId()); else ps.setNull(11, java.sql.Types.BIGINT);
                ps.setInt(12, c.lineCount());
                ps.setString(13, c.content());
                ps.setString(14, vectorLiterals.get(i));
            }
            @Override public int getBatchSize() { return chunks.size(); }
        });
    }

    /**
     * Recherche hybride : similarité cosinus (pgvector {@code <=>}) + filtres SQL optionnels.
     *
     * @param queryVector littéral vecteur de la question
     * @param importId    obligatoire (on cherche dans un import précis)
     * @param onlyErrors  ne garder que les fenêtres contenant une erreur
     * @param minDuration ne garder que les fenêtres dont la durée max >= minDuration (ms), ou null
     * @param sessionId   restreindre à une session, ou null
     * @param topK        nombre de résultats
     */
    public List<ScoredChunk> search(String queryVector, Long importId, boolean onlyErrors,
                                    Long minDuration, String sessionId, int topK) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, import_id, session_id, process_name, filter_code, user_name,
                       has_error, max_duration_ms, first_ts, last_ts, first_log_id, last_log_id,
                       line_count, content,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM log_chunk_embedding
                WHERE import_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(queryVector); // pour le SELECT similarity
        args.add(importId);

        if (onlyErrors) {
            sql.append(" AND has_error = TRUE");
        }
        if (minDuration != null) {
            sql.append(" AND max_duration_ms >= ?");
            args.add(minDuration);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            sql.append(" AND session_id = ?");
            args.add(sessionId);
        }
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        args.add(queryVector); // pour le ORDER BY
        args.add(topK);

        return jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    private static final RowMapper<ScoredChunk> ROW_MAPPER = (rs, n) -> new ScoredChunk(
            rs.getLong("id"),
            rs.getLong("import_id"),
            rs.getString("session_id"),
            rs.getString("process_name"),
            rs.getString("filter_code"),
            rs.getString("user_name"),
            rs.getBoolean("has_error"),
            (Long) rs.getObject("max_duration_ms"),
            rs.getTimestamp("first_ts"),
            rs.getTimestamp("last_ts"),
            (Long) rs.getObject("first_log_id"),
            (Long) rs.getObject("last_log_id"),
            rs.getInt("line_count"),
            rs.getString("content"),
            rs.getDouble("similarity")
    );
}
