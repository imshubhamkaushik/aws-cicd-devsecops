import React, { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import PropTypes from "prop-types";
import { Button } from "./ui/button";
import { Input, Field } from "./ui/field";
import { Panel } from "./ui/surface";
import { LogoMark } from "./ui/logo";
import { resetPassword } from "../api";

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

  if (!token) {
    return (
      <AuthShell
        heading="This link is incomplete"
        description="The reset link is missing its token, so we can't tell which account it is for."
      >
        <Button asChild size="lg" className="w-full">
          <Link to="/forgot-password">Request a new link</Link>
        </Button>
      </AuthShell>
    );
  }

  if (done) {
    return (
      <AuthShell
        heading="Password updated"
        description="You've been signed out everywhere for security. Log in again with your new password."
      >
        <Button size="lg" className="w-full" onClick={() => navigate("/login")}>
          Go to log in
        </Button>
      </AuthShell>
    );
  }

  return (
    <AuthShell heading="Choose a new password" description="Pick something you haven't used on another site.">
      {error && (
        <p role="alert" className={`mb-4 ${errorClass}`}>
          {error}
        </p>
      )}
      <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
        <Field label="New password" hint="At least 6 characters, with a letter and a number.">
          {(id) => (
            <Input
              id={id}
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
              minLength={6}
              disabled={submitting}
            />
          )}
        </Field>
        <Button type="submit" size="lg" disabled={submitting} className="w-full">
          {submitting ? "Resetting…" : "Reset password"}
        </Button>
      </form>
    </AuthShell>
  );
}
