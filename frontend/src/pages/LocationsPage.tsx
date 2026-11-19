import React, { useEffect, useState } from 'react';
import { Plus } from 'lucide-react';
import { api, extractErrorMessage } from '../api/client';
import type { InventoryItem, Location, LocationType } from '../api/types';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { EmptyState, ErrorBanner, LoadingSkeleton, Modal, PageHeader, StatusBadge } from '../components/UiPrimitives';

export const LocationsPage: React.FC = () => {
  const { isAdmin } = useAuth();
  const { showSuccess, showError } = useToast();
  const [locations, setLocations] = useState<Location[]>([]);
  const [typeFilter, setTypeFilter] = useState<string>('');
  const [selectedLoc, setSelectedLoc] = useState<Location | null>(null);
  const [locInventory, setLocInventory] = useState<InventoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [type, setType] = useState<LocationType>('STORE');
  const [address, setAddress] = useState('');

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.get<Location[]>('/api/v1/locations', {
        params: typeFilter ? { type: typeFilter } : {},
      });
      setLocations(data);
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to load locations.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [typeFilter]);

  const inspectLocation = async (loc: Location) => {
    setSelectedLoc(loc);
    try {
      const { data } = await api.get<InventoryItem[]>('/api/v1/inventory', { params: { locationId: loc.id } });
      setLocInventory(data);
    } catch (err) {
      showError(extractErrorMessage(err, 'Failed to load location stock.'));
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post('/api/v1/locations', {
        code: code.trim(),
        name: name.trim(),
        type,
        address: address.trim() || null,
      });
      showSuccess('Location created.');
      setModalOpen(false);
      setCode('');
      setName('');
      setAddress('');
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Unable to create location.'));
    }
  };

  return (
    <div>
      <PageHeader
        title="Supply Chain Locations"
        subtitle="Warehouses, distribution centres, and retail stores across the ShelfSense network."
        actions={
          isAdmin && (
            <button
              type="button"
              onClick={() => setModalOpen(true)}
              className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
            >
              <Plus className="h-4 w-4" /> New Location
            </button>
          )
        }
      />

      <div className="mb-4 flex flex-wrap gap-2">
        {['', 'WAREHOUSE', 'DISTRIBUTION_CENTER', 'STORE'].map((t) => (
          <button
            key={t || 'ALL'}
            type="button"
            onClick={() => setTypeFilter(t)}
            className={`rounded-lg px-3.5 py-1.5 text-xs font-semibold border ${
              typeFilter === t
                ? 'border-indigo-600 bg-indigo-600 text-white'
                : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
            }`}
          >
            {t ? t.replace(/_/g, ' ') : 'ALL LOCATIONS'}
          </button>
        ))}
      </div>

      {error && <ErrorBanner message={error} onRetry={load} />}
      {loading ? (
        <LoadingSkeleton />
      ) : locations.length === 0 ? (
        <EmptyState title="No locations found" description="Add a warehouse, distribution centre, or retail store." />
      ) : (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <div className="lg:col-span-2 overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-4 py-3">Code</th>
                  <th className="px-4 py-3">Name</th>
                  <th className="px-4 py-3">Type</th>
                  <th className="px-4 py-3">Address</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {locations.map((loc) => (
                  <tr
                    key={loc.id}
                    onClick={() => inspectLocation(loc)}
                    className={`cursor-pointer hover:bg-indigo-50/40 ${selectedLoc?.id === loc.id ? 'bg-indigo-50/70' : ''}`}
                  >
                    <td className="px-4 py-3 font-mono text-xs font-semibold text-slate-900">{loc.code}</td>
                    <td className="px-4 py-3 font-medium text-slate-900">{loc.name}</td>
                    <td className="px-4 py-3"><StatusBadge status={loc.type} /></td>
                    <td className="px-4 py-3 text-slate-500">{loc.address || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
            <h2 className="text-sm font-semibold text-slate-900">
              {selectedLoc ? `Inventory at ${selectedLoc.code}` : 'Select a location'}
            </h2>
            {!selectedLoc ? (
              <p className="mt-4 text-sm text-slate-500">Click any location row to view its live stock levels.</p>
            ) : locInventory.length === 0 ? (
              <p className="mt-4 text-sm text-slate-500">No inventory records initialized at this location.</p>
            ) : (
              <ul className="mt-4 divide-y divide-slate-100 text-sm">
                {locInventory.map((inv) => (
                  <li key={inv.id} className="flex items-center justify-between py-2.5">
                    <div>
                      <div className="font-medium text-slate-900">{inv.sku}</div>
                      <div className="text-xs text-slate-500">{inv.productName}</div>
                    </div>
                    <div className="text-right">
                      <div className="font-semibold text-slate-900">{inv.available} avail</div>
                      <div className="text-xs text-slate-500">{inv.onHand} on-hand · {inv.reserved} res</div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}

      <Modal open={modalOpen} title="Create Location" onClose={() => setModalOpen(false)}>
        <form onSubmit={handleCreate} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-700">Code</label>
              <input required value={code} onChange={(e) => setCode(e.target.value)} placeholder="CA_1" className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-700">Type</label>
              <select value={type} onChange={(e) => setType(e.target.value as LocationType)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
                <option value="WAREHOUSE">Warehouse</option>
                <option value="DISTRIBUTION_CENTER">Distribution Center</option>
                <option value="STORE">Retail Store</option>
              </select>
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Name</label>
            <input required value={name} onChange={(e) => setName(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Address</label>
            <input value={address} onChange={(e) => setAddress(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={() => setModalOpen(false)} className="rounded-lg border px-4 py-2 text-sm">Cancel</button>
            <button type="submit" className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white">Create</button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
