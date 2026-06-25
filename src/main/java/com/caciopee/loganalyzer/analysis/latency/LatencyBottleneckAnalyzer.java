package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
        CONTAINER_DOMINATED,
        SINGLE_STEP,
        COMPLEX
    }

    public record BottleneckResult(LatencyTimelineStepDto primary, NarrativeKind kind) {
    }

    public static BottleneckResult analyze(List<LatencyTimelineStepDto> steps,
                                           LatencyOperationClassifier classifier) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }

        LatencyTimelineStepDto root = find(steps, LatencyOperationClassifier.ROOT_QUERY);
        LatencyTimelineStepDto global = find(steps, LatencyOperationClassifier.GLOBAL_CONTAINER);
        LatencyTimelineStepDto partitional = find(steps, LatencyOperationClassifier.PARTITIONAL);
        LatencyTimelineStepDto load = find(steps, LatencyOperationClassifier.CHILD_LOAD);
        LatencyTimelineStepDto rules = find(steps, LatencyOperationClassifier.RULES_ENGINE);
        LatencyTimelineStepDto rendering = find(steps, LatencyOperationClassifier.RENDERING);

        LatencyTimelineStepDto container = firstNonNull(global, partitional, rules);

        if (root != null && isSqlDominated(root, container, load)) {
            return new BottleneckResult(root, NarrativeKind.SQL_DOMINATED);
        }

        if (global != null && isSignificant(global) && isParallelSearchCase(root, global, steps)) {
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
        if (LatencyOperationClassifier.CHILD_LOAD.equals(type)) {
            return NarrativeKind.PARALLEL_LOAD_DOMINATED;
        }
        if (LatencyOperationClassifier.RENDERING.equals(type)) {
            return NarrativeKind.RENDERING_DOMINATED;
        }
        return NarrativeKind.COMPLEX;
    }

    private static LatencyTimelineStepDto find(List<LatencyTimelineStepDto> steps, String type) {
        return steps.stream()
                .filter(s -> type.equals(s.getStepType()))
                .findFirst()
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
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
