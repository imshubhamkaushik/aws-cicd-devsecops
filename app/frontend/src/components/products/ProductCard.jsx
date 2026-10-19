import React, { useState } from "react";
import PropTypes from "prop-types";
import { Trash2 } from "lucide-react";
import { addCartItem } from "../../api";
import { formatPrice } from "../../lib/format";
import { Button } from "../ui/button";
import { Input, Select, Field } from "../ui/field";
import { Badge } from "../ui/badge";

function StockBadge({ quantity }) {
  const qty = quantity ?? 0;
  if (qty === 0) return <Badge tone="danger">Out of stock</Badge>;
  if (qty <= 5) return <Badge tone="warning">{`${qty} left`}</Badge>;
  return <Badge tone="success">{`${qty} in stock`}</Badge>;
}

StockBadge.propTypes = {
  quantity: PropTypes.number,
};

function ProductImage({ product }) {
  const [failed, setFailed] = useState(false);
  if (product.imageUrl && !failed) {
    return (
      <img
        src={product.imageUrl}
        alt={product.name}
        className="size-full object-cover"
        loading="lazy"
        onError={() => setFailed(true)}
      />
    );
  }
  return (
    <div
      className="font-display grid size-full place-items-center bg-brand-soft text-5xl font-semibold text-brand"
      aria-hidden="true"
    >
      {(product.name || "?").charAt(0).toUpperCase()}
    </div>
  );
}

ProductImage.propTypes = {
  product: PropTypes.shape({
    name: PropTypes.string,
    imageUrl: PropTypes.string,
  }).isRequired,
};

// Per-tile quantity + add-to-cart control. Checkout (and the mock payment
// step) happens on the Orders page; this just adds to the persistent
// server-side cart.
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
    <div className="flex items-center gap-2">
      <Input
        className="tnum w-16 shrink-0 px-2 text-center"
        type="number"
        min="1"
        max={product.stockQuantity || 1}
        aria-label={`Quantity of ${product.name}`}
        value={qty}
        onChange={(e) => setQty(Math.max(1, Number.parseInt(e.target.value, 10) || 1))}
        disabled={outOfStock || adding}
      />
      <Button className="flex-1" onClick={handleAdd} disabled={outOfStock || adding}>
        {adding ? "Adding…" : "Add to cart"}
      </Button>
    </div>
  );
}

AddToCartControl.propTypes = {
  product: PropTypes.shape({
    id: PropTypes.number,
    name: PropTypes.string,
    stockQuantity: PropTypes.number,
  }).isRequired,
  onAdded: PropTypes.func.isRequired,
  onError: PropTypes.func.isRequired,
};


export default function ProductCard({ product, canManage, onOpenDetails, onDelete, onAdded, onError }) {
  return (
    <li
      className="relative flex flex-col overflow-hidden rounded-card border border-line bg-surface transition-colors hover:border-ink-2/40"
    >
      <div className="aspect-[4/3] w-full overflow-hidden bg-surface-2">
        <ProductImage product={product} />
      </div>

      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="min-w-0">
          <button
            className="line-clamp-2 min-h-9 text-left font-semibold text-ink hover:text-brand"
            type="button"
            onClick={() => onOpenDetails(product)}
            title="View product details"
            aria-label={`View details for ${product.name}`}
          >
            {product.name}
          </button>
          {product.category && (
            <p className="mt-0.5 text-xs text-ink-2">{product.category}</p>
          )}
        </div>

        <div className="mt-auto flex flex-wrap items-center justify-between gap-2">
          <span className="tnum text-lg font-semibold">{formatPrice(product.price)}</span>
          <StockBadge quantity={product.stockQuantity} />
        </div>

        <AddToCartControl product={product} onAdded={onAdded} onError={onError} />
      </div>

      {canManage && (
        <Button
          variant="secondary"
          size="icon"
          className="absolute right-2 top-2"
          onClick={() => onDelete(product)}
          title="Remove product"
          aria-label={`Remove ${product.name}`}
        >
          <Trash2 aria-hidden="true" />
        </Button>
      )}
    </li>
  );
}

ProductCard.propTypes = {
  product: PropTypes.shape({
    id: PropTypes.number,
    name: PropTypes.string.isRequired,
    description: PropTypes.string,
    price: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
    category: PropTypes.string,
    imageUrl: PropTypes.string,
    stockQuantity: PropTypes.number,
    ownerId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  }).isRequired,
  canManage: PropTypes.bool.isRequired,
  onOpenDetails: PropTypes.func.isRequired,
  onDelete: PropTypes.func.isRequired,
  onAdded: PropTypes.func.isRequired,
  onError: PropTypes.func.isRequired,
};
