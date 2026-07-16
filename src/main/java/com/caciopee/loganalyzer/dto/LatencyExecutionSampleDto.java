package com.caciopee.loganalyzer.dto;

/**
 * Un échantillon d'exécution de la MÊME opération métier, ailleurs dans l'import.
 *
 * <p>Sert à la preuve par comparaison : si la même opération (même processus + action)
 * tourne parfois en 1,5 s et parfois en 47 s <b>avec le même SQL rapide</b>, alors la
 * lenteur ne vient ni des données ni de la requête, mais d'un facteur intermittent
 * (appel externe, verrou, GC…). C'est l'argument le plus fort pour disculper le SQL.</p>
 */
public class LatencyExecutionSampleDto {

    /** Heure de l'exécution (HH:mm:ss). */
    private String time;

    /** Durée de la recherche SQL (searchComposantByRoot) associée, en ms. */
    private Long sqlMs;

    /** Nombre de lignes retournées par la recherche (souvent 0 ou 1). */
    private Integer rowCount;

    /** Durée du moteur de règles (running rules) pour cette exécution, en ms. */
    private Long rulesMs;

    /** Durée totale mesurée de l'opération (took), en ms. */
    private Long totalMs;

    /** {@code true} si c'est l'exécution actuellement analysée (la lente). */
    private boolean current;

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public Long getSqlMs() { return sqlMs; }
    public void setSqlMs(Long sqlMs) { this.sqlMs = sqlMs; }

    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }

    public Long getRulesMs() { return rulesMs; }
    public void setRulesMs(Long rulesMs) { this.rulesMs = rulesMs; }

    public Long getTotalMs() { return totalMs; }
    public void setTotalMs(Long totalMs) { this.totalMs = totalMs; }

    public boolean isCurrent() { return current; }
    public void setCurrent(boolean current) { this.current = current; }
}
