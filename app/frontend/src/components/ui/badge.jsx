import React from "react";
import PropTypes from "prop-types";
import { cva } from "class-variance-authority";
import { cn } from "../../lib/utils";

const badgeStyles = cva(
  "inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold",
  {
    variants: {
      tone: {
        neutral: "bg-surface-2 text-ink-2",
        success: "bg-brand-soft text-brand-strong",
        warning: "bg-saffron-soft text-saffron",
        danger: "bg-danger-soft text-danger",
        info: "bg-info-soft text-info",
      },
    },
    defaultVariants: { tone: "neutral" },
  },
);

export function Badge({ tone, className, ...props }) {
  return <span className={cn(badgeStyles({ tone }), className)} {...props} />;
}
Badge.propTypes = {
  tone: PropTypes.oneOf(["neutral", "success", "warning", "danger", "info"]),
  className: PropTypes.string,
};
