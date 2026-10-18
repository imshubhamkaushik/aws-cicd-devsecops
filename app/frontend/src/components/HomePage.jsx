import React from "react";
import { Link } from "react-router-dom";
import { ArrowRight, Package, ShoppingBag, ShoppingCart, Users } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { PageHeader, Panel } from "./ui/surface";

const STEPS = [
  { title: "Browse and add to cart", text: "Search the shop, check what is in stock and add what you like." },
  { title: "Choose address and pay", text: "Pay by card, UPI or cash on delivery." },
  { title: "Track your order until it arrives", text: "Follow every order from the Orders page." },
];

function QuickLink({ to, icon: Icon, title, text }) {
  return (
    <Link
      to={to}
      className="group flex items-center gap-4 rounded-card border border-line bg-surface p-4 transition-colors hover:border-brand"
    >
      <span className="grid size-11 shrink-0 place-items-center rounded-control bg-brand-soft text-brand">
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block font-semibold text-ink">{title}</span>
        <span className="block text-sm text-ink-2">{text}</span>
      </span>
      <ArrowRight className="size-4 shrink-0 text-ink-2 transition-transform group-hover:translate-x-0.5 group-hover:text-brand" aria-hidden="true" />
    </Link>
  );
}

QuickLink.propTypes = {
  to: (props, name) => (typeof props[name] === "string" ? null : new Error("to must be a string")),
  icon: (props, name) => (props[name] ? null : new Error("icon is required")),
  title: (props, name) => (typeof props[name] === "string" ? null : new Error("title must be a string")),
  text: (props, name) => (typeof props[name] === "string" ? null : new Error("text must be a string")),
};

export default function HomePage() {
  const { user, isAdmin } = useAuth();

  return (
    <div>
      <PageHeader
        title={user?.name ? `Welcome back, ${user.name}` : "Welcome back"}
        description="Pick up where you left off."
      />

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <QuickLink to="/products" icon={ShoppingBag} title="Browse products" text="See what is in the shop today." />
        <QuickLink to="/orders" icon={Package} title="Your orders" text="Check status or pay for an order." />
        <QuickLink to="/cart" icon={ShoppingCart} title="Your cart" text="Review items before you check out." />
        {isAdmin && (
          <QuickLink to="/users" icon={Users} title="Manage users" text="View and remove accounts." />
        )}
      </div>

      <Panel className="mt-8 p-5 sm:p-6">
        <h2 className="text-lg font-semibold">How ordering works</h2>
        <ol className="mt-4 space-y-4">
          {STEPS.map((step, i) => (
            <li key={step.title} className="flex gap-3">
              <span className="tnum grid size-7 shrink-0 place-items-center rounded-full bg-brand-soft text-sm font-semibold text-brand-strong" aria-hidden="true">
                {i + 1}
              </span>
              <div>
                <p className="font-semibold">{step.title}</p>
                <p className="text-sm text-ink-2">{step.text}</p>
              </div>
            </li>
          ))}
        </ol>
      </Panel>
    </div>
  );
}
