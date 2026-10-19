import React, { useEffect, useState, useCallback, useRef } from "react";
import PropTypes from "prop-types";
import { PackageSearch, Plus, Search } from "lucide-react";
import { getProducts, createProduct, deleteProduct } from "../api";
import { useAuth } from "../context/AuthContext";
import ProductDetail from "./ProductDetail";
import ProductCard from "./products/ProductCard";
import Toast from "./Toast";
import { Button } from "./ui/button";
import { Input, Select, Field } from "./ui/field";
import { Skeleton, PageHeader, EmptyState } from "./ui/surface";
import {
  Dialog, DialogContent, DialogTitle, DialogDescription, DialogTrigger,
} from "./ui/dialog";

const PAGE_SIZE = 10;

export default function Products() {
  const { user: currentUser, isAdmin, isSeller } = useAuth();

  const [products, setProducts]     = useState([]);
  const [loading, setLoading]       = useState(false);
  const [error, setError]           = useState("");
  const [toast, setToast]           = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [formOpen, setFormOpen]     = useState(false);

  // Form state
  const [name, setName]                 = useState("");
  const [description, setDescription]   = useState("");
  const [price, setPrice]               = useState("");
  const [category, setCategory]         = useState("");
  const [stockQuantity, setStockQuantity] = useState("");
  const [imageUrl, setImageUrl]         = useState("");

  // Search / filter / pagination (server-side)
  const [search, setSearch]     = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [sortBy, setSortBy]     = useState("");
  const [page, setPage]         = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const debounceRef = useRef(null);


  const [activeProduct, setActiveProduct] = useState(null);


  const fetchProducts = useCallback(async (opts = {}) => {
    setLoading(true);
    setError("");
    try {
      const data = await getProducts({
        search: opts.search ?? search,
        category: opts.categoryFilter ?? categoryFilter,
        minPrice: (opts.minPrice ?? minPrice) || undefined,
        maxPrice: (opts.maxPrice ?? maxPrice) || undefined,
        sortBy: (opts.sortBy ?? sortBy) || undefined,
        page: opts.page ?? page,
        size: PAGE_SIZE,
        sort: "id,desc",
      });
      setProducts(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch {
      setError("Failed to load products. Is the backend running?");
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, search, categoryFilter, minPrice, maxPrice, sortBy]);

  useEffect(() => { fetchProducts(); }, [page]); // eslint-disable-line react-hooks/exhaustive-deps

  // Debounce search/category/price changes, then reset to page 0 and refetch.
  // sortBy is NOT debounced below — a dropdown selection should refetch
  // immediately, there's no rapid-typing to coalesce the way there is for
  // the text/number fields.
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setPage(0);
      fetchProducts({ page: 0 });
    }, 350);
    return () => clearTimeout(debounceRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search, categoryFilter, minPrice, maxPrice]);

  const handleSortChange = (value) => {
    setSortBy(value);
    setPage(0);
    fetchProducts({ page: 0, sortBy: value });
  };

  const handleAdd = async (e) => {
    e.preventDefault();
    if (!name.trim() || !price) return;
    const numericPrice = Number.parseFloat(price);
    if (Number.isNaN(numericPrice) || numericPrice <= 0) {
      setError("Price must be a positive number.");
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      await createProduct({
        name: name.trim(),
        description: description.trim(),
        price: numericPrice,
        category: category.trim() || undefined,
        stockQuantity: stockQuantity === "" ? undefined : Number.parseInt(stockQuantity, 10),
        imageUrl: imageUrl.trim() || undefined,
      });
      setName(""); setDescription(""); setPrice(""); setCategory(""); setStockQuantity(""); setImageUrl("");
      await fetchProducts({ page: 0 });
      setPage(0);
      setFormOpen(false);
      setToast({ message: "Product added successfully.", type: "success" });
    } catch (err) {
      const msg = err.response?.data?.message || "Failed to add product.";
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (product) => {
    if (!globalThis.confirm(`Remove "${product.name}"? This cannot be undone.`)) return;
    try {
      await deleteProduct(product.id);
      await fetchProducts();
      setToast({ message: `"${product.name}" removed.`, type: "success" });
    } catch {
      setError("Failed to delete product.");
    }
  };

  const filtersActive = Boolean(search || categoryFilter || minPrice || maxPrice);

  const clearFilters = () => {
    setSearch(""); setCategoryFilter(""); setMinPrice(""); setMaxPrice("");
  };

  const handleAdded = (product, qty) => {
    setToast({ message: `Added ${qty} × "${product.name}" to your cart.`, type: "success" });
  };

  const errorBox = error && (
    <p role="alert" className="rounded-control bg-danger-soft px-3 py-2 text-sm text-danger">
      {error}
    </p>
  );

  const addProductDialog = (isSeller || isAdmin) && (
    <Dialog open={formOpen} onOpenChange={setFormOpen}>
      <DialogTrigger asChild>
        <Button><Plus aria-hidden="true" />Add product</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogTitle>Add a product</DialogTitle>
        <DialogDescription>It will appear in the shop as soon as you save it.</DialogDescription>
        <form className="mt-5 grid gap-4 sm:grid-cols-2" onSubmit={handleAdd}>
          <Field label="Product name" className="sm:col-span-2">
            {(id) => (
              <Input
                id={id}
                placeholder="e.g. Wireless headphones"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                disabled={submitting}
              />
            )}
          </Field>
          <Field label="Description (optional)" className="sm:col-span-2">
            {(id) => (
              <Input
                id={id}
                placeholder="Short description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                disabled={submitting}
              />
            )}
          </Field>
          <Field label="Category">
            {(id) => (
              <Input
                id={id}
                placeholder="e.g. electronics"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                disabled={submitting}
              />
            )}
          </Field>
          <Field label="Initial stock">
            {(id) => (
              <Input
                id={id}
                type="number"
                min="0"
                step="1"
                placeholder="0"
                value={stockQuantity}
                onChange={(e) => setStockQuantity(e.target.value)}
                disabled={submitting}
              />
            )}
          </Field>
          <Field label="Price (₹)">
            {(id) => (
              <Input
                id={id}
                type="number"
                placeholder="0.00"
                min="0.01"
                step="0.01"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
                required
                disabled={submitting}
              />
            )}
          </Field>
          <Field label="Image URL (optional)" className="sm:col-span-2">
            {(id) => (
              <Input
                id={id}
                type="url"
                placeholder="https://example.com/photo.jpg"
                value={imageUrl}
                onChange={(e) => setImageUrl(e.target.value)}
                disabled={submitting}
              />
            )}
          </Field>
          {formOpen && error && <div className="sm:col-span-2">{errorBox}</div>}
          <div className="flex justify-end gap-2 sm:col-span-2">
            <Button variant="secondary" onClick={() => setFormOpen(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? "Saving…" : "Add product"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );

  return (
    <div>
      <PageHeader
        title="Products"
        description={currentUser?.name ? `Browsing as ${currentUser.name}.` : "Browse what is in the shop today."}
        actions={addProductDialog}
      />

      {toast && <Toast message={toast.message} type={toast.type} onDone={() => setToast(null)} />}
      {!formOpen && error && <div className="mb-4">{errorBox}</div>}

      {/* Toolbar */}
      <div className="mb-5 flex flex-wrap items-center gap-2">
        <div className="relative min-w-[200px] flex-1 basis-60">
          <Search className="pointer-events-none absolute left-3 top-3 size-4 text-ink-2" aria-hidden="true" />
          <Input
            className="pl-9"
            type="search"
            aria-label="Search products"
            placeholder="Search products…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <Input
          className="w-full sm:w-44"
          aria-label="Filter by category"
          placeholder="Filter by category…"
          value={categoryFilter}
          onChange={(e) => setCategoryFilter(e.target.value)}
        />
        <div className="flex items-center gap-2">
          <Input
            className="tnum w-24"
            type="number"
            min="0"
            aria-label="Minimum price"
            placeholder="Min ₹"
            value={minPrice}
            onChange={(e) => setMinPrice(e.target.value)}
          />
          <span className="text-ink-2" aria-hidden="true">–</span>
          <Input
            className="tnum w-24"
            type="number"
            min="0"
            aria-label="Maximum price"
            placeholder="Max ₹"
            value={maxPrice}
            onChange={(e) => setMaxPrice(e.target.value)}
          />
        </div>
        <Select
          className="w-full sm:w-48"
          aria-label="Sort products"
          value={sortBy}
          onChange={(e) => handleSortChange(e.target.value)}
        >
          <option value="">Sort: newest first</option>
          <option value="PRICE_LOW_TO_HIGH">Price: low to high</option>
          <option value="PRICE_HIGH_TO_LOW">Price: high to low</option>
          <option value="NAME_A_TO_Z">Name: A to Z</option>
        </Select>
      </div>

      {!loading && products.length > 0 && (
        <p className="tnum mb-3 text-sm text-ink-2">
          {totalElements} {totalElements === 1 ? "product" : "products"}
        </p>
      )}

      {loading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4" aria-busy="true">
          {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => (
            <div key={i} className="overflow-hidden rounded-card border border-line">
              <Skeleton className="aspect-[4/3] rounded-none" />
              <div className="space-y-3 p-4">
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-3 w-1/3" />
                <Skeleton className="h-10 w-full" />
              </div>
            </div>
          ))}
        </div>
      )}

      {!loading && products.length === 0 && (
        <EmptyState
          icon={PackageSearch}
          title={filtersActive ? "No products match your filters" : "No products yet"}
          description={
            filtersActive
              ? "Try different search terms or a wider price range."
              : (isSeller || isAdmin)
                ? "Add your first product to start selling."
                : "Check back soon, new products are added often."
          }
          action={
            filtersActive ? (
              <Button variant="secondary" onClick={clearFilters}>Clear filters</Button>
            ) : (isSeller || isAdmin) ? (
              <Button onClick={() => setFormOpen(true)}><Plus aria-hidden="true" />Add product</Button>
            ) : null
          }
        />
      )}

      {!loading && products.length > 0 && (
        <>
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {products.map((product) => {
              const canManage = isAdmin || product.ownerId === currentUser?.id;

              return (
                <ProductCard
                  key={product.id}
                  product={product}
                  canManage={canManage}
                  onOpenDetails={setActiveProduct}
                  onDelete={handleDelete}
                  onAdded={handleAdded}
                  onError={setError}
                />
              );
            })}
          </ul>

          {totalPages > 1 && (
            <nav className="mt-8 flex items-center justify-center gap-4" aria-label="Pagination">
              <Button
                variant="secondary"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                Previous
              </Button>
              <span className="tnum text-sm text-ink-2">
                Page {page + 1} of {totalPages}
              </span>
              <Button
                variant="secondary"
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
              >
                Next
              </Button>
            </nav>
          )}
        </>
      )}

      {activeProduct && (
        <ProductDetail product={activeProduct} onClose={() => setActiveProduct(null)} />
      )}
    </div>
  );
}
