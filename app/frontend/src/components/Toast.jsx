import React, { useEffect } from "react";
import PropTypes from "prop-types";
import { CircleAlert, CircleCheck } from "lucide-react";
import { cn } from "../lib/utils";

/**
 * One short message that dismisses itself after 3 seconds. Errors use role="alert" so a
 * screen reader announces them straight away; successes wait for a pause.
 */
export default function Toast({ message, type, onDone }) {
  useEffect(() => {
    const timeoutId = setTimeout(onDone, 3000);

    return () => clearTimeout(timeoutId);
  }, [onDone]);

  const isError = type === "error";
  const Icon = isError ? CircleAlert : CircleCheck;

  return (
    <div
      role={isError ? "alert" : "status"}
      className={cn(
        "fixed inset-x-4 bottom-24 z-50 mx-auto flex max-w-md items-start gap-2.5 rounded-card border px-4 py-3 text-sm font-medium shadow-lg md:inset-x-auto md:bottom-6 md:right-6 md:mx-0",
        isError
          ? "border-danger/30 bg-danger-soft text-danger"
          : "border-brand/30 bg-brand-soft text-brand-strong",
      )}
    >
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}

Toast.propTypes = {
  message: PropTypes.string.isRequired,
  type: PropTypes.oneOf(["success", "error"]).isRequired,
  onDone: PropTypes.func.isRequired,
};
