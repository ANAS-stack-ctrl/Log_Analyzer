package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.config.AiAssistantProperties;
import com.caciopee.loganalyzer.dto.LatencyExecutionSampleDto;
import com.caciopee.loganalyzer.dto.LatencyExplanationResponseDto;
import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;
import com.caciopee.loganalyzer.dto.LatencyTimelineStepDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Génère l'explication « pas à pas » d'une lenteur en confiant la rédaction à un LLM.
 *
 * <p>Architecture hybride volontaire :</p>
 * <ul>
 *   <li><b>Java (déterministe)</b> : sélectionne les logs concernés (fenêtre déjà calculée par
 *       {@code LatencyInvestigationService}) et fournit les FAITS vérifiés (durées, breakdown,
 *       SQL, 0 row, avertissement de mesure). Rien n'est deviné.</li>
 *   <li><b>LLM (le « cerveau »)</b> : reçoit ces faits + les vraies lignes de logs + une amorce de
 *       connaissance WORKS, puis rédige une explication claire pour un client non technique.</li>
 * </ul>
 *
 * <p>Si aucun LLM n'est configuré ou en cas d'erreur, on retombe automatiquement sur le récit
 * règle-based ({@code report.narrativeSummary}) : l'application ne casse jamais.</p>
 */
@Service
public class LatencyLlmExplanationService {

    private final AiAssistantProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LatencyLlmExplanationService(AiAssistantProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public LatencyExplanationResponseDto explain(LatencyOriginReportDto report) {
        if (report == null) {
            return local("", "Aucun rapport de latence fourni.");
        }
        if (!aiProperties.isConfigured()) {
            return local(report.getNarrativeSummary(),
                    "Mode local guidé (aucun LLM actif). Activez le profil Ollama (local) ou une clé OpenAI "
                            + "via app.ai.* pour une explication générée par l'IA.");
        }
        try {
            String content = callLlm(report);
            if (content == null || content.isBlank()) {
                return local(report.getNarrativeSummary(), "Le modèle n'a pas renvoyé de texte exploitable.");
            }
            if (AiResponseSanitizer.looksLikeEnglish(content)) {
                return local(report.getNarrativeSummary(),
                        "Le modèle a répondu en anglais ; explication française règle-based affichée.");
            }
            String sanitized = AiResponseSanitizer.sanitize(content.trim());
            if (contradictsDeterministicVerdict(report, sanitized)) {
                return local(report.getNarrativeSummary(),
                        "L'IA a contredit le verdict déterministe (ex. « SQL rapide » alors que le SQL domine) ; "
                                + "explication rule-based affichée.");
            }
            // Si le récit LOCAL est déjà riche (faits chiffrés) et que le LLM est plus pauvre /
            // générique, on conserve le récit déterministe — l'IA ne doit pas l'écraser.
            if (preferLocalNarrative(report, sanitized)) {
                return local(report.getNarrativeSummary(),
                        "Récit local plus riche (faits chiffrés) conservé ; le modèle n'apportait pas assez de précision.");
            }
            LatencyExplanationResponseDto dto = new LatencyExplanationResponseDto();
            dto.setExplanation(sanitized);
            dto.setSource(aiProperties.hasApiKey() ? "OPENAI" : "OLLAMA");
            dto.setModel(aiProperties.getModel());
            dto.setGeneratedByLlm(true);
            return dto;
        } catch (Exception e) {
            return local(report.getNarrativeSummary(), "Erreur appel IA : " + e.getMessage());
        }
    }

    /**
     * Rejette une réponse LLM qui contredit un goulot SQL évident
     * (cas SUPERVISION ED : prepareSearchByRoot 73s → l'IA ne doit pas dire « SQL rapide »).
     */
    private boolean contradictsDeterministicVerdict(LatencyOriginReportDto report, String explanation) {
        if (report == null || !notBlank(explanation)) return false;
        String lower = explanation.toLowerCase(java.util.Locale.ROOT);

        LatencyTimelineStepDto bottleneck = null;
        if (report.getTimelineSteps() != null) {
            bottleneck = report.getTimelineSteps().stream()
                    .filter(LatencyTimelineStepDto::isBottleneck)
                    .findFirst()
                    .orElse(null);
            if (bottleneck == null) {
                bottleneck = report.getTimelineSteps().stream()
                        .filter(s -> !s.isSuspect() && s.getDurationMs() != null)
                        .max(java.util.Comparator.comparingLong(LatencyTimelineStepDto::getDurationMs))
                        .orElse(null);
            }
        }
        if (bottleneck == null || bottleneck.getDurationMs() == null || report.getMaxDurationMs() == null) {
            return false;
        }
        long total = report.getMaxDurationMs();
        if (total <= 0) return false;
        double share = bottleneck.getDurationMs() * 1.0 / total;
        String op = safe(bottleneck.getOperationName()).toLowerCase(java.util.Locale.ROOT);
        boolean sqlDominates = share >= 0.40 && isSqlStep(op);
        if (!sqlDominates) return false;

        boolean claimsSqlFast = lower.contains("sql rapide")
                || lower.contains("pas la base")
                || lower.contains("n'est pas la base")
                || lower.contains("n’est pas la base")
                || lower.contains("non de la base")
                || (lower.contains("sql") && lower.contains("rapide"));
        boolean blamesRulesWrongly = (lower.contains("moteur de règles") || lower.contains("running rules"))
                && !op.contains("running rules") && !op.contains("fire rules");
        return claimsSqlFast || blamesRulesWrongly;
    }

    /**
     * Conserve le récit Java quand il contient déjà des preuves chiffrées (MAJ_Insert,
     * volume amont, doAction) que le LLM omet ou dilue en texte générique.
     */
    private boolean preferLocalNarrative(LatencyOriginReportDto report, String llmText) {
        String local = report != null ? report.getNarrativeSummary() : null;
        if (!notBlank(local) || !notBlank(llmText)) {
            return false;
        }
        String localLower = local.toLowerCase(java.util.Locale.ROOT);
        String llmLower = llmText.toLowerCase(java.util.Locale.ROOT);

        boolean localHasHardFacts = localLower.contains("maj_insert")
                || localLower.contains("effets de bord")
                || localLower.contains("rapprocher")
                || localLower.contains("volume amont")
                || localLower.contains("chaîne de cause");
        if (!localHasHardFacts) {
            return false;
        }

        boolean llmMentionsFacts = llmLower.contains("maj_insert")
                || llmLower.contains("rapprocher")
                || llmLower.contains("matchall")
                || (llmLower.contains("insert") && llmLower.contains("règle"));
        if (llmMentionsFacts && llmText.length() >= local.length() * 0.55) {
            return false;
        }
        // LLM trop court / trop générique face à un dossier local détaillé.
        return llmText.length() < local.length() * 0.7 || !llmMentionsFacts;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Appel LLM (API OpenAI-compatible : OpenAI ou Ollama local)
    // ─────────────────────────────────────────────────────────────────────────
    private String callLlm(LatencyOriginReportDto report) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", aiProperties.getModel());
        body.put("temperature", aiProperties.getTemperature());
        ArrayNode messages = body.putArray("messages");

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", WORKS_SYSTEM_PROMPT);

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", buildDossier(report));

        String url = aiProperties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

        RestClient.RequestBodySpec spec = restClient.post().uri(url);
        if (aiProperties.hasApiKey()) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey());
        }
        JsonNode apiResponse = spec
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (apiResponse != null
                && apiResponse.path("choices").isArray()
                && !apiResponse.path("choices").isEmpty()) {
            return apiResponse.path("choices").get(0).path("message").path("content").asText("");
        }
        return "";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dossier envoyé au LLM : FAITS vérifiés + vraies lignes de logs
    // ─────────────────────────────────────────────────────────────────────────
    private String buildDossier(LatencyOriginReportDto r) {
        StringBuilder sb = new StringBuilder(8192);

        sb.append("=== FAITS VÉRIFIÉS (mesurés par le moteur d'analyse — ne recalcule rien) ===\n");
        appendFact(sb, "Utilisateur", r.getUserName());
        appendFact(sb, "Processus / traitement", r.getProcessName());
        appendFact(sb, "Filtre", r.getFilterCode());
        appendFact(sb, "Action", r.getActionName());
        appendFact(sb, "Écran", r.getScreenName());
        appendFact(sb, "Objet / classe", r.getClassName());
        appendFact(sb, "Session", r.getSessionId());
        appendFact(sb, "UUID", r.getUuid());
        appendFact(sb, "Durée totale mesurée", formatMs(r.getMaxDurationMs()));
        appendFact(sb, "Cause principale (pré-classée)", r.getPrimaryCause());
        appendFact(sb, "Catégorie technique", r.getCategory());
        appendFact(sb, "Fiabilité de la mesure", r.getAnalysisConfidence());
        appendFact(sb, "Avertissement de mesure", r.getMeasurementWarning());
        appendFact(sb, "Périmètre analysé", r.getScopeDescription());
        appendFact(sb, "Résumé client déjà validé", r.getClientSummary());
        appendFact(sb, "Cause racine résumée", r.getRootCauseSummary());
        if (r.getRecommendations() != null && !r.getRecommendations().isEmpty()) {
            sb.append("\n=== PISTES DÉJÀ CALCULÉES (à conserver / clarifier, pas inventer d'autres) ===\n");
            for (String rec : r.getRecommendations()) {
                if (notBlank(rec)) {
                    sb.append("- ").append(rec.trim()).append('\n');
                }
            }
        }
        if (notBlank(r.getNarrativeSummary())) {
            sb.append("\n=== RÉCIT LOCAL DÉTAILLÉ (référence factuelle — enrichis, ne dilue pas) ===\n");
            sb.append(truncate(r.getNarrativeSummary(), 3500)).append('\n');
        }

        if (notBlank(r.getRootSqlQuery())) {
            sb.append("\n=== REQUÊTE SQL RACINE (si pertinent) ===\n");
            appendFact(sb, "Requête", truncate(r.getRootSqlQuery(), 1200));
            appendFact(sb, "Nb de jointures", r.getRootSqlJoinCount() != null ? String.valueOf(r.getRootSqlJoinCount()) : null);
            appendFact(sb, "Tables", r.getRootSqlTables() != null ? String.join(", ", r.getRootSqlTables()) : null);
            appendFact(sb, "Lignes retournées", r.getRootSqlRowCount() != null ? String.valueOf(r.getRootSqlRowCount()) : null);
        }

        if (r.getEvidence() != null && !r.getEvidence().isEmpty()) {
            sb.append("\n=== PREUVES STRUCTURÉES ===\n");
            for (Map.Entry<String, Object> e : r.getEvidence().entrySet()) {
                appendFact(sb, e.getKey(), String.valueOf(e.getValue()));
            }
        }

        List<LatencyTimelineStepDto> steps = r.getTimelineSteps();

        appendPrecomputedAnalysis(sb, r, steps);
        appendKeyEvidence(sb, r);

        if (steps != null && !steps.isEmpty()) {
            sb.append("\n=== RÉPARTITION DU TEMPS (étapes mesurées, triées par durée décroissante) ===\n");
            List<LatencyTimelineStepDto> ordered = steps.stream()
                    .sorted(java.util.Comparator.comparingLong(
                            (LatencyTimelineStepDto s) -> s.getDurationMs() != null ? s.getDurationMs() : 0L)
                            .reversed())
                    .toList();
            int shown = 0;
            for (LatencyTimelineStepDto s : ordered) {
                if (shown++ >= 40) { sb.append("… (autres étapes tronquées)\n"); break; }
                sb.append("- ");
                if (s.isBottleneck()) sb.append("[GOULOT / CAUSE PRINCIPALE] ");
                if (s.isSuspect()) sb.append("[MESURE SUSPECTE] ");
                sb.append(safe(s.getOperationName()));
                if (s.getDurationMs() != null) {
                    sb.append(" — ").append(formatMs(s.getDurationMs()));
                    if (r.getMaxDurationMs() != null && r.getMaxDurationMs() > 0) {
                        long pct = Math.round(100.0 * s.getDurationMs() / r.getMaxDurationMs());
                        sb.append(" (").append(pct).append("% du total)");
                    }
                }
                if (s.getRowCount() != null) sb.append(" — ").append(s.getRowCount()).append(" ligne(s)");
                if (s.getTimestamp() != null) sb.append(" — ").append(s.getTimestamp());
                if (notBlank(s.getSuspectReason())) sb.append(" (").append(s.getSuspectReason()).append(")");
                sb.append("\n");
            }
        }

        sb.append("\n=== VRAIES LIGNES DE LOGS CONCERNÉES (fenêtre de l'opération lente) ===\n");
        String logs = notBlank(r.getChronologicalText())
                ? r.getChronologicalText()
                : "(logs non disponibles)";
        sb.append(logs).append("\n");

        sb.append("\n=== TA MISSION ===\n");
        sb.append("Rédige une explication de NIVEAU EXPERT de cette lenteur, en français, en MARKDOWN, ");
        sb.append("en suivant EXACTEMENT ce plan (garde les titres) :\n\n");
        sb.append("## Synthèse\nUne à deux phrases : qui, quelle opération, combien de temps, et la cause en une ligne. "
                + "La cause DOIT être cohérente avec le VERDICT DÉTERMINISTE et l'étape [GOULOT].\n\n");
        sb.append("## Où est passé le temps ?\nUn tableau markdown | Étape | Durée | Part | avec d'ABORD l'étape goulot "
                + "(la plus longue), puis les autres. N'invente PAS de ligne « temps NON instrumenté » si le verdict "
                + "dit que le goulot EST une étape mesurée (ex. SQL). Cite le trou silencieux seulement s'il est fourni.\n\n");
        sb.append("## Origine de la lenteur\nReproduis le VERDICT DÉTERMINISTE. "
                + "Si le SQL domine, dis clairement que la cause EST la base de données / la requête racine. "
                + "Ne dis JAMAIS « SQL rapide » si une étape SQL/prepareSearchByRoot/Query Select dépasse 30% du total.\n\n");
        sb.append("## Preuve par comparaison\nUNIQUEMENT si des exécutions comparatives sont fournies ; sinon une ligne "
                + "« non mentionné dans les logs analysés ».\n\n");
        sb.append("## Causes probables (par ordre de vraisemblance)\nListe adaptée AU VERDICT (ex. index manquant / "
                + "16 jointures si SQL ; règles/externe/GC seulement si le verdict le dit).\n\n");
        sb.append("## Recommandations\nActions concrètes cohérentes avec la cause (plan d'exécution SQL / index si SQL ; "
                + "instrumentation règles seulement si la cause est les règles).\n\n");
        sb.append("## Réponse rapide\nOù / Quoi / Pourquoi / Qui / Quand.\n\n");
        sb.append("RÈGLES : n'invente AUCUN chiffre. Ne contredis JAMAIS le VERDICT DÉTERMINISTE ni l'étape [GOULOT]. ");
        sb.append("Si une donnée manque, écris « non mentionné dans les logs analysés ».");

        int budget = Math.max(8000, aiProperties.getMaxContextChars());
        return AiContextBudget.apply(sb.toString(), budget);
    }

    /**
     * Bloc « PREUVES CLÉS » : le trou silencieux, le pic mémoire, la bascule de fichiers et la
     * comparaison inter-exécutions. C'est exactement la matière qui distingue une analyse d'expert
     * d'une paraphrase générique.
     */
    private void appendKeyEvidence(StringBuilder sb, LatencyOriginReportDto r) {
        boolean any = r.getSilentGapMs() != null || r.getHeapPeakMo() != null
                || (r.getWindowFiles() != null && r.getWindowFiles().size() > 1)
                || (r.getExecutionSamples() != null && !r.getExecutionSamples().isEmpty());
        if (!any) return;

        sb.append("\n=== PREUVES CLÉS (déterministes — appuie ton analyse dessus) ===\n");

        if (r.getSilentGapMs() != null) {
            sb.append("- ⏸️ TROU SILENCIEUX : ").append(formatMs(r.getSilentGapMs()))
                    .append(" sans AUCUN log entre ").append(safe(r.getSilentGapFrom()))
                    .append(" et ").append(safe(r.getSilentGapTo()))
                    .append(". C'est là que le temps a été perdu (aucune étape n'y est tracée).\n");
        }
        if (r.getHeapPeakMo() != null) {
            sb.append("- 🧠 MÉMOIRE : pic à ~").append(r.getHeapPeakMo()).append(" Mo dans la fenêtre");
            if (r.getHeapPeakMo() >= 3500) {
                sb.append(" (élevé → une pause GC est plausible pendant le trou silencieux)");
            }
            sb.append(".\n");
        }
        if (r.getWindowFiles() != null && r.getWindowFiles().size() > 1) {
            sb.append("- 📂 BASCULE DE FICHIERS : l'opération traverse ")
                    .append(r.getWindowFiles().size()).append(" fichiers (")
                    .append(String.join(", ", r.getWindowFiles()))
                    .append(") → possible attente / bascule de thread.\n");
        }

        List<LatencyExecutionSampleDto> samples = r.getExecutionSamples();
        if (samples != null && !samples.isEmpty()) {
            sb.append("- 🔬 COMPARAISON INTER-EXÉCUTIONS de la MÊME opération (preuve d'intermittence) :\n");
            sb.append("    Heure | SQL(ms) | lignes | règles(ms)\n");
            for (LatencyExecutionSampleDto s : samples) {
                sb.append("    ").append(safe(s.getTime()))
                        .append(" | ").append(s.getSqlMs() != null ? s.getSqlMs() : "?")
                        .append(" | ").append(s.getRowCount() != null ? s.getRowCount() : "?")
                        .append(" | ").append(s.getRulesMs() != null ? s.getRulesMs() : "?")
                        .append(s.isCurrent() ? "   ← exécution analysée (la lente)" : "")
                        .append("\n");
            }
            sb.append("    LECTURE : si le SQL reste stable/rapide alors que « règles » varie énormément, "
                    + "la lenteur ne vient NI des données NI du SQL, mais d'un facteur intermittent du moteur de règles.\n");
        }
    }

    /**
     * Bloc analytique DÉTERMINISTE : verdict SQL / règles / non-instrumenté
     * calculé correctement pour que le LLM ne puisse pas inventer « SQL rapide »
     * quand prepareSearchByRoot a pris 73 s.
     */
    private void appendPrecomputedAnalysis(StringBuilder sb, LatencyOriginReportDto r,
                                           List<LatencyTimelineStepDto> steps) {
        long totalMs = r.getMaxDurationMs() != null ? r.getMaxDurationMs() : 0L;
        if (steps == null || steps.isEmpty() || totalMs <= 0) {
            return;
        }

        LatencyTimelineStepDto bottleneck = steps.stream()
                .filter(LatencyTimelineStepDto::isBottleneck)
                .findFirst()
                .orElseGet(() -> steps.stream()
                        .filter(s -> !s.isSuspect() && s.getDurationMs() != null)
                        .max(java.util.Comparator.comparingLong(LatencyTimelineStepDto::getDurationMs))
                        .orElse(null));
        long bottleneckMs = bottleneck != null && bottleneck.getDurationMs() != null
                ? bottleneck.getDurationMs() : totalMs;
        String bottleneckName = bottleneck != null ? safe(bottleneck.getOperationName()) : "";
        String bottleneckLower = bottleneckName.toLowerCase(java.util.Locale.ROOT);

        long slowestSqlMs = 0L;
        String slowestSqlName = null;
        int sqlCount = 0;
        boolean zeroRow = false;
        long childSumMs = 0L; // sous-étapes hors goulot (pour containers seulement)
        java.util.Map<String, long[]> byOperation = new java.util.LinkedHashMap<>();

        for (LatencyTimelineStepDto s : steps) {
            if (s == null) continue;
            long d = s.getDurationMs() != null ? s.getDurationMs() : 0L;
            String name = safe(s.getOperationName());
            String lower = name.toLowerCase(java.util.Locale.ROOT);

            if (s != bottleneck && !s.isSuspect() && d > 0) {
                childSumMs += d;
            }
            boolean isSql = isSqlStep(lower);
            if (isSql && d > slowestSqlMs) {
                slowestSqlMs = d;
                slowestSqlName = name;
                sqlCount++;
            } else if (isSql) {
                sqlCount++;
            }
            if (s.getRowCount() != null && s.getRowCount() == 0) {
                zeroRow = true;
            }
            if (!name.isBlank()) {
                long[] agg = byOperation.computeIfAbsent(name, k -> new long[2]);
                agg[0]++;
                agg[1] += d;
            }
        }

        boolean bottleneckIsSql = isSqlStep(bottleneckLower)
                || bottleneckLower.contains("preparesearchbyroot")
                || bottleneckLower.contains("query select");
        boolean bottleneckIsContainer = bottleneckLower.contains("running rules")
                || bottleneckLower.contains("fire rules")
                || bottleneckLower.contains("transit task")
                || bottleneckLower.contains("doaction")
                || (bottleneckLower.contains("global search") && !bottleneckIsSql);

        long bottleneckPct = Math.round(100.0 * bottleneckMs / totalMs);

        sb.append("\n=== VERDICT DÉTERMINISTE (OBLIGATOIRE — ne pas contredire) ===\n");
        if (bottleneck != null) {
            appendFact(sb, "Étape goulot (CAUSE PRINCIPALE)",
                    bottleneckName + " — " + formatMs(bottleneckMs) + " (" + bottleneckPct + "% du total)");
        }

        if (bottleneckIsSql && bottleneckPct >= 40) {
            sb.append("- VERDICT : SQL_DOMINATED — la cause EST la requête / base de données.\n");
            sb.append("- INTERDICTION : ne dis PAS « SQL rapide », « pas la base », ni « moteur de règles ».\n");
            if (bottleneck.getRowCount() != null) {
                appendFact(sb, "Lignes retournées par le goulot", String.valueOf(bottleneck.getRowCount()));
            }
            if (r.getRootSqlJoinCount() != null) {
                appendFact(sb, "Jointures SQL", String.valueOf(r.getRootSqlJoinCount()));
            }
        } else if (bottleneckIsContainer && bottleneckMs > childSumMs) {
            long uninstrumentedMs = bottleneckMs - childSumMs;
            long pct = Math.round(100.0 * uninstrumentedMs / bottleneckMs);
            appendFact(sb, "Sous-étapes mesurées dans le goulot", formatMs(childSumMs));
            if (uninstrumentedMs > 0 && pct >= 40) {
                appendFact(sb, "⚠️ TEMPS NON INSTRUMENTÉ dans le goulot",
                        formatMs(uninstrumentedMs) + " — " + pct + " %");
                sb.append("- VERDICT : TEMPS_NON_INSTRUMENTE — cause HORS des sous-étapes tracées "
                        + "(règles / externe / verrou / GC / workflow). PAS le SQL si SQL < 30%.\n");
            }
        } else if (slowestSqlMs > 0 && slowestSqlMs >= 0.40 * totalMs) {
            sb.append("- VERDICT : SQL_DOMINATED — la requête la plus lente (")
                    .append(safe(slowestSqlName)).append(" — ").append(formatMs(slowestSqlMs))
                    .append(") explique la lenteur.\n");
            sb.append("- INTERDICTION : ne dis PAS « SQL rapide » ni « moteur de règles ».\n");
        } else {
            sb.append("- VERDICT : voir l'étape goulot et la catégorie technique ci-dessus.\n");
        }

        if (sqlCount > 0) {
            appendFact(sb, "Requêtes SQL/recherches détectées",
                    sqlCount + " (la plus lente : " + formatMs(slowestSqlMs)
                            + (slowestSqlName != null ? " = " + slowestSqlName : "") + ")");
            if (!bottleneckIsSql && slowestSqlMs < 0.30 * totalMs) {
                sb.append("- Note : le SQL le plus lent est < 30% du total → le SQL n'est probablement PAS la cause.\n");
            }
        }
        if (zeroRow) {
            appendFact(sb, "Signal", "au moins une recherche « 0 row »");
        }

        List<Map.Entry<String, long[]>> repeated = byOperation.entrySet().stream()
                .filter(e -> e.getValue()[0] > 1)
                .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                .limit(5)
                .collect(java.util.stream.Collectors.toList());
        if (!repeated.isEmpty()) {
            sb.append("- Opérations RÉPÉTÉES :\n");
            for (Map.Entry<String, long[]> e : repeated) {
                sb.append("    • ").append(e.getKey())
                        .append(" ×").append(e.getValue()[0])
                        .append(" — total ").append(formatMs(e.getValue()[1])).append("\n");
            }
        }
    }

    private static boolean isSqlStep(String lower) {
        if (lower == null || lower.isBlank()) return false;
        return lower.contains("preparesearchbyroot")
                || lower.contains("query select")
                || lower.contains("[query]")
                || (lower.contains("query") && !lower.contains("running rules"))
                || lower.contains("searchattributeslist")
                || (lower.contains("loadlistchilds") && lower.contains("query"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    private LatencyExplanationResponseDto local(String narrative, String note) {
        LatencyExplanationResponseDto dto = new LatencyExplanationResponseDto();
        dto.setExplanation(narrative != null ? narrative : "");
        dto.setSource("LOCAL");
        dto.setModel("");
        dto.setGeneratedByLlm(false);
        dto.setNote(note);
        return dto;
    }

    private static void appendFact(StringBuilder sb, String label, String value) {
        if (notBlank(value)) sb.append("- ").append(label).append(" : ").append(value.trim()).append("\n");
    }

    private static String formatMs(Long ms) {
        if (ms == null) return null;
        if (ms >= 1000) return String.format("%,.1f s (%d ms)", ms / 1000.0, ms);
        return ms + " ms";
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String truncate(String v, int max) {
        if (v == null) return null;
        return v.length() <= max ? v : v.substring(0, max) + "…";
    }

    private static final String WORKS_SYSTEM_PROMPT = """
            Tu es un analyste PERFORMANCE senior des logs de l'application WORKS (métier portuaire / logistique).
            Ta mission n'est PAS de recopier ou reformuler les logs : tu dois les ANALYSER, en EXTRAIRE la cause
            racine de la lenteur, et l'EXPLIQUER à un client non technique — exactement comme un expert humain le ferait.

            ── CONNAISSANCE DES LOGS WORKS (pour interpréter) ──
            • Format d'une ligne : « horodatage · NIVEAU · utilisateur · idSession · fichierSource · message ».
            • Durées mesurées : « took [N] ms », « took N ms », « took N(ms) », « total time SAVE … ; N(ms) ».
            • Messages « à pipes » : process|filtre|action|idDébut|idFin|in host took [N] ms.
            • Familles d'opérations :
              - Recherche / SQL : searchComposantByRoot, prepareSearchByRoot, loadListChilds, Query: SELECT…
              - Règles : running rules, fire rules (START/END)
              - Workflow / BPM : Transit task, doAction, persist Operation, JbpmAccessor
              - Webservice : WS_*, End SaveOrUpdate WS, took N(ms)
              - Sauvegarde : saveOperations, insertArrayRel, total time SAVE
              - Impression : printArchive, génération document/PDF, printjob (ne pas inventer CUPS sans trace)
              - Rendu : rendering result
              - UI : événements ZKoss lents (si présents)
              - « 0 row » : recherche vide (souvent normal)

            ── MÉTHODE D'ANALYSE (raisonne étape par étape, comme un expert) ──
            1. Lis d'abord le bloc « VERDICT DÉTERMINISTE » — c'est la vérité mesurée. Ne le contredis JAMAIS.
            2. LOCALISER : l'étape [GOULOT / CAUSE PRINCIPALE] est LA cause si elle représente une grande part du total.
            3. ISOLER selon le verdict :
               - VERDICT SQL_DOMINATED → la cause EST la requête SQL / base de données.
                 Exemple : prepareSearchByRoot took [73455] ms = 73,5 s → cause = SQL, PAS les règles.
                 INTERDIT : « SQL rapide », « pas la base », « moteur de règles ».
               - VERDICT RULES_DOMINATED → cause = règles (+ effets de bord éventuels).
                 Si le récit local mentionne MAJ_Insert / Rapprocher Tout / matchall / volume amont :
                 explique que les règles déclenchent des écritures massives sur le volume filtré —
                 PAS un vague « Drools lent ». Cite les chiffres fournis.
               - VERDICT TEMPS_NON_INSTRUMENTE → cause hors sous-étapes (règles / externe / GC / verrou / workflow).
               - Sinon → suis l'étape goulot et la catégorie technique.
            4. PROUVER avec horodatages / lignes de logs.
            5. CONCLURE : cause + % + 1 recommandation cohérente.

            ── RÈGLES ABSOLUES ──
            • Ne contredis JAMAIS le VERDICT DÉTERMINISTE ni l'étape [GOULOT].
            • N'invente aucun chiffre. Réutilise les durées fournies.
            • Si le résumé client / récit local contient déjà des preuves chiffrées (MAJ_Insert, volume, doAction),
              enrichis-les en français clair — ne les remplace pas par un texte générique plus pauvre.
            • Français uniquement, ton clair pour un non-technicien.
            • Si une information manque : « non mentionné dans les logs analysés ».
            """;
}
