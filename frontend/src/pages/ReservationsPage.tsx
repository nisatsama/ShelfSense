import React, { useEffect, useState } from 'react';
import { Plus, Check, RotateCcw } from 'lucide-react';
import { api, extractErrorMessage } from '../api/client';
import type { Location, Product, Reservation } from '../api/types';
import { useToast } from '../context/ToastContext';
import { EmptyState, ErrorBanner, LoadingSkeleton, Modal, PageHeader, StatusBadge } from '../components/UiPrimitives';

export const ReservationsPage: React.FC = () => {
  const { showSuccess, showError } = useToast();
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [locations, setLocations] = useState<Location[]>([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [productId, setProductId] = useState('');
  const [locationId, setLocationId] = useState('');
  const [quantity, setQuantity] = useState('1');

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [resList, prodRes, locRes] = await Promise.all([
        api.get<Reservation[]>('/api/v1/reservations', { params: statusFilter ? { status: statusFilter } : {} }),
        api.get<Product[]>('/api/v1/products'),
        api.get<Location[]>('/api/v1/locations'),
      ]);
      setReservations(resList.data);
      setProducts(prodRes.data);
      setLocations(locRes.data);
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to load reservations.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [statusFilter]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post('/api/v1/reservations', {
        productId: Number(productId),
        locationId: Number(locationId),
        quantity: Number(quantity),
      });
      showSuccess('Inventory reserved for 10 minutes.');
      setModalOpen(false);
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Unable to create reservation.'));
    }
  };

  const act = async (id: number, action: 'confirm' | 'release') => {
    try {
      await api.put(`/api/v1/reservations/${id}/${action}`);
      showSuccess(action === 'confirm' ? 'Reservation confirmed.' : 'Reservation released.');
      load();
    } catch (err) {
      showError(extractErrorMessage(err, `Failed to ${action} reservation.`));
    }
  };

  return (
    <div>
      <PageHeader
        title="Inventory Reservations"
        subtitle="Create, confirm, or release time-bounded stock holds scoped to your account."
        actions={
          <button
            type="button"
            onClick={() => setModalOpen(true)}
            className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
          >
            <Plus className="h-4 w-4" /> Reserve Stock
          </button>
        }
      />

      <div className="mb-4 flex flex-wrap gap-2">
        {['', 'ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED'].map((st) => (
          <button
            key={st || 'ALL'}
            type="button"
            onClick={() => setStatusFilter(st)}
            className={`rounded-lg border px-3 py-1.5 text-xs font-semibold ${
              statusFilter === st ? 'border-indigo-600 bg-indigo-600 text-white' : 'border-slate-300 bg-white text-slate-700'
            }`}
          >
            {st || 'ALL'}
          </button>
        ))}
      </div>

      {error && <ErrorBanner message={error} onRetry={load} />}
      {loading ? (
        <LoadingSkeleton />
      ) : reservations.length === 0 ? (
        <EmptyState title="No reservations" description="Reserve available inventory to lock units prior to fulfillment." />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">ID</th>
                <th className="px-4 py-3">SKU</th>
                <th className="px-4 py-3">Location</th>
                <th className="px-4 py-3 text-right">Qty</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Expires</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {reservations.map((r) => (
                <tr key={r.id}>
                  <td className="px-4 py-3 font-mono text-xs">#{r.id}</td>
                  <td className="px-4 py-3 font-medium">{r.sku}</td>
                  <td className="px-4 py-3 text-slate-600">{r.locationCode}</td>
                  <td className="px-4 py-3 text-right font-semibold">{r.quantity}</td>
                  <td className="px-4 py-3"><StatusBadge status={r.status} /></td>
                  <td className="px-4 py-3 text-xs text-slate-500">
                    {r.expiresAt ? new Date(r.expiresAt).toLocaleString() : '—'}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {r.status === 'ACTIVE' && (
                      <div className="inline-flex gap-1.5">
                        <button
                          type="button"
                          onClick={() => act(r.id, 'confirm')}
                          className="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-2.5 py-1 text-xs font-semibold text-white hover:bg-emerald-500"
                        >
                          <Check className="h-3 w-3" /> Confirm
                        </button>
                        <button
                          type="button"
                          onClick={() => act(r.id, 'release')}
                          className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100"
                        >
                          <RotateCcw className="h-3 w-3" /> Release
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={modalOpen} title="Create Stock Reservation" onClose={() => setModalOpen(false)}>
        <form onSubmit={handleCreate} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-700">Product</label>
            <select required value={productId} onChange={(e) => setProductId(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="">Select product...</option>
              {products.map((p) => <option key={p.id} value={p.id}>{p.sku} — {p.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Location</label>
            <select required value={locationId} onChange={(e) => setLocationId(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="">Select location...</option>
              {locations.map((l) => <option key={l.id} value={l.id}>{l.code} — {l.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Quantity</label>
            <input required type="number" min="1" value={quantity} onChange={(e) => setQuantity(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setModalOpen(false)} className="rounded-lg border px-4 py-2 text-sm">Cancel</button>
            <button type="submit" className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white">Reserve</button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
