import React, { useEffect, useState, useCallback } from "react";
import PropTypes from "prop-types";
import { getOutboxEntries, retryOutboxEntry } from "../api";

function Toast({ message, onDone }) {
  useEffect(() => {
    const t = setTimeout(onDone, 3000);
    return () => clearTimeout(t);
  }, [onDone]);
  return (
    <div className="toast toast-success">
      <div className="toast-dot" />
      {message}
    </div>
  );
}
Toast.propTypes = { message: PropTypes.string.isRequired, onDone: PropTypes.func.isRequired };

function describeEntry(entry) {
  switch (entry.type) {
    case "RELEASE_STOCK":
      return `Release ${Math.abs(entry.delta)} unit(s) of product #${entry.productId}`;
    default:
      return entry.reason || entry.type;
  }
}

export default function OutboxAdmin() {
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState("");
  const [retryingId, setRetryingId] = useState(null);

  const fetchEntries = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setEntries(await getOutboxEntries());
    } catch {
      setError("Failed to load outbox entries.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchEntries(); }, [fetchEntries]);

  const handleRetry = async (entry) => {
    if (!globalThis.confirm(`Retry compensation entry #${entry.id}? It will be reset to PENDING and picked up on the next processor run.`)) return;
    setRetryingId(entry.id);
    setError("");
    try {
      await retryOutboxEntry(entry.id);
      await fetchEntries();
      setToast(`Entry #${entry.id} reset to PENDING.`);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to retry entry.");
    } finally {
      setRetryingId(null);
    }
  };

  const deadLetterCount = entries.filter((e) => e.status === "DEAD_LETTER").length;
  const pendingCount = entries.filter((e) => e.status === "PENDING").length;

  return (
    <div className="page-wrapper">
      <div className="topbar">
        <div>
          <h1 className="page-title">Compensation Outbox</h1>
          <p className="page-subtitle">
            Stuck stock releases — see checkout-svc&apos;s CompensationOutboxProcessor.
          </p>
        </div>
      </div>

      <div className="page-content">
        {toast && <Toast message={toast} onDone={() => setToast("")} />}
        {error && <div className="toast toast-error">{error}</div>}

        <div className="section-header">
          <div className="section-header-left">
            <span className="section-title">Pending &amp; dead-lettered entries</span>
            {!loading && (
              <span className="section-count">
                {deadLetterCount} dead-letter · {pendingCount} pending
              </span>
            )}
          </div>
        </div>

        {loading && (
          <div className="skeleton-list">
            {[1, 2].map((i) => <div key={i} className="skeleton-row" />)}
          </div>
        )}

        {!loading && entries.length === 0 && (
          <div className="empty-state">
            <p className="empty-title">Nothing stuck</p>
            <p className="empty-sub">Every compensation entry has completed normally.</p>
          </div>
        )}

        {!loading && entries.length > 0 && (
          <div className="item-list">
            {entries.map((entry) => (
              <div key={entry.id} className="item-row">
                <div className="item-meta">
                  <div className="item-name">{describeEntry(entry)}</div>
                  <div className="item-sub">
                    #{entry.id} · attempt {entry.attempts} · created {new Date(entry.createdAt).toLocaleString()}
                    {entry.lastError && ` · last error: ${entry.lastError}`}
                  </div>
                </div>
                <div className="item-actions">
                  <span className={`badge ${entry.status === "DEAD_LETTER" ? "badge-out-of-stock" : "badge-in-stock"}`}>
                    {entry.status === "DEAD_LETTER" ? "Dead letter" : "Pending"}
                  </span>
                  {entry.status === "DEAD_LETTER" && (
                    <button
                      className="icon-btn"
                      type="button"
                      onClick={() => handleRetry(entry)}
                      disabled={retryingId === entry.id}
                      title="Retry"
                    >
                      <svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor">
                        <path d="M11.534 7h3.932a.25.25 0 01.192.41l-1.966 2.36a.25.25 0 01-.384 0l-1.966-2.36a.25.25 0 01.192-.41zm-11 2h3.932a.25.25 0 00.192-.41L2.692 6.23a.25.25 0 00-.384 0L.342 8.59A.25.25 0 00.534 9z"/>
                        <path d="M8 3a5 5 0 00-4.546 2.914.5.5 0 11-.908-.417A6 6 0 0113.985 7.85a.5.5 0 11-.994.098A5 5 0 008 3zm4.546 8.086A5 5 0 013 9.15a.5.5 0 10.994-.098A6 6 0 002.015 10.15a.5.5 0 00-.985.083 6 6 0 0011.516-2.318.5.5 0 10-.908-.417A5 5 0 0112.546 11.086z"/>
                      </svg>
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
