import React from "react";
import PropTypes from "prop-types";
import { cn } from "../../lib/utils";

/** The four-square Catalogix mark on a brand-coloured tile. */
export function LogoMark({ className }) {
  return (
    <span
      className={cn("grid size-8 place-items-center rounded-control bg-brand text-brand-ink", className)}
      aria-hidden="true"
    >
      <svg viewBox="0 0 16 16" width="16" height="16" fill="currentColor">
        <path d="M2 3h5v5H2zm7 0h5v5H9zM2 10h5v4H2zm7 0h5v4H9z" />
      </svg>
    </span>
  );
}
LogoMark.propTypes = { className: PropTypes.string };
