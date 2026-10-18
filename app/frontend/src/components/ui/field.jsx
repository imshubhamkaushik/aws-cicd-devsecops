import React, { useId } from "react";
import PropTypes from "prop-types";
import { cn } from "../../lib/utils";

const control =
  "w-full rounded-control border border-line bg-surface px-3 text-sm text-ink placeholder:text-ink-2/70 transition-colors hover:border-ink-2/40 focus-visible:border-brand disabled:cursor-not-allowed disabled:opacity-60";

export function Input({ className, ...props }) {
  return <input className={cn(control, "h-10", className)} {...props} />;
}
Input.propTypes = { className: PropTypes.string };

export function Textarea({ className, rows = 3, ...props }) {
  return <textarea rows={rows} className={cn(control, "py-2", className)} {...props} />;
}
Textarea.propTypes = { className: PropTypes.string, rows: PropTypes.number };

export function Select({ className, children, ...props }) {
  return (
    <select className={cn(control, "h-10 pr-8", className)} {...props}>
      {children}
    </select>
  );
}
Select.propTypes = { className: PropTypes.string, children: PropTypes.node };

/**
 * A label above a control, with optional hint and error text. Pass the control as a
 * function so it can receive the generated id: <Field label="Email">{(id) => <Input id={id} />}</Field>
 */
export function Field({ label, hint, error, className, children }) {
  const id = useId();
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      <label htmlFor={id} className="text-[13px] font-semibold text-ink">
        {label}
      </label>
      {children(id)}
      {error ? (
        <p role="alert" className="text-xs text-danger">
          {error}
        </p>
      ) : (
        hint && <p className="text-xs text-ink-2">{hint}</p>
      )}
    </div>
  );
}
Field.propTypes = {
  label: PropTypes.node.isRequired,
  hint: PropTypes.node,
  error: PropTypes.node,
  className: PropTypes.string,
  children: PropTypes.func.isRequired,
};
