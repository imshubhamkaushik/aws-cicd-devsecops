import React, { useState } from "react";
import PropTypes from "prop-types";
import { createProduct } from "../api";

const ProductForm = ({ userId, onProductAdded }) => {
  const [name, setName] = useState("");
  const [price, setPrice] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!name.trim() || !price) {
      alert("Please fill in all fields.");
      return;
    }
    const numericPrice = Number.parseFloat(price);
    if (Number.isNaN(numericPrice) || numericPrice < 0) {
      alert("Please enter a valid price.");
      return;
    }

    setSubmitting(true);
    try {
      await createProduct({ name: name.trim(), price: numericPrice }, userId);
      setName("");
      setPrice("");
      if (onProductAdded) onProductAdded();
    } catch (err) {
      console.error(err);
      alert("Failed to add product.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} style={{ marginTop: "20px" }}>
      <input
        type="text"
        placeholder="Product name"
        value={name}
        onChange={(e) => setName(e.target.value)}
        required
        style={{ marginRight: "10px" }}
        disabled={submitting}
      />
      <input
        type="number"
        placeholder="Price"
        value={price}
        onChange={(e) => setPrice(e.target.value)}
        required
        style={{ marginRight: "10px" }}
        disabled={submitting}
      />
      <button type="submit" disabled={submitting}>
        {submitting ? "Adding..." : "Add Product"}
      </button>
    </form>
  );
};

//Add Prop Validation
ProductForm.propTypes = {
  userId: PropTypes.string.isRequired,
  onProductAdded: PropTypes.func,
}

export default ProductForm;
