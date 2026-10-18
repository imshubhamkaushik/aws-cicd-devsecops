import React from "react";
import PropTypes from "prop-types";
import { Button } from "../ui/button";
import { Badge } from "../ui/badge";
import { Panel } from "../ui/surface";
import Section from "./Section";

export default function SellerSection({ requested, busy, onRequest }) {
  return (
    <Section
      id="seller"
      title="Sell on Catalogix"
      description="Request seller access to list your own products and manage their stock."
    >
      <Panel className="flex flex-col gap-3 p-4 sm:p-5">
        {requested ? (
          <>
            <div>
              <Badge tone="info">Request pending</Badge>
            </div>
            <p className="text-sm text-ink-2">
              Your seller request is pending — an admin will review it. Once it is approved,
              sign in again (or wait for your session to refresh) and &quot;My Products&quot;
              appears in the account menu.
            </p>
          </>
        ) : (
          <>
            <div>
              <Badge>Not a seller</Badge>
            </div>
            <p className="text-sm text-ink-2">
              Want to list your own products and manage their stock? An admin reviews every
              request before it is granted.
            </p>
            <div>
              <Button onClick={onRequest} disabled={busy}>
                {busy ? "Sending…" : "Request seller access"}
              </Button>
            </div>
          </>
        )}
      </Panel>
    </Section>
  );
}
SellerSection.propTypes = {
  requested: PropTypes.bool.isRequired,
  busy: PropTypes.bool.isRequired,
  onRequest: PropTypes.func.isRequired,
};
