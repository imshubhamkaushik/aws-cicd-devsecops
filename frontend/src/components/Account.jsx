import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import {
  updateProfile,
  resendVerification,
  logoutEverywhere,
  getAddresses,
  createAddress,
  updateAddress,
  setDefaultAddress,
  deleteAddress,
  getOrders,
  getSessions,
  revokeSession,
  updateNotificationPreferences,
  deleteUser,
  becomeSeller,
} from "../api";

const EMPTY_ADDRESS_FORM = {
  label: "",
  line1: "",
  line2: "",
  city: "",
  state: "",
  pincode: "",
  phone: "",
  makeDefault: false,
};

function formatDate(value) {
  return new Date(value).toLocaleString("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

// Lightweight, dependency-free User-Agent summary — not a full parser, just
// enough to tell sessions apart in a list ("Chrome on macOS" beats a raw
// 150-character UA string). Falls back to the raw string if nothing matches.
function describeUserAgent(userAgent) {
  if (!userAgent) return "Unknown device";

  let browser = "Unknown browser";
  if (userAgent.includes("Edg/")) browser = "Edge";
  else if (userAgent.includes("Chrome/")) browser = "Chrome";
  else if (userAgent.includes("Firefox/")) browser = "Firefox";
  else if (userAgent.includes("Safari/")) browser = "Safari";

  let os = "Unknown OS";
  if (userAgent.includes("Windows")) os = "Windows";
  else if (userAgent.includes("Mac OS")) os = "macOS";
  else if (userAgent.includes("Android")) os = "Android";
  else if (userAgent.includes("iPhone") || userAgent.includes("iPad"))
    os = "iOS";
  else if (userAgent.includes("Linux")) os = "Linux";

  return `${browser} on ${os}`;
}

export default function Account() {
  const { user, updateUser, logout } = useAuth();
  const navigate = useNavigate();

  const [becomingSeller, setBecomingSeller] = useState(false);

  const handleBecomeSeller = async () => {
    setBecomingSeller(true);
    setError("");
    try {
      const updated = await becomeSeller();
      updateUser(updated);
      setToast(
        "Request sent — an admin will review it. You'll be able to list products once it is approved.",
      );
    } catch {
      setError("Failed to send the request. Please try again.");
    } finally {
      setBecomingSeller(false);
    }
  };

  const [name, setName] = useState(user?.name || "");
  const [email, setEmail] = useState(user?.email || "");
  const [newPassword, setNewPassword] = useState("");
  const [currentPassword, setCurrentPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");
  const [resending, setResending] = useState(false);

  // ---- Profile summary ----
  const [orderCount, setOrderCount] = useState(null);

  useEffect(() => {
    getOrders({ page: 0, size: 1 })
      .then((data) => setOrderCount(data.totalElements ?? 0))
      .catch(() => setOrderCount(null)); // summary is a nice-to-have, never blocks the page on failure
  }, []);

  // ---- Notification preferences ----
  const [orderEmailsEnabled, setOrderEmailsEnabled] = useState(
    user?.orderEmailsEnabled ?? true,
  );
  const [prefsSubmitting, setPrefsSubmitting] = useState(false);

  const handleSavePreferences = async () => {
    setPrefsSubmitting(true);
    setError("");
    try {
      const updated = await updateNotificationPreferences({
        orderEmailsEnabled,
      });
      updateUser(updated);
      setToast("Notification preferences saved.");
    } catch {
      setError("Failed to save notification preferences.");
    } finally {
      setPrefsSubmitting(false);
    }
  };

  // ---- Sessions ----
  const [sessions, setSessions] = useState([]);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [revokingId, setRevokingId] = useState(null);

  const fetchSessions = async () => {
    setSessionsLoading(true);
    try {
      setSessions(await getSessions());
    } catch {
      setError("Failed to load your sessions.");
    } finally {
      setSessionsLoading(false);
    }
  };

  useEffect(() => {
    void fetchSessions();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleRevokeSession = async (id) => {
    setRevokingId(id);
    try {
      await revokeSession(id);
      await fetchSessions();
    } catch {
      setError("Failed to revoke that session.");
    } finally {
      setRevokingId(null);
    }
  };

  // ---- Account deletion ----
  const [deletingAccount, setDeletingAccount] = useState(false);

  const handleDeleteAccount = async () => {
    if (
      !globalThis.confirm(
        "Delete your account permanently? This cannot be undone.",
      )
    )
      return;
    setDeletingAccount(true);
    setError("");
    try {
      await deleteUser(user.id);
      await logout();
      await navigate("/login", { replace: true });
    } catch {
      setError("Failed to delete your account. Please try again.");
      setDeletingAccount(false);
    }
  };

  // ---- Address book ----
  const [addresses, setAddresses] = useState([]);
  const [addressesLoading, setAddressesLoading] = useState(false);
  const [addressForm, setAddressForm] = useState(EMPTY_ADDRESS_FORM);
  const [editingAddressId, setEditingAddressId] = useState(null);
  const [showAddressForm, setShowAddressForm] = useState(false);
  const [addressSubmitting, setAddressSubmitting] = useState(false);
  const [addressBusyId, setAddressBusyId] = useState(null);

  const fetchAddresses = async () => {
    setAddressesLoading(true);
    try {
      const data = await getAddresses();
      setAddresses(data || []);
    } catch {
      setError("Failed to load your saved addresses.");
    } finally {
      setAddressesLoading(false);
    }
  };

  useEffect(() => {
    void fetchAddresses();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const openAddAddressForm = () => {
    setAddressForm(EMPTY_ADDRESS_FORM);
    setEditingAddressId(null);
    setShowAddressForm(true);
  };

  const openEditAddressForm = (address) => {
    setAddressForm({
      label: address.label,
      line1: address.line1,
      line2: address.line2 || "",
      city: address.city,
      state: address.state,
      pincode: address.pincode,
      phone: address.phone,
      makeDefault: false,
    });
    setEditingAddressId(address.id);
    setShowAddressForm(true);
  };

  const handleAddressSubmit = async (e) => {
    e.preventDefault();
    setAddressSubmitting(true);
    setError("");
    try {
      if (editingAddressId) {
        await updateAddress(editingAddressId, addressForm);
        setToast("Address updated.");
      } else {
        await createAddress(addressForm);
        setToast("Address added.");
      }
      setShowAddressForm(false);
      await fetchAddresses();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to save address.");
    } finally {
      setAddressSubmitting(false);
    }
  };

  const handleSetDefaultAddress = async (id) => {
    setAddressBusyId(id);
    try {
      await setDefaultAddress(id);
      await fetchAddresses();
    } catch {
      setError("Failed to set default address.");
    } finally {
      setAddressBusyId(null);
    }
  };

  const handleDeleteAddress = async (id) => {
    if (!globalThis.confirm("Delete this address?")) return;
    setAddressBusyId(id);
    try {
      await deleteAddress(id);
      await fetchAddresses();
    } catch {
      setError("Failed to delete address.");
    } finally {
      setAddressBusyId(null);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      const updates = { name };
      if (email.trim() !== user?.email) updates.email = email.trim();
      if (newPassword) updates.newPassword = newPassword;
      if (updates.email || updates.newPassword)
        updates.currentPassword = currentPassword;

      const updated = await updateProfile(updates);
      updateUser(updated);
      setNewPassword("");
      setCurrentPassword("");
      setToast("Profile updated.");
    } catch (err) {
      setError(err.response?.data?.message || "Failed to update profile.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleResend = async () => {
    setResending(true);
    try {
      await resendVerification();
      setToast("Verification email sent — check Mailpit / your inbox.");
    } catch {
      setError("Failed to resend verification email.");
    } finally {
      setResending(false);
    }
  };

  const handleLogoutEverywhere = async () => {
    if (
      !globalThis.confirm("Log out of every device/session for this account?")
    )
      return;
    try {
      await logoutEverywhere();
    } finally {
      await logout();
    }
  };

  let addressSubmitLabel = "Add address";

  if (editingAddressId) {
    addressSubmitLabel = "Update address";
  }

  if (addressSubmitting) {
    addressSubmitLabel = "Saving…";
  }

  return (
    <div className="page-wrapper">
      <div className="topbar">
        <div>
          <h1 className="page-title">Account</h1>
          <p className="page-subtitle">Manage your profile and sessions</p>
        </div>
      </div>

      <div className="page-content">
        {toast && (
          <div className="toast toast-success">
            <div className="toast-dot" />
            {toast}
          </div>
        )}
        {error && <div className="toast toast-error">{error}</div>}

        {!user?.verified && (
          <div className="toast toast-error" style={{ alignItems: "center" }}>
            <div className="toast-dot" />
            Your email isn't verified yet.
            <button
              className="btn-small"
              style={{ marginLeft: 10 }}
              onClick={handleResend}
              disabled={resending}
              type="button"
            >
              {resending ? "Sending…" : "Resend verification email"}
            </button>
          </div>
        )}

        <div className="form-panel">
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              flexWrap: "wrap",
              gap: 12,
            }}
          >
            <div>
              <p style={{ fontSize: 15, fontWeight: 600, margin: 0 }}>
                {user?.name}
              </p>
              <p className="auth-help-text" style={{ margin: "2px 0 0" }}>
                {user?.email}
                {user?.createdAt
                  ? ` · Member since ${formatDate(user.createdAt)}`
                  : ""}
              </p>
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              <span
                className={`badge ${user?.verified ? "badge-in-stock" : "badge-out-of-stock"}`}
              >
                {user?.verified ? "Email verified" : "Email not verified"}
              </span>
              <span className="badge badge-category">
                {orderCount === null ? "…" : orderCount} order
                {orderCount === 1 ? "" : "s"}
              </span>
            </div>
          </div>
        </div>

        {user?.role === "USER" && (
          <div className="form-panel">
            <p className="form-panel-label">Sell on Catalogix</p>
            {user?.requestedRole === "SELLER" ? (
              <p className="auth-help-text">
                Your seller request is pending — an admin will review it. Once
                it is approved, sign in again (or wait for your session to
                refresh) and "My Products" appears in the account menu.
              </p>
            ) : (
              <>
                <p className="auth-help-text">
                  Want to list your own products and manage their stock? Request
                  seller access — an admin reviews every request before it is
                  granted.
                </p>
                <button
                  className="btn-small"
                  onClick={handleBecomeSeller}
                  disabled={becomingSeller}
                  type="button"
                >
                  {becomingSeller ? "Sending…" : "Request seller access"}
                </button>
              </>
            )}
          </div>
        )}

        <div className="form-panel">
          <p className="form-panel-label">Profile</p>
          <form
            className="auth-form"
            onSubmit={handleSubmit}
            style={{ maxWidth: 420 }}
          >
            <div className="field-wrap">
              <label className="field-label" htmlFor="account-name">
                Full name
              </label>
              <input
                id="account-name"
                className="field-input"
                value={name}
                onChange={(e) => setName(e.target.value)}
                autoComplete="name"
              />
            </div>

            <div className="field-wrap">
              <label className="field-label" htmlFor="account-email">
                Email address
              </label>
              <input
                id="account-email"
                className="field-input"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
              />
            </div>

            <div className="field-wrap">
              <label className="field-label" htmlFor="account-new-password">
                New password
              </label>
              <input
                id="account-new-password"
                className="field-input"
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                autoComplete="new-password"
                placeholder="Leave blank to keep the current password"
              />
            </div>

            <div className="field-wrap">
              <label className="field-label" htmlFor="account-current-password">
                Current password
              </label>
              <input
                id="account-current-password"
                className="field-input"
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                autoComplete="current-password"
                placeholder="Required when changing email or password"
              />
            </div>

            <button className="btn-small" type="submit" disabled={submitting}>
              {submitting ? "Saving…" : "Save changes"}
            </button>
          </form>
        </div>

        <div className="form-panel">
          <p className="form-panel-label">Saved addresses</p>

          {addressesLoading && <p className="auth-help-text">Loading…</p>}

          {!addressesLoading && addresses.length === 0 && !showAddressForm && (
            <p className="auth-help-text">No saved addresses yet.</p>
          )}

          {!addressesLoading && addresses.length > 0 && (
            <div className="item-list" style={{ marginBottom: 12 }}>
              {addresses.map((address) => (
                <div key={address.id} className="item-row">
                  <div className="item-meta">
                    <div className="item-name">
                      {address.label}
                      {address.default && (
                        <span className="badge badge-default">Default</span>
                      )}
                    </div>
                    <div className="item-sub">
                      {address.line1}
                      {address.line2 ? `, ${address.line2}` : ""}
                      <br />
                      {address.city}, {address.state} {address.pincode} ·{" "}
                      {address.phone}
                    </div>
                  </div>
                  <div className="item-actions">
                    {!address.default && (
                      <button
                        className="btn-small"
                        onClick={() => handleSetDefaultAddress(address.id)}
                        disabled={addressBusyId === address.id}
                        type="button"
                      >
                        {addressBusyId === address.id
                          ? "Saving…"
                          : "Set default"}
                      </button>
                    )}
                    <button
                      className="btn-small"
                      onClick={() => openEditAddressForm(address)}
                      disabled={addressBusyId === address.id}
                      type="button"
                    >
                      Edit
                    </button>
                    <button
                      className="btn-small"
                      onClick={() => handleDeleteAddress(address.id)}
                      disabled={addressBusyId === address.id}
                      type="button"
                    >
                      Delete
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {showAddressForm && (
            <form
              className="auth-form"
              onSubmit={handleAddressSubmit}
              style={{ maxWidth: 640, marginTop: 12 }}
            >
              <div className="form-fields form-fields-4">
                <div className="field-wrap">
                  <label className="field-label" htmlFor="address-label">
                    Label
                  </label>
                  <input
                    id="address-label"
                    className="field-input"
                    value={addressForm.label}
                    onChange={(e) =>
                      setAddressForm((current) => ({
                        ...current,
                        label: e.target.value,
                      }))
                    }
                    placeholder="Home"
                    required
                  />
                </div>
                <div className="field-wrap">
                  <label className="field-label" htmlFor="address-line1">
                    Address line 1
                  </label>
                  <input
                    id="address-line1"
                    className="field-input"
                    value={addressForm.line1}
                    onChange={(e) =>
                      setAddressForm((current) => ({
                        ...current,
                        line1: e.target.value,
                      }))
                    }
                    required
                  />
                </div>
                <div className="field-wrap">
                  <label className="field-label" htmlFor="address-line2">
                    Address line 2
                  </label>
                  <input
                    id="address-line2"
                    className="field-input"
                    value={addressForm.line2}
                    onChange={(e) =>
                      setAddressForm((current) => ({
                        ...current,
                        line2: e.target.value,
                      }))
                    }
                  />
                </div>
                <div className="field-wrap">
                  <label className="field-label" htmlFor="address-city">
                    City
                  </label>
                  <input
                    id="address-city"
                    className="field-input"
                    value={addressForm.city}
                    onChange={(e) =>
                      setAddressForm((current) => ({
                        ...current,
                        city: e.target.value,
                      }))
                    }
                    required
                  />
                </div>
                <div className="field-wrap">
                  <label className="field-label" htmlFor="address-state">
                    State
                  </label>
                  <input
                    id="address-state"
                    className="field-input"
                    value={addressForm.state}
                    onChange={(e) =>
                      setAddressForm((current) => ({
                        ...current,
                        state: e.target.value,
                      }))
                    }
                    required
                  />
                </div>
                <div className="field-wrap">
                  <label className="field-label" htmlFor="address-pincode">
                    Pincode
                  </label>
                  <input
                    id="address-pincode"
                    className="field-input"
                    value={addressForm.pincode}
                    onChange={(e) =>
                      setAddressForm((current) => ({
                        ...current,
                        pincode: e.target.value,
                      }))
                    }
                    required
                  />
                </div>
                <div className="field-wrap">
                  <label className="field-label" htmlFor="address-phone">
                    Phone
                  </label>
                  <input
                    id="address-phone"
                    className="field-input"
                    value={addressForm.phone}
                    onChange={(e) =>
                      setAddressForm((current) => ({
                        ...current,
                        phone: e.target.value,
                      }))
                    }
                    required
                  />
                </div>
                {!editingAddressId && (
                  <label
                    className="field-wrap"
                    style={{
                      flexDirection: "row",
                      alignItems: "center",
                      gap: 7,
                      alignSelf: "end",
                    }}
                  >
                    <input
                      type="checkbox"
                      checked={addressForm.makeDefault}
                      onChange={(e) =>
                        setAddressForm((current) => ({
                          ...current,
                          makeDefault: e.target.checked,
                        }))
                      }
                    />
                    <span className="field-label">Make default</span>
                  </label>
                )}
              </div>

              <div className="item-actions">
                <button
                  className="btn-small"
                  type="submit"
                  disabled={addressSubmitting}
                >
                  {addressSubmitLabel}
                </button>
                <button
                  className="btn-small"
                  type="button"
                  onClick={() => {
                    setShowAddressForm(false);
                    setEditingAddressId(null);
                    setAddressForm(EMPTY_ADDRESS_FORM);
                  }}
                  disabled={addressSubmitting}
                >
                  Cancel
                </button>
              </div>
            </form>
          )}

          {!showAddressForm && (
            <button
              className="btn-small"
              onClick={openAddAddressForm}
              type="button"
            >
              Add new address
            </button>
          )}
        </div>

        <div className="form-panel">
          <p className="form-panel-label">Notifications</p>
          <label
            style={{
              display: "flex",
              alignItems: "center",
              gap: 8,
              cursor: "pointer",
            }}
          >
            <input
              type="checkbox"
              checked={orderEmailsEnabled}
              onChange={(e) => setOrderEmailsEnabled(e.target.checked)}
            />
            <span>Order status emails</span>
          </label>
          <p
            className="auth-help-text"
            style={{ marginTop: 8, marginBottom: 0 }}
          >
            Receive email updates when your orders change status.
          </p>
          <button
            className="btn-small"
            style={{ marginTop: 10 }}
            onClick={handleSavePreferences}
            disabled={prefsSubmitting}
            type="button"
          >
            {prefsSubmitting ? "Saving…" : "Save preferences"}
          </button>
        </div>

        <div className="form-panel">
          <p className="form-panel-label">Sessions</p>

          {sessionsLoading && <p className="auth-help-text">Loading…</p>}

          {!sessionsLoading && sessions.length === 0 && (
            <p className="auth-help-text">No active sessions found.</p>
          )}

          {!sessionsLoading && sessions.length > 0 && (
            <div className="item-list" style={{ marginBottom: 12 }}>
              {sessions.map((s) => (
                <div key={s.id} className="item-row">
                  <div className="item-meta">
                    <div className="item-name">
                      {describeUserAgent(s.userAgent)}
                      {s.current && (
                        <span className="badge badge-in-stock">
                          This device
                        </span>
                      )}
                    </div>
                    <div className="item-sub">
                      Last active {formatDate(s.lastUsedAt)} · Signed in{" "}
                      {formatDate(s.createdAt)}
                    </div>
                  </div>
                  <div className="item-actions">
                    {!s.current && (
                      <button
                        className="btn-small"
                        onClick={() => handleRevokeSession(s.id)}
                        disabled={revokingId === s.id}
                        type="button"
                      >
                        {revokingId === s.id ? "Revoking…" : "Revoke"}
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}

          <p className="auth-help-text">
            If you think another device might have access to your account, you
            can sign out everywhere at once.
          </p>
          <button
            className="btn-small"
            onClick={handleLogoutEverywhere}
            type="button"
          >
            Log out of all devices
          </button>
        </div>

        <div className="form-panel" style={{ borderColor: "var(--red-200)" }}>
          <p className="form-panel-label" style={{ color: "var(--red-600)" }}>
            Danger zone
          </p>
          <p className="auth-help-text">
            Deleting your account is permanent and cannot be undone. Your saved
            addresses and sessions are removed immediately.
          </p>
          <button
            className="btn-outline"
            style={{ borderColor: "var(--red-600)", color: "var(--red-600)" }}
            onClick={handleDeleteAccount}
            disabled={deletingAccount}
            type="button"
          >
            {deletingAccount ? "Deleting…" : "Delete my account"}
          </button>
        </div>
      </div>
    </div>
  );
}
