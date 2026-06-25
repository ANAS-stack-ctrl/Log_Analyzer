package com.caciopee.loganalyzer.analysis.util;

import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.entity.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogPatternExtractorTest {

    private LogPatternExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new LogPatternExtractor();
    }

    @Test
    void extractGraph_processFilterWithFilterCodeAndClassName() {
        LogEntry log = entry(
                "processFilter-0-ZRE22-0-LOAD",
                "searchComposantByRoot ==> prepareSearchByRoot className [ServicePortuaire] , filter code [TRCEXP_BYAMPE] uuid  [-5387821228861010765]"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("processFilter", ex.getProcess());
        assertNull(ex.getTask());
        assertEquals("LOAD", ex.getAction());
        assertEquals("TRCEXP_BYAMPE", ex.getFilter());
        assertEquals("ServicePortuaire", ex.getObject());
        assertEquals("-5387821228861010765", ex.getUuid());
    }

    @Test
    void extractGraph_fetchingFilterAndZeroRow() {
        LogEntry log = entry(
                "processFilter-0-ZRE22-0-LOAD",
                " fetching filter [TRCEXP_BYAMPE] bean: HashMap[threshold=24,loadFactor=0.75]"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("TRCEXP_BYAMPE", ex.getFilter());
        assertFalse(ex.isZeroResult());
    }

    @Test
    void extractGraph_zeroRowFetched() {
        LogEntry log = entry(
                "processFilter-0-ZRE22-0-LOAD",
                "searchComposantByRoot end execute filter code [TRCEXP_BYAMPE] uuid  [-5387821228861010765] , [0] row fetched"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertTrue(ex.isZeroResult());
        assertEquals("TRCEXP_BYAMPE", ex.getFilter());
    }

    @Test
    void extractGraph_classNameWithoutBrackets() {
        LogEntry log = entry(
                "processFilter-0-ZRE22-0-LOAD",
                "prepareSearchByRoot [Query Select] took [1] ms|0 row | className ServicePortuaireServicePortuaire | filter code [TRCEXP_BYAMPE]"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("ServicePortuaire", ex.getObject());
        assertTrue(ex.isZeroResult());
        assertEquals(1L, ex.getDurationMs());
    }

    @Test
    void extractGraph_processDotColumnAndNoRuleWarning() {
        LogEntry log = entry(
                "process.SUPERVISION ORDRE TRACTION---",
                "uuid [4723395462643928763] no rule found for this params : {taskName=Valider, transition=BEFORE_LOAD}"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("SUPERVISION ORDRE TRACTION", ex.getProcess());
        assertEquals("BEFORE_LOAD", ex.getAction());
        assertTrue(ex.isWarning());
    }

    @Test
    void extractGraph_actionNameColonAndTransition() {
        LogEntry log = entry(
                "process.SUPERVISION ORDRE TRACTION-516580298-StartProcess-0-Start",
                " [813999418177701037] doAction for - actionName : Start Supervision Ordre de traction [Selectionner + Continuer], - transition : start.process.process.SUPERVISION ORDRE TRACTION took 123 ms"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("SUPERVISION ORDRE TRACTION", ex.getProcess());
        assertEquals("Start", ex.getAction());
        assertEquals(123L, ex.getDurationMs());
    }

    @Test
    void extractGraph_pipeSegmentsInMessage() {
        LogEntry log = entry(
                "processFilter-0-ZRE22-0-LOAD",
                "running rules|processFilter|ZRE22|LOAD|null|0|in host took [22] ms"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("processFilter", ex.getProcess());
        assertEquals("ZRE22", ex.getFilter());
        assertNull(ex.getTask(), "ZRE22 est le filtre métier, pas une tâche");
        assertEquals("LOAD", ex.getAction());
        assertEquals(22L, ex.getDurationMs());
    }

    @Test
    void extractGraph_processNameBracketFormat() {
        LogEntry log = entry(
                "processFilter-0-TRCEXP_BYAMPE-0-LOAD",
                "uuid [-8633625065684252869] START fire rules ... ! - processName [processFilter] - taskName [TRCEXP_BYAMPE] - actionName [LOAD]"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("processFilter", ex.getProcess());
        assertEquals("LOAD", ex.getAction());
        assertEquals("TRCEXP_BYAMPE", ex.getFilter());
    }

    @Test
    void extractGraph_searchAttributesListWithTransaction() {
        LogEntry log = entry(
                "",
                "searchAttributesList filter code [searchComposant_1698628377636783380|crudLayout813001|listBoxLayout96492] uuid  [8791120312627512882] [Query] took [892] ms"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("searchComposant_1698628377636783380|crudLayout813001|listBoxLayout96492", ex.getFilter());
        assertEquals("8791120312627512882", ex.getUuid());
        assertEquals(892L, ex.getDurationMs());
    }

    @Test
    void extractGraph_processWebServiceColumn() {
        LogEntry log = entry(
                "processWebService-0-WS_SEARCH_REG_TRACABILITE-0-BEFORE_LOAD",
                "uuid [8351810702067977350] Warning : aucune règle - processName [processWebService] - taskName [WS_SEARCH_REG_TRACABILITE] - actionName [BEFORE_LOAD]"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("processWebService", ex.getProcess());
        assertEquals("WS_SEARCH_REG_TRACABILITE", ex.getTask());
        assertEquals("BEFORE_LOAD", ex.getAction());
        assertTrue(ex.isWarning());
    }

    @Test
    void extractGraph_processFileStartEndFiltre() {
        LogEntry log = entry(
                "processFilter-0-WS_SEARCH_REG-0-LOAD",
                "------------------- WS_SEARCH_REG_TRACABILITE Start Filtre WS_SEARCH_REG -------------------"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("processFilter", ex.getProcess());
        assertEquals("WS_SEARCH_REG", ex.getFilter());
        assertEquals("WS_SEARCH_REG_TRACABILITE", ex.getTask());
        assertEquals("LOAD", ex.getAction());
    }

    @Test
    void extractGraph_tookWithParenthesesMs() {
        LogEntry log = entry(
                "processFilter-0-Find_Tracabilite_WS_REG-0-LOAD",
                "------------------- End WS_SEARCH_REG_TRACABILITE ****** took 2948(ms) -------------------"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals(2948L, ex.getDurationMs());
        assertEquals("Find_Tracabilite_WS_REG", ex.getFilter());
    }

    @Test
    void extractGraph_processRunRulesTomcatWorksField() {
        LogEntry log = entry(
                "processRunRules-0-DATA_LOAD_TASK-0-SAVE",
                "3-- [StrctureDatasInterface.jar/structureData/StructureDataInterface.structureData] "
                        + "/********************** STRUCTURING NE FIELD works = [compteRemboursement.numeroCompte] interface = [numcompte]/"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("processRunRules", ex.getProcess());
        assertEquals("DATA_LOAD_TASK", ex.getTask());
        assertEquals("SAVE", ex.getAction());
        assertEquals("structureData.structureData", ex.getFilter());
        assertEquals("compteRemboursement.numeroCompte", ex.getObject());
    }

    @Test
    void extractGraph_processRunRulesTomcatPutKeyAndRelation() {
        LogEntry log = entry(
                "processRunRules-0-DATA_LOAD_TASK-0-SAVE",
                "10-- [StrctureDatasInterface.jar/structureData/StructureDataInterface.structureData] "
                        + "put key [compteRemboursement.numeroCompte] value [42501486352] in finalStructuredObject"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("processRunRules", ex.getProcess());
        assertEquals("DATA_LOAD_TASK", ex.getTask());
        assertEquals("compteRemboursement.numeroCompte", ex.getObject());
    }

    @Test
    void extractGraph_processRunRulesTomcatStructureMethod() {
        LogEntry log = entry(
                "processRunRules-0-DATA_LOAD_TASK-0-SAVE",
                "START [StrctureDatasInterface.jar/structureData/StructureDataInterface.getDataByType]"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("processRunRules", ex.getProcess());
        assertEquals("DATA_LOAD_TASK", ex.getTask());
        assertEquals("SAVE", ex.getAction());
        assertEquals("structureData.getDataByType", ex.getFilter());
    }

    @Test
    void extractGraph_processFilterColumnFallbackUuidOnly() {
        LogEntry log = entry(
                "processFilter-0-ObjetFret-0-LOAD",
                "uuid [999] took [1] ms"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("processFilter", ex.getProcess());
        assertEquals("ObjetFret", ex.getFilter());
        assertEquals("LOAD", ex.getAction());
        assertNull(ex.getTask());
    }

    @Test
    void extractGraph_processDotColumnFallback() {
        LogEntry log = entry(
                "process.AMPE Contrôle Exploitation---0-SAVE",
                "running rules|process.AMPE Contrôle Exploitation|ControlExploitation|2-controlSelection|100"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertTrue(ex.getProcess().startsWith("AMPE Contrôle Exploitation"));
        assertEquals("SAVE", ex.getAction());
        assertNull(ex.getFilter(), "running rules sans filter code ne doit pas inventer un filtre");
        assertNull(ex.getTask(), "ControlExploitation est une étape, pas une tâche WS/_TASK");
    }

    @Test
    void extractGraph_processWebServiceColumnFallback() {
        LogEntry log = entry(
                "processWebService-0-WS_SEARCH_REG_TRACABILITE-0-BEFORE_LOAD",
                "memory usage (Mo) [x] 100"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("processWebService", ex.getProcess());
        assertEquals("WS_SEARCH_REG_TRACABILITE", ex.getTask());
        assertEquals("BEFORE_LOAD", ex.getAction());
    }

    @Test
    void extractGraph_processFilterObjetFretDoesNotFakeTask() {
        LogEntry log = entry(
                "processFilter-0-ObjetFret-0-LOAD",
                "filter code [Service AMPE Valide] className [ObjetFret] uuid [123456789]"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("processFilter", ex.getProcess());
        assertEquals("LOAD", ex.getAction());
        assertEquals("Service AMPE Valide", ex.getFilter());
        assertEquals("ObjetFret", ex.getObject());
        assertNull(ex.getTask());
    }

    @Test
    void extractGraph_filterCodeWithJavaExtension() {
        LogEntry log = entry(
                "processWebService-0-WS_FIND_TRACABILITE_ALGESIRAS-0-LOAD",
                "memory usage (Mo) [prepareSearchByRoot 1 row - className ServicePortuaire - filter code [Rule_WS_FindTracabiliteAlgesiras_0.java] uuid  [-7284212025511623191]] 2477"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("processWebService", ex.getProcess());
        assertEquals("WS_FIND_TRACABILITE_ALGESIRAS", ex.getTask());
        assertEquals("LOAD", ex.getAction());
        assertEquals("Rule_WS_FindTracabiliteAlgesiras_0.java", ex.getFilter());
        assertEquals("ServicePortuaire", ex.getObject());
        assertEquals(2477, ex.getMemoryMo());
    }

    @Test
    void extractGraph_processDotTraceWithoutFilterCode_hasNoFilterNamedLikeProcess() {
        LogEntry log = entry(
                "process.AMPE Contrôle Exploitation-516575287-ControlExploitation-516576975",
                "memory usage (Mo) [end loadListChilds - thread name [call_1]  ] 26191"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertTrue(ex.getProcess().startsWith("AMPE Contrôle Exploitation"));
        assertNull(ex.getFilter(), "sans filter code, pas de filtre artificiel");
        assertNotEquals(ex.getProcess(), ex.getFilter());
    }

    @Test
    void extractGraph_processDotWithRealFilterCode_keepsFilterDistinctFromProcess() {
        LogEntry log = entry(
                "process.AMPE Contrôle Exploitation-516575287-ControlExploitation-516576975",
                " loadListChilds [total] : took [61] ms filter code [COMMANDE TRACTION IMPORT] uuid [1]"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("COMMANDE TRACTION IMPORT", ex.getFilter());
        assertNotEquals(ex.getProcess(), ex.getFilter());
        assertNull(ex.getTask(), "ControlExploitation n'est pas une tâche");
    }

    @Test
    void extractGraph_processFilterMiddleIsFilterNotTaskOrProcess() {
        LogEntry log = entry(
                "processFilter-0-ObjetFret-0-LOAD",
                "uuid [999] took [1] ms"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertEquals("processFilter", ex.getProcess());
        assertEquals("ObjetFret", ex.getFilter());
        assertEquals("LOAD", ex.getAction());
        assertNull(ex.getTask());
        assertNotEquals("ObjetFret", ex.getProcess());
    }

    @Test
    void extractGraph_processDotTraceMessagesInferAction() {
        LogEntry log = entry(
                "process.AMPE Contrôle Exploitation-516575287-ControlExploitation-516576975",
                " loadListChilds [total] : took [61] ms for thread name call_1 filter code [COMMANDE TRACTION IMPORT] uuid [1]"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertTrue(ex.getProcess().startsWith("AMPE Contrôle Exploitation"));
        assertEquals("loadListChilds", ex.getAction());
        assertEquals("COMMANDE TRACTION IMPORT", ex.getFilter());
    }

    @Test
    void extractGraph_processDotMemoryEndVerb() {
        LogEntry log = entry(
                "process.AMPE Contrôle Exploitation-516575287-ControlExploitation-516576975",
                "memory usage (Mo) [end loadListChilds - thread name [call_1]  ] 26191"
        );

        GraphExtraction ex = extractor.extractGraph(log);

        assertNotNull(ex.getAction());
        assertTrue("loadListChilds".equals(ex.getAction()) || "ControlExploitation".equals(ex.getAction()),
                () -> "action=" + ex.getAction());
    }

    private LogEntry entry(String processName, String message) {
        LogEntry log = new LogEntry();
        log.setProcessName(processName);
        log.setMessage(message);
        log.setLevel("DEBUG");
        log.setIsError(false);
        return log;
    }
}
