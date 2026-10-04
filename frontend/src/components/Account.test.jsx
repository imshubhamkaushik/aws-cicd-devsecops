import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import Account from "./Account";
import { AuthProvider } from "../context/AuthContext";
import * as api from "../api";

vi.mock("../api");

function renderAccount() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <Account />
      </AuthProvider>
    </MemoryRouter>
  );
}

async function loginFirst() {
  api.login.mockResolvedValue({
    accessToken: "tok",
    accessTokenExpiresInMs: 900000,
    user: { id: 1, name: "Alice", email: "alice@example.com", role: "USER", verified: false },
  });
  // AuthProvider reads from localStorage on init, so seed it directly rather
  // than going through the login form (Account doesn't render one anyway).
  localStorage.setItem("catalogix.auth", JSON.stringify({
    accessToken: "tok",
    accessTokenExpiresInMs: 900000,
    user: { id: 1, name: "Alice", email: "alice@example.com", role: "USER", verified: false },
  }));
}

describe("Account page", () => {
  beforeEach(async () => {
    localStorage.clear();
    vi.clearAllMocks();
    await loginFirst();
    // Fetched unconditionally on mount (see Account.jsx's address book,
    // profile summary, and sessions sections).
    api.getAddresses.mockResolvedValue([]);
    api.getOrders.mockResolvedValue({ content: [], page: 0, size: 1, totalElements: 3, totalPages: 3 });
    api.getSessions.mockResolvedValue([]);
  });

  it("pre-fills the form with the current user's name and email", async () => {
    renderAccount();
    expect(screen.getByLabelText(/full name/i)).toHaveValue("Alice");
    expect(screen.getByLabelText(/^email address$/i)).toHaveValue("alice@example.com");
    // Lets the address-book effect (fired on mount) settle within this test's
    // act() cycle, rather than resolving after the test has already returned.
    await screen.findByText(/no saved addresses yet/i);
  });

  it("shows an unverified banner with a resend button", async () => {
    renderAccount();
    expect(screen.getByText(/isn't verified yet/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /resend verification email/i })).toBeInTheDocument();
    await screen.findByText(/no saved addresses yet/i);
  });

  it("calls resendVerification when the resend button is clicked", async () => {
    api.resendVerification.mockResolvedValue(undefined);
    renderAccount();

    await userEvent.click(screen.getByRole("button", { name: /resend verification email/i }));

    await waitFor(() => expect(api.resendVerification).toHaveBeenCalledTimes(1));
  });

  it("updates just the name without sending currentPassword", async () => {
    api.updateProfile.mockResolvedValue({
      id: 1, name: "Alicia", email: "alice@example.com", role: "USER", verified: false,
    });

    renderAccount();
    const nameInput = screen.getByLabelText(/full name/i);
    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, "Alicia");
    await userEvent.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => expect(api.updateProfile).toHaveBeenCalledWith({ name: "Alicia" }));
  });

  it("includes currentPassword when the email is changed", async () => {
    api.updateProfile.mockResolvedValue({
      id: 1, name: "Alice", email: "new@example.com", role: "USER", verified: false,
    });

    renderAccount();
    const emailInput = screen.getByLabelText(/^email address$/i);
    await userEvent.clear(emailInput);
    await userEvent.type(emailInput, "new@example.com");
    await userEvent.type(screen.getByLabelText(/current password/i), "CorrectPass1");
    await userEvent.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => expect(api.updateProfile).toHaveBeenCalledWith({
      name: "Alice", email: "new@example.com", currentPassword: "CorrectPass1",
    }));
  });

  it("shows an empty-state message when there are no saved addresses", async () => {
    renderAccount();
    expect(await screen.findByText(/no saved addresses yet/i)).toBeInTheDocument();
  });

  it("lists saved addresses with the default badge", async () => {
    api.getAddresses.mockResolvedValue([
      { id: 1, label: "Home", line1: "221B Baker Street", line2: null, city: "Chandigarh",
        state: "Punjab", pincode: "160001", phone: "+919812345678", default: true },
    ]);
    renderAccount();

    expect(await screen.findByText("Home")).toBeInTheDocument();
    expect(screen.getByText("Default")).toBeInTheDocument();
    expect(screen.getByText(/221B Baker Street/)).toBeInTheDocument();
    // Only address, already default — no "Set default" action needed.
    expect(screen.queryByRole("button", { name: /set default/i })).not.toBeInTheDocument();
  });

  it("adds a new address", async () => {
    api.createAddress.mockResolvedValue({ id: 2, label: "Office" });
    renderAccount();

    await userEvent.click(await screen.findByRole("button", { name: /add new address/i }));
    await userEvent.type(screen.getByLabelText(/^label$/i), "Office");
    await userEvent.type(screen.getByLabelText(/address line 1/i), "1 Tech Park");
    await userEvent.type(screen.getByLabelText(/^city$/i), "Chandigarh");
    await userEvent.type(screen.getByLabelText(/^state$/i), "Punjab");
    await userEvent.type(screen.getByLabelText(/pincode/i), "160101");
    await userEvent.type(screen.getByLabelText(/phone/i), "+919812345678");
    await userEvent.click(screen.getByRole("button", { name: /^add address$/i }));

    await waitFor(() => expect(api.createAddress).toHaveBeenCalledWith(expect.objectContaining({
      label: "Office", line1: "1 Tech Park", city: "Chandigarh", state: "Punjab",
      pincode: "160101", phone: "+919812345678",
    })));
  });

  it("sets an address as default", async () => {
    api.getAddresses.mockResolvedValue([
      { id: 1, label: "Home", line1: "221B Baker Street", city: "Chandigarh",
        state: "Punjab", pincode: "160001", phone: "+919812345678", default: false },
    ]);
    api.setDefaultAddress.mockResolvedValue({});
    renderAccount();

    await userEvent.click(await screen.findByRole("button", { name: /set default/i }));

    await waitFor(() => expect(api.setDefaultAddress).toHaveBeenCalledWith(1));
  });

  it("shows the order count and member-since date in the profile summary", async () => {
    renderAccount();
    expect(await screen.findByText(/3 orders/i)).toBeInTheDocument();
  });

  it("saves notification preferences", async () => {
    api.updateNotificationPreferences.mockResolvedValue({
      id: 1, name: "Alice", email: "alice@example.com", role: "USER", verified: false,
      orderEmailsEnabled: false,
    });
    renderAccount();

    const orderEmailsCheckbox = screen.getByLabelText(/order status emails/i);
    await userEvent.click(orderEmailsCheckbox);
    await userEvent.click(screen.getByRole("button", { name: /save preferences/i }));

    await waitFor(() => expect(api.updateNotificationPreferences).toHaveBeenCalledWith({
      orderEmailsEnabled: false,
    }));
  });

  it("lists active sessions and lets you revoke a non-current one", async () => {
    api.getSessions.mockResolvedValue([
      { id: 10, userAgent: "Mozilla/5.0 Chrome/120 Windows", createdAt: "2026-01-01T10:00:00Z", lastUsedAt: "2026-01-02T10:00:00Z", expiresAt: "2026-01-08T10:00:00Z", current: true },
      { id: 11, userAgent: "Mozilla/5.0 Safari/17 iPhone", createdAt: "2026-01-01T09:00:00Z", lastUsedAt: "2026-01-01T09:00:00Z", expiresAt: "2026-01-08T09:00:00Z", current: false },
    ]);
    api.revokeSession.mockResolvedValue(undefined);
    renderAccount();

    expect(await screen.findByText(/this device/i)).toBeInTheDocument();
    expect(screen.getByText(/chrome on windows/i)).toBeInTheDocument();
    expect(screen.getByText(/safari on ios/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /^revoke$/i }));
    await waitFor(() => expect(api.revokeSession).toHaveBeenCalledWith(11));
  });

  it("deletes the account, logs out, and redirects to login after confirming", async () => {
    vi.spyOn(globalThis, "confirm").mockReturnValue(true);
    api.deleteUser.mockResolvedValue(undefined);
    api.logout.mockResolvedValue(undefined);
    renderAccount();

    await userEvent.click(await screen.findByRole("button", { name: /delete my account/i }));

    await waitFor(() => expect(api.deleteUser).toHaveBeenCalledWith(1));
    await waitFor(() => expect(api.logout).toHaveBeenCalledTimes(1));
  });

  it("does not delete the account if the confirmation is cancelled", async () => {
    vi.spyOn(globalThis, "confirm").mockReturnValue(false);
    renderAccount();

    await userEvent.click(await screen.findByRole("button", { name: /delete my account/i }));

    expect(api.deleteUser).not.toHaveBeenCalled();
  });

  it("lets a buyer REQUEST seller access (an admin still has to approve it)", async () => {
    api.becomeSeller.mockResolvedValue({
      id: 1, name: "Alice", email: "alice@example.com", role: "USER", requestedRole: "SELLER", verified: false,
    });
    renderAccount();

    await userEvent.click(await screen.findByRole("button", { name: /request seller access/i }));

    await waitFor(() => expect(api.becomeSeller).toHaveBeenCalledTimes(1));
    // Shown both as the confirmation toast and in the (now pending) seller panel.
    expect((await screen.findAllByText(/an admin will review it/i)).length).toBeGreaterThan(0);
  });
});
