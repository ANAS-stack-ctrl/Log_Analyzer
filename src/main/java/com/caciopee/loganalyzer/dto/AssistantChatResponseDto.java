package com.caciopee.loganalyzer.dto;

public class AssistantChatResponseDto {

    private String reply;
    private String mode;
    private boolean aiConfigured;
    private String hint;

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public boolean isAiConfigured() { return aiConfigured; }
    public void setAiConfigured(boolean aiConfigured) { this.aiConfigured = aiConfigured; }

    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }
}
