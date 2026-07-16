package com.caciopee.loganalyzer.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Paramètres du module RAG (Retrieval-Augmented Generation).
 *
 * <p>Le RAG permet de répondre à une question sur un import en ne donnant au LLM
 * QUE les vraies lignes de logs pertinentes (retrouvées par recherche vectorielle),
 * au lieu de tronquer tout l'import. C'est ce qui rend les réponses justes.
 *
 * <p>Préfixe de config : {@code app.rag.*} (voir application-ollama.properties).
 */
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    /** Active le module RAG (indexation + recherche). */
    private boolean enabled = false;

    /** Modèle d'embeddings Ollama. nomic-embed-text = 768 dimensions, léger (~275 Mo). */
    private String embeddingModel = "nomic-embed-text";

    /**
     * URL de base de l'API d'embeddings (compatible OpenAI). Par défaut identique à
     * celle du LLM. Ollama : http://localhost:11434/v1
     */
    private String embeddingBaseUrl = "http://localhost:11434/v1";

    /** Clé API si l'endpoint d'embeddings en requiert une (vide pour Ollama). */
    private String embeddingApiKey = "";

    /**
     * Dimension du vecteur produit par le modèle. DOIT correspondre à la colonne
     * vector(N) dans rag-schema.sql. nomic-embed-text = 768.
     */
    private int dimension = 768;

    /**
     * Préfixes recommandés par nomic-embed-text : ils améliorent nettement la
     * pertinence de la recherche. Mettre à vide pour un autre modèle qui n'en veut pas.
     */
    private String documentPrefix = "search_document: ";
    private String queryPrefix = "search_query: ";

    /** Nombre de lignes de logs par chunk (fenêtre). */
    private int chunkSize = 20;

    /** Chevauchement entre deux fenêtres consécutives (pour ne pas couper un contexte). */
    private int chunkOverlap = 4;

    /** Nombre de chunks (sources) renvoyés au LLM pour répondre. */
    private int topK = 8;

    /** Plafond de chunks indexés par import (protège la machine sur un gros import). */
    private int maxChunksPerImport = 20000;

    /**
     * Mode d'indexation :
     *  - "smart"  : indexe les fenêtres "intéressantes" (erreur, lenteur, warning) +
     *               1 fenêtre "normale" sur N (échantillonnage). Recommandé.
     *  - "all"    : indexe toutes les fenêtres (plus lourd, plus lent).
     */
    private String indexMode = "smart";

    /** En mode "smart", on garde 1 fenêtre "normale" (sans incident) sur N. */
    private int keepEveryNthNormal = 6;

    /** Seuil (ms) au-delà duquel une fenêtre est considérée "lente" donc intéressante. */
    private long slowThresholdMs = 2000;

    /** Taille des lots pour l'insertion en base. */
    private int insertBatchSize = 100;

    // ── getters / setters ────────────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public String getEmbeddingBaseUrl() { return embeddingBaseUrl; }
    public void setEmbeddingBaseUrl(String embeddingBaseUrl) { this.embeddingBaseUrl = embeddingBaseUrl; }

    public String getEmbeddingApiKey() { return embeddingApiKey; }
    public void setEmbeddingApiKey(String embeddingApiKey) { this.embeddingApiKey = embeddingApiKey; }

    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }

    public String getDocumentPrefix() { return documentPrefix; }
    public void setDocumentPrefix(String documentPrefix) { this.documentPrefix = documentPrefix; }

    public String getQueryPrefix() { return queryPrefix; }
    public void setQueryPrefix(String queryPrefix) { this.queryPrefix = queryPrefix; }

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }

    public int getMaxChunksPerImport() { return maxChunksPerImport; }
    public void setMaxChunksPerImport(int maxChunksPerImport) { this.maxChunksPerImport = maxChunksPerImport; }

    public String getIndexMode() { return indexMode; }
    public void setIndexMode(String indexMode) { this.indexMode = indexMode; }

    public int getKeepEveryNthNormal() { return keepEveryNthNormal; }
    public void setKeepEveryNthNormal(int keepEveryNthNormal) { this.keepEveryNthNormal = keepEveryNthNormal; }

    public long getSlowThresholdMs() { return slowThresholdMs; }
    public void setSlowThresholdMs(long slowThresholdMs) { this.slowThresholdMs = slowThresholdMs; }

    public int getInsertBatchSize() { return insertBatchSize; }
    public void setInsertBatchSize(int insertBatchSize) { this.insertBatchSize = insertBatchSize; }

    public boolean hasEmbeddingApiKey() {
        return embeddingApiKey != null && !embeddingApiKey.isBlank();
    }
}
