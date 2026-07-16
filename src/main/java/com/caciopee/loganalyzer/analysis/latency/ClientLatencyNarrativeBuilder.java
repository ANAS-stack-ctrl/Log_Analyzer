package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.dto.LatencyQueryGroupDto;
import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Récit client 100 % factuel à partir de {@link WorksLatencyFacts} — sans LLM.
 */
@Component
public class ClientLatencyNarrativeBuilder {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public String buildSummary(WorksLatencyFacts facts,
                               LatencyBottleneckAnalyzer.NarrativeKind kind,
                               LatencyTimelineStepDto primary,
                               long maxDurationMs) {
        String user = labelUser(facts.getUserName());
        String filter = labelScope(facts);
        long totalMs = resolveTotalMs(facts, primary, maxDurationMs);
        int rows = resolveRowCount(facts, primary);

        return switch (kind) {
            case SQL_DOMINATED -> String.format(Locale.FRANCE,
                    "%s a lancé une recherche sur %s ; le système a exécuté une requête SQL lourde"
                            + " (%s) pour %s — l'opération complète a pris %s.",
                    user, filter, formatDuration(effectiveRootMs(facts, primary)), formatRows(rows), formatDuration(totalMs));
            case PARALLEL_LOAD_DOMINATED -> {
                String base = String.format(Locale.FRANCE,
                        "%s a lancé une recherche sur %s ; le système a chargé %s"
                                + "%s en multithread%s — chargement %s",
                        user, filter, formatRows(rows), labelObject(facts.getClassName()),
                        describeParallelShort(facts),
                        formatDuration(firstPositive(facts.getGlobalMs(), durationOf(primary), totalMs)));
                if (facts.hasSignificantSecondaryRender()) {
                    yield base + String.format(Locale.FRANCE,
                            ", puis rendering result encore %s (deux goulots successifs).",
                            formatDuration(facts.getRenderingMs()));
                }
                yield base + " avant l'affichage.";
            }
            case RULES_DOMINATED -> buildRulesSummary(facts, user, filter, primary, maxDurationMs);
            case RENDERING_DOMINATED -> String.format(Locale.FRANCE,
                    "%s a consulté %s ; l'affichage des résultats a pris %s (les données étaient déjà chargées).",
                    user, filter, formatDuration(firstPositive(facts.getRenderingMs(), durationOf(primary), maxDurationMs)));
            case WS_DOMINATED -> String.format(Locale.FRANCE,
                    "%s a déclenché un appel webservice (%s) ; le traitement côté WORKS a pris %s.",
                    user, wsLabel(facts), formatDuration(totalMs));
            case PRINT_DOMINATED -> String.format(Locale.FRANCE,
                    "%s a lancé une impression / génération PDF sur %s ; l'opération a pris %s.",
                    user, filter, formatDuration(totalMs));
            case OPERATION_LOAD_DOMINATED -> String.format(Locale.FRANCE,
                    "%s a déclenché une action sur %s ; le temps est entièrement consommé"
                            + " lors du chargement du contexte / de l'opération (%s) — pas la génération document.",
                    user, filter,
                    formatDuration(firstPositive(durationOf(primary), totalMs)));
            case AUTOSTART_DOMINATED -> String.format(Locale.FRANCE,
                    "Des règles AutoStart (%s) se sont exécutées en arrière-plan et ont pris %s"
                            + " — sans action utilisateur directe sur un écran.",
                    orNa(facts.getTaskName() != null ? facts.getTaskName() : facts.getProcessName()),
                    formatDuration(totalMs));
            case SAVE_DOMINATED -> String.format(Locale.FRANCE,
                    "%s a enregistré / validé sur %s ; la persistance a pris %s.",
                    user, filter, formatDuration(firstPositive(facts.getSavePersistMs(), durationOf(primary), maxDurationMs)));
            case WORKFLOW_DOMINATED -> String.format(Locale.FRANCE,
                    "%s a exécuté la transition %s. L'étape Transit task a pris %s"
                            + ", ce qui représente la totalité de la durée mesurée dans cette fenêtre.",
                    user, orNa(formatActionDisplay(facts)),
                    formatDuration(firstPositive(durationOf(primary), totalMs)));
            case COMPLEX -> String.format(Locale.FRANCE,
                    "%s a effectué une opération sur %s qui a pris %s"
                            + " — plusieurs étapes contribuent de façon comparable (pas un seul goulot).",
                    user, filter, formatDuration(totalMs));
            case CONTAINER_DOMINATED, SINGLE_STEP -> String.format(Locale.FRANCE,
                    "%s a effectué une opération sur %s qui a pris %s"
                            + (primary != null && primary.getOperationName() != null
                            ? " — étape dominante : " + primary.getOperationName() : "")
                            + ".",
                    user, filter, formatDuration(totalMs));
        };
    }

    public String buildPrimaryCause(WorksLatencyFacts facts,
                                    LatencyBottleneckAnalyzer.NarrativeKind kind,
                                    LatencyTimelineStepDto primary) {
        return switch (kind) {
            case SQL_DOMINATED -> "Requête SQL initiale trop lente — "
                    + formatDuration(effectiveRootMs(facts, primary))
                    + formatRowsSuffix(effectiveRootRows(facts, primary))
                    + formatMemorySuffix(facts.getRootMemoryMo() != null ? facts.getRootMemoryMo() : facts.getMemoryPeakMo());
            case PARALLEL_LOAD_DOMINATED -> {
                String load = "Chargement parallèle multithread — "
                        + formatDuration(firstPositive(facts.getGlobalMs(), durationOf(primary)))
                        + formatRowsSuffix(resolveRowCount(facts, primary));
                if (facts.hasSignificantSecondaryRender()) {
                    yield "Deux goulots successifs — " + load
                            + " puis rendering " + formatDuration(facts.getRenderingMs());
                }
                yield load + (facts.getThreadPoolSize() != null
                        ? " — " + facts.getThreadPoolSize() + " thread(s)"
                        : "");
            }
            case RULES_DOMINATED -> buildRulesPrimaryCause(facts, primary);
            case RENDERING_DOMINATED -> "Affichage de l'écran — "
                    + formatDuration(firstPositive(facts.getRenderingMs(), durationOf(primary)));
            case WS_DOMINATED -> "Webservice « " + wsLabel(facts) + " » — "
                    + formatDuration(firstPositive(durationOf(primary), facts.getRulesEngineMs(), facts.getGlobalMs()));
            case PRINT_DOMINATED -> "Impression / PDF — "
                    + formatDuration(firstPositive(durationOf(primary), facts.getRulesEngineMs()));
            case OPERATION_LOAD_DOMINATED -> "Chargement de l'opération — "
                    + formatDuration(durationOf(primary));
            case AUTOSTART_DOMINATED -> "Règles AutoStart — "
                    + formatDuration(firstPositive(durationOf(primary), facts.getRulesEngineMs()));
            case SAVE_DOMINATED -> "Sauvegarde / validation — "
                    + formatDuration(firstPositive(facts.getSavePersistMs(), durationOf(primary)));
            case WORKFLOW_DOMINATED -> "Transition workflow (Transit task) — "
                    + formatDuration(firstPositive(durationOf(primary), facts.getRulesEngineMs()));
            case COMPLEX -> "Plusieurs étapes contribuent significativement à la lenteur"
                    + complexBreakdownSuffix(facts, primary);
            case CONTAINER_DOMINATED, SINGLE_STEP -> primary != null && primary.getOperationName() != null
                    ? primary.getOperationName() + " — " + formatDuration(primary.getDurationMs())
                    : "Étape lente identifiée dans les logs";
        };
    }

    public String buildFullNarrative(WorksLatencyFacts facts,
                                     LatencyBottleneckAnalyzer.NarrativeKind kind,
                                     long maxDurationMs,
                                     LatencyTimelineStepDto primary) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildSummary(facts, kind, primary, maxDurationMs)).append("\n\n");

        List<String> whyLevels = buildWhyChain(facts, kind, primary, maxDurationMs);
        if (!whyLevels.isEmpty()) {
            sb.append("━━━ Pourquoi ? (en profondeur) ━━━\n");
            for (int i = 0; i < whyLevels.size(); i++) {
                sb.append("Pourquoi ").append(i + 1).append(" : ").append(whyLevels.get(i)).append('\n');
            }
            sb.append('\n');
        }

        sb.append("━━━ Déclencheur métier ━━━\n");
        appendTriggerSection(sb, facts);

        sb.append("\n━━━ Chaîne de cause (d'où vient la lenteur ?) ━━━\n");
        appendCauseChain(sb, facts, kind);

        LatencyQueryGroupDto dominant = facts.dominantRepeatedGroup();
        if (dominant != null) {
            sb.append("\n━━━ Source du problème (requêtes répétées) ━━━\n");
            appendRepeatedQuerySection(sb, facts, dominant);
        }

        if (facts.getDroolsRuleHits() != null && !facts.getDroolsRuleHits().isEmpty()) {
            sb.append("\n━━━ Règles Drools identifiées ━━━\n");
            appendDroolsRulesSection(sb, facts);
        }

        if (kind != LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED
                && (facts.isFastDownstream() || facts.getRenderingMs() != null)) {
            sb.append("\n━━━ Après la lenteur ━━━\n");
            appendAftermath(sb, facts);
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED
                && facts.getSavePersistMs() != null
                && facts.getRulesEngineMs() != null
                && facts.getSavePersistMs() * 5 < facts.getRulesEngineMs()) {
            sb.append("\n━━━ Après / autour des règles ━━━\n");
            sb.append("• SAVE / persistance : ").append(formatDuration(facts.getSavePersistMs()))
                    .append(" — secondaire face aux règles.\n");
            sb.append("→ La lenteur perçue vient du fire rules + effets de bord, pas du SAVE final.\n");
        }

        sb.append("\n━━━ Schéma simplifié ━━━\n");
        appendSchema(sb, facts, kind);

        sb.append("\n━━━ Réponse rapide ━━━\n");
        appendQaTable(sb, facts, kind, maxDurationMs);

        return sb.toString().trim();
    }

    /**
     * Chaîne « pourquoi » multi-niveaux : symptôme → cause réelle → mécanisme.
     * Ex. règles lentes → élimination SAVE/SQL → MAJ_Insert prouvé (ou limite explicite).
     */
    public List<String> buildWhyChain(WorksLatencyFacts facts,
                                      LatencyBottleneckAnalyzer.NarrativeKind kind,
                                      LatencyTimelineStepDto primary,
                                      long maxDurationMs) {
        List<String> levels = new ArrayList<>();
        if (facts == null || kind == null) {
            return levels;
        }
        long totalMs = resolveTotalMs(facts, primary, maxDurationMs);
        LatencyQueryGroupDto dominant = facts.dominantRepeatedGroup();

        switch (kind) {
            case RULES_DOMINATED -> appendRulesWhyLevels(levels, facts, primary, totalMs, dominant);
            case RENDERING_DOMINATED -> appendRenderingWhyLevels(levels, facts, primary, totalMs);
            case PARALLEL_LOAD_DOMINATED -> appendParallelLoadWhyLevels(levels, facts, primary, totalMs);
            case WORKFLOW_DOMINATED -> appendWorkflowWhyLevels(levels, facts, primary, totalMs);
            case OPERATION_LOAD_DOMINATED -> appendOperationLoadWhyLevels(levels, facts, primary, totalMs);
            case COMPLEX -> appendComplexWhyLevels(levels, facts, primary, totalMs);
            case SQL_DOMINATED -> {
                long sqlMs = effectiveRootMs(facts, primary);
                int rows = effectiveRootRows(facts, primary);
                levels.add("La requête SQL initiale (prepareSearchByRoot / Query Select) a pris "
                        + formatDuration(sqlMs)
                        + " — c'est l'étape dominante de l'opération ("
                        + formatDuration(totalMs) + " au total).");
                if (rows <= 1 && sqlMs >= 2000) {
                    levels.add("Parce que la requête est lente alors qu'elle ne renvoie quasiment aucune ligne ("
                            + formatRows(rows)
                            + ") : probable problème d'indexation ou de sélectivité du filtre.");
                } else if (facts.getSqlJoinCount() >= 3) {
                    levels.add("Parce que la requête est complexe ("
                            + facts.getSqlJoinCount()
                            + " jointures détectées) sur le filtre « "
                            + orNa(firstNonBlank(facts.getFilterCode(), facts.getPreRulesFilterCode()))
                            + " »"
                            + (facts.getClassName() != null
                            ? ", objet " + cleanObject(facts.getClassName())
                            : "")
                            + ".");
                } else {
                    levels.add("Parce que la recherche racine sur « "
                            + orNa(firstNonBlank(facts.getFilterCode(), facts.getPreRulesFilterCode()))
                            + " » coûte "
                            + formatDuration(sqlMs)
                            + " pour " + formatRows(rows) + ".");
                }
                if (facts.getRootMemoryMo() != null && facts.getRootMemoryMo() >= 1500) {
                    levels.add("Parce que la mémoire monte à ~"
                            + formatMemory(facts.getRootMemoryMo())
                            + " pendant la requête — pression heap / GC possible.");
                }
            }
            case SAVE_DOMINATED -> {
                levels.add("La persistance / validation (SAVE) a pris "
                        + formatDuration(firstPositive(facts.getSavePersistMs(), durationOf(primary), totalMs))
                        + (facts.getSavePersistKey() != null
                        ? " pour la clé « " + facts.getSavePersistKey() + " »"
                        : "")
                        + ".");
                if (facts.getInsertRelChildCount() != null || facts.getInsertRelRootCount() != null) {
                    levels.add("Parce que des insertArrayRel massifs ont été détectés"
                            + (facts.getInsertRelChildCount() != null
                            ? " (child : " + facts.getInsertRelChildCount() + ")"
                            : "")
                            + (facts.getInsertRelRootCount() != null
                            ? " (root : " + facts.getInsertRelRootCount() + ")"
                            : "")
                            + ".");
                } else if (facts.getMajInsertTotal() > 0) {
                    levels.add("Parce que ~" + facts.getMajInsertTotal()
                            + " MAJ_Insert accompagnent la sauvegarde.");
                } else {
                    levels.add("Parce que la phase validate/saveOperations/persist domine le chronomètre"
                            + " (peu de search dominant dans la fenêtre).");
                }
            }
            case WS_DOMINATED -> {
                long wsMs = firstPositive(durationOf(primary), facts.getRulesEngineMs(), totalMs);
                if (facts.getRootQueryMs() != null && facts.getRootQueryMs() >= 500) {
                    levels.add("Le traitement du webservice « " + wsLabel(facts) + " » dure "
                            + formatDuration(wsMs) + ", dont au moins "
                            + formatDuration(facts.getRootQueryMs())
                            + " sont passés dans une recherche SQL interne"
                            + (facts.getFilterCode() != null
                            ? " (filtre « " + facts.getFilterCode() + " »)"
                            : "")
                            + ".");
                    levels.add("Le temps réseau / serveur distant n'est pas chronométré séparément dans ces logs WORKS"
                            + " — il faut le mesurer à part pour expliquer le reste.");
                } else if (dominant != null) {
                    levels.add("L'appel webservice « " + wsLabel(facts) + " » a pris "
                            + formatDuration(wsMs) + ".");
                    levels.add("Parce que des requêtes répétées (« "
                            + friendlyDroolsName(dominant.getFilterCode()) + " », "
                            + dominant.getCount() + "×) saturent le traitement du WS.");
                } else {
                    levels.add("L'appel webservice « " + wsLabel(facts) + " » a pris "
                            + formatDuration(wsMs) + ".");
                    levels.add("Parce que le temps est passé dans le conteneur WS (SQL interne et/ou attente du service distant)"
                            + " — les logs WORKS ne séparent pas toujours les deux.");
                }
            }
            case PRINT_DOMINATED -> {
                levels.add("L'impression / génération PDF a pris " + formatDuration(totalMs)
                        + (formatActionDisplay(facts) != null
                        ? " (action « " + formatActionDisplay(facts) + " »)"
                        : "")
                        + ".");
                if (facts.getRulesEngineMs() != null && facts.getRulesEngineMs() >= 1000) {
                    levels.add("Parce que les règles liées à Imprimer/PDF ont déjà coûté "
                            + formatDuration(facts.getRulesEngineMs())
                            + " avant/pendant la génération du document.");
                } else {
                    levels.add("Parce que la phase d'impression / génération document concentre le temps"
                            + " — les logs ne détaillent pas toujours printArchive ni la suite de la chaîne d'impression.");
                }
                if (facts.getMajInsertTotal() > 0 || facts.getSavePersistMs() != null) {
                    levels.add("Parce que des écritures / SAVE accompagnent souvent l'impression"
                            + (facts.getMajInsertTotal() > 0
                            ? " (~" + facts.getMajInsertTotal() + " MAJ_Insert)"
                            : "")
                            + (facts.getSavePersistMs() != null
                            ? " (SAVE " + formatDuration(facts.getSavePersistMs()) + ")"
                            : "")
                            + ".");
                }
            }
            case AUTOSTART_DOMINATED -> {
                levels.add("Un batch AutoStart (« "
                        + orNa(facts.getTaskName() != null ? facts.getTaskName() : facts.getProcessName())
                        + " ») a pris " + formatDuration(totalMs)
                        + " sans action utilisateur directe.");
                if (facts.getRootQueryMs() != null && facts.getRootQueryMs() >= 500) {
                    levels.add("Parce qu'une recherche/SQL dans ce batch a coûté "
                            + formatDuration(facts.getRootQueryMs()) + ".");
                } else if (facts.getMajInsertTotal() > 0 || facts.getSavePersistMs() != null) {
                    levels.add("Parce que le batch enchaîne des écritures / SAVE coûteuses"
                            + (facts.getSavePersistMs() != null
                            ? " (SAVE " + formatDuration(facts.getSavePersistMs()) + ")"
                            : "")
                            + ".");
                } else {
                    levels.add("Parce que les règles AutoStart (ex. TRACE_SESSIONS) exécutent un traitement"
                            + " périodique dense — le détail interne est parfois peu journalisé.");
                }
            }
            default -> {
                levels.add("L'étape dominante « "
                        + (primary != null && primary.getOperationName() != null
                        ? primary.getOperationName()
                        : "opération")
                        + " » a pris " + formatDuration(totalMs) + ".");
                if (facts.getRootQueryMs() != null && facts.getRootQueryMs() * 2 >= totalMs) {
                    levels.add("Parce qu'une requête SQL proche coûte déjà "
                            + formatDuration(facts.getRootQueryMs()) + ".");
                } else if (facts.getRulesEngineMs() != null && facts.getRulesEngineMs() * 2 >= totalMs) {
                    levels.add("Parce que les règles proches coûtent "
                            + formatDuration(facts.getRulesEngineMs()) + ".");
                } else {
                    levels.add("Parce que plusieurs sous-étapes se cumulent sans qu'une seule famille"
                            + " (SQL / règles / SAVE) explique tout à elle seule.");
                }
            }
        }
        return levels;
    }

    /**
     * Pourquoi règles — rigueur preuves uniquement :
     * 1) localisation fire rules, 2) élimination des autres étapes mesurées,
     * 3) mécanisme interne UNIQUEMENT si prouvé (MAJ_Insert / SQL rejoué / Rule_*),
     * sinon limite explicite (pas d'hypothèse matchall/volume non démontrée).
     */
    private void appendRulesWhyLevels(List<String> levels,
                                      WorksLatencyFacts facts,
                                      LatencyTimelineStepDto primary,
                                      long totalMs,
                                      LatencyQueryGroupDto dominant) {
        String action = formatActionDisplay(facts);
        long rulesMs = firstPositive(facts.getRulesEngineMs(), durationOf(primary), totalMs);
        long opMs = firstPositive(totalMs, rulesMs);

        // ── Pourquoi 1 : localisation prouvée ──
        StringBuilder why1 = new StringBuilder();
        if (action != null) {
            why1.append("L'action « ").append(action).append(" » dure environ ")
                    .append(formatDuration(opMs))
                    .append(" car l'exécution des règles métier (fire rules / running rules) dure elle-même environ ")
                    .append(formatDuration(rulesMs)).append('.');
        } else {
            why1.append("L'exécution des règles métier (fire rules / running rules) dure environ ")
                    .append(formatDuration(rulesMs))
                    .append(" et concentre la quasi-totalité du temps de l'opération.");
        }
        levels.add(why1.toString());

        // ── Pourquoi 2 : élimination des autres causes mesurées ──
        List<String> others = new ArrayList<>();
        if (facts.getSavePersistMs() != null && facts.getSavePersistMs() > 0
                && facts.getSavePersistMs() * 3 < rulesMs) {
            others.add("SAVE ≈ " + formatDuration(facts.getSavePersistMs()));
        }
        long relMs = 0L;
        if (facts.getInsertRelChildMs() != null) {
            relMs += facts.getInsertRelChildMs();
        }
        if (facts.getInsertRelRootMs() != null) {
            relMs += facts.getInsertRelRootMs();
        }
        if (relMs > 0 && relMs * 3 < rulesMs) {
            others.add("insertArrayRel ≈ " + formatDuration(relMs));
        }
        Long sqlMsObj = firstPositive(facts.getRootQueryMs(), facts.getPreRulesSearchMs());
        long sqlMs = sqlMsObj != null ? sqlMsObj : 0L;
        if (sqlMs > 0 && sqlMs * 3 < rulesMs) {
            others.add("recherche SQL amont ≈ " + formatDuration(sqlMs)
                    + (facts.getPreRulesRowCount() != null
                    ? " (" + formatRows(facts.getPreRulesRowCount()) + ")"
                    : ""));
        }
        if (!others.isEmpty()) {
            levels.add("Les autres traitements mesurés ne sont pas responsables de cette durée : "
                    + String.join(" ; ", others)
                    + ". La durée est concentrée dans la phase fire rules"
                    + (opMs > 0 && rulesMs * 100 / Math.max(opMs, 1) >= 80
                    ? " (~" + (rulesMs * 100 / opMs) + " % du total)"
                    : "")
                    + ".");
        } else {
            levels.add("Dans la fenêtre analysée, aucune autre étape chronométrée (SAVE, insertArrayRel, SQL)"
                    + " n'approche la durée des fire rules — le goulot est bien cette phase.");
        }

        // ── Pourquoi 3 : observations internes (sans sur-affirmer la cause chronométrée) ──
        boolean strongInternalProof = false;
        if (facts.getMajInsertTotal() > 0) {
            StringBuilder maj = new StringBuilder();
            maj.append("À l'intérieur / autour des règles, les logs montrent ~")
                    .append(facts.getMajInsertTotal()).append(" écriture(s) MAJ_Insert");
            if (facts.getMajInsertDominantType() != null) {
                maj.append(" (dominant : MAJ_Insert_").append(facts.getMajInsertDominantType())
                        .append(" ×").append(facts.getMajInsertDominantCount()).append(')');
            }
            maj.append(" — écritures métier observées pendant cette phase.");
            maj.append(" En revanche, sans took individuel sur ces MAJ_Insert, on ne peut pas affirmer")
                    .append(" qu'elles expliquent à elles seules les ")
                    .append(formatDuration(rulesMs))
                    .append(" (SQL, logique métier, appels ou autres règles restent possibles).");
            levels.add(maj.toString());
            // Volume massif = piste forte de charge, pas une preuve chronométrée du goulot.
            if (facts.getMajInsertTotal() >= 50) {
                strongInternalProof = true;
            }
        }
        if (dominant != null && dominant.getCount() >= 2
                && dominant.getTotalMs() >= 2000) {
            levels.add("Les logs montrent aussi le contrôle « "
                    + friendlyDroolsName(dominant.getFilterCode())
                    + " » rejoué " + dominant.getCount() + "× ("
                    + formatDuration(dominant.getTotalMs())
                    + " cumulées) — contribution SQL répétée prouvée.");
            strongInternalProof = true;
        } else if (facts.dominantDroolsRule() != null) {
            levels.add("Nom de règle Drools visible dans les logs : « "
                    + friendlyDroolsName(facts.dominantDroolsRule())
                    + " »"
                    + (facts.getDroolsRuleHits() != null && !facts.getDroolsRuleHits().isEmpty()
                    ? " (" + facts.getDroolsRuleHits().get(0).getCount() + "×)"
                    : "")
                    + " — piste à investiguer, sans chronométrage individuel prouvé.");
        }

        if (!strongInternalProof) {
            levels.add("Les logs fournis ne détaillent pas l'intérieur de START fire rules → END fire rules :"
                    + " on localise la lenteur aux règles, mais on ne peut pas identifier la règle précise,"
                    + " une boucle, des SQL internes ou un autre traitement responsable des "
                    + formatDuration(rulesMs) + "."
                    + " Pour la cause exacte, il faut les traces entre START et END fire rules"
                    + " (Rule_*, took par règle, Query, etc.).");
        } else if (facts.getPreRulesRowCount() != null && facts.getPreRulesRowCount() > 10) {
            // Contexte amont = fait mesuré, mais PAS « donc matchall parcourt N objets ».
            levels.add("Contexte amont mesuré (hors preuve du parcours interne) : "
                    + formatRows(facts.getPreRulesRowCount())
                    + (facts.getPreRulesFilterCode() != null
                    ? " via « " + facts.getPreRulesFilterCode() + " »"
                    : "")
                    + (facts.getPreRulesSearchMs() != null
                    ? " en " + formatDuration(facts.getPreRulesSearchMs())
                    : "")
                    + " — utile pour investiguer, sans prouver à lui seul le coût du fire rules.");
        }
    }

    private void appendOperationLoadWhyLevels(List<String> levels,
                                              WorksLatencyFacts facts,
                                              LatencyTimelineStepDto primary,
                                              long totalMs) {
        long loadMs = firstPositive(durationOf(primary), totalMs);
        String op = primary != null && primary.getOperationName() != null
                ? primary.getOperationName() : "loadOperationById / loadOperationContext";
        levels.add("Le temps est entièrement consommé lors du chargement du contexte de l'opération (« "
                + op + " », " + formatDuration(loadMs) + ").");
        String lower = op.toLowerCase(Locale.ROOT);
        if (lower.contains("loadoperationcontext") || lower.contains("loadoperationbyid")) {
            levels.add("Les logs montrent loadOperationContext / loadOperationById avec la même durée : "
                    + "loadOperationContext passe l'intégralité de son temps dans loadOperationById "
                    + "(lecture des données, accès BD ou chargement des objets associés). "
                    + "Aucune preuve dans ces logs ne montre que la génération PDF ou la chaîne d'impression "
                    + "est responsable de cette durée.");
        } else {
            levels.add("Le goulot mesuré est le chargement de données (« " + op + " »), "
                    + "pas la génération PDF ni la chaîne d'impression — même si le process s'appelle Imprimer.");
        }
    }

    private void appendComplexWhyLevels(List<String> levels,
                                        WorksLatencyFacts facts,
                                        LatencyTimelineStepDto primary,
                                        long totalMs) {
        List<String> parts = complexContributorParts(facts, primary);
        levels.add("Plusieurs étapes contribuent significativement à la lenteur"
                + (totalMs > 0 ? " (parcours ≈ " + formatDuration(totalMs) + ")" : "")
                + " — aucune n'en représente à elle seule la majorité.");
        if (!parts.isEmpty()) {
            levels.add("Répartition mesurée : " + String.join(" ; ", parts) + ".");
        } else {
            levels.add("Parce que plusieurs sous-étapes se cumulent sans qu'une seule famille"
                    + " (SQL / règles / rendu / SAVE) explique tout à elle seule.");
        }
    }

    private String complexBreakdownSuffix(WorksLatencyFacts facts, LatencyTimelineStepDto primary) {
        List<String> parts = complexContributorParts(facts, primary);
        if (parts.isEmpty()) {
            return "";
        }
        return " — " + String.join(", ", parts);
    }

    private List<String> complexContributorParts(WorksLatencyFacts facts, LatencyTimelineStepDto primary) {
        List<String> parts = new ArrayList<>();
        if (facts.getRootQueryMs() != null && facts.getRootQueryMs() >= 2000) {
            parts.add("SQL " + formatDuration(facts.getRootQueryMs()));
        }
        long loadMs = facts.getGlobalMs() != null ? facts.getGlobalMs() : 0L;
        long rootMs = facts.getRootQueryMs() != null ? facts.getRootQueryMs() : 0L;
        if (loadMs >= 2000 && (rootMs == 0 || loadMs > rootMs * 1.2)) {
            parts.add("chargement " + formatDuration(loadMs));
        }
        if (facts.getRulesEngineMs() != null && facts.getRulesEngineMs() >= 2000) {
            parts.add("règles " + formatDuration(facts.getRulesEngineMs()));
        }
        if (facts.getRenderingMs() != null && facts.getRenderingMs() >= 2000) {
            parts.add("rendering " + formatDuration(facts.getRenderingMs()));
        }
        if (facts.getSavePersistMs() != null && facts.getSavePersistMs() >= 2000) {
            parts.add("SAVE " + formatDuration(facts.getSavePersistMs()));
        }
        if (parts.isEmpty() && primary != null && primary.getDurationMs() != null) {
            parts.add((primary.getOperationName() != null ? primary.getOperationName() : "étape")
                    + " " + formatDuration(primary.getDurationMs()));
        }
        return parts;
    }

    /**
     * Pourquoi Transit / workflow — goulot mesuré uniquement.
     * Ne jamais inventer file d'attente, verrou, persist, doAction ou appel externe
     * sans traces explicites dans les faits / fenêtre.
     */
    private void appendWorkflowWhyLevels(List<String> levels,
                                         WorksLatencyFacts facts,
                                         LatencyTimelineStepDto primary,
                                         long totalMs) {
        long transitMs = firstPositive(durationOf(primary), totalMs);
        String action = formatActionDisplay(facts);
        String opName = primary != null && primary.getOperationName() != null
                ? primary.getOperationName() : "Transit task";

        levels.add("La lenteur est entièrement concentrée dans « " + opName + " »"
                + (action != null ? " de la transition « " + action + " »" : "")
                + " (" + formatDuration(transitMs) + ").");

        boolean internalProven = false;
        if (facts.getRulesEngineMs() != null && facts.getRulesEngineMs() >= 1000
                && facts.getRulesEngineMs() * 10 >= transitMs * 7) {
            levels.add("Le Transit task n'est pas lui-même la cause : il englobe principalement"
                    + " l'exécution des règles métier ("
                    + formatDuration(facts.getRulesEngineMs())
                    + ", ~" + (facts.getRulesEngineMs() * 100 / Math.max(transitMs, 1))
                    + " % du Transit) — contribution prouvée.");
            internalProven = true;
        } else if (facts.getRulesEngineMs() != null && facts.getRulesEngineMs() >= 1000
                && facts.getRulesEngineMs() * 2 >= transitMs) {
            levels.add("À l'intérieur / autour de Transit, les logs chronomètrent aussi les règles métier ("
                    + formatDuration(facts.getRulesEngineMs())
                    + ") — contribution prouvée, pas une hypothèse.");
            internalProven = true;
        }
        if (facts.getMajInsertTotal() > 0) {
            levels.add("Effets de bord MAJ_Insert observés (~" + facts.getMajInsertTotal()
                    + ") pendant la fenêtre — piste d'écriture métier prouvée.");
            internalProven = true;
        }
        if (facts.getSavePersistMs() != null && facts.getSavePersistMs() >= 1000
                && facts.getSavePersistMs() * 3 < transitMs) {
            levels.add("Une persistance SAVE est mesurée ("
                    + formatDuration(facts.getSavePersistMs())
                    + ") mais reste secondaire face à Transit.");
            internalProven = true;
        }
        if (!internalProven) {
            levels.add("Les logs montrent que le moteur de workflow est resté "
                    + formatDuration(transitMs)
                    + " dans cette étape Transit, mais ils ne permettent pas d'identifier"
                    + " ce qui est exécuté à l'intérieur (règles, SQL, WS, traitement externe, etc.)."
                    + " File d'attente, verrou ou appel externe ne sont pas démontrés par cette seule ligne.");
        }
    }

    /**
     * Pourquoi chargement parallèle — preuves uniquement :
     * config multithread + volume ; pas d'hypothèse loadListChilds / lots « meilleurs »
     * sans traces ; mention du second goulot rendering s'il est mesuré.
     */
    private void appendParallelLoadWhyLevels(List<String> levels,
                                             WorksLatencyFacts facts,
                                             LatencyTimelineStepDto primary,
                                             long totalMs) {
        int rows = resolveRowCount(facts, primary);
        long loadMs = firstPositive(facts.getGlobalMs(), durationOf(primary), totalMs);

        StringBuilder why1 = new StringBuilder();
        why1.append("Le système charge ").append(formatRows(rows)).append(" en mode multithread");
        if (facts.getThreadPoolSize() != null) {
            why1.append(" (").append(facts.getThreadPoolSize()).append(" thread(s)");
            if (facts.getPartitionSize() != null) {
                why1.append(", partitions de ").append(facts.getPartitionSize());
            }
            why1.append(')');
        } else if (facts.getPartitionSize() != null) {
            why1.append(" (partitions de ").append(facts.getPartitionSize()).append(')');
        }
        why1.append(". Cette étape dure ").append(formatDuration(loadMs))
                .append(" et constitue le principal temps de traitement.");
        levels.add(why1.toString());

        StringBuilder why2 = new StringBuilder();
        why2.append("Les logs montrent que le volume traité est très important (")
                .append(formatRows(rows))
                .append("). La majeure partie du temps est consommée pendant le chargement parallèle");
        if (facts.getRulesEngineMs() != null && facts.getRulesEngineMs() * 20 < loadMs) {
            why2.append(", avant même les règles métier (")
                    .append(formatMsExact(facts.getRulesEngineMs())).append(')');
        }
        why2.append('.');
        if (facts.hasSignificantSecondaryRender()) {
            why2.append(" Une fois les données chargées, rendering result ajoute encore ")
                    .append(formatDuration(facts.getRenderingMs()))
                    .append(" — second goulot d'affichage, distinct du chargement.");
        } else {
            why2.append(" Les logs de cette fenêtre ne détaillent pas les requêtes internes"
                    + " de chaque thread (loadListChilds, SQL par lot, etc.) :"
                    + " le volume et la durée globale du multithread sont les faits démontrés.");
        }
        levels.add(why2.toString());

        if (facts.getLoadChildSampleCount() > 0 && facts.getLoadChildMaxMs() != null
                && facts.getLoadChildMaxMs() >= 1000) {
            levels.add("Preuve interne supplémentaire : loadListChilds atteint jusqu'à "
                    + formatDuration(facts.getLoadChildMaxMs())
                    + (facts.getLoadChildAvgMs() != null
                    ? " (moy. " + formatDuration(facts.getLoadChildAvgMs()) + ")"
                    : "")
                    + " — " + facts.getLoadChildSampleCount() + " mesure(s) dans les logs.");
        } else if (facts.getRootQueryMs() != null && facts.getRootQueryMs() >= 1000
                && facts.getRootQueryMs() * 3 < loadMs) {
            levels.add("En amont, la requête SQL initiale a déjà coûté "
                    + formatDuration(facts.getRootQueryMs())
                    + " pour " + formatRows(facts.getRootRowCount() != null
                    ? facts.getRootRowCount() : rows)
                    + " — elle prépare le volume ensuite chargé en multithread.");
        }
    }

    /**
     * Pourquoi rendering — preuves uniquement :
     * 1) localisation rendering result + amont négligeable,
     * 2) concentration dans cette phase (pas colonnes/JS non journalisés),
     * 3) volume affiché comme corrélation si mesuré.
     */
    private void appendRenderingWhyLevels(List<String> levels,
                                          WorksLatencyFacts facts,
                                          LatencyTimelineStepDto primary,
                                          long totalMs) {
        long renderMs = firstPositive(facts.getRenderingMs(), durationOf(primary), totalMs);

        List<String> upstream = new ArrayList<>();
        if (facts.getRulesEngineMs() != null) {
            upstream.add("running rules = " + formatMsExact(facts.getRulesEngineMs()));
        }
        if (facts.getSavePersistMs() != null) {
            upstream.add("sauvegarde = " + formatMsExact(facts.getSavePersistMs()));
        }
        if (facts.getAggregateMs() != null) {
            upstream.add("aggregate = " + formatMsExact(facts.getAggregateMs()));
        }
        if (facts.getRootQueryMs() != null && facts.getRootQueryMs() * 5 < renderMs) {
            upstream.add("SQL amont = " + formatMsExact(facts.getRootQueryMs()));
        }

        StringBuilder why1 = new StringBuilder();
        why1.append("L'étape rendering result dure ").append(formatDuration(renderMs));
        if (!upstream.isEmpty()) {
            why1.append(", alors que les traitements précédents sont quasi instantanés (")
                    .append(String.join(" ; ", upstream)).append(")");
        } else {
            why1.append(", alors que les traitements précédents (règles, sauvegarde, agrégation)"
                    + " sont négligeables dans la fenêtre");
        }
        why1.append(". Toute la durée est concentrée dans cette phase.");
        levels.add(why1.toString());

        levels.add("Parce que les logs localisent la lenteur dans la génération ou l'affichage du résultat"
                + " (rendering result), et non dans les règles métier ni dans les traitements de préparation."
                + " Ils ne détaillent pas le mécanisme interne de cette phase.");

        Integer volume = facts.getElementCount() != null ? facts.getElementCount() : facts.getGlobalRowCount();
        if (volume != null && volume > 100) {
            levels.add("Le volume affiché (" + formatRows(volume)
                    + ") est un facteur fortement corrélé à cette durée : plus le jeu à rendre est large,"
                    + " plus rendering result tend à croître — corrélation solide, pas une preuve du détail interne.");
        }
    }

    private void appendTriggerSection(StringBuilder sb, WorksLatencyFacts facts) {
        sb.append("• Processus : ").append(orNa(rawProcess(facts.getProcessName()))).append('\n');
        String filterDisplay = firstNonBlank(facts.getPreRulesFilterCode(), facts.getFilterCode());
        sb.append("• Filtre : ").append(orNa(filterDisplay)).append('\n');
        String actionDisplay = formatActionDisplay(facts);
        sb.append("• Action : ").append(orNa(actionDisplay)).append('\n');
        if (facts.getTaskName() != null && !facts.getTaskName().isBlank()) {
            sb.append("• Tâche : ").append(facts.getTaskName().trim()).append('\n');
        }
        sb.append("• Nom de l'écran : ").append(orNa(facts.getScreenName())).append('\n');
        sb.append("• Utilisateur : ").append(orNa(facts.getUserName())).append('\n');
        sb.append("• Session : ").append(orNa(facts.getSessionId())).append('\n');
        sb.append("• UUID : ").append(orNa(facts.getUuid())).append('\n');
        if (facts.getClassName() != null) {
            sb.append("• Objet métier : ").append(cleanObject(facts.getClassName())).append('\n');
        }
        if (facts.getOperationStart() != null) {
            sb.append("• Début : ").append(facts.getOperationStart().format(TIME_FMT));
            if (facts.getOperationEnd() != null) {
                sb.append(" → fin : ").append(facts.getOperationEnd().format(TIME_FMT));
            }
            sb.append('\n');
        }
    }

    private String formatActionDisplay(WorksLatencyFacts facts) {
        String business = cleanActionLabel(facts.getBusinessActionLabel());
        String action = cleanActionLabel(facts.getActionName());
        if (business != null && !business.isBlank()) {
            if (action != null && !action.isBlank()
                    && !business.equalsIgnoreCase(action)) {
                return business.trim() + " (" + action.trim() + ")";
            }
            return business.trim();
        }
        return action;
    }

    /** Retire les balises icône ZK (#icon:…#) pour un libellé métier lisible. */
    private String cleanActionLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replaceAll("(?i)#icon:[^#]*#", "").trim();
        return cleaned.isEmpty() ? raw.trim() : cleaned;
    }

    private String wsLabel(WorksLatencyFacts facts) {
        if (facts.getTaskName() != null && facts.getTaskName().toUpperCase(Locale.ROOT).startsWith("WS_")) {
            return facts.getTaskName().trim();
        }
        String p = rawProcess(facts.getProcessName());
        if (p != null && p.toLowerCase(Locale.ROOT).contains("webservice")) {
            return facts.getTaskName() != null ? facts.getTaskName() : p;
        }
        if (facts.getActionName() != null && facts.getActionName().toUpperCase(Locale.ROOT).startsWith("WS_")) {
            return facts.getActionName();
        }
        return orNa(firstNonBlank(facts.getTaskName(), p));
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private String orNa(String value) {
        return value != null && !value.isBlank() ? value.trim() : "n/a";
    }

    private String rawProcess(String process) {
        if (process == null || process.isBlank()) {
            return null;
        }
        String p = process.trim();
        if (p.startsWith("process.")) {
            p = p.substring("process.".length());
        }
        return p;
    }

    private String cleanObject(String className) {
        if (className == null || className.isBlank()) {
            return "n/a";
        }
        return dedupClassName(className).replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    /** Dédouble le nom d'entité WORKS (ex. "ManifesteManifeste" → "Manifeste"). */
    private String dedupClassName(String className) {
        String s = className.trim();
        int len = s.length();
        if (len % 2 == 0) {
            String first = s.substring(0, len / 2);
            if (first.equals(s.substring(len / 2))) {
                return first;
            }
        }
        return s;
    }

    private void appendRepeatedQuerySection(StringBuilder sb, WorksLatencyFacts facts,
                                            LatencyQueryGroupDto dominant) {
        sb.append("En analysant les logs AVANT le temps élevé, le même contrôle métier rejoue la même requête SQL :\n");
        sb.append("• ").append(friendlyDroolsName(dominant.getFilterCode())).append(" : ")
                .append(dominant.getCount()).append(" exécutions — ")
                .append(formatDuration(dominant.getTotalMs())).append(" cumulées (jusqu'à ")
                .append(formatDuration(dominant.getMaxMs())).append(" pour une seule).\n");
        int shown = 0;
        for (LatencyQueryGroupDto group : facts.getRepeatedQueryGroups()) {
            if (group == dominant || group.getCount() < 2) {
                continue;
            }
            sb.append("• ").append(friendlyDroolsName(group.getFilterCode())).append(" : ")
                    .append(group.getCount()).append(" exécutions — ")
                    .append(formatDuration(group.getTotalMs())).append(" cumulées.\n");
            if (++shown >= 3) {
                break;
            }
        }
        if (facts.getSqlJoinCount() >= 3) {
            sb.append("→ Chaque requête fait ").append(facts.getSqlJoinCount())
                    .append(" jointures SQL ; rejouée des dizaines de fois, elle sature le traitement.\n");
        } else {
            sb.append("→ Cette requête rejouée des dizaines de fois est l'origine réelle de la lenteur.\n");
        }
    }

    private void appendDroolsRulesSection(StringBuilder sb, WorksLatencyFacts facts) {
        sb.append("Règles Drools présentes dans la fenêtre d'analyse (noms tels que dans les logs) :\n");
        int shown = 0;
        for (LatencyQueryGroupDto hit : facts.getDroolsRuleHits()) {
            sb.append("• ").append(friendlyDroolsName(hit.getFilterCode()))
                    .append(" — ").append(hit.getCount()).append(" occurrence(s)\n");
            if (++shown >= 8) {
                int rest = facts.getDroolsRuleHits().size() - shown;
                if (rest > 0) {
                    sb.append("• … et ").append(rest).append(" autre(s) règle(s)\n");
                }
                break;
            }
        }
        String dominant = facts.dominantDroolsRule();
        if (dominant != null) {
            sb.append("→ Règle dominante à investiguer : « ")
                    .append(friendlyDroolsName(dominant)).append(" ».\n");
        }
    }

    /** Affichage lisible : Rule_xxx_0.java → Rule_xxx_0 (conserve le nom technique). */
    private String friendlyDroolsName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "n/a";
        }
        String s = raw.trim();
        if (s.toLowerCase(Locale.ROOT).endsWith(".java")) {
            return s.substring(0, s.length() - 5);
        }
        return s;
    }

    private void appendCauseChain(StringBuilder sb, WorksLatencyFacts facts,
                                  LatencyBottleneckAnalyzer.NarrativeKind kind) {
        int step = 1;

        boolean hideRootForRules = kind == LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED
                && facts.dominantRepeatedGroup() != null;

        if (!hideRootForRules && facts.getRootQueryMs() != null && facts.getRootQueryMs() >= 500) {
            sb.append(step++).append(". Requête principale (identification des données)\n");
            sb.append("   Durée : ").append(formatDuration(facts.getRootQueryMs()));
            sb.append(" — ").append(formatRows(resolveRowCount(facts))).append(" trouvée(s).\n");
            if (facts.getSqlJoinCount() >= 3) {
                sb.append("   → Requête SQL complexe (").append(facts.getSqlJoinCount())
                        .append(" jointures détectées dans les logs).\n");
            } else {
                sb.append("   → Le système cherche d'abord quels enregistrements afficher.\n");
            }
            if (kind == LatencyBottleneckAnalyzer.NarrativeKind.SQL_DOMINATED) {
                sb.append("   → C'est l'étape la plus lente : le reste du traitement est rapide.\n");
            }
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.PARALLEL_LOAD_DOMINATED
                || (facts.isMultithreaded() && resolveRowCount(facts) > 10)) {
            sb.append(step++).append(". Chargement parallèle des détails (multithreading)\n");
            sb.append("   Durée globale : ").append(formatDuration(facts.getGlobalMs()));
            if (facts.getRootQueryMs() != null && facts.getGlobalMs() != null) {
                sb.append(" (dont ~").append(formatDuration(facts.parallelPhaseMs()))
                        .append(" après la requête principale).\n");
            } else {
                sb.append('\n');
            }
            if (facts.getThreadPoolSize() != null || facts.getPartitionSize() != null) {
                sb.append("   → Config mesurée :");
                if (facts.getThreadPoolSize() != null) {
                    sb.append(' ').append(facts.getThreadPoolSize()).append(" thread(s)");
                }
                if (facts.getPartitionSize() != null) {
                    sb.append(", partitions de ").append(facts.getPartitionSize());
                }
                sb.append(".\n");
            }
            if (facts.getLoadChildSampleCount() > 0) {
                sb.append("   → loadListChilds mesuré entre ")
                        .append(formatDuration(facts.getLoadChildMinMs()))
                        .append(" et ").append(formatDuration(facts.getLoadChildMaxMs()));
                if (facts.getLoadChildAvgMs() != null) {
                    sb.append(" (moy. ").append(formatDuration(facts.getLoadChildAvgMs())).append(")");
                }
                sb.append(" — ").append(facts.getLoadChildSampleCount())
                        .append(" mesure(s) dans les logs.\n");
            } else {
                sb.append("   → Détail interne des threads (loadListChilds / SQL par lot)"
                        + " non journalisé dans cette fenêtre.\n");
            }
            if (kind == LatencyBottleneckAnalyzer.NarrativeKind.PARALLEL_LOAD_DOMINATED) {
                sb.append("   → Principal temps de traitement");
                if (facts.hasSignificantSecondaryRender()) {
                    sb.append(" ; rendering result ajoute encore ")
                            .append(formatDuration(facts.getRenderingMs()));
                }
                sb.append(".\n");
            }
        }

        if (facts.getMemoryPeakMo() != null && facts.getMemoryPeakMo() >= 2000) {
            sb.append(step++).append(". Pression mémoire\n");
            sb.append("   → Jusqu'à ~").append(formatMemory(facts.getMemoryPeakMo()))
                    .append(" pendant le chargement.\n");
            sb.append("   → Peut ralentir encore (garbage collector, manque de RAM).\n");
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED
                || kind == LatencyBottleneckAnalyzer.NarrativeKind.PRINT_DOMINATED) {
            appendRulesCauseChain(sb, facts, step);
            if (kind == LatencyBottleneckAnalyzer.NarrativeKind.PRINT_DOMINATED) {
                sb.append(step + 3).append(". Impression / génération document\n");
                sb.append("   → L'action Imprimer/PDF s'appuie sur les règles"
                        + " (+ printArchive / génération document s'ils sont chronométrés).\n");
            }
            return;
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.WS_DOMINATED) {
            sb.append(step++).append(". Appel webservice\n");
            sb.append("   → Service : ").append(wsLabel(facts)).append('\n');
            sb.append("   → Durée dominante : ")
                    .append(formatDuration(firstPositive(facts.getRulesEngineMs(), facts.getGlobalMs())))
                    .append('\n');
            if (facts.getRootQueryMs() != null) {
                sb.append("   → Inclut souvent une recherche SQL (")
                        .append(formatDuration(facts.getRootQueryMs())).append(").\n");
            }
            sb.append("   → Cause typique : charge du WS + SQL derrière, pas un écran utilisateur.\n");
            return;
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.AUTOSTART_DOMINATED) {
            sb.append(step++).append(". Règles AutoStart (batch silencieux)\n");
            sb.append("   → Processus : ").append(orNa(rawProcess(facts.getProcessName()))).append('\n');
            sb.append("   → Tâche : ").append(orNa(facts.getTaskName())).append('\n');
            sb.append("   → Durée : ").append(formatDuration(facts.getRulesEngineMs())).append('\n');
            sb.append("   → Pas d'action utilisateur directe : règles déclenchées automatiquement.\n");
            return;
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.SAVE_DOMINATED) {
            sb.append(step++).append(". Persistance / validation\n");
            sb.append("   → Durée SAVE : ").append(formatDuration(facts.getSavePersistMs())).append('\n');
            if (facts.getSavePersistKey() != null) {
                sb.append("   → Clé : ").append(facts.getSavePersistKey()).append('\n');
            }
            if (facts.getInsertRelChildCount() != null || facts.getInsertRelRootCount() != null) {
                sb.append("   → Insertions de relations détectées (child/root).\n");
            }
            return;
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.WORKFLOW_DOMINATED) {
            sb.append(step++).append(". Transition workflow (Transit task)\n");
            sb.append("   → Action : ").append(orNa(formatActionDisplay(facts))).append('\n');
            sb.append("   → Goulot mesuré : durée de Transit task.\n");
            sb.append("   → Contenu interne de Transit non détaillé dans les logs de cette fenêtre.\n");
            return;
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.RENDERING_DOMINATED) {
            sb.append(step).append(". Génération / affichage du résultat (rendering result)\n");
            sb.append("   Durée : ").append(formatDuration(facts.getRenderingMs())).append('\n');
            if (facts.getElementCount() != null) {
                sb.append("   → Volume amont mesuré : ").append(formatRows(facts.getElementCount())).append('\n');
            }
            sb.append("   → Les préparations sont terminées ; le goulot est cette phase"
                    + " (mécanisme interne du rendu non détaillé dans les logs).\n");
        }
    }

    private void appendRulesCauseChain(StringBuilder sb, WorksLatencyFacts facts, int step) {
        if (facts.getPreRulesRowCount() != null && facts.getPreRulesRowCount() > 0) {
            sb.append(step++).append(". Contexte avant les règles (volume chargé)\n");
            sb.append("   → Filtre « ")
                    .append(orNa(facts.getPreRulesFilterCode()))
                    .append(" » : ")
                    .append(formatRows(facts.getPreRulesRowCount()));
            if (facts.getPreRulesSearchMs() != null) {
                sb.append(" chargée(s) en ").append(formatDuration(facts.getPreRulesSearchMs()));
            }
            sb.append(".\n");
            sb.append("   → Volume amont mesuré avant fire rules (contexte, pas preuve du parcours interne).\n");
        }

        sb.append(step++).append(". Moteur de règles métier (Drools / fire rules)\n");
        sb.append("   Durée : ")
                .append(formatDuration(firstPositive(facts.getRulesEngineMs())))
                .append('\n');
        String action = formatActionDisplay(facts);
        if (action != null) {
            sb.append("   → Action : ").append(action).append('\n');
        }
        String dominantRule = facts.dominantDroolsRule();
        if (dominantRule != null) {
            sb.append("   → Règle Drools dominante : « ")
                    .append(friendlyDroolsName(dominantRule)).append(" »");
            if (!facts.getDroolsRuleHits().isEmpty() && facts.getDroolsRuleHits().get(0).getCount() > 1) {
                sb.append(" (").append(facts.getDroolsRuleHits().get(0).getCount())
                        .append("× dans la fenêtre)");
            }
            sb.append(".\n");
            if (facts.getDroolsRuleNames().size() > 1) {
                sb.append("   → Autres règles vues : ");
                int n = 0;
                for (String name : facts.getDroolsRuleNames()) {
                    if (name.equalsIgnoreCase(dominantRule)) continue;
                    if (n > 0) sb.append(", ");
                    sb.append(friendlyDroolsName(name));
                    if (++n >= 4) break;
                }
                sb.append(".\n");
            }
        }
        if (facts.getMajInsertTotal() > 0) {
            sb.append("   → MAJ_Insert observée(s) pendant les règles : ")
                    .append(facts.getMajInsertTotal())
                    .append(" écriture(s)");
            if (facts.getMajInsertDominantType() != null) {
                sb.append(" (dont ")
                        .append(facts.getMajInsertDominantCount())
                        .append("× MAJ_Insert_")
                        .append(facts.getMajInsertDominantType())
                        .append(')');
            }
            sb.append(" — confirmant des écritures métier, sans prouver à elles seules la durée fire rules.\n");
        } else {
            sb.append("   → L'exécution des règles consomme la quasi-totalité du temps.\n");
        }

        if (facts.getInsertRelChildCount() != null || facts.getInsertRelRootCount() != null
                || facts.getSavePersistMs() != null) {
            sb.append(step++).append(". Persistance après / pendant les règles\n");
            if (facts.getInsertRelChildCount() != null) {
                sb.append("   → insertArrayRel child : ")
                        .append(facts.getInsertRelChildCount())
                        .append(" élément(s)");
                if (facts.getInsertRelChildMs() != null) {
                    sb.append(" en ").append(formatDuration(facts.getInsertRelChildMs()));
                }
                sb.append(".\n");
            }
            if (facts.getInsertRelRootCount() != null) {
                sb.append("   → insertArrayRel root : ")
                        .append(facts.getInsertRelRootCount())
                        .append(" élément(s)");
                if (facts.getInsertRelRootMs() != null) {
                    sb.append(" en ").append(formatDuration(facts.getInsertRelRootMs()));
                }
                sb.append(".\n");
            }
            if (facts.getSavePersistMs() != null) {
                sb.append("   → SAVE");
                if (facts.getSavePersistKey() != null) {
                    sb.append(" (« ").append(facts.getSavePersistKey()).append(" »)");
                }
                sb.append(" : ").append(formatDuration(facts.getSavePersistMs()))
                        .append(" (secondaire face aux règles).\n");
            }
        }
    }

    private String buildRulesSummary(WorksLatencyFacts facts,
                                     String user,
                                     String filter,
                                     LatencyTimelineStepDto primary,
                                     long maxDurationMs) {
        long rulesMs = firstPositive(durationOf(primary), facts.getRulesEngineMs(), maxDurationMs);
        String action = formatActionDisplay(facts);
        if (action == null) {
            action = "un traitement de règles";
        } else {
            action = "« " + action + " »";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.FRANCE,
                "%s a lancé %s sur %s ; le moteur de règles a pris %s",
                user, action, filter, formatDuration(rulesMs)));
        if (facts.getPreRulesRowCount() != null && facts.getPreRulesRowCount() > 10) {
            sb.append(String.format(Locale.FRANCE,
                    " après avoir chargé %s",
                    formatRows(facts.getPreRulesRowCount())));
            if (facts.getPreRulesFilterCode() != null) {
                sb.append(" (filtre « ").append(facts.getPreRulesFilterCode()).append(" »)");
            }
        }
        if (facts.getMajInsertTotal() > 0) {
            sb.append(String.format(Locale.FRANCE,
                    " ; ~%,d MAJ_Insert observée(s) pendant cette phase"
                            + " (écritures confirmées, non chronométrées individuellement)",
                    facts.getMajInsertTotal()));
        }
        sb.append('.');
        return sb.toString();
    }

    private String buildRulesPrimaryCause(WorksLatencyFacts facts, LatencyTimelineStepDto primary) {
        StringBuilder sb = new StringBuilder("Moteur de règles métier — ");
        sb.append(formatDuration(firstPositive(durationOf(primary), facts.getRulesEngineMs())));
        String rule = facts.dominantDroolsRule();
        if (rule != null) {
            sb.append(" — règle « ").append(friendlyDroolsName(rule)).append(" »");
        }
        if (facts.getMajInsertTotal() > 0) {
            sb.append(" — ").append(facts.getMajInsertTotal()).append(" MAJ_Insert observée(s)");
            if (facts.getMajInsertDominantType() != null) {
                sb.append(" (")
                        .append(facts.getMajInsertDominantType())
                        .append('×')
                        .append(facts.getMajInsertDominantCount())
                        .append(')');
            }
        } else if (rule == null && facts.getPreRulesRowCount() != null && facts.getPreRulesRowCount() > 10) {
            sb.append(" — volume amont ").append(facts.getPreRulesRowCount()).append(" ligne(s)");
        }
        return sb.toString();
    }

    private void appendAftermath(StringBuilder sb, WorksLatencyFacts facts) {
        sb.append("Juste après le chargement, les étapes suivantes sont rapides :\n");
        if (facts.getPostSearchRulesMs() != null) {
            sb.append("• Règles / chargement filtre : ").append(formatDuration(facts.getPostSearchRulesMs())).append('\n');
        }
        if (facts.getRenderingMs() != null) {
            sb.append("• Affichage (rendering result) : ").append(formatDuration(facts.getRenderingMs())).append('\n');
        }
        sb.append("→ La lenteur n'est pas l'affichage ni les règles : c'est bien le chargement des données.\n");
    }

    private void appendSchema(StringBuilder sb, WorksLatencyFacts facts,
                              LatencyBottleneckAnalyzer.NarrativeKind kind) {
        String user = labelUser(facts.getUserName());
        sb.append(user).append('\n');

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED) {
            sb.append("  → action ").append(orNa(formatActionDisplay(facts)))
                    .append(" sur ").append(labelScope(facts)).append('\n');
            if (facts.getPreRulesRowCount() != null && facts.getPreRulesRowCount() > 0) {
                sb.append("    → volume amont : ").append(formatRows(facts.getPreRulesRowCount()));
                if (facts.getPreRulesFilterCode() != null) {
                    sb.append(" (« ").append(facts.getPreRulesFilterCode()).append(" »)");
                }
                sb.append('\n');
            }
            sb.append("    → fire rules / running rules : ")
                    .append(formatDuration(facts.getRulesEngineMs())).append('\n');
            if (facts.getMajInsertTotal() > 0) {
                sb.append("    → effets de bord : ~")
                        .append(String.format(Locale.FRANCE, "%,d", facts.getMajInsertTotal()))
                        .append(" MAJ_Insert\n");
            }
            if (facts.getSavePersistMs() != null) {
                sb.append("    → SAVE secondaire : ")
                        .append(formatDuration(facts.getSavePersistMs())).append('\n');
            }
            return;
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.WS_DOMINATED) {
            sb.append("  → webservice « ").append(wsLabel(facts)).append(" »\n");
            sb.append("    → durée dominante : ")
                    .append(formatDuration(firstPositive(facts.getRulesEngineMs(), facts.getGlobalMs())))
                    .append('\n');
            if (facts.getRootQueryMs() != null) {
                sb.append("    → SQL associé : ").append(formatDuration(facts.getRootQueryMs())).append('\n');
            }
            return;
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.PRINT_DOMINATED) {
            sb.append("  → impression / PDF (« ").append(orNa(formatActionDisplay(facts))).append(" »)\n");
            sb.append("    → règles / génération : ")
                    .append(formatDuration(facts.getRulesEngineMs())).append('\n');
            return;
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.AUTOSTART_DOMINATED) {
            sb.append("  → AutoStart (« ").append(orNa(facts.getTaskName())).append(" »)\n");
            sb.append("    → running rules : ")
                    .append(formatDuration(facts.getRulesEngineMs())).append('\n');
            return;
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.SAVE_DOMINATED) {
            sb.append("  → enregistrement / validation\n");
            sb.append("    → SAVE : ").append(formatDuration(facts.getSavePersistMs())).append('\n');
            return;
        }

        sb.append("  → recherche sur ").append(labelScope(facts)).append('\n');

        if (facts.getRootQueryMs() != null && facts.getRootQueryMs() >= 500) {
            sb.append("    → requête principale : ")
                    .append(formatRows(resolveRowCount(facts))).append(" (")
                    .append(formatDuration(facts.getRootQueryMs())).append(")\n");
        }

        if (facts.isMultithreaded() && facts.getBatchCount() != null) {
            sb.append("    → ").append(facts.getBatchCount())
                    .append(" chargements parallèles en base (détails / enfants)\n");
        }

        if (facts.getGlobalMs() != null) {
            sb.append("    → total recherche : ").append(formatDuration(facts.getGlobalMs()));
            if (facts.getMemoryPeakMo() != null) {
                sb.append(", ~").append(formatMemory(facts.getMemoryPeakMo()));
            }
            sb.append('\n');
        }

        if (facts.getRenderingMs() != null) {
            sb.append("    → affichage liste : ").append(formatDuration(facts.getRenderingMs())).append('\n');
        } else {
            sb.append("    → puis affichage de la liste\n");
        }
    }

    private void appendQaTable(StringBuilder sb, WorksLatencyFacts facts,
                               LatencyBottleneckAnalyzer.NarrativeKind kind,
                               long maxDurationMs) {
        sb.append("Où ? ").append(labelScope(facts));
        if (facts.getClassName() != null) {
            sb.append(", objet ").append(labelObject(facts.getClassName()));
        }
        sb.append('\n');

        sb.append("Quoi exactement ? ");
        sb.append(switch (kind) {
            case SQL_DOMINATED -> "requête SQL initiale (prepareSearchByRoot)";
            case PARALLEL_LOAD_DOMINATED -> facts.hasSignificantSecondaryRender()
                    ? "deux goulots : chargement parallèle multithread puis rendering result"
                    : "chargement parallèle multithread (global searchComposantByRoot)";
            case RULES_DOMINATED -> {
                String what = "exécution des règles métier (running rules / fire rules)";
                if (facts.getMajInsertTotal() > 0) {
                    what = "règles (MAJ_Insert ×" + facts.getMajInsertTotal()
                            + " observée(s), non chronométrées individuellement)";
                }
                yield what;
            }
            case RENDERING_DOMINATED -> "affichage de l'écran (rendering result)";
            case WS_DOMINATED -> "appel webservice (« " + wsLabel(facts) + " »)";
            case PRINT_DOMINATED -> "impression / génération PDF";
            case OPERATION_LOAD_DOMINATED -> "chargement du contexte / de l'opération (loadOperationById)";
            case AUTOSTART_DOMINATED -> "règles AutoStart en arrière-plan";
            case SAVE_DOMINATED -> "sauvegarde / validation (SAVE)";
            case WORKFLOW_DOMINATED -> "Transit task (transition workflow) — contenu interne non détaillé";
            case COMPLEX -> "plusieurs contributeurs (SQL / règles / rendu / SAVE…)";
            default -> "étape la plus lente mesurée dans les logs";
        });
        sb.append(" — ").append(formatDuration(maxDurationMs)).append('\n');

        List<String> whyLevels = buildWhyChain(facts, kind, null, maxDurationMs);
        if (whyLevels.isEmpty()) {
            sb.append("Pourquoi ? Voir la section « Pourquoi (en profondeur) ».\n");
        } else {
            for (int i = 0; i < whyLevels.size(); i++) {
                sb.append("Pourquoi ").append(i + 1).append(" ? ").append(whyLevels.get(i)).append('\n');
            }
        }

        sb.append("Qui ? ").append(labelUser(facts.getUserName()));
        if (facts.getSessionId() != null) {
            sb.append(", session ").append(facts.getSessionId());
        }
        sb.append('\n');

        if (facts.getOperationStart() != null) {
            sb.append("Quand ? ");
            sb.append(facts.getOperationStart().format(TIME_FMT));
            if (facts.getOperationEnd() != null) {
                sb.append(" → ").append(facts.getOperationEnd().format(TIME_FMT));
            }
            sb.append('\n');
        }
    }

    private String describeParallelShort(WorksLatencyFacts facts) {
        if (facts.getThreadPoolSize() != null && facts.getPartitionSize() != null) {
            return String.format(Locale.FRANCE, " (%d threads, partitions de %d)",
                    facts.getThreadPoolSize(), facts.getPartitionSize());
        }
        if (facts.getPartitionSize() != null) {
            return String.format(Locale.FRANCE, " (partitions de %d)", facts.getPartitionSize());
        }
        if (facts.getThreadPoolSize() != null) {
            return String.format(Locale.FRANCE, " (%d threads)", facts.getThreadPoolSize());
        }
        return "";
    }

    private long resolveTotalMs(WorksLatencyFacts facts) {
        if (facts.getGlobalMs() != null) {
            return facts.getGlobalMs();
        }
        if (facts.getRulesEngineMs() != null) {
            return facts.getRulesEngineMs();
        }
        if (facts.getRootQueryMs() != null) {
            return facts.getRootQueryMs();
        }
        return 0;
    }

    /**
     * Durée totale de l'opération : on ne sous-estime jamais — la durée maximale
     * mesurée (maxDurationMs) et l'étape goulot servent de planchers.
     */
    private long resolveTotalMs(WorksLatencyFacts facts,
                                LatencyTimelineStepDto primary,
                                long maxDurationMs) {
        long total = Math.max(resolveTotalMs(facts), Math.max(maxDurationMs, 0));
        Long primaryMs = durationOf(primary);
        if (primaryMs != null) {
            total = Math.max(total, primaryMs);
        }
        return total;
    }

    private int resolveRowCount(WorksLatencyFacts facts) {
        return resolveRowCount(facts, null);
    }

    private int resolveRowCount(WorksLatencyFacts facts, LatencyTimelineStepDto primary) {
        if (facts.getGlobalRowCount() != null) {
            return facts.getGlobalRowCount();
        }
        if (facts.getRootRowCount() != null) {
            return facts.getRootRowCount();
        }
        if (facts.getElementCount() != null) {
            return facts.getElementCount();
        }
        if (primary != null && primary.getRowCount() != null) {
            return primary.getRowCount();
        }
        return 0;
    }

    /**
     * Durée de la requête racine alignée sur le vrai goulot : on prend le maximum
     * entre la valeur des faits et l'étape goulot (ROOT_QUERY) pour éviter d'afficher
     * une requête racine accessoire (ex. « 3 ms ») sur une opération longue.
     */
    private Long effectiveRootMs(WorksLatencyFacts facts, LatencyTimelineStepDto primary) {
        long fromFacts = facts.getRootQueryMs() != null ? facts.getRootQueryMs() : 0;
        long fromPrimary = primary != null
                && LatencyOperationClassifier.ROOT_QUERY.equals(primary.getStepType())
                && primary.getDurationMs() != null
                ? primary.getDurationMs()
                : 0;
        long best = Math.max(fromFacts, fromPrimary);
        return best > 0 ? best : null;
    }

    private Integer effectiveRootRows(WorksLatencyFacts facts, LatencyTimelineStepDto primary) {
        if (facts.getRootRowCount() != null) {
            return facts.getRootRowCount();
        }
        if (primary != null
                && LatencyOperationClassifier.ROOT_QUERY.equals(primary.getStepType())) {
            return primary.getRowCount();
        }
        return null;
    }

    private Long durationOf(LatencyTimelineStepDto step) {
        return step != null ? step.getDurationMs() : null;
    }

    private Long firstPositive(Long... values) {
        for (Long value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private Long firstPositive(Long a, Long b, long c) {
        return firstPositive(a, b, c > 0 ? Long.valueOf(c) : null);
    }

    private Long firstPositive(Long a, Long b, Long c) {
        Long fromAb = firstPositive(a, b);
        if (fromAb != null) {
            return fromAb;
        }
        return c != null && c > 0 ? c : null;
    }

    private String labelUser(String user) {
        return user != null && !user.isBlank() ? user.trim() : "L'utilisateur";
    }

    private String labelFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return "l'écran concerné";
        }
        return "l'écran « " + filter.trim() + " »";
    }

    /** Étiquette du périmètre : filtre si présent, sinon le processus métier, sinon générique. */
    private String labelScope(WorksLatencyFacts facts) {
        String f = firstNonBlank(facts.getPreRulesFilterCode(), facts.getFilterCode());
        if (f != null && !f.isBlank()) {
            return "l'écran « " + f.trim() + " »";
        }
        String p = rawProcess(facts.getProcessName());
        if (p != null && !p.isBlank()) {
            return "le traitement « " + p + " »";
        }
        return "l'écran concerné";
    }

    private String labelProcess(String process) {
        if (process == null || process.isBlank()) {
            return "non précisé";
        }
        String p = process.trim();
        if (p.startsWith("process.")) {
            p = p.substring("process.".length());
        }
        return switch (p.toUpperCase(Locale.ROOT)) {
            case "AST_PRINT" -> "AST_PRINT (impression / consultation)";
            case "MANIFESTE" -> "Manifeste";
            case "PROCESSFILTER" -> "Filtre métier";
            default -> p;
        };
    }

    private String labelObject(String className) {
        if (className == null || className.isBlank()) {
            return "";
        }
        String c = dedupClassName(className).replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return " (" + c + ")";
    }

    private String formatRows(int count) {
        if (count <= 0) {
            return "des enregistrements";
        }
        return String.format(Locale.FRANCE, "%,d enregistrement(s)", count);
    }

    private String formatRowsSuffix(Integer count) {
        return count != null ? " — " + count + " ligne(s)" : "";
    }

    private String formatMemorySuffix(Integer mo) {
        return mo != null ? " — " + formatMemory(mo) : "";
    }

    private String formatMemory(int mo) {
        if (mo >= 1024) {
            return String.format(Locale.FRANCE, "%.1f Go", mo / 1024.0);
        }
        return mo + " Mo";
    }

    /** Affiche 0 ms tel quel (contrairement à {@link #formatDuration} qui masque les ≤0). */
    private String formatMsExact(long ms) {
        if (ms >= 1000) {
            return String.format(Locale.FRANCE, "%.1f s", ms / 1000.0);
        }
        return ms + " ms";
    }

    private String formatDuration(Long ms) {
        if (ms == null || ms <= 0) {
            return "durée non mesurée";
        }
        if (ms >= 1000) {
            return String.format(Locale.FRANCE, "%.1f s", ms / 1000.0);
        }
        return ms + " ms";
    }
}
