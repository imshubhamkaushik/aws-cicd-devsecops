import React, { useEffect, useState, useCallback } from "react";
import PropTypes from "prop-types";
import { Link } from "react-router-dom";
import { Check, ChevronDown, ChevronUp, FileText, Package, Printer, X } from "lucide-react";
import {
  getOrders,
  cancelOrder,
  updateOrderStatus,
  getOrderTracking,
  getOrderInvoice,
} from "../api";
import { useAuth } from "../context/AuthContext";
import { cn } from "../lib/utils";
import { formatPrice, formatDateTime as formatDate } from "../lib/format";
import Toast from "./Toast";
import { Button } from "./ui/button";
import { Badge } from "./ui/badge";
import { Input, Select, Field } from "./ui/field";
import { Panel, Skeleton, PageHeader, EmptyState } from "./ui/surface";
import { Dialog, DialogContent, DialogTitle, DialogDescription } from "./ui/dialog";
import PaymentForm from "./orders/PaymentForm";

const STATUS_LABEL = {
  PENDING_PAYMENT: "Awaiting payment",
  PAYMENT_PROCESSING: "Processing payment",
  CONFIRMED: "Confirmed",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
};

const STATUS_TONE = {
  PENDING_PAYMENT: "warning",
  PAYMENT_PROCESSING: "warning",
  CONFIRMED: "success",
  SHIPPED: "info",
  DELIVERED: "success",
  CANCELLED: "danger",
};

function StatusBadge({ status }) {
  return (
    <Badge tone={STATUS_TONE[status] || "neutral"}>
      {STATUS_LABEL[status] || status}
    </Badge>
  );
}

StatusBadge.propTypes = {
  status: PropTypes.string.isRequired,
};

// The happy path, left to right. PAYMENT_PROCESSING sits on the first step.
const TRACK_STEPS = [
  { key: "PENDING_PAYMENT", label: "Placed" },
  { key: "CONFIRMED", label: "Confirmed" },
  { key: "SHIPPED", label: "Shipped" },
  { key: "DELIVERED", label: "Delivered" },
];

function OrderProgress({ status }) {
  if (status === "CANCELLED") {
    return (
      <p className="flex items-center gap-2 text-sm text-danger">
        <X className="size-4" aria-hidden="true" /> This order was cancelled.
      </p>
    );
  }

  const current = Math.max(
    0,
    TRACK_STEPS.findIndex(
      (step) => step.key === (status === "PAYMENT_PROCESSING" ? "PENDING_PAYMENT" : status),
    ),
  );

  return (
    <ol className="flex items-start" aria-label="Order progress">
      {TRACK_STEPS.map((step, index) => {
        const done = index < current || status === "DELIVERED";
        const active = index === current && status !== "DELIVERED";

        return (
          <li
            key={step.key}
            className="relative flex flex-1 flex-col items-center gap-1.5 text-center"
            aria-current={active ? "step" : undefined}
          >
            {index > 0 && (
              <span
                aria-hidden="true"
                className={cn(
                  "absolute right-1/2 top-3 h-0.5 w-full -translate-y-1/2",
                  index <= current || status === "DELIVERED" ? "bg-brand" : "bg-line",
                )}
              />
            )}
            <span
              className={cn(
                "relative z-10 grid size-6 place-items-center rounded-full border-2 text-brand-ink",
                done && "border-brand bg-brand",
                active && "border-brand bg-surface text-brand",
                !done && !active && "border-line bg-surface",
              )}
            >
              {done ? (
                <Check className="size-3.5" aria-hidden="true" />
              ) : (
                active && <span className="size-2 rounded-full bg-brand" />
              )}
            </span>
            <span className={cn("text-xs", done || active ? "font-semibold text-ink" : "text-ink-2")}>
              {step.label}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

OrderProgress.propTypes = {
  status: PropTypes.string.isRequired,
};

// Payment form is extracted to ./orders/PaymentForm.jsx.

// -------- Tracking --------

function TrackingPanel({ orderId, onError }) {
  const [tracking, setTracking] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    getOrderTracking(orderId)
      .then((data) => {
        if (!cancelled) {
          setTracking(data);
        }
      })
      .catch(() => {
        if (!cancelled) {
          onError("Failed to load tracking for this order.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderId]);

  if (loading) {
    return (
      <div className="mt-4 space-y-2" aria-busy="true">
        <p className="text-sm text-ink-2">Loading tracking…</p>
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="h-4 w-1/3" />
      </div>
    );
  }

  if (!tracking) {
    return null;
  }

  return (
    <ol className="mt-4 border-l-2 border-line pl-5">
      {tracking.events.map((event) => {
        const eventKey =
          event.id ?? `${event.status}-${event.createdAt}-${event.note ?? ""}`;

        return (
          <li key={eventKey} className="relative pb-4 last:pb-0">
            <span
              aria-hidden="true"
              className="absolute -left-[27px] top-1 size-2.5 rounded-full border-2 border-brand bg-surface"
            />
            <p className="text-xs font-semibold tracking-wide text-brand">
              {event.status.replace("_", " ")}
            </p>
            {event.note && <p className="text-sm">{event.note}</p>}
            <p className="tnum text-xs text-ink-2">{formatDate(event.createdAt)}</p>
          </li>
        );
      })}
    </ol>
  );
}

TrackingPanel.propTypes = {
  orderId: PropTypes.number.isRequired,
  onError: PropTypes.func.isRequired,
};

// -------- Invoice dialog --------

function MetaRow({ label, children }) {
  return (
    <div className="flex justify-between gap-4 py-1.5 text-sm">
      <dt className="text-ink-2">{label}</dt>
      <dd className="text-right">{children}</dd>
    </div>
  );
}

MetaRow.propTypes = {
  label: PropTypes.string.isRequired,
  children: PropTypes.node,
};

function InvoiceModal({ orderId, onClose, onError }) {
  const [invoice, setInvoice] = useState(null);

  useEffect(() => {
    let cancelled = false;

    getOrderInvoice(orderId)
      .then((data) => {
        if (!cancelled) {
          setInvoice(data);
        }
      })
      .catch(() => {
        if (!cancelled) {
          onError("Failed to load the invoice for this order.");
          onClose();
        }
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderId]);

  if (!invoice) {
    return null;
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-xl">
        <DialogTitle>
          Invoice <span className="tnum text-brand">{invoice.invoiceNumber}</span>
        </DialogTitle>
        <DialogDescription>{invoice.sellerName}</DialogDescription>

        <dl className="mt-4 divide-y divide-line border-y border-line">
          <MetaRow label="Order">#{invoice.orderId}</MetaRow>
          <MetaRow label="Date">{formatDate(invoice.orderDate)}</MetaRow>
          <MetaRow label="Billed to">{invoice.customerEmail}</MetaRow>
          {invoice.billingAddress && (
            <MetaRow label="Shipping address">
              {invoice.billingAddress.line1}, {invoice.billingAddress.city},{" "}
              {invoice.billingAddress.state} {invoice.billingAddress.pincode}
            </MetaRow>
          )}
          <MetaRow label="Payment">
            {invoice.paymentMethod}
            {invoice.paymentReference ? ` · ${invoice.paymentReference}` : ""}
          </MetaRow>
        </dl>

        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-line text-left text-xs text-ink-2">
                <th className="py-2 font-semibold">Item</th>
                <th className="py-2 text-right font-semibold">Qty</th>
                <th className="py-2 text-right font-semibold">Unit price</th>
                <th className="py-2 text-right font-semibold">Subtotal</th>
              </tr>
            </thead>

            <tbody className="tnum">
              {invoice.items.map((line) => {
                const lineKey =
                  line.productId ??
                  `${line.productName}-${line.unitPrice}-${line.quantity}`;

                return (
                  <tr key={lineKey} className="border-b border-line last:border-0">
                    <td className="py-2 pr-2">{line.productName}</td>
                    <td className="py-2 text-right">{line.quantity}</td>
                    <td className="py-2 text-right">{formatPrice(line.unitPrice)}</td>
                    <td className="py-2 text-right">{formatPrice(line.subtotal)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <dl className="tnum mt-4 space-y-1 text-sm">
          <div className="flex justify-between">
            <dt className="text-ink-2">Items subtotal</dt>
            <dd>{formatPrice(invoice.itemsSubtotal)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-ink-2">Taxable value</dt>
            <dd>{formatPrice(invoice.taxableValue)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-ink-2">GST ({invoice.taxRatePercent}%, included)</dt>
            <dd>{formatPrice(invoice.taxAmount)}</dd>
          </div>
          <div className="flex justify-between border-t border-line pt-2 text-base font-semibold">
            <dt>Total</dt>
            <dd>{formatPrice(invoice.totalAmount)}</dd>
          </div>
        </dl>

        <Button
          variant="secondary"
          className="mt-5"
          onClick={() => globalThis.print()}
        >
          <Printer aria-hidden="true" /> Print / Save as PDF
        </Button>
      </DialogContent>
    </Dialog>
  );
}

InvoiceModal.propTypes = {
  orderId: PropTypes.number.isRequired,
  onClose: PropTypes.func.isRequired,
  onError: PropTypes.func.isRequired,
};

// -------- Orders page --------

export default function Orders() {
  const { isAdmin } = useAuth();

  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState(null);

  const [trackingOpenFor, setTrackingOpenFor] = useState(null);
  const [invoiceOpenFor, setInvoiceOpenFor] = useState(null);

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const data = await getOrders({
        sort: "id,desc",
      });

      setOrders(data.content || []);
    } catch {
      setError("Failed to load orders. Is the backend running?");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchOrders();
  }, [fetchOrders]);

  const handlePaid = (result) => {
    void fetchOrders();

    if (result.payment.status !== "SUCCEEDED") {
      setToast({
        message:
          "Payment declined — the order was cancelled and stock released.",
        type: "error",
      });

      return;
    }

    const isCod = result.order.paymentMethod === "COD";

    let successMessage = "Payment successful — order confirmed!";

    if (isCod) {
      successMessage = "Order confirmed — pay in cash when it arrives.";
    }

    setToast({
      message: successMessage,
      type: "success",
    });
  };

  const handleCancel = async (order) => {
    if (
      !globalThis.confirm(`Cancel order #${order.id}? Stock will be restored.`)
    ) {
      return;
    }

    try {
      await cancelOrder(order.id);
      await fetchOrders();

      setToast({
        message: `Order #${order.id} cancelled.`,
        type: "success",
      });
    } catch (requestError) {
      setError(
        requestError.response?.data?.message || "Failed to cancel order.",
      );
    }
  };

  const handleAdvanceStatus = async (order, nextStatus) => {
    try {
      await updateOrderStatus(order.id, nextStatus);

      await fetchOrders();

      setToast({
        message: `Order #${order.id} marked ${nextStatus.toLowerCase()}.`,
        type: "success",
      });
    } catch (requestError) {
      setError(
        requestError.response?.data?.message ||
          "Failed to update order status.",
      );
    }
  };

  const toggleTracking = (orderId) => {
    setTrackingOpenFor((currentOrderId) => {
      if (currentOrderId === orderId) {
        return null;
      }

      return orderId;
    });
  };

  return (
    <div>
      <PageHeader
        title="Orders"
        description={isAdmin ? "Manage every order." : "Track your orders and pay for pending ones."}
      />

      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onDone={() => setToast(null)}
        />
      )}

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

      {!loading && orders.length > 0 && (
        <p className="mb-3 text-sm text-ink-2">
          {isAdmin ? "All orders" : "Your orders"} · {orders.length}{" "}
          {orders.length === 1 ? "order" : "orders"}
        </p>
      )}

      {loading && (
        <div className="space-y-3" aria-busy="true">
          <Skeleton className="h-36" />
          <Skeleton className="h-36" />
          <Skeleton className="h-36" />
        </div>
      )}

      {!loading && orders.length === 0 && (
        <EmptyState
          icon={Package}
          title="No orders yet"
          description="Add items to your cart and check out to place your first order."
          action={
            <Button asChild>
              <Link to="/cart">Go to cart</Link>
            </Button>
          }
        />
      )}

      {!loading && orders.length > 0 && (
        <ul className="space-y-4">
          {orders.map((order) => {
            const isOpen = trackingOpenFor === order.id;
            const hasInvoice =
              order.status !== "PENDING_PAYMENT" &&
              order.status !== "PAYMENT_PROCESSING";

            return (
              <li key={order.id}>
                <Panel className="p-4 sm:p-5">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                      <h2 className="text-lg font-semibold">
                        Order #{order.id}
                        {isAdmin && (
                          <span className="text-sm font-normal text-ink-2">
                            {" · "}user #{order.userId}
                          </span>
                        )}
                      </h2>
                      <p className="tnum text-xs text-ink-2">{formatDate(order.createdAt)}</p>
                    </div>

                    <div className="flex items-center gap-3">
                      <StatusBadge status={order.status} />
                      <span className="tnum text-lg font-semibold">
                        {formatPrice(order.totalAmount)}
                      </span>
                    </div>
                  </div>

                  <p className="mt-3 text-sm text-ink-2">
                    {order.items
                      .map((item) => `${item.quantity} × ${item.productName}`)
                      .join(", ")}
                  </p>

                  <div className="mt-5">
                    <OrderProgress status={order.status} />
                  </div>

                  {order.status === "PENDING_PAYMENT" && (
                    <PaymentForm
                      order={order}
                      onPaid={handlePaid}
                      onError={setError}
                    />
                  )}

                  <div className="mt-5 flex flex-wrap gap-2 border-t border-line pt-4">
                    <Button
                      variant="secondary"
                      size="sm"
                      aria-expanded={isOpen}
                      onClick={() => toggleTracking(order.id)}
                    >
                      {isOpen ? (
                        <ChevronUp aria-hidden="true" />
                      ) : (
                        <ChevronDown aria-hidden="true" />
                      )}
                      {isOpen ? "Hide tracking" : "Track order"}
                    </Button>

                    {hasInvoice && (
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => setInvoiceOpenFor(order.id)}
                      >
                        <FileText aria-hidden="true" /> Invoice
                      </Button>
                    )}

                    {isAdmin && order.status === "CONFIRMED" && (
                      <Button
                        size="sm"
                        onClick={() => {
                          void handleAdvanceStatus(order, "SHIPPED");
                        }}
                      >
                        Mark shipped
                      </Button>
                    )}

                    {isAdmin && order.status === "SHIPPED" && (
                      <Button
                        size="sm"
                        onClick={() => {
                          void handleAdvanceStatus(order, "DELIVERED");
                        }}
                      >
                        Mark delivered
                      </Button>
                    )}

                    {(order.status === "PENDING_PAYMENT" ||
                      order.status === "CONFIRMED") && (
                      <Button
                        variant="danger-outline"
                        size="sm"
                        className="sm:ml-auto"
                        onClick={() => handleCancel(order)}
                      >
                        Cancel order
                      </Button>
                    )}
                  </div>

                  {isOpen && <TrackingPanel orderId={order.id} onError={setError} />}
                </Panel>
              </li>
            );
          })}
        </ul>
      )}

      {invoiceOpenFor && (
        <InvoiceModal
          orderId={invoiceOpenFor}
          onClose={() => setInvoiceOpenFor(null)}
          onError={setError}
        />
      )}
    </div>
  );
}
