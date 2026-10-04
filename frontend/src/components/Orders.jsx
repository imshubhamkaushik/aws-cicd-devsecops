import React, { useEffect, useState, useCallback } from "react";
import PropTypes from "prop-types";
import { Link } from "react-router-dom";
import {
  getOrders,
  cancelOrder,
  payOrder,
  updateOrderStatus,
  getOrderTracking,
  getOrderInvoice,
} from "../api";
import { useAuth } from "../context/AuthContext";
import Toast from "./Toast";

function formatPrice(value) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(value);
}

function formatDate(value) {
  return new Date(value).toLocaleString("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

const STATUS_BADGE_CLASS = {
  PENDING_PAYMENT: "badge-low-stock",
  PAYMENT_PROCESSING: "badge-low-stock",
  CONFIRMED: "badge-in-stock",
  SHIPPED: "badge-category",
  DELIVERED: "badge-admin",
  CANCELLED: "badge-out-of-stock",
};

function StatusBadge({ status }) {
  return (
    <span className={`badge ${STATUS_BADGE_CLASS[status] || "badge-user"}`}>
      {status.replace("_", " ")}
    </span>
  );
}

StatusBadge.propTypes = {
  status: PropTypes.string.isRequired,
};

// -------- Product picker --------

// -------- Payment form --------

function PaymentForm({ order, onPaid, onError }) {
  const [method, setMethod] = useState("CARD");
  const [cardLast4, setCardLast4] = useState("");
  const [upiId, setUpiId] = useState("");
  const [paying, setPaying] = useState(false);
  // Stable for the lifetime of this payment form. A retry after a transport
  // timeout therefore repeats the same logical payment instead of charging
  // twice. We intentionally reset it whenever the payment inputs change.
  const [idempotencyKey, setIdempotencyKey] = useState(() =>
    crypto.randomUUID(),
  );

  let paymentButtonText = `Pay ${formatPrice(order.totalAmount)}`;

  if (method === "COD") {
    paymentButtonText = `Confirm ${formatPrice(order.totalAmount)} on delivery`;
  }

  if (paying) {
    paymentButtonText = "Processing…";
  }

  const handlePay = async (event) => {
    event.preventDefault();
    setPaying(true);

    let cardValue;
    let upiValue;

    if (method === "CARD") {
      cardValue = cardLast4 || "4242";
    } else if (method === "UPI") {
      upiValue = upiId || "buyer@upi";
    }

    try {
      const result = await payOrder(
        order.id,
        method,
        cardValue,
        upiValue,
        idempotencyKey,
      );

      onPaid(result);
    } catch (error) {
      onError(error.response?.data?.message || "Payment failed.");
    } finally {
      setPaying(false);
    }
  };

  return (
    <form className="payment-form" onSubmit={handlePay}>
      <select
        className="sort-select"
        value={method}
        onChange={(event) => {
          setMethod(event.target.value);
          setIdempotencyKey(crypto.randomUUID());
        }}
        disabled={paying}
      >
        <option value="CARD">Card</option>
        <option value="UPI">UPI</option>
        <option value="COD">Cash on Delivery</option>
      </select>

      {method === "CARD" && (
        <input
          className="qty-input"
          style={{ width: 90 }}
          placeholder="Card last 4"
          maxLength={4}
          value={cardLast4}
          onChange={(event) => {
            setCardLast4(event.target.value.replace(/\D/g, ""));
            setIdempotencyKey(crypto.randomUUID());
          }}
          disabled={paying}
        />
      )}

      {method === "UPI" && (
        <input
          className="qty-input"
          style={{ width: 140 }}
          placeholder="you@upi"
          value={upiId}
          onChange={(event) => {
            setUpiId(event.target.value);
            setIdempotencyKey(crypto.randomUUID());
          }}
          disabled={paying}
        />
      )}

      <button
        className="btn-small btn-primary-small"
        type="submit"
        disabled={paying}
      >
        {paymentButtonText}
      </button>

      <span className="auth-help-text" style={{ margin: 0 }}>
        {method === "CARD" &&
          'Mock payment — any card works, "0000" simulates a decline.'}

        {method === "UPI" &&
          'Mock payment — any VPA works, one starting with "fail@" simulates a decline.'}

        {method === "COD" &&
          "Pay in cash when your order arrives — no payment is captured now."}
      </span>
    </form>
  );
}

PaymentForm.propTypes = {
  order: PropTypes.shape({
    id: PropTypes.number,
    totalAmount: PropTypes.number,
  }).isRequired,
  onPaid: PropTypes.func.isRequired,
  onError: PropTypes.func.isRequired,
};

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
      <div className="tracking-panel">
        <span className="auth-help-text" style={{ margin: 0 }}>
          Loading tracking…
        </span>
      </div>
    );
  }

  if (!tracking) {
    return null;
  }

  return (
    <div className="tracking-panel">
      {tracking.events.map((event) => {
        const eventKey =
          event.id ?? `${event.status}-${event.createdAt}-${event.note ?? ""}`;

        return (
          <div key={eventKey} className="tracking-step">
            <div className="tracking-step-dot" />

            <div>
              <div className="tracking-step-status">
                {event.status.replace("_", " ")}
              </div>

              {event.note && (
                <div className="tracking-step-note">{event.note}</div>
              )}

              <div className="tracking-step-date">
                {formatDate(event.createdAt)}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

TrackingPanel.propTypes = {
  orderId: PropTypes.number.isRequired,
  onError: PropTypes.func.isRequired,
};

// -------- Invoice modal --------

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
    <div className="modal-overlay">
      <button
        type="button"
        className="modal-backdrop"
        aria-label="Close invoice"
        onClick={onClose}
      />

      <dialog
        open
        className="modal-card"
        aria-label={`Invoice ${invoice.invoiceNumber}`}
        onCancel={onClose}
      >
        <div className="modal-header">
          <div>
            <p
              className="form-panel-label"
              style={{ marginBottom: 2 }}
            >
              {invoice.invoiceNumber}
            </p>

            <span className="auth-help-text" style={{ margin: 0 }}>
              {invoice.sellerName}
            </span>
          </div>

          <button
            className="modal-close"
            onClick={onClose}
            title="Close"
            type="button"
          >
            ✕
          </button>
        </div>

        <div className="invoice-meta-row">
          <span>Order</span>
          <span>#{invoice.orderId}</span>
        </div>

        <div className="invoice-meta-row">
          <span>Date</span>
          <span>{formatDate(invoice.orderDate)}</span>
        </div>

        <div className="invoice-meta-row">
          <span>Billed to</span>
          <span>{invoice.customerEmail}</span>
        </div>

        {invoice.billingAddress && (
          <div className="invoice-meta-row">
            <span>Shipping address</span>
            <span>
              {invoice.billingAddress.line1}, {invoice.billingAddress.city},{" "}
              {invoice.billingAddress.state} {invoice.billingAddress.pincode}
            </span>
          </div>
        )}

        <div className="invoice-meta-row">
          <span>Payment</span>
          <span>
            {invoice.paymentMethod}
            {invoice.paymentReference ? ` · ${invoice.paymentReference}` : ""}
          </span>
        </div>

        <table className="invoice-table">
          <thead>
            <tr>
              <th>Item</th>
              <th className="num">Qty</th>
              <th className="num">Unit price</th>
              <th className="num">Subtotal</th>
            </tr>
          </thead>

          <tbody>
            {invoice.items.map((line) => {
              const lineKey =
                line.productId ??
                `${line.productName}-${line.unitPrice}-${line.quantity}`;

              return (
                <tr key={lineKey}>
                  <td>{line.productName}</td>
                  <td className="num">{line.quantity}</td>
                  <td className="num">{formatPrice(line.unitPrice)}</td>
                  <td className="num">{formatPrice(line.subtotal)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>

        <div className="invoice-totals">
          <div className="invoice-totals-row">
            <span>Items subtotal</span>
            <span>{formatPrice(invoice.itemsSubtotal)}</span>
          </div>

          <div className="invoice-totals-row">
            <span>Taxable value</span>
            <span>{formatPrice(invoice.taxableValue)}</span>
          </div>

          <div className="invoice-totals-row">
            <span>GST ({invoice.taxRatePercent}%, included)</span>
            <span>{formatPrice(invoice.taxAmount)}</span>
          </div>

          <div className="invoice-totals-row invoice-grand-total">
            <span>Total</span>
            <span>{formatPrice(invoice.totalAmount)}</span>
          </div>
        </div>

        <div
          style={{
            display: "flex",
            gap: 8,
            marginTop: 18,
          }}
        >
          <button
            className="btn-outline"
            onClick={() => globalThis.print()}
            type="button"
          >
            Print / Save as PDF
          </button>

          <button className="btn-outline" onClick={onClose} type="button">
            Close
          </button>
        </div>
      </dialog>
    </div>
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
    <div className="page-wrapper">
      <div className="topbar">
        <div>
          <h1 className="page-title">Orders</h1>

          <p className="page-subtitle">
            {isAdmin ? "Manage every order" : "Track your orders"}
          </p>
        </div>
      </div>

      <div className="page-content">
        {toast && (
          <Toast
            message={toast.message}
            type={toast.type}
            onDone={() => setToast(null)}
          />
        )}

        {error && <div className="toast toast-error">{error}</div>}

        <div className="section-header">
          <div className="section-header-left">
            <span className="section-title">
              {isAdmin ? "All orders" : "Your orders"}
            </span>

            {!loading && (
              <span className="section-count">{orders.length} orders</span>
            )}
          </div>
        </div>

        {loading && (
          <div className="skeleton-list">
            <div className="skeleton-row" />
            <div className="skeleton-row" />
            <div className="skeleton-row" />
          </div>
        )}

        {!loading && orders.length === 0 && (
          <div className="empty-state">
            <div className="empty-icon">
              <svg
                viewBox="0 0 16 16"
                width="20"
                height="20"
                fill="currentColor"
              >
                <path d="M1 2.5A.5.5 0 011.5 2H3a.5.5 0 01.485.379L3.89 4H14.5a.5.5 0 01.491.592l-1 5A.5.5 0 0113.5 10H5a.5.5 0 01-.491-.408L3.01 4.607 2.61 3H1.5a.5.5 0 01-.5-.5zM5 12a1.5 1.5 0 100 3 1.5 1.5 0 000-3zm7 0a1.5 1.5 0 100 3 1.5 1.5 0 000-3z" />
              </svg>
            </div>

            <p className="empty-title">No orders yet</p>

            <p className="empty-sub">
              Add items to your cart and check out to place your first order.
            </p>
            <Link to="/cart" className="btn-outline" style={{ marginTop: 10 }}>
              Go to Cart
            </Link>
          </div>
        )}

        {!loading && orders.length > 0 && (
          <div className="item-list">
            {orders.map((order) => (
              <div key={order.id} className="item-row order-row">
                <div className="item-meta">
                  <div className="item-name">
                    Order #{order.id}
                    {isAdmin && (
                      <span className="item-name-hint">
                        {" · "}user #{order.userId}
                      </span>
                    )}
                  </div>

                  <div className="item-sub">
                    {order.items
                      .map((item) => `${item.quantity} × ${item.productName}`)
                      .join(", ")}
                  </div>

                  <div className="item-sub item-sub-faint">
                    {formatDate(order.createdAt)}
                  </div>

                  {order.status === "PENDING_PAYMENT" && (
                    <PaymentForm
                      order={order}
                      onPaid={handlePaid}
                      onError={setError}
                    />
                  )}

                  <div className="order-detail-actions">
                    <button
                      className="btn-small"
                      type="button"
                      onClick={() => toggleTracking(order.id)}
                    >
                      {trackingOpenFor === order.id
                        ? "Hide tracking"
                        : "Track order"}
                    </button>

                    {order.status !== "PENDING_PAYMENT" &&
                      order.status !== "PAYMENT_PROCESSING" && (
                        <button
                          className="btn-small"
                          type="button"
                          onClick={() => {
                            setInvoiceOpenFor(order.id);
                          }}
                        >
                          Invoice
                        </button>
                      )}
                  </div>

                  {trackingOpenFor === order.id && (
                    <TrackingPanel orderId={order.id} onError={setError} />
                  )}
                </div>

                <div className="item-actions">
                  <StatusBadge status={order.status} />

                  <span className="price-tag">
                    {formatPrice(order.totalAmount)}
                  </span>

                  {(order.status === "PENDING_PAYMENT" ||
                    order.status === "CONFIRMED") && (
                    <button
                      className="btn-small"
                      type="button"
                      onClick={() => handleCancel(order)}
                    >
                      Cancel
                    </button>
                  )}

                  {isAdmin && order.status === "CONFIRMED" && (
                    <button
                      className="btn-small"
                      type="button"
                      onClick={() => {
                        void handleAdvanceStatus(order, "SHIPPED");
                      }}
                    >
                      Mark shipped
                    </button>
                  )}

                  {isAdmin && order.status === "SHIPPED" && (
                    <button
                      className="btn-small"
                      type="button"
                      onClick={() => {
                        void handleAdvanceStatus(order, "DELIVERED");
                      }}
                    >
                      Mark delivered
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

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
