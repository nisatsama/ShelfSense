import React, { useEffect, useState } from 'react';
import { Plus } from 'lucide-react';
import { api, extractErrorMessage } from '../api/client';
import type { Location, Product, Transfer } from '../api/types';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { EmptyState, ErrorBanner, LoadingSkeleton, Modal, PageHeader, StatusBadge } from '../components/UiPrimitives';

export const TransfersPage: React.FC = () => {
  const { isAdmin } = useAuth();
  const { showSuccess, showError } = useToast();
  const [transfers, setTransfers] = useState<Transfer[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [locations, setLocations] = useState<Location[]>([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [productId, setProductId] = useState('');
  const [sourceLocationId, setSourceLocationId] = useState('');
  const [destinationLocationId, setDestinationLocationId] = useState('');
  const [quantity, setQuantity] = useState('10');
  const [notes, setNotes] = useState('');

  const [receiveTarget, setReceiveTarget] = useState<Transfer | null>(null);
  const [receivedQty, setReceivedQty] = useState('0');
  const [damagedQty, setDamagedQty] = useState('0');
  const [receiveNotes, setReceiveNotes] = useState('');

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [trRes, prodRes, locRes] = await Promise.all([
        api.get<Transfer[]>('/api/v1/transfers', { params: statusFilter ? { status: statusFilter } : {} }),
        api.get<Product[]>('/api/v1/products'),
        api.get<Location[]>('/api/v1/locations'),
      ]);
      setTransfers(trRes.data);
      setProducts(prodRes.data);
      setLocations(locRes.data);
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to load transfers.'));
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
      await api.post('/api/v1/transfers', {
        productId: Number(productId),
        sourceLocationId: Number(sourceLocationId),
        destinationLocationId: Number(destinationLocationId),
        quantity: Number(quantity),
        notes: notes.trim() || null,
      });
      showSuccess('Transfer created.');
      setCreateOpen(false);
      setNotes('');
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Failed to create transfer.'));
    }
  };

  const transition = async (id: number, action: 'approve' | 'dispatch' | 'in-transit' | 'cancel') => {
    try {
      await api.put(`/api/v1/transfers/${id}/${action}`, action === 'dispatch' ? {} : undefined);
      showSuccess(`Transfer ${action.replace('-', ' ')} succeeded.`);
      load();
    } catch (err) {
      showError(extractErrorMessage(err, `Transfer ${action} failed.`));
    }
  };

  const handleReceive = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!receiveTarget) return;
    try {
      await api.put(`/api/v1/transfers/${receiveTarget.id}/receive`, {
        receivedQuantity: Number(receivedQty),
        damagedQuantity: Number(damagedQty),
        notes: receiveNotes.trim() || null,
      });
      showSuccess('Transfer receipt recorded.');
      setReceiveTarget(null);
      setReceiveNotes('');
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Failed to receive transfer.'));
    }
  };

  return (
    <div>
      <PageHeader
        title="Multi-Location Transfers"
        subtitle="Move stock across Warehouse → Distribution Centre → Retail Store with full receipt reconciliation."
        actions={
          isAdmin && (
            <button
              type="button"
              onClick={() => setCreateOpen(true)}
              className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
            >
              <Plus className="h-4 w-4" /> New Transfer
            </button>
          )
        }
      />

      <div className="mb-4 flex flex-wrap gap-2">
        {['', 'CREATED', 'APPROVED', 'DISPATCHED', 'IN_TRANSIT', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'].map((st) => (
          <button
            key={st || 'ALL'}
            type="button"
            onClick={() => setStatusFilter(st)}
            className={`rounded-lg border px-3 py-1.5 text-xs font-semibold ${
              statusFilter === st ? 'border-indigo-600 bg-indigo-600 text-white' : 'border-slate-300 bg-white text-slate-700'
            }`}
          >
            {st ? st.replace(/_/g, ' ') : 'ALL'}
          </button>
        ))}
      </div>

      {error && <ErrorBanner message={error} onRetry={load} />}
      {loading ? (
        <LoadingSkeleton />
      ) : transfers.length === 0 ? (
        <EmptyState title="No transfers found" description="Create a multi-location stock transfer to replenish stores." />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Number</th>
                <th className="px-4 py-3">SKU</th>
                <th className="px-4 py-3">Route</th>
                <th className="px-4 py-3 text-right">Req / Disp / Rec / Dmg</th>
                <th className="px-4 py-3">Status</th>
                {isAdmin && <th className="px-4 py-3 text-right">Actions</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {transfers.map((t) => (
                <tr key={t.id}>
                  <td className="px-4 py-3 font-mono text-xs font-semibold">{t.transferNumber}</td>
                  <td className="px-4 py-3 font-medium">{t.sku}</td>
                  <td className="px-4 py-3 text-slate-600">{t.sourceLocationCode} → {t.destinationLocationCode}</td>
                  <td className="px-4 py-3 text-right font-mono text-xs">
                    {t.requestedQuantity} / {t.dispatchedQuantity} / {t.receivedQuantity} / {t.damagedQuantity}
                  </td>
                  <td className="px-4 py-3"><StatusBadge status={t.status} /></td>
                  {isAdmin && (
                    <td className="px-4 py-3 text-right">
                      <div className="inline-flex flex-wrap justify-end gap-1">
                        {t.status === 'CREATED' && (
                          <>
                            <button type="button" onClick={() => transition(t.id, 'approve')} className="rounded bg-sky-600 px-2 py-1 text-xs font-medium text-white">Approve</button>
                            <button type="button" onClick={() => transition(t.id, 'cancel')} className="rounded border px-2 py-1 text-xs">Cancel</button>
                          </>
                        )}
                        {t.status === 'APPROVED' && (
                          <>
                            <button type="button" onClick={() => transition(t.id, 'dispatch')} className="rounded bg-indigo-600 px-2 py-1 text-xs font-medium text-white">Dispatch</button>
                            <button type="button" onClick={() => transition(t.id, 'cancel')} className="rounded border px-2 py-1 text-xs">Cancel</button>
                          </>
                        )}
                        {t.status === 'DISPATCHED' && (
                          <button type="button" onClick={() => transition(t.id, 'in-transit')} className="rounded bg-amber-600 px-2 py-1 text-xs font-medium text-white">In Transit</button>
                        )}
                        {['DISPATCHED', 'IN_TRANSIT', 'PARTIALLY_RECEIVED'].includes(t.status) && (
                          <button
                            type="button"
                            onClick={() => {
                              setReceiveTarget(t);
                              setReceivedQty(String(t.remainingToReceive));
                              setDamagedQty('0');
                            }}
                            className="rounded bg-emerald-600 px-2 py-1 text-xs font-medium text-white"
                          >
                            Receive
                          </button>
                        )}
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={createOpen} title="Create Inventory Transfer" onClose={() => setCreateOpen(false)}>
        <form onSubmit={handleCreate} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-700">Product</label>
            <select required value={productId} onChange={(e) => setProductId(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="">Select product...</option>
              {products.map((p) => <option key={p.id} value={p.id}>{p.sku} — {p.name}</option>)}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-700">Source Location</label>
              <select required value={sourceLocationId} onChange={(e) => setSourceLocationId(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
                <option value="">Select source...</option>
                {locations.map((l) => <option key={l.id} value={l.id}>{l.code} ({l.type})</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700">Destination Location</label>
              <select required value={destinationLocationId} onChange={(e) => setDestinationLocationId(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
                <option value="">Select destination...</option>
                {locations.map((l) => <option key={l.id} value={l.id}>{l.code} ({l.type})</option>)}
              </select>
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Quantity</label>
            <input required type="number" min="1" value={quantity} onChange={(e) => setQuantity(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Notes</label>
            <input value={notes} onChange={(e) => setNotes(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setCreateOpen(false)} className="rounded-lg border px-4 py-2 text-sm">Cancel</button>
            <button type="submit" className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white">Create Transfer</button>
          </div>
        </form>
      </Modal>

      <Modal open={!!receiveTarget} title={receiveTarget ? `Receive ${receiveTarget.transferNumber}` : 'Receive'} onClose={() => setReceiveTarget(null)}>
        <form onSubmit={handleReceive} className="space-y-4">
          <div className="rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
            Remaining in transit: <strong>{receiveTarget?.remainingToReceive}</strong> units (SKU {receiveTarget?.sku})
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-700">Good Received Qty</label>
              <input required type="number" min="0" value={receivedQty} onChange={(e) => setReceivedQty(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700">Damaged / Rejected Qty</label>
              <input required type="number" min="0" value={damagedQty} onChange={(e) => setDamagedQty(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Receipt Notes</label>
            <input value={receiveNotes} onChange={(e) => setReceiveNotes(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setReceiveTarget(null)} className="rounded-lg border px-4 py-2 text-sm">Cancel</button>
            <button type="submit" className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white">Confirm Receipt</button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
