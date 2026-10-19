import axios from "axios";
import { getStoredAccessToken } from "../context/AuthContext";

// Base URL for backend APIs
// - In Docker/K8s behind the gateway, use the /api prefix so SPA routes such as
//   /products, /orders, /users can coexist with their API paths. The gateway
//   strips /api before forwarding to the backend services.
// - For local Vite dev, vite.config.mjs proxies /api to the gateway at
//   http://localhost:11000, keeping the same API contract as the container build.
const API_BASE = "/api";

export const USER_API_BASE = "/users";
export const PRODUCT_API_BASE = "/products";
export const ORDER_API_BASE = "/orders";

// withCredentials: true on both instances — the refresh token now travels as
// an httpOnly cookie (see UserController), not in the request/response body,
// so the browser must be told to actually send/accept cookies on these
// same-origin (through the gateway) requests. Axios does not do this by
// default even for same-origin calls.
export const http = axios.create({ baseURL: API_BASE, withCredentials: true });

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


export const ADDRESS_API_BASE = "/users/me/addresses";
export const CART_API_BASE = "/cart";
export const ADMIN_OUTBOX_API_BASE = "/admin/outbox";
export const NOTIFICATION_API_BASE = "/notifications";
