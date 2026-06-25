package com.caciopee.loganalyzer.analysis.catalog;

import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit automatique : extraction graphe (process, task, filter, object, action)
 * et couverture du catalogue des familles WORKS — sans import manuel.
 */
class ExtractionQualityAuditTest {

    private static final int MAX_LINES_PER_FILE = 8_000;
    private static final double MIN_CATALOG_COVERAGE = 99.0;
    private static final double MIN_GRAPH_FAMILY_COVERAGE = 92.0;
    private static final double MIN_PROCESS_EXTRACTED = 99.5;
    private static final double MIN_ACTION_WHEN_EXPECTED = 99.0;
    private static final double MIN_TASK_WHEN_EXPECTED = 85.0;
    private static final double MIN_OBJECT_WHEN_EXPECTED = 95.0;
    private static final double MIN_FILTER_EXTRACTED = 95.0;
    private static final double MIN_TASK_ON_WEBSERVICE = 90.0;
    private static final double MIN_FILTER_ON_PROCESS_FILTER = 99.0;
    private static final double MAX_SUSPICIOUS_TASK_RATE = 2.0;

    private LogPatternExtractor extractor;
    private WorksMessageCatalogService catalog;

    @BeforeEach
    void setUp() {
        extractor = new LogPatternExtractor();
        catalog = new WorksMessageCatalogService(new ObjectMapper());
        catalog.loadCatalog();
    }

    @Test
    void allCatalogFamiliesHaveMatchingProbe() {
        List<String> failures = new ArrayList<>();
        for (WorksMessageFamily family : catalog.getFamilies()) {
            LogEntry log = buildProbeForFamily(family);
            var matched = catalog.classify(log);
            if (matched.isEmpty() || !family.getId().equals(matched.get().getId())) {
                failures.add(family.getId() + " (" + family.getLabel() + ")");
            }
        }
        assertTrue(failures.isEmpty(),
                () -> "Familles non reconnues par leur sonde: " + failures);
    }

    @Test
    void graphFamiliesHaveMatchingProbeWhenAttachToGraph() {
        List<String> failures = new ArrayList<>();
        for (WorksMessageFamily family : catalog.getFamilies()) {
            if (!family.isAttachToGraph()) {
                continue;
            }
            LogEntry log = buildProbeForFamily(family);
            var matched = catalog.classifyForGraph(log);
            if (matched.isEmpty() || !family.getId().equals(matched.get().getId())) {
                failures.add(family.getId());
            }
        }
        assertTrue(failures.isEmpty(),
                () -> "Familles graphe non reconnues: " + failures);
    }

    private LogEntry buildProbeForFamily(WorksMessageFamily family) {
        Probe explicit = PROBES.get(family.getId());
        if (explicit != null) {
            return explicit.toLogEntry();
        }
        if (family.isColumnOnly() && !family.getPatterns().isEmpty()) {
            String column = columnSampleFromPattern(family.getPatterns().get(0));
            LogEntry log = new LogEntry();
            log.setProcessName(column);
            log.setMessage("probe");
            log.setLevel("DEBUG");
            return log;
        }
        String message = messageSampleFromPatterns(family.getPatterns());
        LogEntry log = new LogEntry();
        log.setProcessName("processFilter-0-PROBE-0-LOAD");
        log.setMessage(message);
        log.setLevel("DEBUG");
        return log;
    }

    private static String columnSampleFromPattern(String regex) {
        if (regex.startsWith("^processFilter")) {
            return "processFilter-0-PROBE-0-LOAD";
        }
        if (regex.startsWith("^processWebService")) {
            return "processWebService-0-WS_PROBE-0-LOAD";
        }
        if (regex.startsWith("^processRunRules")) {
            return "processRunRules-0-DATA_LOAD_TASK-0-SAVE";
        }
        if (regex.contains("AMPE Contr")) {
            return "process.AMPE Contrôle Exploitation-0-SAVE";
        }
        if (regex.contains("MATCX")) {
            return "process.MATCX_ZREAMP-0-LOAD";
        }
        if (regex.contains("SUPERVISION ORDRE")) {
            return "process.SUPERVISION ORDRE TRACTION-0-Start";
        }
        if (regex.contains("TractionInterne")) {
            return "process.TractionInterneDMAFI-0-LOAD";
        }
        if (regex.contains("tractionExterneSurAMP_Demat")) {
            return "process.tractionExterneSurAMP_Demat-0-LOAD";
        }
        if (regex.contains("tractionExterne")) {
            return "process.tractionExterneSurAMP-0-LOAD";
        }
        if (regex.contains("PESAGE_DEMAT")) {
            return "process.SUPERVISION_PESAGE_DEMAT-0-LOAD";
        }
        if (regex.contains("SZVCIZRDV")) {
            return "process.SZVCIZRDV-0-LOAD";
        }
        if (regex.contains("SORTIE MEDHUB PRINT")) {
            return "process.SORTIE MEDHUB PRINT-0-LOAD";
        }
        if (regex.contains("OTRIMAFI")) {
            return "process.OTRIMAFI-0-LOAD";
        }
        if (regex.contains("RAPPORT CTRL")) {
            return "process.RAPPORT CTRL AMP-0-LOAD";
        }
        if (regex.contains("DematTractionExport")) {
            return "process.DematTractionExport-0-LOAD";
        }
        if (regex.contains("AMPI")) {
            return "process.AMPI Imprimer Statut Administratif-0-LOAD";
        }
        if (regex.contains("MEDHUB")) {
            return "process.ENTREeMEDHUB-0-LOAD";
        }
        if (regex.contains("AMPE SUPERVISION")) {
            return "process.AMPE SUPERVISION-0-LOAD";
        }
        if (regex.contains("AMPE")) {
            return "process.AMPE-0-LOAD";
        }
        return "process.PROBE_METIER-0-LOAD";
    }

    private static String messageSampleFromPatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return "uuid [1]";
        }
        String p = patterns.get(0);
        if (p.contains("filter code")) {
            return "filter code [PROBE_FILTER] uuid [1]";
        }
        if (p.contains("fetching filter")) {
            return "fetching filter [PROBE] bean: HashMap[]";
        }
        if (p.contains("searchComposant")) {
            return "searchComposantByRoot probe uuid [1]";
        }
        if (p.startsWith("^-")) {
            return "------------------- WS_PROBE Start Filtre X -------------------";
        }
        return p.replace("\\s*", " ").replace("\\[", "[").replace("\\d+", "1")
                .replace("^", "").replace("$", "").replace(".*", " ").trim() + " uuid [1]";
    }

    @Test
    void extractionFixtures_matchExpectedGraphFields() {
        List<String> failures = new ArrayList<>();
        for (ExtractionFixture fx : FIXTURES) {
            GraphExtraction g = extractor.extractGraph(fx.log());
            if (!fx.matches(g)) {
                failures.add(fx.name() + " → " + fx.describeMismatch(g));
            }
        }
        assertTrue(failures.isEmpty(), () -> "Échecs extraction:\n" + String.join("\n", failures));
    }

    @Test
    void suspiciousTaskOnProcessFilter_isRareInFixtures() {
        long suspicious = FIXTURES.stream()
                .filter(f -> "objet-fret-no-fake-task".equals(f.name()))
                .filter(f -> {
                    GraphExtraction g = extractor.extractGraph(f.log());
                    return g.getTask() != null;
                })
                .count();
        assertEquals(0, suspicious,
                "ObjetFret ne doit pas produire de tâche fantôme");
    }

    @Test
    @EnabledIf("desktopLogsAvailable")
    void auditRealLogFilesOnDesktop() throws Exception {
        AuditStats stats = auditPaths(desktopLogPaths());
        System.out.println(stats.report());

        assertTrue(stats.linesParsed() > 100, "Pas assez de lignes lues sur le Desktop");
        assertTrue(stats.catalogCoveragePercent() >= MIN_CATALOG_COVERAGE,
                () -> "Catalogue: " + stats.catalogCoveragePercent() + "%");
        assertTrue(stats.graphFamilyCoveragePercent() >= MIN_GRAPH_FAMILY_COVERAGE,
                () -> "Familles graphe: " + stats.graphFamilyCoveragePercent() + "%");
        assertTrue(stats.processExtractedPercent() >= MIN_PROCESS_EXTRACTED,
                () -> "Process: " + stats.processExtractedPercent() + "%");
        assertTrue(stats.actionWhenExpectedPercent() >= MIN_ACTION_WHEN_EXPECTED,
                () -> "Action (si attendue): " + stats.actionWhenExpectedPercent() + "% sur " + stats.actionExpectedLines());
        assertTrue(stats.taskWhenExpectedPercent() >= MIN_TASK_WHEN_EXPECTED,
                () -> "Tâche (si attendue): " + stats.taskWhenExpectedPercent() + "% sur " + stats.taskExpectedLines());
        assertTrue(stats.objectWhenExpectedPercent() >= MIN_OBJECT_WHEN_EXPECTED,
                () -> "Objet (si attendu): " + stats.objectWhenExpectedPercent() + "% sur " + stats.objectExpectedLines());
        assertTrue(stats.filterWhenExpectedPercent() >= MIN_FILTER_EXTRACTED,
                () -> "Filtre (si attendu): " + stats.filterWhenExpectedPercent() + "% sur " + stats.filterExpectedLines());
        assertTrue(stats.filterOnProcessFilterPercent() >= MIN_FILTER_ON_PROCESS_FILTER,
                () -> "Filtre sur processFilter: " + stats.filterOnProcessFilterPercent() + "%");
        if (stats.webServiceLines() > 50) {
            assertTrue(stats.taskOnWebServicePercent() >= MIN_TASK_ON_WEBSERVICE,
                    () -> "Tâche sur processWebService: " + stats.taskOnWebServicePercent() + "% ("
                            + stats.webServiceLines() + " lignes)");
        }
        assertTrue(stats.suspiciousTaskRatePercent() <= MAX_SUSPICIOUS_TASK_RATE,
                () -> "Fausses tâches processFilter: " + stats.suspiciousTaskRatePercent() + "%");
        assertEquals(0, stats.filterOverlapsProcess(),
                () -> "Filtre confondu avec process: " + stats.filterOverlapsProcess() + " lignes");
    }

    static boolean desktopLogsAvailable() {
        return desktopLogPaths().stream().anyMatch(Files::isRegularFile);
    }

    private static List<Path> desktopLogPaths() {
        String userHome = System.getProperty("user.home");
        Path metier = Path.of(userHome, "Desktop", "Metier");
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(metier)) {
            return files;
        }
        try (Stream<Path> stream = Files.list(metier)) {
            stream.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".log"))
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("works-"))
                    .sorted()
                    .limit(3)
                    .forEach(files::add);
        } catch (Exception ignored) {
            // skip unreadable dir
        }
        return files;
    }

    private static boolean isWorksProcessColumn(String processName) {
        if (processName == null || processName.isBlank()) {
            return false;
        }
        String p = processName.trim();
        return p.startsWith("processFilter")
                || p.startsWith("processWebService")
                || p.startsWith("processRunRules")
                || p.startsWith("process.");
    }

    private AuditStats auditPaths(List<Path> logFiles) throws Exception {
        long total = 0;
        long withContent = 0;
        long catalogHit = 0;
        long processOk = 0;
        long filterOk = 0;
        long filterExpected = 0;
        long actionOk = 0;
        long actionExpected = 0;
        long taskOk = 0;
        long taskExpected = 0;
        long objectOk = 0;
        long objectExpected = 0;
        long graphFamilyOk = 0;
        long suspiciousTask = 0;
        long filterOverlapsProcess = 0;
        long processFilterLines = 0;
        long filterOkOnProcessFilter = 0;
        long webServiceLines = 0;
        long taskOkOnWebService = 0;
        Map<String, Long> familyHits = new LinkedHashMap<>();
        List<String> unknownSamples = new ArrayList<>();

        for (Path file : logFiles) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count++ < MAX_LINES_PER_FILE) {
                    if (line.isBlank()) {
                        continue;
                    }
                    total++;
                    ParsedLine parsed = ParsedLine.fromPipeLine(line);
                    if (parsed == null || !isWorksProcessColumn(parsed.processName())) {
                        continue;
                    }
                    final String sampleLine = line;
                    withContent++;
                    LogEntry log = parsed.toLogEntry();
                    GraphExtraction g = extractor.extractGraph(log);
                    String procCol = safe(log.getProcessName());
                    String msg = safe(log.getMessage());

                    if (g.getProcess() != null) {
                        processOk++;
                    }
                    if (g.getFilter() != null && g.getProcess() != null
                            && fieldsOverlap(g.getFilter(), g.getProcess())) {
                        filterOverlapsProcess++;
                    }
                    if (expectsFilter(procCol, msg)) {
                        filterExpected++;
                        if (g.getFilter() != null) {
                            filterOk++;
                        }
                    }
                    if (expectsAction(procCol, msg)) {
                        actionExpected++;
                        if (g.getAction() != null) {
                            actionOk++;
                        }
                    }
                    if (expectsTask(procCol, msg)) {
                        taskExpected++;
                        if (g.getTask() != null) {
                            taskOk++;
                        }
                    }
                    if (expectsObject(msg)) {
                        objectExpected++;
                        if (g.getObject() != null) {
                            objectOk++;
                        }
                    }
                    if (catalog.classifyForGraph(log).isPresent()) {
                        graphFamilyOk++;
                    }

                    var family = catalog.classify(log);
                    if (family.isPresent()) {
                        catalogHit++;
                        familyHits.merge(family.get().getId(), 1L, Long::sum);
                    } else if (unknownSamples.size() < 15) {
                        unknownSamples.add(truncate(sampleLine, 200));
                    }

                    if (procCol.startsWith("processFilter")) {
                        processFilterLines++;
                        if (g.getFilter() != null) {
                            filterOkOnProcessFilter++;
                        }
                    }
                    if (procCol.startsWith("processWebService")) {
                        webServiceLines++;
                        if (g.getTask() != null) {
                            taskOkOnWebService++;
                        }
                    }
                    if (procCol.startsWith("processFilter")) {
                        String middle = middleProcessSegment(procCol);
                        String msgLower = msg.toLowerCase(Locale.ROOT);
                        boolean falseTaskFromColumn = g.getTask() != null
                                && notBlank(middle)
                                && middle.equalsIgnoreCase(g.getTask())
                                && !msgLower.contains("taskname")
                                && !msgLower.contains("start filtre")
                                && !msgLower.contains("running rules|");
                        if (falseTaskFromColumn) {
                            suspiciousTask++;
                        }
                    }
                }
            }
        }

        return new AuditStats(
                logFiles,
                total,
                withContent,
                catalogHit,
                graphFamilyOk,
                processOk,
                filterOk,
                filterExpected,
                actionOk,
                actionExpected,
                taskOk,
                taskExpected,
                objectOk,
                objectExpected,
                processFilterLines,
                filterOkOnProcessFilter,
                webServiceLines,
                taskOkOnWebService,
                suspiciousTask,
                filterOverlapsProcess,
                familyHits,
                unknownSamples
        );
    }

    private static boolean fieldsOverlap(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String left = a.trim();
        String right = b.trim();
        if (left.equalsIgnoreCase(right)) {
            return true;
        }
        if (left.regionMatches(true, 0, "process.", 0, 8)) {
            return left.substring(8).trim().equalsIgnoreCase(right);
        }
        return false;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String middleProcessSegment(String processColumn) {
        String[] parts = processColumn.split("-");
        return parts.length > 2 ? parts[2].trim() : "";
    }

    private static boolean expectsAction(String procCol, String message) {
        if (procCol != null && (procCol.startsWith("processFilter")
                || procCol.startsWith("processWebService")
                || procCol.startsWith("processRunRules"))) {
            return true;
        }
        String m = message == null ? "" : message;
        String lower = m.toLowerCase(Locale.ROOT);
        if (lower.contains("actionname [") && !lower.contains("actionname [null]")) {
            return true;
        }
        if (lower.contains("running rules|") || lower.contains("doaction for") || lower.contains("fire rules")) {
            return true;
        }
        if (procCol != null && procCol.startsWith("process.")) {
            return hasProcessDotActionSignal(procCol, m);
        }
        return lower.contains("action_name");
    }

    private static boolean hasProcessDotActionSignal(String procCol, String message) {
        String upper = procCol.toUpperCase(Locale.ROOT);
        if (upper.matches(".*-(LOAD|SAVE|BEFORE_LOAD|AFTER_LOAD|VALIDER|ANNULER|START|CONTROLSELECTION|AFFECTATION|IMPRIMER)(?:-|$).*")) {
            return true;
        }
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return lower.contains("memory usage (mo)")
                || lower.contains("loadlistchilds")
                || lower.contains("searchcomposantbyroot")
                || lower.contains("preparesearchbyroot")
                || lower.contains("end dosearch")
                || lower.contains("global search")
                || lower.contains("fire rules")
                || lower.contains(" ==> ");
    }

    private static boolean expectsFilter(String procCol, String message) {
        if (procCol != null && procCol.startsWith("processFilter")) {
            return true;
        }
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (lower.contains("filter code [") || lower.contains("fetching filter [")) {
            return true;
        }
        if (lower.contains("start filtre") || lower.contains("end filtre")) {
            return true;
        }
        if (lower.contains("structuredata") || lower.contains("strcturedatasinterface")) {
            return true;
        }
        return procCol != null && procCol.startsWith("processWebService")
                && (lower.contains("filter code [") || lower.contains("rule_ws"));
    }

    private static boolean expectsTask(String procCol, String message) {
        if (procCol != null && procCol.startsWith("processWebService")) {
            return true;
        }
        if (procCol != null && procCol.startsWith("processRunRules")) {
            String middle = middleProcessSegment(procCol);
            return notBlank(middle);
        }
        String m = message == null ? "" : message;
        if (m.contains("Start Filtre")) {
            return true;
        }
        if (m.contains("running rules|")) {
            String[] parts = m.split("\\|");
            if (parts.length >= 3) {
                String seg = parts[2].trim().toUpperCase(Locale.ROOT);
                return seg.startsWith("WS_") || seg.contains("_TASK");
            }
        }
        if (m.contains("taskName [") && !m.contains("taskName [null]")) {
            if (procCol != null && procCol.startsWith("processFilter")) {
                String middle = middleProcessSegment(procCol);
                if (notBlank(middle) && m.contains("taskName [" + middle)) {
                    return false;
                }
            }
            return true;
        }
        return m.contains("taskName=") && !m.toLowerCase(Locale.ROOT).contains("taskname=null");
    }

    private static boolean expectsObject(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.toLowerCase(Locale.ROOT);
        return m.contains("classname") || m.contains("put key") || m.contains("field [");
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    private record Probe(String processColumn, String message) {
        LogEntry toLogEntry() {
            LogEntry log = new LogEntry();
            log.setProcessName(processColumn);
            log.setMessage(message);
            log.setLevel("DEBUG");
            return log;
        }
    }

    private record ExtractionFixture(
            String name,
            String processColumn,
            String message,
            String expectedProcess,
            String expectedTask,
            String expectedFilter,
            String expectedObject,
            String expectedAction
    ) {
        LogEntry log() {
            LogEntry log = new LogEntry();
            log.setProcessName(processColumn);
            log.setMessage(message);
            log.setLevel("DEBUG");
            log.setIsError(false);
            return log;
        }

        boolean matches(GraphExtraction g) {
            return eq(expectedProcess, g.getProcess())
                    && eq(expectedTask, g.getTask())
                    && eq(expectedFilter, g.getFilter())
                    && eq(expectedObject, g.getObject())
                    && eq(expectedAction, g.getAction());
        }

        private static boolean eq(String expected, String actual) {
            if (expected == null) {
                return actual == null;
            }
            return expected.equals(actual);
        }

        String describeMismatch(GraphExtraction g) {
            return "process=" + g.getProcess() + " task=" + g.getTask() + " filter=" + g.getFilter()
                    + " object=" + g.getObject() + " action=" + g.getAction()
                    + " (attendu process=" + expectedProcess + " task=" + expectedTask
                    + " filter=" + expectedFilter + " object=" + expectedObject + " action=" + expectedAction + ")";
        }
    }

    private static final Map<String, Probe> PROBES = Map.ofEntries(
            Map.entry("MET-001", new Probe("processFilter-0-X-0-LOAD", "filter code [TRCEXP_BYAMPE] uuid [-1]")),
            Map.entry("MET-002", new Probe(null, "fetching filter [ZRE22]")),
            Map.entry("MET-003", new Probe(null, "className [ServicePortuaire] filter code [X]")),
            Map.entry("MET-004", new Probe(null, "className ServicePortuaire uuid [1]")),
            Map.entry("MET-005", new Probe(null, "[0] row fetched")),
            Map.entry("MET-006", new Probe(null, "took [892] ms")),
            Map.entry("MET-007", new Probe(null, "searchAttributesList filter code [a|b] uuid [1] took [10] ms")),
            Map.entry("MET-008", new Probe(null, "processName [processWebService] - taskName [WS_X] - actionName [LOAD]")),
            Map.entry("MET-009", new Probe(null, "running rules|processWebService|WS_X|LOAD|100")),
            Map.entry("MET-010", new Probe(null, "memory usage (Mo) [x] 100")),
            Map.entry("MET-011", new Probe(null, "searchComposantByRoot filter code [X]")),
            Map.entry("MET-012", new Probe(null, "prepareSearchByRoot 1 row className Obj")),
            Map.entry("MET-013", new Probe(null, "START fire rules")),
            Map.entry("MET-014", new Probe(null, "END fire rules")),
            Map.entry("MET-015", new Probe(null, "execute filter [X]")),
            Map.entry("MET-016", new Probe(null, "[Query] took [1] ms")),
            Map.entry("MET-017", new Probe(null, "loadListChilds start loading data")),
            Map.entry("MET-018", new Probe(null, "PROCESS_NAME [processFilter] only")),
            Map.entry("MET-019", new Probe(null, "transition = SAVE taskName=DATA_LOAD_TASK")),
            Map.entry("PROC-DOT", new Probe("process.FILTRE_AMPE---0-LOAD", null)),
            Map.entry("PROC-AMPE-CTRL", new Probe("process.AMPE Contrôle Exploitation-0-SAVE", "running rules")),
            Map.entry("MET-031", new Probe(null, "searchComposantByRoot ==> using multiThreading MULTITHREADING_DEFAULT_ARGS")),
            Map.entry("MET-032", new Probe(null, "using tempQuery [generateSqlQuery] using [ union (default) ] uuid [1]")),
            Map.entry("MET-033", new Probe(null, "saveTempComposantsLoaded using [ union (default) ] uuid [1]")),
            Map.entry("MET-034", new Probe(null, "Warning : aucune règle n'a été affecté a cette transition ! PROCESS_NAME [p], ACTION_NAME [LOAD]")),
            Map.entry("MET-035", new Probe(null, "Aggregate data for 1 element took [0] ms")),
            Map.entry("MET-036", new Probe(null, "saveRelOperationsComposants took [0] ms")),
            Map.entry("MET-037", new Probe(null, "total time SAVE [1] for key [ServicePortuaire]")),
            Map.entry("MET-038", new Probe(null, "call runRules WS from 192.168.7.11")),
            Map.entry("MET-039", new Probe(null, "you are using a composer [x] as a view mode")),
            Map.entry("WS-005", new Probe(null, "------------------- WS_PROBE Start -------------------")),
            Map.entry("WS-006", new Probe(null, "End Filtre X SIZE : 10")),
            Map.entry("WS-007", new Probe(null, "*********** WS_PROBE_WORKFLOW ****** Start")),
            Map.entry("WS-008", new Probe(null, "WS_FindTracabilitePosition Start")),
            Map.entry("MET-021", new Probe(null, "no rule found for task")),
            Map.entry("MET-022", new Probe(null, "doAction for transition=SAVE took [1] ms")),
            Map.entry("MET-024", new Probe(null, "uuid [123]")),
            Map.entry("MET-025", new Probe(null, "transactionId (abc-123)")),
            Map.entry("MET-026", new Probe(null, "thread name [http-nio-8080-exec-1]")),
            Map.entry("MET-027", new Probe(null, "noDUM = [12345]")),
            Map.entry("MET-028", new Probe(null, "wvParam=[value]")),
            Map.entry("MET-029", new Probe(null, "ENTREE_FILTRE_AMPE trCheckPoint")),
            Map.entry("MET-030", new Probe(null, "Trigger batch was fired")),
            Map.entry("L1-PF", new Probe("processFilter-0-ZRE22-0-LOAD", "ligne technique sans motif message")),
            Map.entry("L1-PW", new Probe("processWebService-0-WS_SEARCH-0-LOAD", null)),
            Map.entry("L1-PRR", new Probe("processRunRules-0-DATA_LOAD_TASK-0-SAVE", null)),
            Map.entry("WS-003", new Probe(null, "Start Filtre WS_SEARCH_REG")),
            Map.entry("WS-004", new Probe(null, "Start WS_MYFLOW")),
            Map.entry("WS-002", new Probe(null, "Warning : aucune règle")),
            Map.entry("TOM-001", new Probe("processRunRules-0-DATA_LOAD_TASK-0-SAVE",
                    "StructureDataInterface.structureData")),
            Map.entry("TOM-002", new Probe(null, "START [StrctureDatasInterface.jar/structureData/StructureDataInterface.getDataByType]")),
            Map.entry("TOM-003", new Probe(null, "works = [field.x] interface = [y]")),
            Map.entry("TOM-004", new Probe(null, "put key [compteRemboursement.numeroCompte] value [1]")),
            Map.entry("TOM-005", new Probe(null, "field [myField]")),
            Map.entry("TOM-006", new Probe(null, "{fieldClassCode} = [CODE]")),
            Map.entry("TOM-007", new Probe(null, "relation [relName]")),
            Map.entry("TOM-008", new Probe(null, "new interface object")),
            Map.entry("TOM-009", new Probe(null, "the value of the interface")),
            Map.entry("TOM-010", new Probe(null, "saveProcessContent insertArray")),
            Map.entry("TOM-011", new Probe(null, "END [StrctureDatasInterface.jar/structureData/StructureDataInterface.putData]")),
            Map.entry("TOM-012", new Probe(null, "STRUCTURING NE FIELD works = [x]")),
            Map.entry("TOM-013", new Probe(null, "need to be trim")),
            Map.entry("TOM-014", new Probe(null, "TYPE_ATTR_STRING")),
            Map.entry("EXT-003", new Probe(null, "memory usage")),
            Map.entry("EXT-004", new Probe(null, "saveLoadLogFile"))
    );

    private static final List<ExtractionFixture> FIXTURES = List.of(
            new ExtractionFixture("objet-fret-no-fake-task",
                    "processFilter-0-ObjetFret-0-LOAD",
                    "filter code [Service AMPE Valide] className [ObjetFret] uuid [1]",
                    "processFilter", null, "Service AMPE Valide", "ObjetFret", "LOAD"),
            new ExtractionFixture("filter-code-column",
                    "processFilter-0-ZRE22-0-LOAD",
                    "filter code [TRCEXP_BYAMPE] uuid [-1]",
                    "processFilter", null, "TRCEXP_BYAMPE", null, "LOAD"),
            new ExtractionFixture("ws-column-task",
                    "processWebService-0-WS_SEARCH_REG_TRACABILITE-0-BEFORE_LOAD",
                    "uuid [1] Warning : aucune règle - processName [processWebService] - taskName [WS_SEARCH_REG_TRACABILITE] - actionName [BEFORE_LOAD]",
                    "processWebService", "WS_SEARCH_REG_TRACABILITE", "WS_SEARCH_REG_TRACABILITE", null, "BEFORE_LOAD"),
            new ExtractionFixture("start-filtre-ws-task",
                    "processFilter-0-WS_SEARCH_REG-0-LOAD",
                    "------------------- WS_SEARCH_REG_TRACABILITE Start Filtre WS_SEARCH_REG -------------------",
                    "processFilter", "WS_SEARCH_REG_TRACABILITE", "WS_SEARCH_REG", null, "LOAD"),
            new ExtractionFixture("run-rules-tomcat",
                    "processRunRules-0-DATA_LOAD_TASK-0-SAVE",
                    "put key [compteRemboursement.numeroCompte] value [42501486352]",
                    "processRunRules", "DATA_LOAD_TASK", null, "compteRemboursement.numeroCompte", "SAVE"),
            new ExtractionFixture("search-attributes",
                    "",
                    "searchAttributesList filter code [a|b|c] uuid [8791120312627512882] [Query] took [892] ms",
                    null, null, "a|b|c", null, null)
    );

    private record ParsedLine(String processName, String message, String level) {
        static ParsedLine fromPipeLine(String line) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 8) {
                return null;
            }
            String level = blank(parts[2]);
            String process = blank(parts[5]);
            int logCodeIdx = findLogCodeIndex(parts);
            String message = logCodeIdx >= 0 ? extractMessage(parts, logCodeIdx) : null;
            if (message == null || message.isBlank()) {
                return null;
            }
            return new ParsedLine(process, message, level != null ? level : "DEBUG");
        }

        LogEntry toLogEntry() {
            LogEntry log = new LogEntry();
            log.setProcessName(processName);
            log.setMessage(message);
            log.setLevel(level);
            log.setIsError("ERROR".equalsIgnoreCase(level));
            return log;
        }

        private static int findLogCodeIndex(String[] parts) {
            int candidate = parts.length - 8;
            if (candidate >= 6 && isInteger(parts[candidate])) {
                return candidate;
            }
            for (int i = 6; i < parts.length; i++) {
                if (!isInteger(parts[i])) {
                    continue;
                }
                String env = i + 3 < parts.length ? parts[i + 3] : null;
                if (isBlank(parts[i + 1]) && isBlank(parts[i + 2]) && env != null && env.trim().startsWith("DS:")) {
                    return i;
                }
            }
            return -1;
        }

        private static String extractMessage(String[] parts, int logCodeIndex) {
            int start = 6;
            int end = logCodeIndex > start ? logCodeIndex : parts.length;
            while (start < end && isBlank(parts[start])) {
                start++;
            }
            if (start >= end) {
                return null;
            }
            StringBuilder sb = new StringBuilder(parts[start].trim());
            for (int i = start + 1; i < end; i++) {
                if (!isBlank(parts[i])) {
                    if (!sb.isEmpty()) {
                        sb.append('|');
                    }
                    sb.append(parts[i].trim());
                }
            }
            return sb.toString().trim();
        }

        private static boolean isInteger(String s) {
            return s != null && s.trim().matches("\\d{3,6}");
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }

        private static String blank(String s) {
            if (s == null || s.isBlank()) {
                return null;
            }
            return s.trim();
        }
    }

    private record AuditStats(
            List<Path> files,
            long linesParsed,
            long withContent,
            long catalogHits,
            long graphFamilyHits,
            long processOk,
            long filterOk,
            long filterExpected,
            long actionOk,
            long actionExpected,
            long taskOk,
            long taskExpected,
            long objectOk,
            long objectExpected,
            long processFilterLines,
            long filterOkOnProcessFilter,
            long webServiceLines,
            long taskOkOnWebService,
            long suspiciousTasks,
            long filterOverlapsProcess,
            Map<String, Long> familyHits,
            List<String> unknownSamples
    ) {
        double catalogCoveragePercent() {
            return withContent == 0 ? 0 : round(100.0 * catalogHits / withContent);
        }

        double graphFamilyCoveragePercent() {
            return withContent == 0 ? 0 : round(100.0 * graphFamilyHits / withContent);
        }

        double processExtractedPercent() {
            return withContent == 0 ? 0 : round(100.0 * processOk / withContent);
        }

        double filterWhenExpectedPercent() {
            return filterExpected == 0 ? 100 : round(100.0 * filterOk / filterExpected);
        }

        long filterExpectedLines() { return filterExpected; }

        double actionWhenExpectedPercent() {
            return actionExpected == 0 ? 100 : round(100.0 * actionOk / actionExpected);
        }

        long actionExpectedLines() { return actionExpected; }

        double taskWhenExpectedPercent() {
            return taskExpected == 0 ? 100 : round(100.0 * taskOk / taskExpected);
        }

        long taskExpectedLines() { return taskExpected; }

        double objectWhenExpectedPercent() {
            return objectExpected == 0 ? 100 : round(100.0 * objectOk / objectExpected);
        }

        long objectExpectedLines() { return objectExpected; }

        double filterOnProcessFilterPercent() {
            return processFilterLines == 0 ? 0 : round(100.0 * filterOkOnProcessFilter / processFilterLines);
        }

        double taskOnWebServicePercent() {
            return webServiceLines == 0 ? 0 : round(100.0 * taskOkOnWebService / webServiceLines);
        }

        double suspiciousTaskRatePercent() {
            return processFilterLines == 0 ? 0 : round(100.0 * suspiciousTasks / processFilterLines);
        }

        String report() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Audit extraction (fichiers réels) ===\n");
            sb.append("Fichiers: ").append(files).append('\n');
            sb.append("Lignes: ").append(linesParsed).append(" (analysables: ").append(withContent).append(")\n");
            sb.append("Catalogue: ").append(catalogCoveragePercent()).append("%\n");
            sb.append("Familles graphe (utiles): ").append(graphFamilyCoveragePercent()).append("%\n");
            sb.append("Process: ").append(processExtractedPercent()).append("%\n");
            sb.append("Filtre (si attendu): ").append(filterWhenExpectedPercent())
                    .append("% (").append(filterExpected).append(" lignes)\n");
            sb.append("Action (si attendue): ").append(actionWhenExpectedPercent())
                    .append("% (").append(actionExpected).append(" lignes)\n");
            sb.append("Tâche (si attendue): ").append(taskWhenExpectedPercent())
                    .append("% (").append(taskExpected).append(" lignes)\n");
            sb.append("Objet (si attendu): ").append(objectWhenExpectedPercent())
                    .append("% (").append(objectExpected).append(" lignes)\n");
            sb.append("Filtre sur processFilter: ").append(filterOnProcessFilterPercent()).append("%\n");
            sb.append("Tâche sur processWebService: ").append(taskOnWebServicePercent()).append("%\n");
            sb.append("Fausses tâches processFilter: ").append(suspiciousTaskRatePercent()).append("%\n");
            sb.append("Filtre = process (chevauchement): ").append(filterOverlapsProcess).append('\n');
            sb.append("Familles touchées: ").append(familyHits.size()).append('\n');
            sb.append("Top familles: ").append(familyHits.entrySet().stream().limit(10).toList()).append('\n');
            if (!unknownSamples.isEmpty()) {
                sb.append("Exemples inconnus:\n");
                unknownSamples.forEach(s -> sb.append("  - ").append(s).append('\n'));
            }
            return sb.toString();
        }

        private static double round(double v) {
            return Math.round(v * 10.0) / 10.0;
        }
    }
}
