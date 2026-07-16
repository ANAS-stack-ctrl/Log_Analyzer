package com.caciopee.loganalyzer.rag;

import com.caciopee.loganalyzer.config.AiAssistantProperties;
import com.caciopee.loganalyzer.rag.dto.RagAnswerDto;
import com.caciopee.loganalyzer.rag.dto.RagAskRequest;
import com.caciopee.loganalyzer.rag.dto.RagSourceDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Génération ANCRÉE (grounded) : on ne laisse PAS le LLM répondre de tête. On lui
 * donne uniquement les vraies fenêtres de logs retrouvées (les "sources"), et on
 * lui impose de citer ses sources et de dire "non présent dans les logs" sinon.
 * C'est le "G" de RAG, et c'est ce qui rend la réponse juste ET vérifiable.
 */
@Service
public class RagChatService {

    private static final Logger log = LoggerFactory.getLogger(RagChatService.class);

    /** deepseek-r1 renvoie son raisonnement entre <think>…</think> : on le retire. */
    private static final Pattern THINK = Pattern.compile("(?is)<think>.*?</think>");

    private static final String SYSTEM_PROMPT = """
            Tu es un analyste expert des logs de l'application WORKS (métier portuaire / logistique).
            Tu réponds à la question d'un utilisateur en te basant UNIQUEMENT sur les SOURCES fournies
            (des extraits réels de logs, numérotés [S1], [S2], …).

            RÈGLES ABSOLUES (obligatoires) :
            • Utilise EXCLUSIVEMENT les informations présentes dans les SOURCES. N'invente jamais un
              horodatage, un chiffre, un filtre, un utilisateur ou une erreur.
            • Chaque fois que tu affirmes un fait, cite la source entre crochets, ex. [S2].
            • Si la réponse n'est pas dans les sources, dis clairement :
              « Cette information n'est pas présente dans les logs analysés. » — ne devine pas.
            • Réponds en FRANÇAIS, de façon claire et directe : d'abord la réponse en 1-2 phrases,
              puis les détails avec les faits cités.
            • Raisonne comme un expert : relie les événements, identifie les causes, mais toujours
              en t'appuyant sur les lignes réelles citées.
            • Ne recopie pas les logs bruts : explique-les.
            """;

    private final AiAssistantProperties aiProperties;
    private final RagRetrievalService retrievalService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RagChatService(AiAssistantProperties aiProperties,
                          RagRetrievalService retrievalService,
                          ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public RagAnswerDto ask(RagAskRequest request) {
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("Question requise.");
        }
        if (request.getImportId() == null) {
            throw new IllegalArgumentException("importId requis.");
        }

        RagAnswerDto answer = new RagAnswerDto();

        // 1) RETRIEVAL
        List<RagSourceDto> sources = retrievalService.retrieve(request);
        answer.setSources(sources);

        if (sources.isEmpty()) {
            answer.setMode("NO_MATCH");
            answer.setAnswer("Aucun extrait de logs pertinent trouvé pour cette question dans cet import. "
                    + "Vérifie que l'import est bien indexé (bouton « Indexer pour l'IA »), "
                    + "ou reformule ta question.");
            return answer;
        }

        // 2) GENERATION ancrée
        if (!aiProperties.isConfigured()) {
            answer.setMode("LOCAL");
            answer.setAnswer(buildLocalAnswer(sources));
            answer.setHint("Mode local (LLM désactivé). Active app.ai.enabled + Ollama pour une vraie analyse.");
            return answer;
        }

        try {
            String reply = callLlm(request.getQuestion(), sources);
            reply = stripThink(reply).trim();
            answer.setMode("LLM");
            answer.setAnswer(reply);
            answer.setHint("Réponse ancrée sur " + sources.size() + " extrait(s) réel(s), via "
                    + aiProperties.getModel() + ".");
        } catch (Exception e) {
            log.warn("[RAG] Appel LLM échoué : {}", e.getMessage());
            answer.setMode("LOCAL");
            answer.setAnswer(buildLocalAnswer(sources));
            answer.setHint("LLM indisponible (" + e.getMessage() + ") — extraits bruts affichés.");
        }
        return answer;
    }

    private String callLlm(String question, List<RagSourceDto> sources) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", aiProperties.getModel());
        body.put("temperature", aiProperties.getTemperature());
        ArrayNode messages = body.putArray("messages");

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", SYSTEM_PROMPT);

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", buildGroundedPrompt(question, sources));

        String url = aiProperties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

        RestClient.RequestBodySpec spec = restClient.post().uri(url);
        if (aiProperties.hasApiKey()) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey());
        }
        JsonNode resp = spec
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (resp != null && resp.path("choices").isArray() && !resp.path("choices").isEmpty()) {
            String content = resp.path("choices").get(0).path("message").path("content").asText("");
            if (!content.isBlank()) return content;
        }
        return "Le modèle n'a pas renvoyé de réponse exploitable.";
    }

    /** Construit le prompt : question + toutes les sources avec leurs vraies lignes. */
    private String buildGroundedPrompt(String question, List<RagSourceDto> sources) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("QUESTION DE L'UTILISATEUR :\n").append(question.trim()).append("\n\n");
        sb.append("SOURCES (extraits réels de logs — réponds UNIQUEMENT à partir de ceux-ci) :\n\n");

        for (RagSourceDto s : sources) {
            sb.append("### [").append(s.getRef()).append("]");
            if (s.getSessionId() != null) sb.append(" · session ").append(s.getSessionId());
            if (s.getProcessName() != null) sb.append(" · process ").append(s.getProcessName());
            if (s.getFilterCode() != null) sb.append(" · filtre ").append(s.getFilterCode());
            if (s.getUserName() != null) sb.append(" · user ").append(s.getUserName());
            if (s.isHasError()) sb.append(" · ⚠ contient une erreur");
            if (s.getMaxDurationMs() != null && s.getMaxDurationMs() > 0)
                sb.append(" · durée max ").append(s.getMaxDurationMs()).append(" ms");
            if (s.getFirstTs() != null)
                sb.append(" · période ").append(s.getFirstTs()).append(" → ").append(s.getLastTs());
            sb.append("\n");
            sb.append(s.getContent()).append("\n\n");
        }

        sb.append("---\n");
        sb.append("Réponds à la question en français, en citant tes sources [S..]. ");
        sb.append("Si l'information n'est pas dans les sources ci-dessus, dis-le explicitement.");
        return sb.toString();
    }

    /** Sans LLM : on renvoie honnêtement les extraits trouvés (déjà utile). */
    private String buildLocalAnswer(List<RagSourceDto> sources) {
        StringBuilder sb = new StringBuilder();
        sb.append("Extraits de logs les plus pertinents pour ta question :\n\n");
        for (RagSourceDto s : sources) {
            sb.append("**[").append(s.getRef()).append("]**");
            if (s.getProcessName() != null) sb.append(" process ").append(s.getProcessName());
            if (s.getMaxDurationMs() != null && s.getMaxDurationMs() > 0)
                sb.append(" — ").append(s.getMaxDurationMs()).append(" ms");
            if (s.isHasError()) sb.append(" — ⚠ erreur");
            sb.append("\n").append(trim(s.getContent(), 500)).append("\n\n");
        }
        return sb.toString().trim();
    }

    static String stripThink(String text) {
        if (text == null) return "";
        return THINK.matcher(text).replaceAll("").trim();
    }

    private static String trim(String v, int max) {
        if (v == null) return "";
        return v.length() <= max ? v : v.substring(0, max) + "…";
    }
}
