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

@Service
public class AssistantFullAnalysisServiceImpl implements AssistantFullAnalysisService {

    private final SessionDiagnosticService sessionDiagnosticService;
    private final AiAssistantProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AssistantFullAnalysisServiceImpl(SessionDiagnosticService sessionDiagnosticService,
                                           AiAssistantProperties aiProperties,
                                           ObjectMapper objectMapper) {
        this.sessionDiagnosticService = sessionDiagnosticService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public AssistantFullAnalysisResponseDto fullAnalysis(GroupAnalysisRequestDto request) {
        if (request == null || request.getImportIds() == null || request.getImportIds().isEmpty()) {
            throw new IllegalArgumentException("Au moins un importId est requis.");
        }
        if (request.getGroupKey() == null || request.getGroupKey().isBlank()) {
            throw new IllegalArgumentException("groupKey requis (ex. session technique).");
        }

        SessionDiagnosticResponseDto diag = sessionDiagnosticService.buildDiagnostic(request);

        AssistantFullAnalysisResponseDto res = new AssistantFullAnalysisResponseDto();
        res.setAiConfigured(aiProperties.isConfigured());

        String localReport = buildLocalReport(diag);

        if (!aiProperties.isConfigured() || Boolean.TRUE.equals(request.getForceLocal())) {
            res.setMode("LOCAL");
            res.setHint(Boolean.TRUE.equals(request.getForceLocal())
                    ? "Rapport guidé local (sans appel au modèle IA)."
                    : "Mode guidé local : activez app.ai.enabled pour l'analyse par modèle.");
            res.setReportMarkdown(localReport);
            return res;
        }

        res.setMode("OPENAI");
        String aiReport = callOpenAiReport(diag, localReport);
        GroupAnalysisResponseDto ga = diag.getGroupAnalysis() != null ? diag.getGroupAnalysis() : new GroupAnalysisResponseDto();
        long total = ga.getTotalLogs() != null ? ga.getTotalLogs() : 0;
        long zero = ga.getZeroResultCount() != null ? ga.getZeroResultCount() : 0;
        if (AiResponseSanitizer.looksLikeEnglish(aiReport) || AiResponseSanitizer.looksLikeGenericAdvice(aiReport, total, zero)) {
            res.setHint("Rapport guidé affiché — l'IA a produit une analyse trop générique ou hors périmètre.");
            res.setReportMarkdown(localReport);
        } else {
            res.setHint("Rapport guidé + complément IA (" + aiProperties.getModel() + ").");
            res.setReportMarkdown(localReport + "\n\n---\n\n## Complément IA\n\n" + aiReport);
        }
        return res;
    }

    private String callOpenAiReport(SessionDiagnosticResponseDto diag, String localReport) {
        try {
            String dossier = AiInvestigationDossierBuilder.build(diag);
            int maxChars = aiProperties.getMaxContextChars();

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", aiProperties.getModel());
            body.put("temperature", aiProperties.getTemperature());
            ArrayNode messages = body.putArray("messages");

            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", """
                    Tu es un analyste senior de logs WORKS.
                    Tu rédiges EXCLUSIVEMENT en français, pour un employé non développeur.
                    
                    Le dossier contient :
                    - Section A/B : rapport guidé déjà validé par le moteur (source de vérité pour les chiffres).
                    - Sections suivantes : preuves et détails techniques.
                    
                    Ta mission : RACONTER ce qui s'est passé comme un humain qui a lu tous les logs,
                    en complétant et nuançant le rapport guidé — sans le contredire.
                    
                    Contraintes :
                    - Ne jamais inventer d'horodatage, filtre, processus ou erreur absents du dossier.
                    - Citer 5 à 10 horodatages ISO pour les faits importants (section G).
                    - Mentionner explicitement ce que tu as analysé (volume, période, processus dominants, 0 row, latences).
                    - Distinguer certitudes et hypothèses (« probablement », « à confirmer avec le métier »).
                    
                    Format Markdown obligatoire :
                    ## Résumé exécutif
                    ## Ce qui s'est passé (récit chronologique)
                    ## Signaux et problèmes détectés
                    ## Causes probables (avec niveau de certitude)
                    ## Actions recommandées
                    ## Preuves citées (extraits de logs)
                    """);

            ObjectNode guided = messages.addObject();
            guided.put("role", "user");
            guided.put("content", "RAPPORT GUIDÉ DÉJÀ VALIDÉ :\n\n" + truncate(localReport, 12000));

            ObjectNode ctx = messages.addObject();
            ctx.put("role", "user");
            ctx.put("content", "DOSSIER COMPLET :\n\n" + truncate(dossier, maxChars));

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

            String content = apiResponse != null
                    ? apiResponse.path("choices").path(0).path("message").path("content").asText("")
                    : "";
            if (content != null && !content.isBlank()) {
                return AiResponseSanitizer.sanitize(content.trim());
            }
            return localReport + "\n\n_Le modèle n'a pas renvoyé de rapport exploitable._";
        } catch (Exception e) {
            return localReport + "\n\n_Erreur lors de l'appel IA : " + e.getMessage() + "_";
        }
    }

    private String buildLocalReport(SessionDiagnosticResponseDto diag) {
        List<IncidentCandidateDto> incidents = diag.getRelatedIncidents() != null
                ? diag.getRelatedIncidents() : List.of();
        GroupAnalysisResponseDto ga = diag.getGroupAnalysis() != null
                ? diag.getGroupAnalysis() : new GroupAnalysisResponseDto();
        return EmployeeDiagnosticReportBuilder.buildMarkdownReport(ga, incidents,
                diag.getImportQuality());
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
