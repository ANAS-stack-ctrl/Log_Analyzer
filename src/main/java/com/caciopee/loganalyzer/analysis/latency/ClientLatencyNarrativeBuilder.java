package com.caciopee.loganalyzer.analysis.latency;

import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Récit client 100 % factuel à partir de {@link WorksLatencyFacts} — sans LLM.
 */
@Component
public class ClientLatencyNarrativeBuilder {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public String buildSummary(WorksLatencyFacts facts, LatencyBottleneckAnalyzer.NarrativeKind kind) {
        String user = labelUser(facts.getUserName());
        String filter = labelFilter(facts.getFilterCode());
        long totalMs = resolveTotalMs(facts);
        int rows = resolveRowCount(facts);

        return switch (kind) {
            case SQL_DOMINATED -> String.format(Locale.FRANCE,
                    "%s a lancé une recherche sur %s ; le système a exécuté une requête SQL lourde"
                            + " (%s) pour %s — l'opération complète a pris %s.",
                    user, filter, formatDuration(facts.getRootQueryMs()), formatRows(rows), formatDuration(totalMs));
            case PARALLEL_LOAD_DOMINATED -> String.format(Locale.FRANCE,
                    "%s a lancé une recherche sur %s ; le système a dû charger %s"
                            + "%s%s — tout cela a pris %s avant d'afficher la liste.",
                    user, filter, formatRows(rows), labelObject(facts.getClassName()),
                    describeParallelShort(facts), formatDuration(totalMs));
            case RULES_DOMINATED -> String.format(Locale.FRANCE,
                    "%s a déclenché un traitement sur %s ; l'exécution des règles métier a pris %s.",
                    user, filter, formatDuration(facts.getRulesEngineMs()));
            case RENDERING_DOMINATED -> String.format(Locale.FRANCE,
                    "%s a consulté %s ; l'affichage des résultats a pris %s (les données étaient déjà chargées).",
                    user, filter, formatDuration(facts.getRenderingMs()));
            default -> String.format(Locale.FRANCE,
                    "%s a effectué une opération sur %s qui a pris %s (%s enregistrement(s) concerné(s)).",
                    user, filter, formatDuration(totalMs), formatRows(rows));
        };
    }

    public String buildPrimaryCause(WorksLatencyFacts facts,
                                    LatencyBottleneckAnalyzer.NarrativeKind kind,
                                    LatencyTimelineStepDto primary) {
        return switch (kind) {
            case SQL_DOMINATED -> "Requête SQL initiale trop lente — "
                    + formatDuration(facts.getRootQueryMs())
                    + formatRowsSuffix(facts.getRootRowCount())
                    + formatMemorySuffix(facts.getRootMemoryMo() != null ? facts.getRootMemoryMo() : facts.getMemoryPeakMo());
            case PARALLEL_LOAD_DOMINATED -> "Chargement massif en parallèle des détails — "
                    + formatDuration(facts.getGlobalMs())
                    + formatRowsSuffix(resolveRowCount(facts))
                    + (facts.getBatchCount() != null ? " — " + facts.getBatchCount() + " lots parallèles" : "");
            case RULES_DOMINATED -> "Moteur de règles métier — " + formatDuration(facts.getRulesEngineMs());
            case RENDERING_DOMINATED -> "Affichage de l'écran — " + formatDuration(facts.getRenderingMs());
            default -> primary != null && primary.getOperationName() != null
                    ? primary.getOperationName() + " — " + formatDuration(primary.getDurationMs())
                    : "Étape lente identifiée dans les logs";
        };
    }

    public String buildFullNarrative(WorksLatencyFacts facts,
                                     LatencyBottleneckAnalyzer.NarrativeKind kind,
                                     long maxDurationMs) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildSummary(facts, kind)).append("\n\n");

        sb.append("━━━ Déclencheur métier ━━━\n");
        appendTriggerSection(sb, facts);

        sb.append("\n━━━ Chaîne de cause (d'où vient la lenteur ?) ━━━\n");
        appendCauseChain(sb, facts, kind);

        if (facts.isFastDownstream() || facts.getRenderingMs() != null) {
            sb.append("\n━━━ Après la lenteur ━━━\n");
            appendAftermath(sb, facts);
        }

        sb.append("\n━━━ Schéma simplifié ━━━\n");
        appendSchema(sb, facts, kind);

        sb.append("\n━━━ Réponse rapide ━━━\n");
        appendQaTable(sb, facts, kind, maxDurationMs);

        return sb.toString().trim();
    }

    private void appendTriggerSection(StringBuilder sb, WorksLatencyFacts facts) {
        sb.append("• Filtre / écran : ").append(labelFilter(facts.getFilterCode())).append('\n');
        sb.append("• Processus : ").append(labelProcess(facts.getProcessName())).append('\n');
        sb.append("• Utilisateur : ").append(labelUser(facts.getUserName())).append('\n');
        if (facts.getSessionId() != null) {
            sb.append("• Session : ").append(facts.getSessionId()).append('\n');
        }
        if (facts.getClassName() != null) {
            sb.append("• Objet métier : ").append(labelObject(facts.getClassName())).append('\n');
        }
        if (facts.getOperationStart() != null) {
            sb.append("• Début : ").append(facts.getOperationStart().format(TIME_FMT));
            if (facts.getOperationEnd() != null) {
                sb.append(" → fin : ").append(facts.getOperationEnd().format(TIME_FMT));
            }
            sb.append('\n');
        }
    }

    private void appendCauseChain(StringBuilder sb, WorksLatencyFacts facts,
                                  LatencyBottleneckAnalyzer.NarrativeKind kind) {
        int step = 1;

        if (facts.getRootQueryMs() != null && facts.getRootQueryMs() >= 500) {
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
            if (facts.getBatchCount() != null && facts.getPartitionSize() != null) {
                sb.append("   → ").append(formatRows(facts.getElementCount() != null
                        ? facts.getElementCount() : resolveRowCount(facts)));
                sb.append(" découpé(s) en ").append(facts.getBatchCount());
                sb.append(" lot(s) de ~").append(facts.getPartitionSize()).append(" élément(s).\n");
            } else if (facts.getBatchCount() != null) {
                sb.append("   → ").append(facts.getBatchCount())
                        .append(" traitements parallèles détectés dans les logs.\n");
            }
            sb.append("   → Pour chaque lot : requêtes SQL + tables temporaires (InsertTemp)")
                    .append(" pour charger les détails / enfants.\n");
            if (facts.getLoadChildSampleCount() > 0) {
                sb.append("   → Par lot : loadListChilds entre ")
                        .append(formatDuration(facts.getLoadChildMinMs()))
                        .append(" et ").append(formatDuration(facts.getLoadChildMaxMs()));
                if (facts.getLoadChildAvgMs() != null) {
                    sb.append(" (moy. ").append(formatDuration(facts.getLoadChildAvgMs())).append(")");
                }
                sb.append(" — ").append(facts.getLoadChildSampleCount())
                        .append(" mesure(s) dans les logs.\n");
            }
            if (kind == LatencyBottleneckAnalyzer.NarrativeKind.PARALLEL_LOAD_DOMINATED) {
                sb.append("   → C'est le cœur de l'attente utilisateur.\n");
            }
        }

        if (facts.getMemoryPeakMo() != null && facts.getMemoryPeakMo() >= 2000) {
            sb.append(step++).append(". Pression mémoire\n");
            sb.append("   → Jusqu'à ~").append(formatMemory(facts.getMemoryPeakMo()))
                    .append(" pendant le chargement.\n");
            sb.append("   → Peut ralentir encore (garbage collector, manque de RAM).\n");
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.RULES_DOMINATED) {
            sb.append(step).append(". Moteur de règles métier (Drools)\n");
            sb.append("   Durée : ").append(formatDuration(facts.getRulesEngineMs())).append('\n');
            sb.append("   → L'exécution des règles consomme la quasi-totalité du temps.\n");
        }

        if (kind == LatencyBottleneckAnalyzer.NarrativeKind.RENDERING_DOMINATED) {
            sb.append(step).append(". Affichage de l'écran\n");
            sb.append("   Durée : ").append(formatDuration(facts.getRenderingMs())).append('\n');
            sb.append("   → Les données étaient prêtes ; c'est le rendu qui bloque.\n");
        }
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
        sb.append("  → recherche sur ").append(labelFilter(facts.getFilterCode())).append('\n');

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
        sb.append("Où ? ").append(labelFilter(facts.getFilterCode()));
        if (facts.getClassName() != null) {
            sb.append(", objet ").append(labelObject(facts.getClassName()));
        }
        sb.append('\n');

        sb.append("Quoi exactement ? ");
        sb.append(switch (kind) {
            case SQL_DOMINATED -> "requête SQL initiale (prepareSearchByRoot)";
            case PARALLEL_LOAD_DOMINATED -> "chargement parallèle des détails (loadListChilds + tables temporaires)";
            case RULES_DOMINATED -> "exécution des règles métier (running rules)";
            case RENDERING_DOMINATED -> "affichage de l'écran (rendering result)";
            default -> "étape la plus lente mesurée dans les logs";
        });
        sb.append(" — ").append(formatDuration(maxDurationMs)).append('\n');

        sb.append("Pourquoi ? ");
        sb.append(switch (kind) {
            case SQL_DOMINATED -> facts.getSqlJoinCount() >= 3
                    ? "requête SQL complexe (" + facts.getSqlJoinCount() + " jointures)"
                    : "exécution SQL lente sur la recherche racine";
            case PARALLEL_LOAD_DOMINATED -> formatRows(resolveRowCount(facts))
                    + " à charger avec requêtes répétées en parallèle — pas un simple écran « lent »";
            case RULES_DOMINATED -> "règles métier lourdes ou volume de faits important";
            case RENDERING_DOMINATED -> "rendu UI coûteux sur le volume affiché";
            default -> "volume de données et requêtes successives";
        });
        sb.append('\n');

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
        if (facts.getBatchCount() != null && facts.getPartitionSize() != null) {
            return String.format(Locale.FRANCE, ", avec %d traitements parallèles (~%d par lot)",
                    facts.getBatchCount(), facts.getPartitionSize());
        }
        if (facts.getBatchCount() != null) {
            return ", avec " + facts.getBatchCount() + " traitements parallèles";
        }
        return ", avec chargement parallèle en base";
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

    private int resolveRowCount(WorksLatencyFacts facts) {
        if (facts.getGlobalRowCount() != null) {
            return facts.getGlobalRowCount();
        }
        if (facts.getRootRowCount() != null) {
            return facts.getRootRowCount();
        }
        if (facts.getElementCount() != null) {
            return facts.getElementCount();
        }
        return 0;
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
        String c = className.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
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
