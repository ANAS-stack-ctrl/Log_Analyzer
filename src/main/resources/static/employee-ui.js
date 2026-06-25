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

    document.getElementById("scenarioPresetSelect")?.addEventListener("change", applyScenarioPreset);
    document.getElementById("assistantChatInput")?.addEventListener("keydown", (e) => {
        if (e.key === "Enter") sendAssistantChatMessage(e);
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
                    ${list.map(step => `
                        <tr class="${step.bottleneck ? "latency-bottleneck-row" : ""}">
                            <td>${escapeHtml(step.operationName || step.stepType || "")}</td>
                            <td><strong>${escapeHtml(formatLatencyDurationMs(step.durationMs))}</strong></td>
                            <td>${step.rowCount != null ? escapeHtml(step.rowCount) : "—"}</td>
                            <td>${step.memoryMo != null ? escapeHtml(step.memoryMo) + " Mo" : "—"}</td>
                            <td class="latency-step-detail">${escapeHtml(step.threadInfo || step.detail || "")}</td>
                        </tr>
                    `).join("")}
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

function renderLatencyOriginPanel(report) {
    const panel = getLatencyOriginPanel();
    if (!panel) return;

    const steps = report.timelineSteps || [];
    const chronoBlock = typeof renderChronologicalContextBlock === "function" && report.chronologicalText
        ? renderChronologicalContextBlock(report.chronologicalText, {
            title: "Chronologie de l'opération lente",
            placeholder: "Tous les termes requis, séparés par ; — ex: prepareSearchByRoot;took",
            showScrollSelected: false
        })
        : "";

    panel.innerHTML = `
        <div class="analysis-block latency-origin-card">
            <div class="latency-origin-header">
                <h3>Origine de la lenteur</h3>
                <button type="button" class="secondary-btn small-btn" onclick="clearLatencyOriginPanel()">Fermer</button>
            </div>
            <div class="analysis-kpis">
                <span class="log-pill">${escapeHtml(report.scopeDescription || "périmètre")}</span>
                <span class="log-pill">Confiance : ${escapeHtml(report.analysisConfidence || "—")}</span>
                <span class="log-pill warn-pill">Max : ${escapeHtml(formatLatencyDurationMs(report.maxDurationMs))}</span>
                <span class="log-pill muted-pill">${escapeHtml(report.logsInWindow || 0)} log(s) analysé(s)</span>
            </div>
            <div class="latency-origin-section latency-client-summary">
                <h4>Résumé (langage métier)</h4>
                <p class="latency-client-summary-text">${escapeHtml(report.clientSummary || report.primaryCause || "")}</p>
            </div>
            <div class="latency-origin-section">
                <h4>Cause principale</h4>
                <p class="latency-primary-cause">${escapeHtml(report.primaryCause || "")}</p>
            </div>
            <div class="latency-origin-section">
                <h4>Analyse détaillée</h4>
                <pre class="latency-narrative">${escapeHtml(report.narrativeSummary || "")}</pre>
            </div>
            <div class="latency-origin-section">
                <h4>Chaîne d'exécution</h4>
                <p>${escapeHtml(report.chainExplanation || "")}</p>
            </div>
            <div class="latency-origin-section">
                <h4>Étapes identifiées (${steps.length})</h4>
                ${renderLatencyTimelineSteps(steps)}
            </div>
            ${chronoBlock}
        </div>
    `;

    if (typeof bindChronologicalContextSearch === "function") {
        bindChronologicalContextSearch();
    }
    panel.scrollIntoView({ behavior: "smooth", block: "start" });
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
        inc?.correlationId
    ].filter(Boolean);
    for (const text of sources) {
        const match = String(text).match(/\buuid\s*\[\s*([^\]]+?)\s*]/i);
        if (match) return match[1].trim();
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
            loadImportAnomalies(importId, anomalies);
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
                <p class="section-hint">Les latences sont listées ci-dessous — explorez-les avant de regrouper les logs.</p>
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
