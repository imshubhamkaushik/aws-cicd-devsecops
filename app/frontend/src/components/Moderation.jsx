import React, { useEffect, useState } from "react";
import { ShieldCheck } from "lucide-react";
import { getProducts, moderateProduct } from "../api";
import { Select } from "./ui/field";
import { Badge } from "./ui/badge";
import { Panel, PageHeader, EmptyState } from "./ui/surface";

const STATUS_TONE = {
  PENDING_REVIEW: "warning",
  PUBLISHED: "success",
  REJECTED: "danger",
  SUSPENDED: "neutral",
};

const STATUS_LABEL = {
  PENDING_REVIEW: "Pending review",
  PUBLISHED: "Published",
  REJECTED: "Rejected",
  SUSPENDED: "Suspended",
};

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
    <>
      <PageHeader
        title="Product moderation"
        description="Review seller listings before they become visible to shoppers."
      />
      {error && (
        <p role="alert" className="mb-4 rounded-control bg-danger-soft px-3 py-2 text-sm text-danger">
          {error}
        </p>
      )}
      {products.length === 0 && !error ? (
        <EmptyState
          icon={ShieldCheck}
          title="Nothing to review"
          description="When sellers add products, they will appear here for you to review."
        />
      ) : (
        <Panel className="divide-y divide-line">
          {products.map((p) => {
            const status = p.moderationStatus || "PUBLISHED";
            return (
              <div
                key={p.id}
                className="flex flex-col gap-3 p-4 md:grid md:grid-cols-[1fr_9rem_13rem] md:items-center md:gap-4"
              >
                <div className="min-w-0">
                  <div className="truncate font-semibold">{p.name}</div>
                  <div className="text-sm text-ink-2">Seller #{p.ownerId || "admin"}</div>
                </div>
                <div>
                  <Badge tone={STATUS_TONE[status] || "neutral"}>{STATUS_LABEL[status] || status}</Badge>
                </div>
                <Select
                  aria-label={`Moderation status for ${p.name}`}
                  value={status}
                  onChange={(e) => change(p.id, e.target.value)}
                >
                  {Object.keys(STATUS_LABEL).map((s) => (
                    <option key={s} value={s}>
                      {STATUS_LABEL[s]}
                    </option>
                  ))}
                </Select>
              </div>
            );
          })}
        </Panel>
      )}
    </>
  );
}
