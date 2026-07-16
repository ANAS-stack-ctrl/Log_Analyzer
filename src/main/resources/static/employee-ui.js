/**
 * Expérience employé : import actif, navigation, accueil, fiche diagnostic, export.
 */
window.activeImportId = null;
window.activeImportLabel = "";
window.currentDiagnosticSheet = null;
window.lastImportsList = [];
window.homeDashboardCache = {
    importId: null,
    incidentsLoaded: false,
    incidentsHtml: ""
};
window.homeDashboardStale = true;

const SCENARIO_PRESETS = {
    incident: {
        label: "Incident (erreurs)",
        groupBy: "sessionId",
        errorOnly: true,
        hint: "Regroupez par session technique, puis lancez la fiche diagnostic."
    },
    zeroRow: {
        label: "Recherche 0 row",
        crossSearchType: "MESSAGE",
        crossSearchValue: "0 row",
        hint: "Recherche les lignes sans résultat."
    },
    ruleMissing: {
        label: "Règle manquante",
        crossSearchType: "MESSAGE",
        crossSearchValue: "no rule found",
        hint: "Warnings de règles absentes."
    },
    supervision: {
        label: "Supervision traction",
        processName: "SUPERVISION",
        hint: "Filtre process supervision."
    },
    tomcat: {
        label: "Run rules / structureData",
        processName: "processRunRules",
        hint: "Logs tomcat structureData."
    }
};

let employeeUxInitialized = false;

function initEmployeeUX() {
    if (employeeUxInitialized) {
        return;
    }
    employeeUxInitialized = true;

    document.getElementById("activeImportSelect")?.addEventListener("change", onActiveImportSelectChange);

    bindDelegatedAction("loadDiagnosticBtn", loadSessionDiagnosticSheet);
    bindDelegatedAction("refreshHomeBtn", () => loadHomeDashboard({ forceRefresh: true }));
    bindDelegatedAction("compareImportsBtn", compareTwoImports);
    bindDelegatedAction("listIncidentsBtn", loadImportIncidentsOnHome);
    bindDelegatedAction("refreshArchiveStatusBtn", loadArchiveAdminPanel);
    bindDelegatedAction("runArchiveNowBtn", runArchiveMaintenanceNow);
    bindDelegatedAction("exportDiagnosticBtn", exportDiagnosticReport);
    bindDelegatedAction("copyAiContextBtn", copyAiContextToClipboard);
    bindDelegatedAction("diagnosticGraphBtn", openGraphFromDiagnostic);
    bindDelegatedAction("printDiagnosticBtn", printDiagnosticReport);
    bindDelegatedAction("assistantChatSendBtn", sendAssistantChatMessage);
    bindDelegatedAction("assistantGuidedReportBtn", (e) => loadAssistantFullAnalysis(e, true));
    bindDelegatedAction("assistantFullAnalysisBtn", loadAssistantFullAnalysis);
    bindDelegatedAction("ragIndexBtn", () => startRagIndexing(false));
    bindDelegatedAction("ragReindexBtn", () => startRagIndexing(true));
    bindDelegatedAction("ragAskBtn", askRagQuestion);

    document.getElementById("scenarioPresetSelect")?.addEventListener("change", applyScenarioPreset);
    document.getElementById("assistantChatInput")?.addEventListener("keydown", (e) => {
        if (e.key === "Enter") sendAssistantChatMessage(e);
    });
    document.getElementById("ragAskInput")?.addEventListener("keydown", (e) => {
        if (e.key === "Enter") askRagQuestion(e);
    });
}

window.assistantChatHistory = [];

const CHAT_SUGGESTED_QUESTIONS = [
    "Résume ce qui s'est passé sur cette session",
    "Y a-t-il des erreurs critiques ?",
    "Pourquoi tant de recherches à 0 résultat ?",
    "Quelles sont les lenteurs ou incidents ?",
    "Quels filtres sont les plus utilisés ?"
];

function renderChatSuggestions(containerId, inputId, onSend) {
    const el = document.getElementById(containerId);
    if (!el) return;
    el.innerHTML = CHAT_SUGGESTED_QUESTIONS.map((q) =>
        `<button type="button" class="chat-suggestion-chip" data-question="${escapeHtml(q)}">${escapeHtml(q)}</button>`
    ).join("");
    el.querySelectorAll(".chat-suggestion-chip").forEach((btn) => {
        btn.addEventListener("click", () => {
            const input = document.getElementById(inputId);
            if (input) input.value = btn.getAttribute("data-question") || "";
            if (typeof onSend === "function") onSend({ preventDefault: () => {} });
        });
    });
}

/** Boutons recréés dynamiquement (fiche diagnostic) — délégation document */
function bindDelegatedAction(id, handler) {
    document.addEventListener("click", (event) => {
        const target = event.target.closest(`#${id}`);
        if (target) {
            handler(event);
        }
    });
}

function showEmployeeChrome() {
    document.getElementById("activeImportBar")?.classList.remove("hidden");
    document.getElementById("mainNav")?.classList.remove("hidden");
}

function switchMainTab(tab, options = {}) {
    if (!tab) {
        return;
    }

    const previousTab = window.currentMainTab;
    window.currentMainTab = tab;

    document.querySelectorAll("[data-main-tab]").forEach(btn => {
        const isActive = btn.getAttribute("data-main-tab") === tab;
        btn.classList.toggle("active", isActive);
    });

    document.querySelectorAll("[data-panel]").forEach(panel => {
        panel.classList.add("hidden");
    });

    const activePanel = document.querySelector(`[data-panel="${tab}"]`);
    if (activePanel) {
        activePanel.classList.remove("hidden");
    }

    if (tab === "import") {
        ["uploadSection", "uploadResultSection", "importsSection"].forEach(id => {
            document.getElementById(id)?.classList.remove("hidden");
        });
    }

    if (tab === "compare") {
        ["mepCompareSection", "explorerSection"].forEach(id => {
            document.getElementById(id)?.classList.remove("hidden");
        });
    }

    if (tab === "advanced" && typeof scheduleLogAnalysisGuidanceRefresh === "function") {
        scheduleLogAnalysisGuidanceRefresh();
    }

    if (tab === "advanced") {
        document.getElementById("logExplorerSection")?.classList.remove("hidden");
        if (typeof restoreVisibleExpertPanels === "function") {
            restoreVisibleExpertPanels();
        }
    }

    if (tab === "home") {
        const needsHomeReload = options.forceRefresh
            || window.homeDashboardStale
            || !isHomeDashboardLoaded();
        if (needsHomeReload) {
            window.homeDashboardStale = false;
            loadHomeDashboard(options).catch(err => {
                showMessage(err.message || "Erreur chargement accueil.", "error");
            });
        }
    }

    if (!options.silent && tab !== previousTab) {
        if (typeof notifyNavigation === "function") {
            notifyNavigation(tab);
        } else if (typeof showMessage === "function") {
            const labels = window.MAIN_TAB_LABELS || {
                home: "Accueil",
                import: "Importer",
                diagnose: "Diagnostiquer",
                compare: "Comparaison",
                advanced: "Expert"
            };
            showMessage(`Onglet : ${labels[tab] || tab}`, "info");
        }
    }

    if (activePanel) {
        activePanel.scrollIntoView({ behavior: "smooth", block: "start" });
    }
}

window.switchMainTab = switchMainTab;
window.initEmployeeUX = initEmployeeUX;
window.setActiveImportId = setActiveImportId;

function setActiveImportId(importId, label) {
    const previous = window.activeImportId;
    if (importId == null || importId === "") {
        window.activeImportId = null;
        window.activeImportLabel = "";
    } else {
        window.activeImportId = Number(importId);
        window.activeImportLabel = label || `Import #${importId}`;
    }
    if (previous !== window.activeImportId) {
        window.homeDashboardStale = true;
        window.homeDashboardCache.incidentsLoaded = false;
        window.homeDashboardCache.incidentsHtml = "";
        window.homeDashboardCache.importId = window.activeImportId;
    }
    syncActiveImportToAllForms();
    updateActiveImportBar();
    if (typeof scheduleLogAnalysisGuidanceRefresh === "function") {
        scheduleLogAnalysisGuidanceRefresh();
    }
}

function syncActiveImportToAllForms() {
    const id = window.activeImportId;
    if (!id) return;

    const str = String(id);
    const fields = [
        "logFilterImportIds",
        "crossSearchImportIds",
        "compareImportIds",
        "compareImportIdA",
        "compareImportIdB",
        "diagnosticImportHint"
    ];

    fields.forEach(fid => {
        const el = document.getElementById(fid);
        if (el && el.tagName !== "SPAN") {
            if (fid === "compareImportIdA" || fid === "compareImportIdB") return;
            el.value = str;
        }
    });
}

function updateActiveImportBar() {
    const label = document.getElementById("activeImportLabel");
    const select = document.getElementById("activeImportSelect");
    if (label) {
        label.textContent = window.activeImportId
            ? window.activeImportLabel
            : "Aucun import sélectionné — importez un fichier ou choisissez dans la liste";
    }
    if (select && window.activeImportId) {
        select.value = String(window.activeImportId);
    }
}

function populateActiveImportSelect(imports) {
    window.lastImportsList = Array.isArray(imports) ? imports : [];
    const select = document.getElementById("activeImportSelect");
    if (!select) return;

    const current = window.activeImportId ? String(window.activeImportId) : "";

    select.innerHTML = `<option value="">— Choisir un import —</option>` +
        window.lastImportsList.map(imp => {
            const id = imp.id ?? imp.importId;
            const name = imp.fileName || imp.originalFileName || "";
            const status = imp.status || "";
            return `<option value="${escapeHtml(String(id))}" ${String(id) === current ? "selected" : ""}>
                #${escapeHtml(String(id))} — ${escapeHtml(name)} (${escapeHtml(status)})
            </option>`;
        }).join("");
}

function onActiveImportSelectChange() {
    const select = document.getElementById("activeImportSelect");
    if (!select || !select.value) {
        setActiveImportId(null, "");
        return;
    }
    const imp = window.lastImportsList.find(i => String(i.id ?? i.importId) === select.value);
    const name = imp ? (imp.fileName || imp.originalFileName || "") : "";
    setActiveImportId(select.value, `#${select.value} — ${name}`);
    showMessage(`Import actif : #${select.value}`, "success");
}

function isHomeDashboardLoaded() {
    return Boolean(document.getElementById("homeDashboard")?.querySelector(".home-actions"));
}

function cacheHomeIncidentsHtml() {
    const el = document.getElementById("homeIncidents");
    if (!el || !el.innerHTML.trim()) {
        return;
    }
    window.homeDashboardCache.importId = window.activeImportId;
    window.homeDashboardCache.incidentsLoaded = true;
    window.homeDashboardCache.incidentsHtml = el.innerHTML;
}

function restoreHomeIncidentsFromCache() {
    const cache = window.homeDashboardCache;
    if (!cache.incidentsLoaded || !cache.incidentsHtml) {
        return;
    }
    if (cache.importId !== window.activeImportId) {
        return;
    }
    const el = document.getElementById("homeIncidents");
    if (el) {
        el.innerHTML = cache.incidentsHtml;
    }
}

async function loadHomeDashboard(options = {}) {
    const container = document.getElementById("homeDashboard");
    if (!container) return;

    const forceRefresh = options.forceRefresh === true;
    const preserveIncidents = !forceRefresh
        && window.homeDashboardCache.incidentsLoaded
        && window.homeDashboardCache.importId === window.activeImportId
        && window.homeDashboardCache.incidentsHtml;

    try {
        if (!preserveIncidents) {
            showMessage("Chargement de l’accueil...", "info");
        }

        const [dashRes, importsRes] = await Promise.all([
            authFetch("/analysis/dashboard"),
            authFetch("/imports")
        ]);

        const dash = await safeJson(dashRes);
        const imports = await safeJson(importsRes);

        if (!dashRes.ok) throw new Error(dash?.message || "Erreur dashboard");
        populateActiveImportSelect(imports);

        if (window.currentUserRole === "ADMIN") {
            loadArchiveAdminPanel();
        }

        let qualityHtml = "";
        if (window.activeImportId) {
            try {
                const qRes = await authFetch(`/explorer/import-quality/${window.activeImportId}`);
                const q = await safeJson(qRes);
                if (qRes.ok) {
                    qualityHtml = `
                        <div class="analysis-block">
                            <h3>Qualité import actif #${escapeHtml(window.activeImportId)}</h3>
                            ${formatExtractionAuditHtml(q)}
                        </div>
                    `;
                }
            } catch (_) { /* ignore */ }
        }

        container.innerHTML = `
            <div class="analysis-kpis">
                <span class="log-pill">${escapeHtml(dash.totalImports)} import(s)</span>
                <span class="log-pill">${escapeHtml(dash.totalLogs)} logs</span>
                <span class="log-pill error-pill">${escapeHtml(dash.totalErrors)} erreurs</span>
                <span class="log-pill warn-pill">${escapeHtml(dash.totalRuleNotFoundSignals)} signaux règles</span>
            </div>
            <div class="home-actions actions wrap">
                <button type="button" onclick="switchMainTab('import')">Importer des logs</button>
                <button type="button" class="secondary-btn" onclick="switchMainTab('diagnose')">Diagnostiquer une session</button>
                <button type="button" class="secondary-btn" id="listIncidentsBtn">Latences import actif</button>
            </div>
            ${qualityHtml}
            <div id="homeIncidents"></div>
            <div id="latencyOriginPanel" class="latency-origin-panel" aria-live="polite"></div>
        `;

        if (preserveIncidents) {
            restoreHomeIncidentsFromCache();
        } else {
            window.homeDashboardCache.incidentsLoaded = false;
            window.homeDashboardCache.incidentsHtml = "";
        }
        window.homeDashboardCache.importId = window.activeImportId;
        window.homeDashboardStale = false;

        if (!preserveIncidents) {
            showMessage("Accueil chargé.", "success");
        }
    } catch (e) {
        container.innerHTML = `<p class="muted-text">${escapeHtml(e.message)}</p>`;
        showMessage(e.message || "Erreur accueil.", "error");
    }
}

window.lastImportIncidents = [];
window.lastImportIncidentsRaw = [];
window.importAnomaliesSeverityFilter = "ALL";
window.latencyFilterThreshold = "gte:2000";
window.latencyFilterUser = "";
window.latencyFilterProcess = "";
window.latencyFilterFilter = "";
window.latencySortBy = "durationDesc";
window.latencyViewMode = "process";

const LATENCY_INCIDENT_TYPES = new Set(["HIGH_LATENCY", "PERFORMANCE_WARNING"]);

/** Une latence peut générer 2 cartes : une message process, une message filtre. */
function expandLatencyIncidents(incidents) {
    const expanded = [];
    for (const inc of incidents) {
        const hasFilter = Boolean(inc.filterCode && String(inc.filterCode).trim());
        const hasProcess = Boolean(inc.processName && String(inc.processName).trim());

        if (hasProcess) {
            expanded.push({ ...inc, _latencyKind: "process" });
        }
        if (hasFilter) {
            expanded.push({ ...inc, _latencyKind: "filter" });
        }
        if (!hasProcess && !hasFilter) {
            expanded.push({ ...inc, _latencyKind: "process" });
        }
    }
    return expanded;
}

function filterByLatencyViewMode(incidents, viewMode) {
    const mode = viewMode === "filter" ? "filter" : "process";
    return incidents.filter(inc => inc._latencyKind === mode);
}

function countLatencyByKind(incidents) {
    const expanded = expandLatencyIncidents(incidents);
    return {
        process: expanded.filter(i => i._latencyKind === "process").length,
        filter: expanded.filter(i => i._latencyKind === "filter").length
    };
}

function filterLatencyIncidents(data) {
    return (Array.isArray(data) ? data : []).filter(inc => LATENCY_INCIDENT_TYPES.has(inc.incidentType));
}

const LATENCY_THRESHOLD_OPTIONS = [
    { value: "gte:2000", label: "≥ 2 s" },
    { value: "gte:3000", label: "≥ 3 s" },
    { value: "gte:5000", label: "≥ 5 s" },
    { value: "gte:10000", label: "≥ 10 s" },
    { value: "gte:20000", label: "≥ 20 s" },
    { value: "gte:30000", label: "≥ 30 s" },
    { value: "gte:60000", label: "≥ 60 s" }
];

function normalizeLatencyThreshold(value) {
    if (value == null || value === "") {
        return "gte:2000";
    }
    const str = String(value);
    if (str.startsWith("lt:") || str.startsWith("gt:")) {
        return `gte:${str.slice(3)}`;
    }
    if (str.includes(":")) {
        return str;
    }
    return `gte:${str}`;
}

function parseLatencyThresholdRule(value) {
    const normalized = normalizeLatencyThreshold(value);
    const sep = normalized.indexOf(":");
    const op = normalized.slice(0, sep);
    const ms = Number(normalized.slice(sep + 1));
    return { op, ms: Number.isFinite(ms) ? ms : 2000 };
}

function matchesLatencyThreshold(durationMs, thresholdValue) {
    const duration = durationMs || 0;
    const { op, ms } = parseLatencyThresholdRule(thresholdValue);
    if (op === "lt") {
        return duration < ms;
    }
    if (op === "gt") {
        return duration > ms;
    }
    return duration >= ms;
}

function latencyThresholdLabel(value) {
    const normalized = normalizeLatencyThreshold(value);
    const found = LATENCY_THRESHOLD_OPTIONS.find(opt => opt.value === normalized);
    return found ? found.label : normalized;
}

function renderLatencyThresholdSelectOptions() {
    const current = normalizeLatencyThreshold(window.latencyFilterThreshold);
    return LATENCY_THRESHOLD_OPTIONS.map(opt =>
        `<option value="${opt.value}" ${current === opt.value ? "selected" : ""}>${escapeHtml(opt.label)}</option>`
    ).join("");
}

function applyLatencyIncidentFilters(data) {
    let incidents = filterLatencyIncidents(data);
    incidents = incidents.filter(inc =>
        matchesLatencyThreshold(inc.maxDurationMs, window.latencyFilterThreshold)
    );

    const user = (window.latencyFilterUser || "").trim().toLowerCase();
    if (user) {
        incidents = incidents.filter(inc => (inc.userName || "").toLowerCase().includes(user));
    }

    const process = (window.latencyFilterProcess || "").trim().toLowerCase();
    if (process) {
        incidents = incidents.filter(inc => (inc.processName || "").toLowerCase().includes(process));
    }

    const filter = (window.latencyFilterFilter || "").trim().toLowerCase();
    if (filter) {
        incidents = incidents.filter(inc => (inc.filterCode || "").toLowerCase().includes(filter));
    }

    if (window.latencySortBy === "durationDesc") {
        incidents.sort((a, b) => (b.maxDurationMs || 0) - (a.maxDurationMs || 0));
    } else if (window.latencySortBy === "durationAsc") {
        incidents.sort((a, b) => (a.maxDurationMs || 0) - (b.maxDurationMs || 0));
    }

    return incidents;
}

function onLatencyFilterChange() {
    const thresholdEl = document.getElementById("latencyFilterThreshold");
    const userEl = document.getElementById("latencyFilterUser");
    const processEl = document.getElementById("latencyFilterProcess");
    const filterEl = document.getElementById("latencyFilterFilter");
    const sortEl = document.getElementById("latencyFilterSort");

    window.latencyFilterThreshold = thresholdEl?.value || "gte:2000";
    window.latencyFilterUser = userEl?.value || "";
    window.latencyFilterProcess = processEl?.value || "";
    window.latencyFilterFilter = filterEl?.value || "";
    window.latencySortBy = sortEl?.value || "durationDesc";

    const panel = document.getElementById("importAnomaliesPanel");
    const home = document.getElementById("homeIncidents");
    if (panel && panel.innerHTML.trim()) {
        renderImportAnomaliesPanel(window.lastImportIncidentsRaw, panel);
    }
    if (home && home.innerHTML.trim()) {
        renderImportAnomaliesPanel(window.lastImportIncidentsRaw, home);
    }
}

function formatIncidentTimestamp(ts) {
    if (!ts) return "—";
    const s = String(ts).replace("T", " ").slice(0, 19);
    return s;
}

function incidentSeverityClass(severity) {
    if (severity === "HIGH") return "error-pill";
    if (severity === "MEDIUM") return "warn-pill";
    return "";
}

function prepareIncidentContext(inc) {
    const importId = inc.importId || window.activeImportId;
    if (importId != null) {
        setInputValue("logFilterImportIds", String(importId));
        setInputValue("crossSearchImportIds", String(importId));
        if (typeof setActiveImportId === "function") {
            setActiveImportId(importId, `#${importId}`);
        }
    }

    let groupBy = "sessionId";
    let groupKey = inc.sessionId || "";
    if (groupKey) {
        setInputValue("logFilterSessionId", groupKey);
        setInputValue("compareSessionA", groupKey);
    } else if (inc.userName) {
        groupBy = "userName";
        groupKey = inc.userName;
        setInputValue("logFilterUserName", groupKey);
    }

    window.currentAnalyzedGroupBy = groupBy;
    window.currentAnalyzedGroupKey = groupKey;
    const groupSelect = document.getElementById("logGroupBy");
    if (groupSelect) groupSelect.value = groupBy;

    if (inc.processName) {
        setInputValue("logFilterProcessName", inc.processName);
    }
    if (inc.filterCode) {
        setInputValue("crossSearchType", "FILTER");
        setInputValue("crossSearchValue", inc.filterCode);
    }
    return { groupBy, groupKey };
}

function setLatencyViewMode(mode) {
    const previous = window.latencyViewMode;
    window.latencyViewMode = mode === "filter" ? "filter" : "process";
    onLatencyFilterChange();
    if (previous !== window.latencyViewMode) {
        showMessage(
            window.latencyViewMode === "filter" ? "Vue latences : filtres" : "Vue latences : process",
            "info"
        );
    }
}

function renderImportAnomaliesPanel(data, container, options = {}) {
    if (!container) return;

    window.lastImportIncidentsRaw = Array.isArray(data) ? data : [];
    const filteredBase = applyLatencyIncidentFilters(window.lastImportIncidentsRaw);
    const expanded = expandLatencyIncidents(filteredBase);
    const kindCounts = countLatencyByKind(filteredBase);
    const viewMode = window.latencyViewMode === "filter" ? "filter" : "process";
    const incidents = filterByLatencyViewMode(expanded, viewMode);
    window.lastImportIncidents = incidents;

    const severityFilter = options.severityFilter || window.importAnomaliesSeverityFilter || "ALL";
    const filtered = severityFilter === "ALL"
        ? incidents
        : incidents.filter(i => i.severity === severityFilter);

    const highCount = incidents.filter(i => i.severity === "HIGH").length;
    const mediumCount = incidents.filter(i => i.severity === "MEDIUM").length;
    const lowCount = incidents.filter(i => i.severity === "LOW").length;

    if (!filteredBase.length) {
        container.innerHTML = `
            <div class="analysis-block success-block import-anomalies-empty">
                <h3>Latences détectées</h3>
                <p>Aucune latence au seuil <strong>${escapeHtml(latencyThresholdLabel(window.latencyFilterThreshold))}</strong> sur cet import.</p>
            </div>
        `;
        maybeCacheHomeIncidents(container);
        return;
    }

    if (!incidents.length) {
        const otherMode = viewMode === "filter" ? "process" : "filter";
        const otherCount = viewMode === "filter" ? kindCounts.process : kindCounts.filter;
        container.innerHTML = `
            <div class="analysis-block import-anomalies-empty">
                <h3>Latences détectées</h3>
                <p>Aucune latence <strong>${viewMode === "filter" ? "filtre" : "process"}</strong> dans la sélection.</p>
                ${otherCount > 0 ? `<button type="button" class="secondary-btn" onclick="setLatencyViewMode('${otherMode}')">Voir les latences ${otherMode === "filter" ? "filtre" : "process"} (${otherCount})</button>` : ""}
            </div>
        `;
        maybeCacheHomeIncidents(container);
        return;
    }

    const viewHint = viewMode === "filter"
        ? "Affichage des messages de latence sur les <strong>codes filtre</strong> (écrans processFilter, recherches…)."
        : "Affichage des messages de latence sur les <strong>processus</strong> (process.*, SAVE, webservice…).";

    const apiLatencies = applyLatencyIncidentFilters(window.lastImportIncidentsRaw);
    const apiWithFilter = apiLatencies.filter(inc => inc.filterCode).length;
    const filterDiag = apiWithFilter === 0
        ? `<p class="muted-text">Détection : <strong>0</strong> latence filtre au seuil actuel — les lignes lentes de cet import sont surtout des <strong>processus</strong> (process.*, SAVE), pas des écrans <code>processFilter</code> avec <code>filter code [...]</code> &gt; 2 s.</p>`
        : `<p class="muted-text">Détection : <strong>${apiWithFilter}</strong> latence(s) avec code filtre sur <strong>${apiLatencies.length}</strong> au seuil actuel.</p>`;

    container.innerHTML = `
        <div class="analysis-block import-anomalies-block">
            <div class="import-anomalies-header">
                <h3>Latences détectées (${incidents.length})</h3>
                <p class="section-hint">${viewHint}</p>
                ${filterDiag}
            </div>
            <div class="latency-kind-toggle actions wrap">
                <span class="latency-kind-label">Afficher</span>
                <button type="button" class="latency-kind-btn ${viewMode === "process" ? "active" : ""}"
                    onclick="setLatencyViewMode('process')">Process uniquement (${kindCounts.process})</button>
                <button type="button" class="latency-kind-btn ${viewMode === "filter" ? "active" : ""}"
                    onclick="setLatencyViewMode('filter')">Filtre uniquement (${kindCounts.filter})</button>
            </div>
            <div class="import-anomalies-filters latency-advanced-filters">
                <label>Seuil ms
                    <select id="latencyFilterThreshold" onchange="onLatencyFilterChange()">
                        ${renderLatencyThresholdSelectOptions()}
                    </select>
                </label>
                <label>Utilisateur
                    <input id="latencyFilterUser" type="text" placeholder="Ex: safouane" value="${escapeHtml(window.latencyFilterUser || "")}" onchange="onLatencyFilterChange()" />
                </label>
                <label>Process
                    <input id="latencyFilterProcess" type="text" placeholder="Ex: CHANGEAMPE" value="${escapeHtml(window.latencyFilterProcess || "")}" onchange="onLatencyFilterChange()" oninput="onLatencyFilterChange()" />
                </label>
                <label>Filtre
                    <input id="latencyFilterFilter" type="text" placeholder="Ex: TRCEXP_BYAMPE" value="${escapeHtml(window.latencyFilterFilter || "")}" onchange="onLatencyFilterChange()" oninput="onLatencyFilterChange()" />
                </label>
                <label>Tri
                    <select id="latencyFilterSort" onchange="onLatencyFilterChange()">
                        <option value="durationDesc" ${window.latencySortBy === "durationDesc" ? "selected" : ""}>Durée ↓</option>
                        <option value="durationAsc" ${window.latencySortBy === "durationAsc" ? "selected" : ""}>Durée ↑</option>
                    </select>
                </label>
            </div>
            <div class="import-anomalies-filters actions wrap">
                <button type="button" class="secondary-btn small-btn ${severityFilter === "ALL" ? "active" : ""}"
                    onclick="filterImportAnomalies('ALL')">Toutes (${incidents.length})</button>
                <button type="button" class="secondary-btn small-btn ${severityFilter === "HIGH" ? "active" : ""}"
                    onclick="filterImportAnomalies('HIGH')">Critiques (${highCount})</button>
                <button type="button" class="secondary-btn small-btn ${severityFilter === "MEDIUM" ? "active" : ""}"
                    onclick="filterImportAnomalies('MEDIUM')">Moyennes (${mediumCount})</button>
                <button type="button" class="secondary-btn small-btn ${severityFilter === "LOW" ? "active" : ""}"
                    onclick="filterImportAnomalies('LOW')">Faibles (${lowCount})</button>
            </div>
            <div class="import-anomalies-list">
                ${filtered.length ? filtered.map((inc, idx) => renderIncidentCard(inc, idx)).join("") :
                    `<p class="muted-text">Aucune anomalie pour ce filtre.</p>`}
            </div>
        </div>
    `;
    maybeCacheHomeIncidents(container);
}

function maybeCacheHomeIncidents(container) {
    if (container?.id === "homeIncidents") {
        cacheHomeIncidentsHtml();
    }
}

function formatIncidentExplanation(inc) {
    const ms = inc.maxDurationMs;
    if (ms != null && Number.isFinite(Number(ms))) {
        if (inc._latencyKind === "filter" && inc.filterCode) {
            return `Une étape atteint ${Number(ms)} ms sur le filtre ${inc.filterCode}.`;
        }
        return `Une étape atteint ${Number(ms)} ms sur ${inc.processName || "process"}.`;
    }
    return inc.explanation || "";
}

function renderIncidentCard(inc, index) {
    const isFilter = inc._latencyKind === "filter";
    const evidence = (inc.evidenceMessages || []).slice(0, 2);
    const meta = [
        isFilter && inc.filterCode
            ? `Filtre : <code><strong>${escapeHtml(inc.filterCode)}</strong></code>`
            : "",
        !isFilter && inc.processName
            ? `Process : <strong>${escapeHtml(inc.processName)}</strong>`
            : "",
        inc.userName ? `Utilisateur : <strong>${escapeHtml(inc.userName)}</strong>` : "",
        inc.sessionId ? `Session : <code>${escapeHtml(inc.sessionId)}</code>` : "",
        inc.maxDurationMs != null ? `<strong>${escapeHtml(inc.maxDurationMs)} ms</strong>` : "",
        inc.logCount != null ? `${escapeHtml(inc.logCount)} log(s)` : "",
        inc.errorCount > 0 ? `<span class="error-text">${escapeHtml(inc.errorCount)} erreur(s)</span>` : ""
    ].filter(Boolean).join(" · ");

    const period = inc.firstTimestamp
        ? `${formatIncidentTimestamp(inc.firstTimestamp)} → ${formatIncidentTimestamp(inc.lastTimestamp)}`
        : "";

    return `
        <div class="explorer-hit-card incident-card import-anomaly-card">
            <div class="incident-card-head">
                <strong>${escapeHtml(inc.title || inc.incidentType)}</strong>
                <span class="log-pill ${isFilter ? "warn-pill" : "muted-pill"}">${isFilter ? "FILTRE" : "PROCESS"}</span>
                <span class="log-pill ${incidentSeverityClass(inc.severity)}">${escapeHtml(inc.severity)}</span>
                <span class="log-pill muted-pill">${escapeHtml(inc.incidentType || "")}</span>
            </div>
            <p>${escapeHtml(formatIncidentExplanation(inc))}</p>
            ${inc.probableCause ? `<p class="muted-text">Cause probable : ${escapeHtml(inc.probableCause)}</p>` : ""}
            ${meta ? `<p class="incident-meta">${meta}</p>` : ""}
            ${period ? `<p class="muted-text">Période : ${escapeHtml(period)}</p>` : ""}
            ${evidence.length ? `
                <details class="incident-evidence">
                    <summary>Extraits des logs (${evidence.length})</summary>
                    <ul>${evidence.map(m => `<li><code>${escapeHtml(m)}</code></li>`).join("")}</ul>
                </details>
            ` : ""}
            <div class="actions wrap incident-actions">
                <button type="button" class="primary-btn" onclick="openIncidentLatencyOrigin(${index})">Origine de la lenteur</button>
                ${inc.sessionId ? `<button type="button" onclick="openIncidentSession('${escapeJs(inc.sessionId)}')">Diagnostiquer session</button>` : ""}
                <button type="button" class="secondary-btn" onclick="openIncidentExplorer(${index})">Voir les logs</button>
                ${inc.userName ? `<button type="button" class="secondary-btn" onclick="openIncidentUserGraph(${index})">Graphe utilisateur</button>` : ""}
                ${isFilter && inc.filterCode ? `<button type="button" class="secondary-btn" onclick="openIncidentFilterSearch(${index})">Logs liés (filtre)</button>` : ""}
                <button type="button" class="secondary-btn" onclick="openIncidentAssistantChat(${index})">Chat IA</button>
            </div>
        </div>
    `;
}

function filterImportAnomalies(severity) {
    window.importAnomaliesSeverityFilter = severity;
    const panel = document.getElementById("importAnomaliesPanel");
    const home = document.getElementById("homeIncidents");
    if (panel && panel.innerHTML.trim()) {
        renderImportAnomaliesPanel(window.lastImportIncidentsRaw, panel, { severityFilter: severity });
    }
    if (home && home.innerHTML.trim()) {
        renderImportAnomaliesPanel(window.lastImportIncidentsRaw, home, { severityFilter: severity });
    }
    const label = severity === "ALL" ? "Toutes" : severity;
    showMessage(`Filtre latences : ${label}`, "info");
}

async function loadImportAnomalies(importId, container) {
    if (!container || importId == null) return;

    showMessage(`Analyse des latences import #${importId}…`, "info");
    container.innerHTML = `<div class="analysis-block"><p class="muted-text">Analyse des anomalies en cours…</p></div>`;

    try {
        const res = await authFetch(`/analysis/import/${importId}/incidents`);
        const data = await safeJson(res);
        if (!res.ok) throw new Error(data?.message || "Erreur détection anomalies");

        renderImportAnomaliesPanel(data, container);
        const shown = filterByLatencyViewMode(
            expandLatencyIncidents(applyLatencyIncidentFilters(data)),
            window.latencyViewMode === "filter" ? "filter" : "process"
        ).length;
        if (shown) {
            showMessage(`${shown} latence(s) affichée(s) sur l’import #${importId}.`, "success");
        } else {
            showMessage(`Aucune latence sur l’import #${importId} avec les filtres actuels.`, "info");
        }
    } catch (e) {
        container.innerHTML = `<div class="analysis-block"><p class="error-text">${escapeHtml(e.message || "Erreur anomalies")}</p></div>`;
        showMessage(e.message || "Erreur latences.", "error");
    }
}

async function loadImportIncidentsOnHome() {
    if (!window.activeImportId) {
        showMessage("Choisissez d’abord un import actif.", "error");
        return;
    }

    const container = document.getElementById("homeIncidents");
    if (!container) return;

    showMessage("Chargement des latences…", "info");
    await loadImportAnomalies(window.activeImportId, container);
}

function openIncidentSession(sessionId) {
    if (!sessionId) return;
    switchMainTab("diagnose", { silent: true });
    showMessage(`Ouverture diagnostic session ${sessionId}…`, "info");
    setInputValue("diagnosticSessionId", sessionId);
    setInputValue("logFilterSessionId", sessionId);
    document.getElementById("logGroupBy").value = "sessionId";
    loadSessionDiagnosticSheet();
}

function openIncidentExplorer(index) {
    const inc = window.lastImportIncidents?.[index];
    if (!inc) return;

    switchMainTab("advanced", { silent: true });
    showMessage("Chargement des logs de l’incident…", "info");
    const { groupBy, groupKey } = prepareIncidentContext(inc);
    const errorCb = document.getElementById("logFilterErrorOnly");
    if (errorCb) errorCb.checked = false;

    if (groupKey && typeof loadLogsFromGroup === "function") {
        loadLogsFromGroup(groupBy, groupKey);
    } else if (typeof onLoadLogsClick === "function") {
        onLoadLogsClick();
    }
}

function buildIncidentGraphLogQuery(inc) {
    if (!inc) return null;
    if (inc._latencyKind === "filter" && inc.filterCode && String(inc.filterCode).trim()) {
        return String(inc.filterCode).trim();
    }
    if (inc.processName && String(inc.processName).trim()) {
        return String(inc.processName).trim();
    }
    if (inc.filterCode && String(inc.filterCode).trim()) {
        return String(inc.filterCode).trim();
    }
    return null;
}

async function openIncidentUserGraph(index) {
    const inc = window.lastImportIncidents?.[index];
    if (!inc?.userName) return;

    switchMainTab("advanced", { silent: true });
    showMessage(`Ouverture graphe utilisateur ${inc.userName}…`, "info");
    prepareIncidentContext(inc);
    window.pendingGraphLogSearch = buildIncidentGraphLogQuery(inc);
    if (typeof loadWorkflowGraph === "function") {
        await loadWorkflowGraph("userName", inc.userName);
    }
}

function openIncidentFilterSearch(index) {
    const inc = window.lastImportIncidents?.[index];
    if (!inc?.filterCode) return;

    switchMainTab("compare", { silent: true });
    showMessage(`Recherche filtre ${inc.filterCode}…`, "info");
    prepareIncidentContext(inc);
    document.getElementById("crossSearchBtn")?.click();
}

function formatLatencyDurationMs(ms) {
    const n = Number(ms);
    if (!Number.isFinite(n) || n <= 0) return "—";
    if (n >= 1000) return `${(n / 1000).toFixed(1).replace(".", ",")} s`;
    return `${n} ms`;
}

function renderLatencyTimelineSteps(steps) {
    const list = Array.isArray(steps) ? steps : [];
    if (!list.length) {
        return `<p class="muted-text">Aucune étape WORKS structurée détectée dans la fenêtre.</p>`;
    }
    return `
        <div class="latency-timeline-table-wrap">
            <table class="latency-timeline-table">
                <thead>
                    <tr>
                        <th>Étape</th>
                        <th>Durée</th>
                        <th>Lignes</th>
                        <th>Mémoire</th>
                        <th>Détail</th>
                    </tr>
                </thead>
                <tbody>
                    ${list.map(step => {
                        const ms = Number(step.durationMs) || 0;
                        const navTerm = ms > 0 ? `took [${ms}]` : (step.operationName || "");
                        const navAttr = navTerm
                            ? `class="latency-step-row is-navable ${step.bottleneck ? "latency-bottleneck-row" : ""} ${step.suspect ? "latency-suspect-row" : ""}" data-latency-nav="${escapeHtml(navTerm)}" title="Cliquez pour voir cette étape dans les logs."`
                            : `class="${step.bottleneck ? "latency-bottleneck-row" : ""} ${step.suspect ? "latency-suspect-row" : ""}"`;
                        return `
                        <tr ${navAttr} ${step.suspect ? `data-suspect-reason="${escapeHtml(step.suspectReason || "")}"` : ""}>
                            <td>${escapeHtml(step.operationName || step.stepType || "")}${step.suspect ? ' <span class="latency-suspect-tag">⚠ mesure douteuse</span>' : ""}${step.logId != null ? ` <span class="latency-evidence-meta">#${escapeHtml(String(step.logId))}</span>` : ""}</td>
                            <td><strong>${escapeHtml(formatLatencyDurationMs(step.durationMs))}</strong></td>
                            <td>${step.rowCount != null ? escapeHtml(step.rowCount) : "—"}</td>
                            <td>${step.memoryMo != null ? escapeHtml(step.memoryMo) + " Mo" : "—"}</td>
                            <td class="latency-step-detail">${escapeHtml(step.threadInfo || step.detail || "")}</td>
                        </tr>`;
                    }).join("")}
                </tbody>
            </table>
        </div>
    `;
}

function getLatencyOriginPanel() {
    const importPanel = document.getElementById("importAnomaliesPanel");
    const onImportTab = importPanel && importPanel.offsetParent !== null && importPanel.innerHTML.trim();
    if (onImportTab) {
        return document.getElementById("latencyOriginPanelImport")
            || document.getElementById("latencyOriginPanel");
    }
    return document.getElementById("latencyOriginPanel")
        || document.getElementById("latencyOriginPanelImport");
}

function latencySeverity(ms) {
    const n = Number(ms) || 0;
    if (n >= 20000) return { level: "critique", label: "Lenteur critique", icon: "🔴" };
    if (n >= 8000) return { level: "elevee", label: "Lenteur élevée", icon: "🟠" };
    return { level: "notable", label: "Lenteur notable", icon: "🟡" };
}

function friendlyConfidence(conf) {
    switch (String(conf || "").toUpperCase()) {
        case "HIGH": return "élevée";
        case "MEDIUM": return "moyenne";
        case "LOW": return "faible";
        default: return "—";
    }
}

function cleanProcessLabel(p) {
    if (!p) return "n/a";
    let s = String(p).trim();
    if (s.startsWith("process.")) s = s.slice("process.".length);
    return s || "n/a";
}

function latencyNaValue(v) {
    const s = (v == null ? "" : String(v)).trim();
    return s ? s : "n/a";
}

const LATENCY_STEP_LABELS = {
    ROOT_QUERY: "Requête principale (base de données)",
    GLOBAL_SEARCH: "Recherche globale",
    PARTITIONAL_SEARCH: "Recherche partitionnée",
    LOAD_CHILDREN_TOTAL: "Chargement des détails",
    SEARCH_ATTRIBUTES: "Chargement des attributs",
    LOAD_CHILDREN_B2: "Chargement complémentaire",
    RENDERING: "Affichage à l'écran",
    RULES_ENGINE: "Règles métier",
    SAVE_PERSIST: "Sauvegarde",
    VALIDATION: "Validation",
    WEBSERVICE: "Webservice",
    PRINT: "Impression / PDF",
    AUTOSTART: "AutoStart",
    WORKFLOW: "Workflow / BPM",
    BUSINESS_ACTION: "Action métier"
};

function friendlyStepLabel(step) {
    return LATENCY_STEP_LABELS[step.stepType] || step.operationName || step.stepType || "Étape";
}

const LATENCY_FIELD_HELP = {
    screen: "L'écran / formulaire métier concerné par l'opération.",
    filter: "Le filtre ou la règle métier exécuté(e) — souvent l'origine d'une requête SQL lourde.",
    action: "L'action déclenchée par l'utilisateur (ex. Valider, Rechercher).",
    process: "Le processus métier (workflow) auquel appartient l'opération.",
    user: "L'utilisateur qui a déclenché l'opération.",
    session: "Identifiant de session — isole l'activité de cet utilisateur.",
    uuid: "Identifiant technique WORKS de l'opération (uuid […] ou [N] doAction…). Filtre les logs de cette exécution.",
    object: "L'objet métier / entité (className) manipulé par la requête (ex. DChargementCont)."
};

const LATENCY_CRUMB_TYPE_LABELS = {
    USER: "Utilisateur", SESSION: "Session", PROCESS: "Processus", FILTER: "Filtre",
    ACTION: "Action", OBJECT: "Objet", UUID: "UUID", SCREEN: "Écran"
};

function crumbTypeLabel(type) {
    return LATENCY_CRUMB_TYPE_LABELS[type] || type;
}

function renderLatencyTriggerGrid(report) {
    const fields = [
        { key: "Utilisateur", value: latencyNaValue(report.userName), term: report.userName, navType: "USER", help: LATENCY_FIELD_HELP.user },
        { key: "Processus", value: cleanProcessLabel(report.processName), term: report.processName, navType: "PROCESS", help: LATENCY_FIELD_HELP.process },
        { key: "UUID", value: latencyNaValue(report.uuid), term: report.uuid, navType: "UUID", help: LATENCY_FIELD_HELP.uuid },
        { key: "Action", value: latencyNaValue(report.actionName), term: report.actionName, navType: "ACTION", help: LATENCY_FIELD_HELP.action },
        { key: "Filtre", value: latencyNaValue(report.filterCode), term: report.filterCode, navType: "FILTER", help: LATENCY_FIELD_HELP.filter },
        { key: "Session", value: latencyNaValue(report.sessionId), term: report.sessionId, navType: "SESSION", help: LATENCY_FIELD_HELP.session },
        { key: "Nom de l'écran", value: latencyNaValue(report.screenName), term: report.screenName, navType: "SCREEN", help: LATENCY_FIELD_HELP.screen },
        { key: "Objet / classe", value: latencyNaValue(report.className), term: report.className, navType: "OBJECT", help: LATENCY_FIELD_HELP.object }
    ];
    return `<div class="latency-trigger-grid">
        ${fields.map(f => {
            const term = (f.term == null ? "" : String(f.term)).trim();
            const navable = f.value !== "n/a" && term.length > 0;
            if (navable) {
                return `<button type="button" class="latency-trigger-item is-navable is-crumb"
                        data-latency-crumb="1"
                        data-nav-type="${escapeHtml(f.navType)}"
                        data-nav-value="${escapeHtml(term)}"
                        data-nav-label="${escapeHtml(f.value)}"
                        title="${escapeHtml(f.help)} — cliquez pour filtrer les logs (navigation cumulative).">
                    <span class="latency-trigger-key">${escapeHtml(f.key)}</span>
                    <span class="latency-trigger-val">${escapeHtml(f.value)}</span>
                    <span class="latency-trigger-go">Filtrer ces logs →</span>
                </button>`;
            }
            return `<div class="latency-trigger-item is-na" title="${escapeHtml(f.help)}">
                <span class="latency-trigger-key">${escapeHtml(f.key)}</span>
                <span class="latency-trigger-val">${escapeHtml(f.value)}</span>
            </div>`;
        }).join("")}
    </div>`;
}

// Chaîne des déclencheurs métier (utilisateur → process → uuid → …).
// Chaque maillon est cliquable et démarre/continue la navigation cumulative.
function renderBusinessTriggerChain(report) {
    const seq = [
        { type: "USER", label: report.userName, value: report.userName },
        { type: "PROCESS", label: cleanProcessLabel(report.processName), value: report.processName },
        { type: "UUID", label: report.uuid, value: report.uuid },
        { type: "ACTION", label: report.actionName, value: report.actionName },
        { type: "FILTER", label: report.filterCode, value: report.filterCode },
        { type: "SESSION", label: report.sessionId, value: report.sessionId },
        { type: "OBJECT", label: report.className, value: report.className },
        { type: "SCREEN", label: report.screenName, value: report.screenName }
    ].filter(s => {
        const v = s.value == null ? "" : String(s.value).trim();
        return v !== "" && latencyNaValue(v) !== "n/a";
    });
    if (!seq.length) {
        return "";
    }
    const parts = seq.map(s => {
        const value = String(s.value).trim();
        const label = String(s.label == null ? s.value : s.label).trim();
        return `<button type="button" class="latency-chain-node is-crumb"
                data-latency-crumb="1"
                data-nav-type="${escapeHtml(s.type)}"
                data-nav-value="${escapeHtml(value)}"
                data-nav-label="${escapeHtml(label)}"
                title="Cliquer pour filtrer les logs sur ${escapeHtml(crumbTypeLabel(s.type))}">
            <span class="latency-chain-type">${escapeHtml(crumbTypeLabel(s.type))}</span>
            <span class="latency-chain-val">${escapeHtml(label)}</span>
        </button>`;
    }).join(`<span class="latency-chain-arrow">→</span>`);
    return `<div class="latency-chain"><span class="latency-chain-label">Chaîne des déclencheurs :</span> ${parts}</div>`;
}

const LATENCY_CONTAINER_TYPES = new Set(["GLOBAL_SEARCH", "PARTITIONAL_SEARCH"]);

function renderLatencyTimeBars(steps, maxDurationMs) {
    // On exclut les étapes "suspect" (took anomalique) : elles fausseraient l'échelle.
    const all = (Array.isArray(steps) ? steps : []).filter(s => Number(s.durationMs) > 0 && !s.suspect);
    // Les conteneurs (recherche globale/partitionnée) agrègent leurs sous-étapes :
    // on les masque pour ne pas fausser la lecture, sauf s'ils sont eux-mêmes le goulot.
    const leaves = all.filter(s => !LATENCY_CONTAINER_TYPES.has(s.stepType) || s.bottleneck);
    const measured = (leaves.length ? leaves : all)
        .sort((a, b) => Number(b.durationMs) - Number(a.durationMs))
        .slice(0, 6);
    if (!measured.length) {
        return `<p class="muted-text">Pas d'étape chronométrée dans la fenêtre analysée.</p>`;
    }
    const top = Number(measured[0].durationMs) || 1;
    return `<div class="latency-bars">
        ${measured.map(s => {
            const ms = Number(s.durationMs) || 0;
            const pct = Math.max(4, Math.round((ms / top) * 100));
            const navTerm = `took [${ms}]`;
            return `<button type="button" class="latency-bar-row is-navable ${s.bottleneck ? "is-bottleneck" : ""}"
                    data-latency-nav="${escapeHtml(navTerm)}"
                    title="Cliquez pour voir cette étape dans les logs.">
                <span class="latency-bar-label">${escapeHtml(friendlyStepLabel(s))}${s.bottleneck ? ' <span class="latency-bar-tag">cause principale</span>' : ""}</span>
                <span class="latency-bar-track"><span class="latency-bar-fill" style="width:${pct}%"></span></span>
                <span class="latency-bar-value">${escapeHtml(formatLatencyDurationMs(ms))}</span>
            </button>`;
        }).join("")}
    </div>`;
}

function renderLatencyPrimaryCause(report) {
    const cause = (report.primaryCause || "").trim();
    if (!cause) {
        return "";
    }
    const term = (report.bottleneckSearchTerm || "").trim();
    if (!term) {
        return `<div class="latency-verdict-cause">${escapeHtml(cause)}</div>`;
    }
    return `<button type="button" class="latency-verdict-cause is-navable"
                data-latency-nav="${escapeHtml(term)}"
                title="Cliquez pour voir le goulot dans les logs (« ${escapeHtml(term)} »).">
                <span>${escapeHtml(cause)}</span>
                <span class="latency-trigger-go">Voir les logs →</span>
            </button>`;
}

function renderLatencyRootCause(report) {
    const groups = Array.isArray(report.repeatedQueryGroups) ? report.repeatedQueryGroups : [];
    if (!report.rootCauseSummary && !groups.length) {
        return "";
    }
    const searchTerm = (report.rootCauseSearchTerm || "").trim();
    const summaryNavable = searchTerm.length > 0 && !!report.rootCauseSummary;
    const summaryBlock = report.rootCauseSummary
        ? (summaryNavable
            ? `<button type="button" class="latency-rootcause-summary is-navable"
                    data-latency-nav="${escapeHtml(searchTerm)}"
                    title="Cliquez pour filtrer les logs sur « ${escapeHtml(searchTerm)} ».">
                    <span class="latency-rootcause-summary-text">${escapeHtml(report.rootCauseSummary)}</span>
                    <span class="latency-trigger-go">Voir les logs →</span>
               </button>`
            : `<p class="latency-rootcause-summary">${escapeHtml(report.rootCauseSummary)}</p>`)
        : "";
    const meaningBlock = report.rootCauseMeaning
        ? `<p class="latency-rootcause-meaning"><strong>Pourquoi c’est cité :</strong> ${escapeHtml(report.rootCauseMeaning)}</p>`
        : (summaryNavable
            ? `<p class="latency-rootcause-meaning"><strong>Pourquoi c’est cité :</strong> élément observé dans la fenêtre d’analyse — cliquez pour voir les logs correspondants.</p>`
            : "");
    const rows = groups.map(g => {
        const term = (g.filterCode == null ? "" : String(g.filterCode)).trim();
        const navable = term.length > 0;
        const tag = navable ? "button" : "div";
        const attrs = navable
            ? `type="button" class="latency-rootcause-row is-navable" data-latency-nav="${escapeHtml(term)}" title="Cliquez pour voir les ${escapeHtml(String(g.count))} exécutions dans les logs."`
            : `class="latency-rootcause-row"`;
        return `<${tag} ${attrs}>
            <span class="latency-rootcause-filter">${escapeHtml(g.filterCode || "?")}</span>
            <span class="latency-rootcause-count">${escapeHtml(String(g.count))}× exéc.</span>
            <span class="latency-rootcause-total">${escapeHtml(formatLatencyDurationMs(g.totalMs))} cumulées</span>
            <span class="latency-rootcause-max">max ${escapeHtml(formatLatencyDurationMs(g.maxMs))}</span>
            ${navable ? `<span class="latency-trigger-go">Voir les logs →</span>` : ""}
        </${tag}>`;
    }).join("");
    return `
        <div class="latency-origin-section latency-rootcause">
            <h4>Source du problème</h4>
            <p class="latency-nav-hint">Cliquez la source pour ouvrir les logs relatifs, puis lisez pourquoi elle est citée.</p>
            ${summaryBlock}
            ${meaningBlock}
            ${rows ? `<div class="latency-rootcause-list">${rows}</div>` : ""}
        </div>`;
}

function renderLatencyWhyChain(report) {
    const levels = Array.isArray(report.whyChain) ? report.whyChain.filter(Boolean) : [];
    if (!levels.length) {
        return "";
    }
    return `
        <div class="latency-origin-section latency-why-chain">
            <h4>Pourquoi ? (en profondeur)</h4>
            <p class="latency-nav-hint">Pas seulement « c'est lent » : chaque niveau explique le niveau précédent.</p>
            <ol class="latency-why-list">
                ${levels.map((w, i) => `
                    <li class="latency-why-item">
                        <span class="latency-why-level">Pourquoi ${i + 1}</span>
                        <span class="latency-why-text">${escapeHtml(w)}</span>
                    </li>`).join("")}
            </ol>
        </div>`;
}

function renderLatencyRecommendations(report) {
    const recs = Array.isArray(report.recommendations) ? report.recommendations : [];
    if (!recs.length) {
        return "";
    }
    return `
        <div class="latency-origin-section latency-reco">
            <h4>Que faire pour corriger ?</h4>
            <p class="latency-nav-hint">Pistes concrètes dérivées des faits mesurés dans les logs.</p>
            <ul class="latency-reco-list">
                ${recs.map(r => `<li>${escapeHtml(r)}</li>`).join("")}
            </ul>
        </div>`;
}

function renderLatencyOriginPanel(report) {
    const panel = getLatencyOriginPanel();
    if (!panel) return;

    initLatencyNavChain(report.importId);
    window.lastLatencyReport = report;

    const steps = report.timelineSteps || [];
    const sev = latencySeverity(report.maxDurationMs);

    panel.innerHTML = `
        <div class="analysis-block latency-origin-card latency-sev-${sev.level}">
            <div class="latency-origin-header">
                <h3>Origine de la lenteur</h3>
                <button type="button" class="secondary-btn small-btn" onclick="clearLatencyOriginPanel()">Fermer</button>
            </div>

            <div class="latency-verdict">
                <div class="latency-verdict-icon">${sev.icon}</div>
                <div class="latency-verdict-main">
                    <div class="latency-verdict-title">${escapeHtml(sev.label)} — ${escapeHtml(formatLatencyDurationMs(report.maxDurationMs))}</div>
                    ${renderLatencyPrimaryCause(report)}
                </div>
                <div class="latency-verdict-confidence" ${report.analysisConfidenceReason ? `title="${escapeHtml(report.analysisConfidenceReason)}"` : ""}>Fiabilité de l'analyse<br><strong>${escapeHtml(friendlyConfidence(report.analysisConfidence))}</strong>${report.analysisConfidenceReason ? `<br><span class="latency-confidence-reason">${escapeHtml(report.analysisConfidenceReason)}</span>` : ""}</div>
            </div>

            ${report.measurementWarning ? `<div class="latency-measure-warning">⚠️ <strong>Mesure non fiable</strong> — ${escapeHtml(report.measurementWarning)}</div>` : ""}

            <div class="latency-origin-section latency-client-summary">
                <h4>En clair</h4>
                <p class="latency-client-summary-text">${escapeHtml(report.clientSummary || report.primaryCause || "")}</p>
            </div>

            ${renderLatencyWhyChain(report)}

            ${renderLatencyRecommendations(report)}

            <div id="latencyNavStatus" class="latency-nav-status" hidden></div>

            <div class="latency-origin-section">
                <h4>Déclencheur métier</h4>
                ${renderBusinessTriggerChain(report)}
                <p class="latency-nav-hint">🔎 Cliquez sur un élément pour filtrer les logs réels (navigation cumulative : chaque clic ajoute un filtre ET).</p>
                ${renderLatencyTriggerGrid(report)}
            </div>

            <div class="latency-origin-section latency-cumulative">
                <h4>Navigation guidée — logs réels (filtre cumulatif)</h4>
                <p class="latency-nav-hint">Chaque déclencheur cliqué <strong>ajoute</strong> un filtre. Utilisez ◀ Préc. / Suiv. ▶ pour remonter ou avancer dans la chaîne. Tous les logs correspondants sont affichés, sans limite.</p>
                <div id="latencyCrumbBar" class="latency-crumb-bar"></div>
                <div id="latencyCumulativeStatus" class="latency-cumulative-status muted-text"></div>
                <div id="latencyCumulativeLogs" class="latency-cumulative-logs"></div>
            </div>

            <div class="latency-origin-section">
                <h4>Où est passé le temps ?</h4>
                ${renderLatencyTimeBars(steps, report.maxDurationMs)}
            </div>

            ${renderLatencyRootCause(report)}

            <details class="latency-origin-section latency-details-tech" open>
                <summary>Analyse détaillée (technique)</summary>
                <div class="latency-tech-body">
                    <div class="latency-explain-head">
                        <h4>Explication pas à pas</h4>
                        <span id="latencyExplainBadge" class="latency-explain-badge is-loading">🧠 Génération par l'IA…</span>
                    </div>
                    <div id="latencyExplainText" class="latency-narrative">${escapeHtml(report.narrativeSummary || "Analyse en cours…")}</div>
                    <h4>Chaîne d'exécution</h4>
                    <p>${escapeHtml(report.chainExplanation || "")}</p>
                    <h4>Étapes identifiées (${steps.length})</h4>
                    ${renderLatencyTimelineSteps(steps)}
                </div>
            </details>

            <div class="latency-origin-meta">
                <span class="log-pill">${escapeHtml(report.scopeDescription || "périmètre")}</span>
                <span class="log-pill muted-pill">${escapeHtml(report.logsInWindow || 0)} log(s) analysé(s)</span>
            </div>
        </div>
    `;

    bindLatencyNavigation(panel);
    renderLatencyBreadcrumb();
    loadLatencyLlmExplanation(report);
    panel.scrollIntoView({ behavior: "smooth", block: "start" });
}

// Confie la rédaction de « Explication pas à pas » à un LLM (Ollama local ou OpenAI).
// Le Java a déjà sélectionné les logs concernés + les faits vérifiés (dans `report`) ;
// on les renvoie au backend qui appelle le modèle. En cas d'indisponibilité, on garde
// le texte règle-based déjà affiché (aucune casse).
async function loadLatencyLlmExplanation(report) {
    const textEl = document.getElementById("latencyExplainText");
    const badgeEl = document.getElementById("latencyExplainBadge");
    if (!textEl) return;

    const fallback = report.narrativeSummary || "";

    try {
        const res = await authFetch("/assistant/latency-explanation", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(report)
        });
        const data = await safeJson(res);
        if (!res.ok) throw new Error(data?.message || data?.error || "Erreur explication IA");

        const explanation = (data && data.explanation) ? data.explanation : fallback;
        const source = data?.source || "LOCAL";
        if (data && data.generatedByLlm) {
            textEl.classList.add("latency-narrative-md");
            textEl.innerHTML = renderSimpleMarkdown(explanation || "Explication indisponible.");
        } else {
            textEl.classList.remove("latency-narrative-md");
            textEl.textContent = explanation || "Explication indisponible.";
        }

        if (badgeEl) {
            badgeEl.classList.remove("is-loading");
            if (source === "OPENAI") {
                badgeEl.classList.add("is-ai");
                badgeEl.textContent = "🤖 Généré par l'IA (OpenAI · " + (data.model || "") + ")";
            } else if (source === "OLLAMA") {
                badgeEl.classList.add("is-ai");
                badgeEl.textContent = "🧠 Généré par l'IA locale (" + (data.model || "Ollama") + ")";
            } else {
                badgeEl.classList.add("is-local");
                badgeEl.textContent = "📋 Analyse guidée (sans IA)";
                if (data?.note) badgeEl.title = data.note;
            }
        }
    } catch (e) {
        textEl.classList.remove("latency-narrative-md");
        textEl.textContent = fallback || "Explication indisponible.";
        if (badgeEl) {
            badgeEl.classList.remove("is-loading");
            badgeEl.classList.add("is-local");
            badgeEl.textContent = "📋 Analyse guidée (IA indisponible)";
            badgeEl.title = e.message || "";
        }
    }
}

// Rendu markdown minimal (titres, gras, listes, tableaux) pour afficher proprement
// l'explication de l'IA. Volontairement simple et sans dépendance externe.
function renderSimpleMarkdown(md) {
    if (!md) return "";
    // Certains modèles ajoutent un bloc de raisonnement <think>…</think> : on l'enlève.
    let text = String(md).replace(/<think>[\s\S]*?<\/think>/gi, "").trim();
    const esc = (s) => s
        .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    const inline = (s) => esc(s)
        .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
        .replace(/(^|[^*])\*([^*]+)\*(?!\*)/g, "$1<em>$2</em>")
        .replace(/`([^`]+)`/g, "<code>$1</code>");

    const lines = text.split(/\r?\n/);
    let html = "";
    let i = 0;
    let listType = null;
    const closeList = () => { if (listType) { html += `</${listType}>`; listType = null; } };

    while (i < lines.length) {
        let line = lines[i];

        // Tableau markdown : ligne | ... | suivie d'une ligne de séparation | --- |
        if (/^\s*\|.*\|\s*$/.test(line) && i + 1 < lines.length && /^\s*\|?[\s:|-]+\|?\s*$/.test(lines[i + 1])) {
            closeList();
            const parseRow = (l) => l.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map(c => c.trim());
            const headers = parseRow(line);
            i += 2;
            let body = "";
            while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) {
                const cells = parseRow(lines[i]);
                body += "<tr>" + cells.map(c => `<td>${inline(c)}</td>`).join("") + "</tr>";
                i++;
            }
            html += `<table class="latency-md-table"><thead><tr>`
                + headers.map(h => `<th>${inline(h)}</th>`).join("")
                + `</tr></thead><tbody>${body}</tbody></table>`;
            continue;
        }

        let m;
        if ((m = line.match(/^\s*(#{1,4})\s+(.*)$/))) {
            closeList();
            const lvl = Math.min(m[1].length + 2, 6);
            html += `<h${lvl}>${inline(m[2])}</h${lvl}>`;
        } else if ((m = line.match(/^\s*[-*•]\s+(.*)$/))) {
            if (listType !== "ul") { closeList(); listType = "ul"; html += "<ul>"; }
            html += `<li>${inline(m[1])}</li>`;
        } else if ((m = line.match(/^\s*\d+[.)]\s+(.*)$/))) {
            if (listType !== "ol") { closeList(); listType = "ol"; html += "<ol>"; }
            html += `<li>${inline(m[1])}</li>`;
        } else if (line.trim() === "") {
            closeList();
        } else {
            closeList();
            html += `<p>${inline(line)}</p>`;
        }
        i++;
    }
    closeList();
    return html;
}

// Rend le panneau navigable : tout élément [data-latency-nav] (champ métier, étape,
// règle fautive) amène la chronologie sur les VRAIES lignes de logs le contenant.
function bindLatencyNavigation(panel) {
    if (!panel) return;
    // Barres de temps + règles répétées : recherche texte dans la chronologie de l'opération.
    panel.querySelectorAll("[data-latency-nav]").forEach(el => {
        el.addEventListener("click", (e) => {
            e.preventDefault();
            navigateLatencyToTerm(panel, el.getAttribute("data-latency-nav"), el);
        });
    });
    // Tuiles déclencheurs + chaîne métier : navigation cumulative (filtres ET).
    panel.querySelectorAll("[data-latency-crumb]").forEach(el => {
        el.addEventListener("click", (e) => {
            e.preventDefault();
            latencyCrumbClick(
                el.getAttribute("data-nav-type"),
                el.getAttribute("data-nav-value"),
                el.getAttribute("data-nav-label")
            );
        });
    });
}

// ─── Navigation cumulative : fil d'Ariane + chargement des logs réels ───
function initLatencyNavChain(importId) {
    window.latencyNavChain = { importId: importId || null, crumbs: [], pointer: -1 };
}

function latencyCrumbClick(type, value, label) {
    if (!type || value == null) return;
    const chain = window.latencyNavChain || (window.latencyNavChain = { importId: null, crumbs: [], pointer: -1 });
    const norm = (s) => String(s == null ? "" : s).trim().toLowerCase();
    const existing = chain.crumbs.findIndex(c => c.type === type && norm(c.value) === norm(value));
    if (existing >= 0) {
        // Déjà dans la chaîne : on s'y repositionne (comme un clic dans le fil d'Ariane).
        chain.pointer = existing;
    } else {
        // On tronque les maillons "en avant" (comportement type historique) puis on ajoute.
        chain.crumbs = chain.crumbs.slice(0, chain.pointer + 1);
        chain.crumbs.push({ type, value: String(value).trim(), label: (label || value) });
        chain.pointer = chain.crumbs.length - 1;
    }
    fetchCumulativeLogs();
}

function latencyNavStep(delta) {
    const chain = window.latencyNavChain;
    if (!chain || !chain.crumbs.length) return;
    const next = chain.pointer + delta;
    if (next < 0 || next >= chain.crumbs.length) return;
    chain.pointer = next;
    fetchCumulativeLogs();
}

function renderLatencyBreadcrumb() {
    const chain = window.latencyNavChain;
    const bar = document.getElementById("latencyCrumbBar");
    if (!bar || !chain) return;

    if (!chain.crumbs.length) {
        bar.innerHTML = `<span class="latency-crumb-empty">Cliquez un déclencheur ci-dessus pour démarrer la navigation.</span>`;
        return;
    }

    const trail = chain.crumbs.map((c, i) => {
        const cls = i === chain.pointer ? "is-current" : (i > chain.pointer ? "is-ahead" : "");
        return `<button type="button" class="latency-crumb ${cls}" data-crumb-index="${i}"
                title="${escapeHtml(crumbTypeLabel(c.type))} : ${escapeHtml(c.value)}">
            <span class="latency-crumb-type">${escapeHtml(crumbTypeLabel(c.type))}</span>
            <span class="latency-crumb-val">${escapeHtml(c.label || c.value)}</span>
        </button>`;
    }).join(`<span class="latency-crumb-sep">→</span>`);

    bar.innerHTML = `
        <div class="latency-crumb-controls">
            <button type="button" class="secondary-btn small-btn" id="latencyNavPrevBtn" ${chain.pointer <= 0 ? "disabled" : ""}>◀ Préc.</button>
            <button type="button" class="secondary-btn small-btn" id="latencyNavNextBtn" ${chain.pointer >= chain.crumbs.length - 1 ? "disabled" : ""}>Suiv. ▶</button>
            <button type="button" class="secondary-btn small-btn" id="latencyNavResetBtn">Réinitialiser</button>
        </div>
        <div class="latency-crumb-trail">${trail}</div>`;

    document.getElementById("latencyNavPrevBtn")?.addEventListener("click", () => latencyNavStep(-1));
    document.getElementById("latencyNavNextBtn")?.addEventListener("click", () => latencyNavStep(1));
    document.getElementById("latencyNavResetBtn")?.addEventListener("click", () => {
        chain.crumbs = [];
        chain.pointer = -1;
        fetchCumulativeLogs();
    });
    bar.querySelectorAll(".latency-crumb").forEach(el => {
        el.addEventListener("click", () => {
            const i = Number(el.getAttribute("data-crumb-index"));
            if (Number.isFinite(i)) {
                chain.pointer = i;
                fetchCumulativeLogs();
            }
        });
    });
}

async function fetchCumulativeLogs() {
    const chain = window.latencyNavChain;
    const status = document.getElementById("latencyCumulativeStatus");
    const box = document.getElementById("latencyCumulativeLogs");
    if (!chain) return;

    renderLatencyBreadcrumb();

    if (!box) return;
    if (chain.pointer < 0 || !chain.crumbs.length) {
        if (status) status.textContent = "";
        box.innerHTML = "";
        return;
    }

    const active = chain.crumbs.slice(0, chain.pointer + 1);
    const criteria = active.map(c => ({ type: c.type, value: c.value }));

    if (status) status.textContent = "Chargement des logs…";
    box.innerHTML = "";

    try {
        const res = await authFetch("/assistant/cumulative-logs", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ importId: chain.importId, criteria })
        });
        const data = await safeJson(res);
        if (!res.ok) {
            throw new Error(data?.message || data?.error || "Erreur navigation cumulative");
        }
        renderCumulativeLogs(data, active);
    } catch (e) {
        if (status) status.textContent = "";
        box.innerHTML = `<p class="error-text">${escapeHtml(e.message || "Erreur")}</p>`;
    }
}

function renderCumulativeLogs(data, active) {
    const status = document.getElementById("latencyCumulativeStatus");
    const box = document.getElementById("latencyCumulativeLogs");
    if (!box) return;

    const lines = Array.isArray(data.lines) ? data.lines : [];
    const chainText = active.map(c => `${crumbTypeLabel(c.type)} ${c.label || c.value}`).join(" → ");

    if (status) {
        status.innerHTML = `<span class="latency-cumulative-count"><strong>${lines.length}</strong> log(s) pour : <em>${escapeHtml(chainText)}</em> `
            + `(sur ${Number(data.totalScanned) || 0} examinés) — aucune limite d'affichage.</span>`
            + `<button type="button" class="secondary-btn small-btn latency-goto-took" id="latencyGotoTookBtn" title="Se positionner sur la ligne à l'origine de la lenteur, sans changer le filtre.">⏱ Aller à la lenteur (took)</button>`;
        document.getElementById("latencyGotoTookBtn")?.addEventListener("click", goToTookInCumulativeLogs);
    }

    if (!lines.length) {
        box.innerHTML = `<p class="muted-text">Aucun log ne correspond à cette combinaison.</p>`;
        return;
    }

    box.innerHTML = lines.map(l => {
        const lvl = String(l.level || "").toUpperCase();
        const lvlClass = lvl === "ERROR" ? "is-error" : ((lvl === "WARN" || lvl === "WARNING") ? "is-warn" : "");
        const meta = [l.timestamp, l.level, l.userName, l.sessionId, l.sourceFileName]
            .filter(Boolean)
            .map(x => escapeHtml(String(x)))
            .join(" · ");
        return `<div class="latency-log-line ${lvlClass}" data-log-id="${escapeHtml(String(l.id == null ? "" : l.id))}">
            <div class="latency-log-meta">${meta}</div>
            <div class="latency-log-msg">${escapeHtml(l.message || "")}</div>
        </div>`;
    }).join("");
}

// Bascule (sans changer le filtre courant) vers la ligne à l'origine de la lenteur
// dans la liste des logs cumulés, puis la surligne d'une couleur distincte.
function goToTookInCumulativeLogs() {
    const report = window.lastLatencyReport || {};
    const box = document.getElementById("latencyCumulativeLogs");
    const status = document.getElementById("latencyCumulativeStatus");
    if (!box) return;

    box.querySelectorAll(".latency-log-line.is-took-target")
        .forEach(el => el.classList.remove("is-took-target"));
    box.querySelectorAll(".latency-log-line.is-nav-match")
        .forEach(el => el.classList.remove("is-nav-match"));
    status?.querySelector(".latency-goto-note")?.remove();

    let target = null;

    // 1) Correspondance exacte par l'ID du log d'ancrage (le log qui mesure le took).
    if (report.anchorLogId != null) {
        target = box.querySelector(`[data-log-id="${CSS.escape(String(report.anchorLogId))}"]`);
    }
    // 2) Repli : première ligne contenant le terme du goulot (ex. "took [159658]").
    if (!target) {
        const term = String(report.bottleneckSearchTerm || "").trim().toLowerCase();
        if (term) {
            target = Array.from(box.querySelectorAll(".latency-log-line"))
                .find(el => el.textContent.toLowerCase().includes(term)) || null;
        }
    }
    // 3) Repli : ligne portant la durée mise en avant.
    if (!target && report.maxDurationMs != null) {
        const needle = `took [${report.maxDurationMs}]`.toLowerCase();
        target = Array.from(box.querySelectorAll(".latency-log-line"))
            .find(el => el.textContent.toLowerCase().includes(needle)) || null;
    }

    if (target) {
        target.classList.add("is-took-target");
        target.scrollIntoView({ behavior: "smooth", block: "center" });
    } else if (status) {
        const note = document.createElement("span");
        note.className = "latency-goto-note";
        note.textContent = " — la ligne du took n'est pas dans ce filtre (élargissez la sélection pour l'inclure).";
        status.appendChild(note);
    }
}

// Clic sur une barre de temps ou une règle : on se positionne sur la/les ligne(s)
// correspondante(s) DANS les logs de la navigation guidée (filtre courant inchangé).
function navigateLatencyToTerm(panel, term, sourceEl) {
    const cleaned = (term == null ? "" : String(term)).trim();
    if (!cleaned) {
        return;
    }

    panel.querySelectorAll("[data-latency-nav].is-active-nav")
        .forEach(n => n.classList.remove("is-active-nav"));
    if (sourceEl) {
        sourceEl.classList.add("is-active-nav");
    }

    const status = document.getElementById("latencyNavStatus");
    const box = document.getElementById("latencyCumulativeLogs");

    if (!box || !box.querySelector(".latency-log-line")) {
        if (status) {
            status.hidden = false;
            status.className = "latency-nav-status is-empty";
            status.innerHTML = `Démarrez d'abord la navigation guidée (cliquez un déclencheur ci-dessus), puis cliquez « ${escapeHtml(cleaned)} » pour vous y positionner.`;
        }
        return;
    }

    const matches = highlightTermInCumulativeLogs(cleaned);
    if (status) {
        status.hidden = false;
        if (matches > 0) {
            status.className = "latency-nav-status is-found";
            status.innerHTML = `<strong>${matches}</strong> occurrence(s) de « ${escapeHtml(cleaned)} » surlignée(s) dans les logs de la navigation.`;
        } else {
            status.className = "latency-nav-status is-empty";
            status.innerHTML = `« ${escapeHtml(cleaned)} » n'apparaît pas dans les logs du filtre courant (élargissez la sélection).`;
        }
    }
}

// Surligne (en bleu) toutes les lignes des logs cumulés contenant le terme et défile vers la première.
function highlightTermInCumulativeLogs(term) {
    const box = document.getElementById("latencyCumulativeLogs");
    if (!box) return 0;
    box.querySelectorAll(".latency-log-line.is-nav-match").forEach(el => el.classList.remove("is-nav-match"));
    box.querySelectorAll(".latency-log-line.is-took-target").forEach(el => el.classList.remove("is-took-target"));
    const t = String(term || "").toLowerCase();
    let first = null;
    let count = 0;
    box.querySelectorAll(".latency-log-line").forEach(el => {
        if (el.textContent.toLowerCase().includes(t)) {
            el.classList.add("is-nav-match");
            count++;
            if (!first) first = el;
        }
    });
    if (first) {
        first.scrollIntoView({ behavior: "smooth", block: "center" });
    }
    return count;
}

function updateLatencyNavStatus(term, count) {
    const status = document.getElementById("latencyNavStatus");
    if (!status) return;
    status.hidden = false;
    if (count > 0) {
        const preview = latencyMatchingPreview(term, 3);
        const previewHtml = preview.length
            ? `<div class="latency-nav-preview">${preview.map(l =>
                `<div class="latency-nav-preview-line">${highlightLatencyTerm(l, term)}</div>`).join("")}</div>`
            : "";
        status.className = "latency-nav-status is-found";
        status.innerHTML = `<div class="latency-nav-line">Exploration : <strong>${escapeHtml(term)}</strong> — `
            + `<strong>${count}</strong> occurrence(s) dans les logs réels :</div>`
            + previewHtml
            + `<div class="latency-nav-hint-inline">Détail complet et navigation ◀ Préc. / Suiv. ▶ dans la chronologie ci-dessous.</div>`;
    } else {
        status.className = "latency-nav-status is-empty";
        status.innerHTML = `Exploration : <strong>${escapeHtml(term)}</strong> — aucune occurrence dans la fenêtre analysée.`;
    }
}

// Renvoie les premières lignes RÉELLES de la chronologie contenant le terme (preuve directe).
function latencyMatchingPreview(term, max) {
    const text = window.lastChronologicalExplanation || "";
    const t = String(term || "").toLowerCase();
    if (!text || !t) return [];
    const out = [];
    for (const line of text.split("\n")) {
        const trimmed = line.trim();
        if (trimmed && trimmed.toLowerCase().includes(t)) {
            out.push(trimmed.length > 240 ? trimmed.slice(0, 237) + "..." : trimmed);
            if (out.length >= max) break;
        }
    }
    return out;
}

function highlightLatencyTerm(line, term) {
    const safe = escapeHtml(line);
    const t = String(term || "");
    if (!t) return safe;
    try {
        const re = new RegExp("(" + escapeRegex(t) + ")", "ig");
        return safe.replace(re, "<mark>$1</mark>");
    } catch {
        return safe;
    }
}

// Pointe automatiquement la chronologie sur la ligne contenant le « took » du goulot,
// pour que l'utilisateur tombe directement sur le log responsable de la lenteur.
function pointChronologyToBottleneck(report) {
    const term = report && report.bottleneckSearchTerm;
    if (!term || typeof applyChronologicalContextSearch !== "function") {
        return;
    }
    const input = document.getElementById("chronoContextSearch");
    if (input) {
        input.value = term;
    }
    setTimeout(() => applyChronologicalContextSearch(term, { resetIndex: true, scrollToMatch: true }), 80);
}

function clearLatencyOriginPanel() {
    const home = document.getElementById("latencyOriginPanel");
    const imp = document.getElementById("latencyOriginPanelImport");
    if (home) home.innerHTML = "";
    if (imp) imp.innerHTML = "";
}

function extractUuidFromIncident(inc) {
    const sources = [
        ...(inc?.evidenceMessages || []),
        inc?.businessKey,
        inc?.correlationId,
        inc?.message,
        inc?.rawLog
    ].filter(Boolean);
    for (const text of sources) {
        const labeled = String(text).match(/\buuid\s*\[\s*([^\]]+?)\s*]/i);
        if (labeled) return labeled[1].trim();
        // Forme WORKS : [3541956776772717142] doAction for …
        const leading = String(text).match(/(?:^|\|)\s*\[(-?\d{10,20})\]\s+(?=[A-Za-z_])/);
        if (leading) return leading[1].trim();
    }
    return null;
}

async function openIncidentLatencyOrigin(index) {
    const inc = window.lastImportIncidents?.[index];
    if (!inc?.importId) {
        showMessage("Incident introuvable.", "error");
        return;
    }

    const panel = getLatencyOriginPanel();
    if (panel) {
        panel.innerHTML = `<div class="analysis-block"><p class="muted-text">Analyse de l'origine de la lenteur…</p></div>`;
    }

    showMessage("Investigation de la latence en cours…", "info");

    try {
        const payload = {
            importId: inc.importId,
            sessionId: inc.sessionId || null,
            uuid: extractUuidFromIncident(inc),
            filterCode: (inc._latencyKind === "filter" && inc.filterCode) ? inc.filterCode : (inc.filterCode || null),
            processName: inc.processName || null
        };

        const res = await authFetch("/assistant/latency-origin", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        const data = await safeJson(res);
        if (!res.ok) {
            const msg = data?.message || data?.error || "Erreur analyse latence";
            throw new Error(msg);
        }

        renderLatencyOriginPanel(data);
        showMessage("Origine de la lenteur identifiée.", "success");
    } catch (e) {
        if (panel) {
            panel.innerHTML = `<div class="analysis-block"><p class="error-text">${escapeHtml(e.message || "Erreur")}</p></div>`;
        }
        showMessage(e.message || "Erreur origine latence.", "error");
    }
}

function openIncidentRelatedLogs(index) {
    const inc = window.lastImportIncidents?.[index];
    const process = inc?.processName;
    if (!process) return;

    switchMainTab("advanced", { silent: true });
    showMessage(`Chargement logs liés process ${process}…`, "info");
    prepareIncidentContext(inc);
    setInputValue("logFilterProcessName", process);

    if (typeof loadRelatedLogsForItem === "function") {
        const dateFrom = inc.firstTimestamp ? String(inc.firstTimestamp).slice(0, 19) : null;
        const dateTo = inc.lastTimestamp ? String(inc.lastTimestamp).slice(0, 19) : null;
        loadRelatedLogsForItem("PROCESS", process, dateFrom, dateTo);
    }
}

function openIncidentAssistantChat(index) {
    const inc = window.lastImportIncidents?.[index];
    if (!inc) return;

    switchMainTab("advanced", { silent: true });
    showMessage("Ouverture assistant IA pour l’incident…", "info");
    prepareIncidentContext(inc);

    const groupBy = inc.userName ? "userName" : "sessionId";
    const groupKey = inc.userName || inc.sessionId;
    if (!groupKey) {
        showMessage("Utilisateur ou session requis pour le chat.", "error");
        return;
    }

    if (typeof openExpertAssistantChat === "function") {
        openExpertAssistantChat(groupBy, groupKey);
    }
}

function applyScenarioPreset() {
    const key = document.getElementById("scenarioPresetSelect")?.value;
    if (!key || !SCENARIO_PRESETS[key]) return;

    const preset = SCENARIO_PRESETS[key];
    if (preset.groupBy) document.getElementById("logGroupBy").value = preset.groupBy;
    if (preset.errorOnly) {
        const cb = document.getElementById("logFilterErrorOnly");
        if (cb) cb.checked = true;
    }
    if (preset.processName) setInputValue("logFilterProcessName", preset.processName);
    if (preset.crossSearchType) {
        const st = document.getElementById("crossSearchType");
        if (st) st.value = preset.crossSearchType;
        updateCrossSearchBusinessFieldVisibility?.();
    }
    if (preset.crossSearchValue) setInputValue("crossSearchValue", preset.crossSearchValue);

    const hint = document.getElementById("scenarioHint");
    if (hint) hint.textContent = preset.hint || "";

    showMessage(`Scénario « ${preset.label} » appliqué.`, "info");
}

async function loadSessionDiagnosticSheet() {
    const sessionId = document.getElementById("diagnosticSessionId")?.value?.trim();
    if (!window.activeImportId) {
        showMessage("Sélectionnez un import actif.", "error");
        return;
    }
    if (!sessionId) {
        showMessage("Saisissez la session technique (timestamp WORKS, 2e colonne du log).", "error");
        return;
    }

    const container = document.getElementById("diagnosticSheet");
    if (!container) return;

    try {
        showMessage("Construction de la fiche diagnostic...", "info");
        container.innerHTML = `<p class="muted-text">Analyse en cours...</p>`;

        const filters = typeof getLogFilters === "function" ? getLogFilters(false) : {};
        const payload = {
            importIds: [window.activeImportId],
            groupBy: "sessionId",
            groupKey: sessionId,
            dateFrom: filters.dateFrom ? toBackendDateTime(filters.dateFrom) : null,
            dateTo: filters.dateTo ? toBackendDateTime(filters.dateTo) : null
        };

        const response = await authFetch("/assistant/session-diagnostic", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        const data = await safeJson(response);
        if (!response.ok) throw new Error(data?.message || "Erreur fiche diagnostic");

        window.currentDiagnosticSheet = data;
        window.currentAnalyzedGroupBy = "sessionId";
        window.currentAnalyzedGroupKey = sessionId;
        setInputValue("logFilterSessionId", sessionId);

        renderDiagnosticSheet(data);
        showMessage("Fiche diagnostic prête.", "success");
    } catch (e) {
        container.innerHTML = `<p class="error-text">${escapeHtml(e.message)}</p>`;
        showMessage(e.message || "Erreur fiche diagnostic.", "error");
    }
}

function renderDiagnosticSheet(data) {
    const container = document.getElementById("diagnosticSheet");
    if (!container) return;

    const ga = data.groupAnalysis || {};
    const quality = data.importQuality || {};
    const audit = data.extractionAudit || {};
    const incidents = Array.isArray(data.relatedIncidents) ? data.relatedIncidents : [];

    container.innerHTML = `
        <div class="diagnostic-sheet">
            <div class="analysis-kpis">
                <span class="log-pill">Session ${escapeHtml(data.groupKey)}</span>
                <span class="log-pill">${escapeHtml(ga.totalLogs ?? 0)} logs</span>
                <span class="log-pill error-pill">${escapeHtml(ga.errorCount ?? 0)} err.</span>
                <span class="log-pill warn-pill">${escapeHtml(ga.zeroResultCount ?? 0)} × 0 row</span>
                <span class="log-pill">Graphe ~${escapeHtml(audit.workflowCoveragePercent ?? 0)}%</span>
                <span class="log-pill">Catalogue ~${escapeHtml(audit.catalogCoveragePercent ?? 0)}%</span>
            </div>

            ${formatExtractionAuditHtml(audit, true)}

            <div class="analysis-block highlight-block">
                <h3>Synthèse employé</h3>
                <p>${escapeHtml(data.employeeSummary || "")}</p>
            </div>

            <div class="diagnostic-actions actions wrap">
                <button type="button" id="exportDiagnosticBtn">Télécharger rapport (HTML)</button>
                <button type="button" class="secondary-btn" id="printDiagnosticBtn">Imprimer / PDF</button>
                <button type="button" class="secondary-btn" id="copyAiContextBtn">Copier contexte IA</button>
                <button type="button" class="secondary-btn" id="assistantGuidedReportBtn">Rapport guidé (sans IA)</button>
                <button type="button" class="secondary-btn" id="assistantFullAnalysisBtn">Analyse IA complète</button>
                <button type="button" class="secondary-btn" id="diagnosticGraphBtn">Voir le graphe</button>
                <button type="button" class="secondary-btn" onclick="analyzeLogGroup('sessionId', '${escapeJs(data.groupKey)}')">Analyse technique</button>
            </div>

            <div class="analysis-block">
                <h3>Conclusion &amp; recommandation</h3>
                <p><strong>Conclusion :</strong> ${escapeHtml(ga.conclusion || "—")}</p>
                <p><strong>Recommandation :</strong> ${escapeHtml(ga.recommendation || "—")}</p>
            </div>

            <div class="analysis-block">
                <h3>Histoire</h3>
                <p>${escapeHtml(ga.narrative || "—")}</p>
            </div>

            ${typeof renderTimelineSection === "function" ? renderTimelineSection(ga.timeline || []) : ""}

            ${incidents.length ? `
                <div class="analysis-block">
                    <h3>Incidents liés (${incidents.length})</h3>
                    ${incidents.map(inc => `
                        <p><strong>${escapeHtml(inc.title)}</strong> — ${escapeHtml(inc.probableCause || inc.explanation || "")}</p>
                    `).join("")}
                </div>
            ` : ""}

            <div class="analysis-block muted-block">
                <h3>Qualité import</h3>
                <p>Fichier : ${escapeHtml(quality.fileName || "—")} — ${escapeHtml(quality.parsedLines)}/${escapeHtml(quality.totalLines)} lignes parsées, ${escapeHtml(quality.failedLines)} échec(s)</p>
                <p class="muted-text">${escapeHtml(audit.note || "")}</p>
            </div>

            <details class="analysis-block">
                <summary>Contexte IA (aperçu)</summary>
                <pre class="ai-context-preview">${escapeHtml((data.aiContext?.context || "").substring(0, 3000))}${(data.aiContext?.context || "").length > 3000 ? "\n…" : ""}</pre>
            </details>

            <div class="analysis-block" id="assistantFullAnalysisBlock">
                <h3>Analyse IA complète</h3>
                <p class="muted-text">Génère un rapport structuré à partir de la fiche diagnostic (résumé, chronologie, causes probables, actions, preuves).</p>
                <div id="assistantFullAnalysisResult" class="chat-content muted-block">Cliquez sur <strong>Analyse IA complète</strong> pour générer le rapport.</div>
            </div>
        </div>
    `;

    container.scrollIntoView({ behavior: "smooth", block: "start" });
    showAssistantChatForSession(data);
}

async function loadAssistantFullAnalysis(event, forceLocal = false) {
    event?.preventDefault?.();
    const sheet = window.currentDiagnosticSheet;
    const out = document.getElementById("assistantFullAnalysisResult");

    if (!window.activeImportId) {
        showMessage("Sélectionnez un import actif.", "error");
        return;
    }
    if (!sheet?.groupKey) {
        showMessage("Générez d’abord une fiche diagnostic.", "error");
        return;
    }
    if (out) {
        out.innerHTML = forceLocal ? "Génération du rapport guidé…" : "Réflexion en cours…";
    }

    try {
        const filters = typeof getLogFilters === "function" ? getLogFilters(false) : {};
        const payload = {
            importIds: [Number(window.activeImportId)],
            groupBy: sheet.groupBy || "sessionId",
            groupKey: sheet.groupKey,
            dateFrom: filters.dateFrom ? toBackendDateTime(filters.dateFrom) : null,
            dateTo: filters.dateTo ? toBackendDateTime(filters.dateTo) : null,
            forceLocal: forceLocal === true
        };

        const res = await authFetch("/assistant/full-analysis", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        const data = await safeJson(res);
        if (!res.ok) throw new Error(data?.message || "Erreur analyse IA complète");

        const modeLabel = data.mode === "OPENAI" ? "IA" : "Rapport guidé (sans IA)";
        const meta = modeLabel + (data.hint ? " — " + data.hint : "");
        const content = (data.reportMarkdown || data.report || data.reply || "—").trim();

        if (out) {
            out.innerHTML = `<div class="chat-meta">${escapeHtml(meta)}</div><div class="chat-content">${formatChatMarkdown(content)}</div>`;
        }
        showMessage(forceLocal ? "Rapport guidé généré." : "Analyse IA complète générée.", "success");
    } catch (e) {
        if (out) out.innerHTML = `<span class="error-text">${escapeHtml(e.message || "Erreur")}</span>`;
        showMessage(e.message || "Erreur analyse IA complète.", "error");
    }
}

function showAssistantChatForSession(data) {
    const section = document.getElementById("assistantChatSection");
    const hint = document.getElementById("assistantChatHint");
    const messages = document.getElementById("assistantChatMessages");
    if (!section || !data?.groupKey) return;

    window.assistantChatHistory = [];
    section.classList.remove("hidden");
    if (hint) {
        hint.textContent = "Session " + data.groupKey + " — posez une question en langage simple (erreurs, lenteur, 0 row, règles…).";
    }
    if (messages) {
        messages.innerHTML = `<div class="chat-bubble chat-assistant">Bonjour. J’ai chargé la fiche de la session <strong>${escapeHtml(data.groupKey)}</strong>. Posez une question ou utilisez une suggestion ci-dessous.</div>`;
    }
    renderChatSuggestions("assistantChatSuggestions", "assistantChatInput", sendAssistantChatMessage);
}

async function sendAssistantChatMessage(event) {
    event?.preventDefault?.();
    const input = document.getElementById("assistantChatInput");
    const messagesEl = document.getElementById("assistantChatMessages");
    const text = input?.value?.trim();
    const sheet = window.currentDiagnosticSheet;

    if (!text) {
        showMessage("Écrivez une question.", "error");
        return;
    }
    if (!sheet?.groupKey) {
        showMessage("Générez d’abord une fiche diagnostic.", "error");
        return;
    }

    appendChatBubble(messagesEl, "user", text);
    input.value = "";
    appendChatBubble(messagesEl, "assistant", "Réflexion en cours…");

    try {
        const filters = typeof getLogFilters === "function" ? getLogFilters(false) : {};
        const importIds = filters.importIds?.length
            ? filters.importIds
            : (window.activeImportId ? [Number(window.activeImportId)] : []);
        const body = {
            importIds: importIds,
            groupBy: sheet.groupBy || "sessionId",
            groupKey: sheet.groupKey,
            userMessage: text,
            history: window.assistantChatHistory.slice(-6),
            dateFrom: filters.dateFrom ? toBackendDateTime(filters.dateFrom) : null,
            dateTo: filters.dateTo ? toBackendDateTime(filters.dateTo) : null
        };
        const res = await authFetch("/assistant/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
        const data = await safeJson(res);
        if (!res.ok) throw new Error(data?.message || "Erreur assistant");

        messagesEl.removeChild(messagesEl.lastChild);
        const modeLabel = data.mode === "OPENAI" ? "IA" : "Mode guidé";
        appendChatBubble(messagesEl, "assistant", data.reply || "—", modeLabel + (data.hint ? " — " + data.hint : ""));

        window.assistantChatHistory.push({ role: "user", content: text });
        window.assistantChatHistory.push({ role: "assistant", content: data.reply });
    } catch (e) {
        if (messagesEl?.lastChild) messagesEl.removeChild(messagesEl.lastChild);
        appendChatBubble(messagesEl, "assistant", "Erreur : " + (e.message || "inconnue"));
    }
}

function appendChatBubble(container, role, content, meta) {
    if (!container) return;
    const div = document.createElement("div");
    div.className = "chat-bubble chat-" + role;
    const metaHtml = meta ? `<span class="chat-meta">${escapeHtml(meta)}</span>` : "";
    div.innerHTML = metaHtml + `<div class="chat-content">${formatChatMarkdown(content)}</div>`;
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
}

function formatChatMarkdown(text) {
    if (!text) return "";
    return escapeHtml(text).replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>").replace(/\n/g, "<br>");
}

function printDiagnosticReport() {
    const data = window.currentDiagnosticSheet;
    if (!data) {
        showMessage("Générez d’abord une fiche diagnostic.", "error");
        return;
    }
    const w = window.open("", "_blank");
    if (!w) {
        showMessage("Autorisez les pop-ups pour imprimer.", "error");
        return;
    }
    const ga = data.groupAnalysis || {};
    w.document.write(`<!DOCTYPE html><html lang="fr"><head><meta charset="UTF-8"/><title>Fiche ${escapeHtml(data.groupKey)}</title>
        <style>body{font-family:Segoe UI,Arial,sans-serif;margin:20px} h1{color:#1a3a8a}</style></head><body>
        <h1>Fiche incident — session ${escapeHtml(data.groupKey)}</h1>
        <p>${escapeHtml(data.employeeSummary || "")}</p>
        <p><strong>Conclusion :</strong> ${escapeHtml(ga.conclusion || "")}</p>
        <p><strong>Recommandation :</strong> ${escapeHtml(ga.recommendation || "")}</p>
        </body></html>`);
    w.document.close();
    w.focus();
    w.print();
}

function openGraphFromDiagnostic() {
    const data = window.currentDiagnosticSheet;
    if (!data?.groupKey) return;
    switchMainTab("advanced", { silent: true });
    showMessage("Ouverture graphe depuis la fiche diagnostic…", "info");
    loadWorkflowGraph("sessionId", data.groupKey);
}

async function copyAiContextToClipboard() {
    const ctx = window.currentDiagnosticSheet?.aiContext?.context;
    if (!ctx) {
        showMessage("Aucun contexte IA — lancez d’abord la fiche diagnostic.", "error");
        return;
    }
    try {
        await navigator.clipboard.writeText(ctx);
        showMessage("Contexte IA copié dans le presse-papier.", "success");
    } catch (e) {
        showMessage("Copie impossible : " + e.message, "error");
    }
}

function exportDiagnosticReport() {
    const data = window.currentDiagnosticSheet;
    if (!data) {
        showMessage("Aucune fiche à exporter.", "error");
        return;
    }

    const ga = data.groupAnalysis || {};
    const html = `<!DOCTYPE html><html lang="fr"><head><meta charset="UTF-8"/>
        <title>Fiche incident — session ${escapeHtml(data.groupKey || "")}</title>
        <style>body{font-family:Arial,sans-serif;margin:24px;max-width:900px}
        h1{color:#2343a3} .kpi{background:#eef2ff;padding:8px 12px;border-radius:8px;margin:4px;display:inline-block}
        pre{white-space:pre-wrap;background:#f5f5f5;padding:12px}</style></head><body>
        <h1>Fiche diagnostic Log Analyzer</h1>
        <p><span class="kpi">Session ${escapeHtml(data.groupKey)}</span>
        <span class="kpi">${ga.totalLogs} logs</span>
        <span class="kpi">${ga.errorCount} erreurs</span></p>
        <h2>Synthèse</h2><p>${escapeHtml(data.employeeSummary || "").replace(/\n/g, "<br>")}</p>
        <h2>Conclusion</h2><p>${escapeHtml(ga.conclusion || "")}</p>
        <h2>Recommandation</h2><p>${escapeHtml(ga.recommendation || "")}</p>
        <h2>Histoire</h2><p>${escapeHtml(ga.narrative || "")}</p>
        <h2>Timeline</h2><ul>${(ga.timeline || []).map(t => `<li>${escapeHtml(t)}</li>`).join("")}</ul>
        <p><em>Généré le ${new Date().toLocaleString("fr-FR")}</em></p>
        </body></html>`;

    const blob = new Blob([html], { type: "text/html;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `fiche-incident-session-${data.groupKey || "export"}.html`;
    a.click();
    URL.revokeObjectURL(url);
    showMessage("Rapport HTML téléchargé.", "success");
}

async function compareTwoImports() {
    const a = document.getElementById("compareImportIdA")?.value?.trim()
        || String(window.activeImportId || "");
    const b = document.getElementById("compareImportIdB")?.value?.trim();

    if (!a || !b) {
        showMessage("Renseignez les deux Import ID (ex. avant/après MEP).", "error");
        return;
    }

    const container = document.getElementById("importCompareResult");
    if (!container) return;

    try {
        showMessage("Comparaison des imports...", "info");
        const response = await authFetch("/explorer/compare-imports", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ importIdA: Number(a), importIdB: Number(b) })
        });
        const data = await safeJson(response);
        if (!response.ok) throw new Error(data?.message || "Erreur comparaison");

        const sa = data.summaryA || {};
        const sb = data.summaryB || {};
        const ma = data.metricsA || {};
        const mb = data.metricsB || {};

        const renderUserPills = (users) => (users || []).slice(0, 5).map(u =>
            `<span class="log-pill">${escapeHtml(u.value)} (${escapeHtml(u.count)})</span>`
        ).join(" ") || "<span class='muted-text'>—</span>";

        const userDeltaRows = (data.userDeltas || []).slice(0, 10).map(u => `
            <tr>
                <td>${escapeHtml(u.userName)}</td>
                <td>${escapeHtml(u.errorsA)} → ${escapeHtml(u.errorsB)}</td>
                <td class="${u.errorDelta > 0 ? "error-text" : ""}">${u.errorDelta > 0 ? "+" : ""}${escapeHtml(u.errorDelta)}</td>
                <td>${escapeHtml(u.logsA)} → ${escapeHtml(u.logsB)}</td>
            </tr>
        `).join("");

        container.innerHTML = `
            <div class="analysis-block compare-summary-block">
                <p>${escapeHtml(data.summary || "")}</p>
                <p><strong>${escapeHtml(data.recommendation || "")}</strong></p>
            </div>
            <div class="analysis-block compare-regressions-block">
                <h4>Signaux de régression</h4>
                <ul class="compare-regression-list">
                    ${(data.regressions || []).map(r => `<li>${escapeHtml(r)}</li>`).join("")}
                </ul>
            </div>
            <div class="compare-snapshots">
                <div class="compare-snapshot">
                    <h4>Import A #${escapeHtml(sa.importId)} (avant)</h4>
                    <p>${escapeHtml(sa.fileName)}</p>
                    <p>${escapeHtml(sa.totalLogs)} logs · ${escapeHtml(sa.totalErrors)} err. (${escapeHtml(sa.errorRate)}%)</p>
                    <p>${escapeHtml(ma.distinctUsers)} utilisateur(s) · ${escapeHtml(ma.slowLogCount)} lenteur(s) · max ${escapeHtml(ma.maxDurationMs)} ms</p>
                    <div class="compare-user-pills">${renderUserPills(ma.topUsersByErrors)}</div>
                </div>
                <div class="compare-snapshot">
                    <h4>Import B #${escapeHtml(sb.importId)} (après)</h4>
                    <p>${escapeHtml(sb.fileName)}</p>
                    <p>${escapeHtml(sb.totalLogs)} logs · ${escapeHtml(sb.totalErrors)} err. (${escapeHtml(sb.errorRate)}%)</p>
                    <p>${escapeHtml(mb.distinctUsers)} utilisateur(s) · ${escapeHtml(mb.slowLogCount)} lenteur(s) · max ${escapeHtml(mb.maxDurationMs)} ms</p>
                    <div class="compare-user-pills">${renderUserPills(mb.topUsersByErrors)}</div>
                </div>
            </div>
            <div class="analysis-block">
                <h4>Écarts détaillés</h4>
                <ul>${(data.differences || []).map(d => `<li>${escapeHtml(d)}</li>`).join("")}</ul>
            </div>
            ${userDeltaRows ? `
            <div class="analysis-block">
                <h4>Utilisateurs — erreurs A → B</h4>
                <table class="compare-user-table">
                    <thead><tr><th>Utilisateur</th><th>Erreurs</th><th>Δ</th><th>Logs</th></tr></thead>
                    <tbody>${userDeltaRows}</tbody>
                </table>
            </div>` : ""}
        `;
        showMessage("Comparaison terminée.", "success");
    } catch (e) {
        showMessage(e.message || "Erreur comparaison imports.", "error");
    }
}

async function loadArchiveAdminPanel() {
    const panel = document.getElementById("archiveAdminPanel");
    if (!panel) return;

    try {
        const res = await authFetch("/imports/archive/status");
        const data = await safeJson(res);
        if (!res.ok) throw new Error(data?.message || "Erreur statut archive");

        panel.classList.remove("hidden");
        panel.innerHTML = `
            <div class="analysis-block archive-admin-block">
                <div class="card-header-inline">
                    <h3>Archivage des imports inactifs</h3>
                    <button type="button" class="secondary-btn small-btn" id="refreshArchiveStatusBtn">Rafraîchir</button>
                </div>
                <p class="section-hint">
                    Les imports non utilisés depuis <strong>${escapeHtml(data.inactiveDays)} jours</strong>
                    sont copiés vers la base archive puis supprimés de la base active.
                    Purge archive après <strong>${escapeHtml(data.purgeDays)} jours</strong>.
                </p>
                <div class="analysis-kpis">
                    <span class="log-pill ${data.enabled ? "success-pill" : "warn-pill"}">${data.enabled ? "Activé" : "Désactivé"}</span>
                    <span class="log-pill">Cron : ${escapeHtml(data.cron)}</span>
                    <span class="log-pill">Lot : ${escapeHtml(data.batchSize)} import(s)/run</span>
                </div>
                <p class="muted-text">Base archive : <code>${escapeHtml(data.archiveDatabaseUrl || "—")}</code></p>
                ${data.lastRunAt ? `<p class="muted-text">Dernière exécution : ${escapeHtml(String(data.lastRunAt).replace("T", " ").slice(0, 19))} — ${escapeHtml(data.lastRunMessage || "")}</p>` : `<p class="muted-text">Aucune exécution encore.</p>`}
                ${!data.enabled ? `<p class="section-hint">Pour activer : <code>app.archive.enabled=true</code> dans application.properties et créer la base <code>logs_archive_db</code>.</p>` : ""}
                <div class="actions wrap">
                    <button type="button" id="runArchiveNowBtn" ${data.enabled ? "" : "disabled"}>Lancer archivage maintenant</button>
                </div>
            </div>
        `;
    } catch (e) {
        panel.classList.add("hidden");
    }
}

async function runArchiveMaintenanceNow() {
    if (!confirm("Lancer l’archivage et la purge des imports inactifs maintenant ?")) {
        return;
    }
    try {
        showMessage("Archivage en cours…", "info");
        const res = await authFetch("/imports/archive/run", { method: "POST" });
        const data = await safeJson(res);
        if (!res.ok) throw new Error(data?.message || "Erreur archivage");
        showMessage(data.message || "Archivage terminé.", "success");
        await loadArchiveAdminPanel();
        await refreshImports?.();
    } catch (e) {
        showMessage(e.message || "Erreur archivage.", "error");
    }
}

function afterEmployeeLogin() {
    initEmployeeUX();
    showEmployeeChrome();
    switchMainTab("home", { silent: true });
}

window.afterEmployeeLogin = afterEmployeeLogin;
window.loadHomeDashboard = loadHomeDashboard;
window.showEmployeeChrome = showEmployeeChrome;

function afterEmployeeUpload(importId, fileName) {
    if (importId != null) {
        setActiveImportId(importId, `#${importId} — ${fileName || ""}`);
        switchMainTab("import", { silent: true });
        showMessage(`Import #${importId} prêt — onglet Importer.`, "info");
        const hint = document.getElementById("uploadQualityHint");
        if (hint) {
            loadUploadQualityHint(importId, hint);
        }
        const anomalies = document.getElementById("importAnomaliesPanel");
        if (anomalies) {
            // Ne pas lancer l'analyse latences automatiquement (évite OOM juste après un gros import).
            anomalies.innerHTML = `
                <div class="analysis-block">
                    <p class="muted-text">Import enregistré. Lancez l’analyse des latences quand vous êtes prêt.</p>
                    <button type="button" class="primary-btn" onclick="loadImportAnomalies(${Number(importId)}, document.getElementById('importAnomaliesPanel'))">
                        Analyser les latences
                    </button>
                </div>`;
        }
    }
}

function formatExtractionAuditHtml(audit, compact) {
    if (!audit || audit.sampledLogs == null) return "";
    const unknown = audit.unknownSamples || [];
    const unknownList = unknown.length
        ? `<details class="audit-unknown"><summary>${escapeHtml(audit.catalogUnknown ?? unknown.length)} structure(s) inconnue(s)</summary><ul>${unknown.map(s => `<li><code>${escapeHtml(s)}</code></li>`).join("")}</ul></details>`
        : "";
    const hits = audit.familyHits && !compact
        ? `<details><summary>Top familles reconnues</summary><ul>${Object.entries(audit.familyHits).slice(0, 8).map(([k, v]) => `<li>${escapeHtml(k)} : ${escapeHtml(v)}</li>`).join("")}</ul></details>`
        : "";
    return `
        <p>Échantillon ${escapeHtml(audit.sampledLogs)} / ${escapeHtml(audit.totalLogs)} lignes —
        catalogue <strong>${escapeHtml(audit.catalogCoveragePercent ?? 0)}%</strong>
        · familles graphe <strong>${escapeHtml(audit.graphFamilyCoveragePercent ?? 0)}%</strong>
        (${escapeHtml(audit.catalogMatched ?? 0)} reconnues, ${escapeHtml(audit.catalogFamilyCount ?? 0)} familles) —
        graphe workflow ~${escapeHtml(audit.workflowCoveragePercent ?? 0)}%</p>
        <p class="muted-text">${escapeHtml(audit.note || "")}</p>
        ${unknownList}
        ${hits}
    `;
}

async function loadUploadQualityHint(importId, container) {
    try {
        const [summaryRes, auditRes] = await Promise.all([
            authFetch(`/analysis/import/${importId}/summary`),
            authFetch(`/explorer/import-quality/${importId}`)
        ]);
        const summary = await safeJson(summaryRes);
        const audit = await safeJson(auditRes);
        if (!summaryRes.ok) return;

        container.innerHTML = `
            <div class="analysis-block success-block">
                <h3>Import #${escapeHtml(importId)} prêt</h3>
                <p>${escapeHtml(summary.parsedLines)} / ${escapeHtml(summary.totalLines)} lignes parsées — 
                ${escapeHtml(summary.totalLogs)} logs en base, ${escapeHtml(summary.totalErrors)} erreur(s).</p>
                ${auditRes.ok ? formatExtractionAuditHtml(audit, true) : ""}
                <p class="section-hint">Puis cliquez sur <strong>Analyser les latences</strong> ci-dessous (pas lancé auto pour éviter de saturer la mémoire).</p>
            </div>
        `;
    } catch (_) { /* ignore */ }
}

window.loadImportAnomalies = loadImportAnomalies;
window.openIncidentLatencyOrigin = openIncidentLatencyOrigin;
window.clearLatencyOriginPanel = clearLatencyOriginPanel;
window.openIncidentFilterSearch = openIncidentFilterSearch;
window.openIncidentSession = openIncidentSession;
window.openIncidentExplorer = openIncidentExplorer;
window.openIncidentUserGraph = openIncidentUserGraph;
window.openIncidentRelatedLogs = openIncidentRelatedLogs;
window.openIncidentAssistantChat = openIncidentAssistantChat;
window.filterImportAnomalies = filterImportAnomalies;
window.onLatencyFilterChange = onLatencyFilterChange;
window.setLatencyViewMode = setLatencyViewMode;
window.loadArchiveAdminPanel = loadArchiveAdminPanel;
window.runArchiveMaintenanceNow = runArchiveMaintenanceNow;
window.afterEmployeeUpload = afterEmployeeUpload;
window.startRagIndexing = startRagIndexing;
window.askRagQuestion = askRagQuestion;

// ─── RAG : indexation vectorielle + question ancrée ─────────────────────────
let ragPollTimer = null;

async function startRagIndexing(reindex) {
    if (!window.activeImportId) {
        showMessage("Choisissez d’abord un import actif.", "error");
        return;
    }
    const statusEl = document.getElementById("ragIndexStatus");
    const wrap = document.getElementById("ragProgressWrap");
    if (statusEl) statusEl.textContent = "Démarrage de l’indexation…";
    if (wrap) wrap.hidden = false;
    try {
        const q = reindex ? "?reindex=true" : "";
        const res = await authFetch(`/rag/index/${window.activeImportId}${q}`, { method: "POST" });
        const data = await safeJson(res);
        if (!res.ok) throw new Error(data?.message || data?.error || "Erreur indexation RAG");
        updateRagProgress(data);
        showMessage(reindex ? "Réindexation lancée." : "Indexation lancée.", "info");
        if (ragPollTimer) clearInterval(ragPollTimer);
        ragPollTimer = setInterval(() => pollRagStatus(), 1500);
    } catch (e) {
        if (statusEl) statusEl.textContent = e.message || "Erreur";
        showMessage(e.message || "Erreur indexation RAG.", "error");
    }
}

async function pollRagStatus() {
    if (!window.activeImportId) return;
    try {
        const res = await authFetch(`/rag/index/${window.activeImportId}/status`);
        const data = await safeJson(res);
        if (!res.ok) return;
        updateRagProgress(data);
        if (data.state === "DONE" || data.state === "ERROR" || data.state === "NONE") {
            if (ragPollTimer) { clearInterval(ragPollTimer); ragPollTimer = null; }
            if (data.state === "DONE") showMessage("Index IA prêt.", "success");
            if (data.state === "ERROR") showMessage(data.message || "Erreur d’indexation.", "error");
        }
    } catch (_) { /* ignore poll errors */ }
}

function updateRagProgress(data) {
    const statusEl = document.getElementById("ragIndexStatus");
    const fill = document.getElementById("ragProgressFill");
    const label = document.getElementById("ragProgressLabel");
    const wrap = document.getElementById("ragProgressWrap");
    if (!data) return;
    const pct = data.progressPercent != null ? data.progressPercent
        : (data.totalChunks > 0 ? Math.round(100 * (data.indexedChunks || 0) / data.totalChunks) : 0);
    if (statusEl) {
        const msg = data.message || "";
        statusEl.textContent = data.state === "DONE"
            ? `Indexé (${data.indexedChunks || 0} extraits). ${msg}`
            : data.state === "RUNNING"
                ? `Indexation… ${data.indexedChunks || 0}/${data.totalChunks || "?"} (${pct}%)`
                : data.state === "ERROR"
                    ? `Erreur : ${msg || "échec"}`
                    : (msg || `État : ${data.state || "NONE"}`);
    }
    if (wrap) wrap.hidden = data.state !== "RUNNING" && data.state !== "DONE";
    if (fill) fill.style.width = `${Math.min(100, Math.max(0, pct))}%`;
    if (label) label.textContent = data.state === "RUNNING" ? `${pct}%` : "";
}

async function askRagQuestion(event) {
    if (event?.preventDefault) event.preventDefault();
    if (!window.activeImportId) {
        showMessage("Choisissez d’abord un import actif.", "error");
        return;
    }
    const input = document.getElementById("ragAskInput");
    const question = (input?.value || "").trim();
    if (!question) {
        showMessage("Saisissez une question.", "error");
        return;
    }
    const answerEl = document.getElementById("ragAnswer");
    const sourcesEl = document.getElementById("ragSources");
    if (answerEl) {
        answerEl.hidden = false;
        answerEl.innerHTML = `<p class="muted-text">Recherche des logs pertinents + génération…</p>`;
    }
    if (sourcesEl) { sourcesEl.hidden = true; sourcesEl.innerHTML = ""; }

    try {
        const res = await authFetch("/rag/ask", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ importId: Number(window.activeImportId), question })
        });
        const data = await safeJson(res);
        if (!res.ok) throw new Error(data?.message || data?.error || "Erreur RAG");

        if (answerEl) {
            const mode = data.mode || "";
            const hint = data.hint ? `<div class="rag-hint muted-text">${escapeHtml(data.hint)}</div>` : "";
            const badge = mode === "LLM" ? "🧠 IA ancrée"
                : mode === "LOCAL" ? "📋 Mode local"
                : mode === "NO_MATCH" ? "❓ Aucun extrait"
                : mode;
            answerEl.innerHTML = `
                <div class="rag-answer-head"><span class="rag-mode-badge">${escapeHtml(badge)}</span></div>
                <div class="rag-answer-body">${typeof renderSimpleMarkdown === "function"
                    ? renderSimpleMarkdown(data.answer || "")
                    : escapeHtml(data.answer || "").replace(/\n/g, "<br>")}</div>
                ${hint}`;
        }

        const sources = data.sources || [];
        if (sourcesEl && sources.length) {
            sourcesEl.hidden = false;
            sourcesEl.innerHTML = `<h4>Sources (${sources.length})</h4>` + sources.map((s, i) => {
                const ref = escapeHtml(s.ref || `S${i + 1}`);
                const meta = [
                    s.processName, s.filterCode, s.userName,
                    s.maxDurationMs != null ? `${s.maxDurationMs} ms` : null
                ].filter(Boolean).map(escapeHtml).join(" · ");
                return `<details class="rag-source">
                    <summary><strong>[${ref}]</strong> ${meta || "extrait"}</summary>
                    <pre class="rag-source-content">${escapeHtml(s.content || "")}</pre>
                </details>`;
            }).join("");
        }
        showMessage("Réponse IA reçue.", "success");
    } catch (e) {
        if (answerEl) answerEl.innerHTML = `<p class="error-text">${escapeHtml(e.message || "Erreur")}</p>`;
        showMessage(e.message || "Erreur question RAG.", "error");
    }
}
