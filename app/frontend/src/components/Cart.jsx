import React, { useEffect, useState, useCallback, useRef } from "react";
import PropTypes from "prop-types";
import { Link, useNavigate } from "react-router-dom";
import { Search, ShoppingCart, Trash2 } from "lucide-react";
import {
  getProducts,
  getCart,
  addCartItem,
  updateCartItemQuantity,
  removeCartItem,
  checkoutCart,
} from "../api";
import { formatPrice } from "../lib/format";
import Toast from "./Toast";
import { Button } from "./ui/button";
import { Input } from "./ui/field";
import { Badge } from "./ui/badge";
import { Panel, PageHeader, EmptyState } from "./ui/surface";

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
    <div className="relative">
      <div className="relative">
        <Search
          className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-2"
          aria-hidden="true"
        />
        <Input
          className="pl-9"
          placeholder="Search products to add to your cart…"
          aria-label="Search products to add to your cart"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>

      {(searching || results.length > 0) && (
        <div className="mt-2 divide-y divide-line overflow-hidden rounded-control border border-line bg-surface">
          {searching && <div className="px-3 py-2.5 text-sm text-ink-2">Searching…</div>}

          {!searching &&
            results.map((product) => {
              const outOfStock = (product.stockQuantity ?? 0) === 0;
              return (
                <button
                  key={product.id}
                  type="button"
                  className="flex min-h-11 w-full items-center gap-3 px-3 py-2 text-left hover:bg-surface-2 disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={outOfStock}
                  onClick={() => handleAddProduct(product)}
                >
                  <span className="min-w-0 flex-1 truncate text-sm font-medium">
                    {product.name}
                  </span>
                  <span className="tnum text-sm text-ink-2">
                    {formatPrice(product.price)}
                  </span>
                  <Badge tone={outOfStock ? "danger" : "success"}>
                    {outOfStock ? "Out of stock" : `${product.stockQuantity} in stock`}
                  </Badge>
                </button>
              );
            })}

          {!searching && results.length === 0 && (
            <div className="px-3 py-2.5 text-sm text-ink-2">No matching products.</div>
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

  const hasItems = cart && cart.items.length > 0;

  return (
    <div>
      <PageHeader title="Cart" description="Search for products, adjust quantities, then check out." />

      {toast && (
        <Toast message={toast.message} type={toast.type} onDone={() => setToast(null)} />
      )}

      {error && (
        <p
          role="alert"
          className="mb-4 rounded-control bg-danger-soft px-3 py-2.5 text-sm text-danger"
        >
          {error}
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_340px] lg:items-start">
        <section aria-labelledby="cart-heading" className="min-w-0">
          <h2 id="cart-heading" className="mb-3 text-lg font-semibold">
            Your cart
          </h2>

          <ProductPicker onAdd={handleAddToCart} />

          {hasItems ? (
            <Panel className="mt-4">
              <ul className="divide-y divide-line">
                {cart.items.map((line) => (
                  <li key={line.productId} className="flex flex-wrap items-center gap-3 p-3 sm:flex-nowrap sm:gap-4 sm:p-4">
                    <div
                      className="font-display grid size-12 shrink-0 place-items-center rounded-control bg-brand-soft text-xl font-semibold text-brand"
                      aria-hidden="true"
                    >
                      {(line.productName || "?").charAt(0).toUpperCase()}
                    </div>

                    <div className="min-w-0 flex-1 basis-40">
                      <p className="truncate font-medium">{line.productName}</p>
                      {line.unitPrice != null && (
                        <p className="tnum text-sm text-ink-2">
                          {formatPrice(line.unitPrice)} each
                        </p>
                      )}
                    </div>

                    {qtyInput(line)}

                    <span className="tnum w-20 shrink-0 text-right font-semibold">
                      {formatPrice(line.subtotal)}
                    </span>

                    <Button
                      variant="ghost"
                      size="icon"
                      title="Remove"
                      aria-label={`Remove ${line.productName}`}
                      onClick={() => removeFromCart(line.productId)}
                    >
                      <Trash2 aria-hidden="true" />
                    </Button>
                  </li>
                ))}
              </ul>
            </Panel>
          ) : (
            <div className="mt-4">
              <EmptyState
                icon={ShoppingCart}
                title="Your cart is empty"
                description="Search above to add a product, or browse the full range."
                action={
                  <Button asChild>
                    <Link to="/products">Browse products</Link>
                  </Button>
                }
              />
            </div>
          )}
        </section>

        {hasItems && (
          <Panel className="p-5 lg:sticky lg:top-20">
            <h2 className="text-lg font-semibold">Order summary</h2>
            <dl className="mt-4 space-y-2 text-sm">
              <div className="flex justify-between">
                <dt className="text-ink-2">Subtotal</dt>
                <dd className="tnum">{formatPrice(cart.subtotal)}</dd>
              </div>
              <div className="flex justify-between border-t border-line pt-3 text-base font-semibold">
                <dt>Total</dt>
                <dd className="tnum">{formatPrice(cart.total)}</dd>
              </div>
            </dl>
            <Button
              size="lg"
              className="mt-5 w-full"
              onClick={handleCheckout}
              disabled={checkingOut}
            >
              {checkingOut ? "Placing order…" : "Checkout"}
            </Button>
            <p className="mt-3 text-xs text-ink-2">
              You will pay for your order on the next page.
            </p>
          </Panel>
        )}
      </div>
    </div>
  );

  function qtyInput(line) {
    return (
      <Input
        className="tnum w-20 px-2 text-center"
        type="number"
        min="1"
        aria-label={`Quantity of ${line.productName}`}
        value={draftQuantities[line.productId] ?? line.quantity}
        onChange={(event) => handleQuantityChange(line.productId, event.target.value)}
        onBlur={() => commitQuantityChange(line.productId, line.quantity)}
      />
    );
  }
}
