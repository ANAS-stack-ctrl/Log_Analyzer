# Module RAG — Guide d'installation et d'utilisation

Ce module ajoute à Log Analyzer une **IA qui répond juste** aux questions sur un import, en ne
donnant au LLM que les **vraies lignes de logs pertinentes** (recherche vectorielle), au lieu de
tronquer tout l'import. C'est la différence entre « le modèle récite » et « le modèle analyse des
preuves réelles ».

## Comment ça marche (en 30 secondes)

1. **Indexation** (une fois par import) : les logs sont découpés en fenêtres, chaque fenêtre est
   transformée en vecteur (`nomic-embed-text`) et stockée dans PostgreSQL avec pgvector.
2. **Question** : ta question est vectorisée, on retrouve par similarité les fenêtres les plus
   proches (+ filtres SQL : erreurs, lenteurs, session).
3. **Réponse ancrée** : on envoie au LLM (`deepseek-r1:8b`) **uniquement** ces extraits réels, avec
   l'ordre strict de citer ses sources et de dire « non présent dans les logs » sinon.

Résultat : une réponse vérifiable (les sources sont renvoyées avec la réponse), donc fiable.

---

## Étape 1 — Modèles Ollama

```bash
ollama pull deepseek-r1:8b       # génération (raisonnement)
ollama pull nomic-embed-text     # embeddings (768 dimensions)
```

## Étape 2 — Installer pgvector sur PostgreSQL

pgvector est une extension de PostgreSQL. Sur Windows, le plus simple est d'installer les binaires
pgvector correspondant à ta version de PostgreSQL (ex. PG 16), puis :

```sql
-- vérifie ta version d'abord
SELECT version();
```

Si tu ne peux pas installer pgvector, dis-le moi : je te fournirai une variante de recherche sans
pgvector (plus lente, mais qui marche).

## Étape 3 — Créer le schéma

```bash
psql -U postgres -d logs_db -f src/main/resources/rag-schema.sql
```

> ⚠️ `vector(768)` correspond à `nomic-embed-text`. Si tu changes de modèle d'embeddings, adapte la
> dimension dans `rag-schema.sql` **et** dans `app.rag.dimension`.

## Étape 4 — Copier les fichiers dans ton projet

Copie l'arborescence `src/main/java/com/caciopee/loganalyzer/rag/` (package `rag` + sous-dossier
`dto`) dans ton projet, au même endroit que tes autres packages. Aucune dépendance Maven à ajouter :
le module utilise `JdbcTemplate` (déjà présent via `spring-boot-starter-data-jpa`) et le `RestClient`
Spring (déjà utilisé par tes services IA).

## Étape 5 — Configuration

Ajoute le contenu de `application-rag.properties` dans ton `application-ollama.properties`, et
assure-toi que le LLM est bien configuré :

```properties
app.ai.enabled=true
app.ai.model=deepseek-r1:8b
app.ai.base-url=http://localhost:11434/v1
app.ai.temperature=0.1

app.rag.enabled=true
app.rag.embedding-model=nomic-embed-text
app.rag.dimension=768
```

## Étape 6 — Lancer

```bash
mvnw spring-boot:run -Dspring-boot.run.profiles=dev,ollama
```

---

## Utilisation (API)

### 1) Indexer un import (à faire une fois)

```bash
curl -X POST "http://localhost:8082/rag/index/129"
# suivre la progression :
curl "http://localhost:8082/rag/index/129/status"
```

Réponse type :
```json
{ "importId": 129, "state": "RUNNING", "totalChunks": 3400, "indexedChunks": 900, "progressPercent": 26 }
```

### 2) Poser une question

```bash
curl -X POST "http://localhost:8082/rag/ask" \
  -H "Content-Type: application/json" \
  -d '{ "importId": 129, "question": "Pourquoi islam.elbassit a eu une lenteur vers 13h37 ?" }'
```

Réponse type :
```json
{
  "answer": "La lenteur vient d'une requête SQL... [S2]. Elle a duré 45331 ms [S1].",
  "mode": "LLM",
  "hint": "Réponse ancrée sur 8 extrait(s) réel(s), via deepseek-r1:8b.",
  "sources": [ { "ref": "S1", "processName": "process.PLANNING_CNT...", "maxDurationMs": 45331, "content": "…vraies lignes…" } ]
}
```

Filtres optionnels dans le corps :
```json
{ "importId": 129, "question": "...", "onlyErrors": true, "minDurationMs": 5000, "sessionId": "1781786192547" }
```

### 3) Supprimer l'index d'un import

```bash
curl -X DELETE "http://localhost:8082/rag/index/129"
```

---

## Étape suivante (frontend)

Côté UI, il restera à ajouter dans l'onglet « Diagnostiquer » :
- un bouton **« Indexer pour l'IA »** (POST `/rag/index/{id}`) + une barre de progression (GET status) ;
- un champ question qui appelle `/rag/ask` et affiche `answer` + les `sources` dépliables (les preuves).

Dis-moi quand le backend tourne, et je te fais le bout de JavaScript à coller dans `employee-ui.js`.

---

## Pourquoi c'est « juste »

L'exactitude ne vient pas du modèle (petit), mais de l'architecture :
1. le LLM ne voit que des **lignes réelles** (pas de troncature aveugle) ;
2. il doit **citer** chaque fait → tu peux vérifier ;
3. il a l'ordre de dire **« non présent »** plutôt que d'inventer ;
4. les **sources sont renvoyées** avec la réponse → transparence totale.

C'est exactement l'argument fort pour ton PFE : *architecture hybride déterministe + RAG ancré avec
garde-fous anti-hallucination*, qui compense un modèle local modeste par une sélection de preuves
intelligente.
