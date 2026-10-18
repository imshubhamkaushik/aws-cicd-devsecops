import React, { useCallback, useEffect, useState } from "react";
import { getProducts, adjustStock } from "../api";
import { useAuth } from "../context/AuthContext";
import PropTypes from "prop-types";
import { Package } from "lucide-react";
import Toast from "./Toast";
import { Button } from "./ui/button";
import { Input } from "./ui/field";
import { Badge } from "./ui/badge";
import { Panel, Skeleton, PageHeader, EmptyState } from "./ui/surface";
import { formatPrice } from "../lib/format";

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
    <form className="flex items-center gap-2" onSubmit={handleSubmit}>
      <Input
        className="w-24 tnum"
        type="number"
        min="0"
        aria-label={`Stock quantity for ${product.name}`}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        disabled={saving}
      />
      <Button type="submit" variant="secondary" disabled={saving}>
        {saving ? "Saving…" : "Update stock"}
      </Button>
    </form>
  );
}
StockEditor.propTypes = {
  product: PropTypes.shape({
    id: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    name: PropTypes.string,
    stockQuantity: PropTypes.number,
  }).isRequired,
  onSaved: PropTypes.func.isRequired,
  onError: PropTypes.func.isRequired,
};

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
    <>
      <PageHeader
        title="My products"
        description={isAdmin ? "Stock across every product." : "Keep stock up to date for the products you sell."}
      />

      {toast && <Toast message={toast.message} type={toast.type} onDone={() => setToast(null)} />}
      {error && (
        <p role="alert" className="mb-4 rounded-control bg-danger-soft px-3 py-2 text-sm text-danger">
          {error}
        </p>
      )}

      {loading && (
        <Panel className="divide-y divide-line">
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex flex-col gap-3 p-4 md:flex-row md:items-center md:justify-between">
              <div className="space-y-2">
                <Skeleton className="h-5 w-48" />
                <Skeleton className="h-4 w-32" />
              </div>
              <Skeleton className="h-10 w-full md:w-56" />
            </div>
          ))}
        </Panel>
      )}

      {!loading && products.length === 0 && (
        <EmptyState
          icon={Package}
          title="No products yet"
          description="Add a product from the Products page and it will show up here so you can manage its stock."
        />
      )}

      {!loading && products.length > 0 && (
        <Panel className="divide-y divide-line">
          {products.map((product) => (
            <div
              key={product.id}
              className="flex flex-col gap-3 p-4 md:flex-row md:items-center md:justify-between"
            >
              <div className="min-w-0">
                <div className="truncate font-semibold">{product.name}</div>
                <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-ink-2">
                  <span className="tnum font-medium text-ink">{formatPrice(product.price)}</span>
                  <Badge>{product.category}</Badge>
                  <Badge tone={(product.stockQuantity ?? 0) === 0 ? "danger" : "success"}>
                    {product.stockQuantity ?? 0} in stock
                  </Badge>
                </div>
              </div>
              <StockEditor
                product={product}
                onSaved={() => {
                  setToast({ message: `Updated stock for "${product.name}".`, type: "success" });
                  fetchMyProducts();
                }}
                onError={(message) => setError(message)}
              />
            </div>
          ))}
        </Panel>
      )}
    </>
  );
}
