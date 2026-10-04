import React, { useEffect, useState, useCallback } from "react";
import { getNotificationLog } from "../api";

export default function NotificationLog() {
  const [page, setPage] = useState({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [pageNum, setPageNum] = useState(0);

  const fetchLog = useCallback(async (p) => {
    setLoading(true);
    setError("");
    try {
      setPage(await getNotificationLog({ page: p, size: 20 }));
    } catch {
      setError("Failed to load notification log.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchLog(pageNum); }, [fetchLog, pageNum]);

  const failedCount = page.content.filter((n) => n.status === "FAILED").length;

  return (
    <div className="page-wrapper">
      <div className="topbar">
        <div>
          <h1 className="page-title">Notification Log</h1>
          <p className="page-subtitle">Read-only visibility into what&apos;s been sent — the actual sending happens via RabbitMQ consumers.</p>
        </div>
      </div>

      <div className="page-content">
        {error && <div className="toast toast-error">{error}</div>}

        <div className="section-header">
          <div className="section-header-left">
            <span className="section-title">Sent &amp; failed notifications</span>
            {!loading && (
              <span className="section-count">
                {page.totalElements} total{failedCount > 0 && ` · ${failedCount} failed on this page`}
              </span>
            )}
          </div>
        </div>

        {loading && (
          <div className="skeleton-list">
            {[1, 2, 3].map((i) => <div key={i} className="skeleton-row" />)}
          </div>
        )}

        {!loading && page.content.length === 0 && (
          <div className="empty-state">
            <p className="empty-title">No notifications logged</p>
            <p className="empty-sub">Nothing has been sent yet.</p>
          </div>
        )}

        {!loading && page.content.length > 0 && (
          <div className="item-list">
            {page.content.map((n) => (
              <div key={n.id} className="item-row">
                <div className="item-meta">
                  <div className="item-name">{n.subject}</div>
                  <div className="item-sub">
                    to {n.recipient} · {new Date(n.createdAt).toLocaleString()}
                    {n.status === "FAILED" && n.error && ` · ${n.error}`}
                  </div>
                </div>
                <div className="item-actions">
                  <span className={`badge ${n.status === "SENT" ? "badge-in-stock" : "badge-out-of-stock"}`}>
                    {n.status === "SENT" ? "Sent" : "Failed"}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}

        {!loading && page.totalPages > 1 && (
          <div className="pagination">
            <button
              className="btn-small"
              type="button"
              onClick={() => setPageNum((p) => Math.max(0, p - 1))}
              disabled={page.page === 0}
            >
              ← Prev
            </button>
            <span className="pagination-label">
              Page {page.page + 1} of {page.totalPages}
            </span>
            <button
              className="btn-small"
              type="button"
              onClick={() => setPageNum((p) => Math.min(page.totalPages - 1, p + 1))}
              disabled={page.page >= page.totalPages - 1}
            >
              Next →
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
