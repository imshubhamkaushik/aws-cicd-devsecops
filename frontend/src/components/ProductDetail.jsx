import React, { useEffect, useRef } from "react";
import PropTypes from "prop-types";

function formatPrice(value) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(value);
}

export default function ProductDetail({ product, onClose }) {
  const dialogRef = useRef(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return undefined;

    if (!dialog.open) {
      if (typeof dialog.showModal === "function") {
        dialog.showModal();
      } else {
        dialog.setAttribute("open", "");
      }
    }

    const handleClose = () => onClose();
    dialog.addEventListener("close", handleClose);
    return () => dialog.removeEventListener("close", handleClose);
  }, [onClose]);

  const closeDialog = () => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (typeof dialog.close === "function") {
      dialog.close();
      return;
    }

    dialog.removeAttribute("open");
    onClose();
  };

  return (
    <dialog ref={dialogRef} className="product-detail-dialog">
      <div className="product-detail-header">
        <div>
          <span className="badge badge-category">{product.category}</span>
          <h2>{product.name}</h2>
        </div>
        <button
          className="icon-btn"
          type="button"
          onClick={closeDialog}
          aria-label="Close product details"
        >
          ×
        </button>
      </div>

      <div className="product-detail-body">
        {product.imageUrl ? (
          <img
            src={product.imageUrl}
            alt={product.name}
            className="product-detail-image"
          />
        ) : null}
        <p>{product.description || "No description available."}</p>
        <div className="product-detail-price">{formatPrice(product.price)}</div>
        <div className="product-detail-stock">
          {(product.stockQuantity ?? 0) > 0
            ? `${product.stockQuantity} in stock`
            : "Out of stock"}
        </div>
      </div>

      <div className="product-detail-footer">
        <button className="btn-outline" type="button" onClick={closeDialog}>
          Close
        </button>
      </div>
    </dialog>
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
