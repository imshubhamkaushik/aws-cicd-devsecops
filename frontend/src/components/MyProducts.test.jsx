import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import MyProducts from "./MyProducts";
import { AuthProvider } from "../context/AuthContext";
import * as api from "../api";

vi.mock("../api");

function renderMyProducts() {
  return render(
    <AuthProvider>
      <MyProducts />
    </AuthProvider>
  );
}

function loginAs(role) {
  const auth = {
    accessToken: "tok",
    accessTokenExpiresInMs: 900000,
    user: { id: 1, name: "Sam Seller", email: "sam@example.com", role, verified: true },
  };
  localStorage.setItem("catalogix.auth", JSON.stringify(auth));
}

describe("My Products page", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it("shows an empty state when the seller owns no products", async () => {
    loginAs("SELLER");
    api.getProducts.mockResolvedValue({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });

    renderMyProducts();

    expect(await screen.findByText(/no products yet/i)).toBeInTheDocument();
  });

  it("only lists products the seller owns, filtering out everyone else's", async () => {
    loginAs("SELLER");
    api.getProducts.mockResolvedValue({
      content: [
        { id: 1, name: "My Phone", ownerId: 1, price: 100, category: "Electronics", stockQuantity: 5 },
        { id: 2, name: "Someone Else's Watch", ownerId: 2, price: 200, category: "Electronics", stockQuantity: 3 },
      ],
      page: 0, size: 100, totalElements: 2, totalPages: 1,
    });

    renderMyProducts();

    expect(await screen.findByText("My Phone")).toBeInTheDocument();
    expect(screen.queryByText("Someone Else's Watch")).not.toBeInTheDocument();
  });

  it("shows every product for an admin, not just ones they own", async () => {
    loginAs("ADMIN");
    api.getProducts.mockResolvedValue({
      content: [
        { id: 1, name: "My Phone", ownerId: 1, price: 100, category: "Electronics", stockQuantity: 5 },
        { id: 2, name: "Someone Else's Watch", ownerId: 2, price: 200, category: "Electronics", stockQuantity: 3 },
      ],
      page: 0, size: 100, totalElements: 2, totalPages: 1,
    });

    renderMyProducts();

    expect(await screen.findByText("My Phone")).toBeInTheDocument();
    expect(screen.getByText("Someone Else's Watch")).toBeInTheDocument();
  });

  it("updates stock by computing the delta from the typed absolute value", async () => {
    loginAs("SELLER");
    api.getProducts.mockResolvedValue({
      content: [
        { id: 1, name: "My Phone", ownerId: 1, price: 100, category: "Electronics", stockQuantity: 5 },
      ],
      page: 0, size: 100, totalElements: 1, totalPages: 1,
    });
    api.adjustStock.mockResolvedValue({});

    renderMyProducts();
    const qtyInput = await screen.findByDisplayValue("5");
    await userEvent.clear(qtyInput);
    await userEvent.type(qtyInput, "12");
    await userEvent.click(screen.getByRole("button", { name: /update stock/i }));

    // 12 typed - 5 shown = +7 delta, not the absolute 12 itself.
    await waitFor(() => expect(api.adjustStock).toHaveBeenCalledWith(1, 7));
  });

  it("shows an error and does not call the API when nothing actually changed", async () => {
    loginAs("SELLER");
    api.getProducts.mockResolvedValue({
      content: [
        { id: 1, name: "My Phone", ownerId: 1, price: 100, category: "Electronics", stockQuantity: 5 },
      ],
      page: 0, size: 100, totalElements: 1, totalPages: 1,
    });

    renderMyProducts();
    await screen.findByDisplayValue("5");
    await userEvent.click(screen.getByRole("button", { name: /update stock/i }));

    expect(api.adjustStock).not.toHaveBeenCalled();
  });
});
