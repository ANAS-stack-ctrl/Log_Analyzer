let currentLogsPage = 0;
let currentLogsTotalPages = 1;

const apiFetch = (...args) => window.apiFetch(...args);

function currentUser() {
    return window.currentUser();
}

function prettyPrint(targetId, data) {
    document.getElementById(targetId).textContent = JSON.stringify(data, null, 2);
}

function setText(targetId, text) {
    document.getElementById(targetId).textContent = text;
}

function safe(value) {
    return value === null || value === undefined ? "" : value;
}

function safeArray(value) {
    return Array.isArray(value) ? value : [];
}

function buildLogsUrl(page = 0) {
    const params = new URLSearchParams();

    const fileName = document.getElementById("fileNameFilter")?.value.trim() || "";
    const importId = document.getElementById("importIdFilter")?.value.trim() || "";
    const level = document.getElementById("levelFilter")?.value.trim() || "";
    const eventType = document.getElementById("eventTypeFilter")?.value.trim() || "";
    const businessKey = document.getElementById("businessKeyFilter")?.value.trim() || "";
    const fieldName = document.getElementById("fieldNameFilter")?.value.trim() || "";
    const triageStatus = document.getElementById("triageStatusFilter")?.value.trim() || "";
    const assignedTo = document.getElementById("assignedToFilter")?.value.trim() || "";
    const errorOnly = document.getElementById("errorOnlyFilter")?.checked || false;
    const importantOnly = document.getElementById("importantOnlyFilter")?.checked || false;
    const onlyActive = document.getElementById("onlyActiveFilter")?.checked || false;
    const size = document.getElementById("pageSizeInput")?.value.trim() || "25";

    if (fileName) params.append("fileName", fileName);
    if (importId) params.append("importId", importId);
    if (level) params.append("level", level);
    if (eventType) params.append("eventType", eventType);
    if (businessKey) params.append("businessKey", businessKey);
    if (fieldName) params.append("fieldName", fieldName);
    if (triageStatus) params.append("triageStatus", triageStatus);
    if (assignedTo) params.append("assignedTo", assignedTo);
    if (errorOnly) params.append("error", "true");
    if (importantOnly) params.append("important", "true");
    if (onlyActive) params.append("onlyActive", "true");

    params.append("page", page);
    params.append("size", size);

    return `/logs?${params.toString()}`;
}

function getSeverityClass(value) {
    const v = safe(value).toUpperCase();
    if (v === "CRITICAL") return "critical";
    if (v === "HIGH") return "high";
    if (v === "MEDIUM") return "medium";
    if (v === "LOW") return "low";
    return "neutral";
}

function getConfidenceClass(value) {
    const v = safe(value).toUpperCase();
    if (v === "HIGH") return "confidence-high";
    if (v === "MEDIUM") return "confidence-medium";
    if (v === "LOW") return "confidence-low";
    return "neutral";
}

function getTriageStatusClass(value) {
    const v = safe(value).toUpperCase();
    if (v === "OPEN") return "triage-open";
    if (v === "IN_PROGRESS") return "triage-in-progress";
    if (v === "RESOLVED") return "triage-resolved";
    if (v === "IGNORED") return "triage-ignored";
    return "neutral";
}

function applyBadge(targetId, label, value, type) {
    const badge = document.getElementById(targetId);
    if (!badge) return;
    badge.className = `status-badge ${type}`;
    badge.textContent = `${label} : ${safe(value) || "-"}`;
}

function renderAssistantList(containerId, items) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const values = safeArray(items);

    if (values.length === 0) {
        container.innerHTML = `<div class="empty-list-state">Aucune donnée disponible.</div>`;
        return;
    }

    container.innerHTML = `
        <ul class="modern-list">
            ${values.map(item => `<li>${safe(item)}</li>`).join("")}
        </ul>
    `;
}

function renderKeyValueList(containerId, mapObject, limit = 8) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const entries = Object.entries(mapObject || {})
        .sort((a, b) => b[1] - a[1])
        .slice(0, limit);

    if (entries.length === 0) {
        container.innerHTML = `<div class="empty-list-state">Aucune donnée disponible.</div>`;
        return;
    }

    container.innerHTML = `
        <ul class="modern-list">
            ${entries.map(([key, value]) => `<li><strong>${safe(key)}</strong> : ${safe(value)}</li>`).join("")}
        </ul>
    `;
}

function renderLogsTable(logs) {
    const body = document.getElementById("logsTableBody");
    if (!body) return;

    body.innerHTML = "";

    if (!Array.isArray(logs) || logs.length === 0) {
        body.innerHTML = `
            <tr>
                <td colspan="11">Aucun log trouvé.</td>
            </tr>
        `;
        return;
    }

    logs.forEach(log => {
        const tr = document.createElement("tr");
        const importantStar = log.important ? "⭐" : "";
        const triageBadgeClass = getTriageStatusClass(log.triageStatus);

        tr.innerHTML = `
            <td>${safe(log.id)}</td>
            <td>${safe(log.importId)}</td>
            <td>${safe(log.logTimestamp)}</td>
            <td>${safe(log.level)}</td>
            <td>${safe(log.eventType)}</td>
            <td>${safe(log.fieldName)}</td>
            <td>${safe(log.businessKey || log.errorBusinessKey)}</td>
            <td>
                <div class="triage-cell">
                    <span class="status-badge ${triageBadgeClass}">${safe(log.triageStatus) || "OPEN"}</span>
                    ${importantStar ? `<span class="triage-star">${importantStar}</span>` : ""}
                </div>
            </td>
            <td>${safe(log.assignedTo)}</td>
            <td class="message-cell">
                ${safe(log.message)}
                ${log.lastComment ? `<div class="sub-note"><strong>Commentaire:</strong> ${safe(log.lastComment)}</div>` : ""}
            </td>
            <td>
                <div class="log-actions">
                    <button class="small-btn" onclick="loadExplanation(${log.id})">Expliquer</button>
                    <button class="small-btn action-progress-btn" onclick="startProgress(${log.id})">En cours</button>
                    <button class="small-btn action-resolve-btn" onclick="resolveLog(${log.id})">Résoudre</button>
                    <button class="small-btn action-ignore-btn" onclick="ignoreLog(${log.id})">Ignorer</button>
                    <button class="small-btn action-important-btn" onclick="${log.important ? `unmarkImportant(${log.id})` : `markImportant(${log.id})`}">
                        ${log.important ? "Retirer ⭐" : "Important"}
                    </button>
                    <button class="small-btn action-assign-btn" onclick="assignLog(${log.id})">Affecter</button>
                    <button class="small-btn action-comment-btn" onclick="commentLog(${log.id})">Commenter</button>
                    <button class="small-btn action-history-btn" onclick="loadTriageHistoryById(${log.id})">Historique</button>
                    <button class="small-btn secondary-small-btn" onclick="prefillIncidentFromLog(${log.id})">Créer incident</button>
                    ${log.triageStatus === "RESOLVED" || log.triageStatus === "IGNORED"
            ? `<button class="small-btn action-reopen-btn" onclick="reopenLog(${log.id})">Rouvrir</button>`
            : ""}
                </div>
            </td>
        `;

        body.appendChild(tr);
    });
}

function renderClusters(clusters) {
    const container = document.getElementById("errorClustersResult");
    if (!container) return;

    container.innerHTML = "";

    if (!Array.isArray(clusters) || clusters.length === 0) {
        container.innerHTML = `<div class="timeline-item"><div class="timeline-text">Aucun cluster trouvé.</div></div>`;
        return;
    }

    clusters.forEach(cluster => {
        const div = document.createElement("div");
        div.className = "timeline-item";

        div.innerHTML = `
            <div class="timeline-meta">
                <span class="status-badge ${getSeverityClass(cluster.severity)}">${safe(cluster.severity)}</span>
                <span class="badge">${safe(cluster.eventType)}</span>
                ${cluster.fieldName ? `<span class="badge">${safe(cluster.fieldName)}</span>` : ""}
            </div>
            <div class="timeline-title">
                ${safe(cluster.totalLogs)} log(s) | ${safe(cluster.openLogs)} ouvert(s)
            </div>
            <div class="timeline-text"><strong>Message exemple :</strong> ${safe(cluster.sampleMessage)}</div>
            <div class="timeline-text"><strong>Imports :</strong> ${safeArray(cluster.importIds).join(", ") || "Aucun"}</div>
            <div class="timeline-text"><strong>Business Keys :</strong> ${safeArray(cluster.businessKeys).join(", ") || "Aucune"}</div>
        `;

        container.appendChild(div);
    });
}

function renderImportsTable(imports) {
    const body = document.getElementById("importsTableBody");
    if (!body) return;

    body.innerHTML = "";

    if (!Array.isArray(imports) || imports.length === 0) {
        body.innerHTML = `
            <tr>
                <td colspan="9">Aucun import trouvé.</td>
            </tr>
        `;
        return;
    }

    imports.forEach(item => {
        const tr = document.createElement("tr");

        tr.innerHTML = `
            <td>${safe(item.id)}</td>
            <td>${safe(item.originalFileName)}</td>
            <td>${safe(item.status)}</td>
            <td>${safe(item.startedAt)}</td>
            <td>${safe(item.finishedAt)}</td>
            <td>${safe(item.totalLines)}</td>
            <td>${safe(item.processedLines)}</td>
            <td>${safe(item.failedLines)}</td>
            <td class="imports-actions">
                <button class="small-btn" onclick="loadImportSummaryById(${item.id})">Résumé</button>
                <button class="small-btn secondary-small-btn" onclick="loadImportDiagnosticById(${item.id})">Diagnostic</button>
            </td>
        `;

        body.appendChild(tr);
    });
}

function renderStory(targetSummaryId, targetAnomaliesId, targetConclusionId, targetStepsId, story) {
    if (!story) {
        setText(targetSummaryId, "Aucune donnée.");
        setText(targetAnomaliesId, "Aucune donnée.");
        setText(targetConclusionId, "Aucune donnée.");
        const target = document.getElementById(targetStepsId);
        if (target) target.innerHTML = "";
        return;
    }

    setText(targetSummaryId, story.summary || "");
    prettyPrint(targetAnomaliesId, story.detectedAnomalies || []);
    setText(targetConclusionId, story.conclusion || "");

    const container = document.getElementById(targetStepsId);
    if (!container) return;
    container.innerHTML = "";

    if (!Array.isArray(story.steps) || story.steps.length === 0) {
        container.innerHTML = `<div class="timeline-item"><div class="timeline-text">Aucune étape importante trouvée.</div></div>`;
        return;
    }

    story.steps.forEach(step => {
        const div = document.createElement("div");
        div.className = `timeline-item ${step.level === "ERROR" ? "error" : ""}`;

        div.innerHTML = `
            <div class="timeline-meta">
                <span class="badge ${step.level === "ERROR" ? "error" : ""}">${safe(step.level)}</span>
                <span class="badge">${safe(step.eventType)}</span>
                ${safe(step.timestamp)}
            </div>
            <div class="timeline-title">
                Log #${safe(step.logId)}
                ${step.fieldName ? ` | Champ: ${step.fieldName}` : ""}
                ${step.interfaceField ? ` | Interface: ${step.interfaceField}` : ""}
                ${step.relationName ? ` | Relation: ${step.relationName}` : ""}
                ${step.businessKey ? ` | BK: ${step.businessKey}` : ""}
            </div>
            <div class="timeline-text">${safe(step.humanExplanation)}</div>
            <div class="timeline-text" style="margin-top:8px;"><strong>Message original :</strong> ${safe(step.originalMessage)}</div>
        `;

        container.appendChild(div);
    });
}

function renderTriageHistory(history) {
    const container = document.getElementById("triageHistoryResult");
    if (!container) return;

    container.innerHTML = "";

    if (!Array.isArray(history) || history.length === 0) {
        container.innerHTML = `<div class="timeline-item"><div class="timeline-text">Aucun historique de triage trouvé.</div></div>`;
        return;
    }

    history.forEach(item => {
        const div = document.createElement("div");
        div.className = "timeline-item";

        div.innerHTML = `
            <div class="timeline-meta">
                <span class="badge">${safe(item.actionType)}</span>
                ${safe(item.actionAt)}
            </div>
            <div class="timeline-title">
                ${safe(item.oldStatus)} → ${safe(item.newStatus)}
                ${item.newAssignedTo ? ` | Assigné à : ${safe(item.newAssignedTo)}` : ""}
            </div>
            <div class="timeline-text">
                <strong>Par :</strong> ${safe(item.actionBy) || "N/A"}
            </div>
            <div class="timeline-text">
                <strong>Commentaire :</strong> ${safe(item.comment) || "Aucun commentaire"}
            </div>
        `;

        container.appendChild(div);
    });
}

function renderAssistantResponse(data) {
    document.getElementById("assistantEmptyState")?.classList.add("hidden");
    document.getElementById("assistantResultCard")?.classList.remove("hidden");

    applyBadge("assistantSeverityBadge", "Gravité", data.severity, getSeverityClass(data.severity));
    applyBadge("assistantConfidenceBadge", "Confiance", data.confidence, getConfidenceClass(data.confidence));
    applyBadge("assistantIntentBadge", "Intent", data.detectedIntent, "neutral");

    const shortEl = document.getElementById("assistantShortAnswerResult");
    if (shortEl) shortEl.textContent = safe(data.shortAnswer) || safe(data.answer) || "Aucune réponse.";

    const answerEl = document.getElementById("assistantAnswerResult");
    if (answerEl) answerEl.textContent = safe(data.answer) || "Aucune réponse détaillée.";

    renderAssistantList("assistantFindingsResult", data.findings);
    renderAssistantList("assistantRecommendationsResult", data.recommendations);
    renderAssistantList("assistantHintsResult", data.hints);
}

function clearAssistantResponse(message) {
    document.getElementById("assistantResultCard")?.classList.add("hidden");
    document.getElementById("assistantEmptyState")?.classList.remove("hidden");
    const el = document.getElementById("assistantEmptyState");
    if (el) el.textContent = message;
}

function renderDiagnostic(data) {
    document.getElementById("importDiagnosticCard")?.classList.remove("hidden");

    applyBadge("diagnosticSeverityBadge", "Gravité", data.severity, getSeverityClass(data.severity));
    applyBadge("diagnosticConfidenceBadge", "Confiance", data.confidence, getConfidenceClass(data.confidence));

    const executiveSummary = document.getElementById("diagnosticExecutiveSummary");
    if (executiveSummary) {
        executiveSummary.textContent = safe(data.executiveSummary) || "Aucun résumé exécutif disponible.";
    }

    const map = {
        diagnosticStatus: safe(data.status) || "-",
        diagnosticTotalLogs: safe(data.totalLogs) || "0",
        diagnosticErrorCount: safe(data.errorCount) || "0",
        diagnosticErrorRate: `${safe(data.errorRate) || "0"} %`
    };

    Object.entries(map).forEach(([id, value]) => {
        const el = document.getElementById(id);
        if (el) el.textContent = value;
    });

    const findings = [
        `Cause principale : ${safe(data.mainCause) || "Non déterminée"}`,
        `Étape de rupture : ${safe(data.failureStep) || "Non déterminée"}`,
        `Signal dominant : ${safe(data.dominantEventType) || "Non déterminé"}`,
        data.firstCriticalLogId ? `Premier log critique : #${data.firstCriticalLogId}` : null,
        data.firstCriticalErrorMessage ? `Premier signal d'erreur : ${data.firstCriticalErrorMessage}` : null,
        data.impact ? `Impact : ${data.impact}` : null
    ].filter(Boolean);

    const impactedZones = [
        ...(safeArray(data.affectedFields).map(field => `Champ impacté : ${field}`)),
        ...(safeArray(data.affectedBusinessKeys).map(bk => `Business key impactée : ${bk}`))
    ];

    renderAssistantList("diagnosticFindings", findings);
    renderAssistantList("diagnosticRecommendations", data.recommendations);
    renderAssistantList("diagnosticAnomalies", data.anomalies);
    renderAssistantList("diagnosticImpacts", impactedZones);
}

function clearDiagnostic() {
    document.getElementById("importDiagnosticCard")?.classList.add("hidden");
}

function renderDashboardAdvanced(data) {
    const mapping = {
        kpiImports: safe(data.totalImports) || "0",
        kpiLogs: safe(data.totalLogs) || "0",
        kpiErrors: safe(data.totalErrors) || "0",
        kpiRate: `${safe(data.errorRate) || "0"} %`,
        kpiBusinessKeys: safe(data.totalBusinessKeys) || "0",
        kpiCriticalImports: safe(data.criticalImportsCount) || "0",
        dashboardTopErrorType: safe(data.topErrorType) || "-",
        dashboardTopProblemField: safe(data.topProblemField) || "-",
        dashboardMostCriticalImport: safe(data.mostCriticalImportLabel) || "-"
    };

    Object.entries(mapping).forEach(([id, value]) => {
        const el = document.getElementById(id);
        if (el) el.textContent = value;
    });

    renderAssistantList("dashboardTopErrorMessages", data.topErrorMessages);
    renderAssistantList("dashboardTopProblemFields", data.topProblemFields);
    renderAssistantList("dashboardCriticalImports", data.criticalImports);
    renderKeyValueList("dashboardEventTypes", data.eventTypeDistribution, 10);
}

function renderPagination(meta) {
    currentLogsPage = meta.page;
    currentLogsTotalPages = meta.totalPages;

    const info = document.getElementById("paginationInfo");
    if (info) {
        info.textContent = `Page ${meta.page + 1} / ${meta.totalPages} — ${meta.totalElements} élément(s)`;
    }

    const prevBtn = document.getElementById("prevPageBtn");
    const nextBtn = document.getElementById("nextPageBtn");

    if (prevBtn) prevBtn.disabled = meta.first;
    if (nextBtn) nextBtn.disabled = meta.last;
}

function renderIncidents(incidents) {
    const container = document.getElementById("incidentsResult");
    if (!container) return;

    container.innerHTML = "";

    if (!Array.isArray(incidents) || incidents.length === 0) {
        container.innerHTML = `<div class="timeline-item"><div class="timeline-text">Aucun incident trouvé.</div></div>`;
        return;
    }

    incidents.forEach(incident => {
        const div = document.createElement("div");
        div.className = "timeline-item";

        div.innerHTML = `
            <div class="timeline-meta">
                <span class="status-badge ${getSeverityClass(incident.severity)}">${safe(incident.severity)}</span>
                <span class="badge">${safe(incident.status)}</span>
                Incident #${safe(incident.id)}
            </div>
            <div class="timeline-title">${safe(incident.title)}</div>
            <div class="timeline-text"><strong>Description :</strong> ${safe(incident.description)}</div>
            <div class="timeline-text"><strong>Assigné à :</strong> ${safe(incident.assignedTo) || "Non assigné"}</div>
            <div class="timeline-text"><strong>Créé par :</strong> ${safe(incident.createdBy)}</div>
            <div class="timeline-text"><strong>Logs liés :</strong> ${safeArray(incident.linkedLogIds).join(", ") || "Aucun"}</div>
            <div class="timeline-text"><strong>Dernière mise à jour :</strong> ${safe(incident.updatedAt)}</div>
        `;

        container.appendChild(div);
    });
}

function renderIncidentComments(comments) {
    const container = document.getElementById("incidentCommentsResult");
    if (!container) return;

    container.innerHTML = "";

    if (!Array.isArray(comments) || comments.length === 0) {
        container.innerHTML = `<div class="timeline-item"><div class="timeline-text">Aucun commentaire trouvé.</div></div>`;
        return;
    }

    comments.forEach(comment => {
        const div = document.createElement("div");
        div.className = "timeline-item";

        div.innerHTML = `
            <div class="timeline-meta">${safe(comment.commentAt)}</div>
            <div class="timeline-title">${safe(comment.commentBy) || "Utilisateur inconnu"}</div>
            <div class="timeline-text">${safe(comment.comment)}</div>
        `;

        container.appendChild(div);
    });
}

function renderExecutiveDashboard(data) {
    const mapping = {
        execTotalIncidents: safe(data.totalIncidents) || "0",
        execOpenIncidents: safe(data.openIncidents) || "0",
        execInProgressIncidents: safe(data.inProgressIncidents) || "0",
        execResolvedIncidents: safe(data.resolvedIncidents) || "0",
        execClosedIncidents: safe(data.closedIncidents) || "0",
        execActiveLogs: safe(data.activeLogs) || "0",
        execAvgTriage: `${safe(data.avgTriageMinutes) || "0"} min`,
        execAvgInvestigation: `${safe(data.avgInvestigationMinutes) || "0"} min`,
        execAvgResolution: `${safe(data.avgResolutionMinutes) || "0"} min`,
        execAvgClosure: `${safe(data.avgClosureMinutes) || "0"} min`
    };

    Object.entries(mapping).forEach(([id, value]) => {
        const el = document.getElementById(id);
        if (el) el.textContent = value;
    });

    renderKeyValueList("execIncidentsBySeverity", data.incidentsBySeverity, 20);
    renderKeyValueList("execIncidentsByStatus", data.incidentsByStatus, 20);
    renderKeyValueList("execActiveLogsByAssignee", data.activeLogsByAssignee, 20);
    renderAssistantList("execHighlights", data.executiveHighlights);
}

function renderAlerts(alerts) {
    const container = document.getElementById("alertsResult");
    if (!container) return;

    container.innerHTML = "";

    if (!Array.isArray(alerts) || alerts.length === 0) {
        container.innerHTML = `<div class="timeline-item"><div class="timeline-text">Aucune alerte trouvée.</div></div>`;
        return;
    }

    alerts.forEach(alert => {
        const div = document.createElement("div");
        div.className = "timeline-item";

        div.innerHTML = `
            <div class="timeline-meta">
                <span class="status-badge ${getSeverityClass(alert.severity)}">${safe(alert.severity)}</span>
                <span class="badge">${safe(alert.status)}</span>
                ${safe(alert.createdAt)}
            </div>
            <div class="timeline-title">${safe(alert.title)}</div>
            <div class="timeline-text">${safe(alert.message)}</div>
            <div class="timeline-text"><strong>Source:</strong> ${safe(alert.sourceType)} ${safe(alert.sourceId)}</div>
            <div class="log-actions" style="margin-top:10px;">
                <button class="small-btn action-comment-btn" onclick="ackAlert(${alert.id})">Ack</button>
                <button class="small-btn action-resolve-btn" onclick="resolveAlert(${alert.id})">Résoudre</button>
            </div>
        `;

        container.appendChild(div);
    });
}

function parseLogIdsInput(value) {
    return (value || "")
        .split(",")
        .map(v => v.trim())
        .filter(v => v.length > 0)
        .map(v => Number(v))
        .filter(v => !Number.isNaN(v));
}

function prefillIncidentFromLog(logId) {
    const input = document.getElementById("incidentLogIdsInput");
    if (input) input.value = String(logId);

    const titleInput = document.getElementById("incidentTitleInput");
    if (titleInput && !titleInput.value.trim()) {
        titleInput.value = `Incident lié au log ${logId}`;
    }

    const descriptionInput = document.getElementById("incidentDescriptionInput");
    if (descriptionInput && !descriptionInput.value.trim()) {
        descriptionInput.value = `Incident créé à partir du log ${logId}.`;
    }
}

async function uploadFile() {
    const fileInput = document.getElementById("logFile");
    const resultBox = document.getElementById("uploadResult");

    if (!fileInput || !resultBox) return;

    if (!fileInput.files || fileInput.files.length === 0) {
        resultBox.textContent = "Choisis d'abord un fichier log.";
        return;
    }

    const formData = new FormData();
    formData.append("file", fileInput.files[0]);

    try {
        resultBox.textContent = "Import en cours...";
        const result = await apiFetch("/ingest/upload", {
            method: "POST",
            body: formData
        });
        resultBox.textContent = result;
        await loadImports();
        await loadDashboard();
        await loadLogs(0);
        await loadClusters();
    } catch (error) {
        resultBox.textContent = "Erreur: " + error.message;
    }
}

async function askAssistant() {
    const input = document.getElementById("assistantQuestionInput");
    if (!input) return;

    const question = input.value.trim();

    if (!question) {
        clearAssistantResponse("Saisis une question.");
        return;
    }

    try {
        clearAssistantResponse("Analyse de la question en cours...");
        const data = await apiFetch("/assistant/ask", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ question })
        });

        renderAssistantResponse(data);
    } catch (error) {
        clearAssistantResponse("Erreur: " + error.message);
    }
}

async function loadDashboard() {
    try {
        const data = await apiFetch("/analysis/dashboard");
        renderDashboardAdvanced(data);
    } catch (error) {
        alert("Erreur dashboard: " + error.message);
    }
}

async function loadImports() {
    try {
        const data = await apiFetch("/imports");
        renderImportsTable(data);
    } catch (error) {
        alert("Erreur lors du chargement des imports: " + error.message);
    }
}

async function loadSummary() {
    try {
        const data = await apiFetch("/analysis/summary");
        prettyPrint("summaryResult", data);
    } catch (error) {
        setText("summaryResult", "Erreur: " + error.message);
    }
}

async function loadImportSummary() {
    const importId = document.getElementById("importIdInput")?.value.trim();
    if (!importId) {
        setText("importSummaryResult", "Saisis un ID d'import.");
        return;
    }
    await loadImportSummaryById(importId);
}

async function loadImportSummaryById(importId) {
    try {
        const data = await apiFetch(`/analysis/import/${importId}/summary`);
        prettyPrint("importSummaryResult", data);
        const input = document.getElementById("importIdInput");
        if (input) input.value = importId;
    } catch (error) {
        setText("importSummaryResult", "Erreur: " + error.message);
    }
}

async function loadImportDiagnostic() {
    const importId = document.getElementById("importIdInput")?.value.trim();
    if (!importId) {
        clearDiagnostic();
        setText("importSummaryResult", "Saisis un ID d'import.");
        return;
    }

    await loadImportDiagnosticById(importId);
}

async function loadImportDiagnosticById(importId) {
    try {
        const data = await apiFetch(`/analysis/import/${importId}/diagnostic`);
        const input = document.getElementById("importIdInput");
        if (input) input.value = importId;
        renderDiagnostic(data);
    } catch (error) {
        clearDiagnostic();
        setText("importSummaryResult", "Erreur diagnostic: " + error.message);
    }
}

async function loadLogs(page = 0) {
    try {
        const data = await apiFetch(buildLogsUrl(page));
        renderLogsTable(data.content || []);
        renderPagination(data);
    } catch (error) {
        alert("Erreur lors du chargement des logs: " + error.message);
    }
}

async function loadClusters() {
    try {
        const onlyActive = document.getElementById("clusterOnlyActiveFilter")?.checked ?? true;
        const limit = document.getElementById("clusterLimitInput")?.value.trim() || "10";
        const data = await apiFetch(`/logs/clusters/errors?onlyActive=${onlyActive}&limit=${limit}`);
        renderClusters(data);
    } catch (error) {
        const target = document.getElementById("errorClustersResult");
        if (target) {
            target.innerHTML = `<div class="timeline-item error"><div class="timeline-text">Erreur: ${error.message}</div></div>`;
        }
    }
}

async function analyzeBusinessKey() {
    const businessKey = document.getElementById("businessKeyInput")?.value.trim();
    if (!businessKey) {
        setText("businessKeyResult", "Saisis une business key.");
        return;
    }

    try {
        const data = await apiFetch(`/analysis/business-key/${encodeURIComponent(businessKey)}`);
        prettyPrint("businessKeyResult", data);
    } catch (error) {
        setText("businessKeyResult", "Erreur: " + error.message);
    }
}

async function analyzeBusinessKeyPerImport() {
    const importId = document.getElementById("importBusinessKeyInput")?.value.trim();
    const businessKey = document.getElementById("businessKeyPerImportInput")?.value.trim();

    if (!importId || !businessKey) {
        setText("businessKeyResult", "Saisis l'ID import et la business key.");
        return;
    }

    try {
        const data = await apiFetch(`/analysis/import/${importId}/business-key/${encodeURIComponent(businessKey)}`);
        prettyPrint("businessKeyResult", data);
    } catch (error) {
        setText("businessKeyResult", "Erreur: " + error.message);
    }
}

async function loadImportBusinessKeys() {
    const importId = document.getElementById("importBusinessKeysIdInput")?.value.trim();
    if (!importId) {
        setText("importBusinessKeysResult", "Saisis un ID import.");
        return;
    }

    try {
        const data = await apiFetch(`/analysis/import/${importId}/business-keys`);
        prettyPrint("importBusinessKeysResult", data);
    } catch (error) {
        setText("importBusinessKeysResult", "Erreur: " + error.message);
    }
}

async function explainLog() {
    const logId = document.getElementById("logIdInput")?.value.trim();
    if (!logId) {
        setText("logExplanationResult", "Saisis un ID de log.");
        return;
    }

    await loadExplanation(logId);
}

async function loadExplanation(logId) {
    try {
        const data = await apiFetch(`/analysis/log/${logId}/explain`);
        prettyPrint("logExplanationResult", data);
        const input = document.getElementById("logIdInput");
        if (input) input.value = logId;
    } catch (error) {
        setText("logExplanationResult", "Erreur: " + error.message);
    }
}

async function explainErrors() {
    const limit = document.getElementById("errorLimitInput")?.value.trim() || "10";

    try {
        const data = await apiFetch(`/analysis/errors/explain?limit=${encodeURIComponent(limit)}`);
        prettyPrint("errorsExplanationResult", data);
    } catch (error) {
        setText("errorsExplanationResult", "Erreur: " + error.message);
    }
}

async function loadImportStory() {
    const importId = document.getElementById("storyImportIdInput")?.value.trim();
    if (!importId) {
        setText("importStorySummaryResult", "Saisis un ID import.");
        return;
    }

    try {
        const data = await apiFetch(`/analysis/import/${importId}/story`);
        renderStory(
            "importStorySummaryResult",
            "importStoryAnomaliesResult",
            "importStoryConclusionResult",
            "importStoryStepsResult",
            data
        );
    } catch (error) {
        setText("importStorySummaryResult", "Erreur: " + error.message);
        setText("importStoryAnomaliesResult", "");
        setText("importStoryConclusionResult", "");
        const target = document.getElementById("importStoryStepsResult");
        if (target) target.innerHTML = "";
    }
}

async function loadBusinessKeyStory() {
    const businessKey = document.getElementById("storyBusinessKeyInput")?.value.trim();
    if (!businessKey) {
        setText("bkStorySummaryResult", "Saisis une business key.");
        return;
    }

    try {
        const data = await apiFetch(`/analysis/business-key/${encodeURIComponent(businessKey)}/story`);
        renderStory(
            "bkStorySummaryResult",
            "bkStoryAnomaliesResult",
            "bkStoryConclusionResult",
            "bkStoryStepsResult",
            data
        );
    } catch (error) {
        setText("bkStorySummaryResult", "Erreur: " + error.message);
        setText("bkStoryAnomaliesResult", "");
        setText("bkStoryConclusionResult", "");
        const target = document.getElementById("bkStoryStepsResult");
        if (target) target.innerHTML = "";
    }
}

async function loadBusinessKeyStoryPerImport() {
    const importId = document.getElementById("storyImportForBkInput")?.value.trim();
    const businessKey = document.getElementById("storyBusinessKeyPerImportInput")?.value.trim();

    if (!importId || !businessKey) {
        setText("bkStorySummaryResult", "Saisis l'ID import et la business key.");
        return;
    }

    try {
        const data = await apiFetch(`/analysis/import/${importId}/business-key/${encodeURIComponent(businessKey)}/story`);
        renderStory(
            "bkStorySummaryResult",
            "bkStoryAnomaliesResult",
            "bkStoryConclusionResult",
            "bkStoryStepsResult",
            data
        );
    } catch (error) {
        setText("bkStorySummaryResult", "Erreur: " + error.message);
        setText("bkStoryAnomaliesResult", "");
        setText("bkStoryConclusionResult", "");
        const target = document.getElementById("bkStoryStepsResult");
        if (target) target.innerHTML = "";
    }
}

async function callTriageAction(logId, path, payload) {
    await apiFetch(`/triage/logs/${logId}/${path}`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
    });

    await loadLogs(currentLogsPage);
    await loadDashboard();
    await loadClusters();
    await loadAlerts();
}

async function startProgress(logId) {
    const comment = prompt("Commentaire optionnel pour passer en cours ?", "") || "";
    await callTriageAction(logId, "start-progress", {
        comment,
        actionBy: currentUser()
    });
}

async function resolveLog(logId) {
    const comment = prompt("Commentaire de résolution (recommandé) :", "") || "";
    await callTriageAction(logId, "resolve", {
        comment,
        actionBy: currentUser()
    });
}

async function ignoreLog(logId) {
    const comment = prompt("Pourquoi ignorer ce log ? (obligatoire)", "");
    if (!comment || !comment.trim()) {
        alert("Un commentaire est obligatoire pour ignorer un log.");
        return;
    }

    await callTriageAction(logId, "ignore", {
        comment,
        actionBy: currentUser()
    });
}

async function reopenLog(logId) {
    const comment = prompt("Pourquoi rouvrir ce log ? (obligatoire)", "");
    if (!comment || !comment.trim()) {
        alert("Un commentaire est obligatoire pour rouvrir un log.");
        return;
    }

    await callTriageAction(logId, "reopen", {
        comment,
        actionBy: currentUser()
    });
}

async function markImportant(logId) {
    await callTriageAction(logId, "mark-important", {
        actionBy: currentUser()
    });
}

async function unmarkImportant(logId) {
    await callTriageAction(logId, "unmark-important", {
        actionBy: currentUser()
    });
}

async function assignLog(logId) {
    const assignedTo = prompt("Assigner à quel utilisateur ?", "");
    if (!assignedTo || !assignedTo.trim()) {
        alert("Saisis un utilisateur.");
        return;
    }

    const comment = prompt("Commentaire d'affectation (optionnel)", "") || "";

    await callTriageAction(logId, "assign", {
        assignedTo,
        comment,
        actionBy: currentUser()
    });
}

async function commentLog(logId) {
    const comment = prompt("Commentaire à ajouter", "");
    if (!comment || !comment.trim()) {
        return;
    }

    await callTriageAction(logId, "comment", {
        comment,
        actionBy: currentUser()
    });
}

async function loadTriageHistory() {
    const logId = document.getElementById("triageHistoryLogIdInput")?.value.trim();
    if (!logId) {
        const target = document.getElementById("triageHistoryResult");
        if (target) {
            target.innerHTML = `<div class="timeline-item"><div class="timeline-text">Saisis un ID de log.</div></div>`;
        }
        return;
    }

    await loadTriageHistoryById(logId);
}

async function loadTriageHistoryById(logId) {
    try {
        const data = await apiFetch(`/triage/logs/${logId}/history`);
        const input = document.getElementById("triageHistoryLogIdInput");
        if (input) input.value = logId;
        renderTriageHistory(data);
    } catch (error) {
        const target = document.getElementById("triageHistoryResult");
        if (target) {
            target.innerHTML = `<div class="timeline-item error"><div class="timeline-text">Erreur: ${error.message}</div></div>`;
        }
    }
}

async function createIncident() {
    try {
        const payload = {
            title: document.getElementById("incidentTitleInput")?.value.trim() || "",
            description: document.getElementById("incidentDescriptionInput")?.value.trim() || "",
            severity: document.getElementById("incidentSeverityInput")?.value.trim() || "",
            createdBy: currentUser(),
            assignedTo: document.getElementById("incidentAssignedToInput")?.value.trim() || "",
            logIds: parseLogIdsInput(document.getElementById("incidentLogIdsInput")?.value || "")
        };

        const data = await apiFetch("/incidents", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        });

        prettyPrint("incidentCreateResult", data);
        await loadIncidents();
    } catch (error) {
        setText("incidentCreateResult", "Erreur: " + error.message);
    }
}

async function loadIncidents() {
    try {
        const data = await apiFetch("/incidents");
        renderIncidents(data);
    } catch (error) {
        const target = document.getElementById("incidentsResult");
        if (target) {
            target.innerHTML = `<div class="timeline-item error"><div class="timeline-text">Erreur: ${error.message}</div></div>`;
        }
    }
}

async function addIncidentComment() {
    const incidentId = document.getElementById("incidentIdInput")?.value.trim();
    const comment = document.getElementById("incidentCommentInput")?.value.trim();

    if (!incidentId || !comment) {
        alert("Incident ID et commentaire requis.");
        return;
    }

    try {
        await apiFetch(`/incidents/${incidentId}/comments`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                comment,
                commentBy: currentUser()
            })
        });

        const input = document.getElementById("incidentCommentInput");
        if (input) input.value = "";

        await loadIncidentComments();
        await loadIncidents();
    } catch (error) {
        alert("Erreur commentaire incident: " + error.message);
    }
}

async function loadIncidentComments() {
    const incidentId = document.getElementById("incidentIdInput")?.value.trim();
    if (!incidentId) {
        alert("Saisis un ID incident.");
        return;
    }

    try {
        const data = await apiFetch(`/incidents/${incidentId}/comments`);
        renderIncidentComments(data);
    } catch (error) {
        const target = document.getElementById("incidentCommentsResult");
        if (target) {
            target.innerHTML = `<div class="timeline-item error"><div class="timeline-text">Erreur: ${error.message}</div></div>`;
        }
    }
}

async function updateIncident() {
    const incidentId = document.getElementById("incidentIdInput")?.value.trim();
    if (!incidentId) {
        alert("Saisis un ID incident.");
        return;
    }

    try {
        await apiFetch(`/incidents/${incidentId}`, {
            method: "PUT",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                status: document.getElementById("incidentUpdateStatusInput")?.value.trim() || "",
                severity: document.getElementById("incidentUpdateSeverityInput")?.value.trim() || "",
                assignedTo: document.getElementById("incidentUpdateAssignedToInput")?.value.trim() || "",
                updatedBy: currentUser()
            })
        });

        await loadIncidents();
        await loadIncidentComments();
    } catch (error) {
        alert("Erreur mise à jour incident: " + error.message);
    }
}

async function runAutoIncidentDetection() {
    try {
        const data = await apiFetch("/auto-incidents/detect", {
            method: "POST"
        });
        prettyPrint("autoIncidentDetectionResult", data);
        await loadIncidents();

        const user = JSON.parse(localStorage.getItem("logAnalyzer.authUser") || "null");
        if (user?.role === "MANAGER" || user?.role === "ADMIN") {
            await loadExecutiveDashboard();
        }
    } catch (error) {
        setText("autoIncidentDetectionResult", "Erreur: " + error.message);
    }
}

async function runAlertChecks() {
    try {
        const data = await apiFetch("/alerts/run-checks", { method: "POST" });
        prettyPrint("alertRunResult", data);
        await loadAlerts();
    } catch (error) {
        setText("alertRunResult", "Erreur: " + error.message);
    }
}

async function loadAlerts() {
    try {
        const status = document.getElementById("alertStatusFilter")?.value.trim() || "";
        const url = status ? `/alerts?status=${encodeURIComponent(status)}` : "/alerts";
        const data = await apiFetch(url);
        renderAlerts(data);
    } catch (error) {
        const target = document.getElementById("alertsResult");
        if (target) {
            target.innerHTML = `<div class="timeline-item error"><div class="timeline-text">Erreur: ${error.message}</div></div>`;
        }
    }
}

async function ackAlert(alertId) {
    try {
        await apiFetch(`/alerts/${alertId}/ack`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username: currentUser() })
        });
        await loadAlerts();
    } catch (error) {
        alert("Erreur ack alert: " + error.message);
    }
}

async function resolveAlert(alertId) {
    try {
        await apiFetch(`/alerts/${alertId}/resolve`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username: currentUser() })
        });
        await loadAlerts();
    } catch (error) {
        alert("Erreur resolve alert: " + error.message);
    }
}

async function loadExecutiveDashboard() {
    try {
        const data = await apiFetch("/executive/dashboard");
        renderExecutiveDashboard(data);
    } catch (error) {
        alert("Erreur dashboard exécutif: " + error.message);
    }
}

async function askLlm() {
    try {
        const prompt = document.getElementById("llmPromptInput")?.value.trim() || "";
        const context = document.getElementById("llmContextInput")?.value.trim() || "";

        const data = await apiFetch("/llm/ask", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ prompt, context })
        });

        prettyPrint("llmResult", data);
    } catch (error) {
        setText("llmResult", "Erreur: " + error.message);
    }
}

async function initializeSecuredData() {
    try {
        await loadDashboard();
        await loadImports();
        await loadLogs(0);
        await loadClusters();
        await loadIncidents();
        await loadAlerts();

        const user = JSON.parse(localStorage.getItem("logAnalyzer.authUser") || "null");
        if (user?.role === "MANAGER" || user?.role === "ADMIN") {
            await loadExecutiveDashboard();
        }

        clearDiagnostic();
        clearAssistantResponse("Pose une question naturelle pour obtenir une réponse intelligente, structurée et actionnable.");
    } catch (e) {
        console.error(e);
    }
}

document.getElementById("uploadBtn")?.addEventListener("click", uploadFile);
document.getElementById("askAssistantBtn")?.addEventListener("click", askAssistant);
document.getElementById("loadDashboardBtn")?.addEventListener("click", loadDashboard);
document.getElementById("loadImportsBtn")?.addEventListener("click", loadImports);
document.getElementById("loadSummaryBtn")?.addEventListener("click", loadSummary);
document.getElementById("loadImportSummaryBtn")?.addEventListener("click", loadImportSummary);
document.getElementById("loadImportDiagnosticBtn")?.addEventListener("click", loadImportDiagnostic);
document.getElementById("loadLogsBtn")?.addEventListener("click", () => loadLogs(0));
document.getElementById("loadClustersBtn")?.addEventListener("click", loadClusters);
document.getElementById("loadTriageHistoryBtn")?.addEventListener("click", loadTriageHistory);
document.getElementById("analyzeBkBtn")?.addEventListener("click", analyzeBusinessKey);
document.getElementById("analyzeBkPerImportBtn")?.addEventListener("click", analyzeBusinessKeyPerImport);
document.getElementById("loadImportBusinessKeysBtn")?.addEventListener("click", loadImportBusinessKeys);
document.getElementById("explainLogBtn")?.addEventListener("click", explainLog);
document.getElementById("explainErrorsBtn")?.addEventListener("click", explainErrors);
document.getElementById("loadImportStoryBtn")?.addEventListener("click", loadImportStory);
document.getElementById("loadBkStoryBtn")?.addEventListener("click", loadBusinessKeyStory);
document.getElementById("loadBkStoryPerImportBtn")?.addEventListener("click", loadBusinessKeyStoryPerImport);

document.getElementById("prevPageBtn")?.addEventListener("click", () => {
    if (currentLogsPage > 0) {
        loadLogs(currentLogsPage - 1);
    }
});

document.getElementById("nextPageBtn")?.addEventListener("click", () => {
    if (currentLogsPage + 1 < currentLogsTotalPages) {
        loadLogs(currentLogsPage + 1);
    }
});

document.getElementById("createIncidentBtn")?.addEventListener("click", createIncident);
document.getElementById("loadIncidentsBtn")?.addEventListener("click", loadIncidents);
document.getElementById("addIncidentCommentBtn")?.addEventListener("click", addIncidentComment);
document.getElementById("loadIncidentCommentsBtn")?.addEventListener("click", loadIncidentComments);
document.getElementById("updateIncidentBtn")?.addEventListener("click", updateIncident);

document.getElementById("loadExecutiveDashboardBtn")?.addEventListener("click", loadExecutiveDashboard);
document.getElementById("runAutoIncidentDetectionBtn")?.addEventListener("click", runAutoIncidentDetection);

document.getElementById("runAlertChecksBtn")?.addEventListener("click", runAlertChecks);
document.getElementById("loadAlertsBtn")?.addEventListener("click", loadAlerts);

document.getElementById("askLlmBtn")?.addEventListener("click", askLlm);

window.addEventListener("DOMContentLoaded", async () => {
    await window.initializeAuth(async () => {
        await initializeSecuredData();
    });
});