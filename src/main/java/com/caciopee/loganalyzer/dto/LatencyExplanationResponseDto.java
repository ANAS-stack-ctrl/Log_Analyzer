package com.caciopee.loganalyzer.dto;

/**
 * Réponse de l'explication de latence générée (idéalement) par un LLM.
 *
 * <p>Le texte {@link #explanation} remplace la section « Explication pas à pas » du panneau
 * « Origine de la lenteur ». Quand aucun LLM n'est configuré (ou en cas d'erreur), le champ
 * {@code source} vaut {@code LOCAL} et {@code explanation} contient le récit règle-based existant :
 * l'application n'échoue jamais.</p>
 */
public class LatencyExplanationResponseDto {

    /** Texte final présenté au client (généré par le LLM ou repli règle-based). */
    private String explanation;

    /** Origine du texte : {@code OLLAMA}, {@code OPENAI} ou {@code LOCAL} (repli sans LLM). */
    private String source;

    /** Modèle utilisé (ex. qwen3:8b, gpt-4o-mini) — vide en mode LOCAL. */
    private String model;

    /** {@code true} si le texte a réellement été produit par un LLM. */
    private boolean generatedByLlm;

    /** Message d'information optionnel (ex. cause du repli local). */
    private String note;

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public boolean isGeneratedByLlm() { return generatedByLlm; }
    public void setGeneratedByLlm(boolean generatedByLlm) { this.generatedByLlm = generatedByLlm; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
