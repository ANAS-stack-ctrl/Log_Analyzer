package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;
import com.caciopee.loganalyzer.dto.LatencyQueryGroupDto;
import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientLatencyNarrativeBuilderTest {

    private final WorksLatencyFactExtractor factExtractor = new WorksLatencyFactExtractor();
    private final ClientLatencyNarrativeBuilder narrativeBuilder = new ClientLatencyNarrativeBuilder();
    private final LatencyOperationClassifier classifier = new LatencyOperationClassifier();

    /**
     * Régression : une fenêtre peut contenir plusieurs prepareSearchByRoot
     * (une lente, une accessoire rapide). Les faits et le récit doivent s'aligner
     * sur la requête racine LA PLUS LENTE, jamais sur la « 3 ms » accessoire.
     */
    @Test
    void primaryCause_picksSlowestRootQuery_notTrailingFastOne() {
        LatencyOriginReportDto report = new LatencyOriginReportDto();
        report.setMaxDurationMs(74532L);

        List<LatencyTimelineStepDto> steps = List.of(
                rootStep(1L, "prepareSearchByRoot [Query Select]", 73455L, 1),
                rootStep(2L, "prepareSearchByRoot [Query Count]", 3L, 0),
                step(3L, LatencyOperationClassifier.GLOBAL_CONTAINER, "global searchComposantByRoot", 74532L, 1),
                step(4L, LatencyOperationClassifier.CHILD_LOAD, "loadListChilds", 1076L, null)
        );

        WorksLatencyFacts facts = factExtractor.extract(report, List.of(), steps);
        assertEquals(73455L, facts.getRootQueryMs(), "Doit retenir la requête racine la plus lente");
        assertEquals(1, facts.getRootRowCount());

        LatencyBottleneckAnalyzer.BottleneckResult bottleneck =
                LatencyBottleneckAnalyzer.analyze(steps, classifier);
        String primaryCause = narrativeBuilder.buildPrimaryCause(
                facts, bottleneck.kind(), bottleneck.primary());

        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.SQL_DOMINATED, bottleneck.kind());
        assertTrue(primaryCause.toLowerCase().contains("requête sql"), primaryCause);
        assertTrue(primaryCause.contains("73"), primaryCause);
        assertFalse(primaryCause.contains("3 ms"), "Ne doit pas afficher la requête accessoire 3 ms : " + primaryCause);
        assertFalse(primaryCause.contains("0 ligne(s)"), "Ne doit pas afficher 0 ligne : " + primaryCause);
    }

    @Test
    void triggerSection_showsBusinessFieldsWithNaFallback() {
        LatencyOriginReportDto report = new LatencyOriginReportDto();
        report.setMaxDurationMs(74532L);
        report.setUserName("adil.benali");
        report.setProcessName("process.Manifeste");
        report.setFilterCode("SUPERVISION ED");
        report.setActionName("LOAD");
        report.setSessionId("1781842330613");
        report.setUuid("5139523748726851988");

        List<LatencyTimelineStepDto> steps = List.of(
                rootStep(1L, "prepareSearchByRoot [Query Select]", 73455L, 1),
                step(2L, LatencyOperationClassifier.GLOBAL_CONTAINER, "global searchComposantByRoot", 74532L, 1)
        );

        WorksLatencyFacts facts = factExtractor.extract(report, List.of(), steps);
        String narrative = narrativeBuilder.buildFullNarrative(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.SQL_DOMINATED, 74532L,
                steps.get(0));

        assertTrue(narrative.contains("Déclencheur métier"), narrative);
        assertTrue(narrative.contains("Processus : Manifeste"), narrative);
        assertTrue(narrative.contains("Filtre : SUPERVISION ED"), narrative);
        assertTrue(narrative.contains("Action : LOAD"), narrative);
        assertTrue(narrative.contains("Utilisateur : adil.benali"), narrative);
        assertTrue(narrative.contains("Session : 1781842330613"), narrative);
        assertTrue(narrative.contains("UUID : 5139523748726851988"), narrative);
        assertTrue(narrative.contains("Nom de l'écran : n/a"), "Écran absent → n/a : " + narrative);
    }

    /**
     * Cas process.Manifeste/Valider : le moteur de règles (159 s) domine de loin une
     * première requête racine de 17 s. Le goulot doit être les règles, pas la requête.
     */
    @Test
    void rulesEngine_dominatesOverShorterSqlQueries() {
        List<LatencyTimelineStepDto> steps = List.of(
                rootStep(1L, "prepareSearchByRoot [Query Select]", 17241L, 0),
                step(2L, LatencyOperationClassifier.GLOBAL_CONTAINER, "global searchComposantByRoot", 17242L, 0),
                rootStep(3L, "prepareSearchByRoot [Query Select]", 13509L, 0),
                step(4L, LatencyOperationClassifier.RULES_ENGINE, "running rules (in host)", 159026L, null),
                step(5L, "GENERIC", "Transit task", 159633L, null)
        );

        LatencyBottleneckAnalyzer.BottleneckResult bottleneck =
                LatencyBottleneckAnalyzer.analyze(steps, classifier);

        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED, bottleneck.kind(),
                "Les règles de 159 s doivent primer sur la requête de 17 s");
        assertEquals(159026L, bottleneck.primary().getDurationMs());
    }

    /**
     * Source du problème : un contrôle métier qui rejoue la même requête SQL doit être
     * agrégé par filtre. Une exécution isolée (count=1) ne devient jamais la cause racine.
     */
    @Test
    void repeatedQueries_aggregatedByFilter_revealRootCause() {
        List<LogEntry> logs = List.of(
                prepLog("prepareSearchByRoot [Query Select] took [17241] ms|0 row | className Manifeste | filter code [Rule_ctrlManifesteDuplicate_0.java] uuid [1]"),
                prepLog("prepareSearchByRoot [Query Select] took [6726] ms|0 row | className Manifeste | filter code [Rule_ctrlManifesteDuplicate_0.java] uuid [2]"),
                prepLog("prepareSearchByRoot [Query Select] took [13509] ms|0 row | className Manifeste | filter code [Rule_ctrlManifesteDuplicate_0.java] uuid [3]"),
                prepLog("prepareSearchByRoot [Query Select] took [73455] ms|1 row | className DChargementCont | filter code [SUPERVISION ED] uuid [9]")
        );

        WorksLatencyFacts facts = factExtractor.extract(new LatencyOriginReportDto(), logs, List.of());

        LatencyQueryGroupDto dominant = facts.dominantRepeatedGroup();
        assertNotNull(dominant, "Un groupe répété dominant doit être identifié");
        assertEquals("Rule_ctrlManifesteDuplicate_0.java", dominant.getFilterCode());
        assertEquals(3, dominant.getCount());
        assertEquals(17241L + 6726L + 13509L, dominant.getTotalMs());
        assertEquals(17241L, dominant.getMaxMs());
    }

    /**
     * Régression : la fenêtre peut contenir plusieurs lignes "running rules ... in host"
     * (opérations voisines). On doit retenir le MAX (159 026 ms) et jamais la dernière
     * valeur rencontrée (505 ms d'une autre opération).
     */
    @Test
    void rulesEngineMs_keepsMaximum_notLastOccurrence() {
        List<LogEntry> logs = List.of(
                prepLog("running rules|process.Manifeste|Valider|Valider|533152366|533152368|in host took [159026] ms"),
                prepLog("running rules|process.ED SUPERVISION|Valider|SAVE|34303427|533152735|in host took [145] ms"),
                prepLog("running rules|process.ED SUPERVISION|Valider|Valider|533152733|533152735|in host took [505] ms")
        );

        WorksLatencyFacts facts = factExtractor.extract(new LatencyOriginReportDto(), logs, List.of());

        assertEquals(159026L, facts.getRulesEngineMs(),
                "Doit conserver le maximum (159 026), pas la dernière valeur (505)");
    }

    /**
     * Cas or MATCX / « Rapprocher Tout » : le récit doit expliquer la vraie cause
     * (volume amont + matchall + MAJ_Insert massifs), jamais un process placeholder.
     */
    @Test
    void matcxRapprocherTout_explainsBulkRulesSideEffects() {
        LatencyOriginReportDto report = new LatencyOriginReportDto();
        report.setMaxDurationMs(79867L);
        report.setUserName("YACHOU.AYOUB");
        report.setProcessName("-0--0-SAVE");
        report.setSessionId("1781861747049");
        report.setUuid("3305662418206214485");

        List<LogEntry> logs = List.of(
                prepLog("global searchComposantByRoot [multithreading] took [7321] ms|248 row | className ServicePortuaire | filter code [AMPEZRE] uuid [3305662418206214485]"),
                prepLog("START fire rules uuid [3305662418206214485]"),
                prepLog("MAJ_Insert_DynaScreen_AMPE_ZRE for composant"),
                prepLog("MAJ_Insert_DynaScreen_AMPE_ZRE for composant"),
                prepLog("MAJ_Insert_DynaScreen_AMPE_ZRE for composant"),
                prepLog("insertArrayRel works_rel_composant_child for [515] element took [900] ms"),
                prepLog("insertArrayRel works_rel_composant_root for [777] element took [800] ms"),
                prepLog("total time SAVE for key [ServicePortuaireCont] ; 1800 (ms)"),
                prepLog("doAction for - actionName : Rapprocher Tout, - transition : matchall took 82104 ms"),
                prepLog("running rules|process.MATCX_ZREAMP|Match|matchall|533179467|533179469|in host took [79867] ms")
        );

        List<LatencyTimelineStepDto> steps = List.of(
                step(1L, LatencyOperationClassifier.RULES_ENGINE, "running rules (in host)", 79867L, null),
                step(2L, "GENERIC", "doAction Rapprocher Tout", 82104L, null)
        );

        WorksLatencyFacts facts = factExtractor.extract(report, logs, steps);
        assertEquals("process.MATCX_ZREAMP", facts.getProcessName());
        assertEquals("matchall", facts.getActionName());
        assertEquals("Rapprocher Tout", facts.getBusinessActionLabel());
        assertEquals("AMPEZRE", facts.getPreRulesFilterCode());
        assertEquals(248, facts.getPreRulesRowCount());
        assertTrue(facts.getMajInsertTotal() >= 3);
        assertEquals("DynaScreen_AMPE_ZRE", facts.getMajInsertDominantType());
        assertEquals(515, facts.getInsertRelChildCount());
        assertEquals(1800L, facts.getSavePersistMs());

        LatencyBottleneckAnalyzer.BottleneckResult bottleneck =
                LatencyBottleneckAnalyzer.analyze(steps, classifier);
        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED, bottleneck.kind());

        String narrative = narrativeBuilder.buildFullNarrative(
                facts, bottleneck.kind(), 79867L, bottleneck.primary());

        assertTrue(narrative.contains("MATCX_ZREAMP"), narrative);
        assertTrue(narrative.contains("Rapprocher Tout"), narrative);
        assertTrue(narrative.contains("AMPEZRE"), narrative);
        assertTrue(narrative.toLowerCase().contains("maj_insert"), narrative);
        assertFalse(narrative.contains("-0--0-SAVE"), narrative);
        assertTrue(narrative.contains("Chaîne de cause"), narrative);
        assertTrue(narrative.contains("Pourquoi ? (en profondeur)"), narrative);
        assertTrue(narrative.contains("Pourquoi 1 :"), narrative);
        assertTrue(narrative.contains("Pourquoi 2 :"), narrative);
        assertFalse(narrative.toLowerCase().contains("puis affichage de la liste"),
                "Schéma règles ne doit pas parler d'affichage liste : " + narrative);

        var why = narrativeBuilder.buildWhyChain(facts, bottleneck.kind(), bottleneck.primary(), 79867L);
        assertTrue(why.size() >= 2, "Au moins 2 niveaux de pourquoi : " + why);
        assertTrue(why.get(0).toLowerCase().contains("règle") || why.get(0).toLowerCase().contains("fire"),
                why.get(0));
        assertTrue(why.stream().anyMatch(w -> w.toLowerCase().contains("maj_insert")), why.toString());

        String primary = narrativeBuilder.buildPrimaryCause(facts, bottleneck.kind(), bottleneck.primary());
        assertTrue(primary.toLowerCase().contains("règles") || primary.toLowerCase().contains("maj_insert"),
                primary);
    }

    @Test
    void rulesWhyChain_rigorous_noMatchallHypothesis_withoutInternalProof() {
        WorksLatencyFacts facts = new WorksLatencyFacts();
        facts.setRulesEngineMs(64082L);
        facts.setSavePersistMs(1800L);
        facts.setInsertRelChildMs(300L);
        facts.setInsertRelChildCount(100);
        facts.setRootQueryMs(578L);
        facts.setActionName("matchall");
        facts.setBusinessActionLabel("Rapprocher Tout");
        facts.setMajInsertTotal(0);

        LatencyTimelineStepDto primary = step(1L, LatencyOperationClassifier.RULES_ENGINE,
                "running rules", 64082L, null);
        var why = narrativeBuilder.buildWhyChain(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED, primary, 64695L);

        assertTrue(why.size() >= 3, why.toString());
        assertTrue(why.get(0).toLowerCase().contains("fire rules")
                        || why.get(0).toLowerCase().contains("règles"),
                why.get(0));
        assertTrue(why.get(1).toLowerCase().contains("save")
                        || why.get(1).toLowerCase().contains("insert"),
                "Pourquoi 2 = élimination des autres étapes : " + why.get(1));
        assertFalse(why.stream().anyMatch(w -> w.toLowerCase().contains("tout le jeu sélectionné")),
                "Ne pas affirmer matchall/volume sans preuve : " + why);
        assertTrue(why.stream().anyMatch(w -> w.toLowerCase().contains("start fire rules")
                        || w.toLowerCase().contains("ne détaillent pas")
                        || w.toLowerCase().contains("ne montre")),
                "Doit admettre la limite de preuve interne : " + why);
    }

    @Test
    void rulesWhyChain_majInsert_massVolume_isStrongSignal_notSoleCause() {
        WorksLatencyFacts facts = new WorksLatencyFacts();
        facts.setRulesEngineMs(79867L);
        facts.setMajInsertTotal(248);
        facts.setMajInsertDominantType("DynaScreen_AMPE_ZRE");
        facts.setMajInsertDominantCount(200);
        facts.setBusinessActionLabel("Rapprocher Tout");
        facts.setSavePersistMs(1800L);

        var why = narrativeBuilder.buildWhyChain(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED, null, 82104L);
        assertTrue(why.stream().anyMatch(w -> w.contains("MAJ_Insert")), why.toString());
        assertTrue(why.stream().anyMatch(w -> w.toLowerCase().contains("ne peut pas affirmer")
                        || w.toLowerCase().contains("à elles seules")),
                why.toString());
        assertFalse(why.stream().anyMatch(w -> w.toLowerCase().contains("ne détaillent pas")),
                why.toString());
    }

    @Test
    void rulesWhyChain_fewMajInsert_doesNotClaimTheyCauseRulesDuration() {
        WorksLatencyFacts facts = new WorksLatencyFacts();
        facts.setRulesEngineMs(81092L);
        facts.setActionName("matchall");
        facts.setProcessName("process.MATCX_ZREAMP");
        facts.setMajInsertTotal(2);
        facts.setMajInsertDominantType("TraceAMPE_Demat_MATCX_ALL");
        facts.setMajInsertDominantCount(2);
        facts.setSavePersistMs(2541L);

        LatencyTimelineStepDto primary = step(1L, LatencyOperationClassifier.RULES_ENGINE,
                "running rules (in host)", 81092L, null);
        var why = narrativeBuilder.buildWhyChain(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED, primary, 81092L);

        assertTrue(why.stream().anyMatch(w -> w.contains("MAJ_Insert")), why.toString());
        assertTrue(why.stream().anyMatch(w -> w.toLowerCase().contains("ne peut pas affirmer")
                        || w.toLowerCase().contains("à elles seules")),
                why.toString());
        assertTrue(why.stream().anyMatch(w -> w.toLowerCase().contains("start fire rules")
                        || w.toLowerCase().contains("ne détaillent")),
                "Avec seulement 2 MAJ non chronométrées, garder la limite de preuve : " + why);

        String summary = narrativeBuilder.buildSummary(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED, primary, 81092L);
        assertFalse(summary.toLowerCase().contains("dont ~2"), summary);
        assertTrue(summary.toLowerCase().contains("observée"), summary);
    }

    @Test
    void familyResolver_mapsWsPrintAutoStart() {
        WorksLatencyFacts wsFacts = new WorksLatencyFacts();
        wsFacts.setProcessName("processWebService");
        wsFacts.setTaskName("WS_RFID_FIND_SERVICES_2");
        LatencyTimelineStepDto wsStep = step(1L, LatencyOperationClassifier.WEBSERVICE,
                "running rules WS", 12230L, null);
        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.WS_DOMINATED,
                LatencyFamilyResolver.refine(
                        LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED, wsFacts, wsStep, null));

        WorksLatencyFacts printFacts = new WorksLatencyFacts();
        printFacts.setProcessName("process.AST_PRINT");
        printFacts.setBusinessActionLabel("Imprimer");
        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.PRINT_DOMINATED,
                LatencyFamilyResolver.refine(
                        LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED, printFacts, null, null));

        WorksLatencyFacts autoFacts = new WorksLatencyFacts();
        autoFacts.setProcessName("processAutoStartRules");
        autoFacts.setTaskName("TRACE_SESSIONS");
        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.AUTOSTART_DOMINATED,
                LatencyFamilyResolver.refine(
                        LatencyBottleneckAnalyzer.NarrativeKind.COMPLEX, autoFacts, null, null));
    }

    @Test
    void droolsRuleNames_extractedAndCitedInNarrative() {
        List<LogEntry> logs = List.of(
                prepLog("prepareSearchByRoot [Query Select] took [17241] ms|0 row | filter code [Rule_ctrlManifesteDuplicate_0.java]"),
                prepLog("prepareSearchByRoot [Query Select] took [6726] ms|0 row | filter code [Rule_ctrlManifesteDuplicate_0.java]"),
                prepLog("prepareSearchByRoot [Query Select] took [13509] ms|0 row | filter code [Rule_ctrlManifesteDuplicate_0.java]"),
                prepLog("filter code [Rule_MAJ_FRET_NOTIFICATIONS_SMS_MainLevee_0.java] uuid [1]"),
                prepLog("running rules|process.Manifeste|Valider|Valider|1|2|in host took [159026] ms")
        );

        WorksLatencyFacts facts = factExtractor.extract(new LatencyOriginReportDto(), logs, List.of(
                step(1L, LatencyOperationClassifier.RULES_ENGINE, "running rules (in host)", 159026L, null)
        ));

        assertEquals("Rule_ctrlManifesteDuplicate_0.java", facts.dominantDroolsRule());
        assertTrue(facts.getDroolsRuleNames().stream()
                        .anyMatch(n -> n.contains("Rule_ctrlManifesteDuplicate")),
                facts.getDroolsRuleNames().toString());
        assertTrue(facts.getDroolsRuleNames().stream()
                        .anyMatch(n -> n.contains("Rule_MAJ_FRET")),
                facts.getDroolsRuleNames().toString());

        String narrative = narrativeBuilder.buildFullNarrative(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED, 159026L,
                step(1L, LatencyOperationClassifier.RULES_ENGINE, "running rules (in host)", 159026L, null));
        assertTrue(narrative.contains("Règles Drools"), narrative);
        assertTrue(narrative.contains("Rule_ctrlManifesteDuplicate"), narrative);

        String primary = narrativeBuilder.buildPrimaryCause(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED,
                step(1L, LatencyOperationClassifier.RULES_ENGINE, "running rules", 159026L, null));
        assertTrue(primary.contains("Rule_ctrlManifesteDuplicate"), primary);
    }

    @Test
    void imprimerProcess_withLoadOperationById_isOperationLoadNotPrint() {
        List<LogEntry> logs = List.of(
                prepLog("process.AMPE-Imprimer action Imprimer filter Comp TYPE_ATTR_COMP"),
                prepLog("loadOperationContext took [14955] ms filter code [Comp TYPE_ATTR_COMP]"),
                prepLog("loadOperationById took [14955] ms filter code [Comp TYPE_ATTR_COMP]"),
                prepLog("loadListChilds [total] : took [14951] ms filter code [Comp TYPE_ATTR_COMP]"),
                prepLog("searchAttributesList filter code [Comp TYPE_ATTR_COMP] [Query] took [14160] ms")
        );
        List<LatencyTimelineStepDto> steps = new WorksLatencyTimelineParser(
                new com.caciopee.loganalyzer.analysis.util.LogPatternExtractor(), classifier)
                .parseTimeline(logs);
        assertTrue(steps.stream().anyMatch(s ->
                        LatencyOperationClassifier.OPERATION_LOAD.equals(s.getStepType())),
                steps.toString());

        var bottleneck = LatencyBottleneckAnalyzer.analyze(steps, classifier);
        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.OPERATION_LOAD_DOMINATED, bottleneck.kind(),
                bottleneck.toString());

        WorksLatencyFacts facts = new WorksLatencyFacts();
        facts.setProcessName("process.AMPE-Imprimer");
        facts.setBusinessActionLabel("Imprimer");
        facts.setFilterCode("Comp TYPE_ATTR_COMP");
        facts.setUserName("YACHOU.AYOUB");
        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.OPERATION_LOAD_DOMINATED,
                LatencyFamilyResolver.refine(bottleneck.kind(), facts, bottleneck.primary(), null));

        var why = narrativeBuilder.buildWhyChain(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.OPERATION_LOAD_DOMINATED,
                bottleneck.primary(), 14955L);
        assertTrue(why.stream().anyMatch(w -> w.toLowerCase().contains("chargement")), why.toString());
        assertFalse(why.stream().anyMatch(w -> w.toLowerCase().contains("génération pdf")
                        && !w.toLowerCase().contains("aucune preuve")),
                why.toString());
        String primary = narrativeBuilder.buildPrimaryCause(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.OPERATION_LOAD_DOMINATED, bottleneck.primary());
        assertTrue(primary.toLowerCase().contains("chargement"), primary);
        assertFalse(primary.toLowerCase().contains("impression / pdf"), primary);
    }

    @Test
    void complexCase_multipleComparableContributors_notSqlDominated() {
        List<LatencyTimelineStepDto> steps = List.of(
                step(1L, LatencyOperationClassifier.ROOT_QUERY, "prepareSearchByRoot", 22000L, 340),
                step(2L, LatencyOperationClassifier.GLOBAL_CONTAINER, "global search", 25000L, 340),
                step(3L, LatencyOperationClassifier.RULES_ENGINE, "running rules", 21000L, null),
                step(4L, LatencyOperationClassifier.RENDERING, "rendering result", 18000L, null),
                step(5L, LatencyOperationClassifier.SAVE_PERSIST, "total time SAVE", 8000L, null)
        );
        var bottleneck = LatencyBottleneckAnalyzer.analyze(steps, classifier);
        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.COMPLEX, bottleneck.kind(),
                "Plusieurs contributeurs comparables → COMPLEX, pas SQL_DOMINATED");

        WorksLatencyFacts facts = new WorksLatencyFacts();
        facts.setRootQueryMs(22000L);
        facts.setGlobalMs(25000L);
        facts.setRulesEngineMs(21000L);
        facts.setRenderingMs(18000L);
        facts.setSavePersistMs(8000L);
        facts.setFilterCode("FILTER_X");
        var why = narrativeBuilder.buildWhyChain(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.COMPLEX, bottleneck.primary(), 25000L);
        assertTrue(why.stream().anyMatch(w -> w.toLowerCase().contains("plusieurs")), why.toString());
        assertTrue(why.stream().anyMatch(w -> w.contains("22") || w.toLowerCase().contains("sql")), why.toString());
        assertTrue(why.stream().anyMatch(w -> w.toLowerCase().contains("rendering")
                        || w.toLowerCase().contains("règles")),
                why.toString());
    }

    @Test
    void workflowWhyChain_transitOnly_noQueueLockHypothesis() {
        WorksLatencyFacts facts = new WorksLatencyFacts();
        facts.setActionName("matchall");
        facts.setTaskName("Match");
        facts.setProcessName("process.MATCX_ZREAMP");
        facts.setUserName("YACHOU.AYOUB");

        LatencyTimelineStepDto primary = step(1L, LatencyOperationClassifier.WORKFLOW,
                "Transit task", 84049L, null);
        var why = narrativeBuilder.buildWhyChain(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.WORKFLOW_DOMINATED, primary, 84049L);

        assertTrue(why.size() >= 2, why.toString());
        assertTrue(why.get(0).toLowerCase().contains("transit"), why.get(0));
        assertFalse(why.stream().anyMatch(w -> w.toLowerCase().contains("file d'attente")
                        && !w.toLowerCase().contains("ne sont pas démontr")),
                "Ne pas affirmer file/verrou comme cause : " + why);
        assertTrue(why.stream().anyMatch(w -> w.toLowerCase().contains("ne permettent pas")
                        || w.toLowerCase().contains("intérieur")),
                why.toString());

        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.WORKFLOW_DOMINATED,
                LatencyFamilyResolver.refine(
                        LatencyBottleneckAnalyzer.NarrativeKind.SINGLE_STEP, facts, primary, null));

        String summary = narrativeBuilder.buildSummary(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.WORKFLOW_DOMINATED, primary, 84049L);
        assertFalse(summary.toLowerCase().contains("file d'attente"), summary);
        assertTrue(summary.toLowerCase().contains("transit"), summary);
    }

    @Test
    void matchall_rulesInsideTransit_familyAndNarrativePreferRules() {
        WorksLatencyFacts facts = new WorksLatencyFacts();
        facts.setActionName("matchall");
        facts.setTaskName("Match");
        facts.setProcessName("process.MATCX_ZREAMP");
        facts.setUserName("YACHOU.AYOUB");
        facts.setBusinessActionLabel("Match");
        facts.setRulesEngineMs(64100L);
        facts.setSavePersistMs(1700L);
        facts.setRootQueryMs(578L);

        LatencyTimelineStepDto transit = step(1L, LatencyOperationClassifier.WORKFLOW,
                "Transit task", 82061L, null);
        LatencyTimelineStepDto rules = step(2L, LatencyOperationClassifier.RULES_ENGINE,
                "running rules (in host)", 64100L, null);

        assertEquals(LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED,
                LatencyFamilyResolver.refine(
                        LatencyBottleneckAnalyzer.NarrativeKind.SINGLE_STEP, facts, transit, null),
                "Transit + running rules ~78 % → RULES, pas WORKFLOW opaque");

        var why = narrativeBuilder.buildWhyChain(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED, rules, 82061L);
        assertTrue(why.stream().anyMatch(w -> w.toLowerCase().contains("règles")), why.toString());
        assertTrue(why.stream().anyMatch(w -> w.toLowerCase().contains("save")
                        || w.toLowerCase().contains("sql")),
                why.toString());
        assertFalse(why.stream().anyMatch(w -> w.toLowerCase().contains("ne permettent pas")),
                why.toString());

        String primaryCause = narrativeBuilder.buildPrimaryCause(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED, rules);
        assertTrue(primaryCause.toLowerCase().contains("règle")
                        || primaryCause.toLowerCase().contains("fire"),
                primaryCause);
    }

    @Test
    void parallelLoadWhyChain_dualBottleneck_noLoadListChildsHypothesis() {
        List<LogEntry> logs = List.of(
                prepLog("prepareSearchByRoot [Query Select] took [7668] ms|75214 row"),
                prepLog("searchComposantByRoot ==> using multiThreading for [75214] ids, partitionSize [100], nbrThreadPool [10]"),
                prepLog("global searchComposantByRoot (Multithreading) took [90629] ms|75214 row"),
                prepLog("running rules ... took [3] ms"),
                prepLog("Aggregate data ... took [0] ms"),
                prepLog("rendering result ... took [35368] ms")
        );
        LatencyTimelineStepDto primary = step(1L, LatencyOperationClassifier.GLOBAL_CONTAINER,
                "global searchComposantByRoot (Multithreading)", 90629L, 75214);
        WorksLatencyFacts facts = factExtractor.extract(new LatencyOriginReportDto(), logs, List.of(primary));

        assertEquals(90629L, facts.getGlobalMs());
        assertEquals(75214, facts.getGlobalRowCount() != null ? facts.getGlobalRowCount() : facts.getElementCount());
        assertEquals(100, facts.getPartitionSize());
        assertEquals(10, facts.getThreadPoolSize());
        assertEquals(35368L, facts.getRenderingMs());
        assertTrue(facts.hasSignificantSecondaryRender());
        assertTrue(facts.observedPipelineMs() >= 90629L + 35368L);

        var why = narrativeBuilder.buildWhyChain(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.PARALLEL_LOAD_DOMINATED, primary, 90629L);
        assertTrue(why.size() >= 2, why.toString());
        assertFalse(why.stream().anyMatch(w -> w.toLowerCase().contains("chaque lot relance")),
                why.toString());
        assertFalse(why.stream().anyMatch(w -> w.toLowerCase().contains("loadlistchilds")
                        && !w.toLowerCase().contains("non journalisé")
                        && !w.toLowerCase().contains("ne détaillent")),
                "Sans traces loadListChilds, ne pas l'affirmer comme mécanisme : " + why);
        assertTrue(why.stream().anyMatch(w -> w.toLowerCase().contains("rendering")
                        || w.contains("35")),
                why.toString());

        String primaryCause = narrativeBuilder.buildPrimaryCause(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.PARALLEL_LOAD_DOMINATED, primary);
        assertTrue(primaryCause.toLowerCase().contains("deux goulots"), primaryCause);
    }

    @Test
    void renderingWhyChain_rigorous_noColumnsHypothesis() {
        List<LogEntry> logs = List.of(
                prepLog("running rules ... took [4] ms"),
                prepLog("saveProcessContent ... took [4] ms"),
                prepLog("Start aggregate data for filter Services Actifs AMP et autres - 50485 element"),
                prepLog("Aggregate data ... took [0] ms"),
                prepLog("End aggregate data ... took [0] ms"),
                prepLog("rendering result ... took [34017] ms")
        );
        LatencyTimelineStepDto primary = step(1L, LatencyOperationClassifier.RENDERING,
                "rendering result", 34017L, null);
        WorksLatencyFacts facts = factExtractor.extract(new LatencyOriginReportDto(), logs, List.of(primary));

        assertEquals(34017L, facts.getRenderingMs());
        assertEquals(50485, facts.getElementCount());
        assertEquals(4L, facts.getRulesEngineMs());
        assertEquals(4L, facts.getSavePersistMs());
        assertEquals(0L, facts.getAggregateMs());

        var why = narrativeBuilder.buildWhyChain(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.RENDERING_DOMINATED, primary, 34017L);

        assertTrue(why.size() >= 2, why.toString());
        assertTrue(why.get(0).toLowerCase().contains("rendering"), why.get(0));
        assertTrue(why.get(0).contains("4") || why.get(0).toLowerCase().contains("running rules"),
                why.get(0));
        assertFalse(why.stream().anyMatch(w -> w.toLowerCase().contains("réduire le nombre de colonnes")
                        || w.toLowerCase().contains("colonnes / volume")),
                "Ne pas affirmer colonnes comme cause : " + why);
        assertTrue(why.get(0).contains("0 ms") || why.get(0).toLowerCase().contains("aggregate"),
                why.get(0));
        assertTrue(why.stream().anyMatch(w -> w.contains("50485") || w.toLowerCase().contains("volume")),
                why.toString());
        assertTrue(why.stream().anyMatch(w -> w.toLowerCase().contains("génération")
                        || w.toLowerCase().contains("affichage")),
                why.toString());
    }

    @Test
    void evidenceLinks_attachSearchTermsForClickNavigation() {
        LatencyOriginReportDto report = new LatencyOriginReportDto();
        report.setMaxDurationMs(79867L);
        report.setUserName("YACHOU.AYOUB");
        report.setProcessName("process.MATCX_ZREAMP");

        List<LogEntry> logs = List.of(
                prepLog("global searchComposantByRoot [multithreading] took [7321] ms|248 row | filter code [AMPEZRE]"),
                prepLog("MAJ_Insert_DynaScreen_AMPE_ZRE for composant"),
                prepLog("running rules|process.MATCX_ZREAMP|Match|matchall|1|2|in host took [79867] ms")
        );
        org.springframework.test.util.ReflectionTestUtils.setField(logs.get(0), "id", 10L);
        org.springframework.test.util.ReflectionTestUtils.setField(logs.get(2), "id", 12L);

        List<LatencyTimelineStepDto> steps = List.of(
                step(12L, LatencyOperationClassifier.RULES_ENGINE, "running rules (in host)", 79867L, null)
        );
        WorksLatencyFacts facts = factExtractor.extract(report, logs, steps);
        var links = LatencyEvidenceBuilder.build(
                facts, LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED,
                steps.get(0), report, logs, steps);

        assertFalse(links.isEmpty());
        assertTrue(links.stream().anyMatch(l -> "79867".equals(String.valueOf(l.getValueMs()))
                || (l.getSearchTerm() != null && l.getSearchTerm().contains("79867"))),
                links.toString());
        assertTrue(links.stream().anyMatch(l -> l.getSearchTerm() != null && !l.getSearchTerm().isBlank()),
                "Chaque preuve doit avoir un terme cliquable");
    }

    private LogEntry prepLog(String message) {
        LogEntry log = new LogEntry();
        log.setMessage(message);
        log.setLogTimestamp(java.time.LocalDateTime.now());
        return log;
    }

    private LatencyTimelineStepDto rootStep(long id, String name, long durationMs, Integer rows) {
        return step(id, LatencyOperationClassifier.ROOT_QUERY, name, durationMs, rows);
    }

    private LatencyTimelineStepDto step(long id, String type, String name, long durationMs, Integer rows) {
        LatencyTimelineStepDto step = new LatencyTimelineStepDto();
        step.setLogId(id);
        step.setStepType(type);
        step.setOperationName(name);
        step.setDurationMs(durationMs);
        step.setRowCount(rows);
        return step;
    }
}
