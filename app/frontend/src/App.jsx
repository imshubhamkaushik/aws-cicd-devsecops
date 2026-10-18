import React, { useEffect, useState } from "react";
import {
  BrowserRouter as Router,
  Routes,
  Route,
  Navigate,
  NavLink,
  Link,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  Bell,
  Boxes,
  ChevronDown,
  House,
  Inbox,
  LayoutGrid,
  LogOut,
  Package,
  ShieldCheck,
  ShoppingCart,
  UserRound,
  UsersRound,
} from "lucide-react";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { getCart } from "./api";
import { cn } from "./lib/utils";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "./components/ui/dropdown-menu";
import { LogoMark } from "./components/ui/logo";
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

function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return children;
}

const topLink = ({ isActive }) =>
  cn(
    "flex h-9 items-center gap-2 rounded-control px-3 text-sm font-semibold transition-colors",
    isActive ? "bg-brand-soft text-brand-strong" : "text-ink-2 hover:bg-surface-2 hover:text-ink",
  );

const tabLink = ({ isActive }) =>
  cn(
    "flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-semibold",
    isActive ? "text-brand-strong" : "text-ink-2",
  );

function CartButton({ count }) {
  const label = `Cart, ${count} item${count === 1 ? "" : "s"}`;
  return (
    <Link
      to="/cart"
      aria-label={label}
      title="Cart"
      className="relative grid size-9 place-items-center rounded-control text-ink-2 hover:bg-surface-2 hover:text-ink"
    >
      <ShoppingCart className="size-5" aria-hidden="true" />
      {count > 0 && (
        <span className="tnum absolute -right-0.5 -top-0.5 min-w-[18px] rounded-full bg-saffron px-1 text-center text-[11px] font-bold leading-[18px] text-saffron-ink">
          {count > 99 ? "99+" : count}
        </span>
      )}
    </Link>
  );
}

function UserMenu({ user, isAdmin, isSeller, isEmailVerified, onLogout }) {
  const initials = (user?.name || "?")
    .split(" ")
    .map((p) => p[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();

  let roleLabel = "Customer";
  if (isAdmin) roleLabel = "Admin";
  else if (isSeller) roleLabel = "Seller";

  return (
    <DropdownMenu>
      <DropdownMenuTrigger className="flex h-9 items-center gap-2 rounded-control pl-1 pr-2 hover:bg-surface-2">
        <span className="grid size-7 place-items-center rounded-full bg-brand text-xs font-bold text-brand-ink">
          {initials}
        </span>
        <span className="hidden max-w-32 truncate text-sm font-semibold sm:inline">
          {user?.name}
        </span>
        <ChevronDown className="size-3.5 text-ink-2" aria-hidden="true" />
      </DropdownMenuTrigger>

      <DropdownMenuContent>
        <DropdownMenuLabel>
          <span className="block font-semibold">{user?.name}</span>
          <span className="block text-xs text-ink-2">
            {roleLabel}
            {isEmailVerified ? "" : ", email not verified"}
          </span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />

        <DropdownMenuItem asChild>
          <Link to="/account">
            <UserRound aria-hidden="true" /> Account
          </Link>
        </DropdownMenuItem>

        {(isSeller || isAdmin) && (
          <DropdownMenuItem asChild>
            <Link to="/my-products">
              <Boxes aria-hidden="true" /> My Products
            </Link>
          </DropdownMenuItem>
        )}

        {isAdmin && (
          <>
            <DropdownMenuItem asChild>
              <Link to="/users">
                <UsersRound aria-hidden="true" /> Users
              </Link>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <Link to="/admin/moderation">
                <ShieldCheck aria-hidden="true" /> Moderation
              </Link>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <Link to="/admin/outbox">
                <Inbox aria-hidden="true" /> Outbox
              </Link>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <Link to="/notifications">
                <Bell aria-hidden="true" /> Notifications
              </Link>
            </DropdownMenuItem>
          </>
        )}

        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={onLogout} className="text-danger [&_svg]:text-danger">
          <LogOut aria-hidden="true" /> Log out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
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
        const count = (cart?.items || []).reduce((sum, i) => sum + (i.quantity || 0), 0);
        setCartCount(count);
      })
      .catch(() => {}); // cart badge is a nice-to-have, never block navigation on it
    return () => {
      cancelled = true;
    };
  }, [location.pathname]);

  return (
    <div className="min-h-dvh">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-50 focus:rounded-control focus:bg-surface focus:px-3 focus:py-2"
      >
        Skip to content
      </a>

      <header className="sticky top-0 z-40 border-b border-line bg-surface/95 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-4 px-4 sm:px-6">
          <Link to="/" className="flex items-center gap-2.5" aria-label="Catalogix home">
            <LogoMark />
            <span className="font-display text-lg font-semibold tracking-tight">Catalogix</span>
          </Link>

          <nav aria-label="Main" className="ml-2 hidden items-center gap-1 md:flex">
            <NavLink to="/" end className={topLink}>
              <House className="size-4" aria-hidden="true" /> Home
            </NavLink>
            <NavLink to="/products" className={topLink}>
              <LayoutGrid className="size-4" aria-hidden="true" /> Products
            </NavLink>
            <NavLink to="/orders" className={topLink}>
              <Package className="size-4" aria-hidden="true" /> Orders
            </NavLink>
          </nav>

          <div className="ml-auto flex items-center gap-1">
            <CartButton count={cartCount} />
            <UserMenu
              user={user}
              isAdmin={isAdmin}
              isSeller={isSeller}
              isEmailVerified={isEmailVerified}
              onLogout={handleLogout}
            />
          </div>
        </div>
      </header>

      {!isEmailVerified && (
        <div className="border-b border-saffron/30 bg-saffron-soft">
          <p className="mx-auto max-w-6xl px-4 py-2 text-sm text-saffron sm:px-6">
            Your email isn&apos;t verified yet. Open the link we sent you, or{" "}
            <Link to="/account" className="font-semibold underline">
              send it again from Account
            </Link>
            .
          </p>
        </div>
      )}

      <main id="main" className="mx-auto max-w-6xl px-4 pb-28 pt-6 sm:px-6 md:pb-12 md:pt-8">
        <Routes>
          <Route index element={<HomePage />} />
          <Route path="products" element={<Products />} />
          <Route path="cart" element={<Cart />} />
          <Route path="orders" element={<Orders />} />
          {(isSeller || isAdmin) && <Route path="my-products" element={<MyProducts />} />}
          <Route path="account" element={<Account />} />
          {isAdmin && <Route path="users" element={<Users />} />}
          {isAdmin && <Route path="admin/moderation" element={<Moderation />} />}
          {isAdmin && <Route path="admin/outbox" element={<OutboxAdmin />} />}
          {isAdmin && <Route path="notifications" element={<NotificationLog />} />}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>

      {/* Phone navigation: the same destinations, reachable with a thumb. */}
      <nav
        aria-label="Main"
        className="fixed inset-x-0 bottom-0 z-40 flex border-t border-line bg-surface pb-[env(safe-area-inset-bottom)] md:hidden"
      >
        <NavLink to="/" end className={tabLink}>
          <House className="size-5" aria-hidden="true" /> Home
        </NavLink>
        <NavLink to="/products" className={tabLink}>
          <LayoutGrid className="size-5" aria-hidden="true" /> Products
        </NavLink>
        <NavLink to="/cart" className={tabLink}>
          <ShoppingCart className="size-5" aria-hidden="true" /> Cart
        </NavLink>
        <NavLink to="/orders" className={tabLink}>
          <Package className="size-5" aria-hidden="true" /> Orders
        </NavLink>
        <NavLink to="/account" className={tabLink}>
          <UserRound className="size-5" aria-hidden="true" /> Account
        </NavLink>
      </nav>
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
