package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;
import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Génère un aperçu texte des explications pour les familles encore à valider en UI.
 * Sortie : target/latency-cases-preview.txt
 */
class LatencyCasePreviewTest {

    private final LogPatternExtractor extractor = new LogPatternExtractor();
    private final LatencyOperationClassifier classifier = new LatencyOperationClassifier();
    private final WorksLatencyTimelineParser parser = new WorksLatencyTimelineParser(extractor, classifier);
    private final LatencyCauseAnalyzer analyzer = new LatencyCauseAnalyzer(
            classifier, new WorksLatencyFactExtractor(), new ClientLatencyNarrativeBuilder());

    @Test
    void writePreviewForRemainingCases() throws Exception {
        StringBuilder out = new StringBuilder();
        int i = 1;
        preview(out, i++, "SQL lent (peu de lignes)", sqlFewRows());
        preview(out, i++, "SAVE massif", saveMassive());
        preview(out, i++, "Webservice (WS)", webservice());
        preview(out, i++, "AutoStart / TRACE_SESSIONS", autoStart());
        preview(out, i++, "Print / Imprimer", printCase());
        preview(out, i++, "COMPLEX (plusieurs étapes proches)", complexCase());

        Path path = Path.of("target/latency-cases-preview.txt");
        Files.createDirectories(path.getParent());
        Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
        System.out.println(out);
        System.out.println("Wrote " + path.toAbsolutePath());
    }

    private void preview(StringBuilder out, int n, String title, CaseScenario scenario) {
        List<LogEntry> logs = scenario.logs();
        List<LatencyTimelineStepDto> steps = parser.parseTimeline(logs);
        LatencyOriginReportDto report = new LatencyOriginReportDto();
        report.setUserName(scenario.user());
        report.setProcessName(scenario.process());
        report.setFilterCode(scenario.filter());
        report.setActionName(scenario.action());
        report.setSessionId("sess-preview");
        report.setUuid(scenario.uuid());
        long max = logs.stream()
                .map(LogEntry::getDurationMs)
                .filter(d -> d != null && d > 0)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
        report.setMaxDurationMs(max);
        report.setTimelineSteps(steps);
        analyzer.enrichReport(report, steps, logs);

        out.append("=".repeat(72)).append('\n');
        out.append("CAS ").append(n).append(" — ").append(title).append('\n');
        out.append("=".repeat(72)).append('\n');
        out.append("\n--- Logs (fenêtre) ---\n");
        for (LogEntry log : logs) {
            out.append("• ").append(log.getMessage()).append('\n');
        }
        out.append("\n--- Famille détectée ---\n");
        out.append(nullToNa(report.getLatencyFamily())).append('\n');
        out.append("\n--- Cause principale ---\n");
        out.append(nullToNa(report.getPrimaryCause())).append('\n');
        out.append("\n--- En clair ---\n");
        out.append(nullToNa(report.getClientSummary())).append('\n');
        out.append("\n--- Pourquoi (chaîne) ---\n");
        if (report.getWhyChain() == null || report.getWhyChain().isEmpty()) {
            out.append("(vide)\n");
        } else {
            int k = 1;
            for (String w : report.getWhyChain()) {
                out.append("Pourquoi ").append(k++).append(" : ").append(w).append('\n');
            }
        }
        out.append("\n--- Fiabilité ---\n");
        out.append(nullToNa(report.getAnalysisConfidence())).append(" — ")
                .append(nullToNa(report.getAnalysisConfidenceReason())).append('\n');
        out.append("\n--- Que faire ---\n");
        if (report.getRecommendations() == null || report.getRecommendations().isEmpty()) {
            out.append("(aucune)\n");
        } else {
            for (String r : report.getRecommendations()) {
                out.append("• ").append(r).append('\n');
            }
        }
        out.append("\n--- Étapes timeline ---\n");
        for (LatencyTimelineStepDto s : report.getTimelineSteps()) {
            out.append("• [").append(s.getStepType()).append("] ")
                    .append(s.getOperationName())
                    .append(s.getDurationMs() != null ? " — " + s.getDurationMs() + " ms" : " — (sans took)")
                    .append('\n');
        }
        out.append('\n');
    }

    private CaseScenario sqlFewRows() {
        String uuid = "5139523748726851988";
        return scenario("adil.benali", "process.Manifeste", "SUPERVISION ED", "LOAD", uuid, List.of(
                log(1, "searchComposantByRoot start execute filter code [SUPERVISION ED] uuid [" + uuid + "]"),
                log(2, 73455L, "prepareSearchByRoot [Query Select] took [73455] ms|1 row | className DChargementCont | filter code [SUPERVISION ED] uuid [" + uuid + "]"),
                log(3, 74532L, "global searchComposantByRoot took [74532] ms|1 row | className DChargementCont , filter code [SUPERVISION ED] uuid [" + uuid + "]"),
                log(4, 120L, "running rules ... took [120] ms"),
                log(5, 80L, "rendering result ... took [80] ms")
        ));
    }

    private CaseScenario saveMassive() {
        String uuid = "9001002003004005006";
        return scenario("ops.user", "process.ServicePortuaire", "SAVE", "Valider", uuid, List.of(
                log(1, "Start SAVE for key [ServicePortuaireCont] uuid [" + uuid + "]"),
                log(2, 42000L, "insertArrayRel works_rel_composant_child uuid [" + uuid + "] for [18420] element took [42000] ms"),
                log(3, 9100L, "insertArrayRel works_rel_composant_root uuid [" + uuid + "] for [2200] element took [9100] ms"),
                log(4, 61200L, "total time SAVE for key [ServicePortuaireCont] ; 61200 (ms)"),
                log(5, 200L, "running rules ... took [200] ms")
        ));
    }

    private CaseScenario webservice() {
        String uuid = "8351810702067977350";
        return scenario("ws.bot", "processWebService", "WS_RFID_FIND_SERVICES_2", "LOAD", uuid, List.of(
                log(1, "processWebService-0-WS_RFID_FIND_SERVICES_2-0-LOAD"),
                log(2, "uuid [" + uuid + "] Warning : aucune règle - processName [processWebService] - taskName [WS_RFID_FIND_SERVICES_2] - actionName [LOAD]"),
                log(3, 8200L, "prepareSearchByRoot [Query Select] took [8200] ms|412 row | className ServicePortuaire | filter code [Find_ServicesTagRFID] uuid [" + uuid + "]"),
                log(4, 12230L, "running rules|processWebService|WS_RFID_FIND_SERVICES_2|LOAD|1|2|in host took [12230] ms"),
                log(5, "*********** WS_RFID_FIND_SERVICES_2 ****** End")
        ));
    }

    private CaseScenario autoStart() {
        String uuid = "7008009001002003004";
        return scenario("system", "processAutoStartRules", "TRACE_SESSIONS", "SAVE", uuid, List.of(
                log(1, "START fire rules processAutoStartRules TRACE_SESSIONS"),
                log(2, 158000L, "running rules|processAutoStartRules|TRACE_SESSIONS|SAVE|null|0|in host took [158000] ms"),
                log(3, 12000L, "total time SAVE for key [SessionTrace] ; 12000 (ms)"),
                log(4, "END fire rules TRACE_SESSIONS")
        ));
    }

    private CaseScenario printCase() {
        String uuid = "6007008009001002003";
        return scenario("print.user", "process.AMPI-Imprimer", "AMPI", "Imprimer", uuid, List.of(
                log(1, "[6007008009001002003] doAction for - actionName : Imprimer, - transition : 1_Imprimer took 92000 ms"),
                log(2, "START fire rules process.AMPI-Imprimer"),
                log(3, 78000L, "running rules|process.AMPI-Imprimer|Imprimer|1_Imprimer|1|2|in host took [78000] ms"),
                log(4, 6500L, "printArchive job started took [6500] ms"),
                log(5, "END fire rules")
        ));
    }

    private CaseScenario complexCase() {
        String uuid = "1112223334445556667";
        return scenario("complex.user", "process.FilterX", "FILTER_X", "LOAD", uuid, List.of(
                log(1, 22000L, "prepareSearchByRoot [Query Select] took [22000] ms|340 row | filter code [FILTER_X] uuid [" + uuid + "]"),
                log(2, 25000L, "global searchComposantByRoot took [25000] ms|340 row | filter code [FILTER_X] uuid [" + uuid + "]"),
                log(3, 21000L, "running rules|process.FilterX|Valider|LOAD|1|2|in host took [21000] ms"),
                log(4, 18000L, "rendering result ... took [18000] ms"),
                log(5, 8000L, "total time SAVE for key [FilterX] ; 8000 (ms)")
        ));
    }

    private LogEntry log(long id, String message) {
        return log(id, null, message);
    }

    private LogEntry log(long id, Long durationMs, String message) {
        LogEntry e = new LogEntry();
        ReflectionTestUtils.setField(e, "id", id);
        e.setMessage(message);
        e.setDurationMs(durationMs);
        e.setLogTimestamp(LocalDateTime.of(2026, 6, 19, 10, 30, 0).plusSeconds(id));
        e.setUserName("preview");
        return e;
    }

    private static String nullToNa(String v) {
        return v == null || v.isBlank() ? "(n/a)" : v;
    }

    private static CaseScenario scenario(String user, String process, String filter, String action, String uuid,
                                         List<LogEntry> logs) {
        List<LogEntry> copy = new ArrayList<>(logs);
        for (LogEntry log : copy) {
            log.setUserName(user);
        }
        return new CaseScenario(user, process, filter, action, uuid, copy);
    }

    private record CaseScenario(String user, String process, String filter, String action, String uuid,
                                List<LogEntry> logs) {
    }
}
