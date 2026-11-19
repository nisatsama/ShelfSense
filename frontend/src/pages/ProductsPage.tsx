import React, { useEffect, useState } from 'react';
import { Plus, Search, Pencil } from 'lucide-react';
import { api, extractErrorMessage } from '../api/client';
import type { InventoryItem, Product } from '../api/types';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { EmptyState, ErrorBanner, LoadingSkeleton, Modal, PageHeader, StatusBadge } from '../components/UiPrimitives';

export const ProductsPage: React.FC = () => {
  const { isAdmin } = useAuth();
  const { showSuccess, showError } = useToast();
  const [products, setProducts] = useState<Product[]>([]);
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null);
  const [sku, setSku] = useState('');
  const [name, setName] = useState('');
  const [category, setCategory] = useState('FOODS');
  const [unitPrice, setUnitPrice] = useState('9.99');
  const [active, setActive] = useState(true);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [prodRes, invRes] = await Promise.all([
        api.get<Product[]>('/api/v1/products', { params: query.trim() ? { q: query.trim() } : {} }),
        api.get<InventoryItem[]>('/api/v1/inventory'),
      ]);
      setProducts(prodRes.data);
      setInventory(invRes.data);
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to load products.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const stockByProduct = inventory.reduce<Record<number, number>>((acc, inv) => {
    acc[inv.productId] = (acc[inv.productId] || 0) + inv.available;
    return acc;
  }, {});

  const openCreate = () => {
    setEditing(null);
    setSku('');
    setName('');
    setCategory('FOODS');
    setUnitPrice('9.99');
    setActive(true);
    setModalOpen(true);
  };

  const openEdit = (p: Product) => {
    setEditing(p);
    setSku(p.sku);
    setName(p.name);
    setCategory(p.category);
    setUnitPrice(String(p.unitPrice));
    setActive(p.active);
    setModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editing) {
        await api.put(`/api/v1/products/${editing.id}`, {
          name: name.trim(),
          category: category.trim(),
          unitPrice: Number(unitPrice),
          active,
        });
        showSuccess('Product updated.');
      } else {
        await api.post('/api/v1/products', {
          sku: sku.trim(),
          name: name.trim(),
          category: category.trim(),
          unitPrice: Number(unitPrice),
        });
        showSuccess('Product created.');
      }
      setModalOpen(false);
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Unable to save product.'));
    }
  };

  return (
    <div>
      <PageHeader
        title="Product Catalog"
        subtitle="Manage SKUs, pricing, categories, and network-wide available stock."
        actions={
          isAdmin && (
            <button
              type="button"
              onClick={openCreate}
              className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
            >
              <Plus className="h-4 w-4" /> New Product
            </button>
          )
        }
      />

      <div className="mb-4 flex gap-2">
        <div className="relative flex-1 max-w-md">
          <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && load()}
            placeholder="Search by SKU, name, or category..."
            className="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm focus:border-indigo-500 focus:outline-none"
          />
        </div>
        <button
          type="button"
          onClick={load}
          className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          Search
        </button>
      </div>

      {error && <ErrorBanner message={error} onRetry={load} />}
      {loading ? (
        <LoadingSkeleton />
      ) : products.length === 0 ? (
        <EmptyState title="No products found" description="Create a product or adjust your search filter." />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">SKU</th>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Category</th>
                <th className="px-4 py-3 text-right">Unit Price</th>
                <th className="px-4 py-3 text-right">Total Available</th>
                <th className="px-4 py-3">Status</th>
                {isAdmin && <th className="px-4 py-3 text-right">Actions</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {products.map((p) => (
                <tr key={p.id} className="hover:bg-slate-50/60">
                  <td className="px-4 py-3 font-mono text-xs font-semibold text-slate-900">{p.sku}</td>
                  <td className="px-4 py-3 font-medium text-slate-900">{p.name}</td>
                  <td className="px-4 py-3 text-slate-600">{p.category}</td>
                  <td className="px-4 py-3 text-right">${Number(p.unitPrice).toFixed(2)}</td>
                  <td className="px-4 py-3 text-right font-semibold">{stockByProduct[p.id] ?? 0}</td>
                  <td className="px-4 py-3">
                    <StatusBadge status={p.active ? 'ACTIVE' : 'DISMISSED'} />
                  </td>
                  {isAdmin && (
                    <td className="px-4 py-3 text-right">
                      <button
                        type="button"
                        onClick={() => openEdit(p)}
                        className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100"
                      >
                        <Pencil className="h-3 w-3" /> Edit
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={modalOpen} title={editing ? `Edit ${editing.sku}` : 'Create Product'} onClose={() => setModalOpen(false)}>
        <form onSubmit={handleSave} className="space-y-4">
          {!editing && (
            <div>
              <label className="block text-xs font-medium text-slate-700">SKU</label>
              <input
                required
                value={sku}
                onChange={(e) => setSku(e.target.value)}
                placeholder="FOODS_3_090"
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
          )}
          <div>
            <label className="block text-xs font-medium text-slate-700">Name</label>
            <input
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-700">Category</label>
              <input
                required
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700">Unit Price ($)</label>
              <input
                required
                type="number"
                step="0.01"
                min="0"
                value={unitPrice}
                onChange={(e) => setUnitPrice(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
          </div>
          {editing && (
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Active SKU
            </label>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={() => setModalOpen(false)} className="rounded-lg border px-4 py-2 text-sm">
              Cancel
            </button>
            <button type="submit" className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white">
              Save
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
