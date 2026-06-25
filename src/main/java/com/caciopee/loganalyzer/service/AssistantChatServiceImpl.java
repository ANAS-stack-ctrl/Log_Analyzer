package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.config.AiAssistantProperties;
import com.caciopee.loganalyzer.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Consumer;

@Service
public class AssistantChatServiceImpl implements AssistantChatService {

    private final AiContextBuilderService aiContextBuilderService;
    private final LogGroupAnalysisService logGroupAnalysisService;
    private final AiAssistantProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AssistantChatServiceImpl(AiContextBuilderService aiContextBuilderService,
                                    LogGroupAnalysisService logGroupAnalysisService,
                                    AiAssistantProperties aiProperties,
                                    ObjectMapper objectMapper) {
        this.aiContextBuilderService = aiContextBuilderService;
        this.logGroupAnalysisService = logGroupAnalysisService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public AssistantChatResponseDto chat(AssistantChatRequestDto request) {
        if (request == null || request.getUserMessage() == null || request.getUserMessage().isBlank()) {
            throw new IllegalArgumentException("Message utilisateur requis.");
        }

        AiContextRequestDto ctxReq = new AiContextRequestDto();
        if (request.getImportIds() != null && !request.getImportIds().isEmpty()) {
            ctxReq.setImportIds(request.getImportIds());
        } else if (request.getImportId() != null) {
            ctxReq.setImportIds(List.of(request.getImportId()));
        }
        ctxReq.setGroupBy(request.getGroupBy() != null ? request.getGroupBy() : "sessionId");
        ctxReq.setGroupKey(request.getGroupKey());
        ctxReq.setDateFrom(request.getDateFrom());
        ctxReq.setDateTo(request.getDateTo());
        ctxReq.setFocusQuery(request.getUserMessage());
        ctxReq.setScopeProcess(request.getScopeProcess());
        ctxReq.setScopeAction(request.getScopeAction());
        ctxReq.setScopeFilter(request.getScopeFilter());
        ctxReq.setScopeNodeType(request.getScopeNodeType());
        ctxReq.setScopeNodeLabel(request.getScopeNodeLabel());
        ctxReq.setMaxEvidenceLogs(30);

        AiContextResponseDto context = aiContextBuilderService.buildContext(ctxReq);
        String contextText = context.getContext() != null ? context.getContext() : "";
        GroupAnalysisResponseDto analysis = resolveGroupAnalysis(request);

        AssistantChatResponseDto response = new AssistantChatResponseDto();
        response.setAiConfigured(aiProperties.isConfigured());

        if (aiProperties.isConfigured()) {
            String reply = callOpenAi(contextText, request);
            if (AiResponseSanitizer.looksLikeEnglish(reply)) {
                response.setMode("LOCAL");
                response.setReply(SmartLocalChatReplyBuilder.build(contextText, request.getUserMessage(), analysis)
                        + "\n\n---\n\n_Note : le modèle a répondu en anglais ; réponse guidée française affichée._");
                response.setHint("Repli français (modèle en anglais).");
            } else {
                response.setMode("OPENAI");
                response.setReply(reply);
                response.setHint("Réponse générée par " + aiProperties.getModel() + ".");
            }
        } else {
            response.setMode("LOCAL");
            response.setReply(SmartLocalChatReplyBuilder.build(contextText, request.getUserMessage(), analysis));
            response.setHint("Mode guidé intelligent (sans LLM). Activez app.ai.enabled pour le modèle.");
        }

        return response;
    }

    private GroupAnalysisResponseDto resolveGroupAnalysis(AssistantChatRequestDto request) {
        if (request.getGroupKey() == null || request.getGroupKey().isBlank()) {
            return new GroupAnalysisResponseDto();
        }
        List<Long> importIds = request.getImportIds();
        if ((importIds == null || importIds.isEmpty()) && request.getImportId() != null) {
            importIds = List.of(request.getImportId());
        }
        if (importIds == null || importIds.isEmpty()) {
            return new GroupAnalysisResponseDto();
        }
        GroupAnalysisRequestDto gaReq = new GroupAnalysisRequestDto();
        gaReq.setImportIds(importIds);
        gaReq.setGroupBy(request.getGroupBy() != null ? request.getGroupBy() : "sessionId");
        gaReq.setGroupKey(request.getGroupKey());
        gaReq.setDateFrom(request.getDateFrom());
        gaReq.setDateTo(request.getDateTo());
        return logGroupAnalysisService.analyzeGroup(gaReq);
    }

    private String callOpenAi(String contextText, AssistantChatRequestDto request) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", aiProperties.getModel());
            body.put("temperature", aiProperties.getTemperature());
            ArrayNode messages = body.putArray("messages");

            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", """
                    Tu es un analyste expert de logs WORKS (application métier portuaire/logistique).
                    Tu racontes ce qui s'est passé comme un collègue métier expérimenté, en français uniquement.
                    
                    RÈGLES ABSOLUES :
                    - Base-toi UNIQUEMENT sur le contexte fourni. N'invente jamais d'horodatage, filtre ou erreur.
                    - Rédige EXCLUSIVEMENT en français.
                    - Commence par répondre directement à la question, puis détaille avec des faits.
                    - Cite des horodatages ISO complets quand tu affirmes un fait (sections ERREURS CONSTATÉES, LENTEURS, PREUVES).
                    - Pour les 0 row : utilise la section RECHERCHES SANS RÉSULTAT PAR FILTRE.
                    - Pour les lenteurs : section INCIDENTS DE LATENCE et LENTEURS DÉTECTÉES.
                    - La SYNTHÈSE MÉTIER en tête du contexte est fiable — tu peux t'en appuyer.
                    - Si l'information manque : dis « non mentionné dans les logs analysés ».
                    - Structure : réponse courte, puis détails, puis recommandation si pertinent.
                    """);

            ObjectNode ctxMsg = messages.addObject();
            ctxMsg.put("role", "user");
            ctxMsg.put("content", "Contexte logs:\n\n"
                    + truncateContextForChat(contextText, aiProperties.getMaxChatContextChars()));

            List<ChatMessageDto> history = request.getHistory();
            if (history != null) {
                for (ChatMessageDto h : history) {
                    if (h == null || h.getContent() == null || h.getContent().isBlank()) continue;
                    if ("assistant".equalsIgnoreCase(h.getRole())) continue;
                    ObjectNode m = messages.addObject();
                    m.put("role", "user");
                    m.put("content", truncate(h.getContent(), 1200));
                }
            }

            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", request.getUserMessage().trim()
                    + "\n\n(Réponds en français, uniquement avec les faits du contexte.)");

            String url = aiProperties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

            Consumer<RestClient.RequestHeadersSpec<?>> auth = spec -> {
                if (aiProperties.hasApiKey()) {
                    spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey());
                }
            };

            RestClient.RequestBodySpec spec = restClient.post().uri(url);
            auth.accept(spec);
            JsonNode apiResponse = spec
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (apiResponse != null
                    && apiResponse.path("choices").isArray()
                    && !apiResponse.path("choices").isEmpty()) {
                String content = apiResponse.path("choices").get(0).path("message").path("content").asText("");
                if (!content.isBlank()) {
                    return AiResponseSanitizer.sanitize(content.trim());
                }
            }
            return "Le modèle n'a pas renvoyé de réponse exploitable.";
        } catch (Exception e) {
            return SmartLocalChatReplyBuilder.build(contextText, request.getUserMessage(),
                    resolveGroupAnalysis(request))
                    + "\n\n_Erreur appel IA : " + e.getMessage() + "_";
        }
    }

    private String truncateContextForChat(String contextText, int max) {
        if (contextText == null) return "";
        int anchorIdx = indexOfAny(contextText,
                "FOCUS SUR LA QUESTION",
                "ERREURS CONSTATÉES",
                "5a. ERREURS EXACTES",
                "LENTEURS DÉTECTÉES",
                "5b. LENTEURS",
                "INCIDENTS DE LATENCE");
        if (anchorIdx < 0) {
            return AiContextBudget.apply(contextText, max);
        }
        String tail = contextText.substring(anchorIdx);
        if (tail.length() >= max) {
            return AiContextBudget.apply(tail, max);
        }
        int headBudget = max - tail.length();
        String head = contextText.substring(0, anchorIdx);
        return AiContextBudget.apply(
                truncate(head, Math.max(1200, headBudget))
                        + "\n… (début tronqué — sections prioritaires ci-dessous)\n\n"
                        + tail,
                max);
    }

    private static int indexOfAny(String text, String... needles) {
        int best = -1;
        for (String n : needles) {
            int i = text.indexOf(n);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
