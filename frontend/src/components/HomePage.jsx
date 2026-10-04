import React from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function HomePage() {
  const navigate = useNavigate();
  const { user, isAdmin } = useAuth();

  return (
    <div className="page-wrapper">
      <div className="topbar">
        <div>
          <h1 className="page-title">Home</h1>
          <p className="page-subtitle">Welcome back, {user?.name}</p>
        </div>
      </div>

      <div className="page-content">

        {/* Hero */}
        <div className="hero-card">
          <div className="hero-icon-wrap">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="#fff">
              <path d="M3 6h18v2H3zm0 5h18v2H3zm0 5h18v2H3z"/>
            </svg>
          </div>
          <h2 className="hero-title">Welcome to Catalogix</h2>
          <p className="hero-desc">
            Browse the catalogue and check
            out — all scoped to your signed-in account.
          </p>
          <div className="hero-actions">
            <button className="btn-primary" type="button" onClick={() => navigate("/products")}>
              <svg viewBox="0 0 16 16" width="12" height="12" fill="#fff">
                <path d="M0 1.5A.5.5 0 01.5 1H2a.5.5 0 01.485.379L2.89 3H14.5a.5.5 0 01.491.592l-1.5 8A.5.5 0 0113 12H4a.5.5 0 01-.491-.408L2.01 3.607 1.61 2H.5a.5.5 0 01-.5-.5zM5 12a2 2 0 100 4 2 2 0 000-4zm7 0a2 2 0 100 4 2 2 0 000-4z"/>
              </svg>
              Browse products
            </button>
            <button className="btn-outline" type="button" onClick={() => navigate("/orders")}>
              View my orders
            </button>
          </div>
        </div>

        {/* Service cards */}
        <div className="service-grid">
          <div className="service-card">
            <div className="service-card-icon ic-blue">
              <svg viewBox="0 0 16 16" width="15" height="15" fill="#185FA5">
                <path d="M0 1.5A.5.5 0 01.5 1H2a.5.5 0 01.485.379L2.89 3H14.5a.5.5 0 01.491.592l-1.5 8A.5.5 0 0113 12H4a.5.5 0 01-.491-.408L2.01 3.607 1.61 2H.5a.5.5 0 01-.5-.5zM5 12a2 2 0 100 4 2 2 0 000-4zm7 0a2 2 0 100 4 2 2 0 000-4z"/>
              </svg>
            </div>
            <h3 className="service-card-title">Product service</h3>
            <p className="service-card-desc">
              Search and filter the catalogue by category, list a new product,
              or restock/delete the ones you own.
            </p>
            <button className="service-card-link" type="button" onClick={() => navigate("/products")}>
              Go to products
              <svg viewBox="0 0 16 16" width="11" height="11" fill="currentColor">
                <path d="M4 8h8M8 4l4 4-4 4" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round"/>
              </svg>
            </button>
          </div>

          <div className="service-card">
            <div className="service-card-icon ic-teal">
              <svg viewBox="0 0 16 16" width="15" height="15" fill="#0F6E56">
                <path d="M1 2.5A.5.5 0 011.5 2H3a.5.5 0 01.485.379L3.89 4H14.5a.5.5 0 01.491.592l-1 5A.5.5 0 0113.5 10H5a.5.5 0 01-.491-.408L3.01 4.607 2.61 3H1.5a.5.5 0 01-.5-.5zM5 12a1.5 1.5 0 100 3 1.5 1.5 0 000-3zm7 0a1.5 1.5 0 100 3 1.5 1.5 0 000-3z"/>
              </svg>
            </div>
            <h3 className="service-card-title">Order service</h3>
            <p className="service-card-desc">
              Build a cart from live inventory, place an order, and track or
              cancel your past orders.
            </p>
            <button className="service-card-link" type="button" onClick={() => navigate("/orders")}>
              Go to orders
              <svg viewBox="0 0 16 16" width="11" height="11" fill="currentColor">
                <path d="M4 8h8M8 4l4 4-4 4" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round"/>
              </svg>
            </button>
          </div>

          {isAdmin && (
            <div className="service-card">
              <div className="service-card-icon ic-teal">
                <svg viewBox="0 0 16 16" width="15" height="15" fill="#0F6E56">
                  <path d="M8 8a3 3 0 100-6 3 3 0 000 6zm-5 6a5 5 0 0110 0H3z"/>
                </svg>
              </div>
              <h3 className="service-card-title">User service</h3>
              <p className="service-card-desc">
                Admin-only directory of every registered account. Remove
                accounts that shouldn't have access anymore.
              </p>
              <button className="service-card-link" type="button" onClick={() => navigate("/users")}>
                Go to users
                <svg viewBox="0 0 16 16" width="11" height="11" fill="currentColor">
                  <path d="M4 8h8M8 4l4 4-4 4" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round"/>
                </svg>
              </button>
            </div>
          )}
        </div>

        {/* How it works */}
        <div className="how-card">
          <p className="how-title">How it works</p>
          <div className="step">
            <div className="step-num">1</div>
            <p className="step-text">
              <strong>Browse the catalogue</strong> — search or filter by
              category on the Products page, and see live stock levels.
            </p>
          </div>
          <div className="step">
            <div className="step-num">2</div>
            <p className="step-text">
              <strong>Add to your order</strong> — pick a quantity and place
              an order; stock is reserved for you automatically.
            </p>
          </div>
          <div className="step">
            <div className="step-num">3</div>
            <p className="step-text">
              <strong>Track or cancel</strong> — the Orders page shows every
              order you've placed and lets you cancel a pending one.
            </p>
          </div>
        </div>

      </div>
    </div>
  );
}
