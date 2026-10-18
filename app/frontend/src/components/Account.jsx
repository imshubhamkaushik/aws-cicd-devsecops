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
import Toast from "./Toast";
import { PageHeader } from "./ui/surface";
import ProfileSection from "./account/ProfileSection";
import SellerSection from "./account/SellerSection";
import AddressesSection from "./account/AddressesSection";
import NotificationsSection from "./account/NotificationsSection";
import SessionsSection from "./account/SessionsSection";
import DangerSection from "./account/DangerSection";

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
      setToast("Verification email sent — check your inbox.");
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


  const cancelAddressForm = () => {
    setShowAddressForm(false);
    setEditingAddressId(null);
    setAddressForm(EMPTY_ADDRESS_FORM);
  };

  const isSeller = user?.role === "USER";
  const navItems = [
    ["profile", "Profile"],
    ...(isSeller ? [["seller", "Sell on Catalogix"]] : []),
    ["addresses", "Addresses"],
    ["notifications", "Notifications"],
    ["sessions", "Sessions"],
    ["delete-account", "Delete account"],
  ];

  return (
    <>
      <PageHeader title="Account" description="Manage your profile, addresses and devices." />

      {toast && <Toast message={toast} type="success" onDone={() => setToast("")} />}
      {error && !showAddressForm && (
        <p role="alert" className="mb-4 rounded-control bg-danger-soft px-3 py-2 text-sm text-danger">
          {error}
        </p>
      )}

      <div className="lg:grid lg:grid-cols-[11rem_minmax(0,1fr)] lg:gap-10">
        <nav aria-label="Account sections" className="hidden lg:block">
          <ul className="sticky top-24 space-y-1 text-sm">
            {navItems.map(([id, label]) => (
              <li key={id}>
                <a
                  href={`#${id}`}
                  className="block rounded-control px-3 py-2 text-ink-2 hover:bg-surface-2 hover:text-ink"
                >
                  {label}
                </a>
              </li>
            ))}
          </ul>
        </nav>

        <div className="max-w-2xl space-y-10">
          <ProfileSection
            user={user}
            orderCount={orderCount}
            name={name}
            setName={setName}
            email={email}
            setEmail={setEmail}
            newPassword={newPassword}
            setNewPassword={setNewPassword}
            currentPassword={currentPassword}
            setCurrentPassword={setCurrentPassword}
            submitting={submitting}
            onSubmit={handleSubmit}
            resending={resending}
            onResend={handleResend}
          />

          {isSeller && (
            <SellerSection
              requested={user?.requestedRole === "SELLER"}
              busy={becomingSeller}
              onRequest={handleBecomeSeller}
            />
          )}

          <AddressesSection
            addresses={addresses}
            loading={addressesLoading}
            form={addressForm}
            setForm={setAddressForm}
            editingId={editingAddressId}
            formOpen={showAddressForm}
            submitting={addressSubmitting}
            busyId={addressBusyId}
            submitLabel={addressSubmitLabel}
            error={error}
            onAdd={openAddAddressForm}
            onEdit={openEditAddressForm}
            onCancel={cancelAddressForm}
            onSubmit={handleAddressSubmit}
            onSetDefault={handleSetDefaultAddress}
            onDelete={handleDeleteAddress}
          />

          <NotificationsSection
            enabled={orderEmailsEnabled}
            setEnabled={setOrderEmailsEnabled}
            saving={prefsSubmitting}
            onSave={handleSavePreferences}
          />

          <SessionsSection
            sessions={sessions}
            loading={sessionsLoading}
            revokingId={revokingId}
            onRevoke={handleRevokeSession}
            onLogoutEverywhere={handleLogoutEverywhere}
          />

          <DangerSection deleting={deletingAccount} onDelete={handleDeleteAccount} />
        </div>
      </div>
    </>
  );
}
