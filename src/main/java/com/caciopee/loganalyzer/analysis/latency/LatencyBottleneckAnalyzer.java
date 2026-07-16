package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Détection générique du goulet d'étranglement par analyse hiérarchique
 * (conteneur global → phases enfants) et ratios de temps partagé.
 */
public final class LatencyBottleneckAnalyzer {

    private static final long SIGNIFICANT_MS = 2000L;
    private static final double DOMINANT_SHARE = 0.85;
    private static final double CHILD_MINOR_SHARE = 0.15;
    private static final int FEW_ROWS_THRESHOLD = 10;

    private LatencyBottleneckAnalyzer() {
    }

    public enum NarrativeKind {
        SQL_DOMINATED,
        PARALLEL_LOAD_DOMINATED,
        RULES_DOMINATED,
        RENDERING_DOMINATED,
        WS_DOMINATED,
        PRINT_DOMINATED,
        AUTOSTART_DOMINATED,
        SAVE_DOMINATED,
        WORKFLOW_DOMINATED,
        /** loadOperationById / loadOperationContext / chargement données d'opération. */
        OPERATION_LOAD_DOMINATED,
        CONTAINER_DOMINATED,
        SINGLE_STEP,
        COMPLEX
    }

    public record BottleneckResult(LatencyTimelineStepDto primary, NarrativeKind kind) {
    }

    public static BottleneckResult analyze(List<LatencyTimelineStepDto> allSteps,
                                           LatencyOperationClassifier classifier) {
        if (allSteps == null || allSteps.isEmpty()) {
            return null;
        }

        // On écarte les étapes dont la durée est une anomalie de mesure (took impossible) :
        // elles ne doivent jamais être désignées comme le goulot réel.
        List<LatencyTimelineStepDto> steps = allSteps.stream()
                .filter(s -> !s.isSuspect())
                .toList();
        if (steps.isEmpty()) {
            return null;
        }

        LatencyTimelineStepDto root = findMax(steps, LatencyOperationClassifier.ROOT_QUERY);
        LatencyTimelineStepDto global = findMax(steps, LatencyOperationClassifier.GLOBAL_CONTAINER);
        LatencyTimelineStepDto partitional = findMax(steps, LatencyOperationClassifier.PARTITIONAL);
        LatencyTimelineStepDto load = findMax(steps, LatencyOperationClassifier.CHILD_LOAD);
        LatencyTimelineStepDto childQuery = findMax(steps, LatencyOperationClassifier.CHILD_QUERY);
        LatencyTimelineStepDto operationLoad = findMax(steps, LatencyOperationClassifier.OPERATION_LOAD);
        LatencyTimelineStepDto rules = findMax(steps, LatencyOperationClassifier.RULES_ENGINE);
        LatencyTimelineStepDto rendering = findMax(steps, LatencyOperationClassifier.RENDERING);
        LatencyTimelineStepDto ws = findMax(steps, LatencyOperationClassifier.WEBSERVICE);
        LatencyTimelineStepDto print = findMax(steps, LatencyOperationClassifier.PRINT);
        LatencyTimelineStepDto autoStart = findMax(steps, LatencyOperationClassifier.AUTOSTART);
        LatencyTimelineStepDto save = findMax(steps, LatencyOperationClassifier.SAVE_PERSIST);

        LatencyTimelineStepDto container = firstNonNull(global, partitional, rules, ws, autoStart, print);
        LatencyTimelineStepDto overallMax = maxMeasurable(steps);
        long overallMaxMs = overallMax != null && overallMax.getDurationMs() != null
                ? overallMax.getDurationMs()
                : 0L;

        // Familles spécialisées avant le générique règles/SQL.
        if (autoStart != null && isSignificant(autoStart) && overallMaxMs > 0
                && autoStart.getDurationMs() >= overallMaxMs * 0.85) {
            return new BottleneckResult(autoStart, NarrativeKind.AUTOSTART_DOMINATED);
        }
        if (ws != null && isSignificant(ws) && overallMaxMs > 0
                && ws.getDurationMs() >= overallMaxMs * 0.85) {
            return new BottleneckResult(ws, NarrativeKind.WS_DOMINATED);
        }
        if (print != null && isSignificant(print) && overallMaxMs > 0
                && print.getDurationMs() >= overallMaxMs * 0.85) {
            return new BottleneckResult(print, NarrativeKind.PRINT_DOMINATED);
        }

        // Chargement d'opération (souvent sous Imprimer) avant toute génération document.
        LatencyTimelineStepDto opLoadPeak = maxOf(operationLoad, load, childQuery);
        if (opLoadPeak != null && isSignificant(opLoadPeak) && overallMaxMs > 0
                && opLoadPeak.getDurationMs() >= overallMaxMs * 0.70
                && (print == null || print.getDurationMs() == null
                || opLoadPeak.getDurationMs() >= print.getDurationMs())
                && (rules == null || rules.getDurationMs() == null
                || opLoadPeak.getDurationMs() >= rules.getDurationMs() * 0.9)) {
            return new BottleneckResult(opLoadPeak, NarrativeKind.OPERATION_LOAD_DOMINATED);
        }

        // Règles imbriquées dans Transit / doAction : l'enveloppe n'est pas la cause.
        // Ex. matchall : running rules 64 s dans Transit 82 s (~78 %) → RULES, pas WORKFLOW.
        if (rulesDominateNestEnvelope(rules, steps)) {
            return new BottleneckResult(rules, NarrativeKind.RULES_DOMINATED);
        }

        // Plusieurs familles de durée comparable → COMPLEX (pas SQL_DOMINATED trompeur).
        BottleneckResult distributed = detectDistributedComplexity(steps);
        if (distributed != null) {
            return distributed;
        }

        // Un agrégat « moteur de règles » (fire rules / running rules in host) qui couvre
        // l'essentiel de l'opération prime sur n'importe quelle requête isolée plus courte.
        // Cas process.Manifeste/Valider : 159 s de règles dominent les requêtes de 17 s.
        if (rules != null && isSignificant(rules) && overallMaxMs > 0
                && rules.getDurationMs() >= overallMaxMs * 0.9) {
            return new BottleneckResult(rules, NarrativeKind.RULES_DOMINATED);
        }

        if (root != null && isSqlDominated(root, container, load)
                && !dwarfedByOverall(root, overallMaxMs)) {
            return new BottleneckResult(root, NarrativeKind.SQL_DOMINATED);
        }

        // Ne pas laisser un global search court (ex. 7 s) masquer des règles bien plus longues.
        if (global != null && isSignificant(global) && isParallelSearchCase(root, global, steps)
                && (rules == null || !isSignificant(rules)
                || rules.getDurationMs() < global.getDurationMs() * 2)) {
            return new BottleneckResult(global, NarrativeKind.PARALLEL_LOAD_DOMINATED);
        }

        if (rules != null && isSignificant(rules) && container == rules) {
            return new BottleneckResult(rules, NarrativeKind.RULES_DOMINATED);
        }

        if (load != null && isParallelLoadDominated(load, container, root)) {
            return new BottleneckResult(load, NarrativeKind.PARALLEL_LOAD_DOMINATED);
        }

        if (rendering != null && isRenderingDominated(rendering, steps)) {
            return new BottleneckResult(rendering, NarrativeKind.RENDERING_DOMINATED);
        }

        if (save != null && isSignificant(save) && overallMaxMs > 0
                && save.getDurationMs() >= overallMaxMs * 0.7
                && (rules == null || rules.getDurationMs() == null || save.getDurationMs() >= rules.getDurationMs())) {
            return new BottleneckResult(save, NarrativeKind.SAVE_DOMINATED);
        }

        if (container != null && isSignificant(container)) {
            LatencyTimelineStepDto dominantChild = findDominantChild(container, steps, classifier);
            if (dominantChild != null && !sameStep(dominantChild, container)) {
                NarrativeKind kind = classifyChildDominance(dominantChild, classifier);
                return new BottleneckResult(dominantChild, kind);
            }
            if (isContainerSelfDominant(container, steps, classifier)) {
                return new BottleneckResult(container, NarrativeKind.CONTAINER_DOMINATED);
            }
        }

        LatencyTimelineStepDto max = maxMeasurable(steps);
        if (max != null) {
            NarrativeKind kind = steps.stream().filter(s -> s.getDurationMs() != null && s.getDurationMs() >= SIGNIFICANT_MS).count() > 1
                    ? NarrativeKind.COMPLEX
                    : NarrativeKind.SINGLE_STEP;
            return new BottleneckResult(max, kind);
        }

        return new BottleneckResult(steps.get(0), NarrativeKind.SINGLE_STEP);
    }

    /**
     * Si ≥2 familles (SQL, règles, rendu, SAVE…) contribuent chacune ≥15 % d'un cumul
     * et qu'aucune n'atteint 50 %, le goulot n'est pas mono-cause → COMPLEX.
     */
    private static BottleneckResult detectDistributedComplexity(List<LatencyTimelineStepDto> steps) {
        Map<String, LatencyTimelineStepDto> byFamily = new LinkedHashMap<>();
        for (LatencyTimelineStepDto step : steps) {
            if (step.getDurationMs() == null || step.getDurationMs() < SIGNIFICANT_MS) {
                continue;
            }
            String family = contributorFamily(step.getStepType());
            if (family == null) {
                continue;
            }
            LatencyTimelineStepDto prev = byFamily.get(family);
            if (prev == null || step.getDurationMs() > prev.getDurationMs()) {
                byFamily.put(family, step);
            }
        }
        // Éviter de compter global + SQL racine (souvent le même parcours).
        if (byFamily.containsKey("SQL") && byFamily.containsKey("GLOBAL")) {
            LatencyTimelineStepDto sql = byFamily.get("SQL");
            LatencyTimelineStepDto glob = byFamily.get("GLOBAL");
            if (sql.getDurationMs() * 100 >= glob.getDurationMs() * 60) {
                byFamily.remove("GLOBAL");
            } else {
                byFamily.remove("SQL");
            }
        }
        // doAction / Transit enveloppent souvent les règles : une seule famille.
        if (byFamily.containsKey("RULES") && byFamily.containsKey("ACTION")) {
            LatencyTimelineStepDto rules = byFamily.get("RULES");
            LatencyTimelineStepDto action = byFamily.get("ACTION");
            if (rules.getDurationMs() >= action.getDurationMs() * 0.7) {
                byFamily.remove("ACTION");
            } else {
                byFamily.remove("RULES");
            }
        }
        if (byFamily.containsKey("RULES") && byFamily.containsKey("WORKFLOW")) {
            LatencyTimelineStepDto rules = byFamily.get("RULES");
            LatencyTimelineStepDto workflow = byFamily.get("WORKFLOW");
            if (rules.getDurationMs() >= workflow.getDurationMs() * 0.7) {
                byFamily.remove("WORKFLOW");
            } else {
                byFamily.remove("RULES");
            }
        }

        if (byFamily.size() < 2) {
            return null;
        }
        long sum = byFamily.values().stream().mapToLong(LatencyTimelineStepDto::getDurationMs).sum();
        if (sum <= 0) {
            return null;
        }
        LatencyTimelineStepDto longest = byFamily.values().stream()
                .max(Comparator.comparingLong(LatencyTimelineStepDto::getDurationMs))
                .orElse(null);
        if (longest == null) {
            return null;
        }
        if (longest.getDurationMs() * 2 >= sum) {
            return null; // une famille ≥ 50 % du cumul → mono-goulot possible
        }
        long peerThreshold = Math.max(SIGNIFICANT_MS, sum * 15 / 100);
        long peers = byFamily.values().stream()
                .filter(s -> s.getDurationMs() >= peerThreshold)
                .count();
        if (peers < 2) {
            return null;
        }
        return new BottleneckResult(longest, NarrativeKind.COMPLEX);
    }

    private static String contributorFamily(String stepType) {
        if (LatencyOperationClassifier.ROOT_QUERY.equals(stepType)) {
            return "SQL";
        }
        if (LatencyOperationClassifier.OPERATION_LOAD.equals(stepType)) {
            return "OP_LOAD";
        }
        if (LatencyOperationClassifier.GLOBAL_CONTAINER.equals(stepType)
                || LatencyOperationClassifier.PARTITIONAL.equals(stepType)
                || LatencyOperationClassifier.CHILD_LOAD.equals(stepType)
                || LatencyOperationClassifier.CHILD_QUERY.equals(stepType)) {
            return "GLOBAL";
        }
        if (LatencyOperationClassifier.RULES_ENGINE.equals(stepType)
                || LatencyOperationClassifier.AUTOSTART.equals(stepType)) {
            return "RULES";
        }
        if (LatencyOperationClassifier.RENDERING.equals(stepType)) {
            return "RENDER";
        }
        if (LatencyOperationClassifier.SAVE_PERSIST.equals(stepType)
                || LatencyOperationClassifier.VALIDATION.equals(stepType)) {
            return "SAVE";
        }
        if (LatencyOperationClassifier.WEBSERVICE.equals(stepType)) {
            return "WS";
        }
        if (LatencyOperationClassifier.PRINT.equals(stepType)) {
            return "PRINT";
        }
        if (LatencyOperationClassifier.WORKFLOW.equals(stepType)) {
            return "WORKFLOW";
        }
        if (LatencyOperationClassifier.BUSINESS_ACTION.equals(stepType)) {
            return "ACTION";
        }
        return null;
    }

    /**
     * Transit task / doAction sont des enveloppes : si running rules / fire rules
     * couvrent ≥ 70 % de cette enveloppe, le goulot est le moteur de règles.
     */
    private static boolean rulesDominateNestEnvelope(LatencyTimelineStepDto rules,
                                                     List<LatencyTimelineStepDto> steps) {
        if (!isSignificant(rules)) {
            return false;
        }
        LatencyTimelineStepDto nest = findNestEnvelope(steps);
        if (nest == null || nest.getDurationMs() == null || nest.getDurationMs() <= 0) {
            return false;
        }
        if (sameStep(nest, rules)) {
            return true;
        }
        return rules.getDurationMs() >= nest.getDurationMs() * 0.7;
    }

    private static LatencyTimelineStepDto findNestEnvelope(List<LatencyTimelineStepDto> steps) {
        return steps.stream()
                .filter(s -> s.getDurationMs() != null && s.getDurationMs() > 0)
                .filter(LatencyBottleneckAnalyzer::isNestEnvelopeStep)
                .max(Comparator.comparingLong(LatencyTimelineStepDto::getDurationMs))
                .orElse(null);
    }

    private static boolean isNestEnvelopeStep(LatencyTimelineStepDto step) {
        String type = step.getStepType();
        if (LatencyOperationClassifier.WORKFLOW.equals(type)
                || LatencyOperationClassifier.BUSINESS_ACTION.equals(type)) {
            return true;
        }
        String op = step.getOperationName() != null
                ? step.getOperationName().toLowerCase(Locale.ROOT) : "";
        String detail = step.getDetail() != null
                ? step.getDetail().toLowerCase(Locale.ROOT) : "";
        String blob = op + " " + detail;
        return blob.contains("transit task") || blob.contains("doaction")
                || blob.contains("do action");
    }

    private static boolean isSqlDominated(LatencyTimelineStepDto root,
                                            LatencyTimelineStepDto container,
                                            LatencyTimelineStepDto load) {
        if (!isSignificant(root)) {
            return false;
        }
        if (root.getRowCount() != null && root.getRowCount() > FEW_ROWS_THRESHOLD) {
            return false;
        }

        long containerMs = container != null && container.getDurationMs() != null
                ? container.getDurationMs()
                : root.getDurationMs();
        double rootShare = containerMs > 0 ? (double) root.getDurationMs() / containerMs : 1.0;

        long loadMs = load != null && load.getDurationMs() != null ? load.getDurationMs() : 0;
        boolean loadIsMinor = load == null || loadMs < root.getDurationMs() * CHILD_MINOR_SHARE;

        return rootShare >= DOMINANT_SHARE && loadIsMinor;
    }

    /**
     * Une requête racine n'est le goulot que si elle pèse une part substantielle de la
     * durée maximale observée. Sinon une étape bien plus longue (règles, conteneur…)
     * existe ailleurs dans la fenêtre et c'est elle le vrai goulot.
     */
    private static boolean dwarfedByOverall(LatencyTimelineStepDto root, long overallMaxMs) {
        if (overallMaxMs <= 0 || root == null || root.getDurationMs() == null) {
            return false;
        }
        return root.getDurationMs() < overallMaxMs * 0.5;
    }

    private static boolean isParallelSearchCase(LatencyTimelineStepDto root,
                                                LatencyTimelineStepDto global,
                                                List<LatencyTimelineStepDto> steps) {
        int rows = root != null && root.getRowCount() != null ? root.getRowCount() : 0;
        if (rows <= FEW_ROWS_THRESHOLD) {
            return false;
        }
        if (global.getOperationName() != null
                && global.getOperationName().toLowerCase(Locale.ROOT).contains("multithreading")) {
            return true;
        }
        return steps.stream().anyMatch(s ->
                LatencyOperationClassifier.MULTITHREAD_START.equals(s.getStepType())
                        && s.getRowCount() != null && s.getRowCount() > 1);
    }

    private static boolean isParallelLoadDominated(LatencyTimelineStepDto load,
                                                   LatencyTimelineStepDto container,
                                                   LatencyTimelineStepDto root) {
        if (!isSignificant(load) || container == null || container.getDurationMs() == null) {
            return false;
        }
        double loadShare = (double) load.getDurationMs() / container.getDurationMs();
        if (loadShare < 0.40) {
            return false;
        }
        if (root != null && root.getDurationMs() != null && root.getDurationMs() > load.getDurationMs()) {
            return false;
        }
        int rows = root != null && root.getRowCount() != null ? root.getRowCount() : 0;
        return rows > FEW_ROWS_THRESHOLD || load.getDurationMs() >= container.getDurationMs() * 0.50;
    }

    private static boolean isRenderingDominated(LatencyTimelineStepDto rendering, List<LatencyTimelineStepDto> steps) {
        if (!isSignificant(rendering)) {
            return false;
        }
        LatencyTimelineStepDto max = maxMeasurable(steps);
        return max != null && sameStep(max, rendering);
    }

    private static boolean isContainerSelfDominant(LatencyTimelineStepDto container,
                                                     List<LatencyTimelineStepDto> steps,
                                                     LatencyOperationClassifier classifier) {
        long containerMs = container.getDurationMs();
        long childrenSum = steps.stream()
                .filter(s -> classifier.isMeasurablePhase(s.getStepType()))
                .filter(s -> !sameStep(s, container))
                .map(LatencyTimelineStepDto::getDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        return childrenSum < containerMs * CHILD_MINOR_SHARE;
    }

    private static LatencyTimelineStepDto findDominantChild(LatencyTimelineStepDto container,
                                                            List<LatencyTimelineStepDto> steps,
                                                            LatencyOperationClassifier classifier) {
        long containerMs = container.getDurationMs() != null ? container.getDurationMs() : 0;
        if (containerMs <= 0) {
            return null;
        }

        return steps.stream()
                .filter(s -> classifier.isMeasurablePhase(s.getStepType()))
                .filter(s -> !sameStep(s, container))
                .filter(s -> s.getDurationMs() != null && s.getDurationMs() > 0)
                .filter(s -> (double) s.getDurationMs() / containerMs >= DOMINANT_SHARE)
                .max(Comparator.comparingLong(LatencyTimelineStepDto::getDurationMs))
                .orElse(null);
    }

    private static NarrativeKind classifyChildDominance(LatencyTimelineStepDto step,
                                                        LatencyOperationClassifier classifier) {
        String type = step.getStepType();
        if (LatencyOperationClassifier.ROOT_QUERY.equals(type)) {
            return NarrativeKind.SQL_DOMINATED;
        }
        if (LatencyOperationClassifier.OPERATION_LOAD.equals(type)
                || LatencyOperationClassifier.CHILD_LOAD.equals(type)
                || LatencyOperationClassifier.CHILD_QUERY.equals(type)) {
            return NarrativeKind.OPERATION_LOAD_DOMINATED;
        }
        if (LatencyOperationClassifier.RENDERING.equals(type)) {
            return NarrativeKind.RENDERING_DOMINATED;
        }
        if (LatencyOperationClassifier.WEBSERVICE.equals(type)) {
            return NarrativeKind.WS_DOMINATED;
        }
        if (LatencyOperationClassifier.PRINT.equals(type)) {
            return NarrativeKind.PRINT_DOMINATED;
        }
        if (LatencyOperationClassifier.AUTOSTART.equals(type)) {
            return NarrativeKind.AUTOSTART_DOMINATED;
        }
        if (LatencyOperationClassifier.SAVE_PERSIST.equals(type)
                || LatencyOperationClassifier.VALIDATION.equals(type)) {
            return NarrativeKind.SAVE_DOMINATED;
        }
        return NarrativeKind.COMPLEX;
    }

    private static LatencyTimelineStepDto findMax(List<LatencyTimelineStepDto> steps, String type) {
        return steps.stream()
                .filter(s -> type.equals(s.getStepType()))
                .filter(s -> s.getDurationMs() != null && s.getDurationMs() > 0)
                .max(Comparator.comparingLong(LatencyTimelineStepDto::getDurationMs))
                .orElse(null);
    }

    private static LatencyTimelineStepDto maxMeasurable(List<LatencyTimelineStepDto> steps) {
        return steps.stream()
                .filter(s -> s.getDurationMs() != null && s.getDurationMs() > 0)
                .max(Comparator.comparingLong(LatencyTimelineStepDto::getDurationMs))
                .orElse(null);
    }

    private static boolean isSignificant(LatencyTimelineStepDto step) {
        return step != null && step.getDurationMs() != null && step.getDurationMs() >= SIGNIFICANT_MS;
    }

    private static boolean sameStep(LatencyTimelineStepDto a, LatencyTimelineStepDto b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.getLogId() != null && b.getLogId() != null) {
            return Objects.equals(a.getLogId(), b.getLogId());
        }
        return Objects.equals(a.getStepType(), b.getStepType())
                && Objects.equals(a.getOperationName(), b.getOperationName());
    }

    @SafeVarargs
    private static LatencyTimelineStepDto maxOf(LatencyTimelineStepDto... steps) {
        LatencyTimelineStepDto best = null;
        for (LatencyTimelineStepDto step : steps) {
            if (step == null || step.getDurationMs() == null) {
                continue;
            }
            if (best == null || step.getDurationMs() > best.getDurationMs()) {
                best = step;
            }
        }
        return best;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
