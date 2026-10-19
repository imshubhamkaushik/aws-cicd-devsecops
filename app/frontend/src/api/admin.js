// Administrative outbox and notification-log requests.

import { http, ADMIN_OUTBOX_API_BASE, NOTIFICATION_API_BASE } from "./client";

export const getOutboxEntries = async () => {
  const res = await http.get(ADMIN_OUTBOX_API_BASE);
  return res.data; // [{ id, type, productId, delta, reason, status, attempts, lastError, createdAt, updatedAt }]
};

// Resets a DEAD_LETTER entry back to PENDING (attempts=0) so
// CompensationOutboxProcessor picks it up again on its next scheduled run.

export const retryOutboxEntry = async (id) => {
  const res = await http.post(`${ADMIN_OUTBOX_API_BASE}/${id}/retry`);
  return res.data;
};

export const getNotificationLog = async (params = {}) => {
  const res = await http.get(NOTIFICATION_API_BASE, { params });
  return res.data; // { content, page, size, totalElements, totalPages }
};

