package com.caciopee.loganalyzer.dto;

/**
 * Regroupement d'exécutions SQL répétées (même filtre/règle) observées dans la
 * fenêtre d'analyse. Permet d'expliquer où le temps d'une opération lente (ex.
 * moteur de règles) est réellement consommé : un contrôle métier exécutant la
 * même requête des dizaines de fois.
 */
public class LatencyQueryGroupDto {

    private String filterCode;
    private int count;
    private long totalMs;
    private long maxMs;

    public LatencyQueryGroupDto() {
    }

    public LatencyQueryGroupDto(String filterCode, int count, long totalMs, long maxMs) {
        this.filterCode = filterCode;
        this.count = count;
        this.totalMs = totalMs;
        this.maxMs = maxMs;
    }

    public String getFilterCode() { return filterCode; }
    public void setFilterCode(String filterCode) { this.filterCode = filterCode; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public long getTotalMs() { return totalMs; }
    public void setTotalMs(long totalMs) { this.totalMs = totalMs; }

    public long getMaxMs() { return maxMs; }
    public void setMaxMs(long maxMs) { this.maxMs = maxMs; }
}
