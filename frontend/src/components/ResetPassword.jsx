import React, { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { resetPassword } from "../api";

export default function ResetPassword() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") || "";
  const navigate = useNavigate();

  const [newPassword, setNewPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError("");

    try {
      await resetPassword(token, newPassword);
      setDone(true);
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "That reset link is invalid or has expired.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  let content;

  if (!token) {
    content = (
      <p className="auth-help-text">
        This link is missing its reset token. Request a new one from the{" "}
        <Link to="/forgot-password">forgot password</Link> page.
      </p>
    );
  } else if (done) {
    content = (
      <div>
        <p className="auth-help-text">
          Your password has been reset. All existing sessions have been signed
          out for security — log in again with your new password.
        </p>

        <button
          className="form-submit auth-submit"
          type="button"
          onClick={() => navigate("/login")}
        >
          Go to log in
        </button>
      </div>
    );
  } else {
    content = (
      <>
        <p className="auth-help-text">
          Choose a new password for your account.
        </p>

        {error && <div className="toast toast-error auth-error">{error}</div>}

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="field-wrap">
            <label className="field-label" htmlFor="rp-password">
              New password
            </label>

            <input
              id="rp-password"
              className="field-input"
              type="password"
              placeholder="Min 6 characters, letter + number"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
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
            {submitting ? "Resetting…" : "Reset password"}
          </button>
        </form>
      </>
    );
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

        {content}
      </div>
    </div>
  );
}
