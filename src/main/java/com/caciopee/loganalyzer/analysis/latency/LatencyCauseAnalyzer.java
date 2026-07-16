package com.caciopee.loganalyzer.analysis.latency;



import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;

import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;

import com.caciopee.loganalyzer.entity.LogEntry;

import org.springframework.stereotype.Component;



import java.util.ArrayList;

import java.util.Comparator;

import java.util.LinkedHashSet;

import java.util.List;

import java.util.Locale;

import java.util.Objects;

import java.util.Set;

import java.util.regex.Matcher;

import java.util.regex.Pattern;

import java.util.stream.Collectors;



@Component

public class LatencyCauseAnalyzer {



    private static final Pattern JOIN_PATTERN = Pattern.compile(

            "\\b(inner\\s+join|join)\\b", Pattern.CASE_INSENSITIVE);



    private final LatencyOperationClassifier classifier;

    private final WorksLatencyFactExtractor factExtractor;

    private final ClientLatencyNarrativeBuilder clientNarrativeBuilder;

    public LatencyCauseAnalyzer(LatencyOperationClassifier classifier,
                                WorksLatencyFactExtractor factExtractor,
                                ClientLatencyNarrativeBuilder clientNarrativeBuilder) {
        this.classifier = classifier;
        this.factExtractor = factExtractor;
        this.clientNarrativeBuilder = clientNarrativeBuilder;
    }



    public void enrichReport(LatencyOriginReportDto report,

                             List<LatencyTimelineStepDto> steps,

                             List<LogEntry> windowLogs) {

        if (steps == null) {
            steps = List.of();
        }

        if (steps.isEmpty()) {
            List<LatencyTimelineStepDto> recovered = synthesizeStepsFromWindow(report, windowLogs);
            if (!recovered.isEmpty()) {
                report.setTimelineSteps(recovered);
                steps = recovered;
            } else {
                // Marqueurs métier sans took : chronologie déduite, jamais « aucune étape mesurable ».
                applyChronologyWithoutTimedSteps(report, windowLogs);
                return;
            }
        }



        LatencyBottleneckAnalyzer.BottleneckResult bottleneck =

                LatencyBottleneckAnalyzer.analyze(steps, classifier);

        // Aucun goulot fiable : toutes les étapes mesurées sont des anomalies de mesure
        // (took impossibles). On l'explique clairement plutôt que d'inventer une cause.
        if (bottleneck == null) {
            applyMeasurementAnomaly(report);
            return;
        }

        LatencyTimelineStepDto primary = bottleneck.primary();

        LatencyBottleneckAnalyzer.NarrativeKind kind = bottleneck.kind();



        markBottleneck(steps, primary);

        WorksLatencyFacts facts = factExtractor.extract(report, windowLogs, steps);

        // Affine le verdict technique vers la famille métier réelle (WS / Print / AutoStart…).
        kind = LatencyFamilyResolver.refine(kind, facts, primary, report);
        facts.setLatencyFamily(kind.name());

        // Remonter les IDs métier enrichis (process réel, filtre amont, action doAction)
        // pour que l'UI n'affiche plus les placeholders (-0--0-SAVE).
        syncIdentityFromFacts(report, facts);

        report.setClassName(cleanClassName(facts.getClassName()));
        report.setLatencyFamily(kind.name());

        long maxMs = report.getMaxDurationMs() != null ? report.getMaxDurationMs() : 0L;
        report.setPrimaryCause(clientNarrativeBuilder.buildPrimaryCause(facts, kind, primary));
        report.setClientSummary(clientNarrativeBuilder.buildSummary(facts, kind, primary, maxMs));
        report.setNarrativeSummary(clientNarrativeBuilder.buildFullNarrative(facts, kind, maxMs, primary));
        report.setWhyChain(clientNarrativeBuilder.buildWhyChain(facts, kind, primary, maxMs));
        report.setChainExplanation(buildChain(report, steps));
        long headlineMs = report.getMaxDurationMs() != null ? report.getMaxDurationMs() : 0L;
        Confidence conf = computeConfidence(steps, kind, primary, headlineMs, facts);
        report.setAnalysisConfidence(conf.level());
        report.setAnalysisConfidenceReason(conf.reason());

        applyRootCauseDrillDown(report, facts);
        report.setBottleneckSearchTerm(buildBottleneckSearchTerm(primary));
        report.setRecommendations(buildRecommendations(facts, kind));
        report.setEvidenceLinks(LatencyEvidenceBuilder.build(
                facts, kind, primary, report, windowLogs, steps));

    }

    private void syncIdentityFromFacts(LatencyOriginReportDto report, WorksLatencyFacts facts) {
        if (facts == null || report == null) {
            return;
        }
        if (notBlank(facts.getProcessName()) && !isPlaceholderProcess(facts.getProcessName())) {
            report.setProcessName(facts.getProcessName());
        }
        if (notBlank(facts.getBusinessActionLabel())) {
            report.setActionName(facts.getBusinessActionLabel().replaceAll("(?i)#icon:[^#]*#", "").trim());
        } else if (notBlank(facts.getActionName())) {
            report.setActionName(facts.getActionName().replaceAll("(?i)#icon:[^#]*#", "").trim());
        }
        if (!notBlank(report.getFilterCode()) && notBlank(facts.getPreRulesFilterCode())) {
            report.setFilterCode(facts.getPreRulesFilterCode());
        } else if (!notBlank(report.getFilterCode()) && notBlank(facts.getFilterCode())) {
            report.setFilterCode(facts.getFilterCode());
        }
        if (!notBlank(report.getUuid()) && notBlank(facts.getUuid())) {
            report.setUuid(facts.getUuid());
        }
        if (!notBlank(report.getUserName()) && notBlank(facts.getUserName())) {
            report.setUserName(facts.getUserName());
        }
    }

    private static boolean isPlaceholderProcess(String process) {
        if (process == null || process.isBlank()) {
            return true;
        }
        String p = process.trim();
        return p.equalsIgnoreCase("-0--0-SAVE")
                || p.matches("(?i)processFilter-.*")
                || p.matches("-?\\d+--\\d+-[A-Za-z_]+");
    }

    /**
     * Cas où la seule « lenteur » détectée est une valeur de took impossible (anomalie de
     * journalisation WORKS). On informe le client sans inventer de cause technique.
     */
    private void applyMeasurementAnomaly(LatencyOriginReportDto report) {
        String warn = report.getMeasurementWarning() != null
                ? report.getMeasurementWarning()
                : "La durée enregistrée est une anomalie de mesure WORKS et ne reflète pas une lenteur réelle.";
        report.setAnalysisConfidence("LOW");
        report.setAnalysisConfidenceReason("La durée provient d'un horodatage erroné (anomalie de mesure) : ce n'est pas une vraie latence.");
        report.setPrimaryCause("Mesure non fiable (anomalie de journalisation)");
        report.setClientSummary(warn);
        report.setNarrativeSummary(
                warn + "\n\n"
                + "Concrètement : l'écran concerné (souvent une impression/édition) journalise un "
                + "horodatage de début erroné, ce qui produit une durée fictive de plusieurs heures. "
                + "L'opération réelle s'est exécutée normalement. "
                + "Aucune action corrective sur la performance n'est nécessaire pour ce cas — "
                + "il s'agit d'un défaut d'instrumentation à corriger côté WORKS si on veut des mesures exactes.");
        report.setChainExplanation(buildChain(report, report.getTimelineSteps()));
        report.setRecommendations(java.util.List.of(
                "Ne pas traiter cette valeur comme une vraie latence (durée de mesure incohérente).",
                "Côté WORKS : corriger l'initialisation de l'horodatage de début pour ces écrans (impression/édition) afin d'obtenir des durées fiables."));
    }

    /**
     * Pistes d'action concrètes, dérivées des faits mesurés (jamais inventées) : elles
     * aident le client à passer de "comprendre la cause" à "corriger le problème".
     */
    private java.util.List<String> buildRecommendations(WorksLatencyFacts facts,
                                                        LatencyBottleneckAnalyzer.NarrativeKind kind) {
        java.util.List<String> recs = new java.util.ArrayList<>();
        com.caciopee.loganalyzer.dto.LatencyQueryGroupDto dominant = facts.dominantRepeatedGroup();

        switch (kind) {
            case RULES_DOMINATED -> {
                if (facts.getMajInsertTotal() > 0) {
                    recs.add("~" + facts.getMajInsertTotal()
                            + " MAJ_Insert observée(s) pendant fire rules"
                            + (facts.getMajInsertDominantType() != null
                            ? " (ex. MAJ_Insert_" + facts.getMajInsertDominantType() + ")"
                            : "")
                            + " : écritures métier confirmées, mais sans took individuel"
                            + " — ne pas les prendre seules pour expliquer toute la durée des règles.");
                }
                // Pagination / lots seulement si volume amont ou écritures massives mesurés.
                if (facts.getPreRulesRowCount() != null && facts.getPreRulesRowCount() > 50) {
                    recs.add("Le volume amont (« "
                            + (facts.getPreRulesFilterCode() != null ? facts.getPreRulesFilterCode() : "filtre")
                            + " », " + facts.getPreRulesRowCount()
                            + " ligne(s)) peut amplifier le coût des règles : restreindre le périmètre avant « Rapprocher Tout » / matchall.");
                } else if (facts.getMajInsertTotal() >= 50) {
                    recs.add("Volume d'écritures MAJ_Insert élevé (~" + facts.getMajInsertTotal()
                            + ") pendant les règles : envisager un traitement par lots plutôt qu'un matchall sur tout le volume"
                            + " — à confirmer avec des traces par règle.");
                }
                if (facts.getBusinessActionLabel() != null
                        && facts.getBusinessActionLabel().toLowerCase(Locale.ROOT).contains("rapprocher")
                        && ((facts.getPreRulesRowCount() != null && facts.getPreRulesRowCount() > 50)
                        || facts.getMajInsertTotal() >= 50)) {
                    recs.add("Pour « Rapprocher Tout » : traiter par paquets (ex. 20–50) plutôt que tout le jeu filtré d'un coup.");
                }
                if (dominant != null) {
                    recs.add("La règle Drools « " + friendlyRuleName(dominant.getFilterCode()) + " » rejoue la même requête "
                            + dominant.getCount() + " fois (souvent une exécution par élément/unité). "
                            + "Mutualiser ces vérifications en une seule requête en lot réduirait fortement le temps total.");
                } else if (facts.dominantDroolsRule() != null) {
                    recs.add("Investiguer la règle Drools « " + friendlyRuleName(facts.dominantDroolsRule())
                            + " » (la plus fréquente dans la fenêtre) : coût SQL interne, volume de faits, nécessité à chaque validation.");
                }
                if (facts.getSqlJoinCount() >= 3) {
                    recs.add("La requête comporte " + facts.getSqlJoinCount() + " jointures : vérifier les index "
                            + "sur les colonnes de jointure et de filtre (ex. composant_fk, cle, date_value).");
                }
                if (facts.getMajInsertTotal() < 50
                        && (facts.getDroolsRuleHits() == null || facts.getDroolsRuleHits().isEmpty())
                        && facts.dominantRepeatedGroup() == null) {
                    recs.add("Activer un niveau de trace plus fin entre « START fire rules » et « END fire rules »"
                            + " (durée par règle, SQL interne, MAJ_Insert) : on sait que les règles durent longtemps,"
                            + " pas encore laquelle ni pourquoi.");
                }
                if (recs.isEmpty()) {
                    recs.add("Confirmer que ce contrôle métier doit s'exécuter à chaque validation, et non uniquement quand c'est nécessaire.");
                }
            }
            case SQL_DOMINATED -> {
                String filter = facts.getFilterCode() != null ? facts.getFilterCode()
                        : (facts.getPreRulesFilterCode() != null ? facts.getPreRulesFilterCode() : "filtre");
                recs.add("Sur le filtre « " + filter + " » : analyser le plan d'exécution SQL (EXPLAIN)"
                        + " et ajouter/vérifier les index des colonnes de filtre et de jointure.");
                if (facts.getSqlJoinCount() >= 3) {
                    recs.add("Requête à " + facts.getSqlJoinCount()
                            + " jointures : simplifier le SQL ou indexer chaque colonne de jointure.");
                }
                if (facts.getRootRowCount() != null && facts.getRootRowCount() <= 1) {
                    recs.add("Lente avec ~0–1 ligne renvoyée : probable problème d'indexation ou de sélectivité"
                            + " du filtre « " + filter + " » — vérifier le plan SQL (EXPLAIN).");
                } else if (facts.getRootRowCount() != null && facts.getRootRowCount() >= 500) {
                    recs.add("Beaucoup de lignes renvoyées (" + facts.getRootRowCount()
                            + ") : restreindre le filtre métier ou paginer côté écran/WS.");
                }
            }
            case PARALLEL_LOAD_DOMINATED -> {
                int rows = facts.getGlobalRowCount() != null ? facts.getGlobalRowCount()
                        : (facts.getElementCount() != null ? facts.getElementCount() : 0);
                recs.add("Réduire le volume chargé (filtres plus restrictifs, pagination)"
                        + (rows > 0 ? " — " + rows + " enregistrement(s) mesurés." : "."));
                if (facts.getLoadChildSampleCount() > 0) {
                    recs.add("Des traces loadListChilds sont présentes : analyser le coût SQL"
                            + " / tables temporaires par lot dans cette fenêtre.");
                } else {
                    recs.add("Analyser les traitements du chargement multithread si des traces plus fines"
                            + " (loadListChilds, requêtes SQL par thread) sont disponibles.");
                }
                if (facts.hasSignificantSecondaryRender()) {
                    recs.add("Second goulot : optimiser le rendu de l'écran (rendering result "
                            + facts.getRenderingMs() + " ms) — le problème ne s'arrête pas au chargement.");
                }
                if (facts.getMemoryPeakMo() != null && facts.getMemoryPeakMo() >= 2000) {
                    recs.add("Pression mémoire élevée (~" + facts.getMemoryPeakMo()
                            + " Mo) : réduire le volume ou surveiller la JVM.");
                }
            }
            case RENDERING_DOMINATED -> {
                recs.add("Réduire le volume de données affiché (filtrage, pagination, chargement progressif).");
                recs.add("Optimiser le mécanisme de rendu de l'écran si un grand volume doit rester affiché.");
                if (facts.getElementCount() != null && facts.getElementCount() > 1000) {
                    recs.add("Volume mesuré avant rendering : " + facts.getElementCount()
                            + " élément(s) — cibler ce périmètre en priorité.");
                }
            }
            case WS_DOMINATED -> {
                recs.add("Analyser le webservice « " + (facts.getTaskName() != null ? facts.getTaskName() : "WS")
                        + " » : temps SQL côté WORKS vs temps de réponse externe.");
                if (dominant != null) {
                    recs.add("Requêtes répétées (« " + dominant.getFilterCode() + " ») : mutualiser ou mettre en cache côté WS.");
                }
                if (facts.getSqlJoinCount() >= 3) {
                    recs.add("Indexer / simplifier la requête derrière le WS (" + facts.getSqlJoinCount() + " jointures).");
                }
            }
            case PRINT_DOMINATED -> {
                recs.add("Réduire le volume imprimé ou différer la génération du document si le volume le justifie.");
                recs.add("Vérifier la génération du document et le traitement printArchive,"
                        + " puis analyser la chaîne d'impression si nécessaire.");
            }
            case OPERATION_LOAD_DOMINATED -> {
                recs.add("Analyser pourquoi loadOperationById / loadOperationContext prend ce temps"
                        + " (requêtes SQL, accès BD, chargement des relations, pièces jointes, etc.).");
                recs.add("Vérifier les performances de la base et des requêtes exécutées pendant ce chargement.");
                recs.add("N'investiguer la génération PDF ou la chaîne d'impression que si des logs"
                        + " montrent explicitement qu'elles consomment le temps.");
            }
            case COMPLEX -> {
                recs.add("Traiter chaque contributeur significatif séparément (SQL, règles, rendering, SAVE)"
                        + " — il n'y a pas un seul goulot exclusif.");
                if (facts.getRootQueryMs() != null && facts.getRootQueryMs() >= 2000) {
                    recs.add("Volet SQL : EXPLAIN / index sur le filtre « "
                            + (facts.getFilterCode() != null ? facts.getFilterCode() : "filtre") + " ».");
                }
                if (facts.getRenderingMs() != null && facts.getRenderingMs() >= 2000) {
                    recs.add("Volet affichage : réduire le volume rendu (pagination / filtres).");
                }
                if (facts.getRulesEngineMs() != null && facts.getRulesEngineMs() >= 2000) {
                    recs.add("Volet règles : traces entre START et END fire rules.");
                }
            }
            case AUTOSTART_DOMINATED -> {
                recs.add("Planifier / espacer les règles AutoStart (ex. TRACE_SESSIONS) hors heures de pointe.");
                recs.add("Vérifier si ce batch doit vraiment s'exécuter aussi souvent.");
            }
            case SAVE_DOMINATED -> {
                recs.add("Optimiser la persistance (insertArrayRel / volume d'enfants) et les index de jointure.");
                if (facts.getSavePersistKey() != null) {
                    recs.add("Cibler la clé SAVE « " + facts.getSavePersistKey() + " ».");
                }
            }
            case WORKFLOW_DOMINATED -> {
                recs.add("Activer des traces plus fines dans Transit / JBPM pour voir les sous-étapes.");
                recs.add("Vérifier si la transition appelle des règles métier, la base, un webservice"
                        + " ou un traitement externe — non démontré par la seule ligne Transit task took.");
                recs.add("Mesurer le détail des sous-étapes du moteur de workflow autour de l'UUID/session.");
            }
            default -> recs.add("Cibler l'étape la plus lente identifiée ci-dessus et vérifier les requêtes et index associés.");
        }
        return recs;
    }

    /**
     * Remonte la "source du problème" en analysant les logs AVANT/PENDANT le took élevé :
     * lorsqu'un même contrôle métier (filtre/règle) rejoue la même requête SQL des dizaines
     * de fois, ces requêtes répétées sont la cause réelle d'une opération dominée par les règles.
     */
    private void applyRootCauseDrillDown(LatencyOriginReportDto report, WorksLatencyFacts facts) {
        List<com.caciopee.loganalyzer.dto.LatencyQueryGroupDto> repeated = facts.getRepeatedQueryGroups().stream()
                .filter(g -> g.getCount() >= 2)
                .limit(6)
                .collect(Collectors.toList());
        report.setRepeatedQueryGroups(repeated);

        com.caciopee.loganalyzer.dto.LatencyQueryGroupDto dominant = facts.dominantRepeatedGroup();
        if (dominant == null && !facts.hasRulesSideEffects()
                && (facts.getDroolsRuleHits() == null || facts.getDroolsRuleHits().isEmpty())) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        String drools = facts.dominantDroolsRule();
        String searchTerm = null;
        String meaning = null;

        if (drools != null) {
            sb.append("Règle Drools dominante : « ").append(friendlyRuleName(drools)).append(" »");
            if (!facts.getDroolsRuleHits().isEmpty()) {
                sb.append(" (").append(facts.getDroolsRuleHits().get(0).getCount()).append("×)");
            }
            sb.append('.');
            if (facts.getDroolsRuleNames().size() > 1) {
                sb.append(" Autres règles : ");
                int n = 0;
                for (String name : facts.getDroolsRuleNames()) {
                    if (name.equalsIgnoreCase(drools)) continue;
                    if (n > 0) sb.append(", ");
                    sb.append(friendlyRuleName(name));
                    if (++n >= 3) break;
                }
                sb.append('.');
            }
            sb.append(' ');
            searchTerm = drools;
            meaning = "Nom de règle visible dans les logs pendant fire rules — piste à investiguer,"
                    + " sans chronométrage individuel prouvant qu'elle explique toute la durée.";
        }
        if (facts.getMajInsertTotal() > 0) {
            String majTerm = facts.getMajInsertDominantType() != null
                    ? "MAJ_Insert_" + facts.getMajInsertDominantType()
                    : "MAJ_Insert_";
            sb.append("Dans la fenêtre des règles : ~").append(facts.getMajInsertTotal())
                    .append(" écriture(s) MAJ_Insert");
            if (facts.getMajInsertDominantType() != null) {
                sb.append(" (dominant : ").append(majTerm)
                        .append(" ×").append(facts.getMajInsertDominantCount()).append(')');
            }
            sb.append('.');
            if (facts.getPreRulesRowCount() != null) {
                sb.append(" Volume amont : ").append(facts.getPreRulesRowCount()).append(" ligne(s)");
                if (facts.getPreRulesFilterCode() != null) {
                    sb.append(" (« ").append(facts.getPreRulesFilterCode()).append(" »)");
                }
                sb.append('.');
            }
            if (facts.getBusinessActionLabel() != null) {
                sb.append(" Action : « ").append(facts.getBusinessActionLabel()).append(" ».");
            }
            searchTerm = majTerm;
            meaning = "MAJ_Insert = écriture métier déclenchée par une règle (insert/update métier WORKS)."
                    + " Leur présence prouve que les règles font des écritures pendant la phase lente,"
                    + " mais sans took individuel on ne peut pas dire qu'elles expliquent à elles seules"
                    + " toute la durée des running rules. Cliquez pour voir ces lignes dans les logs.";
        } else if (dominant != null) {
            sb.append("La règle / contrôle « ").append(friendlyRuleName(dominant.getFilterCode())).append(" » exécute ")
                    .append(dominant.getCount()).append(" requêtes SQL répétées (≈ ")
                    .append(formatDuration(dominant.getTotalMs())).append(" cumulées, jusqu'à ")
                    .append(formatDuration(dominant.getMaxMs())).append(" pour une seule). ")
                    .append("Ces requêtes rejouées sont la source principale de la lenteur.");
            searchTerm = dominant.getFilterCode();
            meaning = "Le même filtre / contrôle SQL est rejoué plusieurs fois dans la fenêtre :"
                    + " le cumul de ces requêtes explique une part mesurée de la lenteur."
                    + " Cliquez pour voir toutes les exécutions dans les logs.";
        } else if (drools == null) {
            return;
        }
        if (sb.length() > 0) {
            report.setRootCauseSummary(sb.toString().trim());
        }
        if (searchTerm != null && !searchTerm.isBlank()) {
            report.setRootCauseSearchTerm(searchTerm.trim());
        }
        if (meaning != null) {
            report.setRootCauseMeaning(meaning);
        }
    }

    private static String friendlyRuleName(String raw) {
        if (raw == null || raw.isBlank()) return "n/a";
        String s = raw.trim();
        if (s.toLowerCase(Locale.ROOT).endsWith(".java")) {
            return s.substring(0, s.length() - 5);
        }
        return s;
    }

    /**
     * Les logs WORKS répètent souvent le nom de l'entité (ex. "DChargementContDChargementCont",
     * "ManifesteManifeste"). On rend l'identifiant exploitable en le dédoublant → "DChargementCont".
     */
    private String cleanClassName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        int len = s.length();
        if (len % 2 == 0) {
            String first = s.substring(0, len / 2);
            String second = s.substring(len / 2);
            if (first.equals(second)) {
                return first;
            }
        }
        return s;
    }

    private String buildBottleneckSearchTerm(LatencyTimelineStepDto primary) {
        if (primary == null || primary.getDurationMs() == null || primary.getDurationMs() <= 0) {
            return null;
        }
        return "took [" + primary.getDurationMs() + "]";
    }



    private void markBottleneck(List<LatencyTimelineStepDto> steps, LatencyTimelineStepDto primary) {

        for (LatencyTimelineStepDto step : steps) {

            step.setBottleneck(sameStep(step, primary));

        }

    }



    private String describeCause(LatencyTimelineStepDto primary, int sqlJoins) {

        String type = safe(primary.getStepType());

        String label = classifier.humanLabel(type, primary.getOperationName());



        StringBuilder sb = new StringBuilder();

        sb.append(label).append(" — ").append(formatDuration(primary.getDurationMs()));

        if (primary.getRowCount() != null) {

            sb.append(" — ").append(primary.getRowCount()).append(" ligne(s)");

        }

        if (primary.getMemoryMo() != null) {

            sb.append(" — ").append(primary.getMemoryMo()).append(" Mo");

        }

        if (LatencyOperationClassifier.ROOT_QUERY.equals(type) && sqlJoins >= 3) {

            sb.append(" — ").append(sqlJoins).append(" jointures SQL");

        }

        return sb.toString();

    }



    private String buildNarrative(LatencyOriginReportDto report,

                                  List<LatencyTimelineStepDto> steps,

                                  LatencyTimelineStepDto primary,

                                  LatencyBottleneckAnalyzer.NarrativeKind kind,

                                  int sqlJoins) {

        return switch (kind) {

            case SQL_DOMINATED -> buildSqlDominatedNarrative(report, steps, sqlJoins);

            case PARALLEL_LOAD_DOMINATED -> buildParallelLoadNarrative(report, steps, primary);

            case RULES_DOMINATED, WS_DOMINATED, PRINT_DOMINATED, AUTOSTART_DOMINATED ->
                    buildRulesNarrative(report, primary);

            case RENDERING_DOMINATED -> buildRenderingNarrative(report, primary);

            case SAVE_DOMINATED, WORKFLOW_DOMINATED, OPERATION_LOAD_DOMINATED ->
                    buildSingleStepNarrative(report, primary);

            case CONTAINER_DOMINATED -> buildContainerNarrative(report, primary, steps);

            case COMPLEX -> buildComplexNarrative(report, steps, primary);

            case SINGLE_STEP -> buildSingleStepNarrative(report, primary);

        };

    }



    private String buildSqlDominatedNarrative(LatencyOriginReportDto report,

                                              List<LatencyTimelineStepDto> steps,

                                              int sqlJoins) {

        LatencyTimelineStepDto root = findStep(steps, LatencyOperationClassifier.ROOT_QUERY);

        LatencyTimelineStepDto global = findStep(steps, LatencyOperationClassifier.GLOBAL_CONTAINER,

                LatencyOperationClassifier.PARTITIONAL);

        LatencyTimelineStepDto load = findStep(steps, LatencyOperationClassifier.CHILD_LOAD);

        LatencyTimelineStepDto searchAttr = findStep(steps, LatencyOperationClassifier.CHILD_QUERY);

        LatencyTimelineStepDto loadB2 = findStep(steps, LatencyOperationClassifier.CHILD_LOAD_B2);



        StringBuilder sb = new StringBuilder();

        sb.append("La lenteur provient quasi entièrement de la requête SQL racine.\n\n");



        appendStepLine(sb, 1, root);

        sb.append("   → ");

        if (sqlJoins >= 3) {

            sb.append("Requête SQL complexe (").append(sqlJoins).append(" jointures détectées).\n");

        } else {

            sb.append("Exécution SQL lente sur la recherche racine.\n");

        }



        long globalMs = global != null && global.getDurationMs() != null

                ? global.getDurationMs()

                : root.getDurationMs();

        double share = globalMs > 0 ? (100.0 * root.getDurationMs() / globalMs) : 100.0;

        sb.append("   → Représente ~").append(String.format(Locale.FRANCE, "%.0f", share))

                .append(" % du temps total de l'opération globale (")

                .append(formatDuration(globalMs)).append(").\n");



        if (global != null) {

            sb.append("\n");

            appendStepLine(sb, 2, global);

            sb.append("   → Phase aval rapide");

            if (load != null) {

                sb.append(" : ").append(load.getOperationName()).append(" ")

                        .append(formatDuration(load.getDurationMs()));

            }

            if (searchAttr != null) {

                sb.append(", ").append(searchAttr.getOperationName()).append(" ")

                        .append(formatDuration(searchAttr.getDurationMs()));

            }

            if (loadB2 != null) {

                sb.append(", Query B2 ").append(formatDuration(loadB2.getDurationMs()));

            }

            sb.append(".\n");

        }



        sb.append("\nConclusion : avec ")

                .append(root.getRowCount() != null ? root.getRowCount() : "peu")

                .append(" ligne(s) retournée(s), le goulet est l'exécution SQL initiale, ")

                .append("pas le chargement parallèle des enfants.");



        return sb.toString().trim();

    }



    private String buildParallelLoadNarrative(LatencyOriginReportDto report,

                                              List<LatencyTimelineStepDto> steps,

                                              LatencyTimelineStepDto primary) {

        LatencyTimelineStepDto root = findStep(steps, LatencyOperationClassifier.ROOT_QUERY);

        LatencyTimelineStepDto global = findStep(steps, LatencyOperationClassifier.GLOBAL_CONTAINER,

                LatencyOperationClassifier.PARTITIONAL);

        LatencyTimelineStepDto multithread = findStep(steps, LatencyOperationClassifier.MULTITHREAD_START,

                LatencyOperationClassifier.MULTITHREAD_CONFIG);



        StringBuilder sb = new StringBuilder();

        sb.append("La lenteur est dominée par le chargement parallèle multithread des données.\n\n");



        if (root != null) {

            appendStepLine(sb, 1, root);

            sb.append("   → La requête racine retourne ")

                    .append(root.getRowCount() != null ? root.getRowCount() : "un volume")

                    .append(" ligne(s) et déclenche le chargement parallèle.\n\n");

        }



        if (multithread != null && multithread.getRowCount() != null && multithread.getRowCount() > 1) {

            sb.append("2. Multithreading : ")

                    .append(multithread.getRowCount()).append(" lot(s) traités en parallèle\n");

        }



        int n = root != null ? 2 : 1;

        appendStepLine(sb, n, primary);

        sb.append("   → Cette étape concentre le temps d'attente utilisateur.\n");



        if (global != null && !sameStep(primary, global)) {

            sb.append("\n");

            appendStepLine(sb, n + 1, global);

            sb.append("   → Durée globale incluant toutes les phases de recherche.\n");

        }



        sb.append("\nLe symptôme (").append(formatDuration(report.getMaxDurationMs()))

                .append(") est porté principalement par « ")

                .append(primary.getOperationName()).append(" ».");



        return sb.toString().trim();

    }



    private String buildRulesNarrative(LatencyOriginReportDto report, LatencyTimelineStepDto primary) {

        StringBuilder sb = new StringBuilder();

        sb.append("La lenteur provient du moteur de règles métier (running rules).\n\n");

        appendStepLine(sb, 1, primary);

        sb.append("\nConclusion : l'exécution des règles Drools sur ")

                .append(label(report.getFilterCode(), report.getProcessName()))

                .append(" consomme la quasi-totalité du temps (")

                .append(formatDuration(primary.getDurationMs()))

                .append("). Vérifier la complexité des règles et le volume de faits en mémoire.");

        return sb.toString().trim();

    }



    private String buildRenderingNarrative(LatencyOriginReportDto report, LatencyTimelineStepDto primary) {

        StringBuilder sb = new StringBuilder();

        sb.append("La lenteur est concentrée sur la phase rendering result.\n\n");

        appendStepLine(sb, 1, primary);

        sb.append("\nLes phases de préparation (règles, sauvegarde, agrégation) sont terminées ;");
        sb.append(" le goulot est la génération ou l'affichage du résultat (");
        sb.append(formatDuration(primary.getDurationMs())).append(").");

        return sb.toString().trim();

    }



    private String buildContainerNarrative(LatencyOriginReportDto report,

                                             LatencyTimelineStepDto primary,

                                             List<LatencyTimelineStepDto> steps) {

        StringBuilder sb = new StringBuilder();

        sb.append("La lenteur est portée par l'opération globale, sans sous-étape clairement dominante.\n\n");

        appendStepLine(sb, 1, primary);



        List<LatencyTimelineStepDto> children = steps.stream()

                .filter(s -> s.getDurationMs() != null && s.getDurationMs() >= 500)

                .filter(s -> !sameStep(s, primary))

                .sorted(Comparator.comparingLong((LatencyTimelineStepDto s) -> s.getDurationMs()).reversed())

                .limit(3)

                .toList();



        if (!children.isEmpty()) {

            sb.append("\n\nSous-étapes significatives :\n");

            int i = 2;

            for (LatencyTimelineStepDto child : children) {

                appendStepLine(sb, i++, child);

            }

        }



        sb.append("\n\nSymptôme global : ").append(formatDuration(report.getMaxDurationMs()));

        return sb.toString().trim();

    }



    private String buildComplexNarrative(LatencyOriginReportDto report,

                                         List<LatencyTimelineStepDto> steps,

                                         LatencyTimelineStepDto primary) {

        StringBuilder sb = new StringBuilder();

        sb.append("Plusieurs étapes contribuent à la lenteur sur ")

                .append(label(report.getFilterCode(), report.getProcessName()))

                .append(" :\n\n");



        List<LatencyTimelineStepDto> significant = steps.stream()

                .filter(s -> s.getDurationMs() != null && s.getDurationMs() >= 500)

                .sorted(Comparator.comparingLong((LatencyTimelineStepDto s) -> s.getDurationMs()).reversed())

                .limit(5)

                .toList();



        int i = 1;

        for (LatencyTimelineStepDto step : significant) {

            appendStepLine(sb, i++, step);

            if (sameStep(step, primary)) {

                sb.append("   → Étape principale identifiée.\n");

            }

        }



        sb.append("\nLe symptôme (").append(formatDuration(report.getMaxDurationMs()))

                .append(") est porté principalement par « ")

                .append(primary.getOperationName()).append(" ».");



        return sb.toString().trim();

    }



    private String buildSingleStepNarrative(LatencyOriginReportDto report, LatencyTimelineStepDto primary) {

        StringBuilder sb = new StringBuilder();

        sb.append("Origine de la lenteur sur ")

                .append(label(report.getFilterCode(), report.getProcessName()))

                .append(" :\n\n");

        appendStepLine(sb, 1, primary);

        sb.append("\nLe symptôme observé (")

                .append(formatDuration(report.getMaxDurationMs()))

                .append(") correspond à l'étape « ")

                .append(primary.getOperationName())

                .append(" ».");

        return sb.toString().trim();

    }



    private String buildChain(LatencyOriginReportDto report, List<LatencyTimelineStepDto> steps) {

        String user = notBlank(report.getUserName()) ? report.getUserName() : "utilisateur";

        String filter = notBlank(report.getFilterCode()) ? report.getFilterCode() : "filtre métier";

        String process = notBlank(report.getProcessName()) ? report.getProcessName() : "processus";



        if (steps == null || steps.isEmpty()) {

            return user + " → " + process + " → filtre " + filter;

        }



        List<String> chainParts = steps.stream()

                .filter(s -> s.getDurationMs() != null || LatencyOperationClassifier.SEARCH_START.equals(s.getStepType()))

                .map(s -> {

                    if (LatencyOperationClassifier.SEARCH_START.equals(s.getStepType())) {

                        return "début recherche";

                    }

                    if (LatencyOperationClassifier.ROOT_QUERY.equals(s.getStepType())) {

                        return "requête racine SQL";

                    }

                    if (LatencyOperationClassifier.CHILD_LOAD.equals(s.getStepType())) {

                        return "chargement enfants";

                    }

                    if (LatencyOperationClassifier.GLOBAL_CONTAINER.equals(s.getStepType())) {

                        return "opération globale";

                    }

                    if (LatencyOperationClassifier.RULES_ENGINE.equals(s.getStepType())) {

                        return "moteur de règles";

                    }

                    if (LatencyOperationClassifier.RENDERING.equals(s.getStepType())) {

                        return "rendu résultat";

                    }

                    return s.getOperationName();

                })

                .distinct()

                .limit(6)

                .collect(Collectors.toList());



        String chain = String.join(" → ", chainParts);

        return user + " → " + process + " → filtre " + filter + " → " + chain;

    }



    private static final Pattern SYNTH_TOOK = Pattern.compile(
            "took\\s*(?:\\[(\\d{1,9})]|(\\d{1,9}))\\s*(?:\\(\\s*ms\\s*\\)|ms)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Filet de sécurité : reconstruit des étapes depuis message (sans rawLog |||)
     * quand le parseur a renvoyé une timeline vide.
     */
    private List<LatencyTimelineStepDto> synthesizeStepsFromWindow(LatencyOriginReportDto report,
                                                                   List<LogEntry> windowLogs) {
        List<LatencyTimelineStepDto> out = new ArrayList<>();
        if (windowLogs == null) {
            return out;
        }
        for (LogEntry log : windowLogs) {
            String msg = firstNonBlank(log.getMessage(), log.getRawLog());
            if (msg == null || msg.isBlank()) {
                continue;
            }
            String lower = msg.toLowerCase(Locale.ROOT);
            Long duration = null;
            Matcher took = SYNTH_TOOK.matcher(msg);
            if (took.find()) {
                String g = took.group(1) != null ? took.group(1) : took.group(2);
                try {
                    duration = Long.parseLong(g);
                } catch (NumberFormatException ignored) {
                    duration = null;
                }
            }
            String type = null;
            String label = null;
            if (lower.contains("doaction")) {
                type = LatencyOperationClassifier.BUSINESS_ACTION;
                label = "doAction";
                Matcher am = Pattern.compile("actionName\\s*:\\s*([^,]+)", Pattern.CASE_INSENSITIVE).matcher(msg);
                if (am.find()) {
                    label = "doAction " + am.group(1).replaceAll("(?i)#icon:[^#]*#", "").trim();
                }
            } else if (lower.contains("running rules") || lower.contains("fire rules")) {
                type = LatencyOperationClassifier.RULES_ENGINE;
                label = lower.contains("start fire rules") ? "START fire rules"
                        : (lower.contains("running rules") ? "running rules" : "fire rules");
            } else if (lower.contains("transit task") || lower.contains("start transit")) {
                type = LatencyOperationClassifier.WORKFLOW;
                label = lower.contains("transit task") ? "Transit task" : "Start transit";
            } else if (lower.contains("preparesearchbyroot") && duration != null) {
                type = LatencyOperationClassifier.ROOT_QUERY;
                label = "prepareSearchByRoot";
            } else if (lower.contains("global searchcomposantbyroot") && duration != null) {
                type = LatencyOperationClassifier.GLOBAL_CONTAINER;
                label = "global searchComposantByRoot";
            } else if (lower.contains("rendering result") && duration != null) {
                type = LatencyOperationClassifier.RENDERING;
                label = "rendering result";
            }
            if (type == null) {
                continue;
            }
            if (duration == null && report.getMaxDurationMs() != null
                    && LatencyOperationClassifier.BUSINESS_ACTION.equals(type)) {
                duration = report.getMaxDurationMs();
            }
            LatencyTimelineStepDto step = new LatencyTimelineStepDto();
            step.setStepType(type);
            step.setOperationName(label);
            step.setDurationMs(duration);
            step.setLogId(log.getId());
            step.setTimestamp(log.getLogTimestamp());
            step.setDetail(msg.length() > 280 ? msg.substring(0, 280) : msg);
            out.add(step);
        }
        return out;
    }

    private void applyChronologyWithoutTimedSteps(LatencyOriginReportDto report, List<LogEntry> windowLogs) {
        List<String> markers = detectBusinessMarkers(windowLogs);
        String origin = deduceOriginFromMarkers(markers, report);

        report.setAnalysisConfidence(markers.isEmpty() ? "LOW" : "MEDIUM");
        report.setAnalysisConfidenceReason(markers.isEmpty()
                ? "Fenêtre sans marqueurs métier ni took exploitables : il manque les traces autour de l'ancre (UUID/session)."
                : "Chronologie métier présente, mais sans sous-étapes chronométrées détaillées :"
                + " origine déduite des marqueurs, pas d'un pourcentage exact.");

        report.setPrimaryCause("Origine : " + origin);
        report.setClientSummary("L'action dure " + formatDuration(report.getMaxDurationMs())
                + " — origine retenue : " + origin
                + (markers.isEmpty()
                ? " (preuves internes absentes de la fenêtre)."
                : " (d'après la chronologie des logs métier)."));

        StringBuilder narrative = new StringBuilder();
        narrative.append("Chronologie reconstituée à partir des logs métier")
                .append(" (pas seulement les lignes took).\n\n");
        narrative.append("Durée de l'action : ").append(formatDuration(report.getMaxDurationMs())).append(".\n");
        if (!markers.isEmpty()) {
            narrative.append("\nTraitements observés pendant la période :\n");
            for (String marker : markers) {
                narrative.append("• ").append(marker).append('\n');
            }
        } else {
            narrative.append("\nAucun marqueur START fire rules / running rules / Transit /")
                    .append(" searchComposantByRoot / SQL n'apparaît dans la fenêtre analysée.\n");
        }
        narrative.append("\nOrigine : ").append(origin).append(".\n");
        narrative.append("Informations manquantes pour un % exact par sous-étape : ")
                .append("took internes entre le début et la fin de l'action ")
                .append("(running rules, Transit task, prepareSearchByRoot, persist, etc.).\n");
        report.setNarrativeSummary(narrative.toString().trim());

        List<String> why = new ArrayList<>();
        why.add("L'action ancrée dure " + formatDuration(report.getMaxDurationMs())
                + (report.getActionName() != null ? " (« " + report.getActionName() + " »)" : "")
                + ".");
        if (!markers.isEmpty()) {
            why.add("Pendant cette période, les logs montrent : " + String.join(" → ", markers) + ".");
        } else {
            why.add("La fenêtre ne contient pas assez de marqueurs métier pour décomposer le temps "
                    + "— élargir UUID/session autour de l'ancre.");
        }
        why.add("Sans chronométrages internes, on ne peut pas calculer le % exact de chaque sous-étape ; "
                + "l'origine ci-dessus est déduite de la chronologie disponible.");
        report.setWhyChain(why);

        List<String> recs = new ArrayList<>();
        recs.add("Élargir le filtre UUID/session et chercher START/END fire rules, Transit task, "
                + "searchComposantByRoot, prepareSearchByRoot autour de l'ancre.");
        recs.add("Récupérer les took [N] ms entre le début et la fin de l'action pour mesurer chaque composant.");
        report.setRecommendations(recs);
        report.setChainExplanation(buildChain(report, List.of()));
        report.setLatencyFamily("CHRONOLOGY_MARKERS");
    }

    private List<String> detectBusinessMarkers(List<LogEntry> windowLogs) {
        Set<String> ordered = new LinkedHashSet<>();
        if (windowLogs == null) {
            return List.of();
        }
        for (LogEntry log : windowLogs) {
            String lower = (safe(log.getMessage()) + " " + safe(log.getRawLog())).toLowerCase(Locale.ROOT);
            if (lower.contains("doaction")) {
                ordered.add("doAction");
            }
            if (lower.contains("start fire rules")) {
                ordered.add("START fire rules");
            }
            if (lower.contains("running rules")) {
                ordered.add("running rules");
            }
            if (lower.contains("end fire rules")) {
                ordered.add("END fire rules");
            }
            if (lower.contains("transit task") || lower.contains("start transit")) {
                ordered.add("Transit task");
            }
            if (lower.contains("jbpm") || lower.contains("persist operation")) {
                ordered.add("persist / JBPM");
            }
            if (lower.contains("preparesearchbyroot")) {
                ordered.add("prepareSearchByRoot (SQL)");
            }
            if (lower.contains("searchcomposantbyroot") || lower.contains("multithreading")) {
                ordered.add("searchComposantByRoot");
            }
            if (lower.contains("rendering result")) {
                ordered.add("rendering result");
            }
            if (lower.contains("maj_insert")) {
                ordered.add("MAJ_Insert");
            }
            if (lower.contains("total time save") || lower.contains("saveprocesscontent")) {
                ordered.add("SAVE / persistance");
            }
        }
        return new ArrayList<>(ordered);
    }

    private String deduceOriginFromMarkers(List<String> markers, LatencyOriginReportDto report) {
        String action = ((report.getActionName() != null ? report.getActionName() : "")
                + " " + (report.getFilterCode() != null ? report.getFilterCode() : ""))
                .toLowerCase(Locale.ROOT);
        boolean rulesAction = action.contains("matchall") || action.contains("rapprocher");

        if (markers.stream().anyMatch(m -> m.toLowerCase(Locale.ROOT).contains("searchcomposant"))
                && markers.stream().noneMatch(m -> m.toLowerCase(Locale.ROOT).contains("fire")
                || m.toLowerCase(Locale.ROOT).contains("running rules"))) {
            return "recherche multithread (searchComposantByRoot)";
        }
        if (markers.stream().anyMatch(m -> m.equals("Transit task"))
                && markers.stream().anyMatch(m -> m.toLowerCase(Locale.ROOT).contains("fire")
                || m.toLowerCase(Locale.ROOT).contains("running rules"))) {
            return "Transit task englobant les règles métier";
        }
        if (markers.stream().anyMatch(m -> m.toLowerCase(Locale.ROOT).contains("running rules")
                || m.toLowerCase(Locale.ROOT).contains("fire rules")
                || m.equals("MAJ_Insert"))
                || rulesAction) {
            return "exécution des règles métier (running rules / fire rules)";
        }
        if (markers.stream().anyMatch(m -> m.toLowerCase(Locale.ROOT).contains("sql")
                || m.toLowerCase(Locale.ROOT).contains("preparesearch"))) {
            return "requête SQL";
        }
        if (markers.stream().anyMatch(m -> m.toLowerCase(Locale.ROOT).contains("save")
                || m.toLowerCase(Locale.ROOT).contains("persist"))) {
            return "persistance";
        }
        if (markers.stream().anyMatch(m -> m.toLowerCase(Locale.ROOT).contains("rendering"))) {
            return "rendu / affichage (rendering result)";
        }
        if (markers.stream().anyMatch(m -> m.equals("doAction"))) {
            return rulesAction
                    ? "exécution des règles métier (action matchall / Rapprocher — détail interne absent de la fenêtre)"
                    : "action métier (doAction) — détail interne absent de la fenêtre";
        }
        return "non déterminée — traces START/END fire rules, Transit, SQL ou search absentes de la fenêtre";
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private String buildFallbackNarrative(LatencyOriginReportDto report) {

        return "Latence détectée (" + formatDuration(report.getMaxDurationMs()) + ") sur "

                + label(report.getFilterCode(), report.getProcessName())

                + ". Reconstituer la chronologie autour de l'UUID/session "

                + "(START fire rules, running rules, Transit, SQL) pour isoler l'origine.";

    }



    private record Confidence(String level, String reason) {
    }

    /**
     * Fiabilité <b>honnête</b>, dérivée de preuves réelles et non d'une constante :
     * <ul>
     *   <li>HIGH : un goulot chronométré qui domine nettement le temps total, sans étape
     *       concurrente de durée comparable ;</li>
     *   <li>MEDIUM : goulot identifié mais part modérée ou plusieurs étapes de poids voisin
     *       (la cause exacte est plausible mais pas certaine) ;</li>
     *   <li>LOW : étape non structurée / durée non rattachée à une opération WORKS connue.</li>
     * </ul>
     */
    private Confidence computeConfidence(List<LatencyTimelineStepDto> steps,
                                         LatencyBottleneckAnalyzer.NarrativeKind kind,
                                         LatencyTimelineStepDto primary,
                                         long headlineMs,
                                         WorksLatencyFacts facts) {
        if (primary == null || primary.getDurationMs() == null || primary.getDurationMs() <= 0) {
            return new Confidence("LOW",
                    "Aucune étape chronométrée fiable : la cause est indicative.");
        }
        long primaryMs = primary.getDurationMs();
        // Ne pas comparer le goulot au seul max(took) : une étape secondaire (ex. rendering)
        // peut être absente du headline et fausser un « 100 % ».
        long pipeline = Math.max(headlineMs, primaryMs);
        if (facts != null) {
            pipeline = Math.max(pipeline, facts.observedPipelineMs());
        }
        double share = pipeline > 0 ? Math.min(1.0, (double) primaryMs / pipeline) : 1.0;
        int sharePct = (int) Math.round(share * 100);

        boolean typed = kind == LatencyBottleneckAnalyzer.NarrativeKind.SQL_DOMINATED
                || kind == LatencyBottleneckAnalyzer.NarrativeKind.PARALLEL_LOAD_DOMINATED
                || kind == LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED
                || kind == LatencyBottleneckAnalyzer.NarrativeKind.RENDERING_DOMINATED
                || kind == LatencyBottleneckAnalyzer.NarrativeKind.WS_DOMINATED
                || kind == LatencyBottleneckAnalyzer.NarrativeKind.PRINT_DOMINATED
                || kind == LatencyBottleneckAnalyzer.NarrativeKind.OPERATION_LOAD_DOMINATED
                || kind == LatencyBottleneckAnalyzer.NarrativeKind.AUTOSTART_DOMINATED
                || kind == LatencyBottleneckAnalyzer.NarrativeKind.SAVE_DOMINATED
                || kind == LatencyBottleneckAnalyzer.NarrativeKind.WORKFLOW_DOMINATED
                || kind == LatencyBottleneckAnalyzer.NarrativeKind.CONTAINER_DOMINATED;

        if (typed) {
            if (facts != null && kind == LatencyBottleneckAnalyzer.NarrativeKind.PARALLEL_LOAD_DOMINATED
                    && facts.hasSignificantSecondaryRender()) {
                return new Confidence("MEDIUM", String.format(Locale.FRANCE,
                        "Deux goulots successifs : chargement parallèle ≈ %d %% du parcours mesuré"
                                + " (global + rendering), puis rendering result encore %s."
                                + " Ce n'est pas 100 %% du temps total.",
                        sharePct, formatDuration(facts.getRenderingMs())));
            }
            if (share >= 0.80) {
                return new Confidence("HIGH", String.format(Locale.FRANCE,
                        "Goulot net : l'étape identifiée représente %d %% du parcours mesuré"
                                + " (phases séquentielles chronométrées), pas seulement du plus gros took.",
                        sharePct));
            }
            return new Confidence("MEDIUM", String.format(Locale.FRANCE,
                    "Cause principale identifiée mais elle ne couvre que %d %% du parcours mesuré"
                            + " : une partie du délai vient d'ailleurs.",
                    sharePct));
        }
        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.COMPLEX) {
            return new Confidence("MEDIUM",
                    "Plusieurs étapes contribuent à la lenteur : la cause dominante est probable mais pas exclusive.");
        }
        // SINGLE_STEP et autres : une durée existe mais sans structure WORKS typée.
        if (share >= 0.80) {
            return new Confidence("MEDIUM",
                    "Une étape lente est identifiée mais sans type d'opération WORKS reconnu : cause à confirmer.");
        }
        return new Confidence("LOW",
                "Durée mesurée diffuse, sans goulot dominant : analyse indicative.");
    }



    private int countSqlJoins(List<LogEntry> windowLogs) {

        if (windowLogs == null) {

            return 0;

        }

        int maxJoins = 0;

        for (LogEntry log : windowLogs) {

            String text = safe(log.getMessage()) + " " + safe(log.getRawLog());

            if (!text.toLowerCase(Locale.ROOT).contains("query:")) {

                continue;

            }

            int joins = 0;

            Matcher matcher = JOIN_PATTERN.matcher(text);

            while (matcher.find()) {

                joins++;

            }

            maxJoins = Math.max(maxJoins, joins);

        }

        return maxJoins;

    }



    private void appendStepLine(StringBuilder sb, int index, LatencyTimelineStepDto step) {

        if (step == null) {

            return;

        }

        sb.append(index).append(". ").append(step.getOperationName()).append(" : ");

        sb.append(formatDuration(step.getDurationMs()));

        if (step.getRowCount() != null) {

            sb.append(" — ").append(step.getRowCount()).append(" ligne(s)");

        }

        if (step.getMemoryMo() != null) {

            sb.append(" — ").append(step.getMemoryMo()).append(" Mo");

        }

        sb.append('\n');

    }



    private LatencyTimelineStepDto findStep(List<LatencyTimelineStepDto> steps, String... types) {

        for (String type : types) {

            for (LatencyTimelineStepDto step : steps) {

                if (type.equals(step.getStepType())) {

                    return step;

                }

            }

        }

        return null;

    }



    private boolean sameStep(LatencyTimelineStepDto a, LatencyTimelineStepDto b) {

        if (a == null || b == null) {

            return false;

        }

        if (a.getLogId() != null && b.getLogId() != null) {

            return Objects.equals(a.getLogId(), b.getLogId());

        }

        return Objects.equals(a.getStepType(), b.getStepType())

                && Objects.equals(a.getOperationName(), b.getOperationName());

    }



    private String label(String filter, String process) {

        if (notBlank(filter)) {

            return "le filtre " + filter.trim();

        }

        if (notBlank(process)) {

            return "le process " + process.trim();

        }

        return "cette opération";

    }



    private String formatDuration(Long ms) {

        if (ms == null || ms <= 0) {

            return "durée non mesurée";

        }

        if (ms >= 1000) {

            double sec = ms / 1000.0;

            return String.format(Locale.FRANCE, "%.1f s", sec);

        }

        return ms + " ms";

    }



    private String safe(String value) {

        return value != null ? value : "";

    }



    private boolean notBlank(String value) {

        return value != null && !value.isBlank();

    }

}


