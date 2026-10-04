import React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ProductDetail from "./ProductDetail";

const product = {
  id: 1,
  name: "Phone",
  description: "A phone",
  price: 100,
  category: "ELECTRONICS",
  stockQuantity: 4,
};

describe("ProductDetail", () => {
  it("renders the supplied product details", async () => {
    const onClose = vi.fn();
    render(<ProductDetail product={product} onClose={onClose} />);

    expect(
      screen.getByRole("heading", { name: "Phone" }),
    ).toBeInTheDocument();
    expect(screen.getByText("A phone")).toBeInTheDocument();
    expect(screen.getByText("₹100")).toBeInTheDocument();
    expect(screen.getByText("4 in stock")).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();

    await userEvent.setup().click(
      screen.getByRole("button", { name: "Close product details" }),
    );
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("closes through the explicit footer close action", async () => {
    const onClose = vi.fn();
    render(<ProductDetail product={product} onClose={onClose} />);

    expect(onClose).not.toHaveBeenCalled();

    await userEvent.setup().click(
      screen.getByRole("button", { name: "Close", exact: true }),
    );
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
