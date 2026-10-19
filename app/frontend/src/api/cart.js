// Server-persisted cart operations.

import { http, CART_API_BASE } from "./client";

export const getCart = async () => {
  const res = await http.get(CART_API_BASE);
  return res.data; // { items, subtotal, total }
};

export const addCartItem = async (productId, quantity) => {
  const res = await http.post(`${CART_API_BASE}/items`, {
    productId,
    quantity,
  });
  return res.data;
};

export const updateCartItemQuantity = async (productId, quantity) => {
  const res = await http.patch(`${CART_API_BASE}/items/${productId}`, {
    quantity,
  });
  return res.data;
};

export const removeCartItem = async (productId) => {
  const res = await http.delete(`${CART_API_BASE}/items/${productId}`);
  return res.data;
};

