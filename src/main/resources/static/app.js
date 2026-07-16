document.addEventListener("DOMContentLoaded", () => {
    const loginForm = document.getElementById("loginForm");
    const logoutBtn = document.getElementById("logoutBtn");
    const uploadBtn = document.getElementById("uploadBtn");
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
    const crossSearchBtn = document.getElementById("crossSearchBtn");
    const clearCrossSearchBtn = document.getElementById("clearCrossSearchBtn");
    const compareSessionsBtn = document.getElementById("compareSessionsBtn");
    const clearCompareBtn = document.getElementById("clearCompareBtn");
    const crossSearchType = document.getElementById("crossSearchType");

    const viewDetailedBtn = document.getElementById("viewDetailedBtn");
    const viewMessagesBtn = document.getElementById("viewMessagesBtn");
    const viewRawBtn = document.getElementById("viewRawBtn");

    loginForm?.addEventListener("submit", onLoginSubmit);
    logoutBtn?.addEventListener("click", onLogoutClick);
    uploadBtn?.addEventListener("click", onUploadClick);
    document.getElementById("localFolderImportBtn")?.addEventListener("click", onLocalFolderImportClick);
    document.getElementById("logFolderFiles")?.addEventListener("change", onLogFolderFilesChange);
    document.getElementById("logFiles")?.addEventListener("change", onLogFilesChange);
    fillImportIdBtn?.addEventListener("click", onFillImportIdClick);
    loadLogsBtn?.addEventListener("click", onLoadLogsClick);
    loadErrorLogsBtn?.addEventListener("click", onLoadErrorLogsClick);
    loadGenericExplanationsBtn?.addEventListener("click", loadGenericExplanations);
    clearLogsBtn?.addEventListener("click", onClearLogsClick);
    loadAllGenericExplanationsBtn?.addEventListener("click", loadAllGenericExplanations);
    refreshImportsBtn?.addEventListener("click", onRefreshImportsClick);
    groupLogsBtn?.addEventListener("click", onGroupLogsClick);
    clearGroupAnalysisBtn?.addEventListener("click", onClearGroupAnalysisClick);
    document.getElementById("groupAnalysisSection")?.addEventListener("click", (e) => {
        if (e.target?.id === "expertAssistantChatSendBtn") {
            sendExpertAssistantChatMessage(e);
        }
    });
    document.getElementById("groupAnalysisSection")?.addEventListener("keydown", (e) => {
        if (e.target?.id === "expertAssistantChatInput" && e.key === "Enter") {
            e.preventDefault();
            sendExpertAssistantChatMessage(e);
        }
    });
    clearRelatedLogsBtn?.addEventListener("click", onClearRelatedLogsClick);
    clearWorkflowGraphBtn?.addEventListener("click", onClearWorkflowGraphClick);
    crossSearchBtn?.addEventListener("click", onCrossSearchClick);
    clearCrossSearchBtn?.addEventListener("click", onClearCrossSearchClick);
    compareSessionsBtn?.addEventListener("click", onCompareSessionsClick);
    clearCompareBtn?.addEventListener("click", onClearCompareClick);
    crossSearchType?.addEventListener("change", updateCrossSearchBusinessFieldVisibility);
    updateCrossSearchBusinessFieldVisibility();

    const logFilterImportIds = document.getElementById("logFilterImportIds");
    logFilterImportIds?.addEventListener("input", scheduleLogAnalysisGuidanceRefresh);
    logFilterImportIds?.addEventListener("change", scheduleLogAnalysisGuidanceRefresh);
    document.getElementById("applySuggestedGroupByBtn")
        ?.addEventListener("click", onApplySuggestedGroupByClick);

    viewDetailedBtn?.addEventListener("click", () => switchLogView("detailed"));
    viewMessagesBtn?.addEventListener("click", () => switchLogView("messages"));
    viewRawBtn?.addEventListener("click", () => switchLogView("raw"));

    bindMainNavigationFallback();
    hideAllExpertResultPanels();

    initializeApp().catch(err => {
        showMessage(err.message || "Erreur d'initialisation.", "error");
    });
});

function bindMainNavigationFallback() {
    const nav = document.getElementById("mainNav");
    if (!nav || nav.dataset.boundFallback === "1") {
        return;
    }
    nav.dataset.boundFallback = "1";
    nav.addEventListener("click", (event) => {
        const btn = event.target.closest("[data-main-tab]");
        if (!btn) {
            return;
        }
        event.preventDefault();
        const tab = btn.getAttribute("data-main-tab");
        if (typeof switchMainTab === "function") {
            switchMainTab(tab);
        }
    });
}

let lastUploadResponse = null;
let currentLogViewMode = "detailed";
let logGuidanceRefreshTimer = null;
let lastLogAnalysisGuidance = null;

window.lastRenderedLogs = null;
window.lastRenderedFilters = null;
window.currentAnalyzedGroupBy = "";
window.currentAnalyzedGroupKey = "";
window.expertChatScope = { nodeType: null, nodeLabel: null, process: null, action: null, filter: null };
window.currentWorkflowGraph = null;
window.graphNavigationHistory = [];
window.graphNavigationFuture = [];
window.graphCurrentView = null;

const EXPERT_RESULT_PANELS = [
    "logsResultSection",
    "groupsResultSection",
    "groupAnalysisSection",
    "workflowGraphSection",
    "relatedLogsSection",
    "humanResultSection",
    "analysisResultSection"
];

const MAIN_TAB_LABELS = {
    home: "Accueil",
    import: "Importer",
    diagnose: "Diagnostiquer",
    compare: "Comparaison",
    advanced: "Expert"
};

const EXPERT_PANEL_LABELS = {
    logsResultSection: "Logs consultables",
    groupsResultSection: "Groupes de logs",
    groupAnalysisSection: "Analyse du groupe",
    workflowGraphSection: "Graphe relationnel",
    relatedLogsSection: "Logs liés",
    humanResultSection: "Explication globale",
    analysisResultSection: "Résultat brut"
};

const LOG_VIEW_LABELS = {
    detailed: "Vue détaillée",
    messages: "Messages seulement",
    raw: "Lignes brutes"
};

const EXPERT_PANEL_CONTENT_MAP = {
    logsResultSection: "logsResult",
    groupsResultSection: "groupsResult",
    groupAnalysisSection: "groupAnalysisResult",
    workflowGraphSection: "workflowGraphResult",
    relatedLogsSection: "relatedLogsResult",
    humanResultSection: "humanResult",
    analysisResultSection: "analysisResult"
};

function expertPanelHasContent(panelId) {
    const innerId = EXPERT_PANEL_CONTENT_MAP[panelId];
    if (!innerId) return false;
    const el = document.getElementById(innerId);
    return Boolean(el?.innerHTML?.trim());
}

function restoreVisibleExpertPanels() {
    EXPERT_RESULT_PANELS.forEach(id => {
        if (expertPanelHasContent(id)) {
            setExpertPanelElementVisible(document.getElementById(id), true);
        }
    });
    syncExpertResultsDockVisibility();
}

function notifyNavigation(tab, options = {}) {
    if (options.silent || !tab) return;
    const label = MAIN_TAB_LABELS[tab] || tab;
    showMessage(`Onglet : ${label}`, "info");
}

function notifyExpertPanel(panelId, options = {}) {
    if (options.silent || !panelId) return;
    const label = EXPERT_PANEL_LABELS[panelId] || "Résultat";
    showMessage(`Affichage : ${label}`, "info");
}

function setExpertPanelElementVisible(el, visible) {
    if (!el) return;
    el.hidden = !visible;
    el.classList.toggle("hidden", !visible);
    el.classList.toggle("expert-panel-open", visible);
    el.style.display = visible ? "" : "none";
}

function syncExpertResultsDockVisibility() {
    const dock = document.getElementById("expertResultsDock");
    if (!dock) return;
    const anyOpen = EXPERT_RESULT_PANELS.some(id => {
        const el = document.getElementById(id);
        return el && el.classList.contains("expert-panel-open");
    });
    dock.classList.toggle("expert-dock-open", anyOpen);
    dock.hidden = !anyOpen;
    dock.style.display = anyOpen ? "" : "none";
}

function hideExpertPanel(panelId) {
    setExpertPanelElementVisible(document.getElementById(panelId), false);
    syncExpertResultsDockVisibility();
}

function hideAllExpertResultPanels() {
    EXPERT_RESULT_PANELS.forEach(id => hideExpertPanel(id));
    const dock = document.getElementById("expertResultsDock");
    if (dock) {
        dock.classList.remove("expert-dock-open");
        dock.hidden = true;
        dock.style.display = "none";
    }
}

function showExpertPanel(panelId, options = {}) {
    if (!panelId) return;
    const section = document.getElementById(panelId);
    if (!section) return;

    if (window.currentMainTab !== "advanced" && typeof switchMainTab === "function") {
        switchMainTab("advanced", { silent: true });
    }

    setExpertPanelElementVisible(section, true);

    const dock = document.getElementById("expertResultsDock");
    if (dock) {
        dock.classList.add("expert-dock-open");
        dock.hidden = false;
        dock.style.display = "";
    }

    syncExpertResultsDockVisibility();
    notifyExpertPanel(panelId, options);

    if (options.scroll !== false) {
        section.scrollIntoView({ behavior: "smooth", block: "start" });
    }
}

async function initializeApp() {
    if (typeof initEmployeeUX === "function") {
        initEmployeeUX();
    }

    if (!getAccessToken()) {
        showLoggedOutUI();
        return;
    }

    try {
        await loadCurrentUser();
        await showLoggedInUI();
        await refreshImports();
        scheduleLogAnalysisGuidanceRefresh();
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
        await showLoggedInUI();
        await refreshImports();
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
    const folderInput = document.getElementById("logFolderFiles");
    const fileInput = document.getElementById("logFiles");
    const folderFiles = folderInput?.files;
    const pickedFiles = fileInput?.files;

    let files = null;
    if (folderFiles && folderFiles.length > 0) {
        files = filterSupportedUploadFiles(folderFiles);
    } else if (pickedFiles && pickedFiles.length > 0) {
        files = filterSupportedUploadFiles(pickedFiles);
    }

    if (!files || files.length === 0) {
        showMessage("Sélectionnez un dossier ou un ou plusieurs fichiers .log / .txt / .out / .zip.", "error");
        return;
    }

    const totalBytes = Array.from(files).reduce((sum, f) => sum + (f.size || 0), 0);
    const totalMo = (totalBytes / (1024 * 1024)).toFixed(1);
    if (totalBytes > 100 * 1024 * 1024) {
        showMessage(
            `Taille totale ${totalMo} Mo — au-dessus de 100 Mo. `
            + "Utilisez « Importer le dossier local » avec le chemin Windows.",
            "error"
        );
        return;
    }

    const folderName = folderFiles?.length
        ? (folderFiles[0]?.webkitRelativePath?.split("/")[0] || "dossier sélectionné")
        : "fichiers sélectionnés";

    const chunkHint = files.length > UPLOAD_CHUNK_SIZE
        ? `\nEnvoi automatique par paquets de ${UPLOAD_CHUNK_SIZE} (un seul import, évite la saturation mémoire).`
        : "";
    const confirmed = await showAppConfirm({
        title: `Importer ${files.length} fichier${files.length > 1 ? "s" : ""} sur ce site ?`,
        message: `Tous les fichiers seront envoyés depuis « ${folderName} » (${totalMo} Mo).${chunkHint}\n`
            + "N'effectuez cette opération que s'il s'agit d'un serveur de confiance.",
        confirmLabel: "Importer",
        cancelLabel: "Annuler"
    });
    if (!confirmed) return;

    try {
        showMessage(`Import en cours (${files.length} fichier(s))…`, "info");
        const result = await uploadLogsWithProgress(files);
        await handleUploadSuccess(result);
    } catch (e) {
        showMessage(e.message || "Erreur lors de l'upload.", "error");
    }
}

function filterSupportedUploadFiles(fileList) {
    const dt = new DataTransfer();
    for (const file of fileList) {
        const name = (file.name || "").toLowerCase();
        if (name.endsWith(".log") || name.endsWith(".txt") || name.endsWith(".out") || name.endsWith(".zip")) {
            dt.items.add(file);
        }
    }
    return dt.files;
}

function onLogFolderFilesChange() {
    const input = document.getElementById("logFolderFiles");
    const hint = document.getElementById("logFolderSelectionHint");
    const files = input?.files;
    if (!hint) return;
    if (!files || files.length === 0) {
        hint.textContent = "Aucun dossier sélectionné.";
        return;
    }
    const supported = filterSupportedUploadFiles(files);
    const totalMo = (Array.from(supported).reduce((s, f) => s + (f.size || 0), 0) / (1024 * 1024)).toFixed(1);
    hint.textContent = `${supported.length} fichier(s) log sélectionné(s) — ${totalMo} Mo (seront importés en un seul lot).`;
    const fileInput = document.getElementById("logFiles");
    if (fileInput) fileInput.value = "";
}

function onLogFilesChange() {
    const folderInput = document.getElementById("logFolderFiles");
    if (folderInput?.files?.length) {
        folderInput.value = "";
        const hint = document.getElementById("logFolderSelectionHint");
        if (hint) hint.textContent = "Aucun dossier sélectionné.";
    }
}

async function onLocalFolderImportClick() {
    const path = document.getElementById("localFolderPath")?.value?.trim();
    if (!path) {
        showMessage("Indiquez le chemin complet du dossier (ex. C:\\logs\\mon-dossier).", "error");
        return;
    }

    const confirmed = await showAppConfirm({
        title: "Importer ce dossier depuis le disque local ?",
        message: `Le serveur va lire directement :\n${path}\n\n`
            + "Pas de limite 100 Mo (lecture disque). "
            + "Le dossier doit être accessible par l'application sur cette machine.",
        confirmLabel: "Importer",
        cancelLabel: "Annuler"
    });
    if (!confirmed) return;

    try {
        setUploadProgress(0, "Démarrage de l'import…");
        const response = await authFetch("/ingest/local-folder", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ folderPath: path, recursive: true })
        });
        const data = await safeJson(response);
        if (!response.ok) throw new Error(data?.message || "Erreur import dossier local.");

        const importId = data?.importId;
        if (importId && (data.processing || data.status === "PROCESSING")) {
            const finalProgress = await waitForImportProgress(importId);
            await handleUploadSuccess([{
                importId,
                fileName: data.fileName,
                success: finalProgress.success,
                message: finalProgress.message
            }]);
            if (!finalProgress.success) {
                showAppAlert(finalProgress.message || "Import échoué.", "error", "Import échoué");
            }
        } else {
            await handleUploadSuccess([data]);
        }
    } catch (e) {
        showMessage(e.message || "Erreur import dossier local.", "error");
    }
}

async function handleUploadSuccess(result, options = {}) {
    lastUploadResponse = result;

    const uploadResult = document.getElementById("uploadResult");
    if (uploadResult) {
        uploadResult.textContent = JSON.stringify(result, null, 2);
    }

    const extractedImportId = extractLatestImportId(result);
    const fileName = Array.isArray(result) && result[0] ? result[0].fileName : "";

    if (extractedImportId && typeof afterEmployeeUpload === "function") {
        afterEmployeeUpload(extractedImportId, fileName);
    } else if (extractedImportId && typeof setActiveImportId === "function") {
        setActiveImportId(extractedImportId, `#${extractedImportId} — ${fileName}`);
    }

    if (Array.isArray(result) && result[0]?.success !== false && !options.skipSuccessPopup) {
        const msg = result[0]?.message
            ? `Import terminé.\n\n${result[0].message}`
            : "Import terminé avec succès.";
        showAppAlert(msg, "success", "Import réussi");
    }
    await refreshImports();
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
    const importId = resolveActiveImportId();

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

        showExpertPanel("logsResultSection", { scroll: false, silent: true });

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

    showExpertPanel("logsResultSection", { scroll: false, silent: true });
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

    document.getElementById("logsResultSection") && hideExpertPanel("logsResultSection");
    document.getElementById("groupsResultSection") && hideExpertPanel("groupsResultSection");
    syncExpertResultsDockVisibility();

    showMessage("Affichage des logs vidé.", "info");
}

function onClearGroupAnalysisClick() {
    const container = document.getElementById("groupAnalysisResult");

    if (container) {
        container.innerHTML = "";
    }

    window.currentAnalyzedGroupBy = "";
    window.currentAnalyzedGroupKey = "";
    window.expertAssistantChatHistory = [];

    hideExpertPanel("groupAnalysisSection");
    showMessage("Analyse du groupe vidée.", "info");
}

function onClearRelatedLogsClick() {
    const container = document.getElementById("relatedLogsResult");

    if (container) {
        container.innerHTML = "";
    }

    hideExpertPanel("relatedLogsSection");
    showMessage("Logs liés vidés.", "info");
}

function onFillImportIdClick() {
    const importId = extractLatestImportId(lastUploadResponse);

    if (!importId) {
        showMessage("Aucun importId détecté dans le dernier résultat d’upload.", "error");
        return;
    }

    const logInput = document.getElementById("logFilterImportIds");

    if (logInput) logInput.value = String(importId);
    if (typeof setActiveImportId === "function") {
        setActiveImportId(importId, `Import #${importId}`);
    }

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
    window.currentUserRole = data.role || "";
}

function setUploadProgress(percent, labelText) {
    const wrap = document.getElementById("uploadProgressWrap");
    const bar = document.getElementById("uploadProgressBar");
    const label = document.getElementById("uploadProgressLabel");
    if (wrap) wrap.classList.remove("hidden");
    if (bar) bar.style.width = `${Math.min(100, Math.max(0, percent))}%`;
    if (label && labelText) label.textContent = labelText;
}

function hideUploadProgress(delayMs = 2500) {
    setTimeout(() => document.getElementById("uploadProgressWrap")?.classList.add("hidden"), delayMs);
}

function extractImportIdFromUploadResponse(data) {
    if (Array.isArray(data)) {
        for (let i = data.length - 1; i >= 0; i--) {
            if (data[i]?.importId != null) return data[i].importId;
        }
        return null;
    }
    return data?.importId ?? null;
}

async function waitForImportProgress(importId) {
    if (!importId) {
        throw new Error("Import ID manquant pour le suivi de progression.");
    }

    setUploadProgress(2, "Analyse démarrée sur le serveur…");

    let lastLines = 0;
    let stalePolls = 0;

    for (;;) {
        const response = await authFetch(`/ingest/progress/${importId}`);
        const progress = await safeJson(response);
        if (!response.ok) {
            throw new Error(progress?.message || "Impossible de lire la progression de l'import.");
        }

        const pct = Number(progress.percent) || 0;
        const fileIndex = progress.fileIndex || 0;
        const fileCount = progress.fileCount || 0;
        const lines = progress.linesRead || 0;
        const parsed = progress.parsedLines || 0;
        const current = progress.currentFile || "";

        if (lines > lastLines) {
            lastLines = lines;
            stalePolls = 0;
        } else {
            stalePolls++;
        }

        let label = progress.message || `Analyse ${pct}%`;
        if (fileCount > 0) {
            label = `Fichier ${fileIndex}/${fileCount} — ${pct}% — ${lines.toLocaleString("fr-FR")} lignes`;
            if (parsed > 0) label += ` (${parsed.toLocaleString("fr-FR")} parsées)`;
            if (current) label += ` — ${current}`;
        }
        if (stalePolls >= 90) {
            label += " — ⚠ aucune nouvelle ligne depuis ~90 s (gros fichier ou serveur chargé)";
        } else if (stalePolls >= 30) {
            label += " — traitement en cours…";
        }

        setUploadProgress(Math.max(pct, 3), label);

        if (progress.finished) {
            setUploadProgress(100, progress.success ? "Import terminé." : "Import terminé avec erreur.");
            hideUploadProgress(3000);
            return progress;
        }

        await new Promise((resolve) => setTimeout(resolve, 1000));
    }
}

async function uploadLogs(fileList) {
    return uploadLogsWithProgress(fileList);
}

/** Au-delà de ce seuil : envoi par paquets (évite Java heap space sur 10–20 fichiers). */
const UPLOAD_CHUNK_SIZE = 4;

async function uploadLogsWithProgress(fileList) {
    const files = Array.from(fileList || []).filter(Boolean);
    if (!files.length) {
        throw new Error("Aucun fichier à importer.");
    }

    const wrap = document.getElementById("uploadProgressWrap");
    if (wrap) wrap.classList.remove("hidden");
    setUploadProgress(0, "Préparation de l'envoi…");

    // Peu de fichiers → un seul multipart (comportement historique).
    if (files.length <= UPLOAD_CHUNK_SIZE) {
        return uploadFilesSingleRequest(files);
    }

    // Beaucoup de fichiers → session découpée : 1 clic utilisateur, 1 import final.
    return uploadFilesChunkedSession(files);
}

function uploadFilesSingleRequest(files) {
    return new Promise((resolve, reject) => {
        const formData = new FormData();
        for (const file of files) {
            formData.append("files", file);
        }

        const xhr = new XMLHttpRequest();
        xhr.open("POST", "/ingest/upload");

        const token = localStorage.getItem("accessToken");
        if (token) xhr.setRequestHeader("Authorization", "Bearer " + token);

        xhr.upload.onprogress = (e) => {
            if (!e.lengthComputable) return;
            const uploadPct = Math.round((e.loaded / e.total) * 100);
            const barPct = Math.round(uploadPct * 0.12);
            setUploadProgress(barPct, `Envoi ${uploadPct}% — puis analyse sur le serveur…`);
        };

        xhr.onload = async () => {
            let data = {};
            try {
                data = JSON.parse(xhr.responseText || "{}");
            } catch (_) {
                data = { message: xhr.responseText };
            }

            if (xhr.status < 200 || xhr.status >= 300) {
                hideUploadProgress(0);
                reject(new Error(data?.message || "Erreur upload."));
                return;
            }

            try {
                resolve(await finishUploadAfterResponse(data));
            } catch (progressError) {
                hideUploadProgress(0);
                reject(progressError);
            }
        };

        xhr.onerror = () => {
            hideUploadProgress(0);
            reject(new Error("Erreur réseau pendant l'upload."));
        };

        xhr.send(formData);
    });
}

async function uploadFilesChunkedSession(files) {
    let sessionId = null;
    try {
        const startRes = await authFetch("/ingest/session/start", { method: "POST" });
        const startData = await safeJson(startRes);
        if (!startRes.ok) {
            throw new Error(startData?.message || startData?.error || "Impossible de démarrer la session d'upload.");
        }
        sessionId = startData.sessionId;
        if (!sessionId) {
            throw new Error("sessionId manquant.");
        }

        const total = files.length;
        let sent = 0;
        for (let i = 0; i < files.length; i += UPLOAD_CHUNK_SIZE) {
            const chunk = files.slice(i, i + UPLOAD_CHUNK_SIZE);
            await uploadSessionChunk(sessionId, chunk, (chunkPct) => {
                const overall = ((sent + (chunk.length * chunkPct) / 100) / total) * 100;
                const barPct = Math.min(70, Math.round(overall * 0.70));
                setUploadProgress(
                    barPct,
                    `Envoi ${Math.min(total, sent + chunk.length)}/${total} fichier(s)…`
                );
            });
            sent += chunk.length;
            setUploadProgress(
                Math.min(70, Math.round((sent / total) * 70)),
                `Envoi ${sent}/${total} fichier(s)…`
            );
        }

        setUploadProgress(72, "Lancement de l'analyse sur le serveur…");
        const commitRes = await authFetch(`/ingest/session/${encodeURIComponent(sessionId)}/commit`, {
            method: "POST"
        });
        const commitData = await safeJson(commitRes);
        sessionId = null; // commit a consommé la session
        if (!commitRes.ok) {
            throw new Error(commitData?.message || commitData?.error || "Erreur finalisation import.");
        }

        const asList = Array.isArray(commitData) ? commitData : [commitData];
        return await finishUploadAfterResponse(asList);
    } catch (e) {
        if (sessionId) {
            try {
                await authFetch(`/ingest/session/${encodeURIComponent(sessionId)}`, { method: "DELETE" });
            } catch (_) { /* ignore */ }
        }
        hideUploadProgress(0);
        throw e;
    }
}

function uploadSessionChunk(sessionId, chunkFiles, onPct) {
    return new Promise((resolve, reject) => {
        const formData = new FormData();
        for (const file of chunkFiles) {
            formData.append("files", file);
        }
        const xhr = new XMLHttpRequest();
        xhr.open("POST", `/ingest/session/${encodeURIComponent(sessionId)}/files`);
        const token = localStorage.getItem("accessToken");
        if (token) xhr.setRequestHeader("Authorization", "Bearer " + token);

        xhr.upload.onprogress = (e) => {
            if (!e.lengthComputable || typeof onPct !== "function") return;
            onPct(Math.round((e.loaded / e.total) * 100));
        };
        xhr.onload = () => {
            let data = {};
            try {
                data = JSON.parse(xhr.responseText || "{}");
            } catch (_) {
                data = { message: xhr.responseText };
            }
            if (xhr.status < 200 || xhr.status >= 300) {
                reject(new Error(data?.message || "Erreur envoi paquet."));
                return;
            }
            resolve(data);
        };
        xhr.onerror = () => reject(new Error("Erreur réseau pendant l'envoi d'un paquet."));
        xhr.send(formData);
    });
}

async function finishUploadAfterResponse(data) {
    const importId = extractImportIdFromUploadResponse(data);
    const processing = Array.isArray(data)
        ? data.some((item) => item?.processing)
        : Boolean(data?.processing);

    if (importId && processing) {
        setUploadProgress(75, "Envoi terminé — analyse sur le serveur…");
        const finalProgress = await waitForImportProgress(importId);
        if (Array.isArray(data) && data[0]) {
            data[0].success = finalProgress.success;
            data[0].message = finalProgress.message;
        } else if (data && typeof data === "object") {
            data.success = finalProgress.success;
            data.message = finalProgress.message;
        }
    } else {
        setUploadProgress(100, "Import terminé.");
        hideUploadProgress();
    }
    return data;
}

function openV2WorkflowInExplorer(groupBy, groupKey) {
    if (!groupKey) {
        showMessage("Clé de groupe introuvable pour ce workflow.", "error");
        return;
    }

    clearGroupDrivenFilters();
    document.getElementById("logGroupBy").value = groupBy || "sessionId";

    switch (groupBy) {
        case "sessionId":
            setInputValue("logFilterSessionId", groupKey);
            setInputValue("compareSessionA", groupKey);
            break;
        case "uuid":
            setInputValue("logFilterUuid", groupKey);
            break;
        case "userName":
            setInputValue("logFilterUserName", groupKey);
            break;
        case "processName":
            setInputValue("logFilterProcessName", groupKey);
            break;
        case "correlationId":
            setInputValue("logFilterUuid", groupKey);
            break;
        case "businessKey":
            setInputValue("logFilterUuid", groupKey);
            break;
        default:
            setInputValue("logFilterSessionId", groupKey);
    }

    const importId = resolveActiveImportId();
    if (importId) {
        setInputValue("logFilterImportIds", importId);
        setInputValue("crossSearchImportIds", importId);
        setInputValue("compareImportIds", importId);
    }

    onLoadLogsClick();
    showMessage("Workflow ouvert dans l’explorateur de logs.", "info");
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

    const list = Array.isArray(data) ? data : [];
    if (typeof populateActiveImportSelect === "function") {
        populateActiveImportSelect(list);
    }
    if (!window.activeImportId && list.length > 0) {
        const latest = list[0];
        const id = latest.id ?? latest.importId;
        if (id != null && typeof setActiveImportId === "function") {
            setActiveImportId(id, `#${id} — ${latest.fileName || ""}`);
        }
    }
    renderImports(list);
}

function renderImports(imports) {
    document.getElementById("importsSection")?.classList.remove("hidden");

    const container = document.getElementById("importsResult");

    if (!container) return;

    if (!imports.length) {
        container.innerHTML = `<div class="empty-state">Aucun import trouvé.</div>`;
        document.getElementById("importDetailPanel")?.classList.add("hidden");
        return;
    }

    window.lastImportsList = imports;

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
        <tr class="import-row" data-import-id="${escapeHtml(id)}" onclick="openImportDetail('${escapeJs(id)}')">
            <td>${escapeHtml(id)}</td>
            <td>${escapeHtml(fileName)}</td>
            <td>${escapeHtml(String(startedAt).replace("T", " ").slice(0, 19))}</td>
            <td>${escapeHtml(status)}</td>
            <td>${escapeHtml(totalLines)}</td>
            <td>${escapeHtml(totalErrors)}</td>
            <td class="imports-actions" onclick="event.stopPropagation()">
                <button type="button" class="secondary-btn" onclick="openImportDetail('${escapeJs(id)}')">Détail</button>
                <button type="button" class="secondary-btn" onclick="useImportId('${escapeJs(id)}')">Utiliser</button>
                <button type="button" class="danger-btn" onclick="deleteImport('${escapeJs(id)}','${escapeJs(fileName)}')">Supprimer</button>
            </td>
        </tr>
    `;
}

async function openImportDetail(importId) {
    if (!importId) return;

    const panel = document.getElementById("importDetailPanel");
    if (!panel) return;

    showMessage(`Chargement du détail import #${importId}…`, "info");
    panel.classList.remove("hidden");
    panel.innerHTML = `<div class="analysis-block"><p class="muted-text">Chargement du détail import #${escapeHtml(importId)}…</p></div>`;
    panel.scrollIntoView({ behavior: "smooth", block: "nearest" });

    try {
        const [detailRes, qualityRes] = await Promise.all([
            authFetch(`/imports/${encodeURIComponent(importId)}/detail`),
            authFetch(`/explorer/import-quality/${encodeURIComponent(importId)}`)
        ]);
        const detail = await safeJson(detailRes);
        const quality = await safeJson(qualityRes);
        if (!detailRes.ok) throw new Error(detail?.message || "Erreur détail import");

        const s = detail.summary || {};
        const files = Array.isArray(detail.sourceFiles) ? detail.sourceFiles : [];
        const fileSizeMo = detail.fileSize != null ? (detail.fileSize / (1024 * 1024)).toFixed(2) + " Mo" : "—";

        panel.innerHTML = `
            <div class="analysis-block import-detail-block">
                <div class="card-header-inline">
                    <h3>Import #${escapeHtml(s.importId ?? importId)} — ${escapeHtml(s.fileName || "")}</h3>
                    <button type="button" class="secondary-btn small-btn" onclick="document.getElementById('importDetailPanel')?.classList.add('hidden')">Fermer</button>
                </div>
                <div class="analysis-kpis">
                    <span class="log-pill">${escapeHtml(s.status || "")}</span>
                    <span class="log-pill">${escapeHtml(s.totalLogs)} logs</span>
                    <span class="log-pill error-pill">${escapeHtml(s.totalErrors)} erreurs</span>
                    <span class="log-pill">${escapeHtml(detail.distinctUsers)} utilisateur(s)</span>
                    <span class="log-pill">${escapeHtml(detail.slowLogCount)} lenteur(s)</span>
                    <span class="log-pill">max ${escapeHtml(detail.maxDurationMs)} ms</span>
                </div>
                <div class="import-detail-meta">
                    <p><strong>Importé le :</strong> ${escapeHtml(fmtImportDate(s.startedAt))}
                       ${s.finishedAt ? ` → terminé ${escapeHtml(fmtImportDate(s.finishedAt))}` : ""}</p>
                    <p><strong>Lignes fichier :</strong> ${escapeHtml(s.parsedLines)} parsées / ${escapeHtml(s.totalLines)} total
                       · ${escapeHtml(s.failedLines)} échec(s) · taux erreur ${escapeHtml(s.errorRate)}%</p>
                    <p><strong>Période logs :</strong> ${escapeHtml(fmtImportDate(s.firstLogTimestamp))} → ${escapeHtml(fmtImportDate(s.lastLogTimestamp))}</p>
                    <p class="muted-text">Taille archive : ${escapeHtml(fileSizeMo)}
                       · dernier accès : ${escapeHtml(fmtImportDate(detail.lastAccessedAt))}
                       · hash : <code>${escapeHtml((detail.fileHash || "").slice(0, 16))}…</code></p>
                    ${s.errorMessage ? `<p class="error-text">${escapeHtml(s.errorMessage)}</p>` : ""}
                </div>
                ${qualityRes.ok ? `<div class="import-detail-quality">${formatExtractionAuditHtml(quality, true)}</div>` : ""}
                <h4>Fichiers sources (${files.length})</h4>
                <div class="imports-table-wrap">
                    <table class="imports-table import-files-table">
                        <thead>
                            <tr>
                                <th>Fichier</th>
                                <th>Chemin relatif</th>
                                <th>Logs</th>
                                <th>Erreurs</th>
                                <th>Lenteurs</th>
                                <th>Max ms</th>
                                <th>Période</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${files.length ? files.map(f => `
                                <tr>
                                    <td>${escapeHtml(f.sourceFileName)}</td>
                                    <td><code>${escapeHtml(f.sourceRelativePath)}</code></td>
                                    <td>${escapeHtml(f.logCount)}</td>
                                    <td>${escapeHtml(f.errorCount)}</td>
                                    <td>${escapeHtml(f.slowLogCount)}</td>
                                    <td>${escapeHtml(f.maxDurationMs ?? "—")}</td>
                                    <td class="muted-text">${escapeHtml(fmtImportDate(f.firstLogTimestamp))} → ${escapeHtml(fmtImportDate(f.lastLogTimestamp))}</td>
                                </tr>
                            `).join("") : `<tr><td colspan="7">Aucun fichier source identifié.</td></tr>`}
                        </tbody>
                    </table>
                </div>
                <div class="actions wrap" style="margin-top:12px">
                    <button type="button" onclick="useImportId('${escapeJs(importId)}')">Définir comme import actif</button>
                    <button type="button" class="secondary-btn" onclick="switchMainTab('import'); loadImportAnomalies(${escapeJs(importId)}, document.getElementById('importAnomaliesPanel'))">Voir latences</button>
                    <button type="button" class="secondary-btn" onclick="switchMainTab('advanced'); setInputValue('logFilterImportIds','${escapeJs(importId)}'); useImportId('${escapeJs(importId)}')">Expert</button>
                </div>
            </div>
        `;

        if (typeof setActiveImportId === "function") {
            document.querySelectorAll(".import-row").forEach(row => row.classList.remove("import-row-active"));
            document.querySelector(`.import-row[data-import-id="${importId}"]`)?.classList.add("import-row-active");
        }
        showMessage(`Détail import #${importId} chargé.`, "success");
    } catch (e) {
        panel.innerHTML = `<div class="analysis-block"><p class="error-text">${escapeHtml(e.message || "Erreur")}</p></div>`;
        showMessage(e.message || "Erreur détail import.", "error");
    }
}

function fmtImportDate(value) {
    if (!value) return "—";
    return String(value).replace("T", " ").slice(0, 19);
}

window.openImportDetail = openImportDetail;

function useImportId(importId) {
    const imp = window.lastImportsList?.find(i => String(i.id ?? i.importId) === String(importId));
    const label = imp ? `#${importId} — ${imp.fileName || ""}` : `#${importId}`;

    if (typeof setActiveImportId === "function") {
        setActiveImportId(importId, label);
    } else {
        const logInput = document.getElementById("logFilterImportIds");
        if (logInput) logInput.value = String(importId || "");
    }

    scheduleLogAnalysisGuidanceRefresh();
    showMessage(`Import #${importId} défini comme import actif.`, "success");
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

function scheduleLogAnalysisGuidanceRefresh() {
    if (logGuidanceRefreshTimer) {
        clearTimeout(logGuidanceRefreshTimer);
    }
    logGuidanceRefreshTimer = setTimeout(() => {
        refreshLogAnalysisGuidance().catch(() => {});
    }, 450);
}

async function refreshLogAnalysisGuidance() {
    const panel = document.getElementById("logAnalysisGuidancePanel");
    if (!panel) {
        return;
    }

    const raw = document.getElementById("logFilterImportIds")?.value?.trim() || "";
    let importIds = [];

    if (raw) {
        const parsed = parseImportIds(raw);
        if (!parsed.ok) {
            panel.classList.remove("hidden");
            renderLogAnalysisGuidance({
                summary: parsed.error || "Import IDs invalides.",
                dominantTypeLabel: "—",
                suggestedGroupBy: "sessionId",
                suggestedGroupByLabel: "Session technique (timestamp)",
                extractableInfos: [],
                avoidGroupByHints: [],
                tips: [],
                imports: []
            });
            return;
        }
        importIds = parsed.values;
    } else if (window.activeImportId) {
        importIds = [window.activeImportId];
    }

    if (!importIds.length) {
        panel.classList.add("hidden");
        lastLogAnalysisGuidance = null;
        return;
    }

    const response = await authFetch("/explorer/analysis-guidance", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ importIds })
    });
    const data = await safeJson(response);
    if (!response.ok) {
        throw new Error(data?.message || "Impossible de charger les suggestions.");
    }

    lastLogAnalysisGuidance = data;
    panel.classList.remove("hidden");
    renderLogAnalysisGuidance(data);
}

function renderLogAnalysisGuidance(data) {
    const typeLabel = document.getElementById("logGuidanceTypeLabel");
    const summary = document.getElementById("logGuidanceSummary");
    const description = document.getElementById("logGuidanceDescription");
    const groupByLabel = document.getElementById("logGuidanceGroupByLabel");
    const extractableList = document.getElementById("logGuidanceExtractableList");
    const avoidList = document.getElementById("logGuidanceAvoidList");
    const tipsList = document.getElementById("logGuidanceTipsList");
    const perImport = document.getElementById("logGuidancePerImport");

    if (typeLabel) {
        typeLabel.textContent = data.dominantTypeLabel || "Type de logs";
    }
    if (summary) {
        summary.textContent = data.summary || "";
    }
    if (description) {
        const first = data.imports?.[0];
        description.textContent = first?.typeDescription
            || "Suggestions basées sur le nom de fichier et le contenu indexé.";
    }
    if (groupByLabel) {
        groupByLabel.textContent = data.suggestedGroupByLabel || "—";
    }

    fillGuidanceUl(extractableList, data.extractableInfos);
    fillGuidanceUl(avoidList, data.avoidGroupByHints);
    fillGuidanceUl(tipsList, data.tips);

    if (perImport) {
        const imports = data.imports || [];
        if (imports.length <= 1) {
            perImport.classList.add("hidden");
            perImport.innerHTML = "";
        } else {
            perImport.classList.remove("hidden");
            perImport.innerHTML = `
                <h4 class="subsection-title" style="margin-top:0">Détail par import</h4>
                ${imports.map(imp => `
                    <div class="log-guidance-import-card">
                        <div class="import-title">#${escapeHtml(imp.importId)} — ${escapeHtml(imp.fileName || "")}</div>
                        <div>${escapeHtml(imp.typeLabel || "")} · ${escapeHtml(String(imp.totalLogs || 0))} logs</div>
                        <div>Regroupement : <strong>${escapeHtml(imp.suggestedGroupByLabel || "")}</strong></div>
                    </div>
                `).join("")}
            `;
        }
    }
}

function fillGuidanceUl(ul, items) {
    if (!ul) {
        return;
    }
    const list = Array.isArray(items) ? items.filter(Boolean) : [];
    if (!list.length) {
        ul.innerHTML = "<li>Aucune indication spécifique.</li>";
        return;
    }
    ul.innerHTML = list.map(item => `<li>${escapeHtml(item)}</li>`).join("");
}

function onApplySuggestedGroupByClick() {
    const groupBy = lastLogAnalysisGuidance?.suggestedGroupBy;
    if (!groupBy) {
        showMessage("Aucune suggestion de regroupement disponible.", "error");
        return;
    }
    const select = document.getElementById("logGroupBy");
    if (select) {
        select.value = groupBy;
    }
    showMessage(
        `Regroupement « ${lastLogAnalysisGuidance.suggestedGroupByLabel || groupBy} » appliqué.`,
        "success"
    );
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

    const importIds = parseImportIds(importIdsRaw);
    const fileNames = parseCsvStrings(fileNamesRaw);

    if (!importIds.ok) {
        throw new Error(
            importIds.error ||
            "Le filtre Import IDs est invalide (ex. 7, 8, 9 ou 7-17 ou 7 à 17)."
        );
    }

    let resolvedImportIds = importIds.values;
    if ((!resolvedImportIds || resolvedImportIds.length === 0) && window.activeImportId) {
        resolvedImportIds = [window.activeImportId];
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
        importIds: resolvedImportIds,
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
    showExpertPanel("groupsResultSection", { scroll: false, silent: true });

    const groupsResult =
        document.getElementById("groupsResult");

    const groupsSummary =
        document.getElementById("groupsSummary");

    if (groupsSummary) {
        groupsSummary.textContent =
            `${groups.length} groupe(s) | groupBy=${filters.groupBy || "sessionId"} | importIds=${formatImportIdsSummary(filters.importIds)}`;
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
                    onclick="loadSessionDiagnosticForGroup(
                        '${escapeJs(group.groupBy || "")}',
                        '${escapeJs(group.groupKey || "")}'
                    )">

                    Fiche diagnostic

                </button>

                <button
                    type="button"
                    class="secondary-btn"
                    onclick="loadFullAnalysisForGroup(
                        '${escapeJs(group.groupBy || "")}',
                        '${escapeJs(group.groupKey || "")}'
                    )">

                    Analyse IA complète

                </button>

                <button
                    type="button"
                    class="secondary-btn"
                    onclick="openExpertAssistantChat(
                        '${escapeJs(group.groupBy || "")}',
                        '${escapeJs(group.groupKey || "")}'
                    )">

                    Chat IA

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

                <button
                    type="button"
                    class="secondary-btn"
                    onclick="prefillSessionCompare(
                        '${escapeJs(group.groupKey || "")}',
                        '${escapeJs(group.groupBy || "")}'
                    )">

                    Comparer session

                </button>

            </div>

        </div>
    `;
}

function updateCrossSearchBusinessFieldVisibility() {
    const type = document.getElementById("crossSearchType")?.value || "";
    const wrap = document.getElementById("crossSearchBusinessFieldWrap");
    if (wrap) {
        wrap.style.display = type === "BUSINESS_FIELD" ? "" : "none";
    }
}

function resolveExplorerImportIds(fieldId) {
    const raw = document.getElementById(fieldId)?.value?.trim() || "";
    if (raw) {
        const parsed = parseImportIds(raw);
        if (!parsed.ok) {
            throw new Error(
                parsed.error ||
                "Import IDs invalides (ex. 6, 7-17 ou 7 à 17)."
            );
        }
        return parsed.values || [];
    }
    const fromLogFilter = getLogFilters(false).importIds || [];
    if (fromLogFilter.length > 0) {
        return fromLogFilter;
    }
    if (window.activeImportId) {
        return [window.activeImportId];
    }
    return [];
}

async function onCrossSearchClick() {
    try {
        const importIds = resolveExplorerImportIds("crossSearchImportIds");
        if (!importIds.length) {
            showMessage("Veuillez saisir au moins un Import ID.", "error");
            return;
        }

        const searchType = document.getElementById("crossSearchType")?.value || "MESSAGE";
        const value = document.getElementById("crossSearchValue")?.value?.trim() || "";
        if (!value) {
            showMessage("Veuillez saisir une valeur de recherche.", "error");
            return;
        }

        document.getElementById("explorerSection")?.classList.remove("hidden");

        const filters = getLogFilters(false);
        const payload = {
            importIds,
            searchType,
            value,
            businessField: searchType === "BUSINESS_FIELD"
                ? document.getElementById("crossSearchBusinessField")?.value?.trim() || null
                : null,
            dateFrom: filters.dateFrom ? toBackendDateTime(filters.dateFrom) : null,
            dateTo: filters.dateTo ? toBackendDateTime(filters.dateTo) : null,
            limit: 500,
            errorsOnly: Boolean(document.getElementById("crossSearchErrorsOnly")?.checked)
        };

        showMessage("Recherche transversale en cours...", "info");

        const response = await authFetch("/explorer/cross-search", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        const data = await safeJson(response);
        if (!response.ok) {
            throw new Error(data?.message || "Erreur recherche transversale.");
        }

        window.lastCrossSearchContext = {
            importIds,
            searchType: payload.searchType,
            value: payload.value,
            dateFrom: payload.dateFrom,
            dateTo: payload.dateTo
        };

        renderCrossSearchResult(data);
        showMessage("Recherche terminée.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur recherche transversale.", "error");
    }
}

function renderCrossSearchResult(data) {
    const container = document.getElementById("crossSearchResult");
    if (!container) return;

    document.getElementById("explorerSection")?.classList.remove("hidden");

    const hits = Array.isArray(data.sessionHits) ? data.sessionHits : [];
    const samples = Array.isArray(data.sampleLogs) ? data.sampleLogs : [];

    const hitsHtml = hits.length
        ? hits.map(h => `
            <div class="explorer-hit-card">
                <div class="analysis-kpis">
                    <span class="log-pill">Import ${escapeHtml(h.importId)}</span>
                    <span class="log-pill">${escapeHtml(h.importFileName || "")}</span>
                    <span class="log-pill">Session ${escapeHtml(h.sessionId)}</span>
                    <span class="log-pill">${escapeHtml(h.logCount)} logs</span>
                    <span class="log-pill error-pill">${escapeHtml(h.errorCount)} err.</span>
                </div>
                <p><strong>Process :</strong> ${escapeHtml(h.dominantProcess || "—")} · <strong>User :</strong> ${escapeHtml(h.userName || "—")}</p>
                <p class="muted-text">${escapeHtml(h.sampleMessage || "")}</p>
                <div class="explorer-hit-actions">
                    <button type="button" ${crossSearchHitDataAttrs(h)} onclick="openGraphFromCrossSearchHitFromEl(this)">
                        Graphe relationnel
                    </button>
                    ${h.anchorLogId
                        ? `<button type="button" class="secondary-btn" ${crossSearchHitDataAttrs(h)} onclick="openContextFromCrossSearchHitFromEl(this)">
                            Logs avant / après
                           </button>`
                        : ""}
                    <button type="button" class="secondary-btn" onclick="openSessionFromCrossSearch('${escapeJs(h.sessionId || "")}')">
                        Ouvrir cette session
                    </button>
                </div>
            </div>
        `).join("")
        : `<p class="muted-text">Aucune session trouvée.</p>`;

    const samplesHtml = samples.length
        ? `<div class="log-list compact-log-list">${samples.map(l => renderCrossSearchSampleLog(l)).join("")}</div>`
        : "";

    container.innerHTML = `
        <div class="analysis-block">
            <div class="analysis-kpis">
                <span class="log-pill">${escapeHtml(data.searchType)} = ${escapeHtml(data.value)}</span>
                <span class="log-pill">${escapeHtml(data.totalMatchingLogs)} lignes</span>
                ${data.truncated ? '<span class="log-pill warn-pill">Tronqué</span>' : ""}
            </div>
            <p>${escapeHtml(data.summary || "")}</p>
        </div>
        <div class="analysis-block">
            <h3>Sessions trouvées (${hits.length})</h3>
            ${hitsHtml}
        </div>
        ${samplesHtml ? `<div class="analysis-block"><h3>Extraits (${samples.length})</h3>${samplesHtml}</div>` : ""}
    `;
}

function renderLogLineCompact(log) {
    return `
        <div class="log-line compact">
            <span class="log-time">${escapeHtml(shortTimestamp(log.logTimestamp))}</span>
            <span class="log-level ${escapeHtml((log.level || "").toLowerCase())}">${escapeHtml(log.level || "")}</span>
            <span class="log-process">${escapeHtml(log.processName || "")}</span>
            <span class="log-msg">${escapeHtml(truncateText(log.message || "", 160))}</span>
        </div>
    `;
}

function renderCrossSearchSampleLog(log) {
    const hit = {
        importId: log.importId,
        sessionId: log.sessionId,
        anchorLogId: log.id,
        firstTimestamp: log.logTimestamp,
        lastTimestamp: log.logTimestamp,
        dominantProcess: log.processName
    };

    return `
        <div class="log-line compact cross-search-sample">
            <div class="cross-search-sample-main">
                <span class="log-time">${escapeHtml(shortTimestamp(log.logTimestamp))}</span>
                <span class="log-level ${escapeHtml((log.level || "").toLowerCase())}">${escapeHtml(log.level || "")}</span>
                <span class="log-process">${escapeHtml(log.processName || "")}</span>
                <span class="log-msg">${escapeHtml(truncateText(log.message || "", 160))}</span>
            </div>
            <div class="explorer-hit-actions compact">
                <button type="button" ${crossSearchHitDataAttrs(hit)} onclick="openGraphFromCrossSearchHitFromEl(this)">Graphe</button>
                ${log.id
                    ? `<button type="button" class="secondary-btn" ${crossSearchHitDataAttrs(hit)} onclick="openContextFromCrossSearchHitFromEl(this)">Avant / après</button>`
                    : ""}
            </div>
        </div>
    `;
}

function toDatetimeLocalValue(value) {
    if (!value) return "";
    const normalized = String(value).trim().replace(" ", "T");
    if (normalized.length >= 16) {
        return normalized.substring(0, 16);
    }
    return normalized;
}

function crossSearchHitDataAttrs(hit) {
    const h = hit || {};
    const parts = [
        `data-import-id="${escapeHtml(h.importId ?? "")}"`,
        `data-session-id="${escapeHtml(h.sessionId || "")}"`,
        `data-anchor-log-id="${escapeHtml(h.anchorLogId ?? "")}"`,
        `data-date-from="${escapeHtml(h.firstTimestamp || "")}"`,
        `data-date-to="${escapeHtml(h.lastTimestamp || "")}"`,
        `data-process="${escapeHtml(h.dominantProcess || "")}"`
    ];
    return parts.join(" ");
}

function readCrossSearchHitFromEl(el) {
    if (!el) return null;
    return {
        importId: el.dataset.importId ? Number(el.dataset.importId) : null,
        sessionId: el.dataset.sessionId || "",
        anchorLogId: el.dataset.anchorLogId ? Number(el.dataset.anchorLogId) : null,
        firstTimestamp: el.dataset.dateFrom || null,
        lastTimestamp: el.dataset.dateTo || null,
        dominantProcess: el.dataset.process || null
    };
}

function openGraphFromCrossSearchHitFromEl(el) {
    return openGraphFromCrossSearchHit(readCrossSearchHitFromEl(el));
}

function openContextFromCrossSearchHitFromEl(el) {
    return openContextFromCrossSearchHit(readCrossSearchHitFromEl(el));
}

function applyCrossSearchHitToFilters(hit) {
    if (!hit) return;
    if (hit.importId != null) {
        setInputValue("logFilterImportIds", String(hit.importId));
        setInputValue("crossSearchImportIds", String(hit.importId));
    }
    if (hit.sessionId) {
        setInputValue("logFilterSessionId", hit.sessionId);
    }
    if (hit.firstTimestamp) {
        setInputValue("logFilterDateFrom", toDatetimeLocalValue(hit.firstTimestamp));
    }
    if (hit.lastTimestamp) {
        setInputValue("logFilterDateTo", toDatetimeLocalValue(hit.lastTimestamp));
    }
}

function prepareCrossSearchGraphGroup(hit) {
    const sessionId = hit?.sessionId;
    if (!sessionId) {
        return null;
    }
    window.currentAnalyzedGroupBy = "sessionId";
    window.currentAnalyzedGroupKey = sessionId;
    return { groupBy: "sessionId", groupKey: sessionId };
}

async function openGraphFromCrossSearchHit(hit) {
    try {
        if (!hit?.sessionId || hit.importId == null) {
            showMessage("Session ou import manquant pour le graphe.", "error");
            return;
        }

        applyCrossSearchHitToFilters(hit);
        prepareCrossSearchGraphGroup(hit);

        showMessage("Construction du graphe pour cette session...", "info");
        await loadWorkflowGraph("sessionId", hit.sessionId);
        showExpertPanel("workflowGraphSection", { silent: true });
    } catch (e) {
        showMessage(e.message || "Erreur ouverture graphe.", "error");
    }
}

async function openContextFromCrossSearchHit(hit) {
    try {
        if (!hit?.anchorLogId) {
            showMessage("Aucun log de référence pour le contexte avant/après.", "error");
            return;
        }

        applyCrossSearchHitToFilters(hit);
        const group = prepareCrossSearchGraphGroup(hit);
        if (!group) {
            showMessage("Session manquante pour le contexte.", "error");
            return;
        }

        window.currentWorkflowGraph = {
            ...(window.currentWorkflowGraph || {}),
            groupBy: group.groupBy,
            groupKey: group.groupKey
        };

        await loadContextAroundLog(hit.anchorLogId);
    } catch (e) {
        showMessage(e.message || "Erreur contexte avant/après.", "error");
    }
}

function openSessionFromCrossSearch(sessionId) {
    if (!sessionId) return;
    setInputValue("logFilterSessionId", sessionId);
    setInputValue("compareSessionA", sessionId);
    document.getElementById("logGroupBy").value = "sessionId";
    onLoadLogsClick();
    showMessage("Session chargée dans les filtres logs.", "info");
}

function prefillSessionCompare(groupKey, groupBy) {
    if (typeof switchMainTab === "function") {
        switchMainTab("compare", { silent: true });
    } else {
        document.getElementById("mepCompareSection")?.classList.remove("hidden");
        document.getElementById("explorerSection")?.classList.remove("hidden");
    }
    const importIds = getLogFilters(false).importIds || [];
    if (importIds.length) {
        setInputValue("compareImportIds", importIds.join(","));
        setInputValue("crossSearchImportIds", importIds.join(","));
    }
    if (groupBy === "sessionId" && groupKey) {
        setInputValue("compareSessionA", groupKey);
        setInputValue("logFilterSessionId", groupKey);
        showMessage("Session A renseignée — saisissez la session B puis Comparez.", "info");
    } else {
        showMessage("Regroupez par Session ID pour préremplir la comparaison.", "info");
    }
}

async function onCompareSessionsClick() {
    try {
        const importIds = resolveExplorerImportIds("compareImportIds");
        if (!importIds.length) {
            showMessage("Veuillez saisir au moins un Import ID.", "error");
            return;
        }

        const sessionIdA = document.getElementById("compareSessionA")?.value?.trim() || "";
        const sessionIdB = document.getElementById("compareSessionB")?.value?.trim() || "";
        if (!sessionIdA || !sessionIdB) {
            showMessage("Les deux Session ID sont requis.", "error");
            return;
        }

        const filters = getLogFilters(false);
        const payload = {
            importIds,
            sessionIdA,
            sessionIdB,
            processName: document.getElementById("compareProcessFilter")?.value?.trim() || null,
            dateFrom: filters.dateFrom ? toBackendDateTime(filters.dateFrom) : null,
            dateTo: filters.dateTo ? toBackendDateTime(filters.dateTo) : null
        };

        showMessage("Comparaison des sessions...", "info");

        const response = await authFetch("/explorer/compare-sessions", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        const data = await safeJson(response);
        if (!response.ok) {
            throw new Error(data?.message || "Erreur comparaison sessions.");
        }

        renderSessionCompareResult(data);
        showMessage("Comparaison terminée.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur comparaison sessions.", "error");
    }
}

function renderSessionCompareResult(data) {
    const container = document.getElementById("sessionCompareResult");
    if (!container) return;

    document.getElementById("explorerSection")?.classList.remove("hidden");

    const a = data.snapshotA || {};
    const b = data.snapshotB || {};
    const diffs = Array.isArray(data.differences) ? data.differences : [];

    const snapshotHtml = `
        <div class="compare-snapshots">
            <div class="compare-snapshot">
                <h4>Session A — ${escapeHtml(data.sessionIdA)}</h4>
                <ul>
                    <li>${escapeHtml(a.totalLogs)} logs · ${escapeHtml(a.errorCount)} erreurs</li>
                    <li>Process : ${escapeHtml(a.dominantProcess || "—")}</li>
                    <li>0 row : ${escapeHtml(a.zeroResultCount)} · warnings : ${escapeHtml(a.warningCount)}</li>
                </ul>
            </div>
            <div class="compare-snapshot">
                <h4>Session B — ${escapeHtml(data.sessionIdB)}</h4>
                <ul>
                    <li>${escapeHtml(b.totalLogs)} logs · ${escapeHtml(b.errorCount)} erreurs</li>
                    <li>Process : ${escapeHtml(b.dominantProcess || "—")}</li>
                    <li>0 row : ${escapeHtml(b.zeroResultCount)} · warnings : ${escapeHtml(b.warningCount)}</li>
                </ul>
            </div>
        </div>
    `;

    const diffRows = diffs.map(d => `
        <tr class="diff-row diff-${escapeHtml((d.status || "").toLowerCase())}">
            <td>${escapeHtml(d.category)}</td>
            <td>${escapeHtml(d.name)}</td>
            <td>${escapeHtml(d.countA)}</td>
            <td>${escapeHtml(d.countB)}</td>
            <td>${escapeHtml(d.deltaCount)}</td>
            <td>${formatDurationCell(d.maxDurationMsA, d.maxDurationMsB)}</td>
            <td><span class="status-badge">${escapeHtml(d.status)}</span></td>
            <td class="muted-text">${escapeHtml(d.note || "")}</td>
        </tr>
    `).join("");

    container.innerHTML = `
        <div class="analysis-block">
            <p>${escapeHtml(data.summary || "")}</p>
            <p><strong>Recommandation :</strong> ${escapeHtml(data.recommendation || "")}</p>
        </div>
        ${snapshotHtml}
        <div class="analysis-block table-scroll">
            <h3>Différences process / tâche / action / filtre</h3>
            <table class="compare-table">
                <thead>
                    <tr>
                        <th>Catégorie</th>
                        <th>Nom</th>
                        <th>Cnt A</th>
                        <th>Cnt B</th>
                        <th>Δ</th>
                        <th>Durée max (A / B)</th>
                        <th>Statut</th>
                        <th>Note</th>
                    </tr>
                </thead>
                <tbody>${diffRows || '<tr><td colspan="8">Aucune différence structurée.</td></tr>'}</tbody>
            </table>
        </div>
    `;
}

function formatDurationCell(msA, msB) {
    const a = msA != null ? msA + " ms" : "—";
    const b = msB != null ? msB + " ms" : "—";
    return escapeHtml(a) + " / " + escapeHtml(b);
}

function onClearCrossSearchClick() {
    const el = document.getElementById("crossSearchResult");
    if (el) el.innerHTML = "";
    showMessage("Recherche transversale vidée.", "info");
}

function onClearCompareClick() {
    const el = document.getElementById("sessionCompareResult");
    if (el) el.innerHTML = "";
    showMessage("Comparaison vidée.", "info");
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

    showExpertPanel("groupAnalysisSection", { scroll: false, silent: true });
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

            <div class="actions wrap" style="margin: 10px 0;">
                <button type="button" class="secondary-btn" onclick="loadSessionDiagnosticForGroup('${escapeJs(data.groupBy || "")}', '${escapeJs(data.groupKey || "")}')">
                    Générer la fiche diagnostic
                </button>
                <button type="button" class="secondary-btn" onclick="loadFullAnalysisForGroup('${escapeJs(data.groupBy || "")}', '${escapeJs(data.groupKey || "")}', { forceLocal: true })">
                    Rapport guidé (sans IA)
                </button>
                <button type="button" class="secondary-btn" onclick="loadFullAnalysisForGroup('${escapeJs(data.groupBy || "")}', '${escapeJs(data.groupKey || "")}')">
                    Analyse IA complète
                </button>
                <button type="button" class="secondary-btn" onclick="openExpertAssistantChat('${escapeJs(data.groupBy || "")}', '${escapeJs(data.groupKey || "")}')">
                    Chat IA
                </button>
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

            <div class="analysis-block" id="expertDiagnosticBlock">
                <h3>Fiche diagnostic (groupe)</h3>
                <div id="expertDiagnosticResult" class="muted-block">Cliquez sur <strong>Générer la fiche diagnostic</strong>.</div>
            </div>

            <div class="analysis-block" id="expertFullAnalysisBlock">
                <h3>Analyse IA complète (groupe)</h3>
                <div id="expertFullAnalysisResult" class="muted-block">Cliquez sur <strong>Analyse IA complète</strong>.</div>
            </div>

            ${renderExpertAssistantChatBlock()}
        </div>
    `;

    section?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function formatDiagnosticSummaryHtml(text) {
    if (!text) return "";
    const blocks = String(text).split(/\n\n+/);
    return blocks.map((block) => {
        const lines = block.trim().split("\n");
        if (!lines.length) return "";
        const first = lines[0].trim();
        const isSectionTitle = /^(Utilisateur|Session|Processus|Fichier|Groupe|Période|Volume|Fichier source|Synthèse exécutive|Bilan|Interprétation|Activité principale|Recherches sans résultat|Avertissements notables|Signaux performance|Chronologie|Actions recommandées|Incidents)/i.test(first)
            && !first.startsWith("•");
        if (isSectionTitle && lines.length === 1) {
            return `<h4 class="diag-heading">${escapeHtml(first)}</h4>`;
        }
        if (isSectionTitle) {
            const body = lines.slice(1).map((line) => {
                const t = line.trim();
                if (t.startsWith("•")) {
                    return `<li>${escapeHtml(t.replace(/^•\s*/, ""))}</li>`;
                }
                return `<p>${escapeHtml(t)}</p>`;
            }).join("");
            const listItems = body.includes("<li>") ? body.replace(/<p><li>/g, "<li>").replace(/<\/li><\/p>/g, "</li>") : body;
            const wrapped = listItems.includes("<li>") ? `<ul class="diag-list">${listItems}</ul>` : listItems;
            return `<h4 class="diag-heading">${escapeHtml(first)}</h4>${wrapped}`;
        }
        const inner = lines.map((line) => {
            const t = line.trim();
            if (t.startsWith("•")) return `<li>${escapeHtml(t.replace(/^•\s*/, ""))}</li>`;
            return `<p>${escapeHtml(t)}</p>`;
        }).join("");
        if (inner.includes("<li>")) return `<ul class="diag-list">${inner}</ul>`;
        return inner;
    }).join("");
}

function renderDiagRankedTable(title, items, max = 8) {
    if (!items || !items.length) return "";
    const rows = items.slice(0, max).map((item) => `
        <tr>
            <td>${escapeHtml(item.name || "—")}</td>
            <td class="diag-count">${escapeHtml(item.count ?? 0)}</td>
        </tr>
    `).join("");
    return `
        <section class="diag-section">
            <h4 class="diag-heading">${escapeHtml(title)}</h4>
            <table class="diag-table">
                <thead><tr><th>Élément</th><th>Occurrences</th></tr></thead>
                <tbody>${rows}</tbody>
            </table>
        </section>
    `;
}

function fixDisplayText(text) {
    if (text == null) return "";
    let s = String(text);
    const reps = [
        ["ÃƒÂ¨", "è"], ["ÃƒÂ©", "é"], ["ÃƒÂª", "ê"], ["ÃƒÂ ", "à"], ["ÃƒÂ´", "ô"],
        ["ÃƒÂ»", "û"], ["ÃƒÂ§", "ç"], ["ÃƒÂ‰", "É"], ["ÃƒÂ¯", "ï"], ["ÃƒÂ¼", "ü"],
        ["ÃƒÂ¢", "â"], ["ÃƒÂ«", "ë"], ["ÃƒÂ®", "î"], ["ÃƒÂ¸", "ø"], ["ÃƒÂ±", "ñ"],
        ["ÃƒÂ", ""], ["Ãƒ", ""], ["Ã©", "é"], ["Ã¨", "è"], ["Ã´", "ô"], ["Ã¢", "â"],
        ["Ã§", "ç"], ["Ã ", "à"], ["ContrÃƒÂ´le", "Contrôle"], ["ContrÃ´le", "Contrôle"],
        ["rÃƒÂ¨gle", "règle"], ["rÃ¨gle", "règle"], ["affectÃƒÂ©", "affecté"], ["affectÃ©", "affecté"]
    ];
    let prev;
    let passes = 0;
    do {
        prev = s;
        for (const [from, to] of reps) s = s.split(from).join(to);
        passes++;
    } while (s !== prev && passes < 6 && /Ã|Â/.test(s));
    return s;
}

function extractBracketField(text, field) {
    if (!text || !field) return null;
    const re = new RegExp(field + "\\s*\\[([^\\]]*)\\]", "gi");
    let m;
    while ((m = re.exec(text)) !== null) {
        const v = (m[1] || "").trim();
        if (v && v.toLowerCase() !== "null") return v;
    }
    return null;
}

function normalizeDiagItemName(item, kind) {
    const raw = fixDisplayText(item?.name || "—");
    if (kind === "warning" && (/aucune r/i.test(raw) || /no rule found/i.test(raw))) {
        const process = fixDisplayText(
            extractBracketField(raw, "PROCESS_NAME")
            || extractBracketField(raw, "processName")
            || "process inconnu"
        );
        const action = fixDisplayText(extractBracketField(raw, "ACTION_NAME") || "action ?");
        const task = fixDisplayText(extractBracketField(raw, "TASK_NAME") || "tâche ?");
        return `Règle absente — ${process} / ${action} / ${task}`;
    }
    if (kind === "perf" && /^Mémoire élevée/i.test(raw)) {
        const m = raw.match(/Mémoire élevée\s+(\d+)\s*Mo/i);
        return m ? `Mémoire ~${m[1]} Mo` : raw;
    }
    return raw;
}

function prepareDiagItems(items, kind) {
    if (!items || !items.length) return [];
    const normalized = items.map((item) => ({
        ...item,
        name: normalizeDiagItemName(item, kind),
        diagnostic: fixDisplayText(item.diagnostic || "")
    }));
    const sum = normalized.reduce((acc, i) => acc + (Number(i.count) || 1), 0);
    const hasAggregatedCounts = normalized.some((i) => Number(i.count) > 1);
    if (hasAggregatedCounts && sum > normalized.length) {
        return [...normalized].sort((a, b) => (Number(b.count) || 0) - (Number(a.count) || 0));
    }
    const map = new Map();
    for (const item of normalized) {
        const key = item.name || "—";
        const prev = map.get(key);
        if (prev) {
            prev.count = (Number(prev.count) || 1) + (Number(item.count) || 1);
        } else {
            map.set(key, { ...item, count: Number(item.count) || 1 });
        }
    }
    return [...map.values()].sort((a, b) => (Number(b.count) || 0) - (Number(a.count) || 0));
}

function humanizeTimelineLine(line) {
    const s = fixDisplayText(line);
    if (/memory usage/i.test(s)) {
        const mo = s.match(/\b(\d{3,5})\s*$/);
        const filter = s.match(/filter code\s*\[([^\]]+)\]/i);
        const tail = mo ? ` : ${mo[1]} Mo` : "";
        const filt = filter ? ` — filtre : ${filter[1]}` : "";
        const time = s.includes(" — ") ? s.split(" — ")[0] + " — " : "";
        return `${time}mémoire observée${tail}${filt}`;
    }
    return s;
}

function looksLikeEnglishAiReport(content) {
    const lower = String(content || "").toLowerCase();
    let en = 0;
    if (lower.includes("the provided")) en++;
    if (lower.includes("key observations")) en++;
    if (lower.includes("recommendations")) en++;
    if (lower.includes("next steps for")) en++;
    if (lower.includes("possible issues")) en++;
    if (lower.includes("visualvm") || lower.includes("jprofiler")) en++;
    let fr = 0;
    if (lower.includes("résumé exécutif") || lower.includes("synthèse exécutive")) fr++;
    if (lower.includes("ce qui s'est passé")) fr++;
    if (lower.includes("recommandation")) fr++;
    return (en >= 2 && fr === 0) || (lower.startsWith("the provided logs") && fr === 0);
}

function aggregateAnalysisItems(items) {
    if (!items || !items.length) return [];
    const map = new Map();
    for (const item of items) {
        const key = item.name || "—";
        const prev = map.get(key);
        if (prev) {
            prev.count = (Number(prev.count) || 1) + (Number(item.count) || 1);
        } else {
            map.set(key, { ...item, count: Number(item.count) || 1 });
        }
    }
    return [...map.values()].sort((a, b) => (Number(b.count) || 0) - (Number(a.count) || 0));
}

function renderDiagnosticExecutiveSummary(ga, incidents, employeeSummary) {
    if (employeeSummary && /Synthèse exécutive|Bilan/i.test(employeeSummary)) {
        return formatDiagnosticSummaryHtml(employeeSummary);
    }
    const total = ga.totalLogs ?? 0;
    const zero = ga.zeroResultCount ?? 0;
    const pct = total > 0 ? ((100 * zero) / total).toFixed(1) : "0";
    const topProcess = ga.processes?.[0]?.name || "—";
    const topFilter = ga.filters?.[0]?.name || "—";
    const inc = incidents?.length || 0;
    return `
        <h4 class="diag-heading">Synthèse exécutive</h4>
        <p>Sur la période analysée, <strong>${escapeHtml(ga.groupKey || "ce groupe")}</strong> a généré
        <strong>${escapeHtml(total)}</strong> logs sans erreur critique.</p>
        <p>Activité dominante : processus <strong>${escapeHtml(topProcess)}</strong>,
        filtre le plus actif <strong>${escapeHtml(topFilter)}</strong>.</p>
        <p><strong>${escapeHtml(zero)}</strong> recherches à 0 résultat (${escapeHtml(pct)} % des logs).
        ${inc ? `<strong>${escapeHtml(inc)}</strong> incident(s) de latence détecté(s).` : ""}</p>
        ${ga.conclusion ? `<p><strong>Conclusion :</strong> ${escapeHtml(ga.conclusion)}</p>` : ""}
        ${ga.recommendation ? `<p><strong>Action :</strong> ${escapeHtml(ga.recommendation)}</p>` : ""}
    `;
}

function renderDiagDetailList(title, items, max = 10, totalDeclared = null) {
    const kind = /avertissement/i.test(title) ? "warning" : /performance|mémoire/i.test(title) ? "perf" : "other";
    const aggregated = prepareDiagItems(items, kind).slice(0, max);
    if (!aggregated.length) return "";
    const sumShown = aggregated.reduce((acc, i) => acc + (Number(i.count) || 1), 0);
    const totalHint = totalDeclared != null && sumShown < totalDeclared
        ? `<p class="section-hint">Principaux éléments (total détecté sur le groupe : ${escapeHtml(totalDeclared)}).</p>`
        : "";
    const lis = aggregated.map((item) => {
        const count = item.count != null && item.count > 1 ? ` <span class="diag-count-inline">(${item.count}×)</span>` : "";
        const diag = item.diagnostic ? ` — ${escapeHtml(item.diagnostic)}` : "";
        return `<li><strong>${escapeHtml(item.name || "—")}</strong>${count}${diag}</li>`;
    }).join("");
    return `
        <section class="diag-section">
            <h4 class="diag-heading">${escapeHtml(title)}</h4>
            ${totalHint}
            <ul class="diag-list">${lis}</ul>
        </section>
    `;
}

function renderDiagTimeline(lines, max = 16) {
    if (!lines || !lines.length) return "";
    const items = lines.slice(0, max).map((line) => `<li>${escapeHtml(humanizeTimelineLine(line))}</li>`).join("");
    const more = lines.length > max
        ? `<p class="section-hint">… et ${lines.length - max} autre(s) étape(s) dans l'analyse groupe.</p>`
        : "";
    return `
        <section class="diag-section">
            <h4 class="diag-heading">Chronologie (extraits)</h4>
            <ul class="diag-timeline">${items}</ul>
            ${more}
        </section>
    `;
}

function renderDiagIncidentsRich(incidents) {
    if (!incidents || !incidents.length) return "";
    const cards = incidents.slice(0, 10).map((inc) => {
        const ms = inc.maxDurationMs != null ? `${inc.maxDurationMs} ms` : "";
        const meta = [inc.processName, inc.filterCode, ms].filter(Boolean).map(fixDisplayText).join(" · ");
        const detail = fixDisplayText(inc.explanation || inc.probableCause || "");
        return `
            <div class="diag-incident-card">
                <strong>${escapeHtml(fixDisplayText(inc.title || "Incident"))}</strong>
                ${meta ? `<div class="diag-incident-meta">${escapeHtml(meta)}</div>` : ""}
                ${detail ? `<p>${escapeHtml(detail)}</p>` : ""}
            </div>
        `;
    }).join("");
    return `
        <section class="diag-section">
            <h4 class="diag-heading">Incidents de latence (${incidents.length})</h4>
            <div class="diag-incident-list">${cards}</div>
        </section>
    `;
}

function renderExpertDiagnosticHtml(data, groupBy, groupKey) {
    const ga = data.groupAnalysis || {};
    const summary = data.employeeSummary || "";
    const ctx = data.aiContext?.context || "";
    const incidents = data.relatedIncidents || [];
    const perfCount = (ga.performanceSignals || []).length;
    const refineBySession = (groupBy || "").toLowerCase() === "username"
        ? `<p class="section-hint" style="margin-top:10px;">
            <button type="button" class="secondary-btn small-btn" onclick="showUserTopSessions('${escapeJs(groupKey)}')">
                Affiner par session (optionnel)
            </button>
           </p>`
        : "";

    const incidentBlock = renderDiagIncidentsRich(incidents);

    const activityBlock = `
        <div class="diag-activity-grid">
            ${renderDiagRankedTable("Processus les plus actifs", ga.processes, 6)}
            ${renderDiagRankedTable("Actions principales", ga.actions, 6)}
            ${renderDiagRankedTable("Filtres les plus utilisés", ga.filters, 8)}
            ${renderDiagRankedTable("Objets métier", ga.businessObjects, 6)}
        </div>
    `;

    return `
        <div class="employee-diagnostic">
            <div class="analysis-kpis">
                <span class="log-pill">Logs : ${escapeHtml(ga.totalLogs ?? 0)}</span>
                <span class="log-pill error-pill">Erreurs : ${escapeHtml(ga.errorCount ?? 0)}</span>
                <span class="log-pill warn-pill">0 row : ${escapeHtml(ga.zeroResultCount ?? 0)}</span>
                <span class="log-pill">Avert. : ${escapeHtml(ga.warningCount ?? 0)}</span>
                ${perfCount ? `<span class="log-pill warn-pill">Perf/mémoire : ${escapeHtml(perfCount)}</span>` : ""}
            </div>
            <section class="diag-section diag-report">
                ${renderDiagnosticExecutiveSummary(ga, incidents, summary)}
            </section>
            ${activityBlock}
            ${renderDiagDetailList("Recherches sans résultat", ga.zeroResults, 12, ga.zeroResultCount)}
            ${renderDiagDetailList("Avertissements", ga.warnings, 8, ga.warningCount)}
            ${renderDiagDetailList("Signaux performance / mémoire", ga.performanceSignals, 8)}
            ${renderDiagTimeline(ga.timeline, 16)}
            ${incidentBlock}
            ${refineBySession}
            <details class="diag-technical-details">
                <summary>Détails techniques (optionnel)</summary>
                <p class="section-hint">Contexte brut pour investigation approfondie — non nécessaire pour une lecture métier.</p>
                <pre class="ai-context-preview">${escapeHtml(ctx.substring(0, 3000))}${ctx.length > 3000 ? "\n…" : ""}</pre>
            </details>
        </div>
    `;
}

async function loadSessionDiagnosticForGroup(groupBy, groupKey) {
    try {
        const filters = getLogFilters(false);
        if (!filters.importIds || filters.importIds.length === 0) {
            showMessage("Veuillez saisir au moins un Import ID avant de générer une fiche diagnostic.", "error");
            return;
        }
        if (!groupKey) {
            showMessage("GroupKey manquant.", "error");
            return;
        }

        ensureExpertDiagnosticPanel();

        const out = document.getElementById("expertDiagnosticResult");
        if (out) out.innerHTML = "Analyse en cours…";

        window.currentAnalyzedGroupBy = groupBy || "sessionId";
        window.currentAnalyzedGroupKey = groupKey;

        const payload = {
            importIds: filters.importIds,
            groupBy: groupBy || "sessionId",
            groupKey: groupKey,
            dateFrom: filters.dateFrom ? toBackendDateTime(filters.dateFrom) : null,
            dateTo: filters.dateTo ? toBackendDateTime(filters.dateTo) : null
        };

        const response = await authFetch("/assistant/session-diagnostic", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        const data = await safeJson(response);
        if (!response.ok) throw new Error(data?.message || "Erreur fiche diagnostic.");

        window.currentDiagnosticSheet = data;

        if (out) {
            out.innerHTML = renderExpertDiagnosticHtml(data, groupBy, groupKey);
        }
        showExpertAssistantChat(groupBy, groupKey);
        showMessage("Fiche diagnostic générée.", "success");
    } catch (e) {
        const out = document.getElementById("expertDiagnosticResult");
        if (out) out.innerHTML = `<span class="error-text">${escapeHtml(e.message || "Erreur")}</span>`;
        showMessage(e.message || "Erreur fiche diagnostic.", "error");
    }
}

async function loadFullAnalysisForGroup(groupBy, groupKey, options = {}) {
    try {
        const filters = getLogFilters(false);
        if (!filters.importIds || filters.importIds.length === 0) {
            showMessage("Veuillez saisir au moins un Import ID avant l’analyse IA complète.", "error");
            return;
        }
        if (!groupKey) {
            showMessage("GroupKey manquant.", "error");
            return;
        }

        ensureExpertDiagnosticPanel();

        const out = document.getElementById("expertFullAnalysisResult");
        if (out) out.innerHTML = "Réflexion en cours…";

        window.currentAnalyzedGroupBy = groupBy || "sessionId";
        window.currentAnalyzedGroupKey = groupKey;

        const payload = {
            importIds: filters.importIds,
            groupBy: groupBy || "sessionId",
            groupKey: groupKey,
            dateFrom: filters.dateFrom ? toBackendDateTime(filters.dateFrom) : null,
            dateTo: filters.dateTo ? toBackendDateTime(filters.dateTo) : null,
            forceLocal: options.forceLocal === true
        };

        const response = await authFetch("/assistant/full-analysis", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        const data = await safeJson(response);
        if (!response.ok) throw new Error(data?.message || "Erreur analyse IA complète.");

        let content = String(data.reportMarkdown || "").trim();
        if (!options.forceLocal && !options._englishRetried && looksLikeEnglishAiReport(content)) {
            showMessage("Réponse IA hors périmètre — bascule sur le rapport guidé local.", "warn");
            return loadFullAnalysisForGroup(groupBy, groupKey, { ...options, forceLocal: true, _englishRetried: true });
        }

        const modeLabel = data.mode === "OPENAI" ? "IA" : "Rapport guidé (sans IA)";
        const meta = modeLabel + (data.hint ? " — " + data.hint : "");

        if (out) {
            out.innerHTML = `<div class="chat-meta">${escapeHtml(meta)}</div><div class="chat-content">${formatAssistantMarkdown(content)}</div>`;
        }
        showMessage(options.forceLocal ? "Rapport guidé généré (sans IA)." : "Analyse IA complète générée.", "success");
    } catch (e) {
        const out = document.getElementById("expertFullAnalysisResult");
        if (out) out.innerHTML = `<span class="error-text">${escapeHtml(e.message || "Erreur")}</span>`;
        showMessage(e.message || "Erreur analyse IA complète.", "error");
    }
}

function renderExpertAssistantChatBlock() {
    return `
        <div class="analysis-block employee-chat-card" id="expertAssistantChatBlock">
            <h3>Chat IA sur ce groupe</h3>
            <p class="section-hint" id="expertAssistantChatHint">Générez une fiche diagnostic ou ouvrez le chat pour poser une question.</p>
            <div id="expertChatSuggestions" class="chat-suggestions"></div>
            <div id="expertAssistantChatMessages" class="chat-messages"></div>
            <div class="chat-input-row">
                <input id="expertAssistantChatInput" type="text" placeholder="Ex : Pourquoi tant d’erreurs ? Quels filtres posent problème ?" />
                <button type="button" id="expertAssistantChatSendBtn">Envoyer</button>
            </div>
        </div>
    `;
}

function ensureExpertAssistantChatBlock() {
    if (document.getElementById("expertAssistantChatBlock")) return;
    const story = document.querySelector("#groupAnalysisResult .analysis-story")
        || document.getElementById("groupAnalysisResult");
    story?.insertAdjacentHTML("beforeend", renderExpertAssistantChatBlock());
}

function expertGroupLabel(groupBy) {
    const gb = (groupBy || "").toLowerCase();
    if (gb === "username") return "utilisateur";
    if (gb === "sessionid") return "session";
    return groupBy || "groupe";
}

function inferParentProcessFromGraph(node, graph) {
    if (!node?.id || !graph) return null;
    const edges = Array.isArray(graph.edges) ? graph.edges : [];
    const nodes = Array.isArray(graph.nodes) ? graph.nodes : [];
    for (const edge of edges) {
        if (edge.to !== node.id) continue;
        const parent = nodes.find(n => n.id === edge.from);
        if (parent?.type === "PROCESS") return parent.label || null;
    }
    return null;
}

function applyExpertChatScopeFromNode(scope, node, graph) {
    if (!node) return scope;

    const type = String(node.type || "").toUpperCase();
    const label = node.label || "";
    scope.nodeType = type || null;
    scope.nodeLabel = label || null;

    if (type === "PROCESS") scope.process = label;
    else if (type === "ACTION") scope.action = label;
    else if (type === "FILTER") scope.filter = label;

    const parentProcess = inferParentProcessFromGraph(node, graph);
    if (parentProcess && type !== "PROCESS") {
        scope.process = parentProcess;
    }

    return scope;
}

function updateExpertChatScope(node) {
    window.expertChatScope = applyExpertChatScopeFromNode(
        { nodeType: null, nodeLabel: null, process: null, action: null, filter: null },
        node,
        window.currentWorkflowGraph
    );
}

function getExpertChatScopeForRequest() {
    const scope = {
        nodeType: null,
        nodeLabel: null,
        process: null,
        action: null,
        filter: null,
        ...(window.expertChatScope || {})
    };

    const view = window.graphCurrentView;
    const graph = window.currentWorkflowGraph;
    if (view?.type === "NODE" && graph) {
        const node = (graph.nodes || []).find(n => n.id === view.nodeId);
        if (node) applyExpertChatScopeFromNode(scope, node, graph);
    }

    return scope;
}

const GRAPH_NODE_TYPE_LABELS_CHAT = {
    PROCESS: "processus",
    TASK: "tâche",
    ACTION: "action",
    FILTER: "filtre",
    OBJECT: "objet",
    FAMILY: "type de message",
    WARNING: "alerte",
    ZERO_RESULT: "0 row",
    ERROR: "erreur",
    SAVE: "sauvegarde",
    PERFORMANCE_ANOMALY: "lenteur",
    MEMORY_ANOMALY: "mémoire anormale"
};

function expertChatScopeHint() {
    const scope = getExpertChatScopeForRequest();
    const parts = [];

    if (scope.nodeType && scope.nodeLabel) {
        const kind = GRAPH_NODE_TYPE_LABELS_CHAT[scope.nodeType] || scope.nodeType.toLowerCase();
        parts.push(kind + " " + scope.nodeLabel);
    } else {
        if (scope.process) parts.push("processus " + scope.process);
        if (scope.action) parts.push("action " + scope.action);
        if (scope.filter) parts.push("filtre " + scope.filter);
    }

    return parts.length ? " Focus graphe : " + parts.join(", ") + "." : "";
}

function showExpertAssistantChat(groupBy, groupKey) {
    ensureExpertAssistantChatBlock();
    const hint = document.getElementById("expertAssistantChatHint");
    const messages = document.getElementById("expertAssistantChatMessages");
    if (!groupKey) return;

    window.expertAssistantChatHistory = [];
    const label = expertGroupLabel(groupBy);
    if (hint) {
        hint.textContent = `${label.charAt(0).toUpperCase() + label.slice(1)} ${groupKey} — posez une question en langage simple (erreurs, lenteur, 0 row, règles…).${expertChatScopeHint()}`;
    }
    if (messages) {
        messages.innerHTML = `<div class="chat-bubble chat-assistant">Bonjour. J’ai chargé le contexte du ${escapeHtml(label)} <strong>${escapeHtml(groupKey)}</strong>. Posez une question ou choisissez une suggestion.${escapeHtml(expertChatScopeHint())}</div>`;
    }
    if (typeof renderChatSuggestions === "function") {
        renderChatSuggestions("expertChatSuggestions", "expertAssistantChatInput", sendExpertAssistantChatMessage);
    }
    document.getElementById("expertAssistantChatBlock")?.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

function openExpertAssistantChat(groupBy, groupKey) {
    const filters = getLogFilters(false);
    if (!filters.importIds || filters.importIds.length === 0) {
        showMessage("Veuillez saisir au moins un Import ID avant d’ouvrir le chat.", "error");
        return;
    }
    if (!groupKey) {
        showMessage("GroupKey manquant.", "error");
        return;
    }

    ensureExpertDiagnosticPanel();
    window.currentAnalyzedGroupBy = groupBy || "sessionId";
    window.currentAnalyzedGroupKey = groupKey;
    showExpertAssistantChat(groupBy, groupKey);
}

async function sendExpertAssistantChatMessage(event) {
    event?.preventDefault?.();
    const input = document.getElementById("expertAssistantChatInput");
    const messagesEl = document.getElementById("expertAssistantChatMessages");
    const text = input?.value?.trim();
    const groupBy = window.currentAnalyzedGroupBy || window.currentDiagnosticSheet?.groupBy || "sessionId";
    const groupKey = window.currentAnalyzedGroupKey || window.currentDiagnosticSheet?.groupKey;

    if (!text) {
        showMessage("Écrivez une question.", "error");
        return;
    }
    if (!groupKey) {
        showMessage("Sélectionnez un groupe ou générez une fiche diagnostic.", "error");
        return;
    }

    const filters = getLogFilters(false);
    if (!filters.importIds || filters.importIds.length === 0) {
        showMessage("Veuillez saisir au moins un Import ID.", "error");
        return;
    }

    appendExpertChatBubble(messagesEl, "user", text);
    input.value = "";
    appendExpertChatBubble(messagesEl, "assistant", "Réflexion en cours…");

    try {
        const scope = getExpertChatScopeForRequest();
        const body = {
            importIds: filters.importIds,
            groupBy: groupBy,
            groupKey: groupKey,
            userMessage: text,
            history: (window.expertAssistantChatHistory || []).slice(-6),
            dateFrom: filters.dateFrom ? toBackendDateTime(filters.dateFrom) : null,
            dateTo: filters.dateTo ? toBackendDateTime(filters.dateTo) : null,
            scopeProcess: scope.process || null,
            scopeAction: scope.action || null,
            scopeFilter: scope.filter || null,
            scopeNodeType: scope.nodeType || null,
            scopeNodeLabel: scope.nodeLabel || null
        };
        const res = await authFetch("/assistant/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
        const data = await safeJson(res);
        if (!res.ok) throw new Error(data?.message || "Erreur assistant");

        messagesEl.removeChild(messagesEl.lastChild);
        const modeLabel = data.mode === "OPENAI" ? "IA" : "Réponse guidée";
        appendExpertChatBubble(messagesEl, "assistant", data.reply || "—", modeLabel + (data.hint ? " — " + data.hint : ""));

        if (!window.expertAssistantChatHistory) window.expertAssistantChatHistory = [];
        window.expertAssistantChatHistory.push({ role: "user", content: text });
        window.expertAssistantChatHistory.push({ role: "assistant", content: data.reply });
    } catch (e) {
        if (messagesEl?.lastChild) messagesEl.removeChild(messagesEl.lastChild);
        appendExpertChatBubble(messagesEl, "assistant", "Erreur : " + (e.message || "inconnue"));
    }
}

function appendExpertChatBubble(container, role, content, meta) {
    if (!container) return;
    const div = document.createElement("div");
    div.className = "chat-bubble chat-" + role;
    const metaHtml = meta ? `<span class="chat-meta">${escapeHtml(meta)}</span>` : "";
    div.innerHTML = metaHtml + `<div class="chat-content">${formatAssistantMarkdown(content)}</div>`;
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
}

function ensureExpertDiagnosticPanel() {
    const section = document.getElementById("groupAnalysisSection");
    const container = document.getElementById("groupAnalysisResult");
    if (!section || !container) return;

    showExpertPanel("groupAnalysisSection", { scroll: false, silent: true });

    let story = container.querySelector(":scope > .analysis-story");
    if (!story) {
        story = document.createElement("div");
        story.className = "analysis-story";
        while (container.firstChild) {
            story.appendChild(container.firstChild);
        }
        container.appendChild(story);
    }

    if (!document.getElementById("expertDiagnosticResult")) {
        const block = document.createElement("div");
        block.className = "analysis-block";
        block.id = "expertDiagnosticBlock";
        block.innerHTML = `
            <h3>Fiche diagnostic (groupe)</h3>
            <div id="expertDiagnosticResult" class="muted-block"></div>
        `;
        story.appendChild(block);
    }

    if (!document.getElementById("expertFullAnalysisResult")) {
        const block = document.createElement("div");
        block.className = "analysis-block";
        block.id = "expertFullAnalysisBlock";
        block.innerHTML = `
            <h3>Analyse IA complète (groupe)</h3>
            <div id="expertFullAnalysisResult" class="muted-block"></div>
        `;
        story.appendChild(block);
    }

    ensureExpertAssistantChatBlock();
}

async function showUserTopSessions(userName) {
    const filters = getLogFilters(false);
    ensureExpertDiagnosticPanel();

    window.currentAnalyzedGroupBy = "userName";
    window.currentAnalyzedGroupKey = userName;

    const diagOut = document.getElementById("expertDiagnosticResult");
    const aiOut = document.getElementById("expertFullAnalysisResult");
    if (diagOut) diagOut.innerHTML = "Recherche des sessions de cet utilisateur…";
    if (aiOut) aiOut.innerHTML = "Choisissez une session ci-dessous pour l’analyse IA.";

    const payload = {
        importIds: filters.importIds,
        userName: userName,
        maxSessions: 5,
        dateFrom: filters.dateFrom ? toBackendDateTime(filters.dateFrom) : null,
        dateTo: filters.dateTo ? toBackendDateTime(filters.dateTo) : null
    };

    const response = await authFetch("/assistant/user-top-sessions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });
    const data = await safeJson(response);
    if (!response.ok) throw new Error(data?.message || "Erreur top sessions utilisateur.");

    const sessions = Array.isArray(data.topSessions) ? data.topSessions : [];
    if (!sessions.length) {
        if (diagOut) diagOut.innerHTML = `<div class="empty-state">Aucune session trouvée pour ${escapeHtml(userName)} (dans les filtres actuels).</div>`;
        return;
    }

    const rows = sessions.map(s => `
        <div class="analysis-item" style="padding:10px;">
            <div><strong>Session ${escapeHtml(s.sessionId)}</strong> — ${escapeHtml(s.totalLogs)} logs · <span class="error-text">${escapeHtml(s.errorCount)} err.</span> · ${escapeHtml(s.zeroRowCount)} × 0 row</div>
            <div class="muted-text">${escapeHtml(s.firstTimestamp || "—")} → ${escapeHtml(s.lastTimestamp || "—")}</div>
            <div class="analysis-item-actions" style="margin-top:8px;">
                <button type="button" onclick="loadSessionDiagnosticForGroup('sessionId', '${escapeJs(s.sessionId)}')">Fiche diagnostic</button>
                <button type="button" class="secondary-btn" onclick="loadFullAnalysisForGroup('sessionId', '${escapeJs(s.sessionId)}')">Analyse IA complète</button>
                <button type="button" class="secondary-btn" onclick="openV2WorkflowInExplorer('sessionId', '${escapeJs(s.sessionId)}')">Ouvrir dans filtres</button>
            </div>
        </div>
    `).join("");

    if (diagOut) {
        diagOut.innerHTML = `
            <p class="section-hint">Un utilisateur regroupe plusieurs sessions. Choisissez une session pour lancer la fiche diagnostic ou l’analyse IA.</p>
            <div><strong>Utilisateur :</strong> ${escapeHtml(userName)} — sessions trouvées : ${escapeHtml(data.totalSessionsFound ?? sessions.length)} (top ${sessions.length})</div>
            <div style="margin-top:10px;">${rows}</div>
        `;
    }

    document.getElementById("groupAnalysisSection")
        ?.scrollIntoView({ behavior: "smooth", block: "start" });

    showMessage("Sessions affichées ci-dessous — choisissez une session.", "info");
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

    showExpertPanel("relatedLogsSection", { scroll: false, silent: true });
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
        ? renderChronologicalContextBlock(data.chronologicalExplanation || "")
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
    bindChronologicalContextSearch();
    setTimeout(scrollToSelectedChronoLine, 300);
}

function renderChronologicalContextBlock(text, options = {}) {
    window.lastChronologicalExplanation = text;
    window.lastChronologicalSearchQuery = "";

    const title = options.title || "Lecture chronologique avant/après";
    const placeholder = options.placeholder
        || "Rechercher (filtre, uuid, texte, SQL…) — plusieurs termes séparés par ;";
    const showScrollSelected = options.showScrollSelected !== false;

    return `
        <div class="analysis-block chrono-context-block">
            <div class="chrono-context-header">
                <h3>${escapeHtml(title)}</h3>
                <div class="chrono-context-toolbar">
                    <input
                        id="chronoContextSearch"
                        type="text"
                        class="chrono-context-search"
                        placeholder="${escapeHtml(placeholder)}"
                        autocomplete="off"
                        spellcheck="false"
                    />
                    <button type="button" class="secondary-btn small-btn" id="chronoContextSearchClearBtn">
                        Effacer
                    </button>
                    <button type="button" class="secondary-btn small-btn" id="chronoContextSearchPrevBtn" title="Précédent (Shift+Entrée)">
                        ◀ Préc.
                    </button>
                    <button type="button" class="secondary-btn small-btn" id="chronoContextSearchNextBtn" title="Suivant (Entrée)">
                        Suiv. ▶
                    </button>
                    ${showScrollSelected ? `
                    <button type="button" class="secondary-btn small-btn" id="chronoContextScrollSelectedBtn">
                        Moment sélectionné
                    </button>
                    ` : ""}
                </div>
                <p id="chronoContextSearchStats" class="chrono-context-stats muted-text"></p>
            </div>
            <div id="chronoContextLines" class="chronological-context-box">
                ${renderChronologicalExplanation(text, "")}
            </div>
        </div>
    `;
}

function bindChronologicalContextSearch() {
    const input = document.getElementById("chronoContextSearch");
    const clearBtn = document.getElementById("chronoContextSearchClearBtn");
    const prevBtn = document.getElementById("chronoContextSearchPrevBtn");
    const nextBtn = document.getElementById("chronoContextSearchNextBtn");
    const scrollBtn = document.getElementById("chronoContextScrollSelectedBtn");

    if (!input) {
        return;
    }

    let timer = null;

    input.addEventListener("input", () => {
        if (timer) {
            clearTimeout(timer);
        }
        timer = setTimeout(() => applyChronologicalContextSearch(input.value, { resetIndex: true, scrollToMatch: true }), 120);
    });

    input.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
            e.preventDefault();
            if (e.shiftKey) {
                navigateChronoSearchMatch(-1);
            } else {
                navigateChronoSearchMatch(1);
            }
        }
        if (e.key === "Escape") {
            input.value = "";
            applyChronologicalContextSearch("");
        }
    });

    clearBtn?.addEventListener("click", () => {
        input.value = "";
        applyChronologicalContextSearch("");
        input.focus();
    });

    prevBtn?.addEventListener("click", () => navigateChronoSearchMatch(-1));
    nextBtn?.addEventListener("click", () => navigateChronoSearchMatch(1));

    scrollBtn?.addEventListener("click", () => scrollToSelectedChronoLine());

    if (!window.chronoContextCtrlFBound) {
        window.chronoContextCtrlFBound = true;
        document.addEventListener("keydown", (e) => {
            if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "f") {
                const chronoInput = document.getElementById("chronoContextSearch");
                const box = document.getElementById("chronoContextLines");
                if (chronoInput && box && chronoInput.offsetParent !== null) {
                    e.preventDefault();
                    e.stopPropagation();
                    chronoInput.focus();
                    chronoInput.select();
                }
            }
        }, true);
    }

    window.chronoSearchMatchIndex = -1;
    window.chronoSearchOccurrenceCount = 0;
    applyChronologicalContextSearch("");
}

function chronologicalLineMatchesAllTerms(line, query) {
    const terms = parseOccurrenceSearchTerms(query);
    if (!terms.length) {
        return true;
    }
    const lower = line.toLowerCase();
    return terms.every(term => lower.includes(term));
}

function countChronologicalOccurrences(lines, query) {
    const terms = parseOccurrenceSearchTerms(query);
    if (!terms.length) {
        return 0;
    }
    let total = 0;
    for (const line of lines) {
        if (!chronologicalLineMatchesAllTerms(line, query)) {
            continue;
        }
        for (const term of terms) {
            try {
                const re = new RegExp(escapeRegex(term), "gi");
                const matches = line.match(re);
                total += matches ? matches.length : 0;
            } catch {
                // ignore invalid regex
            }
        }
    }
    return total;
}

function countChronologicalMatches(lines, query) {
    return countChronologicalOccurrences(lines, query);
}

function applyChronologicalContextSearch(query, options = {}) {
    const box = document.getElementById("chronoContextLines");
    const stats = document.getElementById("chronoContextSearchStats");
    const text = window.lastChronologicalExplanation || "";

    if (!box) {
        return;
    }

    const q = String(query || "").trim();
    window.lastChronologicalSearchQuery = q;

    const lines = text.split("\n").filter(line => line.trim() !== "");
    const matchCount = countChronologicalOccurrences(lines, q);
    window.chronoSearchOccurrenceCount = matchCount;

    if (options.resetIndex) {
        window.chronoSearchMatchIndex = matchCount > 0 ? 0 : -1;
    } else if (window.chronoSearchMatchIndex >= matchCount) {
        window.chronoSearchMatchIndex = matchCount > 0 ? matchCount - 1 : -1;
    } else if (window.chronoSearchMatchIndex < 0 && matchCount > 0) {
        window.chronoSearchMatchIndex = 0;
    }

    box.innerHTML = renderChronologicalExplanation(text, q, window.chronoSearchMatchIndex);

    updateChronoSearchStats(q, lines.length, matchCount, window.chronoSearchMatchIndex);

    if (options.scrollToMatch && window.chronoSearchMatchIndex >= 0) {
        scrollToChronoSearchOccurrence(window.chronoSearchMatchIndex);
    }
}

function updateChronoSearchStats(query, lineCount, matchCount, activeIndex) {
    const stats = document.getElementById("chronoContextSearchStats");
    if (!stats) {
        return;
    }
    if (!query) {
        stats.textContent = `${lineCount} ligne(s) affichée(s).`;
    } else if (matchCount === 0) {
        stats.textContent = `0 / 0 — aucune occurrence de « ${query} » — ${lineCount} lignes affichées.`;
    } else {
        const pos = activeIndex >= 0 ? activeIndex + 1 : 0;
        stats.textContent = `${pos} / ${matchCount} — ${lineCount} lignes affichées`;
    }
}

function navigateChronoSearchMatch(delta) {
    const input = document.getElementById("chronoContextSearch");
    const q = input?.value || window.lastChronologicalSearchQuery || "";
    const matchCount = window.chronoSearchOccurrenceCount || 0;

    if (!q || matchCount === 0) {
        applyChronologicalContextSearch(q);
        return;
    }

    const current = Number.isFinite(window.chronoSearchMatchIndex) && window.chronoSearchMatchIndex >= 0
        ? window.chronoSearchMatchIndex
        : 0;
    window.chronoSearchMatchIndex = (current + delta + matchCount) % matchCount;
    updateChronoActiveOccurrence(window.chronoSearchMatchIndex);
}

function updateChronoActiveOccurrence(index) {
    document.querySelectorAll(".chrono-search-hit").forEach(el => {
        el.classList.remove("chrono-search-hit-active");
    });
    const active = document.querySelector(
        `.chrono-search-hit[data-occurrence-index="${index}"]`
    );
    active?.classList.add("chrono-search-hit-active");
    active?.scrollIntoView({ behavior: "smooth", block: "center" });

    const q = window.lastChronologicalSearchQuery || "";
    const lines = (window.lastChronologicalExplanation || "").split("\n").filter(l => l.trim() !== "");
    updateChronoSearchStats(q, lines.length, window.chronoSearchOccurrenceCount || 0, index);
}

function scrollToChronoSearchOccurrence(index) {
    updateChronoActiveOccurrence(index);
}

function scrollToChronoSearchMatch(index) {
    scrollToChronoSearchOccurrence(index);
}

function renderChronologicalExplanation(text, searchQuery, activeOccurrenceIndex = -1) {
    if (!text) return "";

    const lines = text.split("\n");
    const query = String(searchQuery || "").trim();
    const terms = parseOccurrenceSearchTerms(query);
    const occurrenceCounter = { value: 0 };

    return lines.map(line => {
        if (!line.trim()) {
            return "";
        }

        const isSelected = line.includes(">>> MOMENT SÉLECTIONNÉ");
        const shouldHighlight = terms.length > 0 && chronologicalLineMatchesAllTerms(line, query);

        const classes = [
            "chrono-line",
            isSelected ? "chrono-selected" : "",
            shouldHighlight ? "chrono-line-match" : ""
        ].filter(Boolean).join(" ");

        const content = shouldHighlight
            ? highlightChronologicalOccurrences(line, query, activeOccurrenceIndex, occurrenceCounter)
            : escapeHtml(line);

        return `
            <div class="${classes}" data-chrono-match="${shouldHighlight ? "1" : "0"}">
                ${content}
            </div>
        `;
    }).join("");
}

function highlightChronologicalOccurrences(line, query, activeIndex, counterObj) {
    const terms = parseOccurrenceSearchTerms(query);
    if (!terms.length) {
        return escapeHtml(line);
    }

    const matches = [];
    for (const term of terms) {
        try {
            const re = new RegExp(escapeRegex(term), "gi");
            let m;
            while ((m = re.exec(line)) !== null) {
                matches.push({ start: m.index, end: m.index + m[0].length, text: m[0] });
            }
        } catch {
            // ignore invalid regex
        }
    }

    if (!matches.length) {
        return escapeHtml(line);
    }

    matches.sort((a, b) => a.start - b.start || b.end - a.end);

    let result = "";
    let pos = 0;
    for (const hit of matches) {
        if (hit.start < pos) {
            continue;
        }
        result += escapeHtml(line.slice(pos, hit.start));
        const idx = counterObj.value++;
        const activeClass = idx === activeIndex ? " chrono-search-hit-active" : "";
        result += `<mark class="chrono-search-hit${activeClass}" data-occurrence-index="${idx}">${escapeHtml(hit.text)}</mark>`;
        pos = hit.end;
    }
    result += escapeHtml(line.slice(pos));
    return result;
}

function highlightChronologicalSearch(line, query) {
    return highlightChronologicalOccurrences(line, query, -1, { value: 0 });
}

function escapeRegex(value) {
    return String(value || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
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
                <strong>Message</strong>
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
    const previousMode = currentLogViewMode;
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

    if (previousMode !== mode) {
        showMessage(`Affichage logs : ${LOG_VIEW_LABELS[mode] || mode}`, "info");
    }

    if (window.lastRenderedLogs && window.lastRenderedFilters) {
        renderLogsResult(window.lastRenderedLogs, window.lastRenderedFilters);
    }
}

function renderLogsResult(logs, filters) {
    window.lastRenderedLogs = logs;
    window.lastRenderedFilters = filters;

    showExpertPanel("logsResultSection", { scroll: false, silent: true });

    const logsResult =
        document.getElementById("logsResult");

    const logsSummary =
        document.getElementById("logsSummary");

    if (logsSummary) {
        logsSummary.textContent =
            `${logs.length} log(s)
            | importIds=${formatImportIdsSummary(filters.importIds)}
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

function truncateText(text, maxLen) {
    if (!text) return "";
    const s = String(text);
    return s.length <= maxLen ? s : s.substring(0, maxLen) + "...";
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

                <strong>Message</strong>

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
        window.graphNavigationHistory = [];
        window.graphNavigationFuture = [];
        window.graphCurrentView = null;

        renderWorkflowGraph(data);
        showMessage("Graphe relationnel chargé.", "success");

        const pendingQuery = window.pendingGraphLogSearch;
        if (pendingQuery) {
            window.pendingGraphLogSearch = null;
            const searchInput = document.getElementById("graphLogSearch");
            if (searchInput) {
                searchInput.value = pendingQuery;
            }
            await searchAllGraphLogs();
        }

    } catch (e) {
        window.pendingGraphLogSearch = null;
        showMessage(e.message || "Erreur lors du chargement du graphe.", "error");
    }
}


function pushGraphNavigation(view) {
    if (window.graphCurrentView) {
        window.graphNavigationHistory.push(window.graphCurrentView);
    }

    window.graphCurrentView = view;
    window.graphNavigationFuture = [];
}

function renderGraphNavigationBar() {
    return `
        <div class="graph-navigation-bar">
            <button type="button" onclick="goBackGraph()" ${window.graphNavigationHistory.length ? "" : "disabled"}>
                ← Retour
            </button>

            <button type="button" onclick="goForwardGraph()" ${window.graphNavigationFuture.length ? "" : "disabled"}>
                Avancer →
            </button>
        </div>
    `;
}

function goBackGraph() {
    if (!window.graphNavigationHistory.length) return;

    const current = window.graphCurrentView;
    const previous = window.graphNavigationHistory.pop();

    if (current) {
        window.graphNavigationFuture.push(current);
    }

    renderGraphView(previous, false);
    showMessage("Graphe : retour à la vue précédente.", "info");
}

function goForwardGraph() {
    if (!window.graphNavigationFuture.length) return;

    const current = window.graphCurrentView;
    const next = window.graphNavigationFuture.pop();

    if (current) {
        window.graphNavigationHistory.push(current);
    }

    renderGraphView(next, false);
    showMessage("Graphe : vue suivante.", "info");
}

function renderGraphView(view, push = true) {
    if (!view) return;

    if (push) {
        pushGraphNavigation(view);
    } else {
        window.graphCurrentView = view;
    }

    if (view.type === "NODE") {
        selectGraphNode(view.nodeId, false);
    }

    if (view.type === "RELATION") {
        selectGraphRelation(view.from, view.to, view.relation, false);
    }

    if (view.type === "STEP" && typeof selectExecutionStep === "function") {
        selectExecutionStep(view.stepId, false);
    }
}

function renderWorkflowGraph(graph) {
    const section = document.getElementById("workflowGraphSection");
    const container = document.getElementById("workflowGraphResult");

    showExpertPanel("workflowGraphSection", { scroll: false, silent: true });
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

                <div class="analysis-item-actions wrap" style="margin: 10px 0;">
                    <button type="button" onclick="loadSessionDiagnosticForGroup('${escapeJs(graph.groupBy || "")}', '${escapeJs(graph.groupKey || "")}')">
                        Fiche diagnostic
                    </button>
                    <button type="button" class="secondary-btn" onclick="loadFullAnalysisForGroup('${escapeJs(graph.groupBy || "")}', '${escapeJs(graph.groupKey || "")}')">
                        Analyse IA complète
                    </button>
                    <button type="button" class="secondary-btn" onclick="openExpertAssistantChat('${escapeJs(graph.groupBy || "")}', '${escapeJs(graph.groupKey || "")}')">
                        Chat IA
                    </button>
                </div>

                <div class="graph-search-box">
                    <label for="graphNodeSearch">Chercher dans le graphe (nœuds)</label>
                    <input
                        id="graphNodeSearch"
                        type="text"
                        placeholder="Ex: DML, ZRE22, Valider, ServicePortuaire..."
                        oninput="filterGraphNodes()"
                    />
                    <p class="muted-text graph-sidebar-hint">Filtre les processus, actions et filtres listés ci-dessous.</p>
                </div>

                <div class="graph-search-box">
                    <label for="graphLogSearch">Chercher dans tous les logs (${escapeHtml(graph.totalLogs ?? 0)})</label>
                    <div class="graph-log-search-row">
                        <input
                            id="graphLogSearch"
                            type="text"
                            placeholder="Ex: 14:23:45 ; thread"
                            onkeydown="if(event.key==='Enter'){event.preventDefault();searchAllGraphLogs();}"
                        />
                        <button type="button" onclick="searchAllGraphLogs()">Rechercher</button>
                    </div>
                    <p class="muted-text graph-sidebar-hint">Recherche dans tous les logs du groupe. Plusieurs termes : séparez par <strong>;</strong> (tous requis).</p>
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

function graphNodeMetricValue(node, type) {
    const label = node.label || "";
    if (type === "PERFORMANCE_ANOMALY") {
        const m = label.match(/\((\d+)\s*ms\)/i);
        return m ? Number(m[1]) : 0;
    }
    if (type === "MEMORY_ANOMALY") {
        const m = label.match(/\((\d+)\s*Mo\)/i);
        return m ? Number(m[1]) : 0;
    }
    return Number(node.count || 0);
}

function sortGraphNodesForType(nodes, type) {
    const metricTypes = new Set(["PERFORMANCE_ANOMALY", "MEMORY_ANOMALY"]);
    return [...nodes].sort((a, b) => {
        if (metricTypes.has(type)) {
            return graphNodeMetricValue(b, type) - graphNodeMetricValue(a, type);
        }
        return Number(b.count || 0) - Number(a.count || 0);
    });
}

function renderGraphNodeGroups(nodes) {
    const grouped = {};
    const l2ByParent = {};

    nodes.forEach(n => {
        if (n.level === 2 && n.parentId) {
            if (!l2ByParent[n.parentId]) l2ByParent[n.parentId] = [];
            l2ByParent[n.parentId].push(n);
            return;
        }
        const type = n.type || "OTHER";
        if (!grouped[type]) grouped[type] = [];
        grouped[type].push(n);
    });

    const types = Object.keys(grouped).sort((a, b) => {
        const ia = GRAPH_NODE_TYPE_ORDER.indexOf(a);
        const ib = GRAPH_NODE_TYPE_ORDER.indexOf(b);
        return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
    });

    return types.map(type => `
        <details class="graph-node-group graph-accordion">
            <summary class="graph-accordion-summary">
                ${escapeHtml(GRAPH_NODE_TYPE_LABELS[type] || type)} (${grouped[type].length})
            </summary>
            <div class="graph-node-list">
                ${sortGraphNodesForType(grouped[type], type)
        .map(node => {
            const children = (l2ByParent[node.id] || [])
                .sort((a, b) => Number(b.count || 0) - Number(a.count || 0))
                .map(child => renderGraphNodeButton(child, true))
                .join("");
            return renderGraphNodeButton(node, false) + children;
        })
        .join("")}
            </div>
        </details>
    `).join("");
}

const GRAPH_NODE_TYPE_LABELS = {
    PROCESS: "Processus",
    TASK: "Tâches",
    ACTION: "Actions",
    FILTER: "Filtres",
    OBJECT: "Objets",
    FAMILY: "Types de messages",
    WARNING: "Alertes",
    ZERO_RESULT: "0 row",
    ERROR: "Erreurs",
    SAVE: "Sauvegardes",
    PERFORMANCE_ANOMALY: "Lenteurs",
    MEMORY_ANOMALY: "Mémoire anormale",
    USER: "Utilisateur"
};

const GRAPH_NODE_TYPE_ORDER = [
    "PROCESS", "TASK", "ACTION", "FILTER", "OBJECT", "FAMILY",
    "WARNING", "ZERO_RESULT", "ERROR", "SAVE",
    "PERFORMANCE_ANOMALY", "MEMORY_ANOMALY", "USER", "OTHER"
];

const FAMILY_GROUP_LABELS = {
    "processus-metier": "Processus métier",
    "processus-l1": "Processus technique",
    "filtre-recherche": "Filtre & recherche",
    "regles": "Règles métier",
    "webservice": "Web services",
    "tomcat": "Structure données",
    "sauvegarde": "Sauvegarde",
    "performance": "Performance",
    "batch": "Planifié",
    "donnees-metier": "Données métier",
    "infra": "Infrastructure",
    "commun": "Commun"
};

function renderGraphNodeButton(node, isChild) {
    const severityClass = graphSeverityClass(node.severity);
    const searchText = `${node.type || ""} ${node.label || ""} ${node.familyId || ""} ${node.familyGroup || ""}`.toLowerCase();
    const childClass = isChild ? " graph-node-btn-child" : "";
    const familyHint = node.type === "FAMILY" && node.familyGroup
        ? `<small class="graph-family-category">${escapeHtml(FAMILY_GROUP_LABELS[node.familyGroup] || node.familyGroup)}</small>`
        : "";

    return `
        <button type="button"
                class="graph-node-btn ${severityClass}${childClass}"
                data-graph-node="true"
                data-search="${escapeHtml(searchText)}"
                onclick="selectGraphNode('${escapeJs(node.id || "")}')">
            <span>${escapeHtml(node.label || node.id || "N/A")}</span>
            ${familyHint}
            <small>${escapeHtml(node.count ?? 0)} occurrence(s)</small>
        </button>
    `;
}

function selectGraphNode(nodeId, push = true) {
    const graph = window.currentWorkflowGraph;
    if (!graph) return;

    const nodes = Array.isArray(graph.nodes) ? graph.nodes : [];
    const edges = Array.isArray(graph.edges) ? graph.edges : [];

    const node = nodes.find(n => n.id === nodeId);
    if (!node) return;

    updateExpertChatScope(node);

    if (push) {
        pushGraphNavigation({
            type: "NODE",
            nodeId
        });
        showMessage(`Graphe : ${node.label || node.type || "nœud"} sélectionné.`, "info");
    }

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
            ${renderGraphNavigationBar()}
            <h3>${node.type === "FAMILY" ? "Type de message" : escapeHtml(node.type || "")} : ${escapeHtml(node.label || "")}</h3>

            <div class="analysis-kpis">
                <span class="log-pill">Occurrences : ${escapeHtml(node.count ?? 0)}</span>
                <span class="log-pill ${graphSeverityClass(node.severity)}">Criticité : ${escapeHtml(node.severity || "INFO")}</span>
                ${node.type === "FAMILY" && node.familyGroup ? `<span class="log-pill">${escapeHtml(FAMILY_GROUP_LABELS[node.familyGroup] || node.familyGroup)}</span>` : ""}
                ${node.type === "FAMILY" && node.familyId ? `<span class="log-pill secondary-pill" title="Référence technique">${escapeHtml(node.familyId)}</span>` : ""}
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
    const total = Number(node.count || occurrences.length);

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
            <h3>Occurrences temporelles (${total})</h3>
            ${renderOccurrenceSearchBlock(occurrences, total)}
        </div>
    `;
}

function buildOccurrenceSearchText(occ) {
    return [
        occ.timestamp,
        occ.processName,
        occ.columnProcessName,
        occ.actionName,
        occ.filterCode,
        occ.businessObject,
        occ.messagePreview
    ].filter(Boolean).join(" ").toLowerCase();
}

function renderOccurrenceLineHtml(occ) {
    const logId = Number(occ.logId || 0);
    const disabledAttr = logId > 0 ? "" : "disabled";
    const searchText = buildOccurrenceSearchText(occ);

    return `
        <div class="occurrence-line" data-occ-search="${escapeHtml(searchText)}">
            <div>
                <strong>${escapeHtml(occ.timestamp || "N/A")}</strong><br>
                <small>
                    Process: ${escapeHtml(occ.columnProcessName || occ.processName || "N/A")} |
                    Action: ${escapeHtml(occ.actionName || "N/A")} |
                    Filtre: ${escapeHtml(occ.filterCode || "N/A")} |
                    Objet: ${escapeHtml(occ.businessObject || "N/A")}
                </small>
                <pre>${escapeHtml(occ.messagePreview || "")}</pre>
            </div>
            <div class="occurrence-actions">
                <div class="context-inline-controls">
                    <button type="button" ${disabledAttr}
                        onclick="loadContextAroundLog(${logId}, this)">
                        Voir avant/après
                    </button>
                    <div class="inline-before-after">
                        <label>Avant:
                            <input type="number" class="inline-before-input" min="0" placeholder="défaut" />
                        </label>
                        <label>Après:
                            <input type="number" class="inline-after-input" min="0" placeholder="défaut" />
                        </label>
                    </div>
                </div>
            </div>
        </div>
    `;
}

function parseOccurrenceSearchTerms(query) {
    return String(query || "")
        .split(";")
        .map(term => term.trim().toLowerCase())
        .filter(Boolean);
}

function occurrenceMatchesSearch(searchText, query) {
    const terms = parseOccurrenceSearchTerms(query);
    if (!terms.length) return true;
    return terms.every(term => searchText.includes(term));
}

function renderOccurrenceSearchBlock(occurrences, totalCount) {
    const total = Number(totalCount || occurrences.length);
    return `
        <div class="occurrence-search-box">
            <label for="graphOccurrenceSearch">Rechercher dans les occurrences</label>
            <input
                id="graphOccurrenceSearch"
                type="text"
                placeholder="Tous les termes requis, séparés par ; — ex: CHANGeAMPE;printArchive"
                oninput="filterGraphOccurrences()"
            />
            <span class="occurrence-filter-count" id="graphOccurrenceCount">
                ${occurrences.length} / ${total} affichée(s)
            </span>
        </div>
        <div class="occurrence-list" id="graphOccurrenceList" data-total-count="${total}">
            ${occurrences.map(occ => renderOccurrenceLineHtml(occ)).join("")}
        </div>
    `;
}

function filterGraphOccurrences() {
    const input = document.getElementById("graphOccurrenceSearch");
    const countEl = document.getElementById("graphOccurrenceCount");
    const list = document.getElementById("graphOccurrenceList");
    if (!list) return;

    const query = (input?.value || "").trim();
    const lines = list.querySelectorAll(".occurrence-line");
    const total = Number(list.dataset.totalCount || lines.length);
    let visible = 0;

    lines.forEach(line => {
        const text = line.getAttribute("data-occ-search") || "";
        const show = occurrenceMatchesSearch(text, query);
        line.style.display = show ? "" : "none";
        if (show) visible++;
    });

    if (countEl) {
        countEl.textContent = query
            ? `${visible} / ${total} affichée(s)`
            : `${lines.length} / ${total} affichée(s)`;
    }
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
    if (graph.groupBy && graph.groupKey) {
        return {
            groupBy: graph.groupBy,
            groupKey: graph.groupKey
        };
    }
    return getCurrentAnalyzedGroup();
}

function renderGraphRelationLine(edge, target, targetId) {
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

    const isOutgoing = title.includes("sortantes");
    const grouped = {};

    relations.forEach(edge => {
        const targetId = isOutgoing ? edge.to : edge.from;
        const target = nodes.find(n => n.id === targetId);
        const type = target?.type || "OTHER";
        if (!grouped[type]) grouped[type] = [];
        grouped[type].push({ edge, target, targetId });
    });

    const types = Object.keys(grouped).sort((a, b) => {
        const ia = GRAPH_NODE_TYPE_ORDER.indexOf(a);
        const ib = GRAPH_NODE_TYPE_ORDER.indexOf(b);
        return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
    });

    return `
        <div class="analysis-block">
            <h3>${escapeHtml(title)} (${relations.length})</h3>
            <div class="graph-relations-grouped">
                ${types.map(type => `
                    <details class="graph-node-group graph-accordion" open>
                        <summary class="graph-accordion-summary">
                            ${escapeHtml(GRAPH_NODE_TYPE_LABELS[type] || type)} (${grouped[type].length})
                        </summary>
                        <div class="graph-node-list graph-relations">
                            ${grouped[type]
                                .sort((a, b) => Number(b.edge.count || 0) - Number(a.edge.count || 0))
                                .map(({ edge, target, targetId }) => renderGraphRelationLine(edge, target, targetId))
                                .join("")}
                        </div>
                    </details>
                `).join("")}
            </div>
        </div>
    `;
}

function selectGraphRelation(from, to, relation, push = true) {
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

    if (push) {
        pushGraphNavigation({
            type: "RELATION",
            from,
            to,
            relation
        });
        showMessage(`Graphe : relation « ${relation || "lien"} » affichée.`, "info");
    }

    const fromNode = nodes.find(n => n.id === from);
    const toNode = nodes.find(n => n.id === to);

    const details = document.getElementById("graphDetails");
    if (!details) return;

    const fromOutgoing = edges.filter(e => e.from === from);
    const fromIncoming = edges.filter(e => e.to === from);

    details.innerHTML = `
        <div class="graph-detail-card">
            ${renderGraphNavigationBar()}

            <h3>Relation : ${escapeHtml(edge.relation || "")}</h3>

            <div class="analysis-kpis">
                <span class="log-pill">De : ${escapeHtml(fromNode?.type || "")} - ${escapeHtml(fromNode?.label || from)}</span>
                <span class="log-pill">Vers : ${escapeHtml(toNode?.type || "")} - ${escapeHtml(toNode?.label || to)}</span>
                <span class="log-pill">Occurrences : ${escapeHtml(edge.count ?? 0)}</span>
            </div>

            <div class="analysis-block">
                <h3>Occurrences temporelles de cette relation (${escapeHtml(edge.count ?? 0)})</h3>
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
    const total = Number(edge.count || occurrences.length);

    if (!occurrences.length) {
        return `<div class="empty-state">Aucune occurrence disponible pour cette relation.</div>`;
    }

    return renderOccurrenceSearchBlock(occurrences, total);
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
    const type = String(itemType || "").toUpperCase();
    updateExpertChatScope({ type: type, label: itemName });
    ensureExpertDiagnosticPanel();
    openExpertAssistantChat(
        window.currentAnalyzedGroupBy || "sessionId",
        window.currentAnalyzedGroupKey || ""
    );
    const hint = document.getElementById("expertAssistantChatHint");
    if (hint) {
        hint.textContent = (hint.textContent || "") + expertChatScopeHint();
    }
    showMessage("Contexte IA préparé pour " + type + " " + itemName + " — posez votre question dans le chat.", "success");
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

    hideExpertPanel("workflowGraphSection");
    window.currentWorkflowGraph = null;
    window.graphNavigationHistory = [];
    window.graphNavigationFuture = [];
    window.graphCurrentView = null;

    showMessage("Graphe relationnel vidé.", "info");
}

function resetProtectedSections() {
    const ids = [
        "userInfo",
        "uploadResult",
        "analysisResult",
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

    window.lastRenderedLogs = null;
    window.lastRenderedFilters = null;
    window.currentAnalyzedGroupBy = "";
    window.currentAnalyzedGroupKey = "";
    window.currentWorkflowGraph = null;
    window.graphNavigationHistory = [];
    window.graphNavigationFuture = [];
    window.graphCurrentView = null;

    hideAllExpertResultPanels();
    lastUploadResponse = null;
}

function showLoggedOutUI() {
    document.getElementById("loginSection")?.classList.remove("hidden");
    document.getElementById("activeImportBar")?.classList.add("hidden");
    document.getElementById("mainNav")?.classList.add("hidden");
    document.getElementById("homeSection")?.classList.add("hidden");
    document.getElementById("diagnosticSection")?.classList.add("hidden");
    document.querySelectorAll("[data-panel]").forEach(p => p.classList.add("hidden"));
    document.getElementById("uploadSection")?.classList.add("hidden");
    document.getElementById("uploadResultSection")?.classList.add("hidden");
    document.getElementById("importsSection")?.classList.add("hidden");
    document.getElementById("mepCompareSection")?.classList.add("hidden");
    document.getElementById("explorerSection")?.classList.add("hidden");
    document.getElementById("logExplorerSection")?.classList.add("hidden");
    hideAllExpertResultPanels();
}

function showLoggedInUI() {
    document.getElementById("loginSection")?.classList.add("hidden");

    if (typeof initEmployeeUX === "function") {
        initEmployeeUX();
    }
    if (typeof showEmployeeChrome === "function") {
        showEmployeeChrome();
    }
    if (typeof afterEmployeeLogin === "function") {
        afterEmployeeLogin();
    } else if (typeof switchMainTab === "function") {
        switchMainTab("home", { silent: true });
    }
}

const TOAST_DURATION_MS = { info: 3200, success: 4200, error: 8000, warn: 5000 };
let activeLoadingToast = null;
let activeAppModalResolve = null;

function ensureAppModalElements() {
    let backdrop = document.getElementById("appModalBackdrop");
    if (!backdrop) {
        backdrop = document.createElement("div");
        backdrop.id = "appModalBackdrop";
        backdrop.className = "app-modal-backdrop hidden";
        backdrop.innerHTML = `
            <div class="app-modal" role="dialog" aria-modal="true" aria-labelledby="appModalTitle">
                <h3 id="appModalTitle" class="app-modal-title"></h3>
                <div id="appModalBody" class="app-modal-body"></div>
                <div id="appModalActions" class="app-modal-actions"></div>
            </div>`;
        document.body.appendChild(backdrop);
    }
    return {
        backdrop,
        modal: backdrop.querySelector(".app-modal"),
        title: backdrop.querySelector("#appModalTitle"),
        body: backdrop.querySelector("#appModalBody"),
        actions: backdrop.querySelector("#appModalActions")
    };
}

function closeAppModal(result = false) {
    const { backdrop, modal } = ensureAppModalElements();
    backdrop.classList.add("hidden");
    backdrop.setAttribute("aria-hidden", "true");
    modal.classList.remove("modal-success", "modal-error", "modal-warn", "modal-info");
    if (activeAppModalResolve) {
        const resolve = activeAppModalResolve;
        activeAppModalResolve = null;
        resolve(result);
    }
}

function showAppConfirm({ title, message, confirmLabel = "OK", cancelLabel = "Annuler" }) {
    return new Promise((resolve) => {
        const ui = ensureAppModalElements();
        activeAppModalResolve = resolve;
        ui.title.textContent = title || "Confirmation";
        ui.body.textContent = message || "";
        ui.actions.innerHTML = "";
        ui.modal.classList.remove("modal-success", "modal-error", "modal-warn");

        const cancelBtn = document.createElement("button");
        cancelBtn.type = "button";
        cancelBtn.className = "secondary-btn";
        cancelBtn.textContent = cancelLabel;
        cancelBtn.addEventListener("click", () => closeAppModal(false));

        const okBtn = document.createElement("button");
        okBtn.type = "button";
        okBtn.textContent = confirmLabel;
        okBtn.addEventListener("click", () => closeAppModal(true));

        ui.actions.append(cancelBtn, okBtn);
        ui.backdrop.classList.remove("hidden");
        ui.backdrop.setAttribute("aria-hidden", "false");
        okBtn.focus();
    });
}

function showAppAlert(message, type = "info", title = null) {
    const titles = {
        success: "Succès",
        error: "Erreur",
        warn: "Attention",
        info: "Information"
    };
    return new Promise((resolve) => {
        const ui = ensureAppModalElements();
        activeAppModalResolve = () => resolve(true);
        ui.title.textContent = title || titles[type] || titles.info;
        ui.body.textContent = String(message ?? "");
        ui.actions.innerHTML = "";
        ui.modal.classList.remove("modal-success", "modal-error", "modal-warn", "modal-info");
        if (type) ui.modal.classList.add(`modal-${type}`);

        const okBtn = document.createElement("button");
        okBtn.type = "button";
        okBtn.textContent = "OK";
        okBtn.addEventListener("click", () => closeAppModal(true));
        ui.actions.append(okBtn);

        ui.backdrop.classList.remove("hidden");
        ui.backdrop.setAttribute("aria-hidden", "false");
        okBtn.focus();
    });
}

function ensureToastContainer() {
    let container = document.getElementById("toastContainer");
    if (!container) {
        container = document.createElement("div");
        container.id = "toastContainer";
        container.className = "toast-container";
        container.setAttribute("aria-live", "polite");
        container.setAttribute("aria-atomic", "false");
        document.body.appendChild(container);
    }
    return container;
}

function isLoadingToastMessage(text) {
    const t = String(text ?? "").trim();
    if (/…$|\.\.\.$/.test(t)) return true;
    return /en cours|chargement|construction|rafraîchissement|import en cours|connexion en cours|suppression de|génération|ouverture/i.test(t);
}

function dismissLoadingToast() {
    if (!activeLoadingToast) return;
    const toast = activeLoadingToast;
    activeLoadingToast = null;
    toast.classList.remove("toast-visible");
    toast.classList.add("toast-out");
    setTimeout(() => toast.remove(), 220);
}

function toastIconMarkup(type) {
    if (type === "loading") return `<span class="toast-spinner" aria-hidden="true"></span>`;
    if (type === "success") return `<span class="toast-icon" aria-hidden="true">✓</span>`;
    if (type === "error") return `<span class="toast-icon" aria-hidden="true">!</span>`;
    if (type === "warn") return `<span class="toast-icon" aria-hidden="true">!</span>`;
    return `<span class="toast-icon" aria-hidden="true">i</span>`;
}

function showMessage(message, type = "info") {
    const text = String(message ?? "").trim();
    if (!text) return;

    if (type === "success" || type === "error" || type === "warn") {
        dismissLoadingToast();
        showAppAlert(text, type);
        return;
    }

    const isLoading = type === "info" && isLoadingToastMessage(text);
    if (isLoading && activeLoadingToast) {
        const textEl = activeLoadingToast.querySelector(".toast-text");
        if (textEl) textEl.textContent = text;
        return;
    }

    const container = ensureToastContainer();
    const toastType = isLoading ? "loading" : type;
    const toast = document.createElement("div");
    toast.className = `toast toast-${toastType}`;
    toast.setAttribute("role", "status");

    const closeBtn = document.createElement("button");
    closeBtn.type = "button";
    closeBtn.className = "toast-close";
    closeBtn.setAttribute("aria-label", "Fermer");
    closeBtn.textContent = "×";

    const close = () => {
        if (activeLoadingToast === toast) activeLoadingToast = null;
        toast.classList.remove("toast-visible");
        toast.classList.add("toast-out");
        setTimeout(() => toast.remove(), 220);
    };

    closeBtn.addEventListener("click", close);
    toast.innerHTML = toastIconMarkup(toastType);
    const textEl = document.createElement("span");
    textEl.className = "toast-text";
    textEl.textContent = text;
    toast.appendChild(textEl);
    toast.appendChild(closeBtn);

    container.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add("toast-visible"));

    const visible = container.querySelectorAll(".toast:not(.toast-out)");
    if (visible.length > 5) {
        const oldest = visible[0];
        if (activeLoadingToast === oldest) activeLoadingToast = null;
        oldest.classList.remove("toast-visible");
        oldest.classList.add("toast-out");
        setTimeout(() => oldest.remove(), 220);
    }

    if (isLoading) {
        activeLoadingToast = toast;
        return;
    }

    const duration = TOAST_DURATION_MS[type] ?? TOAST_DURATION_MS.info;
    setTimeout(close, duration);
}

function resolveActiveImportId() {
    if (window.activeImportId) {
        return String(window.activeImportId);
    }
    return extractFirstIdFromCsv(document.getElementById("logFilterImportIds")?.value) || "";
}

function getImportIdOrThrow() {
    const value = resolveActiveImportId();

    if (!value) {
        throw new Error("Sélectionnez un import actif ou saisissez un Import ID.");
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
        const importId = resolveActiveImportId();

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

    const parsed = parseImportIds(value);
    if (parsed.ok && parsed.values.length > 0) {
        return String(parsed.values[0]);
    }

    const first = String(value)
        .split(/[,;]+/)
        .map(x => x.trim().replace(/\.+$/, ""))
        .find(Boolean);

    if (first && /^\d+$/.test(first)) {
        return first;
    }

    return "";
}

/** Plage max (ex. 7-17 = 11 IDs) et total max pour éviter les saisies accidentelles. */
const MAX_IMPORT_ID_RANGE_SPAN = 250;
const MAX_IMPORT_ID_TOTAL = 500;

/**
 * Parse Import IDs : liste (7,8,9), plage (7-17, 7 à 17, 7...17), ou mixte (6,7-10).
 */
function parseImportIds(value) {
    if (!value || !String(value).trim()) {
        return { ok: true, values: [] };
    }

    const ids = new Set();
    const segments = String(value)
        .split(/[,;]+/)
        .map(s => s.trim().replace(/\.+$/, ""))
        .filter(Boolean);

    const rangeRe =
        /^(\d+)\s*(?:-|–|—|\.\.+|à|a|to)\s*(\d+)$/iu;

    for (const segment of segments) {
        const rangeMatch = segment.match(rangeRe);
        if (rangeMatch) {
            const start = Number(rangeMatch[1]);
            const end = Number(rangeMatch[2]);
            if (start > end) {
                return {
                    ok: false,
                    values: [],
                    error: `Plage invalide « ${segment} » : le premier ID doit être ≤ au second.`
                };
            }
            const span = end - start + 1;
            if (span > MAX_IMPORT_ID_RANGE_SPAN) {
                return {
                    ok: false,
                    values: [],
                    error:
                        `Plage trop large (${span} IDs). Maximum ${MAX_IMPORT_ID_RANGE_SPAN} par plage.`
                };
            }
            for (let i = start; i <= end; i++) {
                ids.add(i);
            }
            continue;
        }

        if (/^\d+$/.test(segment)) {
            ids.add(Number(segment));
            continue;
        }

        return {
            ok: false,
            values: [],
            error:
                `Segment invalide « ${segment} ». Utilisez des nombres, des virgules ou une plage (ex. 7-17, 7 à 17).`
        };
    }

    const values = [...ids].sort((a, b) => a - b);
    if (values.length > MAX_IMPORT_ID_TOTAL) {
        return {
            ok: false,
            values: [],
            error:
                `Trop d'Import IDs (${values.length}). Maximum ${MAX_IMPORT_ID_TOTAL}.`
        };
    }

    return { ok: true, values };
}

function parseCsvLongs(value) {
    const parsed = parseImportIds(value);
    if (!parsed.ok) {
        return { ok: false, values: [] };
    }
    return { ok: true, values: parsed.values };
}

function formatImportIdsSummary(ids) {
    if (!ids?.length) {
        return "tous";
    }
    if (ids.length === 1) {
        return String(ids[0]);
    }
    if (ids.length <= 6) {
        return ids.join(",");
    }
    const sorted = [...ids].sort((a, b) => a - b);
    let contiguous = true;
    for (let i = 1; i < sorted.length; i++) {
        if (sorted[i] !== sorted[i - 1] + 1) {
            contiguous = false;
            break;
        }
    }
    if (contiguous) {
        return `${sorted[0]}–${sorted[sorted.length - 1]} (${sorted.length} imports)`;
    }
    return `${sorted[0]}…${sorted[sorted.length - 1]} (${sorted.length} imports)`;
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

function formatAssistantMarkdown(text) {
    if (!text) return "";
    return escapeHtml(text)
        .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
        .replace(/\n/g, "<br>");
}

// EXPORTS GLOBAUX
async function searchAllGraphLogs() {
    const input = document.getElementById("graphLogSearch");
    const query = input?.value?.trim();
    const graph = window.currentWorkflowGraph;

    if (!query) {
        showMessage("Saisissez un terme à rechercher.", "error");
        return;
    }
    if (!graph?.groupKey) {
        showMessage("Chargez d’abord un graphe relationnel.", "error");
        return;
    }

    const filters = getLogFilters(false);
    if (!filters.importIds?.length) {
        showMessage("Veuillez saisir au moins un Import ID.", "error");
        return;
    }

    const details = document.getElementById("graphDetails");
    if (details) {
        details.innerHTML = `<div class="empty-state">Recherche de « ${escapeHtml(query)} » dans ${escapeHtml(graph.totalLogs ?? 0)} logs…</div>`;
    }

    showMessage(`Recherche « ${query} » dans le graphe…`, "info");

    try {
        const payload = {
            importIds: filters.importIds,
            groupBy: graph.groupBy,
            groupKey: graph.groupKey,
            dateFrom: filters.dateFrom ? toBackendDateTime(filters.dateFrom) : (graph.dateFrom || null),
            dateTo: filters.dateTo ? toBackendDateTime(filters.dateTo) : (graph.dateTo || null),
            query: query
        };

        const response = await authFetch("/assistant/graph-log-search", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        const data = await safeJson(response);
        if (!response.ok) throw new Error(data?.message || "Erreur recherche logs");

        renderGraphLogSearchResults(data);
        showMessage(`${data.matchedCount ?? 0} log(s) trouvé(s) sur ${data.totalLogs ?? 0}.`, "success");
    } catch (e) {
        if (details) {
            details.innerHTML = `<div class="error-text">${escapeHtml(e.message || "Erreur")}</div>`;
        }
        showMessage(e.message || "Erreur recherche dans les logs.", "error");
    }
}

function buildGraphSearchChronologicalText(hits) {
    return hits.map(hit => {
        const ts = hit.timestamp || "N/A";
        const msg = hit.fullMessage || hit.messagePreview || "";
        return `${ts} — ${msg}`;
    }).join("\n");
}

function renderGraphLogSearchResults(data) {
    const details = document.getElementById("graphDetails");
    if (!details) return;

    const hits = Array.isArray(data.hits) ? data.hits : [];
    const matched = Number(data.matchedCount || 0);
    const total = Number(data.totalLogs || 0);
    const displayed = Number(data.displayedCount || hits.length);
    const query = data.query || "";

    window.graphCurrentView = { type: "LOG_SEARCH", query: query };

    if (!matched) {
        details.innerHTML = `
            <div class="graph-detail-card">
                ${renderGraphNavigationBar()}
                <h3>Recherche dans tous les logs</h3>
                <div class="analysis-kpis">
                    <span class="log-pill">Requête : ${escapeHtml(query)}</span>
                    <span class="log-pill">0 / ${escapeHtml(total)} trouvé(s)</span>
                </div>
                <div class="empty-state">Aucun log ne correspond à cette recherche dans les ${escapeHtml(total)} logs du groupe.</div>
            </div>
        `;
        return;
    }

    const limitNote = displayed < matched
        ? `<p class="muted-text">Affichage limité aux ${displayed} premiers résultats sur ${matched} trouvé(s).</p>`
        : "";

    const chronoText = buildGraphSearchChronologicalText(hits);

    details.innerHTML = `
        <div class="graph-detail-card">
            ${renderGraphNavigationBar()}
            <h3>Recherche dans tous les logs</h3>
            <div class="analysis-kpis">
                <span class="log-pill">Requête : ${escapeHtml(query)}</span>
                <span class="log-pill">${escapeHtml(matched)} / ${escapeHtml(total)} trouvé(s)</span>
                <span class="log-pill">${escapeHtml(displayed)} affichée(s)</span>
            </div>
            ${limitNote}
            ${renderChronologicalContextBlock(chronoText, {
                title: "Lecture chronologique des logs trouvés",
                placeholder: "Recherche dans le texte (Ctrl+F) — ex: took ou terme1;terme2",
                showScrollSelected: false
            })}
        </div>
    `;

    bindChronologicalContextSearch();
}

function filterGraphNodes() {
    const input = document.getElementById("graphNodeSearch");
    const query = (input?.value || "").toLowerCase().trim();

    const buttons = document.querySelectorAll("[data-graph-node='true']");

    buttons.forEach(btn => {
        const text = btn.getAttribute("data-search") || "";
        const visible = !query || text.includes(query);
        btn.style.display = visible ? "" : "none";
    });

    document.querySelectorAll(".graph-accordion").forEach(group => {
        const visibleButtons = Array.from(group.querySelectorAll("[data-graph-node='true']"))
            .filter(btn => btn.style.display !== "none");

        group.style.display = visibleButtons.length || !query ? "" : "none";

        if (query && visibleButtons.length > 0) {
            group.open = true;
        }
    });
}

window.showExpertPanel = showExpertPanel;
window.hideAllExpertResultPanels = hideAllExpertResultPanels;
window.restoreVisibleExpertPanels = restoreVisibleExpertPanels;
window.notifyNavigation = notifyNavigation;
window.notifyExpertPanel = notifyExpertPanel;
window.MAIN_TAB_LABELS = MAIN_TAB_LABELS;
window.dismissLoadingToast = dismissLoadingToast;
window.showMessage = showMessage;
window.loadLogsFromGroup = loadLogsFromGroup;
window.toggleWorkflowLines = toggleWorkflowLines;
window.loadLogsFromWorkflow = loadLogsFromWorkflow;
window.deleteImport = deleteImport;
window.useImportId = useImportId;
window.analyzeLogGroup = analyzeLogGroup;
window.loadSessionDiagnosticForGroup = loadSessionDiagnosticForGroup;
window.loadFullAnalysisForGroup = loadFullAnalysisForGroup;
window.openExpertAssistantChat = openExpertAssistantChat;
window.loadRelatedLogsForItem = loadRelatedLogsForItem;
window.loadWorkflowGraph = loadWorkflowGraph;
window.openV2WorkflowInExplorer = openV2WorkflowInExplorer;
window.openGraphFromCrossSearchHitFromEl = openGraphFromCrossSearchHitFromEl;
window.openContextFromCrossSearchHitFromEl = openContextFromCrossSearchHitFromEl;
window.loadContextAroundLog = loadContextAroundLog;
window.filterGraphNodes = filterGraphNodes;
window.searchAllGraphLogs = searchAllGraphLogs;
window.filterGraphOccurrences = filterGraphOccurrences;
window.goBackGraph = goBackGraph;
window.goForwardGraph = goForwardGraph;

window.selectGraphNode = selectGraphNode;
window.selectGraphRelation = selectGraphRelation;

window.analyzeGraphNodeWithContext = analyzeGraphNodeWithContext;