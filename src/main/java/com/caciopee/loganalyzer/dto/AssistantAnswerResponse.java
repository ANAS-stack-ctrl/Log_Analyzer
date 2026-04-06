package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class AssistantAnswerResponse {

    private String question;
    private String detectedIntent;

    // Réponse complète, lisible directement dans le frontend actuel
    private String answer;

    // Réponse courte et directe
    private String shortAnswer;

    // LOW / MEDIUM / HIGH / CRITICAL
    private String severity;

    // LOW / MEDIUM / HIGH
    private String confidence;

    // Constats importants
    private List<String> findings = new ArrayList<>();

    // Recommandations d’action
    private List<String> recommendations = new ArrayList<>();

    // Suggestions de questions suivantes
    private List<String> hints = new ArrayList<>();

    public AssistantAnswerResponse() {
    }

    public AssistantAnswerResponse(String question,
                                   String detectedIntent,
                                   String answer,
                                   List<String> hints) {
        this.question = question;
        this.detectedIntent = detectedIntent;
        this.answer = answer;
        this.hints = hints;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getDetectedIntent() {
        return detectedIntent;
    }

    public void setDetectedIntent(String detectedIntent) {
        this.detectedIntent = detectedIntent;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getShortAnswer() {
        return shortAnswer;
    }

    public void setShortAnswer(String shortAnswer) {
        this.shortAnswer = shortAnswer;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public List<String> getFindings() {
        return findings;
    }

    public void setFindings(List<String> findings) {
        this.findings = findings;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    public List<String> getHints() {
        return hints;
    }

    public void setHints(List<String> hints) {
        this.hints = hints;
    }
}