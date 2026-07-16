package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorksLatencyTimelineParserTest {

    private final WorksLatencyTimelineParser parser = new WorksLatencyTimelineParser(
            new LogPatternExtractor(), new LatencyOperationClassifier());

    @Test
    void doAction_withRawLogPipeSuffix_stillProducesTimedStep() {
        LogEntry log = new LogEntry();
        log.setMessage(
                "[3541956776772717142] doAction for - actionName : Rapprocher Tout, - transition : matchall took 84107 ms");
        // Forme production : rawLog porte le suffixe |||env qui cassait l'ancien extractMessagePayload.
        log.setRawLog(
                "2026-06-19 10:30:00|INFO|YACHOU.AYOUB|session|3541956776772717142|"
                        + "[3541956776772717142] doAction for - actionName : Rapprocher Tout, - transition : matchall took 84107 ms"
                        + "|logCode|||DS:PROD|server01|extra");
        org.springframework.test.util.ReflectionTestUtils.setField(log, "id", 99L);

        List<LatencyTimelineStepDto> steps = parser.parseTimeline(List.of(log));

        assertFalse(steps.isEmpty(), "doAction+rawLog||| ne doit plus produire une timeline vide");
        assertEquals(LatencyOperationClassifier.BUSINESS_ACTION, steps.get(0).getStepType());
        assertEquals(84107L, steps.get(0).getDurationMs());
        assertTrue(steps.get(0).getOperationName().toLowerCase().contains("rapprocher"),
                steps.get(0).getOperationName());
    }

    @Test
    void startFireRules_withoutTook_stillBecomesMarkerStep() {
        LogEntry log = new LogEntry();
        log.setMessage("START fire rules for process.MATCX_ZREAMP");
        List<LatencyTimelineStepDto> steps = parser.parseTimeline(List.of(log));
        assertEquals(1, steps.size());
        assertEquals(LatencyOperationClassifier.RULES_ENGINE, steps.get(0).getStepType());
        assertEquals("START fire rules", steps.get(0).getOperationName());
    }
}
