import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import OutboxAdmin from "./OutboxAdmin";
import { AuthProvider } from "../context/AuthContext";
import * as api from "../api";

vi.mock("../api");

function seedUser(role = "ADMIN") {
  localStorage.setItem("catalogix.auth", JSON.stringify({
    accessToken: "tok", accessTokenExpiresInMs: 900000,
    user: { id: 1, name: "Admin", email: "admin@example.com", role, verified: true },
  }));
}

function renderOutboxAdmin() {
  return render(
    <AuthProvider>
      <OutboxAdmin />
    </AuthProvider>
  );
}

// (confirmed against the enum in backend/checkout-svc — see
// describeEntry's own comment in OutboxAdmin.jsx for why REFUND_PAYMENT
// isn't tested here, it doesn't exist).
const STOCK_ENTRY = {
  id: 12, type: "RELEASE_STOCK", productId: 77, delta: 2,
  reason: null, status: "DEAD_LETTER", attempts: 5, lastError: "inventory-svc unreachable",
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T01:00:00Z",
};


describe("OutboxAdmin page", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    seedUser();
  });

  it("shows an empty state when nothing is stuck", async () => {
    api.getOutboxEntries.mockResolvedValue([]);
    renderOutboxAdmin();
    expect(await screen.findByText(/nothing stuck/i)).toBeInTheDocument();
  });

  it("describes a RELEASE_STOCK entry using its productId and delta", async () => {
    api.getOutboxEntries.mockResolvedValue([STOCK_ENTRY]);
    renderOutboxAdmin();
    expect(await screen.findByText(/release 2 unit\(s\) of product #77/i)).toBeInTheDocument();
    expect(screen.getByText("Dead letter")).toBeInTheDocument();
    expect(screen.getByText(/inventory-svc unreachable/i)).toBeInTheDocument();
  });

  it("only offers a retry button for DEAD_LETTER entries, not PENDING ones", async () => {
    api.getOutboxEntries.mockResolvedValue([STOCK_ENTRY]);
    renderOutboxAdmin();
    await screen.findByText(/release 2 unit\(s\)/i);
    // Exactly one retry ("Retry"-titled) icon button — for the DEAD_LETTER
    // entry only, not the PENDING one.
    expect(screen.getAllByTitle(/retry/i)).toHaveLength(1);
  });

  it("retries a dead-lettered entry after confirmation", async () => {
    api.getOutboxEntries.mockResolvedValue([STOCK_ENTRY]);
    api.retryOutboxEntry.mockResolvedValue({});
    vi.stubGlobal("confirm", () => true);

    renderOutboxAdmin();
    await userEvent.click(await screen.findByTitle(/retry/i));

    await waitFor(() => expect(api.retryOutboxEntry).toHaveBeenCalledWith(12));
  });

  it("does not retry if the confirmation is declined", async () => {
    api.getOutboxEntries.mockResolvedValue([STOCK_ENTRY]);
    vi.stubGlobal("confirm", () => false);

    renderOutboxAdmin();
    await userEvent.click(await screen.findByTitle(/retry/i));

    expect(api.retryOutboxEntry).not.toHaveBeenCalled();
  });

  it("shows an error message if loading entries fails", async () => {
    api.getOutboxEntries.mockRejectedValue(new Error("network error"));
    renderOutboxAdmin();
    expect(await screen.findByText(/failed to load outbox entries/i)).toBeInTheDocument();
  });
});
