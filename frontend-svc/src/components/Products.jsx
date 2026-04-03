import React, { useEffect, useState, useCallback } from "react";
import PropTypes from "prop-types";
import { getProducts, createProduct, deleteProduct } from "../api";

// Format number as Indian rupee string e.g. ₹1,899.00
function formatPrice(value) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(value);
}

function Toast({ message, type, onDone }) {
  useEffect(() => {
    const t = setTimeout(onDone, 3000);
    return () => clearTimeout(t);
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

const ProductIcon = () => (
  <svg viewBox="0 0 16 16" width="15" height="15" fill="currentColor">
    <path d="M0 1.5A.5.5 0 01.5 1H2a.5.5 0 01.485.379L2.89 3H14.5a.5.5 0 01.491.592l-1.5 8A.5.5 0 0113 12H4a.5.5 0 01-.491-.408L2.01 3.607 1.61 2H.5a.5.5 0 01-.5-.5zM5 12a2 2 0 100 4 2 2 0 000-4zm7 0a2 2 0 100 4 2 2 0 000-4z"/>
  </svg>
);

export default function Products({ currentUser }) {
  const [products, setProducts]   = useState([]);
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState("");
  const [toast, setToast]         = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // Form state
  const [name, setName]           = useState("");
  const [description, setDescription] = useState("");
  const [price, setPrice]         = useState("");

  // Search
  const [search, setSearch]       = useState("");

  const fetchProducts = useCallback(async () => {
    if (!currentUser?.id) return;
    setLoading(true);
    setError("");
    try {
      const data = await getProducts(currentUser.id);
      setProducts(Array.isArray(data) ? data : []);
    } catch {
      setError("Failed to load products. Is the backend running?");
    } finally {
      setLoading(false);
    }
  }, [currentUser?.id]);

  useEffect(() => { fetchProducts(); }, [fetchProducts]);

  const handleAdd = async (e) => {
    e.preventDefault();
    if (!name.trim() || !price) return;
    const numericPrice = Number.parseFloat(price);
    if (Number.isNaN(numericPrice) || numericPrice <= 0) {
      setError("Price must be a positive number.");
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      await createProduct(
        { name: name.trim(), description: description.trim(), price: numericPrice },
        currentUser.id
      );
      setName(""); setDescription(""); setPrice("");
      await fetchProducts();
      setToast({message:"Product added successfully.", type: "success"});
    } catch (err) {
      const msg = err.response?.data?.message || "Failed to add product.";
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (product) => {
    if (!globalThis.confirm(`Remove "${product.name}"? This cannot be undone.`)) return;
    try {
      await deleteProduct(product.id, currentUser.id);
      await fetchProducts();
      setToast({ message:`"${product.name}" removed.`, type:"success"});
    } catch {
      setError("Failed to delete product.");
    }
  };

  const filtered = products.filter(
    (p) =>
      p.name.toLowerCase().includes(search.toLowerCase()) ||
      (p.description || "").toLowerCase().includes(search.toLowerCase())
  );

  // No user selected — show warning banner, block content
  const noUser = !currentUser?.id;

  return (
    <div className="page-wrapper">
      <div className="topbar">
        <div>
          <h1 className="page-title">Products</h1>
          <p className="page-subtitle">
            {currentUser
              ? `Browsing as ${currentUser.name}`
              : "Browse and manage inventory"}
          </p>
        </div>
      </div>

      <div className="page-content">
        {toast && <Toast message={toast.message} type={toast.type} onDone={() => setToast(null)} />}
        {error && !noUser && <div className="toast toast-error">{error}</div>}

        {/* No active user warning */}
        {noUser && (
          <div className="banner-warning">
            <div className="banner-icon">
              <svg viewBox="0 0 16 16" width="14" height="14" fill="#633806">
                <path d="M8 15A7 7 0 108 1a7 7 0 000 14zm0 1A8 8 0 118 0a8 8 0 010 16z" />
                <path d="M7.002 11a1 1 0 112 0 1 1 0 01-2 0zM7.1 4.995a.905.905 0 111.8 0l-.35 3.507a.553.553 0 01-1.1 0L7.1 4.995z" />
              </svg>
            </div>
            <div>
              <strong>No active user selected.</strong> Go to the Users page and
              select a user to manage products.
            </div>
          </div>
        )}

        {/* Add product form — disabled when no user */}
        <div className={`form-panel ${noUser ? "form-panel-disabled" : ""}`}>
          <p className="form-panel-label">Add new product</p>
          <form
            className="form-fields form-fields-product"
            onSubmit={handleAdd}
          >
            {/* Product Name */}
            <div className="field-wrap field-wide">
              <label className="field-label" htmlFor="prod-name">
                Product name
              </label>
              <input 
                id="prod-name"
                className="field-input"
                placeholder="e.g. Wireless headphones"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                disabled={submitting || noUser}
              />
            </div>
            {/* Product Description */}
            <div className="field-wrap field-wide">
              <label className="field-label" htmlFor="prod-desc">
                Description (optional)
              </label>
              <input
                id="prod-desc"
                className="field-input"
                placeholder="Short description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                disabled={submitting || noUser}
              />
            </div>
            {/* Product price */}
            <div className="field-wrap">
              <label className="field-label" htmlFor="prod-price">
                Price (₹)
              </label>
              <div className="price-field-wrap">
                <span className="price-prefix">₹</span>
                <input
                  id="prod-price"
                  className="field-input price-input"
                  type="number"
                  placeholder="0.00"
                  min="0.01"
                  step="0.01"
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                  required
                  disabled={submitting || noUser}
                />
              </div>
            </div>
            <button
              className="form-submit"
              type="submit"
              disabled={submitting || noUser}
            >
              {submitting ? "Saving…" : "Add product"}
            </button>
          </form>
        </div>

        {/* List header */}
        <div className="section-header">
          <div className="section-header-left">
            <span className="section-title">All products</span>
            {!loading && !noUser && (
              <span className="section-count">{products.length} items</span>
            )}
          </div>
          <div className={`search-box ${noUser ? "search-box-disabled" : ""}`}>
            <svg
              viewBox="0 0 16 16"
              width="13"
              height="13"
              fill="currentColor"
              style={{ opacity: 0.4, flexShrink: 0 }}
            >
              <path d="M11.742 10.344a6.5 6.5 0 10-1.397 1.398l3.85 3.85a1 1 0 001.415-1.414l-3.868-3.834zm-5.242 1.156a5 5 0 110-10 5 5 0 010 10z" />
            </svg>
            <input
              placeholder="Search products…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              disabled={noUser}
            />
          </div>
        </div>

        {/* Loading skeletons */}
        {loading && (
          <div className="skeleton-list">
            {[1, 2, 3].map((i) => (
              <div key={i} className="skeleton-row" />
            ))}
          </div>
        )}

        {/* Empty state */}
        {!loading && !noUser && filtered.length === 0 && (
          <div className="empty-state">
            <div className="empty-icon">
              <ProductIcon />
            </div>
            <p className="empty-title">
              {search ? "No products match your search" : "No products yet"}
            </p>
            <p className="empty-sub">
              {search
                ? "Try a different name."
                : "Add your first product using the form above."}
            </p>
          </div>
        )}

        {/* Product rows */}
        {!loading && !noUser && filtered.length > 0 && (
          <div className="item-list">
            {filtered.map((product) => (
              <div key={product.id} className="item-row">
                <div className="product-icon-wrap">
                  <ProductIcon />
                </div>
                <div className="item-meta">
                  <div className="item-name">{product.name}</div>
                  <div className="item-sub">
                    {product.description || `ID #${product.id}`}
                  </div>
                </div>
                <div className="item-actions">
                  <span className="price-tag">
                    {formatPrice(product.price)}
                  </span>
                  <button
                    className="icon-btn"
                    onClick={() => handleDelete(product)}
                    title="Remove product"
                  >
                    <svg
                      viewBox="0 0 16 16"
                      width="12"
                      height="12"
                      fill="currentColor"
                    >
                      <path d="M11 1.5v1h3.5a.5.5 0 010 1H13v9a1 1 0 01-1 1H4a1 1 0 01-1-1v-9H1.5a.5.5 0 010-1H5v-1A1.5 1.5 0 016.5 0h3A1.5 1.5 0 0111 1.5zm-5 0v1h4v-1a.5.5 0 00-.5-.5h-3a.5.5 0 00-.5.5zM5.5 5.5a.5.5 0 00-1 0v6a.5.5 0 001 0v-6zm2.5 0a.5.5 0 00-1 0v6a.5.5 0 001 0v-6zm2.5 0a.5.5 0 00-1 0v6a.5.5 0 001 0v-6z" />
                    </svg>
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

Products.propTypes = {
  currentUser: PropTypes.shape({ id: PropTypes.number, name: PropTypes.string }),
};
