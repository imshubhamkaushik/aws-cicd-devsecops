import React from "react";
import PropTypes from "prop-types";
import { cn } from "../../lib/utils";

/** A bordered panel. Borders do the separating, so there is no drop shadow. */
export function Panel({ className, ...props }) {
  return (
    <div className={cn("rounded-card border border-line bg-surface", className)} {...props} />
  );
}
Panel.propTypes = { className: PropTypes.string };

export function Skeleton({ className }) {
  return <div aria-hidden="true" className={cn("animate-pulse rounded-control bg-surface-2", className)} />;
}
Skeleton.propTypes = { className: PropTypes.string };

export function Spinner({ className }) {
  return (
    <span
      role="status"
      aria-label="Loading"
      className={cn(
        "inline-block size-4 animate-spin rounded-full border-2 border-current border-t-transparent",
        className,
      )}
    />
  );
}
Spinner.propTypes = { className: PropTypes.string };

/** Title, one plain sentence, and the page's main action on the right. */
export function PageHeader({ title, description, actions }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-x-6 gap-y-3">
      <div className="min-w-0">
        <h1 className="text-[28px] font-semibold sm:text-[32px]">{title}</h1>
        {description && <p className="mt-1 max-w-prose text-ink-2">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}
PageHeader.propTypes = {
  title: PropTypes.node.isRequired,
  description: PropTypes.node,
  actions: PropTypes.node,
};

/** Shown when a list has nothing in it. Always says what to do next. */
export function EmptyState({ icon: Icon, title, description, action }) {
  return (
    <div className="flex flex-col items-center rounded-card border border-dashed border-line px-6 py-14 text-center">
      {Icon && (
        <div className="mb-4 grid size-12 place-items-center rounded-full bg-brand-soft text-brand">
          <Icon className="size-6" aria-hidden="true" />
        </div>
      )}
      <h2 className="text-lg font-semibold">{title}</h2>
      {description && <p className="mt-1 max-w-sm text-ink-2">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
EmptyState.propTypes = {
  icon: PropTypes.elementType,
  title: PropTypes.node.isRequired,
  description: PropTypes.node,
  action: PropTypes.node,
};
