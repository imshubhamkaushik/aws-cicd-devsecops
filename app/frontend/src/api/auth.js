// Authentication, email-verification and password-recovery requests.

import { http, USER_API_BASE } from "./client";

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
