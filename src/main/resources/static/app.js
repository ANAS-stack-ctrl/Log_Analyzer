document.addEventListener("DOMContentLoaded", () => {
    const loginForm = document.getElementById("loginForm");
    const logoutBtn = document.getElementById("logoutBtn");
    const uploadBtn = document.getElementById("uploadBtn");
    const v2Btn = document.getElementById("v2Btn");
    const clearAnalysisBtn = document.getElementById("clearAnalysisBtn");
    const fillImportIdBtn = document.getElementById("fillImportIdBtn");
    const loadLogsBtn = document.getElementById("loadLogsBtn");
    const loadErrorLogsBtn = document.getElementById("loadErrorLogsBtn");
    const loadGenericExplanationsBtn = document.getElementById("loadGenericExplanationsBtn");
    const clearLogsBtn = document.getElementById("clearLogsBtn");
    const loadAllGenericExplanationsBtn = document.getElementById("loadAllGenericExplanationsBtn");
    const refreshImportsBtn = document.getElementById("refreshImportsBtn");

    loginForm?.addEventListener("submit", onLoginSubmit);
    logoutBtn?.addEventListener("click", onLogoutClick);
    uploadBtn?.addEventListener("click", onUploadClick);
    v2Btn?.addEventListener("click", onV2Click);
    clearAnalysisBtn?.addEventListener("click", onClearAnalysisClick);
    fillImportIdBtn?.addEventListener("click", onFillImportIdClick);
    loadLogsBtn?.addEventListener("click", onLoadLogsClick);
    loadErrorLogsBtn?.addEventListener("click", onLoadErrorLogsClick);
    loadGenericExplanationsBtn?.addEventListener("click", loadGenericExplanations);
    clearLogsBtn?.addEventListener("click", onClearLogsClick);
    loadAllGenericExplanationsBtn?.addEventListener("click", loadAllGenericExplanations);
    refreshImportsBtn?.addEventListener("click", onRefreshImportsClick);

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
        await refreshImports();
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
            const logFilterImportIds = document.getElementById("logFilterImportIds");

            if (importIdInput && !importIdInput.value.trim()) {
                importIdInput.value = String(extractedImportId);
            }

            if (logFilterImportIds && !logFilterImportIds.value.trim()) {
                logFilterImportIds.value = String(extractedImportId);
            }
        }

        showMessage("Upload terminé avec succès.", "success");
        await refreshImports();
    } catch (e) {
        showMessage(e.message || "Erreur lors de l'upload.", "error");
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

async function loadGenericExplanations() {
    const importId =
        document.getElementById("importIdInput")?.value?.trim()
        || extractFirstIdFromCsv(document.getElementById("logFilterImportIds")?.value);

    const token = localStorage.getItem("accessToken");

    if (!importId) {
        showMessage("Veuillez saisir un Import ID.", "error");
        return;
    }

    if (!/^\d+$/.test(importId)) {
        showMessage("L'Import ID doit être numérique.", "error");
        return;
    }

    if (!token) {
        showMessage("Vous devez d’abord vous authentifier.", "error");
        return;
    }

    try {
        showMessage("Chargement des logs génériques...", "info");

        const response = await fetch(`/analysis/import/${encodeURIComponent(importId)}/generic-explanations`, {
            method: "GET",
            headers: {
                "Authorization": `Bearer ${token}`,
                "Accept": "application/json"
            }
        });

        if (!response.ok) {
            const errorText = await response.text();
            showMessage(`Erreur ${response.status} : ${errorText}`, "error");
            return;
        }

        const data = await response.json();
        const logs = Array.isArray(data) ? data : [];

        document.getElementById("logsResultSection")?.classList.remove("hidden");

        const logsSummary = document.getElementById("logsSummary");
        const container = document.getElementById("logsResult");

        if (!container) return;

        if (!logs.length) {
            if (logsSummary) {
                logsSummary.textContent = `Logs génériques trouvés : 0 | importId=${importId}`;
            }

            container.innerHTML = `<div class="empty-state">Aucun log générique trouvé pour cet import.</div>`;
            showMessage("Aucun log générique trouvé.", "success");
            return;
        }

        const grouped = groupByMessagePattern(logs);

        if (logsSummary) {
            logsSummary.textContent =
                `Logs génériques : ${logs.length} | Patterns regroupés : ${grouped.length} | importId=${importId}`;
        }

        container.innerHTML = grouped.map(g => `
            <div class="log-card">
                <b>Occurrences:</b> ${g.count}
                <br>
                <b>Pattern:</b>
                <pre>${escapeHtml(g.pattern)}</pre>
                <b>Exemple:</b>
                <pre>${escapeHtml(g.example || "")}</pre>
            </div>
        `).join("");

        document.getElementById("logsResultSection")?.scrollIntoView({ behavior: "smooth", block: "start" });
        showMessage(`Patterns génériques trouvés : ${grouped.length}`, "success");

    } catch (error) {
        showMessage("Erreur réseau : " + error.message, "error");
    }
}
async function loadAllGenericExplanations() {
    const token = localStorage.getItem("accessToken");

    if (!token) {
        showMessage("Vous devez d’abord vous authentifier.", "error");
        return;
    }

    const maxImportId = 250; // augmente si tu as plus d'imports
    const concurrency = 5;

    const allLogs = [];
    const container = document.getElementById("logsResult");
    const logsSummary = document.getElementById("logsSummary");

    document.getElementById("logsResultSection")?.classList.remove("hidden");
    if (container) container.innerHTML = "";
    showMessage("Analyse globale import par import...", "info");

    async function loadOneImport(importId) {
        const response = await fetch(`/analysis/import/${importId}/generic-explanations`, {
            method: "GET",
            headers: {
                "Authorization": `Bearer ${token}`,
                "Accept": "application/json"
            }
        });

        if (!response.ok) {
            return [];
        }

        const data = await response.json();
        return Array.isArray(data) ? data : [];
    }

    for (let i = 1; i <= maxImportId; i += concurrency) {
        const batch = [];

        for (let id = i; id < i + concurrency && id <= maxImportId; id++) {
            batch.push(loadOneImport(id));
        }

        const results = await Promise.all(batch);

        results.forEach(logs => {
            allLogs.push(...logs);
        });

        showMessage(`Analyse globale en cours... imports ${i} à ${Math.min(i + concurrency - 1, maxImportId)} / ${maxImportId}`, "info");
    }

    const grouped = groupByMessagePattern(allLogs);

    if (logsSummary) {
        logsSummary.textContent =
            `Logs génériques globaux : ${allLogs.length} | Patterns regroupés : ${grouped.length}`;
    }

    if (!container) return;

    if (!grouped.length) {
        container.innerHTML = `<div class="empty-state">Aucun log générique global trouvé.</div>`;
        showMessage("Aucun log générique global trouvé.", "success");
        return;
    }

    container.innerHTML = grouped.map(g => `
        <div class="log-card">
            <b>Occurrences:</b> ${g.count}
            <br>
            <b>Pattern:</b>
            <pre>${escapeHtml(g.pattern || "")}</pre>
            <b>Exemple:</b>
            <pre>${escapeHtml(g.example || "")}</pre>
        </div>
    `).join("");

    showMessage(`Analyse terminée : ${allLogs.length} logs génériques | ${grouped.length} patterns`, "success");
}
function groupByMessagePattern(logs) {
    const map = new Map();

    logs.forEach(log => {
        let msg = log.message || "";

        msg = msg
            .replace(/\d+/g, "X")
            .replace(/\[.*?]/g, "[...]")
            .replace(/\s+/g, " ")
            .trim();

        const key = msg.substring(0, 160);

        if (!map.has(key)) {
            map.set(key, {
                count: 0,
                example: log.message
            });
        }

        map.get(key).count++;
    });

    return Array.from(map.entries())
        .map(([pattern, data]) => ({
            pattern,
            count: data.count,
            example: data.example
        }))
        .sort((a, b) => b.count - a.count);
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
    const logInput = document.getElementById("logFilterImportIds");

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

async function fetchWorkflowAnalysisV2(importId) {
    const response = await authFetch(`/workflow-analysis/import/${encodeURIComponent(importId)}/v2`);
    const data = await safeJson(response);

    if (!response.ok) {
        throw new Error(data?.message || "Erreur analyse V2.");
    }

    return data;
}

async function onRefreshImportsClick() {
    try {
        showMessage("Rafraîchissement des imports...", "info");
        await refreshImports();
        showMessage("Imports rafraîchis.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur lors du rafraîchissement des imports.", "error");
    }
}

async function refreshImports() {
    const response = await authFetch("/imports");
    const data = await safeJson(response);

    if (!response.ok) {
        throw new Error(data?.message || "Impossible de charger la liste des imports.");
    }

    renderImports(Array.isArray(data) ? data : []);
}

function renderImports(imports) {
    document.getElementById("importsSection")?.classList.remove("hidden");

    const container = document.getElementById("importsResult");
    if (!container) return;

    if (!imports.length) {
        container.innerHTML = `<div class="empty-state">Aucun import trouvé.</div>`;
        return;
    }

    container.innerHTML = `
        <div class="imports-table-wrap">
            <table class="imports-table">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Fichier</th>
                        <th>Début</th>
                        <th>Statut</th>
                        <th>Total lignes</th>
                        <th>Erreurs</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    ${imports.map(renderImportRow).join("")}
                </tbody>
            </table>
        </div>
    `;
}

function renderImportRow(imp) {
    const id = imp?.id ?? "";
    const fileName = imp?.fileName ?? "";
    const startedAt = imp?.startedAt ?? "";
    const status = imp?.status ?? "";
    const totalLines = imp?.totalLines ?? 0;
    const totalErrors = imp?.totalErrors ?? 0;

    return `
        <tr>
            <td>${escapeHtml(id)}</td>
            <td>${escapeHtml(fileName)}</td>
            <td>${escapeHtml(startedAt)}</td>
            <td>${escapeHtml(status)}</td>
            <td>${escapeHtml(totalLines)}</td>
            <td>${escapeHtml(totalErrors)}</td>
            <td class="imports-actions">
                <button type="button" class="secondary-btn" onclick="useImportId('${escapeJs(id)}')">Utiliser</button>
                <button type="button" class="danger-btn" onclick="deleteImport('${escapeJs(id)}','${escapeJs(fileName)}')">Supprimer</button>
            </td>
        </tr>
    `;
}

function useImportId(importId) {
    const input = document.getElementById("importIdInput");
    const logInput = document.getElementById("logFilterImportIds");
    if (input) input.value = String(importId || "");
    if (logInput) logInput.value = String(importId || "");
    showMessage(`ImportId ${importId} appliqué aux champs.`, "success");
}

async function deleteImport(importId, fileName) {
    if (!importId || !/^\d+$/.test(String(importId))) {
        showMessage("ImportId invalide.", "error");
        return;
    }

    const ok = confirm(`Voulez-vous vraiment supprimer l'import #${importId} (${fileName || "sans nom"}) et tous ses logs ?`);
    if (!ok) {
        showMessage("Suppression annulée.", "info");
        return;
    }

    showMessage(`Suppression de l'import #${importId}...`, "info");
    const response = await authFetch(`/imports/${encodeURIComponent(importId)}`, { method: "DELETE" });

    if (!response.ok) {
        const data = await safeJson(response);
        throw new Error(data?.message || `Erreur suppression import (${response.status}).`);
    }

    await refreshImports();
    showMessage(`Import #${importId} supprimé.`, "success");
}

async function fetchFilteredLogs(filters) {
    const params = new URLSearchParams();

    if (filters.importIds && filters.importIds.length) params.set("importIds", filters.importIds.join(","));
    if (filters.fileNames && filters.fileNames.length) params.set("fileNames", filters.fileNames.join(","));
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
    const importIdsRaw = document.getElementById("logFilterImportIds")?.value || "";
    const fileNamesRaw = document.getElementById("logFilterFileNames")?.value || "";
    const eventType = document.getElementById("logFilterEventType")?.value?.trim() || "";
    const processName = document.getElementById("logFilterProcessName")?.value?.trim() || "";
    const sessionId = document.getElementById("logFilterSessionId")?.value?.trim() || "";
    const uuid = document.getElementById("logFilterUuid")?.value?.trim() || "";
    const limit = document.getElementById("logFilterLimit")?.value?.trim() || "100";
    const errorOnly = forceErrorOnly || Boolean(document.getElementById("logFilterErrorOnly")?.checked);

    const importIds = parseCsvLongs(importIdsRaw);
    const fileNames = parseCsvStrings(fileNamesRaw);

    if (!importIds.ok) {
        throw new Error("Le filtre Import IDs doit contenir uniquement des nombres, séparés par des virgules.");
    }

    if (limit && !/^\d+$/.test(limit)) {
        throw new Error("La limite doit être numérique.");
    }

    return { importIds: importIds.values, fileNames, eventType, processName, sessionId, uuid, limit, errorOnly };
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
            `${logs.length} log(s) | importIds=${filters.importIds?.length ? filters.importIds.join(",") : "tous"} | fileNames=${filters.fileNames?.length ? filters.fileNames.join(",") : "tous"} | eventType=${filters.eventType || "tous"} | processName=${filters.processName || "tous"} | sessionId=${filters.sessionId || "tous"} | uuid=${filters.uuid || "tous"} | errorOnly=${filters.errorOnly ? "oui" : "non"}`;
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
                <span class="log-pill">Timestamp : ${escapeHtml(log.logTimestamp || "N/A")}</span>
                <span class="log-pill">Import : ${escapeHtml(log.importId ?? log.logImportId ?? "N/A")}</span>
                ${log.fileName ? `<span class="log-pill">Fichier : ${escapeHtml(log.fileName)}</span>` : ""}
            </div>

            <div class="log-section">
                <strong>Explication de la ligne</strong>
                <div>${escapeHtml(log.businessMeaning || "Aucune explication disponible.")}</div>
            </div>

            <div class="log-section">
                <strong>Message réel</strong>
                <div class="log-message">${escapeHtml(log.message || "")}</div>
            </div>

            <details class="log-details">
                <summary>Détails techniques</summary>
                <div class="log-details-grid">
                    <div><strong>Event Type</strong><div>${escapeHtml(log.eventType || "N/A")}</div></div>
                    <div><strong>Process</strong><div>${escapeHtml(log.processName || "N/A")}</div></div>
                    <div><strong>Source</strong><div>${escapeHtml(log.sourceClass || "N/A")}</div></div>
                    <div><strong>Session</strong><div>${escapeHtml(log.sessionId || "N/A")}</div></div>
                    <div><strong>UUID</strong><div>${escapeHtml(log.uuid || "N/A")}</div></div>
                    <div><strong>Erreur</strong><div>${isError ? "Oui" : "Non"}</div></div>
                </div>
            </details>
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
        const importInput = document.getElementById("logFilterImportIds");
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
    document.getElementById("importsSection")?.classList.remove("hidden");
    document.getElementById("analysisSection")?.classList.remove("hidden");
    document.getElementById("logExplorerSection")?.classList.remove("hidden");
}

function showLoggedOutUI() {
    document.getElementById("loginSection")?.classList.remove("hidden");
    document.getElementById("userSection")?.classList.add("hidden");
    document.getElementById("uploadSection")?.classList.add("hidden");
    document.getElementById("uploadResultSection")?.classList.add("hidden");
    document.getElementById("importsSection")?.classList.add("hidden");
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
        "logFilterImportIds",
        "logFilterFileNames",
        "logFilterEventType",
        "logFilterProcessName",
        "logFilterSessionId",
        "logFilterUuid",
        "logFilterLimit",
        "importsResult"
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

function parseCsvLongs(raw) {
    const text = String(raw ?? "").trim();
    if (!text) return { ok: true, values: [] };
    const parts = text.split(",").map(x => x.trim()).filter(Boolean);
    const values = [];
    for (const p of parts) {
        if (!/^\d+$/.test(p)) return { ok: false, values: [] };
        values.push(Number(p));
    }
    return { ok: true, values: Array.from(new Set(values)) };
}

function parseCsvStrings(raw) {
    const text = String(raw ?? "").trim();
    if (!text) return [];
    const parts = text.split(",").map(x => x.trim()).filter(Boolean);
    const unique = [];
    const seen = new Set();
    for (const p of parts) {
        const key = p.toLowerCase();
        if (!seen.has(key)) {
            seen.add(key);
            unique.push(p);
        }
    }
    return unique;
}

function extractFirstIdFromCsv(raw) {
    const parsed = parseCsvLongs(raw);
    if (!parsed.ok) return "";
    return parsed.values.length ? String(parsed.values[0]) : "";
}

window.toggleWorkflowLines = toggleWorkflowLines;
window.loadLogsFromWorkflow = loadLogsFromWorkflow;
window.deleteImport = deleteImport;
window.useImportId = useImportId;