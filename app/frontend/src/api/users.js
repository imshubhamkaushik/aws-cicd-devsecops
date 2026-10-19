// Profile, session, role-management and address-book requests.

import { http, USER_API_BASE, ADDRESS_API_BASE } from "./client";

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

