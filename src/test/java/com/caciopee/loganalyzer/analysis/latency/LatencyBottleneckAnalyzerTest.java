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
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
