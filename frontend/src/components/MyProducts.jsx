import React, { useCallback, useEffect, useState } from "react";
import { getProducts, adjustStock } from "../api";
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

// One row's stock editor — local input state so typing doesn't refetch or
// re-render the whole list on every keystroke, only on submit.
function StockEditor({ product, onSaved, onError }) {
  const [value, setValue] = useState(String(product.stockQuantity ?? 0));
  const [saving, setSaving] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    const newQuantity = Number.parseInt(value, 10);

    if (Number.isNaN(newQuantity) || newQuantity < 0) {
      onError("Enter a stock quantity of 0 or more.");
      return;
    }

    const delta = newQuantity - (product.stockQuantity ?? 0);
    if (delta === 0) {
      return;
    }

    setSaving(true);
    try {
      // adjustStock takes a DELTA, not the absolute value the seller typed
      // — computed above from the difference against what's currently shown.
      await adjustStock(product.id, delta);
      onSaved();
    } catch (err) {
      onError(err.response?.data?.message || "Failed to update stock.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="stock-editor-row" onSubmit={handleSubmit}>
      <input
        className="qty-input"
        type="number"
        min="0"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        disabled={saving}
      />
      <button className="btn-small" type="submit" disabled={saving}>
        {saving ? "Saving…" : "Update stock"}
      </button>
    </form>
  );
}

export default function MyProducts() {
  const { user, isAdmin } = useAuth();

  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState(null);

  // The search endpoint has no ownerId filter, so fetch a page large enough to cover
  // one seller's catalogue and filter client-side. Admins see every product here.
  const fetchMyProducts = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getProducts({ page: 0, size: 100, sortBy: "NAME_A_TO_Z" });
      const mine = isAdmin
        ? data.content
        : data.content.filter((p) => p.ownerId === user?.id);
      setProducts(mine);
    } catch {
      setError("Failed to load your products.");
    } finally {
      setLoading(false);
    }
  }, [isAdmin, user?.id]);

  useEffect(() => {
    fetchMyProducts();
  }, [fetchMyProducts]);

  return (
    <div className="page-wrapper">
      <div className="topbar">
        <div>
          <h1 className="page-title">My Products</h1>
          <p className="page-subtitle">
            {isAdmin ? "Stock across every product" : "Manage stock for products you sell"}
          </p>
        </div>
      </div>

      <div className="page-content">
        {toast && (
          <Toast message={toast.message} type={toast.type} onDone={() => setToast(null)} />
        )}
        {error && <div className="toast toast-error">{error}</div>}

        {loading && (
          <div className="skeleton-list">
            <div className="skeleton-row" />
            <div className="skeleton-row" />
            <div className="skeleton-row" />
          </div>
        )}

        {!loading && products.length === 0 && (
          <div className="empty-state">
            <div className="empty-icon">
              <svg viewBox="0 0 16 16" width="20" height="20" fill="currentColor">
                <path d="M2 3h5v5H2zm7 0h5v5H9zM2 10h5v4H2zm7 0h5v4H9z" />
              </svg>
            </div>
            <p className="empty-title">No products yet</p>
            <p className="empty-sub">
              Add a product from the Products page — it'll show up here for stock management.
            </p>
          </div>
        )}

        {!loading && products.length > 0 && (
          <div className="item-list">
            {products.map((product) => (
              <div key={product.id} className="item-row">
                <div className="item-meta">
                  <div className="item-name">{product.name}</div>
                  <div className="item-sub">
                    {formatPrice(product.price)}
                    <span className="badge badge-category">{product.category}</span>
                    <span className="badge badge-in-stock">
                      {product.stockQuantity ?? 0} in stock
                    </span>
                  </div>
                </div>
                <div className="item-actions">
                  <StockEditor
                    product={product}
                    onSaved={() => {
                      setToast({ message: `Updated stock for "${product.name}".`, type: "success" });
                      fetchMyProducts();
                    }}
                    onError={(message) => setError(message)}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
