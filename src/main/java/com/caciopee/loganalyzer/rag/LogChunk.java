package com.caciopee.loganalyzer.rag;

import java.time.LocalDateTime;

/**
 * Une "fenêtre" de logs contigus (même session, ordonnés dans le temps), enrichie
 * de métadonnées. C'est l'unité qu'on embed et qu'on stocke. Les métadonnées
 * permettent la recherche HYBRIDE : vecteur (sens) + filtres SQL (session, erreur, durée).
 */
public record LogChunk(
        Long importId,
        String sessionId,
        String processName,
        String filterCode,
        String userName,
        boolean hasError,
        Long maxDurationMs,
        LocalDateTime firstTs,
        LocalDateTime lastTs,
        Long firstLogId,
        Long lastLogId,
        int lineCount,
        String content
) {
    /** Une fenêtre est "intéressante" si elle contient une erreur ou une lenteur notable. */
    public boolean isInteresting(long slowThresholdMs) {
        return hasError || (maxDurationMs != null && maxDurationMs >= slowThresholdMs);
    }
}
