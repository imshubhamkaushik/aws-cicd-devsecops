// Catalogue, seller stock management and product moderation requests.

import { http, PRODUCT_API_BASE } from "./client";

export const getProducts = async (params = {}) => {
  const res = await http.get(PRODUCT_API_BASE, { params });
  return res.data; // { content, page, size, totalElements, totalPages }
};

export const getProduct = async (id) => {
  const res = await http.get(`${PRODUCT_API_BASE}/${id}`);
  return res.data;
};

export const createProduct = async (product) => {
  const res = await http.post(PRODUCT_API_BASE, product);
  return res.data;
};

export const deleteProduct = async (id) => {
  const res = await http.delete(`${PRODUCT_API_BASE}/${id}`);
  return res.data;
};

export const adjustStock = async (id, delta) => {
  const res = await http.patch(`${PRODUCT_API_BASE}/${id}/stock`, { delta });
  return res.data;
};

export const moderateProduct = async (id, status) =>
  (
    await http.patch(`${PRODUCT_API_BASE}/${id}/moderation`, null, {
      params: { status },
    })
  ).data;
