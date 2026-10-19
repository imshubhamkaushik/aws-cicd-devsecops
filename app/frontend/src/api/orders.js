// Checkout and order lifecycle requests, including mock payment actions.

import { http, ORDER_API_BASE } from "./client";

// items: [{ productId, quantity }]
// idempotencyKey: reusing it after a transport failure returns the original order.
// addressId: optional saved address whose fields are snapshotted onto the order.
export const createOrder = async (items, idempotencyKey, addressId) => {
  const headers = idempotencyKey
    ? { "Idempotency-Key": idempotencyKey }
    : undefined;
  const res = await http.post(
    ORDER_API_BASE,
    { items, addressId },
    { headers },
  );
  return res.data;
};

export const getOrders = async (params = {}) => {
  const res = await http.get(ORDER_API_BASE, { params });
  return res.data; // { content, page, size, totalElements, totalPages }
};

export const getOrder = async (id) => {
  const res = await http.get(`${ORDER_API_BASE}/${id}`);
  return res.data; // includes shippingAddress (nullable)
};

export const getOrderTracking = async (id) => {
  const res = await http.get(`${ORDER_API_BASE}/${id}/tracking`);
  return res.data; // { orderId, currentStatus, events: [{ status, note, createdAt }] }
};

export const getOrderInvoice = async (id) => {
  const res = await http.get(`${ORDER_API_BASE}/${id}/invoice`);
  return res.data; // { invoiceNumber, items, taxableValue, taxAmount, totalAmount, ... }
};

export const cancelOrder = async (id) => {
  const res = await http.patch(`${ORDER_API_BASE}/${id}/cancel`);
  return res.data;
};

// method: "CARD" | "UPI" | "COD". cardLast4 "0000" always declines (mock);
// upiId starting with "fail@" always declines (mock). Neither is needed for COD.
// idempotencyKey is reused when the browser retries the same payment request,
// preventing a payment-service timeout from creating a second payment.

export const payOrder = async (
  id,
  method,
  cardLast4,
  upiId,
  idempotencyKey,
) => {
  const headers = idempotencyKey
    ? { "Idempotency-Key": idempotencyKey }
    : undefined;
  const res = await http.post(
    `${ORDER_API_BASE}/${id}/pay`,
    { method, cardLast4, upiId },
    { headers },
  );
  return res.data; // { order, payment }
};

// Admin-only: CONFIRMED -> SHIPPED -> DELIVERED.

export const updateOrderStatus = async (id, status) => {
  const res = await http.patch(`${ORDER_API_BASE}/${id}/status`, { status });
  return res.data;
};

export const checkoutCart = async (idempotencyKey, addressId) => {
  const headers = idempotencyKey
    ? { "Idempotency-Key": idempotencyKey }
    : undefined;
  const res = await http.post(
    `${ORDER_API_BASE}/checkout`,
    { addressId },
    { headers },
  );
  return res.data;
};

