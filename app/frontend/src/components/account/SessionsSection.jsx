import React from "react";
import PropTypes from "prop-types";
import { Button } from "../ui/button";
import { Badge } from "../ui/badge";
import { Panel, Skeleton } from "../ui/surface";
import { formatDateTime } from "../../lib/format";
import Section from "./Section";

// Lightweight, dependency-free User-Agent summary — not a full parser, just
// enough to tell sessions apart in a list ("Chrome on macOS" beats a raw
// 150-character UA string). Falls back to a generic label if nothing matches.
function describeUserAgent(userAgent) {
  if (!userAgent) return "Unknown device";

  let browser = "Unknown browser";
  if (userAgent.includes("Edg/")) browser = "Edge";
  else if (userAgent.includes("Chrome/")) browser = "Chrome";
  else if (userAgent.includes("Firefox/")) browser = "Firefox";
  else if (userAgent.includes("Safari/")) browser = "Safari";

  let os = "Unknown OS";
  if (userAgent.includes("Windows")) os = "Windows";
  else if (userAgent.includes("Mac OS")) os = "macOS";
  else if (userAgent.includes("Android")) os = "Android";
  else if (userAgent.includes("iPhone") || userAgent.includes("iPad")) os = "iOS";
  else if (userAgent.includes("Linux")) os = "Linux";

  return `${browser} on ${os}`;
}

export default function SessionsSection({ sessions, loading, revokingId, onRevoke, onLogoutEverywhere }) {
  return (
    <Section
      id="sessions"
      title="Sessions"
      description="The devices where you're signed in. Sign out of any you don't recognise."
    >
      <Panel className="divide-y divide-line">
        {loading && (
          <div className="space-y-2 p-4 sm:p-5">
            <Skeleton className="h-5 w-48" />
            <Skeleton className="h-4 w-64" />
          </div>
        )}

        {!loading && sessions.length === 0 && (
          <p className="p-4 text-sm text-ink-2 sm:p-5">No active sessions found.</p>
        )}

        {!loading &&
          sessions.map((s) => (
            <div
              key={s.id}
              className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5"
            >
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2 font-medium">
                  {describeUserAgent(s.userAgent)}
                  {s.current && <Badge tone="success">This device</Badge>}
                </div>
                <div className="mt-0.5 text-sm text-ink-2">
                  Last active {formatDateTime(s.lastUsedAt)} · Signed in {formatDateTime(s.createdAt)}
                </div>
              </div>
              {!s.current && (
                <Button
                  variant="secondary"
                  size="sm"
                  className="self-start sm:self-auto"
                  onClick={() => onRevoke(s.id)}
                  disabled={revokingId === s.id}
                >
                  {revokingId === s.id ? "Revoking…" : "Revoke"}
                </Button>
              )}
            </div>
          ))}

        <div className="flex flex-wrap items-center justify-between gap-3 p-4 sm:p-5">
          <p className="max-w-md text-sm text-ink-2">
            If you think another device might have access to your account, you can sign out
            everywhere at once.
          </p>
          <Button variant="secondary" onClick={onLogoutEverywhere}>
            Log out of all devices
          </Button>
        </div>
      </Panel>
    </Section>
  );
}
SessionsSection.propTypes = {
  sessions: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
      userAgent: PropTypes.string,
      current: PropTypes.bool,
      lastUsedAt: PropTypes.string,
      createdAt: PropTypes.string,
    }),
  ).isRequired,
  loading: PropTypes.bool.isRequired,
  revokingId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  onRevoke: PropTypes.func.isRequired,
  onLogoutEverywhere: PropTypes.func.isRequired,
};
