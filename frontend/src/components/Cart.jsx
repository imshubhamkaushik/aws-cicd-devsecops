import React, { useEffect, useState, useCallback, useRef } from "react";
import PropTypes from "prop-types";
import { useNavigate } from "react-router-dom";
import {
  getProducts,
  getCart,
  addCartItem,
  updateCartItemQuantity,
  removeCartItem,
  checkoutCart,
} from "../api";
import Toast from "./Toast";

function formatPrice(value) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(value);
}

// -------- Product search / add-to-cart --------

function ProductPicker({ onAdd }) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const debounceRef = useRef(null);

  useEffect(() => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    if (!query.trim()) {
      setResults([]);
      return undefined;
    }

    debounceRef.current = setTimeout(async () => {
      setSearching(true);

      try {
        const data = await getProducts({
          search: query,
          page: 0,
          size: 5,
        });

        setResults(data.content || []);
      } catch {
        setResults([]);
      } finally {
        setSearching(false);
      }
    }, 300);

    return () => clearTimeout(debounceRef.current);
  }, [query]);

  const handleAddProduct = (product) => {
    onAdd(product);
    setQuery("");
    setResults([]);
  };

  return (
    <div className="product-picker">
      <div className="search-box">
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
          placeholder="Search products to add to your cart…"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>

      {(searching || results.length > 0) && (
        <div className="picker-results">
          {searching && <div className="picker-result-empty">Searching…</div>}

          {!searching &&
            results.map((product) => (
              <button
                key={product.id}
                type="button"
                className="picker-result-row"
                disabled={(product.stockQuantity ?? 0) === 0}
                onClick={() => handleAddProduct(product)}
              >
                <span className="picker-result-name">{product.name}</span>

                <span className="picker-result-price">
                  {formatPrice(product.price)}
                </span>

                <span
                  className={`badge ${
                    (product.stockQuantity ?? 0) === 0
                      ? "badge-out-of-stock"
                      : "badge-in-stock"
                  }`}
                >
                  {(product.stockQuantity ?? 0) === 0
                    ? "Out of stock"
                    : `${product.stockQuantity} in stock`}
                </span>
              </button>
            ))}

          {!searching && results.length === 0 && (
            <div className="picker-result-empty">No matching products.</div>
          )}
        </div>
      )}
    </div>
  );
}

ProductPicker.propTypes = {
  onAdd: PropTypes.func.isRequired,
};

// -------- Cart page --------

export default function Cart() {
  const navigate = useNavigate();

  const [cart, setCart] = useState(null);
  const [checkingOut, setCheckingOut] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState(null);
  const [draftQuantities, setDraftQuantities] = useState({});

  const fetchCart = useCallback(async () => {
    try {
      setCart(await getCart());
    } catch {
      setError("Failed to load your cart.");
    }
  }, []);

  useEffect(() => {
    void fetchCart();
  }, [fetchCart]);

  const handleAddToCart = async (product) => {
    try {
      setCart(await addCartItem(product.id, 1));
    } catch (requestError) {
      setError(
        requestError.response?.data?.message || "Failed to add to cart.",
      );
    }
  };

  const updateQty = async (productId, quantity) => {
    try {
      setCart(await updateCartItemQuantity(productId, Math.max(1, quantity)));
      setDraftQuantities((current) => {
        if (!(productId in current)) return current;
        const next = { ...current };
        delete next[productId];
        return next;
      });
    } catch (requestError) {
      setError(
        requestError.response?.data?.message || "Failed to update quantity.",
      );
    }
  };

  const handleQuantityChange = (productId, rawValue) => {
    setDraftQuantities((current) => ({ ...current, [productId]: rawValue }));
  };

  const commitQuantityChange = (productId, fallbackQuantity) => {
    if (!(productId in draftQuantities)) return;

    const quantity = Number.parseInt(draftQuantities[productId], 10);

    if (Number.isNaN(quantity) || quantity < 1) {
      setDraftQuantities((current) => ({
        ...current,
        [productId]: String(fallbackQuantity),
      }));
      return;
    }

    void updateQty(productId, quantity);
  };

  const removeFromCart = async (productId) => {
    setDraftQuantities((current) => {
      if (!(productId in current)) return current;
      const next = { ...current };
      delete next[productId];
      return next;
    });

    try {
      setCart(await removeCartItem(productId));
    } catch {
      setError("Failed to remove item.");
    }
  };


  const handleCheckout = async () => {
    if (!cart || cart.items.length === 0) {
      return;
    }

    setCheckingOut(true);
    setError("");

    try {
      await checkoutCart(crypto.randomUUID());

      await fetchCart();

      // Order + payment live on the Orders page now that Cart is its own
      // route — send the shopper straight there to finish paying instead
      // of leaving them on an now-empty cart with nothing left to do.
      void navigate("/orders");
    } catch (requestError) {
      setError(
        requestError.response?.data?.message ||
          "Checkout failed — one or more items may be unavailable.",
      );
    } finally {
      setCheckingOut(false);
    }
  };

  return (
    <div className="page-wrapper">
      <div className="topbar">
        <div>
          <h1 className="page-title">Cart</h1>
          <p className="page-subtitle">Search, add items, and check out</p>
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

        <div className="form-panel">
          <p className="form-panel-label">Your cart</p>

          <ProductPicker onAdd={handleAddToCart} />

          {cart && cart.items.length > 0 ? (
            <div className="cart-list">
              {cart.items.map((line) => (
                <div key={line.productId} className="cart-line">
                  <span className="cart-line-name">{line.productName}</span>

                  <input
                    className="qty-input"
                    type="number"
                    min="1"
                    value={draftQuantities[line.productId] ?? line.quantity}
                    onChange={(event) =>
                      handleQuantityChange(line.productId, event.target.value)
                    }
                    onBlur={() =>
                      commitQuantityChange(line.productId, line.quantity)
                    }
                  />

                  <span className="cart-line-subtotal">
                    {formatPrice(line.subtotal)}
                  </span>

                  <button
                    className="icon-btn"
                    type="button"
                    onClick={() => removeFromCart(line.productId)}
                    title="Remove"
                  >
                    <svg
                      viewBox="0 0 16 16"
                      width="12"
                      height="12"
                      fill="currentColor"
                    >
                      <path d="M2.146 2.854a.5.5 0 111.415-1.415L8 6.086l4.44-4.647a.5.5 0 01.708.707L8.707 6.793l4.647 4.647a.5.5 0 01-.708.708L8 7.5l-4.44 4.648a.5.5 0 01-.707-.708L7.293 6.793l-4.647-4.647.5.708z" />
                    </svg>
                  </button>
                </div>
              ))}

              <div className="cart-footer">
                <div>
                  <div className="auth-help-text" style={{ margin: 0 }}>
                    Subtotal: {formatPrice(cart.subtotal)}
                  </div>

                  <span className="cart-total">
                    Total: {formatPrice(cart.total)}
                  </span>
                </div>

                <button
                  className="form-submit cart-submit"
                  type="button"
                  onClick={handleCheckout}
                  disabled={checkingOut}
                >
                  {checkingOut ? "Placing order…" : "Checkout"}
                </button>
              </div>
            </div>
          ) : (
            <div className="empty-state">
              <div className="empty-icon">
                <svg viewBox="0 0 16 16" width="20" height="20" fill="currentColor">
                  <path d="M0 1.5A.5.5 0 01.5 1H2a.5.5 0 01.485.379L2.89 3H14.5a.5.5 0 01.491.592l-1.5 8A.5.5 0 0113 12H4a.5.5 0 01-.491-.408L2.01 3.607 1.61 2H.5a.5.5 0 01-.5-.5zM5 12a2 2 0 100 4 2 2 0 000-4zm7 0a2 2 0 100 4 2 2 0 000-4z" />
                </svg>
              </div>
              <p className="empty-title">Your cart is empty</p>
              <p className="empty-sub">Search for a product above to add it.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
