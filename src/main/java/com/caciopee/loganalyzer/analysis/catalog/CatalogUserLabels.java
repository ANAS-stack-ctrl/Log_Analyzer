package com.caciopee.loganalyzer.analysis.catalog;

import java.util.Locale;
import java.util.Map;

/**
 * Libellés affichés dans l'interface (graphe, audit) — sans codes techniques MET-xxx / PROC-xxx.
 */
public final class CatalogUserLabels {

    private CatalogUserLabels() {
    }

    private static final Map<String, String> FAMILY_LABELS = Map.ofEntries(
            Map.entry("PROC-AMPE-CTRL", "Contrôle exploitation AMPE"),
            Map.entry("PROC-MATCX", "Processus MATCX / ZRE AMP"),
            Map.entry("PROC-SUP-TRACTION", "Supervision ordre de traction"),
            Map.entry("PROC-TRACTION-DMAFI", "Traction interne DMAFI"),
            Map.entry("PROC-TRACTION-EXT-DEMAT", "Traction externe AMP (démat)"),
            Map.entry("PROC-TRACTION-EXT", "Traction externe sur AMP"),
            Map.entry("PROC-PESAGE-DEMAT", "Supervision pesage démat"),
            Map.entry("PROC-SORTIE-MEDHUB-PRINT", "Impression sortie MEDHUB"),
            Map.entry("PROC-MEDHUB", "Entrée ou sortie MEDHUB"),
            Map.entry("PROC-AMPI", "AMPI / liaison BAD"),
            Map.entry("PROC-AMPE-SUP", "Supervision AMPE"),
            Map.entry("PROC-SZVCIZRDV", "Processus SZVC / rendez-vous"),
            Map.entry("PROC-OTRIMAFI", "Processus OTRIMAFI"),
            Map.entry("PROC-RAPPORT-AMP", "Rapport de contrôle AMP"),
            Map.entry("PROC-DEMAT-EXPORT", "Export traction démat"),
            Map.entry("PROC-AMPE", "Processus AMPE"),
            Map.entry("PROC-CHANGE-AMPE", "Changement AMPE (CHANGeAMPE)"),
            Map.entry("PROC-DOT", "Autre processus métier"),

            Map.entry("L1-PF", "Écran filtre WORKS"),
            Map.entry("L1-PW", "Web service WORKS"),
            Map.entry("L1-PRR", "Exécution de règles métier"),
            Map.entry("L1-PDOC", "Impression document WORKS"),
            Map.entry("PROC-SAVE", "Sauvegarde WORKS (SAVE)"),

            Map.entry("MET-040", "Archivage / impression document"),
            Map.entry("MET-041", "Erreur JDBC / Hibernate"),
            Map.entry("MET-042", "Exécution recherche (doSearch)"),
            Map.entry("MET-043", "Rendu résultat écran"),
            Map.entry("MET-044", "Exécution règle métier"),
            Map.entry("MET-045", "Début / fin requête filtre"),
            Map.entry("MET-046", "PreparedStatement filtre"),
            Map.entry("MET-047", "Multithreading recherche"),
            Map.entry("MET-048", "Lignes récupérées"),
            Map.entry("MET-049", "Recherche globale composant"),
            Map.entry("MET-050", "Requête SQL (Query:)"),
            Map.entry("MET-051", "Thread parallèle terminé"),
            Map.entry("MET-052", "Trace SAVE métier"),
            Map.entry("MET-053", "Tryptique unité"),
            Map.entry("MET-054", "Message JSON ampScanner"),
            Map.entry("MET-055", "MAJ trace / scanner AMPE"),
            Map.entry("MET-056", "Règle sélection AMPE"),
            Map.entry("MET-057", "MAJ DynaScreen / démat"),
            Map.entry("MET-058", "Conversion / impression CUPS"),
            Map.entry("MET-059", "Statut job impression"),
            Map.entry("MET-060", "Stack trace Java"),
            Map.entry("MET-061", "VirtualHost / routage"),
            Map.entry("MET-062", "Chargement personne"),
            Map.entry("MET-063", "Étanchéité / PM"),
            Map.entry("MET-064", "Portail / login"),
            Map.entry("MET-065", "Cache serveur (init / ajout / mise à jour)"),
            Map.entry("MET-069", "Planification automatique"),
            Map.entry("L1-INIT", "Initialisation WORKS"),
            Map.entry("MET-066", "Lenteur événement ZKoss"),
            Map.entry("MET-067", "Mapping listbox"),
            Map.entry("WS-009", "Web service traçabilité"),
            Map.entry("WS-010", "Appel WS distant"),
            Map.entry("WS-011", "SaveOrUpdate WS"),
            Map.entry("WS-012", "Workflow douane / DUM"),
            Map.entry("WS-013", "Étape traçabilité WS"),
            Map.entry("WS-014", "Debug paramètres WS"),
            Map.entry("MET-068", "Démarrage doSearch"),
            Map.entry("MET-031", "Recherche composants (multithread)"),
            Map.entry("MET-032", "Requête SQL temporaire"),
            Map.entry("MET-033", "Composants temporaires chargés"),
            Map.entry("MET-034", "Transition sans règle affectée"),
            Map.entry("MET-035", "Agrégation des données filtre"),
            Map.entry("MET-036", "Sauvegarde relations composants"),
            Map.entry("MET-037", "Durée totale enregistrement"),
            Map.entry("MET-038", "Règles WS appelées à distance"),
            Map.entry("MET-039", "Écran en mode vue (composer)"),

            Map.entry("WS-005", "Étape visuelle workflow WS"),
            Map.entry("WS-006", "Fin d'étape filtre WS"),
            Map.entry("WS-004", "Démarrage web service"),
            Map.entry("WS-007", "Début traitement web service"),
            Map.entry("WS-008", "Recherche traçabilité position"),
            Map.entry("WS-003", "Début ou fin de filtre WS"),
            Map.entry("WS-002", "Web service sans règle"),

            Map.entry("TOM-012", "Structuration d'un champ métier"),
            Map.entry("TOM-002", "Début méthode structure données"),
            Map.entry("TOM-011", "Fin méthode structure données"),
            Map.entry("TOM-004", "Valeur métier enregistrée (clé)"),
            Map.entry("TOM-003", "Champ works / interface"),
            Map.entry("TOM-005", "Champ métier (field)"),
            Map.entry("TOM-006", "Code classe de champ"),
            Map.entry("TOM-007", "Relation métier"),
            Map.entry("TOM-008", "Création objet interface"),
            Map.entry("TOM-009", "Lecture valeur interface"),
            Map.entry("TOM-010", "Enregistrement en base"),
            Map.entry("TOM-013", "Normalisation texte (trim)"),
            Map.entry("TOM-014", "Attribut technique TYPE_ATTR"),
            Map.entry("TOM-001", "Étape structure données"),

            Map.entry("MET-013", "Début exécution des règles"),
            Map.entry("MET-014", "Fin exécution des règles"),
            Map.entry("MET-009", "Exécution règles (ligne pipe)"),
            Map.entry("MET-021", "Aucune règle trouvée"),
            Map.entry("MET-022", "Action métier (doAction)"),
            Map.entry("MET-019", "Paramètres transition / tâche"),
            Map.entry("MET-008", "Processus, tâche et action déclarés"),
            Map.entry("MET-018", "Alerte processus / action"),

            Map.entry("MET-011", "Recherche composant par racine"),
            Map.entry("MET-012", "Préparation de la recherche"),
            Map.entry("MET-007", "Liste d'attributs de recherche"),
            Map.entry("MET-015", "Exécution d'un filtre"),
            Map.entry("MET-017", "Chargement liste enfants"),
            Map.entry("MET-002", "Chargement d'un filtre"),
            Map.entry("MET-016", "Requête SQL ([Query])"),
            Map.entry("MET-005", "Recherche sans résultat (0 ligne)"),
            Map.entry("MET-003", "Classe métier ciblée"),
            Map.entry("MET-004", "Classe métier (texte libre)"),
            Map.entry("MET-001", "Filtre métier appliqué"),
            Map.entry("MET-024", "Identifiant technique (UUID)"),

            Map.entry("MET-010", "Consommation mémoire JVM"),
            Map.entry("MET-006", "Durée d'exécution"),
            Map.entry("EXT-003", "Consommation mémoire"),
            Map.entry("MET-029", "Point de contrôle métier"),
            Map.entry("MET-030", "Tâche planifiée (trigger)"),
            Map.entry("MET-027", "Donnée métier (noDUM, référence…)"),
            Map.entry("MET-028", "Paramètre de requête écran"),
            Map.entry("MET-025", "Identifiant de transaction"),
            Map.entry("MET-026", "Fil d'exécution (thread)"),
            Map.entry("EXT-004", "Journal saveLoad")
    );

    private static final Map<String, String> GROUP_LABELS = Map.ofEntries(
            Map.entry("processus-metier", "Processus métier"),
            Map.entry("processus-l1", "Processus technique WORKS"),
            Map.entry("filtre-recherche", "Filtre & recherche"),
            Map.entry("regles", "Règles métier"),
            Map.entry("webservice", "Web services"),
            Map.entry("tomcat", "Structure des données"),
            Map.entry("sauvegarde", "Sauvegarde"),
            Map.entry("performance", "Performance"),
            Map.entry("batch", "Traitement planifié"),
            Map.entry("donnees-metier", "Données métier"),
            Map.entry("infra", "Infrastructure"),
            Map.entry("impression", "Impression"),
            Map.entry("commun", "Commun")
    );

    public static String displayLabel(WorksMessageFamily family) {
        if (family == null) {
            return "";
        }
        if (family.getUserLabel() != null && !family.getUserLabel().isBlank()) {
            return family.getUserLabel().trim();
        }
        String fromMap = FAMILY_LABELS.get(family.getId());
        if (fromMap != null) {
            return fromMap;
        }
        if (family.getLabel() != null && !family.getLabel().isBlank()) {
            return family.getLabel().trim();
        }
        return family.getId() != null ? family.getId() : "Famille inconnue";
    }

    public static String groupLabel(String group) {
        if (group == null || group.isBlank()) {
            return "";
        }
        return GROUP_LABELS.getOrDefault(group.trim(),
                group.replace('-', ' ').substring(0, 1).toUpperCase(Locale.ROOT)
                        + group.replace('-', ' ').substring(1));
    }

    public static String graphDescription(WorksMessageFamily family) {
        if (family == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String gl = groupLabel(family.getGroup());
        if (!gl.isBlank()) {
            sb.append("Catégorie : ").append(gl).append(". ");
        }
        if (family.getLabel() != null && !family.getLabel().isBlank()) {
            sb.append(family.getLabel()).append(" ");
        }
        sb.append("(réf. ").append(family.getId()).append("). ");
        if (family.getExtractable() != null && !family.getExtractable().isEmpty()) {
            sb.append("Informations extraites : ")
                    .append(String.join(", ", family.getExtractable()))
                    .append(".");
        }
        return sb.toString().trim();
    }
}
