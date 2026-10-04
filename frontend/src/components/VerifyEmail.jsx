import React, { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { verifyEmail } from "../api";

export default function VerifyEmail() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") || "";
  const { isAuthenticated } = useAuth();

  const [status, setStatus] = useState(token ? "loading" : "missing"); // loading | success | error | missing
  const [error, setError] = useState("");

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    (async () => {
      try {
        await verifyEmail(token);
        if (!cancelled) setStatus("success");
      } catch (err) {
        if (!cancelled) {
          setError(err.response?.data?.message || "That verification link is invalid or has expired.");
          setStatus("error");
        }
      }
    })();
    return () => { cancelled = true; };
  }, [token]);

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

        {status === "missing" && (
          <p className="auth-help-text">This link is missing its verification token.</p>
        )}
        {status === "loading" && <p className="auth-help-text">Verifying your email…</p>}
        {status === "success" && (
          <p className="auth-help-text">Your email is verified. You're all set.</p>
        )}
        {status === "error" && <div className="toast toast-error auth-error">{error}</div>}

        <Link
          className="form-submit auth-submit"
          style={{ display: "block", textAlign: "center", marginTop: 14 }}
          to={isAuthenticated ? "/" : "/login"}
        >
          {isAuthenticated ? "Back to Catalogix" : "Go to log in"}
        </Link>
      </div>
    </div>
  );
}
