import React, { useState } from "react";
import { BrowserRouter as Router, Routes, Route, NavLink, useNavigate } from "react-router-dom";
import HomePage from "./components/HomePage";
import Users from "./components/Users";
import Products from "./components/Products";
import "./styles.css";

// Icons
const HomeIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" width="15" height="15">
    <path d="M8.354 1.146a.5.5 0 00-.708 0l-6 6A.5.5 0 002 8v6a.5.5 0 00.5.5h4a.5.5 0 00.5-.5v-3h2v3a.5.5 0 00.5.5h4a.5.5 0 00.5-.5V8a.5.5 0 00-.146-.354L13 6.793V3.5a.5.5 0 00-.5-.5h-1a.5.5 0 00-.5.5v1.293L8.354 1.146z"/>
  </svg>
);
const UsersIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" width="15" height="15">
    <path d="M8 8a3 3 0 100-6 3 3 0 000 6zm-5 6a5 5 0 0110 0H3z"/>
  </svg>
);
const ProductsIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" width="15" height="15">
    <path d="M0 1.5A.5.5 0 01.5 1H2a.5.5 0 01.485.379L2.89 3H14.5a.5.5 0 01.491.592l-1.5 8A.5.5 0 0113 12H4a.5.5 0 01-.491-.408L2.01 3.607 1.61 2H.5a.5.5 0 01-.5-.5zM5 12a2 2 0 100 4 2 2 0 000-4zm7 0a2 2 0 100 4 2 2 0 000-4z"/>
  </svg>
);

function Layout({ currentUser, setCurrentUser }) {
  const navigate = useNavigate();

  return (
    <div className="app-shell">
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="sidebar-logo">
          <div className="logo-mark">
            <div className="logo-icon">
              <svg viewBox="0 0 16 16" width="14" height="14" fill="#fff">
                <path d="M2 3h5v5H2zm7 0h5v5H9zM2 10h5v4H2zm7 0h5v4H9z"/>
              </svg>
            </div>
            <span className="logo-text">Catalogix</span>
          </div>
        </div>

        <nav className="sidebar-nav">
          <span className="nav-section-label">Pages</span>
          <NavLink to="/" end className={({ isActive }) => `nav-item${isActive ? " active" : ""}`}>
            <HomeIcon /> Home
          </NavLink>

          <span className="nav-section-label" style={{ marginTop: 10 }}>Services</span>
          <NavLink to="/users" className={({ isActive }) => `nav-item${isActive ? " active" : ""}`}>
            <UsersIcon /> Users
          </NavLink>
          <NavLink to="/products" className={({ isActive }) => `nav-item${isActive ? " active" : ""}`}>
            <ProductsIcon /> Products
          </NavLink>
        </nav>

        <div className="sidebar-footer">
          <div className="active-user-pill">
            <div className={`active-dot ${currentUser ? "dot-green" : "dot-gray"}`} />
            <div className="active-user-info">
              <div className="active-user-label">Active user</div>
              {currentUser
                ? <div className="active-user-name">{currentUser.name}</div>
                : <div className="active-user-none">None selected</div>
              }
            </div>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <div className="main-area">
        <Routes>
          <Route path="/" element={
            <HomePage
              currentUser={currentUser}
              onGoToUsers={() => navigate("/users")}
              onGoToProducts={() => navigate("/products")}
            />
          }/>
          <Route path="/users" element={
            <Users
              currentUser={currentUser}
              onUserSelected={setCurrentUser}
            />
          }/>
          <Route path="/products" element={
            <Products currentUser={currentUser} />
          }/>
        </Routes>
      </div>
    </div>
  );
}

export default function App() {
  const [currentUser, setCurrentUser] = useState(null);

  return (
    <Router>
      <Layout currentUser={currentUser} setCurrentUser={setCurrentUser} />
    </Router>
  );
}
