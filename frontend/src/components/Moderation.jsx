import React, { useEffect, useState } from "react";
import { getProducts, moderateProduct } from "../api";
export default function Moderation() {
  const [products, setProducts] = useState([]),
    [error, setError] = useState("");
  const load = () =>
    getProducts({ page: 0, size: 100, sort: "id,desc" })
      .then((x) => setProducts(x.content || []))
      .catch(() => setError("Failed to load products."));
  useEffect(load, []);
  const change = async (id, status) => {
    try {
      await moderateProduct(id, status);
      await load();
    } catch (e) {
      setError(e?.response?.data?.message || "Moderation update failed.");
    }
  };
  return (
    <div className="page-wrapper">
      <div className="topbar">
        <div>
          <h1 className="page-title">Product moderation</h1>
          <p className="page-subtitle">
            Review seller listings before they become visible to shoppers
          </p>
        </div>
      </div>
      <div className="page-content">
        {error && <div className="toast toast-error">{error}</div>}
        <section className="panel">
          {products.map((p) => (
            <div className="table-row" key={p.id}>
              <div>
                <strong>{p.name}</strong>
                <div className="subtle-note">
                  Seller #{p.ownerId || "admin"}
                </div>
              </div>
              <span className="status-badge">
                {p.moderationStatus || "PUBLISHED"}
              </span>
              <select
                value={p.moderationStatus || "PUBLISHED"}
                onChange={(e) => change(p.id, e.target.value)}
              >
                <option>PENDING_REVIEW</option>
                <option>PUBLISHED</option>
                <option>REJECTED</option>
                <option>SUSPENDED</option>
              </select>
            </div>
          ))}
        </section>
      </div>
    </div>
  );
}
