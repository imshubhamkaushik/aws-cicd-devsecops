import React, { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import PropTypes from "prop-types";
import { Button } from "./ui/button";
import { Input, Field } from "./ui/field";
import { Panel } from "./ui/surface";
import { LogoMark } from "./ui/logo";
import { Skeleton } from "./ui/surface";
import { useAuth } from "../context/AuthContext";
import { verifyEmail } from "../api";

function AuthShell({ heading, description, children, footer }) {
  return (
    <div className="grid min-h-dvh place-items-center px-4 py-10">
      <Panel className="w-full max-w-sm p-6 sm:p-8">
        <div className="mb-6 flex items-center gap-2.5">
          <LogoMark />
          <span className="font-display text-lg font-semibold">Catalogix</span>
        </div>
        <h1 className="text-2xl font-semibold">{heading}</h1>
        {description && <p className="mt-1.5 text-sm text-ink-2">{description}</p>}
        <div className="mt-6">{children}</div>
        {footer && <div className="mt-6 text-center text-sm text-ink-2">{footer}</div>}
      </Panel>
    </div>
  );
}
AuthShell.propTypes = {
  heading: PropTypes.node.isRequired,
  description: PropTypes.node,
  children: PropTypes.node,
  footer: PropTypes.node,
};

const linkClass = "font-semibold text-brand underline-offset-4 hover:underline";
const errorClass = "rounded-control bg-danger-soft px-3 py-2 text-sm text-danger";

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

  const headings = {
    loading: "Verifying your email…",
    success: "Email verified",
    error: "We couldn't verify your email",
    missing: "This link is incomplete",
  };
  const descriptions = {
    loading: "This only takes a moment.",
    success: "Thanks, you're all set.",
    missing: "The verification link is missing its token. Open the link from your email again.",
  };

  return (
    <AuthShell heading={headings[status]} description={descriptions[status]}>
      {status === "loading" && (
        <div role="status" aria-label="Verifying" className="mb-6">
          <Skeleton className="h-10 w-full" />
        </div>
      )}
      {status === "error" && (
        <p role="alert" className={`mb-6 ${errorClass}`}>
          {error}
        </p>
      )}
      <Button asChild size="lg" variant={status === "success" ? "primary" : "secondary"} className="w-full">
        <Link to={isAuthenticated ? "/" : "/login"}>
          {isAuthenticated ? "Back to Catalogix" : "Go to log in"}
        </Link>
      </Button>
    </AuthShell>
  );
}
