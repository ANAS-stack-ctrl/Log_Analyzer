package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Requête de navigation cumulative : on retourne tous les logs d'un import qui
 * satisfont EN MÊME TEMPS (ET logique) l'ensemble des critères cliqués par
 * l'utilisateur (utilisateur, session, process, filtre, action, objet, uuid, écran).
 * L'ordre des critères correspond au fil d'Ariane construit par l'utilisateur.
 */
public class CumulativeLogFilterRequestDto {

    private Long importId;
    private List<Criterion> criteria = new ArrayList<>();

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }

    public List<Criterion> getCriteria() { return criteria; }
    public void setCriteria(List<Criterion> criteria) { this.criteria = criteria; }

    /** Un maillon du fil d'Ariane : un type (USER, SESSION, PROCESS, …) et sa valeur. */
    public static class Criterion {
        private String type;
        private String value;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
