import React, { useEffect, useState } from "react";
import { getProducts, deleteProduct } from "../api";
import ProductForm from "./ProductForm";

const Products = (currentUser) => {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  if(!currentUser || !currentUser.id) {
    return <p>Please select a user.</p>
  }

  const fetchProducts = async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getProducts();
      setProducts(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error(err);
      setError("Failed to fetch products.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProducts();
  }, [currentUser]);

  const handleProductAdded = () => {
    fetchProducts();
  };

  const handleDelete = async (id) => {
    const confirm = globalThis.confirm("Delete this product?");
    if (!confirm) return;
    try {
      await deleteProduct(id);
      await fetchProducts();
    } catch (err) {
      console.error("Error deleting product:", err);
      alert("Failed to delete product.");
    }
  };

  return (
    <div style={{ padding: "20px" }}>
      <h2>Products</h2>

      <ProductForm userId={currentUser.id} onProductAdded={handleProductAdded} />

      {loading && <p>Loading products...</p>}
      {error && <p style={{ color: "red" }}>{error}</p>}

      {!loading && !error && products.length === 0 && <p>No products found.</p>}

      {!loading && !error && products.length > 0 && (
        <table
          style={{
            margin: "20px auto",
            borderCollapse: "collapse",
            minWidth: "60%",
          }}
        >
          <thead>
            <tr>
              <th style={{ border: "1px solid #ccc", padding: "8px" }}>ID</th>
              <th style={{ border: "1px solid #ccc", padding: "8px" }}>Name</th>
              <th style={{ border: "1px solid #ccc", padding: "8px" }}>
                Price
              </th>
              <th style={{ border: "1px solid #ccc", padding: "8px" }}>
                Actions
              </th>
            </tr>
          </thead>
          <tbody>
            {products.map((p) => (
              <tr key={p.id}>
                <td style={{ border: "1px solid #ccc", padding: "8px" }}>
                  {p.id}
                </td>
                <td style={{ border: "1px solid #ccc", padding: "8px" }}>
                  {p.name}
                </td>
                <td style={{ border: "1px solid #ccc", padding: "8px" }}>
                  ₹{p.price}
                </td>
                <td style={{ border: "1px solid #ccc", padding: "8px" }}>
                  <button onClick={() => handleDelete(p.id)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default Products;
