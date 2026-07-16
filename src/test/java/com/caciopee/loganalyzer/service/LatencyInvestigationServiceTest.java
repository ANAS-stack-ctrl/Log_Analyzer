package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.latency.ClientLatencyNarrativeBuilder;
import com.caciopee.loganalyzer.analysis.latency.LatencyCauseAnalyzer;
import com.caciopee.loganalyzer.analysis.latency.LatencyOperationClassifier;
import com.caciopee.loganalyzer.analysis.latency.WorksLatencyFactExtractor;
import com.caciopee.loganalyzer.analysis.latency.WorksLatencyTimelineParser;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.entity.LogImport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LatencyInvestigationServiceTest {

    @Mock
    private com.caciopee.loganalyzer.repository.LogEntryRepository logEntryRepository;

    private LatencyInvestigationService service;

    @BeforeEach
    void setUp() {
        LogPatternExtractor extractor = new LogPatternExtractor();
        LatencyOperationClassifier classifier = new LatencyOperationClassifier();
        WorksLatencyTimelineParser parser = new WorksLatencyTimelineParser(extractor, classifier);
        WorksLatencyFactExtractor factExtractor = new WorksLatencyFactExtractor();
        ClientLatencyNarrativeBuilder narrativeBuilder = new ClientLatencyNarrativeBuilder();
        LatencyCauseAnalyzer analyzer = new LatencyCauseAnalyzer(classifier, factExtractor, narrativeBuilder);
        com.caciopee.loganalyzer.analysis.latency.LatencyRootCauseClassifier rootCauseClassifier =
                new com.caciopee.loganalyzer.analysis.latency.LatencyRootCauseClassifier();
        service = new LatencyInvestigationService(logEntryRepository, extractor, parser, analyzer, rootCauseClassifier);
    }

    @Test
    void investigate_complexFilterSearch_buildsModelNarrative() {
        Long importId = 8L;
        String sessionId = "sess-gemini";
        String uuid = "1639940358752192346";
        String filter = "ORDRE_TRACTION_GEMINI";

        List<LogEntry> logs = List.of(
                log(1L, importId, sessionId, "mohamed.dkhissi", null,
                        "searchComposantByRoot start execute filter code [" + filter + "] uuid [" + uuid + "]"),
                log(2L, importId, sessionId, "mohamed.dkhissi", null,
                        "searchComposantByRoot ==> using multiThreading for [5169] ids, partitionSize [20], nbrThreadPool [4] filter code [" + filter + "] uuid [" + uuid + "]"),
                log(3L, importId, sessionId, "mohamed.dkhissi", 14200L,
                        "prepareSearchByRoot [Query Select] took [14200] ms|5169 row | className ServicePortuaire | filter code [" + filter + "] uuid [" + uuid + "]"),
                log(4L, importId, sessionId, "mohamed.dkhissi", null,
                        "memory usage (Mo) [prepareSearchByRoot 5169 row - className ServicePortuaire - filter code [" + filter + "] uuid [" + uuid + "]] 7144"),
                log(5L, importId, sessionId, "mohamed.dkhissi", null,
                        "Start doSearch (using multiThreading) for 259 element filter code [" + filter + "] uuid [" + uuid + "]"),
                log(6L, importId, sessionId, "mohamed.dkhissi", 28750L,
                        " loadListChilds [total] : took [28750] ms filter code [" + filter + "] uuid [" + uuid + "]"),
                log(100L, importId, sessionId, "mohamed.dkhissi", 41600L,
                        "global searchComposantByRoot took [41600] ms|5169 row | className ServicePortuaire , filter code [" + filter + "] uuid [" + uuid + "]"),
                log(101L, importId, sessionId, "mohamed.dkhissi", 1200L,
                        "rendering result for " + filter + " took [1200] ms uuid [" + uuid + "]")
        );

        when(logEntryRepository.findByLogImportIdAndSessionIdOrderByLogTimestampAsc(importId, sessionId))
                .thenReturn(logs);

        LatencyOriginReportDto report = service.investigate(importId, 1L, sessionId, uuid, filter, "processFilter");

        assertEquals("HIGH", report.getAnalysisConfidence());
        assertEquals(uuid, report.getUuid());
        assertNotNull(report.getClientSummary());
        assertTrue(report.getClientSummary().contains("5") && report.getClientSummary().contains("169"));
        assertTrue(report.getNarrativeSummary().toLowerCase().contains("parallèle"));
        assertTrue(report.getNarrativeSummary().contains("259") || report.getClientSummary().contains("259"));
        assertTrue(report.getPrimaryCause().toLowerCase().contains("parallèle")
                || report.getPrimaryCause().contains("41"));
        assertTrue(report.getLogsInWindow() > 5);
        assertTrue(report.getTimelineSteps().size() >= 3);
    }

    @Test
    void investigate_supervisionEd_sqlDominatedCase() {
        Long importId = 13L;
        String sessionId = "1781842330613";
        String filter = "SUPERVISION ED";
        String uuid = "5139523748726851988";

        List<LogEntry> logs = new ArrayList<>(List.of(
                log(10L, importId, sessionId, "adil.benali", null,
                        "searchComposantByRoot start execute filter code [" + filter + "]  uuid  [" + uuid + "]"),
                log(11L, importId, sessionId, "adil.benali", null,
                        "filter code [" + filter + "] uuid  [" + uuid + "] Query: select comp0.composant_id from works_composant comp0 "
                                + "inner join works_rel_composant_child rel_fils1 on rel_fils1.composant_fk = comp0.composant_id "
                                + "inner join works_composant comp1 on rel_fils1.composant2_fk = comp1.composant_id "
                                + "inner join works_detail_composant det_comp_fils1 on det_comp_fils1.composant_fk = comp1.composant_id"),
                log(12L, importId, sessionId, "adil.benali", 73455L,
                        "prepareSearchByRoot [Query Select] took [73455] ms|1 row | className DChargementCont | filter code [" + filter + "] uuid  [" + uuid + "]"),
                log(13L, importId, sessionId, "adil.benali", null,
                        "memory usage (Mo) [prepareSearchByRoot 1 row - className DChargementCont - filter code [" + filter + "] uuid  [" + uuid + "] ] 2867"),
                log(14L, importId, sessionId, "adil.benali", null,
                        "searchComposantByRoot ==> ignore multiThreading for [1] ids filter code [" + filter + "] uuid  [" + uuid + "]"),
                log(15L, importId, sessionId, "adil.benali", null,
                        "Start doSearch  for 1 element filter code [" + filter + "] uuid  [" + uuid + "]"),
                log(16L, importId, sessionId, "adil.benali", 1076L,
                        " loadListChilds [total] : took [1076] ms filter code [" + filter + "] uuid  [" + uuid + "]"),
                log(17L, importId, sessionId, "adil.benali", 561L,
                        "searchAttributesList filter code [" + filter + "] uuid  [" + uuid + "] [Query] took [561] ms"),
                log(18L, importId, sessionId, "adil.benali", 74532L,
                        "global searchComposantByRoot took [74532] ms|1 row | className DChargementCont , filter code [" + filter + "] uuid [" + uuid + "]"),
                log(19L, importId, sessionId, "adil.benali", null,
                        "searchComposantByRoot end execute filter code [" + filter + "] uuid  [" + uuid + "] , [1] row fetched")
        ));

        LogEntry weakEvidence = log(99L, importId, sessionId, "adil.benali", 0L,
                "saveTempComposantsLoaded using [ union (default) ] uuid [" + uuid + "] took [0] ms");
        logs.add(weakEvidence);

        when(logEntryRepository.findByLogImportIdAndSessionIdOrderByLogTimestampAsc(importId, sessionId))
                .thenReturn(logs);

        LatencyOriginReportDto report = service.investigate(importId, null, sessionId, uuid, filter, "Manifeste");

        assertEquals("HIGH", report.getAnalysisConfidence());
        assertTrue(report.getLogsInWindow() >= 8);
        assertTrue(report.getNarrativeSummary().toLowerCase().contains("requête sql"));
        assertTrue(report.getNarrativeSummary().contains("73"));
        assertTrue(report.getNarrativeSummary().contains("1 enregistrement") || report.getNarrativeSummary().contains("1 ligne"));
        assertTrue(report.getPrimaryCause().toLowerCase().contains("requête sql"));
        assertNotNull(report.getClientSummary());
        assertTrue(report.getClientSummary().toLowerCase().contains("requête sql"));
        assertEquals(74532L, report.getMaxDurationMs());
        assertTrue(report.getTimelineSteps().stream()
                .anyMatch(s -> "ROOT_QUERY".equals(s.getStepType()) && s.getDurationMs() >= 73000));
    }

    @Test
    void investigate_ignoresWeakEvidenceLogId() {
        Long importId = 13L;
        String sessionId = "1781842330613";
        String filter = "SUPERVISION ED";
        String uuid = "5139523748726851988";

        LogEntry slow = log(18L, importId, sessionId, "adil.benali", 74532L,
                "global searchComposantByRoot took [74532] ms|1 row | filter code [" + filter + "] uuid [" + uuid + "]");
        LogEntry weak = log(99L, importId, sessionId, "adil.benali", 0L,
                "saveTempComposantsLoaded uuid [" + uuid + "] took [0] ms");

        List<LogEntry> logs = List.of(
                log(12L, importId, sessionId, "adil.benali", 73455L,
                        "prepareSearchByRoot [Query Select] took [73455] ms|1 row | filter code [" + filter + "] uuid [" + uuid + "]"),
                slow,
                weak
        );

        when(logEntryRepository.findById(99L)).thenReturn(Optional.of(weak));
        when(logEntryRepository.findByLogImportIdAndSessionIdOrderByLogTimestampAsc(importId, sessionId))
                .thenReturn(logs);

        LatencyOriginReportDto report = service.investigate(importId, 99L, sessionId, uuid, filter, "Manifeste");

        assertEquals(18L, report.getAnchorLogId());
        assertNotNull(report.getNarrativeSummary());
    }

    @Test
    void investigate_rulesEngineLatency() {
        Long importId = 129L;
        String sessionId = "1781861747049";
        String filter = "AMPEZRE";

        List<LogEntry> logs = List.of(
                log(1L, importId, sessionId, "YACHOU.AYOUB", 7321L,
                        "global searchComposantByRoot [multithreading] took [7321] ms|248 row | className ServicePortuaire | filter code [AMPEZRE]"),
                log(2L, importId, sessionId, "YACHOU.AYOUB", null,
                        "MAJ_Insert_DynaScreen_AMPE_ZRE for composant"),
                log(3L, importId, sessionId, "YACHOU.AYOUB", null,
                        "MAJ_Insert_DynaScreen_AMPE_ZRE for composant"),
                log(4L, importId, sessionId, "YACHOU.AYOUB", 82104L,
                        "doAction for - actionName : Rapprocher Tout, - transition : matchall took 82104 ms"),
                log(5L, importId, sessionId, "YACHOU.AYOUB", 79867L,
                        "running rules|process.MATCX_ZREAMP|Match|matchall|533179467|533179469|in host took [79867] ms"),
                log(6L, importId, sessionId, "YACHOU.AYOUB", 1826L,
                        "total time SAVE for key [ServicePortuaireCont] ; 1826 (ms)")
        );

        when(logEntryRepository.findByLogImportIdAndSessionIdOrderByLogTimestampAsc(importId, sessionId))
                .thenReturn(logs);

        LatencyOriginReportDto report = service.investigate(importId, null, sessionId, null, filter, "-0--0-SAVE");

        assertEquals("HIGH", report.getAnalysisConfidence());
        assertTrue(report.getPrimaryCause().toLowerCase().contains("règles")
                || report.getPrimaryCause().toLowerCase().contains("maj_insert"), report.getPrimaryCause());
        assertTrue(report.getNarrativeSummary().contains("Rapprocher Tout"), report.getNarrativeSummary());
        assertTrue(report.getNarrativeSummary().contains("MATCX_ZREAMP"), report.getNarrativeSummary());
        assertFalse(report.getNarrativeSummary().contains("-0--0-SAVE"), report.getNarrativeSummary());
        assertEquals("process.MATCX_ZREAMP", report.getProcessName());
        assertEquals("Rapprocher Tout", report.getActionName());
        // doAction (82 s) enveloppe les règles (80 s) : c'est le temps perçu utilisateur.
        assertTrue(report.getMaxDurationMs() >= 79867L, String.valueOf(report.getMaxDurationMs()));
        assertTrue(report.getMaxDurationMs() <= 82104L, String.valueOf(report.getMaxDurationMs()));
    }

    @Test
    void investigate_transitAnchor_recoversRulesBuriedUnderMajInsertNoise() {
        Long importId = 130L;
        String sessionId = "1781861748911";
        LocalDateTime base = LocalDateTime.of(2026, 6, 19, 10, 34, 0);

        List<LogEntry> logs = new ArrayList<>();
        logs.add(logAt(1L, importId, sessionId, "YACHOU.AYOUB", 7321L, base,
                "global searchComposantByRoot [multithreading] took [7321] ms|248 row | filter code [AMPEZRE]"));
        // >800 lignes MAJ entre les règles et Transit : l'ancien lookback marqueur les ratait.
        for (int i = 0; i < 900; i++) {
            logs.add(logAt(10L + i, importId, sessionId, "YACHOU.AYOUB", null,
                    base.plusSeconds(1 + i / 20),
                    "MAJ_Insert_DynaScreen_AMPE_ZRE for composant #" + i));
        }
        logs.add(logAt(1000L, importId, sessionId, "YACHOU.AYOUB", 64100L,
                base.plusSeconds(80),
                "running rules|process.MATCX_ZREAMP|Match|matchall|533179467|533179469|in host took [64100] ms"));
        logs.add(logAt(1001L, importId, sessionId, "YACHOU.AYOUB", 1700L,
                base.plusSeconds(82),
                "total time SAVE for key [ServicePortuaireCont] ; 1700 (ms)"));
        Long transitId = 1002L;
        logs.add(logAt(transitId, importId, sessionId, "YACHOU.AYOUB", 84049L,
                base.plusSeconds(84),
                "Transit task |process.MATCX_ZREAMP|Match|matchall|533179467|533179469|in host took [84049] ms"));

        when(logEntryRepository.findByLogImportIdAndSessionIdOrderByLogTimestampAsc(importId, sessionId))
                .thenReturn(logs);
        when(logEntryRepository.findById(transitId)).thenReturn(Optional.of(logs.get(logs.size() - 1)));

        LatencyOriginReportDto report = service.investigate(
                importId, transitId, sessionId, null, null, "process.MATCX_ZREAMP");

        assertEquals("RULES_DOMINATED", report.getLatencyFamily(),
                "Transit ancré + running rules dans la fenêtre temporelle → règles, pas workflow opaque");
        assertTrue(report.getPrimaryCause().toLowerCase().contains("règle")
                        || report.getPrimaryCause().toLowerCase().contains("fire"),
                report.getPrimaryCause());
        assertFalse(report.getWhyChain() != null && report.getWhyChain().stream()
                        .anyMatch(w -> w.toLowerCase().contains("ne permettent pas")),
                String.valueOf(report.getWhyChain()));
    }

    @Test
    void investigate_parallelLoadDominated() {
        Long importId = 8L;
        String sessionId = "sess-load";
        String uuid = "1639940358752192346";
        String filter = "ORDRE_TRACTION_GEMINI";

        List<LogEntry> logs = List.of(
                log(1L, importId, sessionId, "user", null,
                        "searchComposantByRoot start execute filter code [" + filter + "] uuid [" + uuid + "]"),
                log(2L, importId, sessionId, "user", null,
                        "searchComposantByRoot ==> using multiThreading for [5169] ids, partitionSize [20] filter code [" + filter + "] uuid [" + uuid + "]"),
                log(3L, importId, sessionId, "user", 14200L,
                        "Start doSearch (using multiThreading) for 259 element filter code [" + filter + "] uuid [" + uuid + "]"),
                log(4L, importId, sessionId, "user", 14200L,
                        "prepareSearchByRoot [Query Select] took [14200] ms|5169 row | filter code [" + filter + "] uuid [" + uuid + "]"),
                log(5L, importId, sessionId, "user", 28750L,
                        "loadListChilds [total] : took [28750] ms filter code [" + filter + "] uuid [" + uuid + "]"),
                log(6L, importId, sessionId, "user", 41600L,
                        "global searchComposantByRoot took [41600] ms|5169 row | filter code [" + filter + "] uuid [" + uuid + "]")
        );

        when(logEntryRepository.findByLogImportIdAndSessionIdOrderByLogTimestampAsc(importId, sessionId))
                .thenReturn(logs);

        LatencyOriginReportDto report = service.investigate(importId, null, sessionId, uuid, filter, "processFilter");

        assertEquals("HIGH", report.getAnalysisConfidence());
        assertTrue(report.getPrimaryCause().toLowerCase().contains("parallèle"));
        assertTrue(report.getNarrativeSummary().toLowerCase().contains("parallèle"));
        assertTrue(report.getNarrativeSummary().contains("259"));
    }

    private LogEntry log(Long id, Long importId, String sessionId, String user, Long durationMs, String message) {
        return logAt(id, importId, sessionId, user, durationMs,
                LocalDateTime.of(2026, 6, 19, 5, 12, (int) (id % 60)), message);
    }

    private LogEntry logAt(Long id, Long importId, String sessionId, String user, Long durationMs,
                           LocalDateTime ts, String message) {
        LogEntry log = new LogEntry();
        LogImport imp = new LogImport();
        ReflectionTestUtils.setField(imp, "id", importId);
        log.setLogImport(imp);
        ReflectionTestUtils.setField(log, "id", id);
        log.setSessionId(sessionId);
        log.setUserName(user);
        log.setDurationMs(durationMs);
        log.setMessage(message);
        log.setLogTimestamp(ts);
        return log;
    }
}
