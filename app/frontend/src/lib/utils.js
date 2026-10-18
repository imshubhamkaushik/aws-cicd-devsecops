import { clsx } from "clsx";
import { twMerge } from "tailwind-merge";

/** Joins class names and lets the last Tailwind class win when two conflict. */
export function cn(...inputs) {
  return twMerge(clsx(inputs));
}
