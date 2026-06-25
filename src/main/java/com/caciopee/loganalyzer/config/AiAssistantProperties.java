package com.caciopee.loganalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiAssistantProperties {

    private boolean enabled = false;
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private String baseUrl = "https://api.openai.com/v1";
    private double temperature = 0.1;
    private int maxContextChars = 48000;
    private int maxChatContextChars = 36000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxContextChars() { return maxContextChars; }
    public void setMaxContextChars(int maxContextChars) { this.maxContextChars = maxContextChars; }

    public int getMaxChatContextChars() { return maxChatContextChars; }
    public void setMaxChatContextChars(int maxChatContextChars) { this.maxChatContextChars = maxChatContextChars; }

    public boolean isConfigured() {
        return enabled && (hasApiKey() || isLocalOpenAiCompatible());
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Ollama expose une API OpenAI-compatible en local (ex. http://localhost:11434/v1).
     * Dans ce cas, une clé n'est pas nécessaire.
     */
    public boolean isLocalOpenAiCompatible() {
        if (baseUrl == null) return false;
        String u = baseUrl.toLowerCase();
        return enabled && (u.contains("localhost") || u.contains("127.0.0.1"));
    }
}
