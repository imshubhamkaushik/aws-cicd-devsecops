import React, { useEffect } from "react";
import PropTypes from "prop-types";

export default function Toast({ message, type, onDone }) {
  useEffect(() => {
    const timeoutId = setTimeout(onDone, 3000);

    return () => clearTimeout(timeoutId);
  }, [onDone]);

  return (
    <div className={`toast toast-${type}`}>
      <div className="toast-dot" />
      {message}
    </div>
  );
}

Toast.propTypes = {
  message: PropTypes.string.isRequired,
  type: PropTypes.oneOf(["success", "error"]).isRequired,
  onDone: PropTypes.func.isRequired,
};
