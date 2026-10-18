import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import Orders from "./Orders";
import { AuthProvider } from "../context/AuthContext";
import * as api from "../api";

vi.mock("../api");

function renderOrders() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <Orders />
      </AuthProvider>
    </MemoryRouter>
  );
}

describe("Orders page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getOrders.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it("shows an empty state when there are no past orders", async () => {
    renderOrders();
    expect(await screen.findByText(/no orders yet/i)).toBeInTheDocument();
  });

  it("shows a payment form for a pending-payment order and completes a card payment", async () => {
    api.getOrders.mockResolvedValue({
      content: [{
        id: 7, userId: 1, status: "PENDING_PAYMENT", totalAmount: 100,
        createdAt: new Date().toISOString(),
        items: [{ productId: 1, productName: "Phone", quantity: 1, unitPrice: 100, subtotal: 100 }],
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    api.payOrder.mockResolvedValue({
      order: { id: 7, status: "CONFIRMED", paymentMethod: "CARD" },
      payment: { status: "SUCCEEDED" },
    });

    renderOrders();
    await userEvent.click(await screen.findByRole("button", { name: /^pay/i }));

    await waitFor(() => expect(api.payOrder).toHaveBeenCalledWith(7, "CARD", "4242", undefined, expect.any(String)));
    expect(await screen.findByText(/payment successful/i)).toBeInTheDocument();
  });

  it("pays via UPI when that method is selected", async () => {
    api.getOrders.mockResolvedValue({
      content: [{
        id: 7, userId: 1, status: "PENDING_PAYMENT", totalAmount: 100,
        createdAt: new Date().toISOString(),
        items: [{ productId: 1, productName: "Phone", quantity: 1, unitPrice: 100, subtotal: 100 }],
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    api.payOrder.mockResolvedValue({
      order: { id: 7, status: "CONFIRMED", paymentMethod: "UPI" },
      payment: { status: "SUCCEEDED" },
    });

    renderOrders();
    await userEvent.selectOptions(await screen.findByDisplayValue(/^card$/i), "UPI");
    await userEvent.click(screen.getByRole("button", { name: /^pay/i }));

    await waitFor(() => expect(api.payOrder).toHaveBeenCalledWith(7, "UPI", undefined, "buyer@upi", expect.any(String)));
  });

  it("confirms a COD order without claiming a payment happened", async () => {
    api.getOrders.mockResolvedValue({
      content: [{
        id: 7, userId: 1, status: "PENDING_PAYMENT", totalAmount: 100,
        createdAt: new Date().toISOString(),
        items: [{ productId: 1, productName: "Phone", quantity: 1, unitPrice: 100, subtotal: 100 }],
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    api.payOrder.mockResolvedValue({
      order: { id: 7, status: "CONFIRMED", paymentMethod: "COD" },
      payment: { status: "SUCCEEDED" },
    });

    renderOrders();
    await userEvent.selectOptions(await screen.findByDisplayValue(/^card$/i), "COD");
    await userEvent.click(screen.getByRole("button", { name: /confirm.*on delivery/i }));

    await waitFor(() => expect(api.payOrder).toHaveBeenCalledWith(7, "COD", undefined, undefined, expect.any(String)));
    expect(await screen.findByText(/pay in cash when it arrives/i)).toBeInTheDocument();
  });

  it("shows a cancel button for cancellable orders and calls the API", async () => {
    api.getOrders.mockResolvedValue({
      content: [{
        id: 8, userId: 1, status: "CONFIRMED", totalAmount: 200,
        createdAt: new Date().toISOString(),
        items: [{ productId: 1, productName: "Phone", quantity: 2, unitPrice: 100, subtotal: 200 }],
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    api.cancelOrder.mockResolvedValue({});
    vi.stubGlobal("confirm", () => true);

    renderOrders();
    const cancelBtn = await screen.findByRole("button", { name: /cancel/i });
    await userEvent.click(cancelBtn);

    await waitFor(() => expect(api.cancelOrder).toHaveBeenCalledWith(8));
  });

  it("shows and hides the tracking timeline for an order", async () => {
    api.getOrders.mockResolvedValue({
      content: [{
        id: 9, userId: 1, status: "SHIPPED", totalAmount: 100,
        createdAt: new Date().toISOString(),
        items: [{ productId: 1, productName: "Phone", quantity: 1, unitPrice: 100, subtotal: 100 }],
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    api.getOrderTracking.mockResolvedValue({
      orderId: 9, currentStatus: "SHIPPED",
      events: [
        { status: "PENDING_PAYMENT", note: "Order placed", createdAt: new Date().toISOString() },
        { status: "CONFIRMED", note: "Payment confirmed", createdAt: new Date().toISOString() },
        { status: "SHIPPED", note: "Order shipped", createdAt: new Date().toISOString() },
      ],
    });

    renderOrders();
    await userEvent.click(await screen.findByRole("button", { name: /^track order$/i }));

    await waitFor(() => expect(api.getOrderTracking).toHaveBeenCalledWith(9));
    expect(await screen.findByText("Order shipped")).toBeInTheDocument();
    expect(screen.getByText(/^CONFIRMED$/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /hide tracking/i }));
    expect(screen.queryByText("Order shipped")).not.toBeInTheDocument();
  });

  it("does not offer an invoice for an order that hasn't been paid", async () => {
    api.getOrders.mockResolvedValue({
      content: [{
        id: 10, userId: 1, status: "PENDING_PAYMENT", totalAmount: 100,
        createdAt: new Date().toISOString(),
        items: [{ productId: 1, productName: "Phone", quantity: 1, unitPrice: 100, subtotal: 100 }],
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });

    renderOrders();
    await screen.findByText(/order #10/i);
    expect(screen.queryByRole("button", { name: /^invoice$/i })).not.toBeInTheDocument();
  });

  it("opens and closes the invoice modal for a paid order", async () => {
    api.getOrders.mockResolvedValue({
      content: [{
        id: 11, userId: 1, status: "CONFIRMED", totalAmount: 118,
        createdAt: new Date().toISOString(),
        items: [{ productId: 1, productName: "Phone", quantity: 1, unitPrice: 100, subtotal: 100 }],
      }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    api.getOrderInvoice.mockResolvedValue({
      invoiceNumber: "INV-00000011", orderId: 11, orderDate: new Date().toISOString(),
      sellerName: "Catalogix Retail Pvt. Ltd.", customerEmail: "buyer@example.com",
      billingAddress: null,
      items: [{ productName: "Phone", quantity: 1, unitPrice: 100, subtotal: 100 }],
      itemsSubtotal: 100, taxableValue: 100, taxRatePercent: 18, taxAmount: 18,
      totalAmount: 118, paymentMethod: "CARD", paymentReference: "MOCK-CARD-abc",
    });

    renderOrders();
    await userEvent.click(await screen.findByRole("button", { name: /^invoice$/i }));

    await waitFor(() => expect(api.getOrderInvoice).toHaveBeenCalledWith(11));
    expect(await screen.findByText("INV-00000011")).toBeInTheDocument();

    const dialog = screen.getByRole("dialog", {
      name: "Invoice INV-00000011",
    });

    await userEvent.click(
      within(dialog).getByRole("button", {
        name: "Close",
        exact: true,
      }),
    );
    expect(screen.queryByText("INV-00000011")).not.toBeInTheDocument();
  });

;

;

;
});
