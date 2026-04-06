const AUTH_STORAGE_KEYS = {
    accessToken: "logAnalyzer.accessToken",
    refreshToken: "logAnalyzer.refreshToken",
    authUser: "logAnalyzer.authUser"
};

function getAccessToken() {
    return localStorage.getItem(AUTH_STORAGE_KEYS.accessToken);
}

function getRefreshToken() {
    return localStorage.getItem(AUTH_STORAGE_KEYS.refreshToken);
}

function getStoredUser() {
    const raw = localStorage.getItem(AUTH_STORAGE_KEYS.authUser);
    return raw ? JSON.parse(raw) : null;
}

function storeAuth(data) {
    localStorage.setItem(AUTH_STORAGE_KEYS.accessToken, data.accessToken);
    localStorage.setItem(AUTH_STORAGE_KEYS.refreshToken, data.refreshToken);
    localStorage.setItem(AUTH_STORAGE_KEYS.authUser, JSON.stringify({
        username: data.username,
        displayName: data.displayName,
        role: data.role
    }));
}

function clearAuth() {
    localStorage.removeItem(AUTH_STORAGE_KEYS.accessToken);
    localStorage.removeItem(AUTH_STORAGE_KEYS.refreshToken);
    localStorage.removeItem(AUTH_STORAGE_KEYS.authUser);
}

window.currentUser = function () {
    const user = getStoredUser();
    return user?.username || "anonymous";
};

window.apiFetch = async function (url, options = {}, retry = true) {
    const headers = new Headers(options.headers || {});
    const accessToken = getAccessToken();

    if (accessToken) {
        headers.set("Authorization", `Bearer ${accessToken}`);
    }

    const response = await fetch(url, {
        ...options,
        headers
    });

    const contentType = response.headers.get("content-type") || "";
    const isJson = contentType.includes("application/json");

    if (response.status === 401 && retry && getRefreshToken()) {
        const refreshed = await refreshAccessToken();
        if (refreshed) {
            return window.apiFetch(url, options, false);
        }
    }

    if (!response.ok) {
        const body = isJson ? await response.json() : await response.text();
        throw new Error(typeof body === "string" ? body : JSON.stringify(body, null, 2));
    }

    return isJson ? response.json() : response.text();
};

async function refreshAccessToken() {
    const refreshToken = getRefreshToken();
    if (!refreshToken) return false;

    try {
        const response = await fetch("/auth/refresh", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ refreshToken })
        });

        if (!response.ok) {
            clearAuth();
            updateAuthUi(null);
            return false;
        }

        const data = await response.json();
        storeAuth(data);
        updateAuthUi(getStoredUser());
        return true;
    } catch (e) {
        clearAuth();
        updateAuthUi(null);
        return false;
    }
}

async function loadMe() {
    try {
        const user = await window.apiFetch("/auth/me");
        localStorage.setItem(AUTH_STORAGE_KEYS.authUser, JSON.stringify(user));
        updateAuthUi(user);
        return user;
    } catch (e) {
        return null;
    }
}

function updateAuthUi(user) {
    const authDisplayName = document.getElementById("authDisplayName");
    const authRole = document.getElementById("authRole");
    const authState = document.getElementById("authState");
    const loginCard = document.getElementById("loginCard");
    const securedApp = document.getElementById("securedApp");

    if (user) {
        authDisplayName.textContent = user.displayName || user.username;
        authRole.textContent = user.role || "-";
        authState.textContent = "Connecté";
        authState.className = "status-badge confidence-high";

        loginCard.classList.add("hidden");
        securedApp.classList.remove("hidden");
    } else {
        authDisplayName.textContent = "-";
        authRole.textContent = "-";
        authState.textContent = "Déconnecté";
        authState.className = "status-badge neutral";

        loginCard.classList.remove("hidden");
        securedApp.classList.add("hidden");
    }
}

async function login() {
    const username = document.getElementById("loginUsernameInput").value.trim();
    const password = document.getElementById("loginPasswordInput").value;
    const loginError = document.getElementById("loginError");

    loginError.textContent = "";

    if (!username || !password) {
        loginError.textContent = "Username et mot de passe requis.";
        return;
    }

    try {
        const response = await fetch("/auth/login", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ username, password })
        });

        if (!response.ok) {
            loginError.textContent = "Identifiants invalides.";
            return;
        }

        const data = await response.json();
        storeAuth(data);
        updateAuthUi(getStoredUser());

        if (typeof window.onAuthReady === "function") {
            await window.onAuthReady();
        }
    } catch (e) {
        loginError.textContent = "Erreur de connexion.";
    }
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
    } catch (e) {
        // ignore
    }

    clearAuth();
    updateAuthUi(null);
}

window.initializeAuth = async function (onReady) {
    window.onAuthReady = onReady;

    document.getElementById("loginBtn").addEventListener("click", login);
    document.getElementById("logoutBtn").addEventListener("click", logout);

    const storedUser = getStoredUser();
    const accessToken = getAccessToken();

    if (storedUser && accessToken) {
        updateAuthUi(storedUser);
        const me = await loadMe();
        if (me && typeof window.onAuthReady === "function") {
            await window.onAuthReady();
        } else {
            clearAuth();
            updateAuthUi(null);
        }
    } else {
        updateAuthUi(null);
    }
};