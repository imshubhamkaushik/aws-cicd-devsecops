import React, { useEffect, useState, useCallback } from "react";
import { BellOff, ChevronLeft, ChevronRight } from "lucide-react";
import { getNotificationLog } from "../api";
import { formatDateTime } from "../lib/format";
import { Button } from "./ui/button";
import { Badge } from "./ui/badge";
import { Panel, Skeleton, PageHeader, EmptyState } from "./ui/surface";

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
    <div>
      <PageHeader
        title="Notification log"
        description="Every email the shop has tried to send, and whether it went through."
      />

      {error && (
        <p role="alert" className="mb-4 rounded-control bg-danger-soft px-3 py-2.5 text-sm text-danger">
          {error} Refresh the page to try again.
        </p>
      )}

      <div className="mb-3 flex flex-wrap items-baseline gap-x-3">
        <h2 className="text-lg font-semibold">Sent and failed notifications</h2>
        {!loading && (
          <span className="tnum text-sm text-ink-2">
            {page.totalElements} total{failedCount > 0 && ` · ${failedCount} failed on this page`}
          </span>
        )}
      </div>

      {loading && (
        <Panel className="divide-y divide-line" aria-busy="true">
          {[1, 2, 3].map((i) => (
            <div key={i} className="p-4">
              <Skeleton className="h-5 w-1/2" />
              <Skeleton className="mt-2 h-4 w-1/3" />
            </div>
          ))}
        </Panel>
      )}

      {!loading && page.content.length === 0 && (
        <EmptyState
          icon={BellOff}
          title="No notifications logged"
          description="Nothing has been sent yet. Entries appear here after an order or account email goes out."
        />
      )}

      {!loading && page.content.length > 0 && (
        <Panel className="overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-line bg-surface-2 text-ink-2 max-md:sr-only">
              <tr>
                <th scope="col" className="px-4 py-2.5 font-semibold">Subject</th>
                <th scope="col" className="px-4 py-2.5 font-semibold">Recipient</th>
                <th scope="col" className="px-4 py-2.5 font-semibold">Time</th>
                <th scope="col" className="px-4 py-2.5 font-semibold">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line max-md:block">
              {page.content.map((n) => (
                <tr key={n.id} className="align-top max-md:flex max-md:flex-col max-md:gap-1 max-md:p-4">
                  <td className="px-4 py-3 max-md:p-0">
                    <span className="font-medium">{n.subject}</span>
                    {n.status === "FAILED" && n.error && (
                      <span className="mt-0.5 block text-danger">{n.error}</span>
                    )}
                  </td>
                  <td className="break-all px-4 py-3 text-ink-2 max-md:p-0">
                    <span className="md:hidden">To </span>
                    {n.recipient}
                  </td>
                  <td className="tnum whitespace-nowrap px-4 py-3 text-ink-2 max-md:p-0">
                    {formatDateTime(n.createdAt)}
                  </td>
                  <td className="px-4 py-3 max-md:p-0">
                    <Badge tone={n.status === "SENT" ? "success" : n.status === "FAILED" ? "danger" : "neutral"}>
                      {n.status === "SENT" ? "Sent" : n.status === "FAILED" ? "Failed" : n.status}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      )}

      {!loading && page.totalPages > 1 && (
        <nav aria-label="Pagination" className="mt-4 flex items-center justify-center gap-3">
          <Button
            variant="secondary"
            onClick={() => setPageNum((p) => Math.max(0, p - 1))}
            disabled={page.page === 0}
          >
            <ChevronLeft aria-hidden="true" />
            Prev
          </Button>
          <span className="tnum text-sm text-ink-2">
            Page {page.page + 1} of {page.totalPages}
          </span>
          <Button
            variant="secondary"
            onClick={() => setPageNum((p) => Math.min(page.totalPages - 1, p + 1))}
            disabled={page.page >= page.totalPages - 1}
          >
            Next
            <ChevronRight aria-hidden="true" />
          </Button>
        </nav>
      )}
    </div>
  );
}
