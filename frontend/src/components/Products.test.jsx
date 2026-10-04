import React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import * as api from "../api";
import Products from "./Products";

vi.mock("../api", () => ({ getProducts: vi.fn(), createProduct: vi.fn(), deleteProduct: vi.fn(), addCartItem: vi.fn(), adjustStock: vi.fn() }));
vi.mock("../context/AuthContext", () => ({ useAuth: () => ({ user: { id: 1 }, isAdmin: false, isSeller: false }) }));

const product = { id: 1, name: "Phone", description: "A phone", price: 100, category: "ELECTRONICS", stockQuantity: 5, ownerId: 1 };
function renderProducts() { return render(<MemoryRouter><Products /></MemoryRouter>); }

describe("Products", () => {
  beforeEach(() => { vi.clearAllMocks(); api.getProducts.mockResolvedValue({ content: [product], totalPages: 1, totalElements: 1 }); });

  it("lists products", async () => {
    renderProducts();
    expect(await screen.findByText("Phone")).toBeInTheDocument();
  });

  it("adds a product to the cart", async () => {
    api.addCartItem.mockResolvedValue({});
    renderProducts();
    const user = userEvent.setup();
    await screen.findByText("Phone");
    await user.click(screen.getByRole("button", { name: /add to cart/i }));
    await waitFor(() => expect(api.addCartItem).toHaveBeenCalledWith(1, 1));
  });
});
