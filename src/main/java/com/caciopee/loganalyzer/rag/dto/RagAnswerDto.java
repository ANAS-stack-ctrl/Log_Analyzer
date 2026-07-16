package com.caciopee.loganalyzer.rag.dto;

import java.util.ArrayList;
import java.util.List;

/** Réponse du chat RAG : le texte + les sources (preuves) + le mode. */
public class RagAnswerDto {

    private String answer;
    /** LLM | LOCAL | NOT_INDEXED | NO_MATCH */
    private String mode;
    private String hint;
    private List<RagSourceDto> sources = new ArrayList<>();

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }
    public List<RagSourceDto> getSources() { return sources; }
    public void setSources(List<RagSourceDto> sources) { this.sources = sources; }
}
