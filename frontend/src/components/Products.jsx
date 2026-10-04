import React, { useEffect, useState, useCallback, useRef } from "react";
import PropTypes from "prop-types";
import {
  getProducts, createProduct, deleteProduct, addCartItem,
} from "../api";
import { useAuth } from "../context/AuthContext";
import ProductDetail from "./ProductDetail";

const PAGE_SIZE = 10;

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

function StockBadge({ quantity }) {
  const qty = quantity ?? 0;
  let cls = "badge-in-stock";
  let label = `${qty} in stock`;
  if (qty === 0) {
    cls = "badge-out-of-stock";
    label = "Out of stock";
  } else if (qty <= 5) {
    cls = "badge-low-stock";
    label = `${qty} left`;
  }
  return <span className={`badge ${cls}`}>{label}</span>;
}

StockBadge.propTypes = {
  quantity: PropTypes.number,
};


// Per-row quantity + add-to-cart control. Checkout (and the mock payment
// step) happens on the Orders page now that orders go through a proper
// PENDING_PAYMENT -> pay -> CONFIRMED lifecycle — this just adds to the
// persistent server-side cart.
function AddToCartControl({ product, onAdded, onError }) {
  const [qty, setQty] = useState(1);
  const [adding, setAdding] = useState(false);
  const outOfStock = (product.stockQuantity ?? 0) === 0;

  const handleAdd = async () => {
    setAdding(true);
    try {
      await addCartItem(product.id, qty);
      onAdded(product, qty);
      setQty(1);
    } catch (err) {
      onError(err.response?.data?.message || "Failed to add to cart.");
    } finally {
      setAdding(false);
    }
  };

  return (
    <div className="order-control">
      <input
        className="qty-input"
        type="number"
        min="1"
        max={product.stockQuantity || 1}
        value={qty}
        onChange={(e) => setQty(Math.max(1, Number.parseInt(e.target.value, 10) || 1))}
        disabled={outOfStock || adding}
      />
      <button
        className="btn-small btn-primary-small"
        type="button"
        onClick={handleAdd}
        disabled={outOfStock || adding}
      >
        {adding ? "…" : "Add to cart"}
      </button>
    </div>
  );
}

AddToCartControl.propTypes = {
  product: PropTypes.shape({
    id: PropTypes.number,
    stockQuantity: PropTypes.number,
  }).isRequired,
  onAdded: PropTypes.func.isRequired,
  onError: PropTypes.func.isRequired,
};

export default function Products() {
  const { user: currentUser, isAdmin, isSeller } = useAuth();

  const [products, setProducts]     = useState([]);
  const [loading, setLoading]       = useState(false);
  const [error, setError]           = useState("");
  const [toast, setToast]           = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // Form state
  const [name, setName]                 = useState("");
  const [description, setDescription]   = useState("");
  const [price, setPrice]               = useState("");
  const [category, setCategory]         = useState("");
  const [stockQuantity, setStockQuantity] = useState("");
  const [imageUrl, setImageUrl]         = useState("");

  // Search / filter / pagination (server-side)
  const [search, setSearch]     = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [sortBy, setSortBy]     = useState("");
  const [page, setPage]         = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const debounceRef = useRef(null);


  const [activeProduct, setActiveProduct] = useState(null);


  const fetchProducts = useCallback(async (opts = {}) => {
    setLoading(true);
    setError("");
    try {
      const data = await getProducts({
        search: opts.search ?? search,
        category: opts.categoryFilter ?? categoryFilter,
        minPrice: (opts.minPrice ?? minPrice) || undefined,
        maxPrice: (opts.maxPrice ?? maxPrice) || undefined,
        sortBy: (opts.sortBy ?? sortBy) || undefined,
        page: opts.page ?? page,
        size: PAGE_SIZE,
        sort: "id,desc",
      });
      setProducts(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch {
      setError("Failed to load products. Is the backend running?");
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, search, categoryFilter, minPrice, maxPrice, sortBy]);

  useEffect(() => { fetchProducts(); }, [page]); // eslint-disable-line react-hooks/exhaustive-deps

  // Debounce search/category/price changes, then reset to page 0 and refetch.
  // sortBy is NOT debounced below — a dropdown selection should refetch
  // immediately, there's no rapid-typing to coalesce the way there is for
  // the text/number fields.
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setPage(0);
      fetchProducts({ page: 0 });
    }, 350);
    return () => clearTimeout(debounceRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search, categoryFilter, minPrice, maxPrice]);

  const handleSortChange = (value) => {
    setSortBy(value);
    setPage(0);
    fetchProducts({ page: 0, sortBy: value });
  };

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
      await createProduct({
        name: name.trim(),
        description: description.trim(),
        price: numericPrice,
        category: category.trim() || undefined,
        stockQuantity: stockQuantity === "" ? undefined : Number.parseInt(stockQuantity, 10),
        imageUrl: imageUrl.trim() || undefined,
      });
      setName(""); setDescription(""); setPrice(""); setCategory(""); setStockQuantity(""); setImageUrl("");
      await fetchProducts({ page: 0 });
      setPage(0);
      setToast({ message: "Product added successfully.", type: "success" });
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
      await deleteProduct(product.id);
      await fetchProducts();
      setToast({ message: `"${product.name}" removed.`, type: "success" });
    } catch {
      setError("Failed to delete product.");
    }
  };

  const handleAdded = (product, qty) => {
    setToast({ message: `Added ${qty} × "${product.name}" to your cart.`, type: "success" });
  };

  return (
    <div className="page-wrapper">
      <div className="topbar">
        <div>
          <h1 className="page-title">Products</h1>
          <p className="page-subtitle">Browsing as {currentUser?.name}</p>
        </div>
      </div>

      <div className="page-content">
        {toast && <Toast message={toast.message} type={toast.type} onDone={() => setToast(null)} />}
        {error && <div className="toast toast-error">{error}</div>}

        {/* Add product form — sellers and admins only; a buyer account has
            no listing capability at all. */}
        {(isSeller || isAdmin) && (
        <div className="form-panel">
          <p className="form-panel-label">Add new product</p>
          <form className="form-fields form-fields-product" onSubmit={handleAdd}>
            <div className="field-wrap field-wide">
              <label className="field-label" htmlFor="prod-name">Product name</label>
              <input
                id="prod-name"
                className="field-input"
                placeholder="e.g. Wireless headphones"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                disabled={submitting}
              />
            </div>
            <div className="field-wrap field-wide">
              <label className="field-label" htmlFor="prod-desc">Description (optional)</label>
              <input
                id="prod-desc"
                className="field-input"
                placeholder="Short description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                disabled={submitting}
              />
            </div>
            <div className="field-wrap">
              <label className="field-label" htmlFor="prod-category">Category</label>
              <input
                id="prod-category"
                className="field-input"
                placeholder="e.g. electronics"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                disabled={submitting}
              />
            </div>
            <div className="field-wrap">
              <label className="field-label" htmlFor="prod-stock">Initial stock</label>
              <input
                id="prod-stock"
                className="field-input"
                type="number"
                min="0"
                step="1"
                placeholder="0"
                value={stockQuantity}
                onChange={(e) => setStockQuantity(e.target.value)}
                disabled={submitting}
              />
            </div>
            <div className="field-wrap">
              <label className="field-label" htmlFor="prod-price">Price (₹)</label>
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
                  disabled={submitting}
                />
              </div>
            </div>
            <div className="field-wrap field-wide">
              <label className="field-label" htmlFor="prod-image">Image URL (optional)</label>
              <input
                id="prod-image"
                className="field-input"
                type="url"
                placeholder="https://example.com/photo.jpg"
                value={imageUrl}
                onChange={(e) => setImageUrl(e.target.value)}
                disabled={submitting}
              />
            </div>
            <button className="form-submit" type="submit" disabled={submitting}>
              {submitting ? "Saving…" : "Add product"}
            </button>
          </form>
        </div>
        )}

        {/* List header */}
        <div className="section-header">
          <div className="section-header-left">
            <span className="section-title">All products</span>
            {!loading && <span className="section-count">{totalElements} items</span>}
          </div>
          <div className="filter-controls">
            <div className="search-box">
              <svg viewBox="0 0 16 16" width="13" height="13" fill="currentColor" style={{ opacity: 0.4, flexShrink: 0 }}>
                <path d="M11.742 10.344a6.5 6.5 0 10-1.397 1.398l3.85 3.85a1 1 0 001.415-1.414l-3.868-3.834zm-5.242 1.156a5 5 0 110-10 5 5 0 010 10z" />
              </svg>
              <input
                placeholder="Search products…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            <input
              className="field-input category-filter-input"
              placeholder="Filter by category…"
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
            />
            <div className="price-range-inputs">
              <input
                className="field-input price-range-input"
                type="number"
                min="0"
                placeholder="Min ₹"
                value={minPrice}
                onChange={(e) => setMinPrice(e.target.value)}
              />
              <span className="price-range-sep">–</span>
              <input
                className="field-input price-range-input"
                type="number"
                min="0"
                placeholder="Max ₹"
                value={maxPrice}
                onChange={(e) => setMaxPrice(e.target.value)}
              />
            </div>
            <select
              className="sort-select"
              value={sortBy}
              onChange={(e) => handleSortChange(e.target.value)}
            >
              <option value="">Sort: newest first</option>
              <option value="PRICE_LOW_TO_HIGH">Price: low to high</option>
              <option value="PRICE_HIGH_TO_LOW">Price: high to low</option>
              <option value="NAME_A_TO_Z">Name: A to Z</option>
            </select>
          </div>
        </div>

        {/* Loading skeletons */}
        {loading && (
          <div className="skeleton-list">
            {[1, 2, 3].map((i) => <div key={i} className="skeleton-row" />)}
          </div>
        )}

        {/* Empty state */}
        {!loading && products.length === 0 && (
          <div className="empty-state">
            <div className="empty-icon"><ProductIcon /></div>
            <p className="empty-title">
              {search || categoryFilter || minPrice || maxPrice
                ? "No products match your filters" : "No products yet"}
            </p>
            <p className="empty-sub">
              {search || categoryFilter || minPrice || maxPrice
                ? "Try different search terms or a wider price range." : "Add your first product using the form above."}
            </p>
          </div>
        )}

        {/* Product cards */}
        {!loading && products.length > 0 && (
          <>
            <div className="product-grid">
              {products.map((product) => {
                const canManage = isAdmin || product.ownerId === currentUser?.id;

                return (
                  <div key={product.id} className="product-card">
                    <div className="product-card-media">
                      {product.imageUrl ? (
                        <img
                          src={product.imageUrl}
                          alt={product.name}
                          className="product-card-image"
                          loading="lazy"
                          onError={(e) => {
                            // Broken/unreachable URL — fall back to the icon
                            // rather than showing the browser's broken-image
                            // glyph. Swap the whole media block's content
                            // instead of just hiding the <img>, so the
                            // fallback icon still renders in its place.
                            e.currentTarget.style.display = "none";
                            e.currentTarget.nextSibling.style.display = "flex";
                          }}
                        />
                      ) : null}
                      <div
                        className="product-card-icon-fallback"
                        style={{ display: product.imageUrl ? "none" : "flex" }}
                      >
                        <ProductIcon />
                      </div>
                    </div>

                    <div className="product-card-body">
                      <span className="badge badge-category product-card-category">
                        {product.category}
                      </span>

                      <button
                        className="product-card-name"
                        type="button"
                        onClick={() => setActiveProduct(product)}
                        title="View product details"
                        aria-label={`View details for ${product.name}`}
                      >
                        {product.name}
                      </button>

                      <div className="product-card-desc">
                        {product.description || `ID #${product.id}`}
                      </div>


                      <div className="product-card-footer">
                        <div className="product-card-price-row">
                          <span className="price-tag">{formatPrice(product.price)}</span>
                          <StockBadge quantity={product.stockQuantity} />
                        </div>

                        <div className="product-card-actions">
                          <AddToCartControl
                            product={product}
                            onAdded={handleAdded}
                            onError={setError}
                          />
                        </div>
                      </div>
                    </div>

                    {canManage && (
                      <div className="product-card-manage">
                        <button
                          className="icon-btn"
                          type="button"
                          onClick={() => handleDelete(product)}
                          title="Remove product"
                          aria-label={`Remove ${product.name}`}
                        >
                          <svg
                            viewBox="0 0 16 16"
                            width="12"
                            height="12"
                            fill="currentColor"
                            aria-hidden="true"
                          >
                            <path d="M11 1.5v1h3.5a.5.5 0 010 1H13v9a1 1 0 01-1 1H4a1 1 0 01-1-1v-9H1.5a.5.5 0 010-1H5v-1A1.5 1.5 0 016.5 0h3A1.5 1.5 0 0111 1.5zm-5 0v1h4v-1a.5.5 0 00-.5-.5h-3a.5.5 0 00-.5.5zM5.5 5.5a.5.5 0 00-1 0v6a.5.5 0 001 0v-6zm2.5 0a.5.5 0 00-1 0v6a.5.5 0 001 0v-6zm2.5 0a.5.5 0 00-1 0v6a.5.5 0 001 0v-6z" />
                          </svg>
                        </button>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="pagination">
                <button
                  className="btn-small"
                  type="button"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                >
                  ← Prev
                </button>
                <span className="pagination-label">
                  Page {page + 1} of {totalPages}
                </span>
                <button
                  className="btn-small"
                  type="button"
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                >
                  Next →
                </button>
              </div>
            )}
          </>
        )}
      </div>

      {activeProduct && (
        <ProductDetail product={activeProduct} onClose={() => setActiveProduct(null)} />
      )}
    </div>
  );
}
