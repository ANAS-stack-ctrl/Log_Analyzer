package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.entity.LogImport;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatencyBottleneckAnalyzerTest {

    @Test
    void gemini_detectsParallelLoadDominated() {
        LogPatternExtractor extractor = new LogPatternExtractor();
        LatencyOperationClassifier classifier = new LatencyOperationClassifier();
        WorksLatencyTimelineParser parser = new WorksLatencyTimelineParser(extractor, classifier);
        String filter = "ORDRE_TRACTION_GEMINI";
        String uuid = "1639940358752192346";
        List<LogEntry> logs = List.of(
                log(1L, 0L, "searchComposantByRoot start filter code [" + filter + "] uuid [" + uuid + "]"),
                log(2L, 14200L, "prepareSearchByRoot [Query Select] took [14200] ms|5169 row | filter code [" + filter + "] uuid [" + uuid + "]"),
                log(3L, 0L, "Start doSearch (using multiThreading) for 259 element filter code [" + filter + "] uuid [" + uuid + "]"),
                log(4L, 28750L, "loadListChilds [total] : took [28750] ms filter code [" + filter + "] uuid [" + uuid + "]"),
                log(5L, 41600L, "global searchComposantByRoot took [41600] ms|5169 row | filter code [" + filter + "] uuid [" + uuid + "]")
        );
        List<LatencyTimelineStepDto> steps = parser.parseTimeline(logs);
        LatencyBottleneckAnalyzer.BottleneckResult result =
                LatencyBottleneckAnalyzer.analyze(steps, classifier);

        assertNotNull(result);
        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.PARALLEL_LOAD_DOMINATED, result.kind());
        assertEquals(LatencyOperationClassifier.GLOBAL_CONTAINER, result.primary().getStepType());
    }

    @Test
    void supervisionEd_detectsSqlDominated() {
        List<LatencyTimelineStepDto> steps = parseSupervisionLogs();
        LatencyBottleneckAnalyzer.BottleneckResult result =
                LatencyBottleneckAnalyzer.analyze(steps, new LatencyOperationClassifier());

        assertNotNull(result);
        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.SQL_DOMINATED, result.kind());
        assertEquals(LatencyOperationClassifier.ROOT_QUERY, result.primary().getStepType());
    }

    @Test
    void printArtifact_isFlaggedSuspect_andHasNoPlausibleBottleneck() {
        LogPatternExtractor extractor = new LogPatternExtractor();
        LatencyOperationClassifier classifier = new LatencyOperationClassifier();
        WorksLatencyTimelineParser parser = new WorksLatencyTimelineParser(extractor, classifier);
        // took [221207453] ms ≈ 61 h : anomalie de mesure (flux impression PDF ETAT CTRL AMP).
        List<LogEntry> logs = List.of(
                log(100L, 221207453L, "prepareSearchByRoot [Query Select] took [221207453] ms|247 row | className ServicePortuaireServicePortuaire | filter code [PDF ETAT CTRL AMP] uuid [-4976848940943194057]"),
                log(101L, 221208076L, "global searchComposantByRoot (Multithreading) took [221208076] ms|247 row | className ServicePortuaire , filter code [PDF ETAT CTRL AMP] uuid [-4976848940943194057]")
        );
        List<LatencyTimelineStepDto> steps = parser.parseTimeline(logs);

        assertTrue(steps.stream().allMatch(LatencyTimelineStepDto::isSuspect),
                "Toutes les étapes anomaliques doivent être marquées suspect");
        assertNull(LatencyBottleneckAnalyzer.analyze(steps, classifier),
                "Aucun goulot fiable ne doit être retourné quand tout est anomalique");
    }

    @Test
    void realLongQuery_33min_zeroRow_isSqlDominated_notSuspect() {
        LogPatternExtractor extractor = new LogPatternExtractor();
        LatencyOperationClassifier classifier = new LatencyOperationClassifier();
        WorksLatencyTimelineParser parser = new WorksLatencyTimelineParser(extractor, classifier);
        String filter = "0665 Services a facturer AMPI";
        String uuid = "-127337174171104825";
        // took [2025068] ms ≈ 33 min : lenteur SQL RÉELLE (0 ligne → index manquant). < 1 h donc non suspect.
        List<LogEntry> logs = List.of(
                log(200L, 0L, "searchComposantByRoot start execute filter code [" + filter + "] uuid [" + uuid + "]"),
                log(201L, 2025068L, "prepareSearchByRoot [Query Select] took [2025068] ms|0 row | className ServicePortuaireServicePortuaire | filter code [" + filter + "] uuid [" + uuid + "]"),
                log(202L, 2025068L, "global searchComposantByRoot took [2025068] ms|0 row | className ServicePortuaire , filter code [" + filter + "] uuid [" + uuid + "]")
        );
        List<LatencyTimelineStepDto> steps = parser.parseTimeline(logs);

        assertTrue(steps.stream().noneMatch(LatencyTimelineStepDto::isSuspect),
                "Une requête de 33 min reste plausible (< 1 h) et ne doit pas être marquée suspect");
        LatencyBottleneckAnalyzer.BottleneckResult result =
                LatencyBottleneckAnalyzer.analyze(steps, classifier);
        assertNotNull(result);
        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.SQL_DOMINATED, result.kind());
    }

    @Test
    void matchall_rulesInsideTransit_dominatesOverWorkflowEnvelope() {
        LatencyOperationClassifier classifier = new LatencyOperationClassifier();
        // Cas réel MATCX matchall : Transit ~82 s enveloppe running rules ~64 s ;
        // un global search court (7 s) ne doit pas voler le goulot.
        List<LatencyTimelineStepDto> steps = List.of(
                step(1L, LatencyOperationClassifier.GLOBAL_CONTAINER,
                        "global searchComposantByRoot [multithreading]", 7321L, 248),
                step(2L, LatencyOperationClassifier.ROOT_QUERY,
                        "prepareSearchByRoot [Query Select]", 578L, 248),
                step(3L, LatencyOperationClassifier.RULES_ENGINE,
                        "running rules (in host)", 64100L, null),
                step(4L, LatencyOperationClassifier.SAVE_PERSIST,
                        "total time SAVE", 1700L, null),
                step(5L, LatencyOperationClassifier.WORKFLOW,
                        "Transit task", 82061L, null)
        );

        LatencyBottleneckAnalyzer.BottleneckResult result =
                LatencyBottleneckAnalyzer.analyze(steps, classifier);

        assertNotNull(result);
        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED, result.kind(),
                "running rules (~78 % du Transit) doit primer sur l'enveloppe workflow");
        assertEquals(LatencyOperationClassifier.RULES_ENGINE, result.primary().getStepType());
        assertEquals(64100L, result.primary().getDurationMs());
    }

    @Test
    void mixedWindow_picksPlausibleBottleneck_overSuspect() {
        LogPatternExtractor extractor = new LogPatternExtractor();
        LatencyOperationClassifier classifier = new LatencyOperationClassifier();
        WorksLatencyTimelineParser parser = new WorksLatencyTimelineParser(extractor, classifier);
        List<LogEntry> logs = List.of(
                log(300L, 221207453L, "prepareSearchByRoot [Query Select] took [221207453] ms|247 row | className ServicePortuaireServicePortuaire | filter code [PDF ETAT CTRL AMP] uuid [-1]"),
                log(301L, 30000L, "running rules|process.Manifeste|Valider|Valider|1|2|in host took [30000] ms")
        );
        List<LatencyTimelineStepDto> steps = parser.parseTimeline(logs);
        LatencyBottleneckAnalyzer.BottleneckResult result =
                LatencyBottleneckAnalyzer.analyze(steps, classifier);

        assertNotNull(result);
        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED, result.kind());
        assertFalse(result.primary().isSuspect(), "Le goulot retenu ne doit jamais être une étape suspecte");
    }

    @Test
    void realLongSave_under6h_isNotSuspect_butArtifactOver6h_is() {
        LogPatternExtractor extractor = new LogPatternExtractor();
        LatencyOperationClassifier classifier = new LatencyOperationClassifier();
        WorksLatencyTimelineParser parser = new WorksLatencyTimelineParser(extractor, classifier);
        // SAVE réel de ~73 min (4 420 000 ms) observé même jour dans le dataset → plausible.
        List<LogEntry> realSave = List.of(
                log(400L, 4420000L, "saveInstanceOperation -0--0-SAVE took [4420000] ms uuid [-1]"));
        // Artefact ~13 h (46 800 000 ms), début multi-jours → suspect.
        List<LogEntry> artifact = List.of(
                log(401L, 46800000L, "saveInstanceOperation -0--0-SAVE took [46800000] ms uuid [-2]"));

        assertTrue(parser.parseTimeline(realSave).stream().noneMatch(LatencyTimelineStepDto::isSuspect),
                "Un SAVE réel de 73 min (< 6 h) ne doit pas être marqué suspect");
        assertTrue(parser.parseTimeline(artifact).stream().allMatch(LatencyTimelineStepDto::isSuspect),
                "Un took de 13 h (> 6 h) doit être marqué suspect");
    }

    private List<LatencyTimelineStepDto> parseSupervisionLogs() {
        LogPatternExtractor extractor = new LogPatternExtractor();
        WorksLatencyTimelineParser parser = new WorksLatencyTimelineParser(extractor, new LatencyOperationClassifier());
        String filter = "SUPERVISION ED";
        String uuid = "5139523748726851988";
        List<LogEntry> logs = List.of(
                log(10L, 73455L, "searchComposantByRoot start execute filter code [" + filter + "] uuid [" + uuid + "]"),
                log(12L, 73455L, "prepareSearchByRoot [Query Select] took [73455] ms|1 row | filter code [" + filter + "] uuid [" + uuid + "]"),
                log(16L, 1076L, "loadListChilds [total] : took [1076] ms filter code [" + filter + "] uuid [" + uuid + "]"),
                log(18L, 74532L, "global searchComposantByRoot took [74532] ms|1 row | filter code [" + filter + "] uuid [" + uuid + "]")
        );
        return parser.parseTimeline(logs);
    }

    private LatencyTimelineStepDto step(Long logId, String type, String name, Long ms, Integer rows) {
        LatencyTimelineStepDto s = new LatencyTimelineStepDto();
        s.setLogId(logId);
        s.setStepType(type);
        s.setOperationName(name);
        s.setDurationMs(ms);
        s.setRowCount(rows);
        return s;
    }

    private LogEntry log(Long id, Long durationMs, String message) {
        LogEntry log = new LogEntry();
        LogImport imp = new LogImport();
        ReflectionTestUtils.setField(imp, "id", 13L);
        log.setLogImport(imp);
        ReflectionTestUtils.setField(log, "id", id);
        log.setDurationMs(durationMs);
        log.setMessage(message);
        log.setLogTimestamp(LocalDateTime.of(2026, 6, 19, 5, 12, id.intValue() % 60));
        return log;
    }
}
