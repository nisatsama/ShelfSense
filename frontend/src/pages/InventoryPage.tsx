import React, { useEffect, useState } from 'react';
import { Plus, SlidersHorizontal } from 'lucide-react';
import { api, extractErrorMessage } from '../api/client';
import type { AdjustmentReason, InventoryItem, InventoryTransaction, Location, Product } from '../api/types';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { EmptyState, ErrorBanner, LoadingSkeleton, Modal, PageHeader, StatusBadge } from '../components/UiPrimitives';

const REASONS: AdjustmentReason[] = [
  'CYCLE_COUNT', 'DAMAGE', 'SHRINKAGE', 'EXPIRY', 'RESTOCK_CORRECTION', 'RETURN_RESTOCK', 'OTHER',
];

export const InventoryPage: React.FC = () => {
  const { isAdmin } = useAuth();
  const { showSuccess, showError } = useToast();
  const [tab, setTab] = useState<'stock' | 'ledger'>('stock');
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [ledger, setLedger] = useState<InventoryTransaction[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [locations, setLocations] = useState<Location[]>([]);
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [initModal, setInitModal] = useState(false);
  const [initProductId, setInitProductId] = useState('');
  const [initLocationId, setInitLocationId] = useState('');
  const [initOnHand, setInitOnHand] = useState('50');

  const [adjModal, setAdjModal] = useState(false);
  const [adjTarget, setAdjTarget] = useState<InventoryItem | null>(null);
  const [adjDelta, setAdjDelta] = useState('10');
  const [adjReason, setAdjReason] = useState<AdjustmentReason>('CYCLE_COUNT');
  const [adjNotes, setAdjNotes] = useState('');

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [invRes, txRes, prodRes, locRes] = await Promise.all([
        api.get<InventoryItem[]>('/api/v1/inventory', { params: lowStockOnly ? { lowStockThreshold: 20 } : {} }),
        api.get<InventoryTransaction[]>('/api/v1/inventory/transactions', { params: { limit: 100 } }),
        api.get<Product[]>('/api/v1/products'),
        api.get<Location[]>('/api/v1/locations'),
      ]);
      setItems(invRes.data);
      setLedger(txRes.data);
      setProducts(prodRes.data);
      setLocations(locRes.data);
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to load inventory data.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [lowStockOnly]);

  const handleInit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post('/api/v1/inventory', {
        productId: Number(initProductId),
        locationId: Number(initLocationId),
        onHand: Number(initOnHand),
      });
      showSuccess('Inventory record initialized.');
      setInitModal(false);
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Failed to initialize inventory.'));
    }
  };

  const handleAdjust = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!adjTarget) return;
    try {
      await api.post('/api/v1/inventory/adjustments', {
        productId: adjTarget.productId,
        locationId: adjTarget.locationId,
        quantityDelta: Number(adjDelta),
        reason: adjReason,
        notes: adjNotes.trim() || null,
        idempotencyKey: `ui-adj-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      });
      showSuccess('Stock adjustment recorded in ledger.');
      setAdjModal(false);
      setAdjNotes('');
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Stock adjustment rejected.'));
    }
  };

  return (
    <div>
      <PageHeader
        title="Inventory & Audit Ledger"
        subtitle="Track on-hand, reserved, and available stock alongside immutable transaction history."
        actions={
          isAdmin && (
            <button
              type="button"
              onClick={() => setInitModal(true)}
              className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
            >
              <Plus className="h-4 w-4" /> Initialize Stock
            </button>
          )
        }
      />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setTab('stock')}
            className={`rounded-lg px-4 py-2 text-sm font-medium ${tab === 'stock' ? 'bg-indigo-600 text-white' : 'bg-white border border-slate-300 text-slate-700'}`}
          >
            Stock Levels ({items.length})
          </button>
          <button
            type="button"
            onClick={() => setTab('ledger')}
            className={`rounded-lg px-4 py-2 text-sm font-medium ${tab === 'ledger' ? 'bg-indigo-600 text-white' : 'bg-white border border-slate-300 text-slate-700'}`}
          >
            Transaction Ledger ({ledger.length})
          </button>
        </div>

        {tab === 'stock' && (
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" checked={lowStockOnly} onChange={(e) => setLowStockOnly(e.target.checked)} />
            Low-stock alerts only (available ≤ 20)
          </label>
        )}
      </div>

      {error && <ErrorBanner message={error} onRetry={load} />}

      {loading ? (
        <LoadingSkeleton />
      ) : tab === 'stock' ? (
        items.length === 0 ? (
          <EmptyState title="No inventory records" description="Initialize stock for a product and location to begin." />
        ) : (
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-4 py-3">SKU</th>
                  <th className="px-4 py-3">Product</th>
                  <th className="px-4 py-3">Location</th>
                  <th className="px-4 py-3 text-right">On-Hand</th>
                  <th className="px-4 py-3 text-right">Reserved</th>
                  <th className="px-4 py-3 text-right">Available</th>
                  {isAdmin && <th className="px-4 py-3 text-right">Action</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {items.map((inv) => (
                  <tr key={inv.id} className="hover:bg-slate-50/60">
                    <td className="px-4 py-3 font-mono text-xs font-semibold text-slate-900">{inv.sku}</td>
                    <td className="px-4 py-3 font-medium text-slate-900">{inv.productName}</td>
                    <td className="px-4 py-3 text-slate-600">{inv.locationCode} — {inv.locationName}</td>
                    <td className="px-4 py-3 text-right">{inv.onHand}</td>
                    <td className="px-4 py-3 text-right text-slate-500">{inv.reserved}</td>
                    <td className={`px-4 py-3 text-right font-bold ${inv.available <= 5 ? 'text-rose-600' : inv.available <= 20 ? 'text-amber-600' : 'text-emerald-700'}`}>
                      {inv.available}
                    </td>
                    {isAdmin && (
                      <td className="px-4 py-3 text-right">
                        <button
                          type="button"
                          onClick={() => { setAdjTarget(inv); setAdjDelta('10'); setAdjModal(true); }}
                          className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100"
                        >
                          <SlidersHorizontal className="h-3 w-3" /> Adjust
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Timestamp</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">SKU</th>
                <th className="px-4 py-3">Location</th>
                <th className="px-4 py-3 text-right">On-Hand Δ</th>
                <th className="px-4 py-3 text-right">Reserved Δ</th>
                <th className="px-4 py-3 text-right">Ref ID</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {ledger.map((tx) => (
                <tr key={tx.id}>
                  <td className="px-4 py-2.5 text-xs text-slate-500">{new Date(tx.createdAt).toLocaleString()}</td>
                  <td className="px-4 py-2.5"><StatusBadge status={tx.type} /></td>
                  <td className="px-4 py-2.5 font-mono text-xs">{tx.sku}</td>
                  <td className="px-4 py-2.5 text-slate-600">{tx.locationCode}</td>
                  <td className={`px-4 py-2.5 text-right font-semibold ${tx.onHandDelta > 0 ? 'text-emerald-600' : tx.onHandDelta < 0 ? 'text-rose-600' : 'text-slate-400'}`}>
                    {tx.onHandDelta > 0 ? `+${tx.onHandDelta}` : tx.onHandDelta}
                  </td>
                  <td className="px-4 py-2.5 text-right text-slate-600">
                    {tx.reservedDelta > 0 ? `+${tx.reservedDelta}` : tx.reservedDelta}
                  </td>
                  <td className="px-4 py-2.5 text-right text-xs text-slate-500">{tx.referenceId ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={initModal} title="Initialize Stock Record" onClose={() => setInitModal(false)}>
        <form onSubmit={handleInit} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-700">Product</label>
            <select required value={initProductId} onChange={(e) => setInitProductId(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="">Select product...</option>
              {products.map((p) => <option key={p.id} value={p.id}>{p.sku} — {p.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Location</label>
            <select required value={initLocationId} onChange={(e) => setInitLocationId(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="">Select location...</option>
              {locations.map((l) => <option key={l.id} value={l.id}>{l.code} — {l.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Initial On-Hand Quantity</label>
            <input required type="number" min="0" value={initOnHand} onChange={(e) => setInitOnHand(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setInitModal(false)} className="rounded-lg border px-4 py-2 text-sm">Cancel</button>
            <button type="submit" className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white">Initialize</button>
          </div>
        </form>
      </Modal>

      <Modal open={adjModal} title={adjTarget ? `Adjust Stock: ${adjTarget.sku} @ ${adjTarget.locationCode}` : 'Adjust Stock'} onClose={() => setAdjModal(false)}>
        <form onSubmit={handleAdjust} className="space-y-4">
          <div className="rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
            Current On-Hand: <strong>{adjTarget?.onHand}</strong> · Reserved: <strong>{adjTarget?.reserved}</strong> · Available: <strong>{adjTarget?.available}</strong>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-700">Quantity Delta (+ or -)</label>
              <input required type="number" value={adjDelta} onChange={(e) => setAdjDelta(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700">Reason</label>
              <select value={adjReason} onChange={(e) => setAdjReason(e.target.value as AdjustmentReason)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
                {REASONS.map((r) => <option key={r} value={r}>{r.replace(/_/g, ' ')}</option>)}
              </select>
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Audit Notes</label>
            <input value={adjNotes} onChange={(e) => setAdjNotes(e.target.value)} placeholder="Cycle count variance..." className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setAdjModal(false)} className="rounded-lg border px-4 py-2 text-sm">Cancel</button>
            <button type="submit" className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white">Apply Adjustment</button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
