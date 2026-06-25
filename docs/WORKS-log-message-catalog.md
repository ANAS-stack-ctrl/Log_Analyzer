# Catalogue des structures de messages WORKS

Document de référence pour **connaître 100 % des familles de messages** présentes dans vos logs,
et pour chaque famille : **ce qu’on peut extraire** et **comment l’ajouter** quand une forme nouvelle apparaît.

| Champ | Valeur |
|-------|--------|
| Version | 1.1 |
| Dernière analyse | 2026-05-21 (mining Desktop Metier/LOGS) |
| Familles | 68 — libellés en français, ordre spécifique → générique |
| Extracteur code | `LogPatternExtractor.java` |
| Catalogue machine | `src/main/resources/works-message-families.json` |
| Classification | `WorksMessageCatalogService.java` |
| Sortie graphe | `GraphExtraction` via `extractGraph(LogEntry)` |

---

## Datasets de référence (pas besoin de les recoller dans le chat)

| Dataset | Chemin local | Fichiers | Rôle |
|---------|----------------|----------|------|
| **Métier** | `C:\Users\Asus\Desktop\Metier` | 11 × `works-WORKS24-*.log` | Écrans, `processFilter`, process métier `process.*` |
| **LOGS_2** | `C:\Users\Asus\Desktop\LOGS_2` | ~967 fichiers | Volume complet : `works-*`, `process-*`, `perfs-*`, `saveLoadLogFile-*`, `memPerfs-*`, `server.log` |
| **Tomcat / structure** | `C:\Users\Asus\Desktop\LOGS` | 31 × `works-tomcat*.log` | Structuration `structureData`, champs `works=`, `put key`, etc. |

**Vous n’avez pas besoin de redonner les fichiers** tant que ces dossiers restent accessibles.
Indiquez seulement un **nouveau chemin** si vous déplacez ou ajoutez un dataset.

Format commun (ligne pipe `|`) :

```text
timestamp | sessionId | level | user | logger | processName(colonne) | … | message | logCode | IP | … | serveur | version | correlationId
```

---

## Comment ajouter une structure nouvelle

1. **Repérer** une ligne représentative (message + colonne `processName`).
2. **Ajouter une entrée** dans ce fichier (section adaptée) avec :
   - `id` unique (ex. `MET-012`)
   - `signature` (mot-clé ou regex courte)
   - `exemple`
   - `champs_extractables`
   - `mapping_graphe` (process / task / action / filter / object / signaux)
3. **Ajouter** la même entrée dans `works-message-families.json` (regex + champs extractables).
4. **Implémenter** dans `LogPatternExtractor.java` (constante `Pattern` + branche dans `extract` / `extractGraph`).
5. **Tester** dans `LogPatternExtractorTest.java` avec la ligne réelle.
6. **Vérifier** via `GET /explorer/import-quality/{importId}` : la ligne ne doit plus apparaître dans `unknownSamples`.

Statuts utilisés dans ce catalogue :

| Statut | Signification |
|--------|----------------|
| `OK` | Couvert par `LogPatternExtractor` + tests ou scan Metier |
| `PARTIEL` | Extrait partiellement ou hors graphe volontairement |
| `A_FAIRE` | Vu dans les logs, pas encore dans l’extracteur |

---

## Niveau 1 — Types de process (colonne `processName`)

| ID | Colonne process | Exemple colonne | Process graphe | Statut |
|----|-----------------|-----------------|----------------|--------|
| L1-PF | `processFilter` | `processFilter-0-ZRE22-0-LOAD` | `processFilter` | OK |
| L1-PW | `processWebService` | `processWebService-0-WS_SEARCH_REG_TRACABILITE-0-BEFORE_LOAD` | `processWebService` | OK |
| L1-PRR | `processRunRules` | `processRunRules-0-DATA_LOAD_TASK-0-SAVE` | `processRunRules` | OK |
| L1-PD | Process métier | `process.SUPERVISION ORDRE TRACTION---` | nom après `process.` | OK |
| L1-PIPE | Segments pipe dans message | `running rules\|processFilter\|ZRE22\|LOAD\|…` | déduit du message | OK |

**Colonnes hyphen** : `processType-0-<segment2>-0-<action>`  
→ segment2 = souvent **task** ou **filter** (heuristique `looksLikeFilterCode` / `WS_*`).

---

## Niveau 2 — Familles de messages (structures répétitives)

### A. Métier — Filtres et recherche (`processFilter`)

| ID | Signature / déclencheur | Exemple message | Champs extractables | Mapping graphe | Statut |
|----|-------------------------|-----------------|---------------------|----------------|--------|
| MET-001 | `filter code [` | `filter code [TRCEXP_BYAMPE] uuid [-5387…]` | filter, uuid | filter, uuid | OK |
| MET-002 | `fetching filter [` | `fetching filter [TRCEXP_BYAMPE] bean: HashMap…` | filter | filter | OK |
| MET-003 | `className [` | `className [ServicePortuaire]` | object (className simplifié) | object | OK |
| MET-004 | `className` sans `[]` | `className ServicePortuaireServicePortuaire` (doublon collé) | object | object | OK |
| MET-005 | `[N] row fetched` / `0 row` | `[0] row fetched`, `0 row` | rowCount, zeroResult | zeroResult | OK |
| MET-006 | `took [N] ms` / `took N(ms)` | `took [892] ms`, `took 2948(ms)` | durationMs | durationMs | OK |
| MET-007 | `searchAttributesList` + filter | `searchAttributesList filter code [searchComposant_…\|crudLayout…]` | filter, uuid, durationMs | filter, uuid | OK |
| MET-008 | `processName [` / `taskName [` / `actionName [` | `START fire rules … processName [processFilter] taskName [TRCEXP] actionName [LOAD]` | process, task, action, filter?, uuid | tous | OK |
| MET-009 | `running rules\|` (pipe) | `running rules\|processFilter\|ZRE22\|LOAD\|null\|0\|in host took [22] ms` | process, task, action, durationMs | tous | OK |
| MET-010 | `memory usage (Mo)` | `memory usage (Mo) [prepareSearchByRoot 1 row - className … filter code [Rule_….java]] 2477` | filter, object, memoryMo, rowCount | filter, object, memoryMo | OK |
| MET-011 | `searchComposantByRoot` / fin filtre | `searchComposantByRoot end execute filter code […]` | filter, uuid, zeroResult | filter, uuid | OK |
| MET-012 | `prepareSearchByRoot` / `Query Select` | `prepareSearchByRoot [Query Select] took [1] ms\|0 row \| className …` | filter, object, durationMs, zeroResult | idem | OK |

**Occurrences Metier (scan 2026-05-21)** : `filter_code` ~77k, `uuid_br` ~98k, `P_dot` ~66k, `PF_colon` ~18k, `took_ms` ~48k, `searchAttributes` ~8.6k.

---

### B. Métier — Process écran (`process.*`)

| ID | Signature | Exemple | Champs extractables | Mapping graphe | Statut |
|----|-----------|---------|---------------------|----------------|--------|
| MET-020 | Colonne `process.NOM---` | `process.SUPERVISION ORDRE TRACTION---` | process, action (dernier segment) | process, action | OK |
| MET-021 | `no rule found` + `taskName=` / `transition=` | `no rule found for this params : {taskName=Valider, transition=BEFORE_LOAD}` | task, action, uuid, warning | task, action, warning | OK |
| MET-022 | `doAction for` + `actionName :` + `transition :` | `doAction for - actionName : Start Supervision… - transition : start.process… took 123 ms` | process, action, durationMs | process, action, durationMs | OK |
| MET-023 | `actionName [` dans colonne longue | `process.SUPERVISION…-516580298-StartProcess-0-Start` | process, action | process, action | OK |
| MET-024 | `uuid [` seul | `uuid [4723395462643928763] …` | uuid | uuid | OK |
| MET-025 | `transactionId` | `transactionId (…)` ou `transactionId […]` | transactionId | transactionId | OK |
| MET-026 | `thread name [` | `thread name [http-nio-8080-exec-12]` | threadName | (hors graphe, contexte IA) | OK |
| MET-027 | Paires métier `noDUM=`, `refDemande=`, etc. | `noDUM = [12345]` | businessKey, businessValue | object (clé=valeur) | OK |
| MET-028 | Paramètres requête `wv…=`, `f…=` | `wvParam=[valeur]` | queryParam name/value | (contexte IA) | OK |
| MET-029 | Checkpoint `ENTREE_` / `SORTIE_` | `trCheckPoint: ENTREE_SAISIE` | checkpoint | (contexte IA / timeline) | OK |
| MET-030 | `Trigger… was fired` | triggers planifiés (saveLoadLogFile aussi) | triggerName | (hors graphe principal) | PARTIEL |

---

### C. Webservices (`processWebService` + fichiers `process-*.log`)

| ID | Signature | Exemple | Champs extractables | Mapping graphe | Statut |
|----|-----------|---------|---------------------|----------------|--------|
| WS-001 | Colonne `processWebService-0-WS_*-0-*` | `processWebService-0-WS_FIND_TRACABILITE-0-LOAD` | process, task (WS_*), action | tous | OK |
| WS-002 | `Warning : aucune règle` | même famille que MET-021 | warning, process, task, action | warning | OK |
| WS-003 | `Start Filtre` / `End Filtre` | `Start Filtre WS_SEARCH_REG` | filter, task (WS dans libellé) | filter, task | OK |
| WS-004 | `Start WS_*` (workflow) | `Start WS_SEARCH_REG_TRACABILITE` | task | task | OK |
| WS-005 | `filter code [Rule_….java]` | règle Java nommée | filter | filter | OK |
| WS-006 | `------------------- End … took N(ms)` | fin de filtre WS | filter, durationMs | filter, durationMs | OK |

Fichiers **`process-WORKS24-*.log`** dans LOGS_2 : même familles WS-003/004/006, souvent sans colonne `processWebService` mais message identique.

---

### D. Tomcat / structuration (`works-tomcat*.log`, `processRunRules`)

| ID | Signature | Exemple | Champs extractables | Mapping graphe | Statut |
|----|-----------|---------|---------------------|----------------|--------|
| TOM-001 | `StructureDataInterface.<méthode>` | `…StructureDataInterface.structureData] …` | filter = `structureData.<méthode>` | filter | OK |
| TOM-002 | `START [StrctureDatasInterface….<méthode>]` | `START […getDataByType]` | filter, action (= méthode) | filter, action | OK |
| TOM-003 | `works = [` + `interface = [` | `works = [compteRemboursement.numeroCompte] interface = [numcompte]` | object (works), interface | object | OK |
| TOM-004 | `put key [` + value | `put key [compteRemboursement.numeroCompte] value [42501486352]` | object (= key), valeur (contexte) | object | OK |
| TOM-005 | `field [` | `field [compteRemboursement.numeroCompte] interface = [numcompte]` | object | object | OK |
| TOM-006 | `{fieldClassCode} = [` | `{fieldClassCode} = [creance$compteRemboursement$numeroCompte]` | object (code métier) | object | OK |
| TOM-007 | `relation [` | relations entre entités | object | object | OK |
| TOM-008 | `new interface object` | création objet interface | businessObjectKey | object | PARTIEL |
| TOM-009 | `the value of the interface`, `parsing the value` | étapes intermédiaires | (texte libre) | — | PARTIEL |
| TOM-010 | `saveProcessContent` / `insertArray` | persistance | save=true | save | OK |

Logger typique : `structureData.StructureDataInterface`, colonne `processRunRules-0-DATA_LOAD_TASK-0-SAVE`.

---

### E. Fichiers LOGS_2 — hors graphe workflow (volontaire)

| ID | Type fichier | Raison | Statut |
|----|--------------|--------|--------|
| EXT-001 | `memPerfs-*.log` | Mémoire JVM serveur, pas de process métier | Hors scope graphe |
| EXT-002 | `server.log` | WildFly/Tomcat infra, format différent | A_FAIRE si besoin infra |
| EXT-003 | `perfs-*.log` | Souvent `memory usage` seul | PARTIEL (memoryMo) |
| EXT-004 | `saveLoadLogFile-*.log` | Triggers / batch | PARTIEL (triggers) |

---

## Table de correspondance — champs → graphe / IA

| Champ extrait (`ExtractedLogContext`) | Nœud / attribut graphe | Usage LLM futur |
|---------------------------------------|----------------------|-----------------|
| processName | PROCESS | Contexte session |
| taskName | TASK | Étape workflow |
| actionName / transition | ACTION | Transition |
| filterCode | FILTER | Requête / règle / étape structure |
| className / works / put key | OBJECT | Entité métier |
| uuid | lien session technique | Regroupement |
| transactionId | lien UUID transaction | Regroupement métier |
| durationMs, rowCount, memoryMo | métriques arêtes | Perf / lenteur |
| warning, zeroResult, error, save | flags | Incidents |
| businessKey/Value, queryParam | texte structuré IA | Fiche diagnostic |
| checkpoint, trigger | timeline | Parcours utilisateur |

---

## Structures vues dans les logs mais à surveiller (écarts possibles)

Ajouter ici toute ligne qui **ne matche aucune famille** lors d’un audit import :

| Date | Import / fichier | Extrait message | Action |
|------|------------------|-----------------|--------|
| | | | |

Pour l’audit automatique : endpoint `GET /explorer/import-quality/{importId}` et fiche diagnostic.

---

## Prochaine évolution (alignée avec votre idée L2)

Ce catalogue décrit le **niveau message** (L2). Pour le graphe hiérarchique :

1. **L1** : process → task → action → filter → object (déjà en place).
2. **L2** : sous-nœuds par **famille** (MET-001, TOM-004, …) rattachés au filter/object parent — à matérialiser quand vous passerez à la phase « sous-extractions + LLM ».

Ce fichier Markdown reste la **documentation** ; `works-message-families.json` est la **source machine** chargée au démarrage pour l’audit d’import.

---

## Références code

| Fichier | Rôle |
|---------|------|
| `LogPatternExtractor.java` | Tous les `Pattern` et résolution graphe |
| `LogPatternExtractorTest.java` | 16 exemples réels (Metier + WS + Tomcat) |
| `WorkflowGraphServiceImpl.java` | Construction nœuds/arêtes |
| `ImportExtractionAuditService.java` | Taux de couverture par import |
