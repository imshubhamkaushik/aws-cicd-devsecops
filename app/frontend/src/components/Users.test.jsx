import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import * as api from "../api";
import Users from "./Users";

vi.mock("../api");
vi.mock("../context/AuthContext", () => ({
  useAuth: () => ({ user: { id: 1, name: "Root", role: "ADMIN" } }),
}));

const USERS = [
  { id: 1, name: "Root Admin", email: "root@example.com", role: "ADMIN" },
  { id: 2, name: "Bob Buyer", email: "bob@example.com", role: "USER", requestedRole: "SELLER" },
  { id: 3, name: "Cara Customer", email: "cara@example.com", role: "USER" },
];

describe("Users (admin role management)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getUsers.mockResolvedValue(USERS);
    api.assignUserRole.mockResolvedValue({});
    api.rejectRoleRequest.mockResolvedValue({});
  });

  it("shows pending seller requests with approve / decline actions", async () => {
    render(<Users />);

    expect(await screen.findByText(/requests seller/i)).toBeInTheDocument();
    expect(screen.getByText(/1 pending request/i)).toBeInTheDocument();
  });

  it("approving a request assigns the requested role", async () => {
    render(<Users />);

    await userEvent.click(await screen.findByRole("button", { name: /approve/i }));

    await waitFor(() => expect(api.assignUserRole).toHaveBeenCalledWith(2, "SELLER"));
  });

  it("declining a request leaves the role alone", async () => {
    render(<Users />);

    await userEvent.click(await screen.findByRole("button", { name: /decline/i }));

    await waitFor(() => expect(api.rejectRoleRequest).toHaveBeenCalledWith(2));
    expect(api.assignUserRole).not.toHaveBeenCalled();
  });

  it("lets an admin change any other user's role, but not their own", async () => {
    render(<Users />);

    const cara = await screen.findByRole("combobox", { name: /role of cara customer/i });
    await userEvent.selectOptions(cara, "SELLER");

    await waitFor(() => expect(api.assignUserRole).toHaveBeenCalledWith(3, "SELLER"));
    expect(screen.queryByRole("combobox", { name: /role of root admin/i })).not.toBeInTheDocument();
  });
});
