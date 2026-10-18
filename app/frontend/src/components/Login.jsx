import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import PropTypes from "prop-types";
import { Button } from "./ui/button";
import { Input, Field } from "./ui/field";
import { Panel } from "./ui/surface";
import { LogoMark } from "./ui/logo";
import { useAuth } from "../context/AuthContext";


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

  const seg = (active) =>
    `h-9 flex-1 rounded-[8px] text-sm font-semibold transition-colors ${
      active ? "bg-surface text-ink ring-1 ring-line" : "text-ink-2 hover:text-ink"
    }`;

  return (
    <AuthShell
      heading={mode === "login" ? "Welcome back" : "Create your account"}
      description={
        mode === "login"
          ? "Log in to shop, track orders and manage your listings."
          : "Join Catalogix to buy from and sell to local shops."
      }
      footer={
        mode === "login" && (
          <Link className={linkClass} to="/forgot-password">
            Forgot your password?
          </Link>
        )
      }
    >
      <div className="mb-5 flex gap-1 rounded-control bg-surface-2 p-1">
        <button
          type="button"
          aria-pressed={mode === "login"}
          className={seg(mode === "login")}
          onClick={() => {
            setMode("login");
            setError("");
          }}
        >
          Log in
        </button>
        <button
          type="button"
          aria-pressed={mode === "register"}
          className={seg(mode === "register")}
          onClick={() => {
            setMode("register");
            setError("");
          }}
        >
          Register
        </button>
      </div>

      {error && (
        <p role="alert" className={`mb-4 ${errorClass}`}>
          {error}
        </p>
      )}

      <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
        {mode === "register" && (
          <Field label="Full name">
            {(id) => (
              <Input
                id={id}
                placeholder="e.g. Priya Patel"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                disabled={submitting}
              />
            )}
          </Field>
        )}
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
        <Field label="Password" hint={mode === "register" ? "At least 6 characters." : undefined}>
          {(id) => (
            <Input
              id={id}
              type="password"
              placeholder="Min 6 characters"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={6}
              disabled={submitting}
            />
          )}
        </Field>
        <Button type="submit" size="lg" disabled={submitting} className="w-full">
          {submitButtonText}
        </Button>
      </form>
    </AuthShell>
  );
}
