import axios from "axios";
import { getStoredAccessToken } from "./context/AuthContext";

// Base URL for backend APIs
// - In Docker/K8s behind the gateway, use the /api prefix so SPA routes such as
//   /products, /orders, /users can coexist with their API paths. The gateway
//   strips /api before forwarding to the backend services.
// - For local Vite dev, vite.config.mjs proxies /api to the gateway at
//   http://localhost:11000, keeping the same API contract as the container build.
const API_BASE = "/api";

const USER_API_BASE = "/users";
const PRODUCT_API_BASE = "/products";
const ORDER_API_BASE = "/orders";

// withCredentials: true on both instances — the refresh token now travels as
// an httpOnly cookie (see UserController), not in the request/response body,
// so the browser must be told to actually send/accept cookies on these
// same-origin (through the gateway) requests. Axios does not do this by
// default even for same-origin calls.
const http = axios.create({ baseURL: API_BASE, withCredentials: true });

// A separate plain instance for the refresh call itself — it must never go
// through the interceptors below, or a failed refresh would try to refresh
// itself and loop forever.
const refreshClient = axios.create({
  baseURL: API_BASE,
  withCredentials: true,
});

http.interceptors.request.use((config) => {
  const token = getStoredAccessToken();
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Shared in-flight refresh promise: if several requests 401 at once (e.g. a
// page that fires 3 requests on load right as the access token expires), they
// all await the same refresh instead of each rotating the refresh token and
// invalidating one another.
let refreshPromise = null;

function performRefresh() {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      // No body: the refresh token rides along automatically as the httpOnly
      // catalogix_refresh_token cookie (withCredentials: true above). If
      // there's no valid cookie, the backend returns 401 and the catch below
      // fires the same forced-logout event as before.
      const res = await refreshClient.post(`${USER_API_BASE}/refresh`);
      // res.data is { accessToken, accessTokenExpiresInMs } — refreshToken is
      // never in the body now; the rotated cookie is set directly by the
      // Set-Cookie header, invisible to and unneeded by this code.
      window.dispatchEvent(
        new CustomEvent("catalogix:tokens-refreshed", { detail: res.data }),
      );
      return res.data.accessToken;
    })().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

http.interceptors.response.use(
  (res) => res,
  async (err) => {
    const { config, response } = err;
    const isAuthEndpoint =
      config?.url?.startsWith(`${USER_API_BASE}/login`) ||
      config?.url?.startsWith(`${USER_API_BASE}/register`) ||
      config?.url?.startsWith(`${USER_API_BASE}/refresh`);

    if (
      response?.status === 401 &&
      config &&
      !config._retry &&
      !isAuthEndpoint
    ) {
      config._retry = true;
      try {
        const newAccessToken = await performRefresh();
        config.headers = config.headers || {};
        config.headers.Authorization = `Bearer ${newAccessToken}`;
        return http(config);
      } catch {
        window.dispatchEvent(new Event("catalogix:unauthorized"));
        throw err;
      }
    }

    if (response?.status === 401 && (isAuthEndpoint || config?._retry)) {
      window.dispatchEvent(new Event("catalogix:unauthorized"));
    }

    throw err;
  },
);

// -------- AUTH APIs --------

export const login = async (email, password) => {
  const res = await http.post(`${USER_API_BASE}/login`, { email, password });
  return res.data; // { accessToken, accessTokenExpiresInMs, user } — refreshToken
  // arrives as an httpOnly Set-Cookie, never in this body.
};

export const register = async (name, email, password) => {
  const res = await http.post(`${USER_API_BASE}/register`, {
    name,
    email,
    password,
  });
  return res.data; // { accessToken, accessTokenExpiresInMs, user } — same cookie note as login.
};

// No body: the cookie set by login/register/refresh is sent automatically
// (withCredentials: true), and the backend reads + revokes + clears it.
export const logout = async () => {
  await http.post(`${USER_API_BASE}/logout`);
};

export const logoutEverywhere = async () => {
  await http.post(`${USER_API_BASE}/logout-all`);
};

export const forgotPassword = async (email) => {
  await http.post(`${USER_API_BASE}/forgot-password`, { email });
};

export const resetPassword = async (token, newPassword) => {
  await http.post(`${USER_API_BASE}/reset-password`, { token, newPassword });
};

export const verifyEmail = async (token) => {
  await http.get(`${USER_API_BASE}/verify-email`, { params: { token } });
};

export const resendVerification = async () => {
  await http.post(`${USER_API_BASE}/resend-verification`);
};

export const updateProfile = async (updates) => {
  const res = await http.patch(`${USER_API_BASE}/me`, updates);
  return res.data;
};

export const getSessions = async () => {
  const res = await http.get(`${USER_API_BASE}/me/sessions`);
  return res.data;
};

export const revokeSession = async (id) => {
  await http.delete(`${USER_API_BASE}/me/sessions/${id}`);
};

export const updateNotificationPreferences = async (preferences) => {
  const res = await http.patch(
    `${USER_API_BASE}/me/notification-preferences`,
    preferences,
  );
  return res.data;
};

// Asks for seller access. This only records a PENDING request (the returned user has
// requestedRole "SELLER" and an unchanged role) — an admin approves or rejects it.
export const becomeSeller = async () => {
  const res = await http.post(`${USER_API_BASE}/me/become-seller`);
  return res.data;
};

// -------- USER APIs (admin directory) --------

export const getUsers = async () => {
  const res = await http.get(USER_API_BASE);
  return res.data;
};

export const deleteUser = async (id) => {
  const res = await http.delete(`${USER_API_BASE}/${id}`);
  return res.data;
};

// Admin only: assign USER / SELLER / ADMIN. This is also how a pending seller request
// is approved (role SELLER) or declined (leave the role alone and use rejectRoleRequest).
export const assignUserRole = async (id, role) => {
  const res = await http.put(`${USER_API_BASE}/${id}/role`, { role });
  return res.data;
};

// Admin only: decline a pending role request without changing the user's role.
export const rejectRoleRequest = async (id) => {
  const res = await http.delete(`${USER_API_BASE}/${id}/role-request`);
  return res.data;
};

// -------- PRODUCT APIs --------

// params: { search, category, minPrice, maxPrice, sortBy, page, size }
// sortBy: "PRICE_LOW_TO_HIGH" | "PRICE_HIGH_TO_LOW" | "NEWEST" | "NAME_A_TO_Z"
export const getProducts = async (params = {}) => {
  const res = await http.get(PRODUCT_API_BASE, { params });
  return res.data; // { content, page, size, totalElements, totalPages }
};

export const getProduct = async (id) => {
  const res = await http.get(`${PRODUCT_API_BASE}/${id}`);
  return res.data;
};

export const createProduct = async (product) => {
  const res = await http.post(PRODUCT_API_BASE, product);
  return res.data;
};

export const deleteProduct = async (id) => {
  const res = await http.delete(`${PRODUCT_API_BASE}/${id}`);
  return res.data;
};

export const adjustStock = async (id, delta) => {
  const res = await http.patch(`${PRODUCT_API_BASE}/${id}/stock`, { delta });
  return res.data;
};

// -------- ADDRESS BOOK APIs --------

const ADDRESS_API_BASE = "/users/me/addresses";

export const getAddresses = async () => {
  const res = await http.get(ADDRESS_API_BASE);
  return res.data; // [{ id, label, line1, line2, city, state, pincode, phone, default }]
};

export const createAddress = async (address) => {
  const res = await http.post(ADDRESS_API_BASE, address);
  return res.data;
};

export const updateAddress = async (id, address) => {
  const res = await http.put(`${ADDRESS_API_BASE}/${id}`, address);
  return res.data;
};

export const setDefaultAddress = async (id) => {
  const res = await http.patch(`${ADDRESS_API_BASE}/${id}/default`);
  return res.data;
};

export const deleteAddress = async (id) => {
  await http.delete(`${ADDRESS_API_BASE}/${id}`);
};

// -------- ORDER APIs --------

// items: [{ productId, quantity }]
// idempotencyKey: optional client-generated UUID; passing the same key for a
// retried "place order" click returns the original order instead of creating
// a duplicate — see checkout-svc's Idempotency-Key header handling.
// addressId: optional — id of a saved address (see ADDRESS APIs above); if
// given, its fields are snapshotted onto the order for shipping/invoicing.
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

// -------- CART APIs (server-side, persists across refresh/devices) --------

const CART_API_BASE = "/cart";

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


// Converts the cart into an order (PENDING_PAYMENT, stock reserved) and
// clears the cart on success — the order still needs payOrder() to complete.
// Lives at /orders/checkout, not /cart/checkout: checkout-svc is the
// orchestrator that actually places the order (talking to catalog-svc,
// inventory-svc), cart-svc just supplies the contents.
// addressId: optional, same as createOrder's.
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

// -------- OUTBOX ADMIN APIs (admin-only) --------
// checkout-svc's compensation outbox — see CompensationOutboxProcessor.
// Only PENDING and DEAD_LETTER entries are ever returned (COMPLETED ones
// are the normal, uninteresting case and aren't surfaced here).

const ADMIN_OUTBOX_API_BASE = "/admin/outbox";

export const getOutboxEntries = async () => {
  const res = await http.get(ADMIN_OUTBOX_API_BASE);
  return res.data; // [{ id, type, productId, delta, reason, status, attempts, lastError, createdAt, updatedAt }]
};

// Resets a DEAD_LETTER entry back to PENDING (attempts=0) so
// CompensationOutboxProcessor picks it up again on its next scheduled run.
export const retryOutboxEntry = async (id) => {
  const res = await http.post(`${ADMIN_OUTBOX_API_BASE}/${id}/retry`);
  return res.data;
};

// -------- NOTIFICATION LOG API (admin-only) --------

const NOTIFICATION_API_BASE = "/notifications";

export const getNotificationLog = async (params = {}) => {
  const res = await http.get(NOTIFICATION_API_BASE, { params });
  return res.data; // { content, page, size, totalElements, totalPages }
};

// -------- MODERATION --------
export const moderateProduct = async (id, status) =>
  (
    await http.patch(`${PRODUCT_API_BASE}/${id}/moderation`, null, {
      params: { status },
    })
  ).data;
