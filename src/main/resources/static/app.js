document.addEventListener("DOMContentLoaded", () => {
    const loginForm = document.getElementById("loginForm");
    const logoutBtn = document.getElementById("logoutBtn");
    const uploadBtn = document.getElementById("uploadBtn");
    const humanBtn = document.getElementById("humanBtn");
    const v2Btn = document.getElementById("v2Btn");
    const summaryBtn = document.getElementById("summaryBtn");
    const storyBtn = document.getElementById("storyBtn");
    const rawBtn = document.getElementById("rawBtn");
    const clearAnalysisBtn = document.getElementById("clearAnalysisBtn");
    const fillImportIdBtn = document.getElementById("fillImportIdBtn");
    const loadLogsBtn = document.getElementById("loadLogsBtn");
    const loadErrorLogsBtn = document.getElementById("loadErrorLogsBtn");
    const clearLogsBtn = document.getElementById("clearLogsBtn");

    loginForm?.addEventListener("submit", onLoginSubmit);
    logoutBtn?.addEventListener("click", onLogoutClick);
    uploadBtn?.addEventListener("click", onUploadClick);
    humanBtn?.addEventListener("click", onHumanClick);
    v2Btn?.addEventListener("click", onV2Click);
    summaryBtn?.addEventListener("click", onSummaryClick);
    storyBtn?.addEventListener("click", onStoryClick);
    rawBtn?.addEventListener("click", onRawClick);
    clearAnalysisBtn?.addEventListener("click", onClearAnalysisClick);
    fillImportIdBtn?.addEventListener("click", onFillImportIdClick);
    loadLogsBtn?.addEventListener("click", onLoadLogsClick);
    loadErrorLogsBtn?.addEventListener("click", onLoadErrorLogsClick);
    clearLogsBtn?.addEventListener("click", onClearLogsClick);

    initializeApp().catch(err => {
        showMessage(err.message || "Erreur d'initialisation.", "error");
    });
});

let lastUploadResponse = null;

async function initializeApp() {
    if (!getAccessToken()) {
        showLoggedOutUI();
        return;
    }

    try {
        await loadCurrentUser();
        showLoggedInUI();
        showMessage("Session active.", "success");
    } catch (e) {
        clearTokens();
        showLoggedOutUI();
        showMessage(e.message || "Session invalide.", "error");
    }
}

async function onLoginSubmit(event) {
    event.preventDefault();

    const username = document.getElementById("username")?.value?.trim();
    const password = document.getElementById("password")?.value || "";

    if (!username || !password) {
        showMessage("Veuillez remplir le nom d’utilisateur et le mot de passe.", "error");
        return;
    }

    try {
        showMessage("Connexion en cours...", "info");
        await login(username, password);
        await loadCurrentUser();
        showLoggedInUI();
        showMessage("Connexion réussie.", "success");
    } catch (e) {
        showLoggedOutUI();
        showMessage(e.message || "Erreur de connexion.", "error");
    }
}

async function onLogoutClick() {
    try {
        await logout();
    } catch (_) {
    }

    clearTokens();
    showLoggedOutUI();
    resetProtectedSections();
    showMessage("Déconnexion effectuée.", "info");
}

async function onUploadClick() {
    const input = document.getElementById("logFiles");
    const files = input?.files;

    if (!files || files.length === 0) {
        showMessage("Veuillez sélectionner au moins un fichier.", "error");
        return;
    }

    try {
        showMessage("Upload en cours...", "info");
        const result = await uploadLogs(files);
        lastUploadResponse = result;

        const uploadResult = document.getElementById("uploadResult");
        if (uploadResult) {
            uploadResult.textContent = JSON.stringify(result, null, 2);
        }

        const extractedImportId = extractLatestImportId(result);
        if (extractedImportId) {
            const importIdInput = document.getElementById("importIdInput");
            const logFilterImportId = document.getElementById("logFilterImportId");

            if (importIdInput && !importIdInput.value.trim()) {
                importIdInput.value = String(extractedImportId);
            }

            if (logFilterImportId && !logFilterImportId.value.trim()) {
                logFilterImportId.value = String(extractedImportId);
            }
        }

        showMessage("Upload terminé avec succès.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur lors de l'upload.", "error");
    }
}

async function onHumanClick() {
    const importId = getImportIdOrThrow();

    try {
        showMessage("Explication globale en cours...", "info");
        const result = await fetchHumanExplanation(importId);
        renderHumanResult(result);
        showMessage("Explication globale chargée.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur lors de l’explication globale.", "error");
    }
}

async function onV2Click() {
    const importId = getImportIdOrThrow();

    try {
        showMessage("Analyse V2 compréhensible en cours...", "info");
        const result = await fetchWorkflowAnalysisV2(importId);
        renderV2Result(result);
        showMessage("Analyse V2 chargée.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur lors de l’analyse V2.", "error");
    }
}

async function onSummaryClick() {
    const importId = getImportIdOrThrow();

    try {
        showMessage("Résumé technique en cours...", "info");
        const result = await fetchWorkflowSummary(importId);
        renderRawResult(result);
        showMessage("Résumé chargé.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur lors du résumé.", "error");
    }
}

async function onStoryClick() {
    const importId = getImportIdOrThrow();

    try {
        showMessage("Story technique en cours...", "info");
        const result = await fetchWorkflowStory(importId);
        renderRawResult(result);
        showMessage("Story chargée.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur lors du story engine.", "error");
    }
}

async function onRawClick() {
    const importId = getImportIdOrThrow();

    try {
        showMessage("Analyse JSON complète en cours...", "info");
        const result = await fetchWorkflowAnalysis(importId);
        renderRawResult(result);
        showMessage("Analyse complète chargée.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur lors de l’analyse complète.", "error");
    }
}

async function onLoadLogsClick() {
    try {
        showMessage("Chargement des logs...", "info");
        const filters = getLogFilters(false);
        const result = await fetchFilteredLogs(filters);
        renderLogsResult(result, filters);
        showMessage("Logs chargés.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur lors du chargement des logs.", "error");
    }
}

async function onLoadErrorLogsClick() {
    try {
        const errorCheckbox = document.getElementById("logFilterErrorOnly");
        if (errorCheckbox) {
            errorCheckbox.checked = true;
        }

        showMessage("Chargement des logs en erreur...", "info");
        const filters = getLogFilters(true);
        const result = await fetchFilteredLogs(filters);
        renderLogsResult(result, filters);
        showMessage("Logs en erreur chargés.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur lors du chargement des logs en erreur.", "error");
    }
}

function onClearLogsClick() {
    const logsResult = document.getElementById("logsResult");
    const logsSummary = document.getElementById("logsSummary");

    if (logsResult) logsResult.innerHTML = "";
    if (logsSummary) logsSummary.textContent = "";

    document.getElementById("logsResultSection")?.classList.add("hidden");
    showMessage("Affichage des logs vidé.", "info");
}

function onClearAnalysisClick() {
    const raw = document.getElementById("analysisResult");
    const human = document.getElementById("humanResult");
    const v2 = document.getElementById("v2Result");

    if (raw) raw.textContent = "";
    if (human) human.innerHTML = "";
    if (v2) v2.innerHTML = "";

    document.getElementById("humanResultSection")?.classList.add("hidden");
    document.getElementById("analysisResultSection")?.classList.add("hidden");
    document.getElementById("v2ResultSection")?.classList.add("hidden");

    showMessage("Affichage d’analyse vidé.", "info");
}

function onFillImportIdClick() {
    const importId = extractLatestImportId(lastUploadResponse);

    if (!importId) {
        showMessage("Aucun importId détecté dans le dernier résultat d’upload.", "error");
        return;
    }

    const input = document.getElementById("importIdInput");
    const logInput = document.getElementById("logFilterImportId");

    if (input) input.value = String(importId);
    if (logInput) logInput.value = String(importId);

    showMessage("Le dernier importId a été inséré dans les champs.", "success");
}

async function loadCurrentUser() {
    const response = await authFetch("/auth/me");
    const data = await safeJson(response);

    if (!response.ok) {
        throw new Error(data?.message || "Impossible de charger l'utilisateur.");
    }

    const box = document.getElementById("userInfo");
    if (box) {
        box.innerHTML = `
            <div><strong>Utilisateur :</strong> ${escapeHtml(data.username || "")}</div>
            <div><strong>Nom :</strong> ${escapeHtml(data.displayName || "")}</div>
            <div><strong>Email :</strong> ${escapeHtml(data.email || "")}</div>
            <div><strong>Rôle :</strong> ${escapeHtml(data.role || "")}</div>
        `;
    }
}

async function uploadLogs(fileList) {
    const formData = new FormData();

    for (const file of fileList) {
        formData.append("files", file);
    }

    const response = await authFetch("/ingest/upload", {
        method: "POST",
        body: formData
    });

    const data = await safeJson(response);

    if (!response.ok) {
        throw new Error(data?.message || "Erreur upload.");
    }

    return data;
}

async function fetchHumanExplanation(importId) {
    const response = await authFetch(`/workflow-analysis/import/${encodeURIComponent(importId)}/human`);
    const data = await safeJson(response);

    if (!response.ok) {
        throw new Error(data?.message || "Erreur explication utilisateur.");
    }

    return data;
}

async function fetchWorkflowAnalysisV2(importId) {
    const response = await authFetch(`/workflow-analysis/import/${encodeURIComponent(importId)}/v2`);
    const data = await safeJson(response);

    if (!response.ok) {
        throw new Error(data?.message || "Erreur analyse V2.");
    }

    return data;
}

async function fetchWorkflowAnalysis(importId) {
    const response = await authFetch(`/workflow-analysis/import/${encodeURIComponent(importId)}`);
    const data = await safeJson(response);

    if (!response.ok) {
        throw new Error(data?.message || "Erreur analyse complète.");
    }

    return data;
}

async function fetchWorkflowSummary(importId) {
    const response = await authFetch(`/workflow-analysis/import/${encodeURIComponent(importId)}/summary`);
    const data = await safeJson(response);

    if (!response.ok) {
        throw new Error(data?.message || "Erreur résumé.");
    }

    return data;
}

async function fetchWorkflowStory(importId) {
    const response = await authFetch(`/workflow-analysis/import/${encodeURIComponent(importId)}/story`);
    const data = await safeJson(response);

    if (!response.ok) {
        throw new Error(data?.message || "Erreur story.");
    }

    return data;
}

async function fetchFilteredLogs(filters) {
    const params = new URLSearchParams();

    if (filters.importId) params.set("importId", filters.importId);
    if (filters.fileName) params.set("fileName", filters.fileName);
    if (filters.errorOnly) params.set("error", "true");
    if (filters.eventType) params.set("eventType", filters.eventType);
    if (filters.processName) params.set("processName", filters.processName);
    if (filters.sessionId) params.set("sessionId", filters.sessionId);
    if (filters.uuid) params.set("uuid", filters.uuid);
    if (filters.limit) params.set("limit", filters.limit);

    const response = await authFetch(`/logs?${params.toString()}`);
    const data = await safeJson(response);

    if (!response.ok) {
        throw new Error(data?.message || "Erreur chargement logs.");
    }

    return Array.isArray(data) ? data : [];
}

function getLogFilters(forceErrorOnly) {
    const importId = document.getElementById("logFilterImportId")?.value?.trim() || "";
    const fileName = document.getElementById("logFilterFileName")?.value?.trim() || "";
    const eventType = document.getElementById("logFilterEventType")?.value?.trim() || "";
    const processName = document.getElementById("logFilterProcessName")?.value?.trim() || "";
    const sessionId = document.getElementById("logFilterSessionId")?.value?.trim() || "";
    const uuid = document.getElementById("logFilterUuid")?.value?.trim() || "";
    const limit = document.getElementById("logFilterLimit")?.value?.trim() || "100";
    const errorOnly = forceErrorOnly || Boolean(document.getElementById("logFilterErrorOnly")?.checked);

    if (importId && !/^\d+$/.test(importId)) {
        throw new Error("Le filtre Import ID doit être numérique.");
    }

    if (limit && !/^\d+$/.test(limit)) {
        throw new Error("La limite doit être numérique.");
    }

    return { importId, fileName, eventType, processName, sessionId, uuid, limit, errorOnly };
}

function renderRawResult(result) {
    document.getElementById("analysisResultSection")?.classList.remove("hidden");
    document.getElementById("humanResultSection")?.classList.add("hidden");
    document.getElementById("v2ResultSection")?.classList.add("hidden");

    const analysisResult = document.getElementById("analysisResult");
    if (analysisResult) {
        analysisResult.textContent = JSON.stringify(result, null, 2);
    }
}

function renderHumanResult(result) {
    document.getElementById("humanResultSection")?.classList.remove("hidden");
    document.getElementById("analysisResultSection")?.classList.add("hidden");
    document.getElementById("v2ResultSection")?.classList.add("hidden");

    const box = document.getElementById("humanResult");
    if (!box) return;

    const segments = Array.isArray(result.segments) ? result.segments : [];

    box.innerHTML = `
        <div class="human-overview">
            <div class="status-badge ${escapeHtml(result.overallStatus || "UNKNOWN")}">
                ${escapeHtml(result.overallStatus || "UNKNOWN")}
            </div>

            <div><strong>Explication globale :</strong><br>${escapeHtml(result.globalExplanation || "")}</div>
            <div><strong>Conclusion principale :</strong><br>${escapeHtml(result.keyConclusion || "")}</div>

            <div>
                <strong>Vue d’ensemble :</strong><br>
                Segments analysés : ${escapeHtml(result.totalSegments ?? 0)} |
                Segments en erreur : ${escapeHtml(result.errorSegments ?? 0)} |
                Segments avec avertissements : ${escapeHtml(result.warningSegments ?? 0)} |
                Segments avec performance dégradée : ${escapeHtml(result.performanceSegments ?? 0)}
            </div>
        </div>

        <div class="segment-list">
            ${segments.map(renderHumanSegment).join("")}
        </div>
    `;
}

function renderHumanSegment(segment) {
    return `
        <div class="segment-card">
            <h3>${escapeHtml(segment.title || "")}</h3>
            <div><strong>Explication simple :</strong> ${escapeHtml(segment.simpleExplanation || "")}</div>
            <div><strong>Cause probable :</strong> ${escapeHtml(segment.probableCause || "")}</div>
            <div><strong>Impact métier :</strong> ${escapeHtml(segment.businessImpact || "")}</div>
            <div><strong>Recommandation :</strong> ${escapeHtml(segment.recommendation || "")}</div>
        </div>
    `;
}

function renderV2Result(result) {
    document.getElementById("v2ResultSection")?.classList.remove("hidden");
    document.getElementById("humanResultSection")?.classList.add("hidden");
    document.getElementById("analysisResultSection")?.classList.add("hidden");

    const box = document.getElementById("v2Result");
    if (!box) return;

    const workflows = Array.isArray(result.workflows) ? result.workflows : [];

    box.innerHTML = `
        <div class="v2-overview">
            <div><strong>Import :</strong> ${escapeHtml(result.importId ?? "")}</div>
            <div><strong>Total workflows reconstruits :</strong> ${escapeHtml(result.totalWorkflows ?? 0)}</div>
            <div><strong>Workflows avec problème de règle :</strong> ${escapeHtml(result.workflowsWithRuleProblems ?? 0)}</div>
            <div><strong>Workflows avec résultat vide :</strong> ${escapeHtml(result.workflowsWithZeroResults ?? 0)}</div>
            <div><strong>Workflows avec signal de performance :</strong> ${escapeHtml(result.workflowsWithPerformanceProblems ?? 0)}</div>
        </div>

        <div class="v2-grid">
            ${workflows.map(renderWorkflowCard).join("")}
        </div>
    `;
}

function renderWorkflowCard(workflow) {
    const detectedInputs = Array.isArray(workflow.detectedInputs) ? workflow.detectedInputs : [];
    const timeline = Array.isArray(workflow.timeline) ? workflow.timeline : [];
    const lines = Array.isArray(workflow.lines) ? workflow.lines : [];
    const workflowId = encodeForAttr(workflow.workflowKey || "workflow");

    return `
        <div class="workflow-card">
            <h3>${escapeHtml(workflow.title || workflow.workflowKey || "Workflow")}</h3>

            <div class="workflow-meta">
                <span class="workflow-pill">Clé : ${escapeHtml(workflow.workflowKey || "N/A")}</span>
                <span class="workflow-pill">Regroupement : ${escapeHtml(workflow.groupingStrategy || "N/A")}</span>
                <span class="workflow-pill">UUID : ${escapeHtml(workflow.uuid || "N/A")}</span>
                <span class="workflow-pill">Session : ${escapeHtml(extractSessionFromKey(workflow.workflowKey) || "N/A")}</span>
                <span class="workflow-pill">Filtre : ${escapeHtml(workflow.filterCode || "N/A")}</span>
            </div>

            <div class="workflow-section">
                <strong>Ce que l’utilisateur a semblé demander</strong>
                <div>${escapeHtml(workflow.userIntentSummary || "")}</div>
            </div>

            <div class="workflow-section">
                <strong>Ce qui s’est passé</strong>
                <div>${escapeHtml(workflow.whatHappenedSummary || "")}</div>
            </div>

            <div class="workflow-section">
                <strong>Résultat final du workflow</strong>
                <div>${escapeHtml(workflow.finalOutcome || "")}</div>
            </div>

            <div class="workflow-section">
                <strong>Cause probable</strong>
                <div>${escapeHtml(workflow.probableCause || "")}</div>
            </div>

            <div class="workflow-section">
                <strong>Recommandation</strong>
                <div>${escapeHtml(workflow.recommendation || "")}</div>
            </div>

            <div class="workflow-section">
                <strong>Données d’entrée détectées</strong>
                ${
        detectedInputs.length
            ? `<ul class="input-list">${detectedInputs.map(x => `<li>${escapeHtml(x)}</li>`).join("")}</ul>`
            : `<div>Aucune entrée métier explicite détectée.</div>`
    }
            </div>

            <div class="workflow-section">
                <strong>Enchaînement des actions</strong>
                ${
        timeline.length
            ? `<ul class="timeline-list">${timeline.map(x => `<li>${escapeHtml(x)}</li>`).join("")}</ul>`
            : `<div>Aucune timeline reconstruite.</div>`
    }
            </div>

            <div class="workflow-actions">
                <button type="button" onclick="toggleWorkflowLines('${workflowId}')">
                    Voir / cacher les lignes du workflow
                </button>
                <button type="button" onclick="loadLogsFromWorkflow('${escapeJs(workflow.workflowKey || "")}','${escapeJs(workflow.uuid || "")}','${escapeJs(workflow.processName || "")}')">
                    Consulter ces logs dans l’explorateur
                </button>
            </div>

            <div class="workflow-lines" id="wf-lines-${workflowId}">
                ${lines.map(renderWorkflowLineCard).join("")}
            </div>
        </div>
    `;
}

function renderWorkflowLineCard(line) {
    return `
        <div class="line-card">
            <div class="line-header">
                <span class="line-pill">${escapeHtml(line.timestamp || "N/A")}</span>
                <span class="line-pill">${escapeHtml(line.level || "N/A")}</span>
                <span class="line-pill">${escapeHtml(line.eventType || "N/A")}</span>
            </div>

            <div class="workflow-section">
                <strong>Interprétation de cette ligne</strong>
                <div>${escapeHtml(line.businessMeaning || "Aucune interprétation disponible.")}</div>
            </div>

            <div class="workflow-section">
                <strong>Process</strong>
                <div>${escapeHtml(line.processName || "N/A")}</div>
            </div>

            <div class="workflow-section">
                <strong>Source</strong>
                <div>${escapeHtml(line.sourceClass || "N/A")}</div>
            </div>

            <div class="workflow-section">
                <strong>Message réel</strong>
                <div class="line-message">${escapeHtml(line.message || "")}</div>
            </div>
        </div>
    `;
}

function renderLogsResult(logs, filters) {
    document.getElementById("logsResultSection")?.classList.remove("hidden");

    const logsResult = document.getElementById("logsResult");
    const logsSummary = document.getElementById("logsSummary");

    if (logsSummary) {
        logsSummary.textContent =
            `${logs.length} log(s) | importId=${filters.importId || "tous"} | fileName=${filters.fileName || "tous"} | eventType=${filters.eventType || "tous"} | processName=${filters.processName || "tous"} | sessionId=${filters.sessionId || "tous"} | uuid=${filters.uuid || "tous"} | errorOnly=${filters.errorOnly ? "oui" : "non"}`;
    }

    if (!logsResult) return;

    if (!logs.length) {
        logsResult.innerHTML = `<div class="empty-state">Aucun log trouvé avec ces filtres.</div>`;
        return;
    }

    logsResult.innerHTML = `
        <div class="logs-list">
            ${logs.map(renderLogCard).join("")}
        </div>
    `;

    document.getElementById("logsResultSection")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function renderLogCard(log) {
    const isError = Boolean(log.isError) || String(log.level || "").toUpperCase() === "ERROR";
    const levelClass = isError ? "error-pill" : "info-pill";

    return `
        <div class="log-card">
            <h3>Log #${escapeHtml(log.id ?? "N/A")}</h3>

            <div class="log-meta">
                <span class="log-pill ${levelClass}">Level : ${escapeHtml(log.level || "N/A")}</span>
                <span class="log-pill">Import : ${escapeHtml(log.importId ?? log.logImportId ?? "N/A")}</span>
                <span class="log-pill">Event : ${escapeHtml(log.eventType || "N/A")}</span>
                <span class="log-pill">Erreur : ${isError ? "Oui" : "Non"}</span>
            </div>

            <div class="log-section">
                <strong>Timestamp</strong>
                <div>${escapeHtml(log.logTimestamp || "N/A")}</div>
            </div>

            <div class="log-section">
                <strong>Process</strong>
                <div>${escapeHtml(log.processName || "N/A")}</div>
            </div>

            <div class="log-section">
                <strong>Source</strong>
                <div>${escapeHtml(log.sourceClass || "N/A")}</div>
            </div>

            <div class="log-section">
                <strong>Explication de la ligne</strong>
                <div>${escapeHtml(log.businessMeaning || "Aucune explication disponible.")}</div>
            </div>

            <div class="log-section">
                <strong>Message réel</strong>
                <div class="log-message">${escapeHtml(log.message || "")}</div>
            </div>
        </div>
    `;
}

function getImportIdOrThrow() {
    const value = document.getElementById("importIdInput")?.value?.trim();

    if (!value) {
        throw new Error("Veuillez saisir un importId.");
    }

    if (!/^\d+$/.test(value)) {
        throw new Error("L'importId doit être numérique.");
    }

    return value;
}

function getLogFilters(forceErrorOnly) {
    const importId = document.getElementById("logFilterImportId")?.value?.trim() || "";
    const fileName = document.getElementById("logFilterFileName")?.value?.trim() || "";
    const eventType = document.getElementById("logFilterEventType")?.value?.trim() || "";
    const processName = document.getElementById("logFilterProcessName")?.value?.trim() || "";
    const sessionId = document.getElementById("logFilterSessionId")?.value?.trim() || "";
    const uuid = document.getElementById("logFilterUuid")?.value?.trim() || "";
    const limit = document.getElementById("logFilterLimit")?.value?.trim() || "100";
    const errorOnly = forceErrorOnly || Boolean(document.getElementById("logFilterErrorOnly")?.checked);

    if (importId && !/^\d+$/.test(importId)) {
        throw new Error("Le filtre Import ID doit être numérique.");
    }

    if (limit && !/^\d+$/.test(limit)) {
        throw new Error("La limite doit être numérique.");
    }

    return { importId, fileName, eventType, processName, sessionId, uuid, limit, errorOnly };
}

function toggleWorkflowLines(workflowId) {
    const box = document.getElementById(`wf-lines-${workflowId}`);
    if (!box) return;
    box.classList.toggle("open");
}

async function loadLogsFromWorkflow(workflowKey, uuid, processName) {
    try {
        const importId = document.getElementById("importIdInput")?.value?.trim() || "";
        const uuidInput = document.getElementById("logFilterUuid");
        const processInput = document.getElementById("logFilterProcessName");
        const importInput = document.getElementById("logFilterImportId");
        const sessionInput = document.getElementById("logFilterSessionId");

        if (importInput && importId) importInput.value = importId;
        if (uuidInput) uuidInput.value = uuid || "";
        if (processInput) processInput.value = processName || "";

        const extractedSession = extractSessionFromKey(workflowKey);
        if (sessionInput) sessionInput.value = extractedSession || "";

        showMessage("Chargement des logs du workflow...", "info");
        const filters = getLogFilters(false);
        const result = await fetchFilteredLogs(filters);
        renderLogsResult(result, filters);
        showMessage("Logs du workflow chargés dans l’explorateur.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur lors du chargement des logs du workflow.", "error");
    }
}

function extractSessionFromKey(workflowKey) {
    if (!workflowKey) return "";
    if (workflowKey.startsWith("SESSION::")) {
        return workflowKey.substring("SESSION::".length);
    }
    return "";
}

function extractLatestImportId(result) {
    if (!result) return null;

    if (Array.isArray(result)) {
        for (let i = result.length - 1; i >= 0; i--) {
            const item = result[i];
            if (item && item.importId != null) {
                return item.importId;
            }
        }
    }

    if (typeof result === "object" && result.importId != null) {
        return result.importId;
    }

    return null;
}

function showLoggedInUI() {
    document.getElementById("loginSection")?.classList.add("hidden");
    document.getElementById("userSection")?.classList.remove("hidden");
    document.getElementById("uploadSection")?.classList.remove("hidden");
    document.getElementById("uploadResultSection")?.classList.remove("hidden");
    document.getElementById("analysisSection")?.classList.remove("hidden");
    document.getElementById("logExplorerSection")?.classList.remove("hidden");
}

function showLoggedOutUI() {
    document.getElementById("loginSection")?.classList.remove("hidden");
    document.getElementById("userSection")?.classList.add("hidden");
    document.getElementById("uploadSection")?.classList.add("hidden");
    document.getElementById("uploadResultSection")?.classList.add("hidden");
    document.getElementById("analysisSection")?.classList.add("hidden");
    document.getElementById("logExplorerSection")?.classList.add("hidden");
    document.getElementById("humanResultSection")?.classList.add("hidden");
    document.getElementById("analysisResultSection")?.classList.add("hidden");
    document.getElementById("v2ResultSection")?.classList.add("hidden");
    document.getElementById("logsResultSection")?.classList.add("hidden");
}

function resetProtectedSections() {
    const ids = [
        "userInfo",
        "uploadResult",
        "analysisResult",
        "importIdInput",
        "logFilterImportId",
        "logFilterFileName",
        "logFilterEventType",
        "logFilterProcessName",
        "logFilterSessionId",
        "logFilterUuid",
        "logFilterLimit"
    ];

    ids.forEach(id => {
        const el = document.getElementById(id);
        if (!el) return;

        if (el.tagName === "INPUT") {
            el.value = id === "logFilterLimit" ? "100" : "";
        } else {
            el.textContent = "";
        }
    });

    const human = document.getElementById("humanResult");
    if (human) human.innerHTML = "";

    const v2 = document.getElementById("v2Result");
    if (v2) v2.innerHTML = "";

    const logsResult = document.getElementById("logsResult");
    if (logsResult) logsResult.innerHTML = "";

    const logsSummary = document.getElementById("logsSummary");
    if (logsSummary) logsSummary.textContent = "";

    const errorOnly = document.getElementById("logFilterErrorOnly");
    if (errorOnly) errorOnly.checked = false;

    lastUploadResponse = null;
}

function showMessage(message, type = "info") {
    const box = document.getElementById("messageBox");
    if (!box) return;
    box.className = type;
    box.textContent = message;
}

function encodeForAttr(value) {
    return String(value ?? "")
        .replaceAll(" ", "_")
        .replaceAll(":", "_")
        .replaceAll("|", "_")
        .replaceAll("/", "_")
        .replaceAll("\\", "_")
        .replaceAll(".", "_")
        .replaceAll("[", "_")
        .replaceAll("]", "_");
}

function escapeJs(value) {
    return String(value ?? "")
        .replaceAll("\\", "\\\\")
        .replaceAll("'", "\\'");
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#039;");
}

window.toggleWorkflowLines = toggleWorkflowLines;
window.loadLogsFromWorkflow = loadLogsFromWorkflow;