import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import PropTypes from "prop-types";
import { login as apiLogin, register as apiRegister, logout as apiLogout } from "../api";

const AuthContext = createContext(null);

const STORAGE_KEY = "catalogix.auth";

function readStoredAuth() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function writeStoredAuth(next) {
  if (next) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } else {
    localStorage.removeItem(STORAGE_KEY);
  }
}

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(readStoredAuth);

  const persist = useCallback((next) => {
    setAuth(next);
    writeStoredAuth(next);
  }, []);

  const login = useCallback(async (email, password) => {
    const data = await apiLogin(email, password);
    persist(data);
    return data;
  }, [persist]);

  const register = useCallback(async (name, email, password) => {
    const data = await apiRegister(name, email, password);
    persist(data);
    return data;
  }, [persist]);

  const logout = useCallback(async () => {
    persist(null);
    // Best-effort: revoke server-side too, but don't block clearing the
    // local session on it (e.g. the backend being briefly unreachable
    // shouldn't trap the user in a "logged in" state on their own machine).
    // No refresh token to pass — it lives only in the httpOnly cookie, which
    // the browser attaches to this request automatically (see api.jsx).
    try { await apiLogout(); } catch { /* already logged out locally */ }
  }, [persist]);

  // Merges a fresh profile (e.g. the response from PATCH /users/me) into the
  // current session without touching the tokens — used after editing your profile.
  const updateUser = useCallback((updatedUser) => {
    const current = readStoredAuth();
    if (!current) return;
    persist({ ...current, user: updatedUser });
  }, [persist]);

  // api.jsx dispatches this when even a silent token refresh fails (refresh
  // token itself expired/revoked) — there's no way to recover the session.
  useEffect(() => {
    const onForcedLogout = () => persist(null);
    window.addEventListener("catalogix:unauthorized", onForcedLogout);
    return () => window.removeEventListener("catalogix:unauthorized", onForcedLogout);
  }, [persist]);

  // api.jsx calls this after silently refreshing an expired access token, so
  // the new access token is persisted without a full React state round-trip.
  // The rotated refresh token is never in e.detail — it arrived as a
  // Set-Cookie header the browser already stored on its own; there's nothing
  // for this code to read or persist for it.
  useEffect(() => {
    const onTokensRefreshed = (e) => {
      const current = readStoredAuth();
      if (!current) return;
      const next = { ...current, accessToken: e.detail.accessToken };
      setAuth(next);
      writeStoredAuth(next);
    };
    window.addEventListener("catalogix:tokens-refreshed", onTokensRefreshed);
    return () => window.removeEventListener("catalogix:tokens-refreshed", onTokensRefreshed);
  }, []);

  const value = useMemo(() => ({
    user: auth?.user ?? null,
    isAuthenticated: Boolean(auth?.accessToken),
    isAdmin: auth?.user?.role === "ADMIN",
    isSeller: auth?.user?.role === "SELLER",
    login,
    register,
    logout,
    updateUser,
  }), [auth, login, register, logout, updateUser]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

AuthProvider.propTypes = {
  children: PropTypes.node.isRequired,
};

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}

// Read directly from storage (not React state) so api.jsx — which lives
// outside the component tree — can always grab the latest tokens without
// needing a hook.
export function getStoredAccessToken() {
  return readStoredAuth()?.accessToken ?? null;
}
