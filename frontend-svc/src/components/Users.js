import React, { useEffect, useState } from "react";
import { getUsers, deleteUser } from "../api";
import UserForm from "./UserForm";

const Users = (onUserSelected) => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const fetchUsers = async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getUsers();
      console.log("Fetched users:", data);
      setUsers(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error("Error fetching users:", err);
      setError("Failed to fetch users.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const handleUserAdded = () => {
    fetchUsers();
  };

  const handleDelete = async (id) => {
    const shouldDelete = globalThis.confirm("Delete this user?");
    if (!shouldDelete) return;
    try {
      await deleteUser(id);
      await fetchUsers();
    } catch (err) {
      console.error("Error deleting user:", err);
      alert("Failed to delete user.");
    }
  };

  return (
    <div style={{ padding: "20px" }}>
      <h2>Users</h2>

      <UserForm onUserAdded={fetchUsers} />

      {loading && <p>Loading users...</p>}
      {error && <p style={{ color: "red" }}>{error}</p>}

      {!loading && !error && users.length === 0 && <p>No users found.</p>}

      {!loading && !error && users.length > 0 && (
        <table
          style={{
            margin: "20px auto",
            borderCollapse: "collapse",
            minWidth: "60%",
          }}
        >
          <thead>
            <tr>
              <th style={{ border: "1px solid #ccc", padding: "8px" }}>ID</th>
              <th style={{ border: "1px solid #ccc", padding: "8px" }}>Name</th>
              <th style={{ border: "1px solid #ccc", padding: "8px" }}>
                Email
              </th>
              <th style={{ border: "1px solid #ccc", padding: "8px" }}>
                Actions
              </th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td style={{ border: "1px solid #ccc", padding: "8px" }}>
                  {u.id}
                </td>
                <td style={{ border: "1px solid #ccc", padding: "8px" }}>
                  {u.name}
                </td>
                <td style={{ border: "1px solid #ccc", padding: "8px" }}>
                  {u.email}
                </td>
                <td style={{ border: "1px solid #ccc", padding: "8px" }}>
                  <button onClick={() => onUserSelected(u)}>Use</button>
                  <button onClick={() => handleDelete(u.id)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default Users;
