import React from "react";

export default function HomePage({ currentUser, onGoToUsers, onGoToProducts }) {
  return (
    <div className="page-wrapper">
      <div className="topbar">
        <div>
          <h1 className="page-title">Home</h1>
          <p className="page-subtitle">Welcome to Catalogix</p>
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
          <h2 className="hero-title">Product catalogue manager</h2>
          <p className="hero-desc">
            Register users, browse and manage your product inventory.
            Select a user to begin managing products.
          </p>
          <div className="hero-actions">
            <button className="btn-primary" onClick={onGoToUsers}>
              <svg viewBox="0 0 16 16" width="12" height="12" fill="#fff">
                <path d="M8 8a3 3 0 100-6 3 3 0 000 6zm-5 6a5 5 0 0110 0H3z"/>
              </svg>
              Manage users
            </button>
            <button className="btn-outline" onClick={onGoToProducts}>
              Browse products
            </button>
          </div>
        </div>

        {/* Service cards */}
        <div className="service-grid">
          <div className="service-card">
            <div className="service-card-icon ic-teal">
              <svg viewBox="0 0 16 16" width="15" height="15" fill="#0F6E56">
                <path d="M8 8a3 3 0 100-6 3 3 0 000 6zm-5 6a5 5 0 0110 0H3z"/>
              </svg>
            </div>
            <h3 className="service-card-title">User service</h3>
            <p className="service-card-desc">
              Register new users, manage accounts, and select an active user
              context for product browsing.
            </p>
            <button className="service-card-link" onClick={onGoToUsers}>
              Go to users
              <svg viewBox="0 0 16 16" width="11" height="11" fill="currentColor">
                <path d="M4 8h8M8 4l4 4-4 4" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round"/>
              </svg>
            </button>
          </div>

          <div className="service-card">
            <div className="service-card-icon ic-blue">
              <svg viewBox="0 0 16 16" width="15" height="15" fill="#185FA5">
                <path d="M0 1.5A.5.5 0 01.5 1H2a.5.5 0 01.485.379L2.89 3H14.5a.5.5 0 01.491.592l-1.5 8A.5.5 0 0113 12H4a.5.5 0 01-.491-.408L2.01 3.607 1.61 2H.5a.5.5 0 01-.5-.5zM5 12a2 2 0 100 4 2 2 0 000-4zm7 0a2 2 0 100 4 2 2 0 000-4z"/>
              </svg>
            </div>
            <h3 className="service-card-title">Product service</h3>
            <p className="service-card-desc">
              Add, view and delete products. Requires an active user to be
              selected before browsing the catalogue.
            </p>
            <button className="service-card-link" onClick={onGoToProducts}>
              Go to products
              <svg viewBox="0 0 16 16" width="11" height="11" fill="currentColor">
                <path d="M4 8h8M8 4l4 4-4 4" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round"/>
              </svg>
            </button>
          </div>
        </div>

        {/* How it works */}
        <div className="how-card">
          <p className="how-title">How it works</p>
          <div className="step">
            <div className="step-num">1</div>
            <p className="step-text">
              <strong>Register a user</strong> — go to the Users page and add
              an account with name, email and password.
            </p>
          </div>
          <div className="step">
            <div className="step-num">2</div>
            <p className="step-text">
              <strong>Select active user</strong> — click Select on a user row
              to set them as the active session context.
            </p>
          </div>
          <div className="step">
            <div className="step-num">3</div>
            <p className="step-text">
              <strong>Manage products</strong> — navigate to Products to add
              items to the catalogue and manage inventory.
            </p>
          </div>
        </div>

      </div>
    </div>
  );
}
