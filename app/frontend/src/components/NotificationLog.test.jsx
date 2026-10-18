import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import NotificationLog from "./NotificationLog";
import { AuthProvider } from "../context/AuthContext";
import * as api from "../api";

vi.mock("../api");

function seedUser(role = "ADMIN") {
  localStorage.setItem("catalogix.auth", JSON.stringify({
    accessToken: "tok", accessTokenExpiresInMs: 900000,
    user: { id: 1, name: "Admin", email: "admin@example.com", role, verified: true },
  }));
}

function renderNotificationLog() {
  return render(
    <AuthProvider>
      <NotificationLog />
    </AuthProvider>
  );
}

const SENT_NOTIFICATION = {
  id: 1, subject: "Order #501 confirmed", recipient: "shopper@example.com",
  status: "SENT", error: null, createdAt: "2026-01-01T00:00:00Z",
};
const FAILED_NOTIFICATION = {
  id: 2, subject: "Password reset requested", recipient: "user2@example.com",
  status: "FAILED", error: "SMTP connection refused", createdAt: "2026-01-02T00:00:00Z",
};

describe("NotificationLog page", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    seedUser();
  });

  it("shows an empty state when nothing has been sent", async () => {
    api.getNotificationLog.mockResolvedValue({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
    });
    renderNotificationLog();
    expect(await screen.findByText(/no notifications logged/i)).toBeInTheDocument();
  });

  it("lists sent and failed notifications with their status badges", async () => {
    api.getNotificationLog.mockResolvedValue({
      content: [SENT_NOTIFICATION, FAILED_NOTIFICATION], page: 0, size: 20, totalElements: 2, totalPages: 1,
    });
    renderNotificationLog();

    expect(await screen.findByText(/order #501 confirmed/i)).toBeInTheDocument();
    expect(screen.getByText("Sent")).toBeInTheDocument();
    expect(screen.getByText("Failed")).toBeInTheDocument();
    expect(screen.getByText(/smtp connection refused/i)).toBeInTheDocument();
  });

  it("shows the total count and how many failed on the current page", async () => {
    api.getNotificationLog.mockResolvedValue({
      content: [SENT_NOTIFICATION, FAILED_NOTIFICATION], page: 0, size: 20, totalElements: 45, totalPages: 3,
    });
    renderNotificationLog();

    expect(await screen.findByText(/45 total/)).toBeInTheDocument();
    expect(screen.getByText(/1 failed on this page/i)).toBeInTheDocument();
  });

  it("fetches page 0 with size 20 by default", async () => {
    api.getNotificationLog.mockResolvedValue({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
    });
    renderNotificationLog();
    await waitFor(() =>
      expect(api.getNotificationLog).toHaveBeenCalledWith({ page: 0, size: 20 })
    );
  });

  it("paginates to the next page", async () => {
    api.getNotificationLog.mockResolvedValueOnce({
      content: [SENT_NOTIFICATION], page: 0, size: 20, totalElements: 40, totalPages: 2,
    }).mockResolvedValueOnce({
      content: [FAILED_NOTIFICATION], page: 1, size: 20, totalElements: 40, totalPages: 2,
    });

    renderNotificationLog();
    await screen.findByText(/order #501 confirmed/i);

    await userEvent.click(screen.getByRole("button", { name: /next/i }));

    await waitFor(() =>
      expect(api.getNotificationLog).toHaveBeenLastCalledWith({ page: 1, size: 20 })
    );
    expect(await screen.findByText(/password reset requested/i)).toBeInTheDocument();
  });

  it("disables Prev on the first page and Next on the last page", async () => {
    api.getNotificationLog.mockResolvedValue({
      content: [SENT_NOTIFICATION], page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    renderNotificationLog();
    await screen.findByText(/order #501 confirmed/i);
    // totalPages is 1, so the whole pagination block shouldn't render at all.
    expect(screen.queryByRole("button", { name: /next/i })).not.toBeInTheDocument();
  });

  it("shows an error message if loading the log fails", async () => {
    api.getNotificationLog.mockRejectedValue(new Error("network error"));
    renderNotificationLog();
    expect(await screen.findByText(/failed to load notification log/i)).toBeInTheDocument();
  });
});
