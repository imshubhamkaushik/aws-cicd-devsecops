import React, { useEffect, useRef, useState } from "react";
import {
  BrowserRouter as Router,
  Routes,
  Route,
  Navigate,
  NavLink,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { getCart } from "./api";
import HomePage from "./components/HomePage";
import Users from "./components/Users";
import Products from "./components/Products";
import Cart from "./components/Cart";
import Orders from "./components/Orders";
import MyProducts from "./components/MyProducts";
import Login from "./components/Login";
import ForgotPassword from "./components/ForgotPassword";
import ResetPassword from "./components/ResetPassword";
import VerifyEmail from "./components/VerifyEmail";
import Account from "./components/Account";
import OutboxAdmin from "./components/OutboxAdmin";
import NotificationLog from "./components/NotificationLog";
import Moderation from "./components/Moderation";
import "./styles.css";

// Icons
const HomeIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" width="15" height="15">
    <path d="M8.354 1.146a.5.5 0 00-.708 0l-6 6A.5.5 0 002 8v6a.5.5 0 00.5.5h4a.5.5 0 00.5-.5v-3h2v3a.5.5 0 00.5.5h4a.5.5 0 00.5-.5V8a.5.5 0 00-.146-.354L13 6.793V3.5a.5.5 0 00-.5-.5h-1a.5.5 0 00-.5.5v1.293L8.354 1.146z" />
  </svg>
);
const UsersIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" width="15" height="15">
    <path d="M8 8a3 3 0 100-6 3 3 0 000 6zm-5 6a5 5 0 0110 0H3z" />
  </svg>
);
const ProductsIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" width="15" height="15">
    <path d="M0 1.5A.5.5 0 01.5 1H2a.5.5 0 01.485.379L2.89 3H14.5a.5.5 0 01.491.592l-1.5 8A.5.5 0 0113 12H4a.5.5 0 01-.491-.408L2.01 3.607 1.61 2H.5a.5.5 0 01-.5-.5zM5 12a2 2 0 100 4 2 2 0 000-4zm7 0a2 2 0 100 4 2 2 0 000-4z" />
  </svg>
);
const OrdersIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" width="15" height="15">
    <path d="M1 2.5A.5.5 0 011.5 2H3a.5.5 0 01.485.379L3.89 4H14.5a.5.5 0 01.491.592l-1 5A.5.5 0 0113.5 10H5a.5.5 0 01-.491-.408L3.01 4.607 2.61 3H1.5a.5.5 0 01-.5-.5zM5 12a1.5 1.5 0 100 3 1.5 1.5 0 000-3zm7 0a1.5 1.5 0 100 3 1.5 1.5 0 000-3z" />
  </svg>
);
const LogoutIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" width="14" height="14">
    <path d="M6 12.5a.5.5 0 00.5.5h2a.5.5 0 000-1h-2a.5.5 0 00-.5.5zM6 3a.5.5 0 01.5-.5h2a.5.5 0 010 1h-2A.5.5 0 016 3z" />
    <path d="M11.854 8.354a.5.5 0 000-.708l-3-3a.5.5 0 10-.708.708L10.293 7.5H1.5a.5.5 0 000 1h8.793l-2.147 2.146a.5.5 0 00.708.708l3-3z" />
    <path d="M4 2.5a1.5 1.5 0 00-1.5 1.5v8A1.5 1.5 0 004 13.5h1a.5.5 0 000-1H4a.5.5 0 01-.5-.5V4A.5.5 0 014 3.5h1a.5.5 0 000-1H4z" />
  </svg>
);
const AccountIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" width="15" height="15">
    <path d="M8 8a3 3 0 100-6 3 3 0 000 6zM3 14s-1 0-1-1 1-4 6-4 6 3 6 4-1 1-1 1H3z" />
  </svg>
);
const OutboxIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" width="15" height="15">
    <path d="M2.5 3A1.5 1.5 0 001 4.5v.793l7 3.978 7-3.978V4.5A1.5 1.5 0 0013.5 3h-11zM15 6.383l-4.708 2.674L15 11.734v-5.35zm-.034 6.878L9.786 9.815 8 10.833l-1.786-1.018-5.18 3.446A1.5 1.5 0 002.5 14h11a1.5 1.5 0 001.466-.739zM1 11.734l4.708-2.677L1 6.383v5.35z" />
  </svg>
);
const CartIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" width="16" height="16">
    <path d="M0 1.5A.5.5 0 01.5 1H2a.5.5 0 01.485.379L2.89 3H14.5a.5.5 0 01.491.592l-1.5 8A.5.5 0 0113 12H4a.5.5 0 01-.491-.408L2.01 3.607 1.61 2H.5a.5.5 0 01-.5-.5zM5 12a2 2 0 100 4 2 2 0 000-4zm7 0a2 2 0 100 4 2 2 0 000-4z" />
  </svg>
);
const ChevronDownIcon = () => (
  <svg
    viewBox="0 0 16 16"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.5"
    width="11"
    height="11"
  >
    <path d="M4 6l4 4 4-4" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);
const NotificationLogIcon = () => (
  <svg viewBox="0 0 16 16" fill="currentColor" width="15" height="15">
    <path d="M8 16a2 2 0 002-2H6a2 2 0 002 2zM8 1.918l-.797.161A4.002 4.002 0 004 6c0 .628-.134 2.197-.459 3.742-.16.767-.376 1.566-.663 2.258h10.244c-.287-.692-.502-1.49-.663-2.258C12.134 8.197 12 6.628 12 6a4.002 4.002 0 00-3.203-3.92L8 1.917zM14.22 12c.223.447.481.801.78 1H1c.299-.199.557-.553.78-1C2.68 10.2 3 6.88 3 6c0-2.42 1.72-4.44 4.005-4.901a1 1 0 111.99 0A5.002 5.002 0 0113 6c0 .88.32 4.2 1.22 6z" />
  </svg>
);

function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return children;
}

function UserMenu({ user, isAdmin, isSeller, isEmailVerified, onLogout }) {
  const [open, setOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    function handleClickOutside(e) {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const initials = (user?.name || "?")
    .split(" ")
    .map((p) => p[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();

  const userStatusLabel = isEmailVerified ? "Signed in" : "Email not verified";
  const activeDotClass = isEmailVerified ? "dot-green" : "dot-amber";

  return (
    <div className="user-menu" ref={menuRef}>
      <button
        className="user-menu-trigger"
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="true"
        aria-expanded={open}
      >
        <div className="user-avatar">{initials}</div>
        <span className="user-menu-name">{user?.name}</span>
        <ChevronDownIcon />
      </button>

      {open && (
        <div className="user-menu-dropdown" role="menu">
          <div className="user-menu-status">
            <div className={`active-dot ${activeDotClass}`} />
            <span className="user-menu-status-text">
              {userStatusLabel}
              {isAdmin ? " · admin" : isSeller ? " · seller" : ""}
            </span>
          </div>

          <NavLink
            to="/account"
            className={({ isActive }) =>
              `user-menu-item${isActive ? " active" : ""}`
            }
            onClick={() => setOpen(false)}
          >
            <AccountIcon /> Account
          </NavLink>

          {(isSeller || isAdmin) && (
            <NavLink
              to="/my-products"
              className={({ isActive }) =>
                `user-menu-item${isActive ? " active" : ""}`
              }
              onClick={() => setOpen(false)}
            >
              <ProductsIcon /> My Products
            </NavLink>
          )}

          {isAdmin && (
            <>
              <NavLink
                to="/users"
                className={({ isActive }) =>
                  `user-menu-item${isActive ? " active" : ""}`
                }
                onClick={() => setOpen(false)}
              >
                <UsersIcon /> Users
              </NavLink>
              <NavLink
                to="/admin/moderation"
                className={({ isActive }) =>
                  `user-menu-item${isActive ? " active" : ""}`
                }
                onClick={() => setOpen(false)}
              >
                <ProductsIcon /> Moderation
              </NavLink>
              <NavLink
                to="/admin/outbox"
                className={({ isActive }) =>
                  `user-menu-item${isActive ? " active" : ""}`
                }
                onClick={() => setOpen(false)}
              >
                <OutboxIcon /> Outbox
              </NavLink>
              <NavLink
                to="/notifications"
                className={({ isActive }) =>
                  `user-menu-item${isActive ? " active" : ""}`
                }
                onClick={() => setOpen(false)}
              >
                <NotificationLogIcon /> Notifications
              </NavLink>
            </>
          )}

          <div className="user-menu-divider" />

          <button
            className="user-menu-item logout-item"
            type="button"
            onClick={onLogout}
          >
            <LogoutIcon /> Log out
          </button>
        </div>
      )}
    </div>
  );
}

function Layout() {
  const { user, isAdmin, isSeller, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [cartCount, setCartCount] = useState(0);

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  const isEmailVerified = user?.verified !== false;

  useEffect(() => {
    let cancelled = false;
    getCart()
      .then((cart) => {
        if (cancelled) return;
        const count = (cart?.items || []).reduce(
          (sum, i) => sum + (i.quantity || 0),
          0,
        );
        setCartCount(count);
      })
      .catch(() => {}); // cart badge is a nice-to-have, never block navigation on it
    return () => {
      cancelled = true;
    };
  }, [location.pathname]);

  return (
    <div className="app-shell">
      {/* Top nav */}
      <header className="topnav">
        <div className="logo-mark">
          <div className="logo-icon">
            <svg viewBox="0 0 16 16" width="14" height="14" fill="#fff">
              <path d="M2 3h5v5H2zm7 0h5v5H9zM2 10h5v4H2zm7 0h5v4H9z" />
            </svg>
          </div>
          <span className="logo-text">Catalogix</span>
        </div>

        <nav className="topnav-links">
          <NavLink
            to="/"
            end
            className={({ isActive }) => `nav-item${isActive ? " active" : ""}`}
          >
            <HomeIcon /> <span className="nav-label">Home</span>
          </NavLink>
          <NavLink
            to="/products"
            className={({ isActive }) => `nav-item${isActive ? " active" : ""}`}
          >
            <ProductsIcon /> <span className="nav-label">Products</span>
          </NavLink>
          <NavLink
            to="/orders"
            className={({ isActive }) => `nav-item${isActive ? " active" : ""}`}
          >
            <OrdersIcon /> <span className="nav-label">Orders</span>
          </NavLink>
        </nav>

        <div className="topnav-right">
          <button
            className="cart-nav-btn"
            type="button"
            title="Cart"
            aria-label={`Cart, ${cartCount} item${cartCount === 1 ? "" : "s"}`}
            onClick={() => navigate("/cart")}
          >
            <CartIcon />
            {cartCount > 0 && (
              <span className="cart-nav-badge">
                {cartCount > 99 ? "99+" : cartCount}
              </span>
            )}
          </button>

          <UserMenu
            user={user}
            isAdmin={isAdmin}
            isSeller={isSeller}
            isEmailVerified={isEmailVerified}
            onLogout={handleLogout}
          />
        </div>
      </header>

      {/* Main content */}
      <div className="main-area">
        <Routes>
          <Route index element={<HomePage />} />
          <Route path="products" element={<Products />} />
          <Route path="cart" element={<Cart />} />
          <Route path="orders" element={<Orders />} />
          {(isSeller || isAdmin) && (
            <Route path="my-products" element={<MyProducts />} />
          )}
          <Route path="account" element={<Account />} />
          {isAdmin && <Route path="users" element={<Users />} />}
          {isAdmin && (
            <Route path="admin/moderation" element={<Moderation />} />
          )}
          {isAdmin && <Route path="admin/outbox" element={<OutboxAdmin />} />}
          {isAdmin && (
            <Route path="notifications" element={<NotificationLog />} />
          )}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
    </div>
  );
}

function AppRoutes() {
  const { isAuthenticated } = useAuth();

  return (
    <Routes>
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/" replace /> : <Login />}
      />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      {/* Reachable whether or not you're logged in — e.g. after changing your
          email in Account settings while still signed in. */}
      <Route path="/verify-email" element={<VerifyEmail />} />
      <Route
        path="/*"
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}

export default function App() {
  return (
    <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </Router>
  );
}
