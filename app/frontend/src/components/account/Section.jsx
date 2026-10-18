import React from "react";
import PropTypes from "prop-types";

/** One settings section: heading, a sentence of help, then the content. */
export default function Section({ id, title, description, children }) {
  return (
    <section id={id} aria-labelledby={`${id}-heading`} className="scroll-mt-24">
      <h2 id={`${id}-heading`} className="text-lg font-semibold">
        {title}
      </h2>
      {description && <p className="mt-1 mb-4 text-sm text-ink-2">{description}</p>}
      {!description && <div className="mb-4" />}
      {children}
    </section>
  );
}
Section.propTypes = {
  id: PropTypes.string.isRequired,
  title: PropTypes.node.isRequired,
  description: PropTypes.node,
  children: PropTypes.node,
};
