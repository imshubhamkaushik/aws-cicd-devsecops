import React from "react";
import PropTypes from "prop-types";
import { Button } from "../ui/button";
import { Panel } from "../ui/surface";
import Section from "./Section";

export default function NotificationsSection({ enabled, setEnabled, saving, onSave }) {
  return (
    <Section
      id="notifications"
      title="Notifications"
      description="Choose which emails we send you."
    >
      <Panel className="flex flex-col gap-4 p-4 sm:p-5">
        <label className="flex min-h-9 cursor-pointer items-center justify-between gap-4">
          <span>
            <span className="block font-medium">Order status emails</span>
            <span className="block text-sm text-ink-2">
              Receive email updates when your orders change status.
            </span>
          </span>
          <input
            type="checkbox"
            role="switch"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
            className="relative h-6 w-10 shrink-0 cursor-pointer appearance-none rounded-full bg-line transition-colors checked:bg-brand focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand after:absolute after:left-0.5 after:top-0.5 after:size-5 after:rounded-full after:bg-surface after:transition-transform after:content-[''] checked:after:translate-x-4"
          />
        </label>
        <div>
          <Button variant="secondary" onClick={onSave} disabled={saving}>
            {saving ? "Saving…" : "Save preferences"}
          </Button>
        </div>
      </Panel>
    </Section>
  );
}
NotificationsSection.propTypes = {
  enabled: PropTypes.bool.isRequired,
  setEnabled: PropTypes.func.isRequired,
  saving: PropTypes.bool.isRequired,
  onSave: PropTypes.func.isRequired,
};
