import React from "react";
import PropTypes from "prop-types";
import { Button } from "../ui/button";
import { Input, Field } from "../ui/field";
import { Badge } from "../ui/badge";
import { Panel } from "../ui/surface";
import { formatDateTime } from "../../lib/format";
import Section from "./Section";

export default function ProfileSection({
  user,
  orderCount,
  name,
  setName,
  email,
  setEmail,
  newPassword,
  setNewPassword,
  currentPassword,
  setCurrentPassword,
  submitting,
  onSubmit,
  resending,
  onResend,
}) {
  return (
    <Section
      id="profile"
      title="Profile"
      description="Your name, email address and password."
    >
      <Panel className="divide-y divide-line">
        <div className="flex flex-wrap items-center justify-between gap-3 p-4 sm:p-5">
          <div className="min-w-0">
            <p className="truncate font-semibold">{user?.name}</p>
            <p className="break-all text-sm text-ink-2">
              {user?.email}
              {user?.createdAt ? ` · Member since ${formatDateTime(user.createdAt)}` : ""}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Badge tone={user?.verified ? "success" : "warning"}>
              {user?.verified ? "Email verified" : "Email not verified"}
            </Badge>
            <Badge className="tnum">
              {orderCount === null ? "…" : orderCount} order
              {orderCount === 1 ? "" : "s"}
            </Badge>
          </div>
        </div>

        {!user?.verified && (
          <div className="flex flex-wrap items-center justify-between gap-3 bg-saffron-soft p-4 sm:px-5">
            <p className="text-sm text-ink">Your email isn&apos;t verified yet.</p>
            <Button variant="secondary" size="sm" onClick={onResend} disabled={resending}>
              {resending ? "Sending…" : "Resend verification email"}
            </Button>
          </div>
        )}

        <form className="grid max-w-md gap-4 p-4 sm:p-5" onSubmit={onSubmit}>
          <Field label="Full name">
            {(id) => (
              <Input
                id={id}
                value={name}
                onChange={(e) => setName(e.target.value)}
                autoComplete="name"
              />
            )}
          </Field>
          <Field label="Email address">
            {(id) => (
              <Input
                id={id}
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
              />
            )}
          </Field>
          <Field label="New password">
            {(id) => (
              <Input
                id={id}
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                autoComplete="new-password"
                placeholder="Leave blank to keep the current password"
              />
            )}
          </Field>
          <Field label="Current password" hint="Needed only when you change your email or password.">
            {(id) => (
              <Input
                id={id}
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                autoComplete="current-password"
                placeholder="Required when changing email or password"
              />
            )}
          </Field>
          <div>
            <Button type="submit" disabled={submitting}>
              {submitting ? "Saving…" : "Save changes"}
            </Button>
          </div>
        </form>
      </Panel>
    </Section>
  );
}

ProfileSection.propTypes = {
  user: PropTypes.shape({
    name: PropTypes.string,
    email: PropTypes.string,
    createdAt: PropTypes.string,
    verified: PropTypes.bool,
  }),
  orderCount: PropTypes.number,
  name: PropTypes.string.isRequired,
  setName: PropTypes.func.isRequired,
  email: PropTypes.string.isRequired,
  setEmail: PropTypes.func.isRequired,
  newPassword: PropTypes.string.isRequired,
  setNewPassword: PropTypes.func.isRequired,
  currentPassword: PropTypes.string.isRequired,
  setCurrentPassword: PropTypes.func.isRequired,
  submitting: PropTypes.bool.isRequired,
  onSubmit: PropTypes.func.isRequired,
  resending: PropTypes.bool.isRequired,
  onResend: PropTypes.func.isRequired,
};
