import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function Login() {
  const { login, register } = useAuth();
  const navigate = useNavigate();

  const [mode, setMode] = useState("login"); // "login" | "register"
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

    const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setSubmitting(true);

    try {
      if (mode === "login") {
        await login(email.trim(), password);
      } else {
        await register(name.trim(), email.trim(), password);
      }

      navigate("/", { replace: true });
    } catch (err) {
      const msg = err.response?.data?.message
        || (mode === "login"
          ? "Invalid email or password."
          : "Failed to register.");

      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  let submitButtonText = "Create account";

  if (submitting) {
    submitButtonText = "Please wait…";
  } else if (mode === "login") {
    submitButtonText = "Log in";
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="logo-mark auth-logo">
          <div className="logo-icon">
            <svg viewBox="0 0 16 16" width="14" height="14" fill="#fff">
              <path d="M2 3h5v5H2zm7 0h5v5H9zM2 10h5v4H2zm7 0h5v4H9z" />
            </svg>
          </div>
          <span className="logo-text">Catalogix</span>
        </div>

        <div className="auth-tabs">
          <button
            className={`auth-tab${mode === "login" ? " auth-tab-active" : ""}`}
            onClick={() => {
              setMode("login");
              setError("");
            }}
            type="button"
          >
            Log in
          </button>
          <button
            className={`auth-tab${mode === "register" ? " auth-tab-active" : ""}`}
            onClick={() => {
              setMode("register");
              setError("");
            }}
            type="button"
          >
            Register
          </button>
        </div>

        {error && <div className="toast toast-error auth-error">{error}</div>}

        <form className="auth-form" onSubmit={handleSubmit}>
          {mode === "register" && (
            <div className="field-wrap">
              <label className="field-label" htmlFor="auth-name">
                Full name
              </label>
              <input
                id="auth-name"
                className="field-input"
                placeholder="e.g. Priya Patel"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                disabled={submitting}
              />
            </div>
          )}
          <div className="field-wrap">
            <label className="field-label" htmlFor="auth-email">
              Email address
            </label>
            <input
              id="auth-email"
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
            <label className="field-label" htmlFor="auth-password">
              Password
            </label>
            <input
              id="auth-password"
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
          <button
            className="form-submit auth-submit"
            type="submit"
            disabled={submitting}
          >
            {submitButtonText}
          </button>
        </form>

        {mode === "login" && (
          <p
            className="auth-help-text"
            style={{ marginTop: 14, textAlign: "center" }}
          >
            <Link to="/forgot-password">Forgot your password?</Link>
          </p>
        )}
      </div>
    </div>
  );
}
