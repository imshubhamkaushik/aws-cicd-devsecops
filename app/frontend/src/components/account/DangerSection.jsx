import React from "react";
import PropTypes from "prop-types";
import { Button } from "../ui/button";
import { Panel } from "../ui/surface";
import Section from "./Section";

export default function DangerSection({ deleting, onDelete }) {
  return (
    <Section
      id="delete-account"
      title="Delete account"
      description="Deleting your account is permanent and cannot be undone. Your saved addresses and sessions are removed immediately."
    >
      <Panel className="border-danger/40 p-4 sm:p-5">
        <Button variant="danger-outline" onClick={onDelete} disabled={deleting}>
          {deleting ? "Deleting…" : "Delete my account"}
        </Button>
      </Panel>
    </Section>
  );
}
DangerSection.propTypes = {
  deleting: PropTypes.bool.isRequired,
  onDelete: PropTypes.func.isRequired,
};
