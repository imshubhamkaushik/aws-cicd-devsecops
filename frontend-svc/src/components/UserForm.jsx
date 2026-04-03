import React, { useState } from "react";
import PropTypes from "prop-types";
import { createUser } from "../api";

const UserForm = ({ onUserAdded }) => {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name.trim() || !email.trim() || !password.trim()) {
      alert("Please fill in all fields.");
      return;
    }
    setSubmitting(true);
    try {
      console.log("Creating user:", { name, email, password });
      const created = await createUser({
        name: name.trim(),
        email: email.trim(),
        password,
      });
      console.log("User created:", created);

      if (typeof onUserAdded === "function") {
        await onUserAdded(); // will refetech the user list in parent
      }

      setName("");
      setEmail("");
      setPassword("");
      alert("User added successfully.");
    } catch (err) {
      console.error("Error in creating user:", err);
      alert("Failed to add user.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form
      onSubmit={handleSubmit}
      style={{ display: "flex", gap: "10px", marginBottom: "20px" }}
    >
      <input
        type="text"
        placeholder="Name"
        value={name}
        onChange={(e) => setName(e.target.value)}
        required
        disabled={submitting}
      />
      <input
        type="email"
        placeholder="Email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        required
        disabled={submitting}
      />
      <input
        type="password"
        placeholder="Password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        required
        disabled={submitting}
      />
      <button type="submit" disabled={submitting}>
        {submitting ? "Adding..." : "Add User"}
      </button>
    </form>
  );
};

// Add Prop Validation
UserForm.propTypes = {
  onUserAdded: PropTypes.func,
}

export default UserForm;