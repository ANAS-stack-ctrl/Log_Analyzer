package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;
import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies a slow latency into one of 8 root-cause categories, validated on
 * a real WORKS dataset of 274k log lines (1493 slow tooks, 99.1% coverage).
 *
 * <p>Categories (in order of decision priority):
 * <ol>
 *   <li>MEASUREMENT_ANOMALY — implausible duration (e.g. 14 min took), measurement bug</li>
 *   <li>PARALLEL_MULTITHREADED — operation dispatched on multiple threads (partitional/Multithreading marker)</li>
 *   <li>RULES_REPLAYED — same filter replays the same SQL N+ times in the cause window</li>
 *   <li>SAVE_INSERT_DOMINATED — dominant sub-took is an insertArrayRel</li>
 *   <li>SQL_DOMINATED — dominant sub-took is a Query Select / [Query]</li>
 *   <li>LARGE_DATA_LOAD — dominant or anchor matches loadListChilds [...] size [N]</li>
 *   <li>ATOMIC_SQL_LEAF — anchor IS the SQL query, no sub-tooks</li>
 *   <li>SAVE_DIFFUSE — save anchor with no clear dominant insert (small ops cumulated)</li>
 *   <li>SEARCH_DIFFUSE / UNKNOWN — fallbacks</li>
 * </ol>
 */
@Component
public class LatencyRootCauseClassifier {

    // ─────────────────────────────────────────────────────────────────────
    // Thresholds
    // ─────────────────────────────────────────────────────────────────────
    private static final long SLOW_THRESHOLD_MS = 2_000L;
    private static final long IMPLAUSIBLE_MS    = 300_000L;  // 5 min = anomaly
    private static final double DOMINATION_RATIO = 0.60;
    private static final int REPEAT_QUERY_THRESHOLD = 3;

    // ─────────────────────────────────────────────────────────────────────
    // Patterns — extracted from real WORKS log analysis
    // ─────────────────────────────────────────────────────────────────────
    private static final Pattern P_SQL_QUERY_KIND = Pattern.compile(
            "Query:\\s*(SELECT|INSERT|UPDATE|DELETE)", Pattern.CASE_INSENSITIVE);

    private static final Pattern P_SQL_QUERY_TEXT = Pattern.compile(
            "Query:\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern P_JOIN = Pattern.compile(
            "\\b(inner\\s+join|left\\s+join|right\\s+join|join)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern P_TABLE = Pattern.compile(
            "\\b(?:from|join)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern P_INSERT_ARRAY = Pattern.compile(
            "insertArrayRel\\s+(\\w+)\\s+uuid\\s+\\[[^\\]]+\\]\\s+for\\s+\\[(\\d+)\\]\\s+element",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern P_SIZE_LOAD = Pattern.compile(
            "loadListChilds\\s+\\[.*?\\]\\s+size\\s+\\[(\\d+)\\]");

    private static final Pattern P_MULTITHREADING = Pattern.compile(
            "\\(Multithreading\\)", Pattern.CASE_INSENSITIVE);

    private static final Pattern P_PARTITIONAL = Pattern.compile(
            "partitional", Pattern.CASE_INSENSITIVE);

    private static final Pattern P_PARTITION_MARKER = Pattern.compile(
            "-->\\s*\\d+\\s*/\\s*\\d+");

    private static final Pattern P_QUERY_SELECT = Pattern.compile(
            "(?:prepareSearchByRoot\\s+\\[Query Select\\]|searchAttributesList.*\\[Query\\])",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern P_SIBLING_SAVE = Pattern.compile(
            "(saveInstanceOperation|saveOperations|validateAttributesOperation|validateOperation)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern P_SIBLING_SEARCH = Pattern.compile(
            "(global\\s+searchComposantByRoot|loadListChilds\\s+\\[total\\])",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern P_ROW_FETCHED = Pattern.compile(
            "(\\d+)\\s+row\\s+fetched");
    private static final Pattern P_ROW_PIPE = Pattern.compile(
            "\\|\\s*(\\d+)\\s+row\\s*\\|");

    // ── Patterns ajoutés v2 (découverts sur vague1 : 900 cas, 3 dossiers) ──
    /** Étapes de workflow / BPM / webservice dont le took englobe un temps d'attente. */
    private static final Pattern P_WORKFLOW = Pattern.compile(
            "(Transit task|persist Operation|JbpmAccessor|End SaveOrUpdate|Start SaveOrUpdate"
            + "|save documents|saveProcessContent)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern P_RUNNING_RULES = Pattern.compile(
            "running rules", Pattern.CASE_INSENSITIVE);

    /** Ancres "load*" qui cachent en réalité une requête SQL dominante. */
    private static final Pattern P_LOAD_OP = Pattern.compile(
            "(loadOperationById|loadFilsOperation|loadOperationContext|searchDataSecurity)",
            Pattern.CASE_INSENSITIVE);

    /** Détecte "total time SAVE ... ; N(ms)" — utile pour les anomalies de mesure SAVE. */
    private static final Pattern P_TOTAL_SAVE = Pattern.compile(
            "total time SAVE.*?;\\s*(\\d+)\\s*\\(ms\\)", Pattern.CASE_INSENSITIVE);

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────
    public enum Category {
        MEASUREMENT_ANOMALY,
        WORKFLOW_WAIT_ANOMALY,   // ajouté v2 : attente workflow/BPM/webservice, pas du calcul
        PARALLEL_MULTITHREADED,
        RULES_REPLAYED,
        RULES_ENGINE_SINGLE,     // un seul running/fire rules dominant (pas de rejeu)
        SAVE_INSERT_DOMINATED,
        SQL_DOMINATED,
        LARGE_DATA_LOAD,
        ATOMIC_SQL_LEAF,
        SAVE_DIFFUSE,
        SEARCH_DIFFUSE,
        RENDERING_DOMINATED,
        EXTERNAL_WAIT,           // print / WS / appel externe / trou silencieux hors SQL
        UNKNOWN
    }

    /** Classification result with evidence + recommendations + deduplication key. */
    public static class Classification {
        public Category category;
        public String primaryCause;
        public Map<String, Object> evidence = new LinkedHashMap<>();
        public List<String> recommendations = new ArrayList<>();
        /** Used to merge duplicate measurements of the same incident. */
        public String dedupKey;
    }

    /**
     * Main entry point. Classifies a latency anchor + its cause window into a Category
     * with evidence and concrete recommendations.
     *
     * @param anchor      The log entry containing the "took [N] ms" measurement
     * @param windowLogs  The cause window (logs in same uuid/filter/session, ts before anchor)
     * @param steps       The timeline steps already computed (sub-tooks)
     */
    public Classification classify(LogEntry anchor,
                                   List<LogEntry> windowLogs,
                                   List<LatencyTimelineStepDto> steps) {
        Classification r = new Classification();
        long took = resolveTook(anchor);
        String anchorMsg = anchor.getMessage() != null ? anchor.getMessage() : "";

        // Build sub-took list from steps (= measured operations in the cause window)
        List<LatencyTimelineStepDto> subTooks = new ArrayList<>();
        if (steps != null) {
            for (LatencyTimelineStepDto s : steps) {
                if (s.getDurationMs() != null && s.getDurationMs() > 0 && !s.isSuspect()) {
                    subTooks.add(s);
                }
            }
        }

        // ─── G. Measurement anomaly ───────────────────────────────────────
        // (a) Durée matériellement impossible
        boolean anomaly = took >= IMPLAUSIBLE_MS;
        // (b) Anomalie "span réel" : le took est énorme mais le travail technique
        //     réellement mesuré dans la fenêtre est négligeable, et l'écart temporel
        //     entre la 1re et la dernière ligne de cause est très inférieur au took.
        //     → le compteur a démarré trop tôt (ouverture d'écran, attente).
        if (!anomaly && took > 30_000) {
            long maxWorker = 0;
            for (LatencyTimelineStepDto s : subTooks) {
                String m = safe(s);
                if (P_SIBLING_SAVE.matcher(m).find() || P_SIBLING_SEARCH.matcher(m).find()
                        || P_WORKFLOW.matcher(m).find() || P_RUNNING_RULES.matcher(m).find()) {
                    continue; // ignore les wrappers
                }
                maxWorker = Math.max(maxWorker, s.getDurationMs());
            }
            long span = causeSpanMs(windowLogs);
            if (maxWorker < 0.10 * took && span >= 0 && span < 0.20 * took) {
                anomaly = true;
            }
        }
        if (anomaly) {
            r.category = Category.MEASUREMENT_ANOMALY;
            r.primaryCause = String.format(
                    "Durée mesurée (%.0fs) incohérente avec le travail réel observé — "
                    + "anomalie d'horodatage WORKS (le compteur démarre avant le travail effectif).",
                    took / 1000.0);
            r.recommendations = List.of(
                    "Ne pas traiter comme une vraie latence.",
                    "Côté WORKS : corriger l'initialisation du timestamp de début pour cette opération.");
            r.dedupKey = "anomaly:" + anchor.getSessionId() + ":" + (took / 10_000);
            return r;
        }

        // ─── D. Multithreaded search ─────────────────────────────────────
        if (P_MULTITHREADING.matcher(anchorMsg).find()
                || P_PARTITION_MARKER.matcher(anchorMsg).find()) {
            classifyMultithreaded(r, anchor, windowLogs, took);
            return r;
        }

        // ─── C. Rules replayed (same filter repeats SQL N+ times) ────────
        Map<String, Integer> queryCountByFilter = countQueriesByFilter(windowLogs);
        String topFilter = topKey(queryCountByFilter);
        int topCount = topFilter != null ? queryCountByFilter.get(topFilter) : 0;
        if (topCount >= REPEAT_QUERY_THRESHOLD) {
            long filterTotal = windowLogs.stream()
                    .filter(l -> topFilter.equals(extractFilter(l.getMessage())))
                    .mapToLong(this::resolveTook).sum();
            r.category = Category.RULES_REPLAYED;
            r.primaryCause = String.format(
                    "Le contrôle « %s » rejoue %d fois la même requête (%.1fs cumulés).",
                    topFilter, topCount, filterTotal / 1000.0);
            r.evidence.put("filter_code", topFilter);
            r.evidence.put("replay_count", topCount);
            r.evidence.put("replay_total_ms", filterTotal);
            r.recommendations = List.of(
                    String.format("Mutualiser les %d exécutions de « %s » en une seule requête en lot.", topCount, topFilter),
                    "Vérifier si ce contrôle doit s'exécuter par élément ou peut être globalisé.");
            r.dedupKey = "rules:" + anchor.getId() + ":" + topFilter;
            return r;
        }

        // Find dominant sub-took, drilling through sibling wrappers
        LatencyTimelineStepDto dominant = findDominantStep(subTooks, anchorMsg, took);

        // ─── B. Save-insert dominated ─────────────────────────────────────
        if (dominant != null && P_INSERT_ARRAY.matcher(safe(dominant)).find()) {
            classifySaveInsert(r, anchor, dominant, took);
            return r;
        }

        // ─── A. SQL_DOMINATED (Query Select) ─────────────────────────────
        if ((dominant != null && P_QUERY_SELECT.matcher(safe(dominant)).find())
                || P_QUERY_SELECT.matcher(anchorMsg).find()) {
            classifySqlDominated(r, anchor, windowLogs, dominant, took);
            return r;
        }

        // ─── E. Large data load (size [N]) ────────────────────────────────
        LatencyTimelineStepDto sizeTarget = (dominant != null && P_SIZE_LOAD.matcher(safe(dominant)).find())
                ? dominant : null;
        if (sizeTarget == null && P_SIZE_LOAD.matcher(anchorMsg).find()) {
            // Use the anchor itself as the size carrier
            sizeTarget = stepFromAnchor(anchor);
        }
        if (sizeTarget != null) {
            classifyLargeLoad(r, anchor, sizeTarget, took);
            return r;
        }

        // ─── I. insertArrayRel as direct anchor ──────────────────────────
        if (P_INSERT_ARRAY.matcher(anchorMsg).find()) {
            classifyInsertAnchor(r, anchor, anchorMsg, took);
            return r;
        }

        // ─── F. Atomic SQL leaf (anchor IS the query) ─────────────────────
        if (P_QUERY_SELECT.matcher(anchorMsg).find()
                || anchorMsg.contains("Query Select")) {
            classifyAtomicSql(r, anchor, windowLogs, took);
            return r;
        }

        // ─── J. loadOperation* / searchDataSecurity cachant un SQL dominant ──
        //     (découvert v2 : ancre "loadOperationById" mais le vrai coût est un
        //      [Query Select] de plusieurs centaines de secondes dans la fenêtre)
        if (P_LOAD_OP.matcher(anchorMsg).find()) {
            LatencyTimelineStepDto qDom = null;
            for (LatencyTimelineStepDto s : subTooks) {
                if (P_QUERY_SELECT.matcher(safe(s)).find()
                        && s.getDurationMs() >= 0.40 * took
                        && (qDom == null || s.getDurationMs() > qDom.getDurationMs())) {
                    qDom = s;
                }
            }
            if (qDom != null) {
                classifySqlDominated(r, anchor, windowLogs, qDom, took);
                return r;
            }
        }

        // ─── K. Workflow / BPM / webservice wait anomaly ─────────────────────
        //     (découvert v2 : "Transit task", "running rules", "End SaveOrUpdate WS",
        //      "persist Operation" avec took énorme mais travail technique négligeable
        //      = temps d'attente utilisateur ou transition d'état, pas du calcul)
        if (P_WORKFLOW.matcher(anchorMsg).find() || P_RUNNING_RULES.matcher(anchorMsg).find()) {
            long maxWorker = 0;
            for (LatencyTimelineStepDto s : subTooks) {
                String m = safe(s);
                if (P_SIBLING_SAVE.matcher(m).find() || P_SIBLING_SEARCH.matcher(m).find()
                        || P_WORKFLOW.matcher(m).find() || P_RUNNING_RULES.matcher(m).find()) {
                    continue;
                }
                maxWorker = Math.max(maxWorker, s.getDurationMs());
            }
            if (maxWorker < 0.30 * took) {
                r.category = Category.WORKFLOW_WAIT_ANOMALY;
                String kind = P_WORKFLOW.matcher(anchorMsg).find()
                        ? "transition de workflow/webservice (BPM)"
                        : "attente entre règles métier";
                r.primaryCause = String.format(
                        "Durée de %.0fs attribuée à une %s, mais le travail technique réel "
                        + "est négligeable. Le compteur englobe un temps d'attente "
                        + "(utilisateur ou transition d'état), pas du calcul.",
                        took / 1000.0, kind);
                r.evidence.put("max_worker_ms", maxWorker);
                r.evidence.put("anchor_kind", "workflow_transit");
                r.recommendations = List.of(
                        "Ne pas traiter comme une latence technique — c'est un temps d'attente/transition.",
                        "Si récurrent, investiguer pourquoi l'étape de workflow reste ouverte si "
                        + "longtemps (process métier, attente utilisateur, ou tâche asynchrone bloquée).");
                r.dedupKey = "workflow:" + scopeKey(anchor) + ":" + (took / 10_000);
                return r;
            }
        }

        // ─── SAVE_DIFFUSE — save anchor without clear insert dominant ────
        if (P_SIBLING_SAVE.matcher(anchorMsg).find()) {
            classifySaveDiffuse(r, anchor, subTooks, took);
            return r;
        }

        // ─── SEARCH_DIFFUSE — search anchor with no clear cause ──────────
        if (P_SIBLING_SEARCH.matcher(anchorMsg).find()) {
            r.category = Category.SEARCH_DIFFUSE;
            r.primaryCause = String.format(
                    "Recherche de %.1fs dominée par chargements multiples sans étape isolable.",
                    took / 1000.0);
            r.evidence.put("sub_step_count", subTooks.size());
            r.recommendations = List.of("Examiner les sous-opérations agrégées dans loadListChilds [total].");
            r.dedupKey = "search_diffuse:" + anchor.getId() + ":" + (took / 1000);
            return r;
        }

        // ─── RENDERING_DOMINATED ─────────────────────────────────────────
        if (dominant != null && safe(dominant).toLowerCase().contains("rendering result")
                && dominant.getDurationMs() != null && dominant.getDurationMs() >= 0.50 * took) {
            r.category = Category.RENDERING_DOMINATED;
            r.primaryCause = String.format(
                    "Le rendu du résultat (« rendering result ») représente %.1fs (%.0f%% du total).",
                    dominant.getDurationMs() / 1000.0,
                    100.0 * dominant.getDurationMs() / Math.max(1, took));
            r.evidence.put("rendering_ms", dominant.getDurationMs());
            r.recommendations = List.of(
                    "Réduire le volume de données affiché (filtrage, pagination, chargement progressif).",
                    "Optimiser le mécanisme de rendu de l'écran si un grand volume doit rester affiché.");
            r.dedupKey = "render:" + scopeKey(anchor) + ":" + (took / 1000);
            return r;
        }

        // ─── RULES_ENGINE_SINGLE — un seul running/fire rules dominant ───
        if (P_RUNNING_RULES.matcher(anchorMsg).find()
                || (dominant != null && P_RUNNING_RULES.matcher(safe(dominant)).find())) {
            long rulesMs = dominant != null && P_RUNNING_RULES.matcher(safe(dominant)).find()
                    ? dominant.getDurationMs() : took;
            r.category = Category.RULES_ENGINE_SINGLE;
            r.primaryCause = String.format(
                    "Le moteur de règles (« running rules » / fire rules) concentre %.1fs.",
                    rulesMs / 1000.0);
            r.evidence.put("rules_ms", rulesMs);
            r.recommendations = List.of(
                    "Instrumenter les règles de l'action concernée (sous-took autour des appels internes).",
                    "Vérifier appels externes / verrous déclenchés par les règles.",
                    "Surveiller la JVM (GC) si la mémoire est élevée pendant la fenêtre.");
            r.dedupKey = "rules_single:" + scopeKey(anchor) + ":" + (took / 1000);
            return r;
        }

        // ─── EXTERNAL_WAIT — print / CUPS / WS / trou hors SQL ───────────
        String lowerAnchor = anchorMsg.toLowerCase();
        if (lowerAnchor.contains("printarchive") || lowerAnchor.contains("printjob")
                || lowerAnchor.contains("cups") || lowerAnchor.contains("ws_")) {
            r.category = Category.EXTERNAL_WAIT;
            r.primaryCause = String.format(
                    "Latence de %.1fs liée à un appel externe / impression / webservice "
                    + "(temps passé hors du SQL local).",
                    took / 1000.0);
            r.evidence.put("external_kind",
                    lowerAnchor.contains("print") || lowerAnchor.contains("cups") ? "print" : "webservice");
            r.recommendations = List.of(
                    "Vérifier printArchive / génération document, ou le webservice distant et ses timeouts.",
                    "Ne pas optimiser le SQL local en premier — ce n'est probablement pas la cause.");
            r.dedupKey = "external:" + scopeKey(anchor) + ":" + (took / 1000);
            return r;
        }

        // ─── L. Anomalie de mesure résiduelle (règle v6) ─────────────────────
        //     took important mais AUCUNE sous-opération (quel qu'en soit le type) ne
        //     dépasse 15% du took → le temps est passé hors des opérations instrumentées
        //     (attente, transition, ou travail hors fenêtre) = anomalie de mesure.
        if (took > 30_000 && !subTooks.isEmpty()) {
            long maxAny = 0;
            for (LatencyTimelineStepDto s : subTooks) {
                maxAny = Math.max(maxAny, s.getDurationMs());
            }
            if (maxAny < 0.15 * took) {
                r.category = Category.MEASUREMENT_ANOMALY;
                r.primaryCause = String.format(
                        "Durée de %.0fs sans aucune sous-opération significative (max %.1fs) — "
                        + "le temps est passé hors des opérations instrumentées : anomalie de mesure "
                        + "ou travail effectué hors de la fenêtre observée.",
                        took / 1000.0, maxAny / 1000.0);
                r.recommendations = List.of(
                        "Traiter avec prudence : la cause technique n'est pas dans la fenêtre observée.",
                        "Si récurrent, élargir la fenêtre de capture ou vérifier l'horodatage WORKS.");
                r.dedupKey = "anomaly:" + anchor.getSessionId() + ":" + (took / 10_000);
                return r;
            }
        }

        // ─── H. Unknown ───────────────────────────────────────────────────
        r.category = Category.UNKNOWN;
        r.primaryCause = String.format("Latence %.1fs, pattern non identifié.", took / 1000.0);
        r.recommendations = List.of("Investigation manuelle requise.");
        r.dedupKey = "unk:" + anchor.getId();
        return r;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sub-classifiers — one per category
    // ─────────────────────────────────────────────────────────────────────
    private void classifyMultithreaded(Classification r, LogEntry anchor,
                                       List<LogEntry> windowLogs, long took) {
        String sql = extractSqlText(windowLogs);
        int joins = sql != null ? countMatches(P_JOIN, sql) : 0;
        List<String> tables = sql != null ? extractTables(sql) : List.of();
        Integer rowCount = detectRowCount(windowLogs, anchor.getMessage());
        long partitions = windowLogs.stream()
                .filter(l -> l.getMessage() != null
                        && P_PARTITIONAL.matcher(l.getMessage()).find())
                .count();

        r.category = Category.PARALLEL_MULTITHREADED;
        r.evidence.put("sql_query", truncate(sql, 1500));
        r.evidence.put("sql_join_count", joins);
        r.evidence.put("sql_tables", tables);
        r.evidence.put("row_count", rowCount);
        r.evidence.put("partition_count", partitions);

        StringBuilder cause = new StringBuilder("Recherche parallélisée");
        if (partitions > 0) cause.append(" sur ").append(partitions).append(" partitions");
        if (joins > 0)     cause.append(", requête à ").append(joins).append(" jointures");
        cause.append(".");
        r.primaryCause = cause.toString();

        List<String> recs = new ArrayList<>();
        if (joins >= 3) {
            recs.add(String.format(
                    "La requête racine (%d jointures sur tables EAV) reste lente même parallélisée. "
                            + "Optimiser cette requête d'abord (index, plan d'exécution).", joins));
        }
        if (rowCount != null && rowCount > 1000) {
            recs.add(String.format(
                    "Volume important (%d lignes) — envisager pagination ou réduction du périmètre.",
                    rowCount));
        }
        if (recs.isEmpty()) {
            recs.add("Examiner la requête racine; le multithreading masque mais n'efface pas la lenteur SQL.");
        }
        r.recommendations = recs;
        // Dedup: same uuid (or filter) + bucket of seconds = same incident
        String scope = isBlank(anchor.getUserCorrelationId())
                ? safeStr(extractFilter(anchor.getMessage()))
                : anchor.getUserCorrelationId();
        r.dedupKey = "mt:" + scope + ":" + (took / 1000);
    }

    private void classifySaveInsert(Classification r, LogEntry anchor,
                                    LatencyTimelineStepDto dominant, long took) {
        Matcher m = P_INSERT_ARRAY.matcher(safe(dominant));
        m.find();
        String table = m.group(1);
        int elementCount = Integer.parseInt(m.group(2));
        long insertMs = dominant.getDurationMs();

        r.category = Category.SAVE_INSERT_DOMINATED;
        r.primaryCause = String.format(
                "Insertion sur %s (%d élément(s)) prend %.1fs (%d%% du total).",
                table, elementCount, insertMs / 1000.0,
                Math.round(100.0 * insertMs / took));
        r.evidence.put("table", table);
        r.evidence.put("element_count", elementCount);
        r.evidence.put("insert_duration_ms", insertMs);

        List<String> recs = new ArrayList<>();
        recs.add(String.format(
                "Pour %d élément(s), %.1fs est anormal — suspecter un trigger lent, "
                        + "un check d'intégrité, ou un verrou attendu.",
                elementCount, insertMs / 1000.0));
        recs.add("Vérifier triggers/contraintes/index sur " + table + ".");
        if (insertMs >= 5000) {
            recs.add("Lancer EXPLAIN ANALYZE sur un INSERT équivalent ; vérifier l'absence de verrou concurrent.");
        }
        r.recommendations = recs;
        r.dedupKey = "save:" + anchor.getUserCorrelationId() + ":" + table + ":" + elementCount;
    }

    private void classifySqlDominated(Classification r, LogEntry anchor,
                                      List<LogEntry> windowLogs,
                                      LatencyTimelineStepDto dominant, long took) {
        String sql = extractSqlText(windowLogs);
        Integer rc = detectRowCount(windowLogs, anchor.getMessage());
        int joins = sql != null ? countMatches(P_JOIN, sql) : 0;
        List<String> tables = sql != null ? extractTables(sql) : List.of();
        long sqlMs = dominant != null ? dominant.getDurationMs() : took;

        r.category = Category.SQL_DOMINATED;
        StringBuilder cause = new StringBuilder();
        cause.append(String.format("Requête SELECT prend %.1fs", sqlMs / 1000.0));
        if (rc != null && rc <= 5) cause.append(String.format(" pour %d ligne(s)", rc));
        cause.append(String.format(". %d jointure(s) sur %d table(s).", joins, tables.size()));
        r.primaryCause = cause.toString();

        r.evidence.put("sql_query", truncate(sql, 1500));
        r.evidence.put("sql_join_count", joins);
        r.evidence.put("sql_tables", tables);
        r.evidence.put("row_count", rc);
        r.evidence.put("sql_duration_ms", sqlMs);

        List<String> recs = new ArrayList<>();
        if (rc != null && rc <= 5 && sqlMs >= 10_000) {
            recs.add(String.format(
                    "Requête lente (%.1fs) pour seulement %d ligne(s) — signal fort de "
                            + "balayage complet de table; vérifier les index.",
                    sqlMs / 1000.0, rc));
        }
        if (joins >= 3) {
            recs.add(String.format(
                    "%d jointures sur tables EAV — vérifier index composites "
                            + "(composant_fk + cle) sur works_detail_composant.", joins));
        }
        if (recs.isEmpty()) {
            recs.add("Récupérer le plan d'exécution (EXPLAIN ANALYZE) sur la requête racine.");
        }
        r.recommendations = recs;
        r.dedupKey = "sql:" + safeStr(anchor.getUserCorrelationId()) + ":" + (sqlMs / 1000);
    }

    private void classifyLargeLoad(Classification r, LogEntry anchor,
                                   LatencyTimelineStepDto target, long took) {
        Matcher m = P_SIZE_LOAD.matcher(safe(target));
        m.find();
        int size = Integer.parseInt(m.group(1));
        long dur = target.getDurationMs() != null ? target.getDurationMs() : took;

        r.category = Category.LARGE_DATA_LOAD;
        String ratio = (target.getLogId() != null && target.getLogId().equals(anchor.getId()))
                ? ""
                : String.format(" (%d%% du total)", Math.round(100.0 * dur / took));
        r.primaryCause = String.format("Chargement de %d éléments enfants en %.1fs%s.",
                size, dur / 1000.0, ratio);
        r.evidence.put("load_size", size);
        r.evidence.put("load_duration_ms", dur);

        double msPerElem = dur / Math.max(size, 1);
        List<String> recs = new ArrayList<>();
        if (msPerElem > 200) {
            recs.add(String.format(
                    "Coût moyen de %.0fms par élément — chargement individuel inefficace. Activer le batch.",
                    msPerElem));
        }
        if (size > 100) {
            recs.add(String.format("Volume important (%d éléments) — envisager la pagination.", size));
        }
        if (recs.isEmpty()) {
            recs.add("Chargement multi-éléments — investiguer les requêtes B1/B2 ou jointures.");
        }
        r.recommendations = recs;
        r.dedupKey = "load:" + safeStr(anchor.getUserCorrelationId()) + ":" + size + ":" + (dur / 1000);
    }

    private void classifyInsertAnchor(Classification r, LogEntry anchor,
                                      String anchorMsg, long took) {
        Matcher m = P_INSERT_ARRAY.matcher(anchorMsg);
        m.find();
        String table = m.group(1);
        int ec = Integer.parseInt(m.group(2));
        r.category = Category.SAVE_INSERT_DOMINATED;
        r.primaryCause = String.format(
                "Insertion atomique sur %s (%d élément(s)) en %.1fs — anormalement lente.",
                table, ec, took / 1000.0);
        r.evidence.put("table", table);
        r.evidence.put("element_count", ec);
        r.recommendations = List.of(
                "Suspecter un trigger lent ou un verrou sur " + table + ".",
                "Vérifier triggers/contraintes/index sur " + table + ".");
        r.dedupKey = "save:" + anchor.getUserCorrelationId() + ":" + table + ":" + ec;
    }

    private void classifyAtomicSql(Classification r, LogEntry anchor,
                                   List<LogEntry> windowLogs, long took) {
        List<LogEntry> sources = new ArrayList<>(windowLogs);
        sources.add(anchor);
        String sql = extractSqlText(sources);
        Integer rc = detectRowCount(windowLogs, anchor.getMessage());
        int joins = sql != null ? countMatches(P_JOIN, sql) : 0;
        List<String> tables = sql != null ? extractTables(sql) : List.of();

        r.category = Category.ATOMIC_SQL_LEAF;
        r.primaryCause = String.format(
                "Requête SQL atomique de %.1fs — durée passée entièrement dans le SGBD.",
                took / 1000.0);
        r.evidence.put("sql_query", truncate(sql, 1500));
        r.evidence.put("sql_join_count", joins);
        r.evidence.put("sql_tables", tables);
        r.evidence.put("row_count", rc);

        List<String> recs = new ArrayList<>();
        recs.add("Exécuter EXPLAIN ANALYZE sur cette requête.");
        recs.add("Vérifier les statistiques (ANALYZE des tables impliquées).");
        if (joins >= 3) {
            recs.add(joins + " jointures — vérifier index composites sur tables EAV.");
        }
        r.recommendations = recs;
        r.dedupKey = "sql:" + safeStr(anchor.getUserCorrelationId()) + ":" + (took / 1000);
    }

    private void classifySaveDiffuse(Classification r, LogEntry anchor,
                                     List<LatencyTimelineStepDto> subTooks, long took) {
        // Find non-sibling sub-tooks
        List<LatencyTimelineStepDto> nonSib = new ArrayList<>();
        for (LatencyTimelineStepDto s : subTooks) {
            String m = safe(s);
            if (!P_SIBLING_SAVE.matcher(m).find()) nonSib.add(s);
        }

        r.category = Category.SAVE_DIFFUSE;
        if (!nonSib.isEmpty()) {
            LatencyTimelineStepDto top = nonSib.stream()
                    .max(Comparator.comparingLong(LatencyTimelineStepDto::getDurationMs))
                    .orElseThrow();
            long otherMs = nonSib.stream().mapToLong(LatencyTimelineStepDto::getDurationMs).sum();
            long insertMs = nonSib.stream()
                    .filter(s -> safe(s).contains("insertArrayRel"))
                    .mapToLong(LatencyTimelineStepDto::getDurationMs).sum();
            r.primaryCause = String.format(
                    "Sauvegarde de %.1fs distribuée sur %d sous-étapes (total %.1fs). "
                            + "Plus longue: %s (%.1fs).",
                    took / 1000.0, nonSib.size(), otherMs / 1000.0,
                    truncate(safe(top), 60), top.getDurationMs() / 1000.0);
            r.evidence.put("sub_step_count", nonSib.size());
            r.evidence.put("longest_step_ms", top.getDurationMs());
            r.evidence.put("longest_step_msg", truncate(safe(top), 200));
            List<String> recs = new ArrayList<>();
            recs.add("Sauvegarde diffuse — lenteur cumulée de plusieurs micro-opérations.");
            if (insertMs > 0.3 * took) {
                recs.add(String.format(
                        "Inserts cumulés: %.1fs — vérifier indexation des tables works_rel_composant_*.",
                        insertMs / 1000.0));
            }
            r.recommendations = recs;
        } else {
            r.primaryCause = String.format(
                    "Sauvegarde de %.1fs sans sous-étape mesurable — travail dans des appels "
                            + "non instrumentés (commits, validations système).",
                    took / 1000.0);
            r.recommendations = List.of(
                    "Activer une journalisation plus fine côté WORKS pour cette opération.",
                    "Suspecter coûts de commit, de contraintes, ou de cache.");
        }
        r.dedupKey = "save_diffuse:" + anchor.getUserCorrelationId() + ":" + (took / 1000);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────
    private static final Pattern TOOK_ANY = Pattern.compile(
            "took\\s*(?:\\[(\\d{1,9})]|(\\d{1,9}))\\s*(?:\\(\\s*ms\\s*\\)|ms)"
                    + "|took\\s*\\(\\s*ms\\s*\\)\\s*=\\s*(\\d{1,9})"
                    + "|total time SAVE.*?;\\s*(\\d{1,9})\\s*\\(\\s*ms\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private long resolveTook(LogEntry log) {
        if (log.getDurationMs() != null && log.getDurationMs() > 0) {
            return log.getDurationMs();
        }
        String text = (log.getMessage() != null ? log.getMessage() : "")
                + " " + (log.getRawLog() != null ? log.getRawLog() : "");
        Matcher m = TOOK_ANY.matcher(text);
        Long last = null;
        while (m.find()) {
            for (int g = 1; g <= m.groupCount(); g++) {
                String v = m.group(g);
                if (v != null && !v.isBlank()) {
                    try { last = Long.parseLong(v.trim()); } catch (NumberFormatException ignore) { }
                }
            }
        }
        return last != null ? last : 0L;
    }

    /** Écart en ms entre la première et la dernière ligne de la fenêtre de cause.
     *  Sert à détecter les anomalies de mesure (took énorme mais span réel court). */
    private long causeSpanMs(List<LogEntry> windowLogs) {
        if (windowLogs == null || windowLogs.size() < 2) return -1;
        LocalDateTime min = null, max = null;
        for (LogEntry l : windowLogs) {
            LocalDateTime ts = l.getLogTimestamp();
            if (ts == null) continue;
            if (min == null || ts.isBefore(min)) min = ts;
            if (max == null || ts.isAfter(max)) max = ts;
        }
        if (min == null || max == null) return -1;
        return java.time.Duration.between(min, max).toMillis();
    }

    /** Clé de scope pour la déduplication (uuid > txid > filter > session).
     *  txid et filterCode ne sont pas des colonnes dédiées : on les lit dans le message. */
    private String scopeKey(LogEntry anchor) {
        if (!isBlank(anchor.getUserCorrelationId())) return anchor.getUserCorrelationId();
        String msg = anchor.getMessage() != null ? anchor.getMessage() : "";
        Matcher tx = Pattern.compile("transactionId\\s*[\\[(]([^\\])]+)[\\])]",
                Pattern.CASE_INSENSITIVE).matcher(msg);
        if (tx.find()) return tx.group(1).trim();
        String filt = extractFilter(msg);
        if (filt != null) return filt;
        return safeStr(anchor.getSessionId());
    }

    private long resolveTook(LatencyTimelineStepDto step) {
        return step.getDurationMs() != null ? step.getDurationMs() : 0L;
    }

    private String safe(LatencyTimelineStepDto s) {
        String op = s.getOperationName();
        String detail = s.getDetail();
        return (op == null ? "" : op) + " " + (detail == null ? "" : detail);
    }

    private String safeStr(String s) { return s == null ? "" : s; }
    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private int countMatches(Pattern p, String s) {
        if (s == null) return 0;
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private List<String> extractTables(String sql) {
        if (sql == null) return List.of();
        Set<String> tables = new LinkedHashSet<>();
        Matcher m = P_TABLE.matcher(sql);
        while (m.find()) {
            tables.add(m.group(1).toLowerCase());
            if (tables.size() >= 8) break;
        }
        return new ArrayList<>(tables);
    }

    private String extractSqlText(List<LogEntry> logs) {
        for (LogEntry l : logs) {
            if (l.getMessage() == null) continue;
            Matcher m = P_SQL_QUERY_TEXT.matcher(l.getMessage());
            if (m.find()) return m.group(1).trim();
        }
        return null;
    }

    private Integer detectRowCount(List<LogEntry> logs, String anchorMsg) {
        // Check anchor first (some logs put "1 row" right in the anchor msg)
        if (anchorMsg != null) {
            Integer r = findRowCount(anchorMsg);
            if (r != null) return r;
        }
        for (LogEntry l : logs) {
            if (l.getMessage() == null) continue;
            Integer r = findRowCount(l.getMessage());
            if (r != null) return r;
        }
        return null;
    }

    private Integer findRowCount(String msg) {
        Matcher m = P_ROW_FETCHED.matcher(msg);
        if (m.find()) return Integer.parseInt(m.group(1));
        m = P_ROW_PIPE.matcher(msg);
        if (m.find()) return Integer.parseInt(m.group(1));
        Matcher sz = P_SIZE_LOAD.matcher(msg);
        if (sz.find()) return Integer.parseInt(sz.group(1));
        return null;
    }

    private String extractFilter(String msg) {
        if (msg == null) return null;
        Matcher m = Pattern.compile("filter code \\[([^\\]]+)\\]").matcher(msg);
        return m.find() ? m.group(1).trim() : null;
    }

    private Map<String, Integer> countQueriesByFilter(List<LogEntry> logs) {
        Map<String, Integer> map = new HashMap<>();
        for (LogEntry l : logs) {
            String m = l.getMessage();
            if (m == null) continue;
            if (!P_SQL_QUERY_KIND.matcher(m).find()) continue;
            String filt = extractFilter(m);
            if (filt == null) continue;
            map.merge(filt, 1, Integer::sum);
        }
        return map;
    }

    private String topKey(Map<String, Integer> map) {
        return map.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
    }

    private LatencyTimelineStepDto findDominantStep(
            List<LatencyTimelineStepDto> steps, String anchorMsg, long took) {
        if (steps.isEmpty()) return null;
        List<LatencyTimelineStepDto> sorted = new ArrayList<>(steps);
        sorted.sort((a, b) -> Long.compare(b.getDurationMs(), a.getDurationMs()));

        boolean anchorIsSaveSib   = P_SIBLING_SAVE.matcher(anchorMsg).find();
        boolean anchorIsSearchSib = P_SIBLING_SEARCH.matcher(anchorMsg).find();

        for (LatencyTimelineStepDto cand : sorted) {
            if (cand.getDurationMs() < DOMINATION_RATIO * took) return null;
            String m = safe(cand);
            boolean candIsSaveSib   = P_SIBLING_SAVE.matcher(m).find();
            boolean candIsSearchSib = P_SIBLING_SEARCH.matcher(m).find();
            // Skip if candidate is a sibling wrapper of the anchor
            if ((anchorIsSaveSib && candIsSaveSib) || (anchorIsSearchSib && candIsSearchSib)) {
                continue;
            }
            return cand;
        }
        return null;
    }

    /** Build a synthetic step from the anchor (used when anchor itself carries the size info). */
    private LatencyTimelineStepDto stepFromAnchor(LogEntry anchor) {
        LatencyTimelineStepDto s = new LatencyTimelineStepDto();
        s.setLogId(anchor.getId());
        s.setTimestamp(anchor.getLogTimestamp());
        s.setDurationMs(resolveTook(anchor));
        s.setDetail(anchor.getMessage());
        s.setOperationName(anchor.getMessage());
        return s;
    }
}
