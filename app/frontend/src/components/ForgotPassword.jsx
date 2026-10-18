import React, { useState } from "react";
import { Link } from "react-router-dom";
import PropTypes from "prop-types";
import { Button } from "./ui/button";
import { Input, Field } from "./ui/field";
import { Panel } from "./ui/surface";
import { LogoMark } from "./ui/logo";
import { forgotPassword } from "../api";

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

  if (submitted) {
    return (
      <AuthShell heading="Check your inbox">
        <p className="text-sm text-ink-2">
          If an account exists for <strong className="text-ink">{email.trim()}</strong>, we've sent a link to reset
          your password. It may take a minute to arrive.
        </p>
        <Button asChild size="lg" className="mt-6 w-full">
          <Link to="/login">Back to log in</Link>
        </Button>
      </AuthShell>
    );
  }

  return (
    <AuthShell
      heading="Reset your password"
      description="Enter your account email and we'll send you a reset link."
      footer={
        <Link className={linkClass} to="/login">
          Back to log in
        </Link>
      }
    >
      {error && (
        <p role="alert" className={`mb-4 ${errorClass}`}>
          {error}
        </p>
      )}
      <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
        <Field label="Email address">
          {(id) => (
            <Input
              id={id}
              type="email"
              placeholder="priya@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              disabled={submitting}
            />
          )}
        </Field>
        <Button type="submit" size="lg" disabled={submitting} className="w-full">
          {submitting ? "Sending…" : "Send reset link"}
        </Button>
      </form>
    </AuthShell>
  );
}
