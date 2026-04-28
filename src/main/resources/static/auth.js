const TOKEN_KEYS = {
    access: "accessToken",
    refresh: "refreshToken"
};

function saveTokens(accessToken, refreshToken) {
    localStorage.setItem(TOKEN_KEYS.access, accessToken);
    localStorage.setItem(TOKEN_KEYS.refresh, refreshToken);
}

function getAccessToken() {
    return localStorage.getItem(TOKEN_KEYS.access);
}

function getRefreshToken() {
    return localStorage.getItem(TOKEN_KEYS.refresh);
}

function clearTokens() {
    localStorage.removeItem(TOKEN_KEYS.access);
    localStorage.removeItem(TOKEN_KEYS.refresh);
}

async function login(username, password) {
    const response = await fetch("/auth/login", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ username, password })
    });

    const data = await safeJson(response);

    if (!response.ok) {
        throw new Error(data?.message || "Échec de connexion.");
    }

    saveTokens(data.accessToken, data.refreshToken);
    return data;
}

async function refreshAccessToken() {
    const refreshToken = getRefreshToken();

    if (!refreshToken) {
        throw new Error("Aucun refresh token disponible.");
    }

    const response = await fetch("/auth/refresh", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ refreshToken })
    });

    const data = await safeJson(response);

    if (!response.ok) {
        clearTokens();
        throw new Error(data?.message || "Session expirée.");
    }

    saveTokens(data.accessToken, data.refreshToken);
    return data.accessToken;
}

async function logout() {
    const refreshToken = getRefreshToken();

    try {
        if (refreshToken) {
            await fetch("/auth/logout", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({ refreshToken })
            });
        }
    } finally {
        clearTokens();
    }
}

async function authFetch(url, options = {}, retry = true) {
    const accessToken = getAccessToken();

    const headers = new Headers(options.headers || {});

    if (accessToken) {
        headers.set("Authorization", `Bearer ${accessToken}`);
    }

    const response = await fetch(url, {
        ...options,
        headers
    });

    if (response.status === 401 && retry && getRefreshToken()) {
        try {
            const newAccessToken = await refreshAccessToken();

            const retryHeaders = new Headers(options.headers || {});
            retryHeaders.set("Authorization", `Bearer ${newAccessToken}`);

            return fetch(url, {
                ...options,
                headers: retryHeaders
            });
        } catch (e) {
            clearTokens();
            throw e;
        }
    }

    return response;
}

async function safeJson(response) {
    const text = await response.text();
    if (!text) return null;

    try {
        return JSON.parse(text);
    } catch {
        return { message: text };
    }
}