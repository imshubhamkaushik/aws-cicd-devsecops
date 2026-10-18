import React, { useState } from "react";
import PropTypes from "prop-types";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import { formatPrice } from "../lib/format";
import { Badge } from "./ui/badge";
import { Button } from "./ui/button";

export default function ProductDetail({ product, onClose }) {
  const [imageFailed, setImageFailed] = useState(false);
  const qty = product.stockQuantity ?? 0;
  const showImage = product.imageUrl && !imageFailed;

  return (
    <DialogPrimitive.Root open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/50" />
        <DialogPrimitive.Content className="fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[calc(100%-2rem)] max-w-3xl -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-card border border-line bg-surface shadow-xl">
          <DialogPrimitive.Close asChild>
            <button
              type="button"
              aria-label="Close product details"
              className="absolute right-3 top-3 z-10 grid size-9 place-items-center rounded-control bg-surface/90 text-ink-2 hover:bg-surface-2 hover:text-ink"
            >
              <X className="size-4" aria-hidden="true" />
            </button>
          </DialogPrimitive.Close>

          <div className="grid md:grid-cols-2">
            <div className="aspect-[4/3] w-full bg-brand-soft md:aspect-auto md:min-h-80">
              {showImage ? (
                <img
                  src={product.imageUrl}
                  alt={product.name}
                  className="size-full object-cover"
                  onError={() => setImageFailed(true)}
                />
              ) : (
                <div className="font-display grid size-full place-items-center text-6xl font-semibold text-brand" aria-hidden="true">
                  {(product.name || "?").charAt(0).toUpperCase()}
                </div>
              )}
            </div>

            <div className="flex flex-col gap-4 p-6">
              <div className="pr-8">
                {product.category && <p className="text-sm text-ink-2">{product.category}</p>}
                <DialogPrimitive.Title className="font-display text-2xl font-semibold">
                  {product.name}
                </DialogPrimitive.Title>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <span className="tnum text-3xl font-semibold">{formatPrice(product.price)}</span>
                {qty > 0 ? (
                  <Badge tone={qty <= 5 ? "warning" : "success"}>{`${qty} in stock`}</Badge>
                ) : (
                  <Badge tone="danger">Out of stock</Badge>
                )}
              </div>
              <DialogPrimitive.Description className="max-w-prose text-ink-2">
                {product.description || "No description available."}
              </DialogPrimitive.Description>
              <div className="mt-auto pt-2">
                <DialogPrimitive.Close asChild>
                  <Button variant="secondary">Close</Button>
                </DialogPrimitive.Close>
              </div>
            </div>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}

ProductDetail.propTypes = {
  product: PropTypes.shape({
    id: PropTypes.number.isRequired,
    name: PropTypes.string,
    description: PropTypes.string,
    price: PropTypes.number,
    category: PropTypes.string,
    imageUrl: PropTypes.string,
    stockQuantity: PropTypes.number,
  }).isRequired,
  onClose: PropTypes.func.isRequired,
};
