import React, { useEffect, useState, useCallback } from "react";
import { CheckCircle2, RotateCw, X } from "lucide-react";
import { getOutboxEntries, retryOutboxEntry } from "../api";
import { formatDateTime } from "../lib/format";
import Toast from "./Toast";
import { Button } from "./ui/button";
import { Badge } from "./ui/badge";
import { Panel, Skeleton, PageHeader, EmptyState } from "./ui/surface";

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
    <div>
      <PageHeader
        title="Failed stock releases"
        description="When an order is cancelled but inventory-svc cannot be reached, the stock release is queued here and retried. Entries that keep failing become dead letters you can retry by hand."
      />

      {toast && <Toast message={toast} type="success" onDone={() => setToast("")} />}
      {error && (
        <div
          role="alert"
          className="mb-4 flex items-start justify-between gap-3 rounded-control border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger"
        >
          <span>{error}</span>
          <button
            type="button"
            aria-label="Dismiss error"
            className="rounded p-0.5 hover:bg-danger/10"
            onClick={() => setError("")}
          >
            <X className="size-4" aria-hidden="true" />
          </button>
        </div>
      )}

      {!loading && entries.length > 0 && (
        <p className="mb-3 text-sm text-ink-2">
          {deadLetterCount} dead-letter · {pendingCount} pending
        </p>
      )}

      {loading && (
        <div className="space-y-2" aria-busy="true">
          {[1, 2].map((i) => (
            <Skeleton key={i} className="h-16" />
          ))}
        </div>
      )}

      {!loading && entries.length === 0 && (
        <EmptyState
          icon={CheckCircle2}
          title="Nothing stuck"
          description="Every compensation entry has completed normally."
        />
      )}

      {!loading && entries.length > 0 && (
        <Panel>
          <ul className="divide-y divide-line">
            {entries.map((entry) => (
              <li
                key={entry.id}
                className="flex flex-wrap items-center gap-x-4 gap-y-2 p-3 sm:p-4"
              >
                <div className="min-w-0 flex-1 basis-64">
                  <p className="font-semibold">{describeEntry(entry)}</p>
                  <p className="tnum text-sm text-ink-2">
                    #{entry.id} · attempt {entry.attempts} · created {formatDateTime(entry.createdAt)}
                    {entry.lastError && ` · last error: ${entry.lastError}`}
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <Badge tone={entry.status === "DEAD_LETTER" ? "danger" : "warning"}>
                    {entry.status === "DEAD_LETTER" ? "Dead letter" : "Pending"}
                  </Badge>
                  {entry.status === "DEAD_LETTER" && (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => handleRetry(entry)}
                      disabled={retryingId === entry.id}
                      title="Retry"
                    >
                      <RotateCw aria-hidden="true" /> Retry
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </Panel>
      )}
    </div>
  );
}
