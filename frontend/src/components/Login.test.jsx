import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import Login from "./Login";
import { AuthProvider } from "../context/AuthContext";
import * as api from "../api";

vi.mock("../api");

// "Log in" is both a tab label and the submit button's label — disambiguate by type.
function submitLogInButton() {
  return screen
    .getAllByRole("button", { name: /^log in$/i })
    .find((btn) => btn.type === "submit");
}

function renderLogin() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <Login />
      </AuthProvider>
    </MemoryRouter>
  );
}

describe("Login", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it("renders the login form by default (no name field)", () => {
    renderLogin();
    expect(submitLogInButton()).toBeInTheDocument();
    expect(screen.queryByLabelText(/full name/i)).not.toBeInTheDocument();
  });

  it("switches to the register tab and shows the name field", async () => {
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: /register/i }));
    expect(screen.getByLabelText(/full name/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /create account/i })).toBeInTheDocument();
  });

  it("calls the login API with a trimmed email and untouched password", async () => {
    api.login.mockResolvedValue({
      accessToken: "token",
      accessTokenExpiresInMs: 900000,
      user: { id: 1, name: "Alice", email: "alice@example.com", role: "USER" },
    });

    renderLogin();
    await userEvent.type(screen.getByLabelText(/email address/i), "  alice@example.com  ");
    await userEvent.type(screen.getByLabelText(/^password$/i), "Password1");
    await userEvent.click(submitLogInButton());

    await waitFor(() => expect(api.login).toHaveBeenCalledWith("alice@example.com", "Password1"));
  });

  it("calls the register API when in register mode", async () => {
    api.register.mockResolvedValue({
      accessToken: "token",
      accessTokenExpiresInMs: 900000,
      user: { id: 2, name: "Bob", email: "bob@example.com", role: "USER" },
    });

    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: /register/i }));
    await userEvent.type(screen.getByLabelText(/full name/i), "Bob");
    await userEvent.type(screen.getByLabelText(/email address/i), "bob@example.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "Password1");
    await userEvent.click(screen.getByRole("button", { name: /create account/i }));

    await waitFor(() =>
      expect(api.register).toHaveBeenCalledWith("Bob", "bob@example.com", "Password1")
    );
  });

  it("shows the server's error message when login fails", async () => {
    api.login.mockRejectedValue({ response: { data: { message: "Invalid email or password" } } });

    renderLogin();
    await userEvent.type(screen.getByLabelText(/email address/i), "bad@example.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "wrongpass");
    await userEvent.click(submitLogInButton());

    expect(await screen.findByText("Invalid email or password")).toBeInTheDocument();
  });

  it("falls back to a generic error message when the server gives no message", async () => {
    api.login.mockRejectedValue(new Error("network down"));

    renderLogin();
    await userEvent.type(screen.getByLabelText(/email address/i), "bad@example.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "wrongpass");
    await userEvent.click(submitLogInButton());

    expect(await screen.findByText(/invalid email or password/i)).toBeInTheDocument();
  });
});
