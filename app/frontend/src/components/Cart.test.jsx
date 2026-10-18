import React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import * as api from "../api";
import Cart from "./Cart";

vi.mock("../api", () => ({
  getProducts: vi.fn(), getCart: vi.fn(), addCartItem: vi.fn(),
  updateCartItemQuantity: vi.fn(), removeCartItem: vi.fn(), checkoutCart: vi.fn(),
}));

const cart = (items = []) => ({ items, subtotal: items.reduce((n, x) => n + x.subtotal, 0), total: items.reduce((n, x) => n + x.subtotal, 0) });

function renderCart() { return render(<MemoryRouter><Cart /></MemoryRouter>); }

describe("Cart", () => {
  beforeEach(() => { vi.clearAllMocks(); api.getCart.mockResolvedValue(cart()); api.getProducts.mockResolvedValue({ content: [] }); });

  it("loads the current cart", async () => { renderCart(); await waitFor(() => expect(api.getCart).toHaveBeenCalled()); expect(screen.getByText("Your cart", { exact: true })).toBeInTheDocument(); });

  it("adds a selected product", async () => {
    const product = { id: 1, name: "Phone", price: 100, stockQuantity: 5 };
    api.getProducts.mockResolvedValue({ content: [product] });
    api.addCartItem.mockResolvedValue(cart([{ productId: 1, productName: "Phone", quantity: 1, subtotal: 100 }]));
    renderCart();
    const user = userEvent.setup();
    await user.type(screen.getByPlaceholderText(/search products to add/i), "Phone");
    await waitFor(() => expect(screen.getByRole("button", { name: /Phone/i })).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /Phone/i }));
    await waitFor(() => expect(api.addCartItem).toHaveBeenCalledWith(1, 1));
  });

  it("updates and removes cart lines", async () => {
    const item = { productId: 1, productName: "Phone", quantity: 2, subtotal: 200 };
    api.getCart.mockResolvedValue(cart([item]));
    api.updateCartItemQuantity.mockResolvedValue(cart([{ ...item, quantity: 3, subtotal: 300 }]));
    api.removeCartItem.mockResolvedValue(cart());
    renderCart();
    await screen.findByText("Phone");
    const user = userEvent.setup();
    const qty = screen.getByDisplayValue("2");
    await user.clear(qty);
    await user.type(qty, "3");
    await user.tab();
    await waitFor(() => expect(api.updateCartItemQuantity).toHaveBeenCalledWith(1, 3));
    await user.click(screen.getByTitle("Remove"));
    await waitFor(() => expect(api.removeCartItem).toHaveBeenCalledWith(1));
  });

  it("starts checkout with a client idempotency key", async () => {
    api.getCart.mockResolvedValue(cart([{ productId: 1, productName: "Phone", quantity: 1, subtotal: 100 }]));
    api.checkoutCart.mockResolvedValue({});
    renderCart();
    await screen.findByText("Phone");
    await userEvent.setup().click(screen.getByRole("button", { name: "Checkout" }));
    await waitFor(() => expect(api.checkoutCart).toHaveBeenCalledWith(expect.any(String)));
  });
});
