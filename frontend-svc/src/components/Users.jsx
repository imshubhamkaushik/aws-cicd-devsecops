import React, { useEffect, useState, useCallback } from "react";
import PropTypes from "prop-types";
import { getUsers, createUser, deleteUser } from "../api";

// Generate initials from a name string
function initials(name) {
  return name
    .split(" ")
    .slice(0, 2)
    .map((w) => w[0])
    .join("")
    .toUpperCase();
}

// Cycle through avatar colours deterministically by user id
const AVATAR_CLASSES = ["av-teal", "av-blue", "av-purple", "av-amber", "av-coral"];
function avatarClass(id) {
  return AVATAR_CLASSES[(id - 1) % AVATAR_CLASSES.length];
}

// Inline toast — no library needed
function Toast({ message, onDone }) {
  useEffect(() => {
    const t = setTimeout(onDone, 3000);
    return () => clearTimeout(t);
  }, [onDone]);

  return (
    <div className="toast toast-success">
      <div className="toast-dot" />
      {message}
    </div>
  );
}

Toast.propTypes = {
  message: PropTypes.string.isRequired,
  onDone: PropTypes.func.isRequired,
};

export default function Users({ currentUser, onUserSelected }) {
  const [users, setUsers]       = useState([]);
  const [loading, setLoading]   = useState(false);
  const [error, setError]       = useState("");
  const [toast, setToast]       = useState("");
  const [submitting, setSubmitting] = useState(false);

  // Form state
  const [name, setName]         = useState("");
  const [email, setEmail]       = useState("");
  const [password, setPassword] = useState("");

  // Search filter
  const [search, setSearch]     = useState("");

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getUsers();
      setUsers(Array.isArray(data) ? data : []);
    } catch {
      setError("Failed to load users. Is the backend running?");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  const handleRegister = async (e) => {
    e.preventDefault();
    if (!name.trim() || !email.trim() || !password.trim()) return;
    setSubmitting(true);
    try {
      await createUser({ name: name.trim(), email: email.trim(), password });
      setName(""); setEmail(""); setPassword("");
      await fetchUsers();
      setToast("User registered successfully.");
    } catch (err) {
      const msg = err.response?.data?.message || "Failed to register user.";
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (user) => {
    // Inline confirmation instead of browser confirm()
    if (!globalThis.confirm(`Remove ${user.name}? This cannot be undone.`)) return;
    try {
      await deleteUser(user.id);
      if (currentUser?.id === user.id) onUserSelected(null);
      await fetchUsers();
      setToast(`${user.name} removed.`);
    } catch {
      setError("Failed to delete user.");
    }
  };

  const handleSelect = (user) => {
    onUserSelected(user);
    setToast(`${user.name} set as active user.`);
  };

  const filtered = users.filter(
    (u) =>
      u.name.toLowerCase().includes(search.toLowerCase()) ||
      u.email.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="page-wrapper">
      <div className="topbar">
        <div>
          <h1 className="page-title">Users</h1>
          <p className="page-subtitle">Manage registered accounts</p>
        </div>
      </div>

      <div className="page-content">
        {toast && <Toast message={toast} onDone={() => setToast("")} />}
        {error && <div className="toast toast-error">{error}</div>}

        {/* Register form */}
        <div className="form-panel">
          <p className="form-panel-label">Register new user</p>
          <form className="form-fields form-fields-4" onSubmit={handleRegister}>
            <div className="field-wrap">
              <label className="field-label" htmlFor="reg-name">Full name</label>
              <input
                className="field-input"
                placeholder="e.g. Priya Patel"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                disabled={submitting}
              />
            </div>
            <div className="field-wrap">
              <label className="field-label" htmlFor="reg-email">Email address</label>
              <input
                className="field-input"
                type="email"
                placeholder="priya@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                disabled={submitting}
              />
            </div>
            <div className="field-wrap">
              <label className="field-label" htmlFor="reg-password">Password</label>
              <input
                className="field-input"
                type="password"
                placeholder="Min 6 characters"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={6}
                disabled={submitting}
              />
            </div>
            <button className="form-submit" type="submit" disabled={submitting}>
              {submitting ? "Saving…" : "Register"}
            </button>
          </form>
        </div>

        {/* List header */}
        <div className="section-header">
          <div className="section-header-left">
            <span className="section-title">All users</span>
            {!loading && (
              <span className="section-count">{users.length} registered</span>
            )}
          </div>
          <div className="search-box">
            <svg
              viewBox="0 0 16 16"
              width="13"
              height="13"
              fill="currentColor"
              style={{ opacity: 0.4, flexShrink: 0 }}
            >
              <path d="M11.742 10.344a6.5 6.5 0 10-1.397 1.398l3.85 3.85a1 1 0 001.415-1.414l-3.868-3.834zm-5.242 1.156a5 5 0 110-10 5 5 0 010 10z" />
            </svg>
            <input
              placeholder="Search users…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>

        {/* Loading skeleton */}
        {loading && (
          <div className="skeleton-list">
            {[1, 2, 3].map((i) => (
              <div key={i} className="skeleton-row" />
            ))}
          </div>
        )}

        {/* Empty state */}
        {!loading && filtered.length === 0 && (
          <div className="empty-state">
            <div className="empty-icon">
              <svg
                viewBox="0 0 16 16"
                width="20"
                height="20"
                fill="currentColor"
              >
                <path d="M8 8a3 3 0 100-6 3 3 0 000 6zm-5 6a5 5 0 0110 0H3z" />
              </svg>
            </div>
            <p className="empty-title">
              {search ? "No users match your search" : "No users yet"}
            </p>
            <p className="empty-sub">
              {search
                ? "Try a different name or email."
                : "Register the first user using the form above."}
            </p>
          </div>
        )}

        {/* User rows */}
        {!loading && filtered.length > 0 && (
          <div className="item-list">
            {filtered.map((user) => {
              const isActive = currentUser?.id === user.id;
              return (
                <div
                  key={user.id}
                  className={`item-row${isActive ? " item-row-active" : ""}`}
                >
                  <div className={`avatar ${avatarClass(user.id)}`}>
                    {initials(user.name)}
                  </div>
                  <div className="item-meta">
                    <div className="item-name">{user.name}</div>
                    <div className="item-sub">{user.email}</div>
                  </div>
                  <div className="item-actions">
                    <button
                      className={`chip ${isActive ? "chip-active" : "chip-idle"}`}
                      onClick={() => !isActive && handleSelect(user)}
                    >
                      {isActive ? "Active" : "Select"}
                    </button>
                    <button
                      className="icon-btn"
                      onClick={() => handleDelete(user)}
                      title="Remove user"
                    >
                      <svg
                        viewBox="0 0 16 16"
                        width="12"
                        height="12"
                        fill="currentColor"
                      >
                        <path d="M11 1.5v1h3.5a.5.5 0 010 1H13v9a1 1 0 01-1 1H4a1 1 0 01-1-1v-9H1.5a.5.5 0 010-1H5v-1A1.5 1.5 0 016.5 0h3A1.5 1.5 0 0111 1.5zm-5 0v1h4v-1a.5.5 0 00-.5-.5h-3a.5.5 0 00-.5.5zM5.5 5.5a.5.5 0 00-1 0v6a.5.5 0 001 0v-6zm2.5 0a.5.5 0 00-1 0v6a.5.5 0 001 0v-6zm2.5 0a.5.5 0 00-1 0v6a.5.5 0 001 0v-6z" />
                      </svg>
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

Users.propTypes = {
  currentUser: PropTypes.shape({ id: PropTypes.number, name: PropTypes.string }),
  onUserSelected: PropTypes.func.isRequired,
};
