-- ============================================================================
--  Schéma RAG (pgvector) pour Log Analyzer
--  À exécuter UNE FOIS sur la base logs_db (psql -U postgres -d logs_db -f rag-schema.sql)
--
--  Prérequis : l'extension pgvector doit être installée sur le serveur PostgreSQL.
--  (Windows : la trouve dans le dossier /extension de l'install PostgreSQL, ou via
--   les binaires pgvector ; puis CREATE EXTENSION ci-dessous.)
-- ============================================================================

-- 1) Activer pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- 2) Table des fenêtres de logs vectorisées
--    IMPORTANT : vector(768) correspond au modèle nomic-embed-text.
--    Si tu changes de modèle d'embeddings, adapte la dimension ICI et dans app.rag.dimension.
CREATE TABLE IF NOT EXISTS log_chunk_embedding (
    id              BIGSERIAL PRIMARY KEY,
    import_id       BIGINT      NOT NULL,
    session_id      VARCHAR(100),
    process_name    VARCHAR(500),
    filter_code     VARCHAR(255),
    user_name       VARCHAR(255),
    has_error       BOOLEAN     NOT NULL DEFAULT FALSE,
    max_duration_ms BIGINT,
    first_ts        TIMESTAMP,
    last_ts         TIMESTAMP,
    first_log_id    BIGINT,
    last_log_id     BIGINT,
    line_count      INTEGER     NOT NULL DEFAULT 0,
    content         TEXT        NOT NULL,
    embedding       vector(768) NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);

-- 3) Index de recherche vectorielle (cosinus).
--    HNSW = rapide et précis (pgvector >= 0.5.0). Si ta version est plus ancienne,
--    remplace par la variante IVFFLAT plus bas.
CREATE INDEX IF NOT EXISTS idx_lce_embedding_hnsw
    ON log_chunk_embedding USING hnsw (embedding vector_cosine_ops);

-- Variante si HNSW indisponible (pgvector < 0.5) :
-- CREATE INDEX IF NOT EXISTS idx_lce_embedding_ivff
--     ON log_chunk_embedding USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 4) Index pour les filtres SQL (recherche hybride)
CREATE INDEX IF NOT EXISTS idx_lce_import      ON log_chunk_embedding (import_id);
CREATE INDEX IF NOT EXISTS idx_lce_import_err  ON log_chunk_embedding (import_id, has_error);
CREATE INDEX IF NOT EXISTS idx_lce_session     ON log_chunk_embedding (import_id, session_id);
