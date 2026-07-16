package com.caciopee.loganalyzer.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Client d'embeddings. Transforme un texte en vecteur numérique via l'API
 * compatible OpenAI d'Ollama ({@code POST /v1/embeddings}).
 *
 * <p>Un embedding = une liste de nombres qui capture le "sens" d'un texte.
 * Deux textes proches en sens ont des vecteurs proches. C'est ce qui permet
 * de retrouver les logs pertinents pour une question.
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    private final RagProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public EmbeddingClient(RagProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    /** Embedding d'un document (log) à indexer. */
    public float[] embedDocument(String text) {
        return embed(props.getDocumentPrefix() + safe(text));
    }

    /** Embedding d'une question utilisateur. */
    public float[] embedQuery(String text) {
        return embed(props.getQueryPrefix() + safe(text));
    }

    /**
     * Appelle l'API d'embeddings et renvoie le vecteur. Lève une exception claire
     * si la dimension ne correspond pas à la config (erreur de configuration fréquente).
     */
    public float[] embed(String text) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", props.getEmbeddingModel());
            body.put("input", text);

            String url = props.getEmbeddingBaseUrl().replaceAll("/+$", "") + "/embeddings";

            RestClient.RequestBodySpec spec = restClient.post().uri(url);
            if (props.hasEmbeddingApiKey()) {
                spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getEmbeddingApiKey());
            }

            JsonNode resp = spec
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            float[] vector = parseVector(resp);
            if (vector.length != props.getDimension()) {
                throw new IllegalStateException(
                        "Dimension d'embedding = " + vector.length + " mais app.rag.dimension = "
                                + props.getDimension() + ". Corrige la config ET la colonne vector(N) "
                                + "dans rag-schema.sql pour qu'elles correspondent au modèle "
                                + props.getEmbeddingModel() + ".");
            }
            return vector;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Échec de l'appel d'embeddings : " + e.getMessage(), e);
        }
    }

    /**
     * Gère les deux formats de réponse possibles :
     *  - OpenAI-compatible : { "data": [ { "embedding": [...] } ] }
     *  - Ollama natif      : { "embedding": [...] }
     */
    private float[] parseVector(JsonNode resp) {
        if (resp == null) {
            throw new IllegalStateException("Réponse d'embeddings vide.");
        }
        JsonNode arr = null;
        if (resp.path("data").isArray() && !resp.path("data").isEmpty()) {
            arr = resp.path("data").get(0).path("embedding");
        } else if (resp.path("embedding").isArray()) {
            arr = resp.path("embedding");
        }
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            throw new IllegalStateException("Aucun vecteur d'embedding dans la réponse : " + resp);
        }
        float[] out = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = (float) arr.get(i).asDouble();
        }
        return out;
    }

    /** Convertit un vecteur en littéral pgvector : "[0.1,0.2,...]" */
    public static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /** Embeddings en lot (séquentiel — Ollama traite une requête à la fois sur petit GPU). */
    public List<float[]> embedDocuments(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String t : texts) {
            out.add(embedDocument(t));
        }
        return out;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
