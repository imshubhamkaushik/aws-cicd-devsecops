import React, { useEffect, useState, useCallback } from "react";
import { Search, Trash2, Users as UsersIcon, X } from "lucide-react";
import { getUsers, deleteUser, assignUserRole, rejectRoleRequest } from "../api";
import { useAuth } from "../context/AuthContext";
import Toast from "./Toast";
import { Button } from "./ui/button";
import { Badge } from "./ui/badge";
import { Input, Select } from "./ui/field";
import { Panel, Skeleton, PageHeader, EmptyState } from "./ui/surface";

// Generate initials from a name string
function initials(name) {
  return name
    .split(" ")
    .slice(0, 2)
    .map((w) => w[0])
    .join("")
    .toUpperCase();
}

const ROLE_TONE = { ADMIN: "info", SELLER: "warning", USER: "neutral" };

// Admin-only directory of every registered account (route itself is hidden
// from non-admins in App.jsx; the backend also rejects GET /users for
// non-admins, so this is defense in depth, not the only guard).
export default function Users() {
  const { user: currentUser } = useAuth();
  const [users, setUsers]     = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState("");
  const [toast, setToast]     = useState("");
  const [search, setSearch]   = useState("");

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

  const handleDelete = async (user) => {
    if (!globalThis.confirm(`Remove ${user.name}? This cannot be undone.`)) return;
    try {
      await deleteUser(user.id);
      await fetchUsers();
      setToast(`${user.name} removed.`);
    } catch {
      setError("Failed to delete user.");
    }
  };

  // Approving a request is simply assigning the requested role.
  const handleAssignRole = async (user, role) => {
    if (role === user.role) return;
    if (role === "ADMIN" && !globalThis.confirm(`Make ${user.name} an admin? Admins can manage every user and role.`)) return;
    try {
      await assignUserRole(user.id, role);
      await fetchUsers();
      setToast(`${user.name} is now ${role}.`);
    } catch (e) {
      setError(e?.response?.data?.message || "Failed to change the role.");
    }
  };

  const handleRejectRequest = async (user) => {
    try {
      await rejectRoleRequest(user.id);
      await fetchUsers();
      setToast(`Request from ${user.name} declined.`);
    } catch {
      setError("Failed to decline the request.");
    }
  };

  const pendingCount = users.filter((u) => u.requestedRole).length;

  const filtered = users.filter(
    (u) =>
      u.name.toLowerCase().includes(search.toLowerCase()) ||
      u.email.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div>
      <PageHeader
        title="Users"
        description="Everyone who has registered. Approve seller requests and manage roles here."
      />

      {toast && <Toast message={toast} type="success" onDone={() => setToast("")} />}
      {error && (
        <div
          role="alert"
          className="mb-4 flex items-start justify-between gap-3 rounded-control border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger"
        >
          <span>{error}</span>
          <button
            type="button"
            aria-label="Dismiss error"
            className="rounded p-0.5 hover:bg-danger/10"
            onClick={() => setError("")}
          >
            <X className="size-4" aria-hidden="true" />
          </button>
        </div>
      )}

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        {!loading && (
          <p className="text-sm text-ink-2">
            {users.length} registered
            {pendingCount > 0
              ? ` · ${pendingCount} pending request${pendingCount === 1 ? "" : "s"}`
              : ""}
          </p>
        )}
        <div className="relative w-full sm:ml-auto sm:w-72">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-2"
            aria-hidden="true"
          />
          <Input
            type="search"
            aria-label="Search users"
            className="pl-9"
            placeholder="Search by name or email"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {loading && (
        <div className="space-y-2" aria-busy="true">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-16" />
          ))}
        </div>
      )}

      {!loading && filtered.length === 0 && (
        <EmptyState
          icon={UsersIcon}
          title={search ? "No users match your search" : "No users yet"}
          description={search ? "Try a different name or email." : "Nobody has registered yet."}
        />
      )}

      {!loading && filtered.length > 0 && (
        <Panel>
          <ul className="divide-y divide-line">
            {filtered.map((user) => {
              const isSelf = currentUser?.id === user.id;
              return (
                <li
                  key={user.id}
                  className="flex flex-wrap items-center gap-x-4 gap-y-3 p-3 sm:p-4"
                >
                  <span
                    aria-hidden="true"
                    className="grid size-10 shrink-0 place-items-center rounded-full bg-brand-soft text-sm font-semibold text-brand-strong"
                  >
                    {initials(user.name)}
                  </span>

                  <div className="min-w-0 flex-1 basis-48">
                    <p className="truncate font-semibold">
                      {user.name}
                      {isSelf && <span className="font-normal text-ink-2"> (you)</span>}
                    </p>
                    <p className="truncate text-sm text-ink-2">{user.email}</p>
                  </div>

                  <div className="flex flex-wrap items-center gap-2">
                    {user.requestedRole && !isSelf && (
                      <>
                        <Badge tone="warning">Requests {user.requestedRole}</Badge>
                        <Button
                          size="sm"
                          onClick={() => handleAssignRole(user, user.requestedRole)}
                        >
                          Approve
                        </Button>
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => handleRejectRequest(user)}
                        >
                          Decline
                        </Button>
                      </>
                    )}

                    {isSelf ? (
                      <Badge tone={ROLE_TONE[user.role] || "neutral"}>{user.role}</Badge>
                    ) : (
                      <Select
                        className="h-8 w-auto text-[13px]"
                        aria-label={`Role of ${user.name}`}
                        value={user.role}
                        onChange={(e) => handleAssignRole(user, e.target.value)}
                      >
                        <option value="USER">USER</option>
                        <option value="SELLER">SELLER</option>
                        <option value="ADMIN">ADMIN</option>
                      </Select>
                    )}

                    <Button
                      variant="ghost"
                      size="icon"
                      className="text-danger hover:bg-danger-soft hover:text-danger"
                      onClick={() => handleDelete(user)}
                      title="Remove user"
                      aria-label={`Remove ${user.name}`}
                    >
                      <Trash2 aria-hidden="true" />
                    </Button>
                  </div>
                </li>
              );
            })}
          </ul>
        </Panel>
      )}
    </div>
  );
}
