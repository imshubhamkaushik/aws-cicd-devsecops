import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";
import ForgotPassword from "./ForgotPassword";
import * as api from "../api";

vi.mock("../api");

function renderPage() {
  return render(
    <MemoryRouter>
      <ForgotPassword />
    </MemoryRouter>
  );
}

describe("ForgotPassword", () => {
  beforeEach(() => vi.clearAllMocks());

  it("submits the entered email", async () => {
    api.forgotPassword.mockResolvedValue(undefined);
    renderPage();

    await userEvent.type(screen.getByLabelText(/email address/i), "someone@example.com");
    await userEvent.click(screen.getByRole("button", { name: /send reset link/i }));

    await waitFor(() => expect(api.forgotPassword).toHaveBeenCalledWith("someone@example.com"));
  });

  it("shows the same generic success message whether or not the account exists", async () => {
    api.forgotPassword.mockResolvedValue(undefined);
    renderPage();

    await userEvent.type(screen.getByLabelText(/email address/i), "maybe-not-real@example.com");
    await userEvent.click(screen.getByRole("button", { name: /send reset link/i }));

    expect(await screen.findByText(/we've sent a link/i)).toBeInTheDocument();
  });
});
