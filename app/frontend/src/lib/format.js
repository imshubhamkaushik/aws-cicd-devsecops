const rupee = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

/** 1899 -> "₹1,899" */
export function formatPrice(value) {
  return rupee.format(Number(value) || 0);
}

/** ISO timestamp -> "12 Oct 2026, 4:30 pm" (empty string when missing or invalid). */
export function formatDateTime(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}
