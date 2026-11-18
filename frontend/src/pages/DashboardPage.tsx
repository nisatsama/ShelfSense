import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Package,
  MapPin,
  Boxes,
  AlertTriangle,
  Truck,
  ShoppingCart,
  RefreshCw,
  TrendingUp,
} from 'lucide-react';
import { ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid } from 'recharts';
import { api, extractErrorMessage } from '../api/client';
import type { DashboardSummary, Forecast, InventoryItem, ReplenishmentRecommendation } from '../api/types';
import { ErrorBanner, LoadingSkeleton, PageHeader, StatCard, StatusBadge } from '../components/UiPrimitives';

export const DashboardPage: React.FC = () => {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [lowStock, setLowStock] = useState<InventoryItem[]>([]);
  const [replenishments, setReplenishments] = useState<ReplenishmentRecommendation[]>([]);
  const [forecasts, setForecasts] = useState<Forecast[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [sumRes, invRes, repRes, fcRes] = await Promise.all([
        api.get<DashboardSummary>('/api/v1/dashboard/summary'),
        api.get<InventoryItem[]>('/api/v1/inventory', { params: { lowStockThreshold: 20 } }),
        api.get<ReplenishmentRecommendation[]>('/api/v1/replenishment', { params: { status: 'PENDING' } }),
        api.get<Forecast[]>('/api/v1/forecasts'),
      ]);
      setSummary(sumRes.data);
      setLowStock(invRes.data.slice(0, 8));
      setReplenishments(repRes.data.slice(0, 6));
      setForecasts(fcRes.data.slice(0, 8));
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to load dashboard overview.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  if (loading) return <LoadingSkeleton rows={6} />;

  const forecastChartData = forecasts.map((f) => ({
    label: `${f.sku} (${f.locationCode})`,
    dailyDemand: Number(f.predictedDailyDemand),
  }));

  return (
    <div>
      <PageHeader
        title="Operations Overview"
        subtitle="Real-time stock health, supply chain throughput, and demand intelligence."
        actions={
          <button
            type="button"
            onClick={load}
            className="rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Refresh
          </button>
        }
      />

      {error && <ErrorBanner message={error} onRetry={load} />}

      {summary && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard label="Total Products" value={summary.totalProducts} icon={<Package className="h-5 w-5" />} tone="info" />
          <StatCard label="Locations" value={summary.totalLocations} icon={<MapPin className="h-5 w-5" />} />
          <StatCard
            label="Total Inventory Units"
            value={summary.totalInventoryUnits.toLocaleString()}
            helper={`${summary.totalAvailableUnits.toLocaleString()} available · ${summary.totalReservedUnits.toLocaleString()} reserved`}
            icon={<Boxes className="h-5 w-5" />}
            tone="success"
          />
          <StatCard
            label="Low / Critical Stock"
            value={`${summary.lowStockItems} / ${summary.criticalStockItems}`}
            helper="Available ≤ 20 / ≤ 5 units"
            icon={<AlertTriangle className="h-5 w-5" />}
            tone={summary.criticalStockItems > 0 ? 'danger' : 'warning'}
          />
          <StatCard label="Pending Transfers" value={summary.pendingTransfers} icon={<Truck className="h-5 w-5" />} />
          <StatCard label="Open Purchase Orders" value={summary.pendingPurchaseOrders} icon={<ShoppingCart className="h-5 w-5" />} />
          <StatCard
            label="Replenishment Alerts"
            value={summary.pendingReplenishments}
            icon={<RefreshCw className="h-5 w-5" />}
            tone={summary.pendingReplenishments > 0 ? 'warning' : 'default'}
          />
          <StatCard label="Generated Forecasts" value={summary.totalForecasts} icon={<TrendingUp className="h-5 w-5" />} tone="info" />
        </div>
      )}

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Low Stock Table */}
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-900">Low-Stock Alerts</h2>
            <Link to="/inventory" className="text-xs font-medium text-indigo-600 hover:underline">
              View all inventory
            </Link>
          </div>
          {lowStock.length === 0 ? (
            <p className="py-8 text-center text-sm text-slate-500">All tracked SKUs are above low-stock threshold.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-100 text-xs uppercase text-slate-500">
                  <tr>
                    <th className="py-2">SKU</th>
                    <th className="py-2">Location</th>
                    <th className="py-2 text-right">Available</th>
                    <th className="py-2 text-right">Reserved</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {lowStock.map((item) => (
                    <tr key={item.id}>
                      <td className="py-2.5 font-medium text-slate-900">{item.sku}</td>
                      <td className="py-2.5 text-slate-600">{item.locationCode}</td>
                      <td className={`py-2.5 text-right font-semibold ${item.available <= 5 ? 'text-rose-600' : 'text-amber-600'}`}>
                        {item.available}
                      </td>
                      <td className="py-2.5 text-right text-slate-500">{item.reserved}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Demand Forecast Overview Chart */}
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-900">Recent Daily Demand Forecasts</h2>
            <Link to="/forecasting" className="text-xs font-medium text-indigo-600 hover:underline">
              Open forecasting
            </Link>
          </div>
          {forecastChartData.length === 0 ? (
            <p className="py-12 text-center text-sm text-slate-500">
              No forecasts persisted yet. Generate one in Demand Forecasting.
            </p>
          ) : (
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={forecastChartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Bar dataKey="dailyDemand" name="Predicted Daily Units" fill="#4f46e5" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
      </div>

      {/* Pending Replenishments */}
      <div className="mt-6 rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-900">Pending Replenishment Recommendations</h2>
          <Link to="/replenishment" className="text-xs font-medium text-indigo-600 hover:underline">
            Manage replenishment
          </Link>
        </div>
        {replenishments.length === 0 ? (
          <p className="py-6 text-center text-sm text-slate-500">No pending replenishment actions.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-100 text-xs uppercase text-slate-500">
                <tr>
                  <th className="py-2">SKU</th>
                  <th className="py-2">Location</th>
                  <th className="py-2 text-right">Available</th>
                  <th className="py-2 text-right">Reorder Point</th>
                  <th className="py-2 text-right">Recommended Qty</th>
                  <th className="py-2">Priority</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {replenishments.map((r) => (
                  <tr key={r.id}>
                    <td className="py-2.5 font-medium text-slate-900">{r.sku}</td>
                    <td className="py-2.5 text-slate-600">{r.locationCode}</td>
                    <td className="py-2.5 text-right">{r.availableStock}</td>
                    <td className="py-2.5 text-right">{r.reorderPoint}</td>
                    <td className="py-2.5 text-right font-semibold text-indigo-700">{r.recommendedQuantity}</td>
                    <td className="py-2.5"><StatusBadge status={r.priority} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
