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
    const groupLogsBtn = document.getElementById("groupLogsBtn");
    const clearGroupAnalysisBtn = document.getElementById("clearGroupAnalysisBtn");
    const clearRelatedLogsBtn = document.getElementById("clearRelatedLogsBtn");
    const clearWorkflowGraphBtn = document.getElementById("clearWorkflowGraphBtn");

    const viewDetailedBtn = document.getElementById("viewDetailedBtn");
    const viewMessagesBtn = document.getElementById("viewMessagesBtn");
    const viewRawBtn = document.getElementById("viewRawBtn");

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
    groupLogsBtn?.addEventListener("click", onGroupLogsClick);
    clearGroupAnalysisBtn?.addEventListener("click", onClearGroupAnalysisClick);
    clearRelatedLogsBtn?.addEventListener("click", onClearRelatedLogsClick);
    clearWorkflowGraphBtn?.addEventListener("click", onClearWorkflowGraphClick);

    viewDetailedBtn?.addEventListener("click", () => switchLogView("detailed"));
    viewMessagesBtn?.addEventListener("click", () => switchLogView("messages"));
    viewRawBtn?.addEventListener("click", () => switchLogView("raw"));

    initializeApp().catch(err => {
        showMessage(err.message || "Erreur d'initialisation.", "error");
    });
});

let lastUploadResponse = null;
let currentLogViewMode = "detailed";

window.lastRenderedLogs = null;
window.lastRenderedFilters = null;
window.currentAnalyzedGroupBy = "";
window.currentAnalyzedGroupKey = "";
window.currentWorkflowGraph = null;

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
    const groupBy = document.getElementById("logGroupBy")?.value || "";

    try {
        showMessage("Analyse V2 compréhensible en cours...", "info");
        const result = await fetchWorkflowAnalysisV2(importId, groupBy);
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

async function onGroupLogsClick() {
    try {
        showMessage("Regroupement des logs en cours...", "info");

        const filters = getLogFilters(false);
        const groups = await fetchGroupedLogs(filters);

        renderGroupsResult(groups, filters);

        showMessage("Groupes chargés.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur lors du regroupement des logs.", "error");
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

    const maxImportId = 250;
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

        showMessage(
            `Analyse globale en cours... imports ${i} à ${Math.min(i + concurrency - 1, maxImportId)} / ${maxImportId}`,
            "info"
        );
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

    showMessage(
        `Analyse terminée : ${allLogs.length} logs génériques | ${grouped.length} patterns`,
        "success"
    );
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
    const groupsResult = document.getElementById("groupsResult");
    const groupsSummary = document.getElementById("groupsSummary");

    if (logsResult) logsResult.innerHTML = "";
    if (logsSummary) logsSummary.textContent = "";
    if (groupsResult) groupsResult.innerHTML = "";
    if (groupsSummary) groupsSummary.textContent = "";

    window.lastRenderedLogs = null;
    window.lastRenderedFilters = null;

    document.getElementById("logsResultSection")?.classList.add("hidden");
    document.getElementById("groupsResultSection")?.classList.add("hidden");

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

function onClearGroupAnalysisClick() {
    const container = document.getElementById("groupAnalysisResult");

    if (container) {
        container.innerHTML = "";
    }

    window.currentAnalyzedGroupBy = "";
    window.currentAnalyzedGroupKey = "";

    document.getElementById("groupAnalysisSection")?.classList.add("hidden");
    showMessage("Analyse du groupe vidée.", "info");
}

function onClearRelatedLogsClick() {
    const container = document.getElementById("relatedLogsResult");

    if (container) {
        container.innerHTML = "";
    }

    document.getElementById("relatedLogsSection")?.classList.add("hidden");
    showMessage("Logs liés vidés.", "info");
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

async function fetchWorkflowAnalysisV2(importId, groupBy = "") {
    const params = new URLSearchParams();

    if (groupBy) {
        params.set("groupBy", groupBy);
    }

    const suffix = params.toString() ? `?${params.toString()}` : "";

    const response = await authFetch(
        `/workflow-analysis/import/${encodeURIComponent(importId)}/v2${suffix}`
    );

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

    const ok = confirm(
        `Voulez-vous vraiment supprimer l'import #${importId} (${fileName || "sans nom"}) et tous ses logs ?`
    );

    if (!ok) {
        showMessage("Suppression annulée.", "info");
        return;
    }

    showMessage(`Suppression de l'import #${importId}...`, "info");

    const response = await authFetch(
        `/imports/${encodeURIComponent(importId)}`,
        { method: "DELETE" }
    );

    if (!response.ok) {
        const data = await safeJson(response);

        throw new Error(
            data?.message || `Erreur suppression import (${response.status}).`
        );
    }

    await refreshImports();

    showMessage(`Import #${importId} supprimé.`, "success");
}

async function fetchFilteredLogs(filters) {
    const params = buildLogQueryParams(filters);

    const response = await authFetch(`/logs?${params.toString()}`);

    const data = await safeJson(response);

    if (!response.ok) {
        throw new Error(data?.message || "Erreur chargement logs.");
    }

    return Array.isArray(data) ? data : [];
}

async function fetchGroupedLogs(filters) {
    const params = buildLogQueryParams(filters);

    if (filters.groupBy) {
        params.set("groupBy", filters.groupBy);
    }

    const response = await authFetch(`/logs/groups?${params.toString()}`);

    const data = await safeJson(response);

    if (!response.ok) {
        throw new Error(data?.message || "Erreur chargement groupes.");
    }

    return Array.isArray(data) ? data : [];
}

function buildLogQueryParams(filters) {
    const params = new URLSearchParams();

    if (filters.importIds?.length) {
        params.set("importIds", filters.importIds.join(","));
    }

    if (filters.fileNames?.length) {
        params.set("fileNames", filters.fileNames.join(","));
    }

    if (filters.errorOnly) {
        params.set("error", "true");
    }

    if (filters.eventType) {
        params.set("eventType", filters.eventType);
    }

    if (filters.processName) {
        params.set("processName", filters.processName);
    }

    if (filters.userName) {
        params.set("userName", filters.userName);
    }

    if (filters.sessionId) {
        params.set("sessionId", filters.sessionId);
    }

    if (filters.uuid) {
        params.set("uuid", filters.uuid);
    }

    if (filters.dateFrom) {
        params.set("dateFrom", toBackendDateTime(filters.dateFrom));
    }

    if (filters.dateTo) {
        params.set("dateTo", toBackendDateTime(filters.dateTo));
    }

    if (filters.limit) {
        params.set("limit", filters.limit);
    }

    return params;
}

function toBackendDateTime(value) {
    if (!value) return "";

    return value.length === 16
        ? `${value}:00`
        : value;
}

function getLogFilters(forceErrorOnly) {
    const importIdsRaw =
        document.getElementById("logFilterImportIds")?.value || "";

    const fileNamesRaw =
        document.getElementById("logFilterFileNames")?.value || "";

    const eventType =
        document.getElementById("logFilterEventType")?.value?.trim() || "";

    const processName =
        document.getElementById("logFilterProcessName")?.value?.trim() || "";

    const userName =
        document.getElementById("logFilterUserName")?.value?.trim() || "";

    const sessionId =
        document.getElementById("logFilterSessionId")?.value?.trim() || "";

    const uuid =
        document.getElementById("logFilterUuid")?.value?.trim() || "";

    const dateFrom =
        document.getElementById("logFilterDateFrom")?.value || "";

    const dateTo =
        document.getElementById("logFilterDateTo")?.value || "";

    const groupBy =
        document.getElementById("logGroupBy")?.value || "sessionId";

    const limit =
        document.getElementById("logFilterLimit")?.value?.trim() || "100";

    const errorOnly =
        forceErrorOnly ||
        Boolean(document.getElementById("logFilterErrorOnly")?.checked);

    const importIds = parseCsvLongs(importIdsRaw);
    const fileNames = parseCsvStrings(fileNamesRaw);

    if (!importIds.ok) {
        throw new Error(
            "Le filtre Import IDs doit contenir uniquement des nombres."
        );
    }

    if (limit && !/^\d+$/.test(limit)) {
        throw new Error("La limite doit être numérique. Mets 0 pour charger tous les logs.");
    }

    if (dateFrom && dateTo && dateFrom > dateTo) {
        throw new Error(
            "La date début doit être avant la date fin."
        );
    }

    return {
        importIds: importIds.values,
        fileNames,
        eventType,
        processName,
        userName,
        sessionId,
        uuid,
        dateFrom,
        dateTo,
        groupBy,
        limit,
        errorOnly
    };
}
function renderGroupsResult(groups, filters) {
    document.getElementById("groupsResultSection")
        ?.classList.remove("hidden");

    const groupsResult =
        document.getElementById("groupsResult");

    const groupsSummary =
        document.getElementById("groupsSummary");

    if (groupsSummary) {
        groupsSummary.textContent =
            `${groups.length} groupe(s) | groupBy=${filters.groupBy || "sessionId"} | importIds=${filters.importIds?.length ? filters.importIds.join(",") : "tous"}`;
    }

    if (!groupsResult) return;

    if (!groups.length) {
        groupsResult.innerHTML =
            `<div class="empty-state">Aucun groupe trouvé avec ces filtres.</div>`;

        return;
    }

    groupsResult.innerHTML = `
        <div class="groups-list">
            ${groups.map(g => renderGroupCard(g, filters)).join("")}
        </div>
    `;

    document.getElementById("groupsResultSection")
        ?.scrollIntoView({
            behavior: "smooth",
            block: "start"
        });
}

function renderGroupCard(group, filters) {
    const errorClass =
        Number(group.errorCount || 0) > 0
            ? "error-pill"
            : "info-pill";

    return `
        <div class="group-card">

            <h3>
                ${escapeHtml(group.groupBy || "Groupe")}
                :
                ${escapeHtml(group.groupKey || "NON_RENSEIGNE")}
            </h3>

            <div class="log-meta">

                <span class="log-pill">
                    Total : ${escapeHtml(group.totalLogs ?? 0)}
                </span>

                <span class="log-pill ${errorClass}">
                    Erreurs : ${escapeHtml(group.errorCount ?? 0)}
                </span>

                <span class="log-pill">
                    Warnings : ${escapeHtml(group.warningCount ?? 0)}
                </span>

                <span class="log-pill">
                    Infos : ${escapeHtml(group.infoCount ?? 0)}
                </span>

            </div>

            <div class="workflow-section">

                <strong>Période</strong>

                <div>
                    ${escapeHtml(group.firstTimestamp || "N/A")}
                    →
                    ${escapeHtml(group.lastTimestamp || "N/A")}
                </div>

            </div>

            <div class="workflow-section">

                <strong>Contexte dominant</strong>

                <div>
                    Utilisateur :
                    ${escapeHtml(group.mainUserName || "N/A")}
                    |
                    Process :
                    ${escapeHtml(group.mainProcessName || "N/A")}
                    |
                    Fichier :
                    ${escapeHtml(group.mainFileName || "N/A")}
                </div>

            </div>

            <div class="workflow-section">

                <strong>Résumé</strong>

                <div>
                    ${escapeHtml(group.summary || "")}
                </div>

            </div>

            <div class="workflow-actions">

                <button
                    type="button"
                    onclick="loadLogsFromGroup(
                        '${escapeJs(group.groupBy || "")}',
                        '${escapeJs(group.groupKey || "")}'
                    )">

                    Consulter les logs de ce groupe

                </button>

                <button
                    type="button"
                    class="secondary-btn"
                    onclick="analyzeLogGroup(
                        '${escapeJs(group.groupBy || "")}',
                        '${escapeJs(group.groupKey || "")}'
                    )">

                    Analyser ce groupe

                </button>

                <button
                    type="button"
                    class="secondary-btn"
                    onclick="loadWorkflowGraph(
                        '${escapeJs(group.groupBy || "")}',
                        '${escapeJs(group.groupKey || "")}'
                    )">

                    Graphe relationnel

                </button>

            </div>

        </div>
    `;
}

async function analyzeLogGroup(groupBy, groupKey) {
    try {
        const filters = getLogFilters(false);

        if (!filters.importIds || filters.importIds.length === 0) {
            showMessage("Veuillez saisir au moins un Import ID avant d’analyser le groupe.", "error");
            return;
        }

        showMessage("Analyse intelligente du groupe en cours...", "info");

        const payload = {
            importIds: filters.importIds,
            groupBy: groupBy,
            groupKey: groupKey,
            dateFrom: filters.dateFrom ? toBackendDateTime(filters.dateFrom) : null,
            dateTo: filters.dateTo ? toBackendDateTime(filters.dateTo) : null
        };

        const response = await authFetch("/assistant/analyze-group", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        });

        const data = await safeJson(response);

        if (!response.ok) {
            throw new Error(data?.message || "Erreur analyse groupe.");
        }

        renderGroupAnalysis(data);
        showMessage("Analyse du groupe terminée.", "success");

    } catch (e) {
        showMessage(e.message || "Erreur lors de l’analyse du groupe.", "error");
    }
}

function renderGroupAnalysis(data) {
    window.currentAnalyzedGroupBy = data.groupBy || "";
    window.currentAnalyzedGroupKey = data.groupKey || "";

    const section = document.getElementById("groupAnalysisSection");
    const container = document.getElementById("groupAnalysisResult");

    section?.classList.remove("hidden");
    if (!container) return;

    container.innerHTML = `
        <div class="analysis-story">
            <div class="analysis-kpis">
                <span class="log-pill">Groupe : ${escapeHtml(data.groupBy || "")} = ${escapeHtml(data.groupKey || "")}</span>
                <span class="log-pill">Total logs : ${escapeHtml(data.totalLogs ?? 0)}</span>
                <span class="log-pill error-pill">Erreurs : ${escapeHtml(data.errorCount ?? 0)}</span>
                <span class="log-pill">Warnings : ${escapeHtml(data.warningCount ?? 0)}</span>
                <span class="log-pill">0 résultat : ${escapeHtml(data.zeroResultCount ?? 0)}</span>
            </div>

            <div class="analysis-block">
                <h3>Histoire chronologique concise</h3>
                <p>${escapeHtml(data.narrative || "")}</p>
            </div>

            <div class="analysis-block">
                <h3>Conclusion</h3>
                <p>${escapeHtml(data.conclusion || "")}</p>
            </div>

            <div class="analysis-block">
                <h3>Recommandation</h3>
                <p>${escapeHtml(data.recommendation || "")}</p>
            </div>

            ${renderTimelineSection(data.timeline || [])}

            ${renderAnalysisItems("Process détectés", data.processes || [])}
            ${renderAnalysisItems("Actions détectées", data.actions || [])}
            ${renderAnalysisItems("Filtres utilisés", data.filters || [])}
            ${renderAnalysisItems("Objets métier", data.businessObjects || [])}
            ${renderAnalysisItems("Warnings / règles absentes", data.warnings || [])}
            ${renderAnalysisItems("Recherches avec 0 résultat", data.zeroResults || [])}
            ${renderAnalysisItems("Signaux performance / mémoire", data.performanceSignals || [])}
        </div>
    `;

    section?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function renderTimelineSection(timeline) {
    if (!timeline.length) {
        return `
            <div class="analysis-block">
                <h3>Timeline importante</h3>
                <div class="empty-state">Aucune timeline reconstruite.</div>
            </div>
        `;
    }

    return `
        <div class="analysis-block">
            <h3>Timeline importante</h3>
            <div class="analysis-timeline">
                ${timeline.map(item => `
                    <div class="analysis-timeline-line">${escapeHtml(item)}</div>
                `).join("")}
            </div>
        </div>
    `;
}

function renderAnalysisItems(title, items) {
    if (!items.length) {
        return `
            <div class="analysis-block">
                <h3>${escapeHtml(title)}</h3>
                <div class="empty-state">Aucun élément détecté.</div>
            </div>
        `;
    }

    return `
        <div class="analysis-block">
            <h3>${escapeHtml(title)} (${items.length})</h3>

            <div class="analysis-items">
                ${items.map(item => renderAnalysisItem(item)).join("")}
            </div>
        </div>
    `;
}

function renderAnalysisItem(item) {
    const examples = Array.isArray(item.examples) ? item.examples : [];

    return `
        <details class="analysis-item">
            <summary>
                <strong>${escapeHtml(item.name || "N/A")}</strong>
                <span> — ${escapeHtml(item.count ?? 0)} occurrence(s)</span>
                <span> — ${escapeHtml(item.firstTimestamp || "N/A")} → ${escapeHtml(item.lastTimestamp || "N/A")}</span>
            </summary>

            <div class="analysis-item-body">
                <div><strong>Diagnostic :</strong> ${escapeHtml(item.diagnostic || "")}</div>

                <div class="analysis-item-actions">
                    <button type="button" onclick="loadRelatedLogsForItem(
                        '${escapeJs(item.type || "")}',
                        '${escapeJs(item.name || "")}',
                        null,
                        null
                    )">
                        Voir logs liés
                    </button>

                    <button type="button" class="secondary-btn" onclick="loadRelatedLogsForItem(
                        '${escapeJs(item.type || "")}',
                        '${escapeJs(item.name || "")}',
                        '${escapeJs(item.firstTimestamp || "")}',
                        '${escapeJs(item.lastTimestamp || "")}'
                    )">
                        Voir logs entre ces timestamps
                    </button>
                </div>

                ${
        examples.length
            ? `
                            <div class="analysis-examples">
                                <strong>Exemples :</strong>
                                ${examples.map(ex => `<pre>${escapeHtml(ex)}</pre>`).join("")}
                            </div>
                          `
            : ""
    }
            </div>
        </details>
    `;
}

async function loadRelatedLogsForItem(itemType, itemName, dateFrom, dateTo) {
    try {
        const filters = getLogFilters(false);

        if (!filters.importIds || filters.importIds.length === 0) {
            showMessage("Veuillez saisir au moins un Import ID.", "error");
            return;
        }

        const currentGroup = getCurrentAnalyzedGroup();

        showMessage("Chargement des logs liés...", "info");

        const payload = {
            importIds: filters.importIds,
            groupBy: currentGroup.groupBy,
            groupKey: currentGroup.groupKey,
            itemType: itemType,
            itemName: itemName,
            dateFrom: dateFrom || (filters.dateFrom ? toBackendDateTime(filters.dateFrom) : null),
            dateTo: dateTo || (filters.dateTo ? toBackendDateTime(filters.dateTo) : null),
            limit: Number(filters.limit || 300)
        };

        const response = await authFetch("/assistant/related-logs", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        });

        const data = await safeJson(response);

        if (!response.ok) {
            throw new Error(data?.message || "Erreur chargement logs liés.");
        }

        renderRelatedLogs(data);
        showMessage("Logs liés chargés.", "success");

    } catch (e) {
        showMessage(e.message || "Erreur lors du chargement des logs liés.", "error");
    }
}

function getCurrentAnalyzedGroup() {
    return {
        groupBy: window.currentAnalyzedGroupBy || "",
        groupKey: window.currentAnalyzedGroupKey || ""
    };
}

function renderRelatedLogs(data) {
    const section = document.getElementById("relatedLogsSection");
    const container = document.getElementById("relatedLogsResult");

    section?.classList.remove("hidden");
    if (!container) return;

    const logs = Array.isArray(data.logs) ? data.logs : [];

    container.innerHTML = `
        <div class="related-logs-box">
            <div class="analysis-kpis">
                <span class="log-pill">${escapeHtml(data.title || "Logs liés")}</span>
                <span class="log-pill">Total : ${escapeHtml(data.total ?? logs.length)}</span>
            </div>

            <div class="analysis-block">
                <h3>Résumé</h3>
                <p>${escapeHtml(data.summary || "")}</p>
            </div>

            ${data.chronologicalExplanation
        ? `
                    <div class="analysis-block">
                        <h3>Lecture chronologique avant/après</h3>
                        <div class="chronological-context-box">
                            ${renderChronologicalExplanation(data.chronologicalExplanation || "")}
                        </div>
                    </div>
                  `
        : ""
    }

            ${
        logs.length
            ? `<div class="related-logs-list">${logs.map(renderRelatedLogCard).join("")}</div>`
            : `<div class="empty-state">Aucun log lié trouvé.</div>`
    }
        </div>
    `;

    section?.scrollIntoView({ behavior: "smooth", block: "start" });
    setTimeout(scrollToSelectedChronoLine, 300);
}

function renderChronologicalExplanation(text) {
    if (!text) return "";

    const lines = text.split("\n");

    return lines.map(line => {
        const isSelected = line.includes(">>> MOMENT SÉLECTIONNÉ");

        return `
            <div class="chrono-line ${isSelected ? "chrono-selected" : ""}">
                ${escapeHtml(line)}
            </div>
        `;
    }).join("");
}

function scrollToSelectedChronoLine() {
    const selected = document.querySelector(".chrono-selected");
    if (selected) {
        selected.scrollIntoView({
            behavior: "smooth",
            block: "center"
        });
    }
}

function renderRelatedLogCard(log) {
    const isError = String(log.level || "").toUpperCase() === "ERROR";
    const levelClass = isError ? "error-pill" : "info-pill";

    return `
        <div class="related-log-card">
            <div class="log-meta">
                <span class="log-pill ${levelClass}">Level : ${escapeHtml(log.level || "N/A")}</span>
                <span class="log-pill">Time : ${escapeHtml(log.timestamp || log.logTimestamp || "N/A")}</span>
                <span class="log-pill">User : ${escapeHtml(log.userName || "N/A")}</span>
                <span class="log-pill">Process : ${escapeHtml(log.processName || "N/A")}</span>
                <span class="log-pill">Fichier : ${escapeHtml(log.sourceFileName || log.fileName || "N/A")}</span>
            </div>

            <div class="log-section">
                <strong>Explication détaillée de cette ligne</strong>
                <div>${escapeHtml(log.detailedExplanation || "Aucune explication détaillée.")}</div>
            </div>

            <div class="log-section">
                <strong>Explication métier existante</strong>
                <div>${escapeHtml(log.businessMeaning || "Aucune explication métier disponible.")}</div>
            </div>

            <div class="log-section">
                <strong>Message réel</strong>
                <div class="log-message">${escapeHtml(log.message || "")}</div>
            </div>
        </div>
    `;
}

async function loadLogsFromGroup(groupBy, groupKey) {
    try {
        if (!groupBy || !groupKey) {
            showMessage("Groupe invalide.", "error");
            return;
        }

        const key =
            groupKey === "NON_RENSEIGNE"
                ? ""
                : groupKey;

        clearGroupDrivenFilters();

        switch (groupBy) {
            case "sessionId":
                setInputValue("logFilterSessionId", key);
                break;

            case "userName":
                setInputValue("logFilterUserName", key);
                break;

            case "processName":
                setInputValue("logFilterProcessName", key);
                break;

            case "eventType":
                setInputValue("logFilterEventType", key);
                break;

            case "sourceFileName":
            case "fileName":
                setInputValue("logFilterFileNames", key);
                break;

            case "uuid":
            case "businessKey":
            case "correlationId":
                setInputValue("logFilterUuid", key);
                break;

            default:
                break;
        }

        showMessage("Chargement des logs du groupe...", "info");

        const filters = getLogFilters(false);

        const result = await fetchFilteredLogs(filters);

        renderLogsResult(result, filters);

        showMessage("Logs du groupe chargés.", "success");

    } catch (e) {
        showMessage(
            e.message || "Erreur lors du chargement du groupe.",
            "error"
        );
    }
}
function clearGroupDrivenFilters() {
    setInputValue("logFilterSessionId", "");
    setInputValue("logFilterUserName", "");
    setInputValue("logFilterProcessName", "");
    setInputValue("logFilterEventType", "");
    setInputValue("logFilterUuid", "");
}

function setInputValue(id, value) {
    const el = document.getElementById(id);

    if (el) {
        el.value = value || "";
    }
}

function switchLogView(mode) {
    currentLogViewMode = mode;

    document.getElementById("viewDetailedBtn")?.classList.remove("active");
    document.getElementById("viewMessagesBtn")?.classList.remove("active");
    document.getElementById("viewRawBtn")?.classList.remove("active");

    if (mode === "detailed") {
        document.getElementById("viewDetailedBtn")?.classList.add("active");
    }

    if (mode === "messages") {
        document.getElementById("viewMessagesBtn")?.classList.add("active");
    }

    if (mode === "raw") {
        document.getElementById("viewRawBtn")?.classList.add("active");
    }

    if (window.lastRenderedLogs && window.lastRenderedFilters) {
        renderLogsResult(window.lastRenderedLogs, window.lastRenderedFilters);
    }
}

function renderLogsResult(logs, filters) {
    window.lastRenderedLogs = logs;
    window.lastRenderedFilters = filters;

    document.getElementById("logsResultSection")
        ?.classList.remove("hidden");

    const logsResult =
        document.getElementById("logsResult");

    const logsSummary =
        document.getElementById("logsSummary");

    if (logsSummary) {
        logsSummary.textContent =
            `${logs.length} log(s)
            | importIds=${filters.importIds?.length ? filters.importIds.join(",") : "tous"}
            | fileNames=${filters.fileNames?.length ? filters.fileNames.join(",") : "tous"}
            | userName=${filters.userName || "tous"}
            | eventType=${filters.eventType || "tous"}
            | processName=${filters.processName || "tous"}
            | sessionId=${filters.sessionId || "tous"}
            | uuid=${filters.uuid || "tous"}
            | période=${filters.dateFrom || "début"} → ${filters.dateTo || "fin"}
            | errorOnly=${filters.errorOnly ? "oui" : "non"}`;
    }

    if (!logsResult) return;

    if (!logs.length) {
        logsResult.innerHTML =
            `<div class="empty-state">Aucun log trouvé avec ces filtres.</div>`;

        return;
    }

    if (currentLogViewMode === "messages") {
        logsResult.innerHTML = renderMessagesOnly(logs);
    } else if (currentLogViewMode === "raw") {
        logsResult.innerHTML = renderRawLines(logs);
    } else {
        logsResult.innerHTML = `
            <div class="logs-list">
                ${logs.map(renderLogCard).join("")}
            </div>
        `;
    }

    document.getElementById("logsResultSection")
        ?.scrollIntoView({
            behavior: "smooth",
            block: "start"
        });
}

function renderMessagesOnly(logs) {
    return `
        <div class="messages-only-view">
            ${logs.map(log => `
                <div class="message-line">
                    <span class="msg-time">${escapeHtml(shortTimestamp(log.logTimestamp))}</span>
                    <span class="msg-text">${escapeHtml(log.message || "")}</span>
                </div>
            `).join("")}
        </div>
    `;
}

function renderRawLines(logs) {
    return `
        <div class="raw-lines-view">
            ${logs.map(log => `
                <div class="raw-line">${escapeHtml(buildRawLine(log))}</div>
            `).join("")}
        </div>
    `;
}

function buildRawLine(log) {
    const raw =
        log.rawLine ||
        log.rawLog ||
        log.fullRawLine ||
        log.originalLine ||
        "";

    if (raw) {
        return raw;
    }

    return [
        log.logTimestamp || "",
        log.level || "",
        log.userName || "",
        log.processName || "",
        log.message || ""
    ].filter(part => String(part).trim() !== "").join(" ");
}

function shortTimestamp(ts) {
    if (!ts) return "";

    const value = String(ts).replace("T", " ");

    if (value.length >= 23) {
        return value.substring(11, 23);
    }

    if (value.length >= 19) {
        return value.substring(11, 19);
    }

    return value;
}

function renderLogCard(log) {
    const isError =
        Boolean(log.isError)
        || String(log.level || "").toUpperCase() === "ERROR";

    const levelClass =
        isError
            ? "error-pill"
            : "info-pill";

    return `
        <div class="log-card">

            <h3>
                Log #${escapeHtml(log.id ?? "N/A")}
            </h3>

            <div class="log-meta">

                <span class="log-pill ${levelClass}">
                    Level :
                    ${escapeHtml(log.level || "N/A")}
                </span>

                <span class="log-pill">
                    Timestamp :
                    ${escapeHtml(log.logTimestamp || "N/A")}
                </span>

                <span class="log-pill">
                    Import :
                    ${escapeHtml(log.importId ?? log.logImportId ?? "N/A")}
                </span>

                ${log.fileName
        ? `<span class="log-pill">
                        Fichier :
                        ${escapeHtml(log.fileName)}
                    </span>`
        : ""}

                ${log.userName
        ? `<span class="log-pill">
                        User :
                        ${escapeHtml(log.userName)}
                    </span>`
        : ""}

                ${log.sourceRelativePath
        ? `<span class="log-pill">
                        Chemin :
                        ${escapeHtml(log.sourceRelativePath)}
                    </span>`
        : ""}

            </div>

            <div class="log-section">

                <strong>Explication de la ligne</strong>

                <div>
                    ${escapeHtml(log.businessMeaning || "Aucune explication disponible.")}
                </div>

            </div>

            <div class="log-section">

                <strong>Message réel</strong>

                <div class="log-message">
                    ${escapeHtml(log.message || "")}
                </div>

            </div>

            <details class="log-details">

                <summary>Détails techniques</summary>

                <div class="log-details-grid">

                    <div>
                        <strong>Event Type</strong>
                        <div>${escapeHtml(log.eventType || "N/A")}</div>
                    </div>

                    <div>
                        <strong>Process</strong>
                        <div>${escapeHtml(log.processName || "N/A")}</div>
                    </div>

                    <div>
                        <strong>Source</strong>
                        <div>${escapeHtml(log.sourceClass || "N/A")}</div>
                    </div>

                    <div>
                        <strong>Session</strong>
                        <div>${escapeHtml(log.sessionId || "N/A")}</div>
                    </div>

                    <div>
                        <strong>UUID</strong>
                        <div>${escapeHtml(log.uuid || "N/A")}</div>
                    </div>

                    <div>
                        <strong>Erreur</strong>
                        <div>${isError ? "Oui" : "Non"}</div>
                    </div>

                </div>

            </details>

        </div>
    `;
}

async function loadWorkflowGraph(groupBy, groupKey) {
    try {
        const filters = getLogFilters(false);

        if (!filters.importIds || filters.importIds.length === 0) {
            showMessage("Veuillez saisir au moins un Import ID.", "error");
            return;
        }

        showMessage("Construction du graphe relationnel sur la période sélectionnée...", "info");

        const payload = {
            importIds: filters.importIds,
            groupBy: groupBy,
            groupKey: groupKey,
            dateFrom: filters.dateFrom ? toBackendDateTime(filters.dateFrom) : null,
            dateTo: filters.dateTo ? toBackendDateTime(filters.dateTo) : null
        };

        const response = await authFetch("/assistant/workflow-graph", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        });

        const data = await safeJson(response);

        if (!response.ok) {
            throw new Error(data?.message || "Erreur graphe relationnel.");
        }

        data.dateFrom = data.dateFrom || payload.dateFrom;
        data.dateTo = data.dateTo || payload.dateTo;

        window.currentWorkflowGraph = data;
        window.currentAnalyzedGroupBy = data.groupBy || groupBy || "";
        window.currentAnalyzedGroupKey = data.groupKey || groupKey || "";

        renderWorkflowGraph(data);
        showMessage("Graphe relationnel chargé.", "success");

    } catch (e) {
        showMessage(e.message || "Erreur lors du chargement du graphe.", "error");
    }
}

function renderWorkflowGraph(graph) {
    const section = document.getElementById("workflowGraphSection");
    const container = document.getElementById("workflowGraphResult");

    section?.classList.remove("hidden");
    if (!container) return;

    const nodes = Array.isArray(graph.nodes) ? graph.nodes : [];
    const edges = Array.isArray(graph.edges) ? graph.edges : [];

    container.innerHTML = `
        <div class="graph-layout">
            <div class="graph-sidebar">
                <div class="analysis-kpis">
                    <span class="log-pill">Groupe : ${escapeHtml(graph.groupBy || "")} = ${escapeHtml(graph.groupKey || "")}</span>
                    <span class="log-pill">Logs : ${escapeHtml(graph.totalLogs ?? 0)}</span>
                    <span class="log-pill">Nodes : ${nodes.length}</span>
                    <span class="log-pill">Relations : ${edges.length}</span>
                    <span class="log-pill">Période : ${escapeHtml(graph.dateFrom || "début")} → ${escapeHtml(graph.dateTo || "fin")}</span>
                </div>

                <div class="graph-search-box">
                    <label for="graphNodeSearch">Chercher dans le graphe</label>
                    <input
                        id="graphNodeSearch"
                        type="text"
                        placeholder="Ex: DML, ZRE22, Valider, ServicePortuaire..."
                        oninput="filterGraphNodes()"
                    />
                </div>

                ${renderGraphNodeGroups(nodes)}
            </div>

            <div class="graph-details" id="graphDetails">
                <div class="empty-state">Clique sur un process, filtre, action, objet, warning ou résultat pour voir ses relations.</div>
            </div>
        </div>
    `;

    section?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function renderGraphNodeGroups(nodes) {
    const grouped = {};

    nodes.forEach(n => {
        const type = n.type || "OTHER";
        if (!grouped[type]) grouped[type] = [];
        grouped[type].push(n);
    });

    return Object.keys(grouped).sort().map(type => `
        <div class="graph-node-group">
            <h3>${escapeHtml(type)} (${grouped[type].length})</h3>
            <div class="graph-node-list">
                ${grouped[type]
        .sort((a, b) => Number(b.count || 0) - Number(a.count || 0))
        .map(node => renderGraphNodeButton(node))
        .join("")}
            </div>
        </div>
    `).join("");
}

function renderGraphNodeButton(node) {
    const severityClass = graphSeverityClass(node.severity);
    const searchText = `${node.type || ""} ${node.label || ""}`.toLowerCase();

    return `
        <button type="button"
                class="graph-node-btn ${severityClass}"
                data-graph-node="true"
                data-search="${escapeHtml(searchText)}"
                onclick="selectGraphNode('${escapeJs(node.id || "")}')">
            <span>${escapeHtml(node.label || node.id || "N/A")}</span>
            <small>${escapeHtml(node.count ?? 0)} occurrence(s)</small>
        </button>
    `;
}

function selectGraphNode(nodeId) {
    const graph = window.currentWorkflowGraph;
    if (!graph) return;

    const nodes = Array.isArray(graph.nodes) ? graph.nodes : [];
    const edges = Array.isArray(graph.edges) ? graph.edges : [];

    const node = nodes.find(n => n.id === nodeId);
    if (!node) return;

    const outgoing = edges.filter(e => e.from === nodeId);
    const incoming = edges.filter(e => e.to === nodeId);

    const linkedNodeIds = new Set([
        ...outgoing.map(e => e.to),
        ...incoming.map(e => e.from)
    ]);

    const linkedNodes = nodes.filter(n => linkedNodeIds.has(n.id));

    const details = document.getElementById("graphDetails");
    if (!details) return;

    details.innerHTML = `
        <div class="graph-detail-card">
            <h3>${escapeHtml(node.type || "")} : ${escapeHtml(node.label || "")}</h3>

            <div class="analysis-kpis">
                <span class="log-pill">Occurrences : ${escapeHtml(node.count ?? 0)}</span>
                <span class="log-pill ${graphSeverityClass(node.severity)}">Criticité : ${escapeHtml(node.severity || "INFO")}</span>
            </div>

            <p>${escapeHtml(node.description || "")}</p>

            <div class="analysis-item-actions">
                <button type="button" onclick="loadRelatedLogsForItem(
                    '${escapeJs(node.type || "")}',
                    '${escapeJs(node.label || "")}',
                    null,
                    null
                )">
                    Voir logs liés
                </button>

                <button type="button" class="secondary-btn" onclick="analyzeGraphNodeWithContext(
                    '${escapeJs(node.type || "")}',
                    '${escapeJs(node.label || "")}'
                )">
                    Préparer contexte IA
                </button>
            </div>

            ${renderNodeOccurrences(node)}

            ${renderGraphRelations("Relations sortantes", outgoing, nodes)}
            ${renderGraphRelations("Relations entrantes", incoming, nodes)}
            ${renderLinkedNodes(linkedNodes)}
            ${renderNodeExamples(node)}
        </div>
    `;
}

function renderNodeOccurrences(node) {
    const occurrences = Array.isArray(node.occurrences) ? node.occurrences : [];

    if (!occurrences.length) {
        return `
            <div class="analysis-block">
                <h3>Occurrences temporelles</h3>
                <div class="empty-state">Aucune occurrence disponible.</div>
            </div>
        `;
    }

    return `
        <div class="analysis-block">
            <h3>Occurrences temporelles (${occurrences.length})</h3>
            <div class="occurrence-list">
                ${occurrences.map(occ => {
        const logId = Number(occ.logId || 0);
        const disabledAttr = logId > 0 ? "" : "disabled";

        return `
                        <div class="occurrence-line">
                            <div>
                                <strong>${escapeHtml(occ.timestamp || "N/A")}</strong><br>
                                <small>
                                    Process: ${escapeHtml(occ.processName || "N/A")} |
                                    Action: ${escapeHtml(occ.actionName || "N/A")} |
                                    Filtre: ${escapeHtml(occ.filterCode || "N/A")} |
                                    Objet: ${escapeHtml(occ.businessObject || "N/A")}
                                </small>
                                <pre>${escapeHtml(occ.messagePreview || "")}</pre>
                            </div>
                            <div class="occurrence-actions">
                                <button type="button" ${disabledAttr} onclick="loadContextAroundLog(${logId})">
                                    Voir avant/après
                                </button>
                            </div>
                        </div>
                    `;
    }).join("")}
            </div>
        </div>
    `;
}

function getContextBeforeAfterCounts() {
    const beforeRaw = document.getElementById("contextBeforeCount")?.value || "20";
    const afterRaw = document.getElementById("contextAfterCount")?.value || "20";

    let before = Number(beforeRaw);
    let after = Number(afterRaw);

    if (!Number.isFinite(before) || before < 0) before = 20;
    if (!Number.isFinite(after) || after < 0) after = 20;

    before = Math.min(before, 100);
    after = Math.min(after, 100);

    return { before, after };
}

async function loadContextAroundLog(logId, buttonElement = null) {
    try {
        const filters = getLogFilters(false);
        const currentGroup = getCurrentGraphGroup();

        let before = null;
        let after = null;

        if (buttonElement) {
            const container = buttonElement.closest(".context-inline-controls");

            before = container?.querySelector(".inline-before-input")?.value?.trim();
            after = container?.querySelector(".inline-after-input")?.value?.trim();
        }

        const globalBefore = document.getElementById("contextBeforeCount")?.value?.trim() || "20";
        const globalAfter = document.getElementById("contextAfterCount")?.value?.trim() || "20";

        before = before !== null && before !== "" ? before : globalBefore;
        after = after !== null && after !== "" ? after : globalAfter;

        before = Number(before);
        after = Number(after);

        if (!Number.isFinite(before) || before < 0) before = 20;
        if (!Number.isFinite(after) || after < 0) after = 20;

        before = Math.min(before, 500);
        after = Math.min(after, 500);

        if (!filters.importIds || filters.importIds.length === 0) {
            showMessage("Veuillez saisir au moins un Import ID.", "error");
            return;
        }

        showMessage(`Chargement du contexte ${before} avant / ${after} après...`, "info");

        const payload = {
            importIds: filters.importIds,
            groupBy: currentGroup.groupBy,
            groupKey: currentGroup.groupKey,
            centerLogId: Number(logId),
            before: before,
            after: after
        };

        const response = await authFetch("/assistant/related-context", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        });

        const data = await safeJson(response);

        if (!response.ok) {
            throw new Error(data?.message || "Erreur contexte avant/après.");
        }

        renderRelatedLogs(data);
        showMessage("Contexte avant/après chargé.", "success");

    } catch (e) {
        showMessage(e.message || "Erreur lors du chargement du contexte.", "error");
    }
}


function getCurrentGraphGroup() {
    const graph = window.currentWorkflowGraph || {};
    return {
        groupBy: graph.groupBy || "",
        groupKey: graph.groupKey || ""
    };
}

function renderGraphRelations(title, relations, nodes) {
    if (!relations.length) {
        return `
            <div class="analysis-block">
                <h3>${escapeHtml(title)}</h3>
                <div class="empty-state">Aucune relation.</div>
            </div>
        `;
    }

    return `
        <div class="analysis-block">
            <h3>${escapeHtml(title)} (${relations.length})</h3>
            <div class="graph-relations">
                ${relations.map(edge => {
        const targetId = title.includes("sortantes") ? edge.to : edge.from;
        const target = nodes.find(n => n.id === targetId);
        return `
                        <div class="graph-relation-line">
                            <span class="log-pill">${escapeHtml(edge.relation || "")}</span>
                            <button type="button" onclick="selectGraphRelation(
                                '${escapeJs(edge.from || "")}',
                                '${escapeJs(edge.to || "")}',
                                '${escapeJs(edge.relation || "")}'
                            )">
                                ${escapeHtml(target?.type || "")} : ${escapeHtml(target?.label || targetId)}
                            </button>
                            <span>${escapeHtml(edge.count ?? 0)} fois</span>
                        </div>
                    `;
    }).join("")}
            </div>
        </div>
    `;
}

function selectGraphRelation(from, to, relation) {
    const graph = window.currentWorkflowGraph;
    if (!graph) return;

    const nodes = Array.isArray(graph.nodes) ? graph.nodes : [];
    const edges = Array.isArray(graph.edges) ? graph.edges : [];

    const edge = edges.find(e =>
        e.from === from &&
        e.to === to &&
        e.relation === relation
    );

    if (!edge) return;

    const fromNode = nodes.find(n => n.id === from);
    const toNode = nodes.find(n => n.id === to);

    const details = document.getElementById("graphDetails");
    if (!details) return;

    const fromOutgoing = edges.filter(e => e.from === from);
    const fromIncoming = edges.filter(e => e.to === from);

    details.innerHTML = `
        <div class="graph-detail-card">

            <h3>Relation : ${escapeHtml(edge.relation || "")}</h3>

            <div class="analysis-kpis">
                <span class="log-pill">De : ${escapeHtml(fromNode?.type || "")} - ${escapeHtml(fromNode?.label || from)}</span>
                <span class="log-pill">Vers : ${escapeHtml(toNode?.type || "")} - ${escapeHtml(toNode?.label || to)}</span>
                <span class="log-pill">Occurrences : ${escapeHtml(edge.count ?? 0)}</span>
            </div>

            <div class="analysis-block">
                <h3>Occurrences temporelles de cette relation</h3>
                ${renderRelationOccurrences(edge)}
            </div>

            <div class="analysis-block">
                <h3>Autres relations liées à ${escapeHtml(fromNode?.label || from)}</h3>
                ${renderGraphRelations("Relations sortantes", fromOutgoing, nodes)}
                ${renderGraphRelations("Relations entrantes", fromIncoming, nodes)}
            </div>

        </div>
    `;
}

function renderRelationOccurrences(edge) {
    const occurrences = Array.isArray(edge.occurrences) ? edge.occurrences : [];

    if (!occurrences.length) {
        return `<div class="empty-state">Aucune occurrence disponible pour cette relation.</div>`;
    }

    return `
        <div class="occurrence-list">
            ${occurrences.map(occ => {
        const logId = Number(occ.logId || 0);
        const disabledAttr = logId > 0 ? "" : "disabled";

        return `
                        <div class="occurrence-line">
                            <div>
                                <strong>${escapeHtml(occ.timestamp || "N/A")}</strong><br>
                                <small>
                                    Process: ${escapeHtml(occ.processName || "N/A")} |
                                    Action: ${escapeHtml(occ.actionName || "N/A")} |
                                    Filtre: ${escapeHtml(occ.filterCode || "N/A")} |
                                    Objet: ${escapeHtml(occ.businessObject || "N/A")}
                                </small>
                                <pre>${escapeHtml(occ.messagePreview || "")}</pre>
                            </div>
                            <div class="occurrence-actions">
                               <div class="context-inline-controls">

    <button
        type="button"
        onclick="
            loadContextAroundLog(
                ${Number(occ.logId)},
                this
            )
        "
    >
        Voir avant/après
    </button>

    <div class="inline-before-after">

        <label>
            Avant:
            <input
                type="number"
                class="inline-before-input"
                min="0"
                placeholder="défaut"
            />
        </label>

        <label>
            Après:
            <input
                type="number"
                class="inline-after-input"
                min="0"
                placeholder="défaut"
            />
        </label>

    </div>

</div>
                            </div>
                        </div>
                    `;
    }).join("")}
        </div>
    `;
}

function renderLinkedNodes(nodes) {
    if (!nodes.length) return "";

    return `
        <div class="analysis-block">
            <h3>Éléments liés</h3>
            <div class="graph-linked-nodes">
                ${nodes.map(n => `
                    <button type="button" class="graph-chip ${graphSeverityClass(n.severity)}"
                            onclick="selectGraphNode('${escapeJs(n.id || "")}')">
                        ${escapeHtml(n.type || "")} : ${escapeHtml(n.label || "")}
                    </button>
                `).join("")}
            </div>
        </div>
    `;
}

function renderNodeExamples(node) {
    const examples = Array.isArray(node.examples) ? node.examples : [];

    if (!examples.length) return "";

    return `
        <div class="analysis-block">
            <h3>Exemples de logs</h3>
            ${examples.map(ex => `<pre>${escapeHtml(ex)}</pre>`).join("")}
        </div>
    `;
}

async function analyzeGraphNodeWithContext(itemType, itemName) {
    showMessage("Pour l’instant, utilise 'Voir logs liés'. Le contexte IA détaillé du node sera branché ensuite.", "info");
}

function graphSeverityClass(severity) {
    const s = String(severity || "").toUpperCase();

    if (s === "CRITICAL" || s === "ERROR") return "error-pill";
    if (s === "SUSPECT" || s === "WARNING") return "warning-pill";
    return "info-pill";
}

function onClearWorkflowGraphClick() {
    const container = document.getElementById("workflowGraphResult");
    if (container) container.innerHTML = "";

    document.getElementById("workflowGraphSection")?.classList.add("hidden");
    window.currentWorkflowGraph = null;

    showMessage("Graphe relationnel vidé.", "info");
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
        "logFilterUserName",
        "logFilterDateFrom",
        "logFilterDateTo",
        "logGroupBy",
        "groupsResult",
        "groupsSummary",
        "groupAnalysisResult",
        "relatedLogsResult",
        "logFilterLimit",
        "importsResult"
    ];

    ids.forEach(id => {
        const el = document.getElementById(id);

        if (!el) return;

        if (el.tagName === "INPUT" || el.tagName === "SELECT") {
            el.value =
                id === "logFilterLimit"
                    ? "100"
                    : "";
        } else {
            el.textContent = "";
        }
    });

    const groupBy = document.getElementById("logGroupBy");

    if (groupBy) {
        groupBy.value = "sessionId";
    }

    const human = document.getElementById("humanResult");

    if (human) {
        human.innerHTML = "";
    }

    const v2 = document.getElementById("v2Result");

    if (v2) {
        v2.innerHTML = "";
    }

    const logsResult = document.getElementById("logsResult");

    if (logsResult) {
        logsResult.innerHTML = "";
    }

    const logsSummary = document.getElementById("logsSummary");

    if (logsSummary) {
        logsSummary.textContent = "";
    }

    const errorOnly = document.getElementById("logFilterErrorOnly");

    if (errorOnly) {
        errorOnly.checked = false;
    }

    document.getElementById("groupAnalysisSection")?.classList.add("hidden");
    document.getElementById("relatedLogsSection")?.classList.add("hidden");

    window.lastRenderedLogs = null;
    window.lastRenderedFilters = null;
    window.currentAnalyzedGroupBy = "";
    window.currentAnalyzedGroupKey = "";
    window.currentWorkflowGraph = null;
    lastUploadResponse = null;
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
    document.getElementById("groupsResultSection")?.classList.add("hidden");
    document.getElementById("groupAnalysisSection")?.classList.add("hidden");
    document.getElementById("relatedLogsSection")?.classList.add("hidden");
    document.getElementById("workflowGraphSection")?.classList.add("hidden");
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

function showMessage(message, type = "info") {
    const box = document.getElementById("messageBox");
    if (!box) return;
    box.className = type;
    box.textContent = message;
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

        if (importId) setInputValue("logFilterImportIds", importId);
        setInputValue("logFilterUuid", uuid || "");
        setInputValue("logFilterProcessName", processName || "");

        const extractedSession = extractSessionFromKey(workflowKey);
        setInputValue("logFilterSessionId", extractedSession || "");

        showMessage("Chargement des logs du workflow...", "info");

        const filters = getLogFilters(false);
        const result = await fetchFilteredLogs(filters);

        renderLogsResult(result, filters);
        showMessage("Logs du workflow chargés.", "success");
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
            if (item && item.importId != null) return item.importId;
        }
    }

    if (typeof result === "object" && result.importId != null) {
        return result.importId;
    }

    return null;
}

function extractFirstIdFromCsv(value) {
    if (!value) return "";

    const first = String(value)
        .split(",")
        .map(x => x.trim())
        .find(Boolean);

    return first || "";
}

function parseCsvLongs(value) {
    if (!value || !value.trim()) {
        return { ok: true, values: [] };
    }

    const parts = value.split(",").map(x => x.trim()).filter(Boolean);

    for (const part of parts) {
        if (!/^\d+$/.test(part)) {
            return { ok: false, values: [] };
        }
    }

    return { ok: true, values: parts.map(Number) };
}

function parseCsvStrings(value) {
    if (!value || !value.trim()) {
        return [];
    }

    return value.split(",")
        .map(x => x.trim())
        .filter(Boolean);
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

// EXPORTS GLOBAUX
function filterGraphNodes() {
    const input = document.getElementById("graphNodeSearch");
    const query = (input?.value || "").toLowerCase().trim();

    const buttons = document.querySelectorAll("[data-graph-node='true']");

    buttons.forEach(btn => {
        const text = btn.getAttribute("data-search") || "";
        const visible = !query || text.includes(query);
        btn.style.display = visible ? "" : "none";
    });

    document.querySelectorAll(".graph-node-group").forEach(group => {
        const visibleButtons = Array.from(group.querySelectorAll("[data-graph-node='true']"))
            .filter(btn => btn.style.display !== "none");

        group.style.display = visibleButtons.length ? "" : "none";
    });
}

window.showMessage = showMessage;
window.loadLogsFromGroup = loadLogsFromGroup;
window.toggleWorkflowLines = toggleWorkflowLines;
window.loadLogsFromWorkflow = loadLogsFromWorkflow;
window.deleteImport = deleteImport;
window.useImportId = useImportId;
window.analyzeLogGroup = analyzeLogGroup;
window.loadRelatedLogsForItem = loadRelatedLogsForItem;
window.loadWorkflowGraph = loadWorkflowGraph;
window.loadContextAroundLog = loadContextAroundLog;
window.filterGraphNodes = filterGraphNodes;

window.selectGraphNode = selectGraphNode;
window.selectGraphRelation = selectGraphRelation;

window.analyzeGraphNodeWithContext = analyzeGraphNodeWithContext;