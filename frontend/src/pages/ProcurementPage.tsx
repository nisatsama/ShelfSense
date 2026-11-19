import React, { useEffect, useState } from 'react';
import { Plus } from 'lucide-react';
import { api, extractErrorMessage } from '../api/client';
import type { Location, Product, PurchaseOrder, Supplier } from '../api/types';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { EmptyState, ErrorBanner, LoadingSkeleton, Modal, PageHeader, StatusBadge } from '../components/UiPrimitives';

export const ProcurementPage: React.FC = () => {
  const { isAdmin } = useAuth();
  const { showSuccess, showError } = useToast();
  const [tab, setTab] = useState<'orders' | 'suppliers'>('orders');
  const [orders, setOrders] = useState<PurchaseOrder[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [locations, setLocations] = useState<Location[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [supOpen, setSupOpen] = useState(false);
  const [supCode, setSupCode] = useState('');
  const [supName, setSupName] = useState('');
  const [supEmail, setSupEmail] = useState('');
  const [supLead, setSupLead] = useState('7');

  const [poOpen, setPoOpen] = useState(false);
  const [poSupplierId, setPoSupplierId] = useState('');
  const [poLocationId, setPoLocationId] = useState('');
  const [poProductId, setPoProductId] = useState('');
  const [poQty, setPoQty] = useState('50');
  const [poCost, setPoCost] = useState('5.00');
  const [poNotes, setPoNotes] = useState('');

  const [grnTarget, setGrnTarget] = useState<PurchaseOrder | null>(null);
  const [grnQtys, setGrnQtys] = useState<Record<number, string>>({});
  const [grnNotes, setGrnNotes] = useState('');

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [poRes, supRes, prodRes, locRes] = await Promise.all([
        api.get<PurchaseOrder[]>('/api/v1/purchase-orders'),
        api.get<Supplier[]>('/api/v1/suppliers'),
        api.get<Product[]>('/api/v1/products'),
        api.get<Location[]>('/api/v1/locations'),
      ]);
      setOrders(poRes.data);
      setSuppliers(supRes.data);
      setProducts(prodRes.data);
      setLocations(locRes.data);
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to load procurement data.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const createSupplier = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post('/api/v1/suppliers', {
        code: supCode.trim(),
        name: supName.trim(),
        contactEmail: supEmail.trim() || null,
        leadTimeDays: Number(supLead),
        active: true,
      });
      showSuccess('Supplier added.');
      setSupOpen(false);
      setSupCode('');
      setSupName('');
      setSupEmail('');
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Failed to add supplier.'));
    }
  };

  const createPo = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post('/api/v1/purchase-orders', {
        supplierId: Number(poSupplierId),
        destinationLocationId: Number(poLocationId),
        notes: poNotes.trim() || null,
        items: [{ productId: Number(poProductId), orderedQuantity: Number(poQty), unitCost: Number(poCost) }],
      });
      showSuccess('Purchase order created.');
      setPoOpen(false);
      setPoNotes('');
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Failed to create purchase order.'));
    }
  };

  const poAction = async (id: number, action: 'approve' | 'order' | 'cancel') => {
    try {
      await api.put(`/api/v1/purchase-orders/${id}/${action}`);
      showSuccess(`Purchase order ${action} succeeded.`);
      load();
    } catch (err) {
      showError(extractErrorMessage(err, `Failed to ${action} PO.`));
    }
  };

  const openGrn = (po: PurchaseOrder) => {
    setGrnTarget(po);
    const initial: Record<number, string> = {};
    po.items.forEach((item) => {
      initial[item.id] = String(item.remainingQuantity);
    });
    setGrnQtys(initial);
  };

  const submitGrn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!grnTarget) return;
    const items = Object.entries(grnQtys)
      .map(([itemId, val]) => ({ purchaseOrderItemId: Number(itemId), receivedQuantity: Number(val) }))
      .filter((x) => x.receivedQuantity > 0);
    if (items.length === 0) {
      showError('Enter at least one received quantity greater than 0.');
      return;
    }
    try {
      await api.post(`/api/v1/purchase-orders/${grnTarget.id}/receipts`, {
        notes: grnNotes.trim() || null,
        idempotencyKey: `ui-grn-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        items,
      });
      showSuccess('Goods receipt verified and stock added to inventory.');
      setGrnTarget(null);
      setGrnNotes('');
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Goods receipt failed.'));
    }
  };

  return (
    <div>
      <PageHeader
        title="Procurement & Goods Receipt"
        subtitle="Manage suppliers, purchase order approvals, and partial or full delivery receipts."
        actions={
          isAdmin && (
            <>
              <button
                type="button"
                onClick={() => setSupOpen(true)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                <Plus className="h-4 w-4" /> Supplier
              </button>
              <button
                type="button"
                onClick={() => setPoOpen(true)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
              >
                <Plus className="h-4 w-4" /> New Purchase Order
              </button>
            </>
          )
        }
      />

      <div className="mb-4 flex gap-2">
        <button
          type="button"
          onClick={() => setTab('orders')}
          className={`rounded-lg px-4 py-2 text-sm font-medium ${tab === 'orders' ? 'bg-indigo-600 text-white' : 'bg-white border border-slate-300 text-slate-700'}`}
        >
          Purchase Orders ({orders.length})
        </button>
        <button
          type="button"
          onClick={() => setTab('suppliers')}
          className={`rounded-lg px-4 py-2 text-sm font-medium ${tab === 'suppliers' ? 'bg-indigo-600 text-white' : 'bg-white border border-slate-300 text-slate-700'}`}
        >
          Suppliers ({suppliers.length})
        </button>
      </div>

      {error && <ErrorBanner message={error} onRetry={load} />}
      {loading ? (
        <LoadingSkeleton />
      ) : tab === 'orders' ? (
        orders.length === 0 ? (
          <EmptyState title="No purchase orders" description="Create a purchase order for a supplier to receive inventory." />
        ) : (
          <div className="space-y-3">
            {orders.map((po) => (
              <div key={po.id} className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <div className="flex items-center gap-2.5">
                      <span className="font-mono text-sm font-bold text-slate-900">{po.poNumber}</span>
                      <StatusBadge status={po.status} />
                    </div>
                    <p className="mt-1 text-xs text-slate-500">
                      Supplier: <strong>{po.supplierName}</strong> → Destination: <strong>{po.destinationLocationCode}</strong> · Total: <strong>${Number(po.totalAmount).toFixed(2)}</strong>
                      {po.expectedDeliveryDate ? ` · ETA: ${po.expectedDeliveryDate}` : ''}
                    </p>
                  </div>
                  {isAdmin && (
                    <div className="flex flex-wrap gap-1.5">
                      {po.status === 'DRAFT' && (
                        <>
                          <button type="button" onClick={() => poAction(po.id, 'approve')} className="rounded bg-sky-600 px-3 py-1.5 text-xs font-semibold text-white">Approve</button>
                          <button type="button" onClick={() => poAction(po.id, 'cancel')} className="rounded border px-3 py-1.5 text-xs">Cancel</button>
                        </>
                      )}
                      {po.status === 'APPROVED' && (
                        <button type="button" onClick={() => poAction(po.id, 'order')} className="rounded bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white">Mark Ordered</button>
                      )}
                      {['APPROVED', 'ORDERED', 'PARTIALLY_RECEIVED'].includes(po.status) && (
                        <button type="button" onClick={() => openGrn(po)} className="rounded bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white">Receive Goods</button>
                      )}
                    </div>
                  )}
                </div>
                <div className="mt-3 overflow-x-auto border-t border-slate-100 pt-3">
                  <table className="w-full text-left text-xs">
                    <thead className="text-slate-500">
                      <tr>
                        <th className="py-1">SKU</th>
                        <th className="py-1">Product</th>
                        <th className="py-1 text-right">Ordered</th>
                        <th className="py-1 text-right">Received</th>
                        <th className="py-1 text-right">Unit Cost</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {po.items.map((item) => (
                        <tr key={item.id}>
                          <td className="py-1.5 font-mono font-semibold">{item.sku}</td>
                          <td className="py-1.5">{item.productName}</td>
                          <td className="py-1.5 text-right">{item.orderedQuantity}</td>
                          <td className="py-1.5 text-right font-semibold text-emerald-700">{item.receivedQuantity}</td>
                          <td className="py-1.5 text-right">${Number(item.unitCost).toFixed(2)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ))}
          </div>
        )
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Code</th>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Contact Email</th>
                <th className="px-4 py-3 text-right">Lead Time (Days)</th>
                <th className="px-4 py-3">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {suppliers.map((s) => (
                <tr key={s.id}>
                  <td className="px-4 py-3 font-mono text-xs font-semibold">{s.code}</td>
                  <td className="px-4 py-3 font-medium">{s.name}</td>
                  <td className="px-4 py-3 text-slate-600">{s.contactEmail || '—'}</td>
                  <td className="px-4 py-3 text-right">{s.leadTimeDays}</td>
                  <td className="px-4 py-3"><StatusBadge status={s.active ? 'ACTIVE' : 'DISMISSED'} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={supOpen} title="Add Supplier" onClose={() => setSupOpen(false)}>
        <form onSubmit={createSupplier} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-700">Code</label>
              <input required value={supCode} onChange={(e) => setSupCode(e.target.value)} placeholder="SUP-001" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700">Lead Time (Days)</label>
              <input required type="number" min="1" value={supLead} onChange={(e) => setSupLead(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Supplier Name</label>
            <input required value={supName} onChange={(e) => setSupName(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Contact Email</label>
            <input type="email" value={supEmail} onChange={(e) => setSupEmail(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setSupOpen(false)} className="rounded-lg border px-4 py-2 text-sm">Cancel</button>
            <button type="submit" className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white">Save Supplier</button>
          </div>
        </form>
      </Modal>

      <Modal open={poOpen} title="Create Purchase Order" onClose={() => setPoOpen(false)}>
        <form onSubmit={createPo} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-700">Supplier</label>
            <select required value={poSupplierId} onChange={(e) => setPoSupplierId(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="">Select supplier...</option>
              {suppliers.map((s) => <option key={s.id} value={s.id}>{s.code} — {s.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Destination Location</label>
            <select required value={poLocationId} onChange={(e) => setPoLocationId(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="">Select location...</option>
              {locations.map((l) => <option key={l.id} value={l.id}>{l.code} — {l.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Product</label>
            <select required value={poProductId} onChange={(e) => setPoProductId(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="">Select product...</option>
              {products.map((p) => <option key={p.id} value={p.id}>{p.sku} — {p.name}</option>)}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-700">Quantity</label>
              <input required type="number" min="1" value={poQty} onChange={(e) => setPoQty(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700">Unit Cost ($)</label>
              <input required type="number" step="0.01" min="0" value={poCost} onChange={(e) => setPoCost(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Notes</label>
            <input value={poNotes} onChange={(e) => setPoNotes(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setPoOpen(false)} className="rounded-lg border px-4 py-2 text-sm">Cancel</button>
            <button type="submit" className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white">Create PO</button>
          </div>
        </form>
      </Modal>

      <Modal open={!!grnTarget} title={grnTarget ? `Goods Receipt: ${grnTarget.poNumber}` : 'Receive Goods'} onClose={() => setGrnTarget(null)}>
        <form onSubmit={submitGrn} className="space-y-4">
          {grnTarget?.items.map((item) => (
            <div key={item.id} className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 p-3">
              <div>
                <div className="text-sm font-medium text-slate-900">{item.sku}</div>
                <div className="text-xs text-slate-500">Remaining: {item.remainingQuantity} of {item.orderedQuantity}</div>
              </div>
              <input
                type="number"
                min="0"
                max={item.remainingQuantity}
                value={grnQtys[item.id] ?? '0'}
                onChange={(e) => setGrnQtys((prev) => ({ ...prev, [item.id]: e.target.value }))}
                className="w-24 rounded-lg border border-slate-300 px-2.5 py-1.5 text-sm text-right"
              />
            </div>
          ))}
          <div>
            <label className="block text-xs font-medium text-slate-700">Delivery Notes</label>
            <input value={grnNotes} onChange={(e) => setGrnNotes(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setGrnTarget(null)} className="rounded-lg border px-4 py-2 text-sm">Cancel</button>
            <button type="submit" className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white">Verify &amp; Receive</button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
