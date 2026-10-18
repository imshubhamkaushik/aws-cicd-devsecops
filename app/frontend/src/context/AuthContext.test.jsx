import { render, screen, waitFor, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { AuthProvider, useAuth } from "./AuthContext";
import * as api from "../api";

vi.mock("../api");

function Harness() {
  const { user, isAuthenticated, isAdmin, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="auth-state">{isAuthenticated ? "in" : "out"}</span>
      <span data-testid="user-name">{user?.name ?? "none"}</span>
      <span data-testid="is-admin">{isAdmin ? "yes" : "no"}</span>
      <button onClick={() => login("a@b.com", "pw")}>do-login</button>
      <button onClick={() => logout()}>do-logout</button>
    </div>
  );
}

function renderHarness() {
  return render(
    <AuthProvider>
      <Harness />
    </AuthProvider>
  );
}

describe("AuthContext", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it("starts unauthenticated when storage is empty", () => {
    renderHarness();
    expect(screen.getByTestId("auth-state")).toHaveTextContent("out");
  });

  it("becomes authenticated after login() resolves and persists to localStorage", async () => {
    // No refreshToken here — the real backend never puts it in the JSON body
    // anymore (see UserController); it arrives as an httpOnly Set-Cookie the
    // browser manages on its own, invisible to and unstored by this code.
    api.login.mockResolvedValue({
      accessToken: "tok",
      accessTokenExpiresInMs: 900000,
      user: { id: 1, name: "Alice", email: "a@b.com", role: "ADMIN" },
    });

    renderHarness();
    await act(async () => {
      screen.getByText("do-login").click();
    });

    await waitFor(() => expect(screen.getByTestId("auth-state")).toHaveTextContent("in"));
    expect(screen.getByTestId("user-name")).toHaveTextContent("Alice");
    expect(screen.getByTestId("is-admin")).toHaveTextContent("yes");

    const stored = JSON.parse(localStorage.getItem("catalogix.auth"));
    expect(stored.accessToken).toBe("tok");
    // The whole point of this migration: no refresh token ever lands in
    // localStorage, so an XSS bug reading it can't carry away a long-lived
    // credential.
    expect(stored.refreshToken).toBeUndefined();
  });

  it("clears state and storage on logout", async () => {
    api.login.mockResolvedValue({
      accessToken: "tok",
      accessTokenExpiresInMs: 900000,
      user: { id: 1, name: "Alice", email: "a@b.com", role: "USER" },
    });
    api.logout.mockResolvedValue(undefined);

    renderHarness();
    await act(async () => {
      screen.getByText("do-login").click();
    });
    await waitFor(() => expect(screen.getByTestId("auth-state")).toHaveTextContent("in"));

    await act(async () => {
      screen.getByText("do-logout").click();
    });

    await waitFor(() => expect(screen.getByTestId("auth-state")).toHaveTextContent("out"));
    expect(localStorage.getItem("catalogix.auth")).toBeNull();
  });

  it("reacts to a forced-logout event (e.g. an expired/revoked refresh token)", async () => {
    api.login.mockResolvedValue({
      accessToken: "tok",
      accessTokenExpiresInMs: 900000,
      user: { id: 1, name: "Alice", email: "a@b.com", role: "USER" },
    });

    renderHarness();
    await act(async () => {
      screen.getByText("do-login").click();
    });
    await waitFor(() => expect(screen.getByTestId("auth-state")).toHaveTextContent("in"));

    await act(async () => {
      window.dispatchEvent(new Event("catalogix:unauthorized"));
    });

    await waitFor(() => expect(screen.getByTestId("auth-state")).toHaveTextContent("out"));
  });

  it("updates the stored access token on a silent-refresh event without logging out", async () => {
    api.login.mockResolvedValue({
      accessToken: "old-token",
      accessTokenExpiresInMs: 900000,
      user: { id: 1, name: "Alice", email: "a@b.com", role: "USER" },
    });

    renderHarness();
    await act(async () => {
      screen.getByText("do-login").click();
    });
    await waitFor(() => expect(screen.getByTestId("auth-state")).toHaveTextContent("in"));

    await act(async () => {
      // Matches what api.jsx's performRefresh actually dispatches now: no
      // refreshToken field — the rotated cookie is set directly by the
      // Set-Cookie header on the /users/refresh response, never surfaced
      // to JS.
      window.dispatchEvent(new CustomEvent("catalogix:tokens-refreshed", {
        detail: { accessToken: "new-token", accessTokenExpiresInMs: 900000 },
      }));
    });

    await waitFor(() => {
      const stored = JSON.parse(localStorage.getItem("catalogix.auth"));
      expect(stored.accessToken).toBe("new-token");
      expect(stored.refreshToken).toBeUndefined();
    });
    // Still logged in — a silent refresh must not look like a logout.
    expect(screen.getByTestId("auth-state")).toHaveTextContent("in");
  });
});
