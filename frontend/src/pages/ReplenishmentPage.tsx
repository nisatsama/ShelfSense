import React, { useEffect, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { api, extractErrorMessage } from '../api/client';
import type { ReplenishmentRecommendation, Supplier } from '../api/types';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { EmptyState, ErrorBanner, LoadingSkeleton, Modal, PageHeader, StatusBadge } from '../components/UiPrimitives';

export const ReplenishmentPage: React.FC = () => {
  const { isAdmin } = useAuth();
  const { showSuccess, showError } = useToast();
  const [items, setItems] = useState<ReplenishmentRecommendation[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [approveTarget, setApproveTarget] = useState<ReplenishmentRecommendation | null>(null);
  const [supplierId, setSupplierId] = useState('');
  const [notes, setNotes] = useState('');

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [repRes, supRes] = await Promise.all([
        api.get<ReplenishmentRecommendation[]>('/api/v1/replenishment', {
          params: statusFilter ? { status: statusFilter } : {},
        }),
        api.get<Supplier[]>('/api/v1/suppliers'),
      ]);
      setItems(repRes.data);
      setSuppliers(supRes.data);
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to load replenishment recommendations.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [statusFilter]);

  const handleGenerate = async () => {
    setRunning(true);
    try {
      const { data } = await api.post<ReplenishmentRecommendation[]>('/api/v1/replenishment/generate', {});
      showSuccess(`Evaluated inventory: ${data.length} active replenishment recommendations.`);
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Failed to generate replenishment recommendations.'));
    } finally {
      setRunning(false);
    }
  };

  const handleApprove = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!approveTarget) return;
    try {
      await api.put(`/api/v1/replenishment/${approveTarget.id}/approve`, {
        supplierId: supplierId ? Number(supplierId) : null,
        notes: notes.trim() || null,
      });
      showSuccess(supplierId ? 'Recommendation approved and Purchase Order created.' : 'Recommendation approved.');
      setApproveTarget(null);
      setSupplierId('');
      setNotes('');
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Approval failed.'));
    }
  };

  const handleDismiss = async (id: number) => {
    try {
      await api.put(`/api/v1/replenishment/${id}/dismiss`, { notes: 'Dismissed via dashboard' });
      showSuccess('Recommendation dismissed.');
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Dismiss failed.'));
    }
  };

  return (
    <div>
      <PageHeader
        title="Replenishment Engine"
        subtitle="Reorder points, safety stock, incoming supply deductions, and forecast-backed order quantities."
        actions={
          isAdmin && (
            <button
              type="button"
              disabled={running}
              onClick={handleGenerate}
              className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-60"
            >
              <RefreshCw className={`h-4 w-4 ${running ? 'animate-spin' : ''}`} />
              {running ? 'Calculating...' : 'Run Replenishment'}
            </button>
          )
        }
      />

      <div className="mb-4 flex flex-wrap gap-2">
        {['PENDING', 'APPROVED', 'ORDERED', 'DISMISSED', ''].map((st) => (
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
      ) : items.length === 0 ? (
        <EmptyState title="No recommendations" description="Run the replenishment engine to identify SKUs below reorder point." />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">SKU / Location</th>
                <th className="px-4 py-3 text-right">Avail (+In)</th>
                <th className="px-4 py-3 text-right">Daily Demand</th>
                <th className="px-4 py-3 text-right">Safety / ROP</th>
                <th className="px-4 py-3 text-right">Rec. Qty</th>
                <th className="px-4 py-3">Priority</th>
                <th className="px-4 py-3">Status</th>
                {isAdmin && <th className="px-4 py-3 text-right">Actions</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((r) => (
                <tr key={r.id}>
                  <td className="px-4 py-3">
                    <div className="font-mono text-xs font-semibold text-slate-900">{r.sku}</div>
                    <div className="text-xs text-slate-500">{r.locationCode}</div>
                  </td>
                  <td className="px-4 py-3 text-right">{r.availableStock} (+{r.incomingStock})</td>
                  <td className="px-4 py-3 text-right">{Number(r.avgDailyDemand).toFixed(2)}/d</td>
                  <td className="px-4 py-3 text-right">{r.safetyStock} / {r.reorderPoint}</td>
                  <td className="px-4 py-3 text-right font-bold text-indigo-700">{r.recommendedQuantity}</td>
                  <td className="px-4 py-3"><StatusBadge status={r.priority} /></td>
                  <td className="px-4 py-3"><StatusBadge status={r.status} /></td>
                  {isAdmin && (
                    <td className="px-4 py-3 text-right">
                      {r.status === 'PENDING' && (
                        <div className="inline-flex gap-1.5">
                          <button
                            type="button"
                            onClick={() => setApproveTarget(r)}
                            className="rounded bg-emerald-600 px-2.5 py-1 text-xs font-semibold text-white"
                          >
                            Approve
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDismiss(r.id)}
                            className="rounded border border-slate-300 px-2.5 py-1 text-xs"
                          >
                            Dismiss
                          </button>
                        </div>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={!!approveTarget} title="Approve Replenishment Recommendation" onClose={() => setApproveTarget(null)}>
        <form onSubmit={handleApprove} className="space-y-4">
          <div className="rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
            SKU <strong>{approveTarget?.sku}</strong> at <strong>{approveTarget?.locationCode}</strong> · Recommended order: <strong>{approveTarget?.recommendedQuantity}</strong> units
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Auto-Create Purchase Order with Supplier (Optional)</label>
            <select value={supplierId} onChange={(e) => setSupplierId(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="">Approve only (no auto-PO)</option>
              {suppliers.map((s) => <option key={s.id} value={s.id}>{s.code} — {s.name} ({s.leadTimeDays}d lead)</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Review Notes</label>
            <input value={notes} onChange={(e) => setNotes(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setApproveTarget(null)} className="rounded-lg border px-4 py-2 text-sm">Cancel</button>
            <button type="submit" className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white">Confirm Approval</button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
