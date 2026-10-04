import React, { useState } from "react";
import { Link } from "react-router-dom";
import { forgotPassword } from "../api";

export default function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      await forgotPassword(email.trim());
      // Always show the same success message, whether or not the email
      // exists — the backend deliberately doesn't reveal that either.
      setSubmitted(true);
    } catch {
      setError("Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

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

        {submitted ? (
          <div>
            <p className="auth-help-text">
              If an account exists for <strong>{email.trim()}</strong>, we've sent a link to reset your password.
              Check Mailpit / your inbox for the email.
            </p>
            <Link className="form-submit auth-submit" style={{ display: "block", textAlign: "center", marginTop: 14 }} to="/login">
              Back to log in
            </Link>
          </div>
        ) : (
          <>
            <p className="auth-help-text">Enter your account email and we'll send you a reset link.</p>
            {error && <div className="toast toast-error auth-error">{error}</div>}
            <form className="auth-form" onSubmit={handleSubmit}>
              <div className="field-wrap">
                <label className="field-label" htmlFor="fp-email">Email address</label>
                <input
                  id="fp-email"
                  className="field-input"
                  type="email"
                  placeholder="priya@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  disabled={submitting}
                />
              </div>
              <button className="form-submit auth-submit" type="submit" disabled={submitting}>
                {submitting ? "Sending…" : "Send reset link"}
              </button>
            </form>
            <p className="auth-help-text" style={{ marginTop: 14 }}>
              <Link to="/login">Back to log in</Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
}
