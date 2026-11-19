import React, { useEffect, useState } from 'react';
import { Sparkles } from 'lucide-react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
} from 'recharts';
import { api, extractErrorMessage } from '../api/client';
import type { Forecast, Location, ModelInfo, Product } from '../api/types';
import { useToast } from '../context/ToastContext';
import { ErrorBanner, LoadingSkeleton, PageHeader, StatCard, StatusBadge } from '../components/UiPrimitives';

export const ForecastingPage: React.FC = () => {
  const { showSuccess, showError } = useToast();
  const [forecasts, setForecasts] = useState<Forecast[]>([]);
  const [selected, setSelected] = useState<Forecast | null>(null);
  const [modelInfo, setModelInfo] = useState<ModelInfo | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [locations, setLocations] = useState<Location[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [productId, setProductId] = useState('');
  const [locationId, setLocationId] = useState('');
  const [horizonDays, setHorizonDays] = useState('14');
  const [generating, setGenerating] = useState(false);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [fcRes, infoRes, prodRes, locRes] = await Promise.all([
        api.get<Forecast[]>('/api/v1/forecasts'),
        api.get<ModelInfo>('/api/v1/forecasts/model-info'),
        api.get<Product[]>('/api/v1/products'),
        api.get<Location[]>('/api/v1/locations'),
      ]);
      setForecasts(fcRes.data);
      if (fcRes.data.length > 0) setSelected(fcRes.data[0]);
      setModelInfo(infoRes.data);
      setProducts(prodRes.data);
      setLocations(locRes.data);
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to load forecasting intelligence.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleGenerate = async (e: React.FormEvent) => {
    e.preventDefault();
    setGenerating(true);
    try {
      const { data } = await api.post<Forecast>('/api/v1/forecasts/generate', {
        productId: Number(productId),
        locationId: Number(locationId),
        horizonDays: Number(horizonDays),
      });
      showSuccess(`Forecast generated (${data.predictedDailyDemand.toFixed(2)} units/day).`);
      setSelected(data);
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Forecast generation failed. Is the ML service running?'));
    } finally {
      setGenerating(false);
    }
  };

  if (loading) return <LoadingSkeleton rows={6} />;

  const metrics = modelInfo?.metadata?.test_metrics;
  const actualVsPred = modelInfo?.evaluation?.daily_actual_vs_predicted ?? [];

  return (
    <div>
      <PageHeader
        title="Demand Forecasting Intelligence"
        subtitle="Real Walmart M5 forecasting model evaluation, SKU-store predictions, and horizon projections."
      />

      {error && <ErrorBanner message={error} onRetry={load} />}

      {/* Model Status & Genuine Evaluation Metrics */}
      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <StatCard
          label="ML Service Status"
          value={modelInfo?.status || 'UNKNOWN'}
          helper={modelInfo?.model_version || modelInfo?.error}
          tone={modelInfo?.status === 'READY' ? 'success' : 'warning'}
        />
        <StatCard label="Test MAE" value={typeof metrics?.mae === 'number' ? metrics.mae.toFixed(3) : '—'} helper="Units / day (held-out 28d)" />
        <StatCard label="Test RMSE" value={typeof metrics?.rmse === 'number' ? metrics.rmse.toFixed(3) : '—'} helper="Root mean squared error" />
        <StatCard label="Test WAPE" value={typeof metrics?.wape === 'number' ? `${(metrics.wape * 100).toFixed(1)}%` : '—'} helper="Weighted absolute % error" tone="info" />
        <StatCard label="Test Bias" value={typeof metrics?.bias === 'number' ? `${metrics.bias >= 0 ? '+' : ''}${metrics.bias.toFixed(3)}` : '—'} helper="Mean(pred − actual)" />
      </div>

      {/* Generate Forecast Form */}
      <div className="mb-6 rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
        <h2 className="mb-3 text-sm font-semibold text-slate-900">Run Live Demand Forecast</h2>
        <form onSubmit={handleGenerate} className="grid grid-cols-1 gap-3 sm:grid-cols-4">
          <select required value={productId} onChange={(e) => setProductId(e.target.value)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
            <option value="">Select product...</option>
            {products.map((p) => <option key={p.id} value={p.id}>{p.sku} — {p.name}</option>)}
          </select>
          <select required value={locationId} onChange={(e) => setLocationId(e.target.value)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
            <option value="">Select location...</option>
            {locations.map((l) => <option key={l.id} value={l.id}>{l.code} — {l.name}</option>)}
          </select>
          <input
            required
            type="number"
            min="1"
            max="90"
            value={horizonDays}
            onChange={(e) => setHorizonDays(e.target.value)}
            placeholder="Horizon days"
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />
          <button
            type="submit"
            disabled={generating}
            className="inline-flex items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-60"
          >
            <Sparkles className="h-4 w-4" />
            {generating ? 'Forecasting...' : 'Generate Forecast'}
          </button>
        </form>
      </div>

      {/* Charts */}
      <div className="mb-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <h2 className="mb-3 text-sm font-semibold text-slate-900">
            M5 Held-Out Test Evaluation: Actual vs Predicted (28 Days)
          </h2>
          {actualVsPred.length === 0 ? (
            <p className="py-12 text-center text-sm text-slate-500">Start the Python ML service to display model evaluation curves.</p>
          ) : (
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={actualVsPred}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 10 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Legend />
                  <Line type="monotone" dataKey="actual" name="Actual Sales" stroke="#0f172a" strokeWidth={2} dot={false} />
                  <Line type="monotone" dataKey="predicted" name="Model Predicted" stroke="#4f46e5" strokeWidth={2} strokeDasharray="5 5" dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
          <h2 className="mb-3 text-sm font-semibold text-slate-900">
            {selected ? `Horizon Projection: ${selected.sku} @ ${selected.locationCode} (${selected.horizonDays}d)` : 'Selected Forecast Horizon'}
          </h2>
          {!selected || !selected.dailyPredictions?.length ? (
            <p className="py-12 text-center text-sm text-slate-500">Select or generate a forecast to inspect daily predictions.</p>
          ) : (
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={selected.dailyPredictions}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 10 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Area type="monotone" dataKey="predictedDemand" name="Predicted Units" stroke="#4f46e5" fill="#e0e7ff" strokeWidth={2} />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
      </div>

      {/* Forecast History Table */}
      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs">
        <div className="border-b border-slate-200 px-5 py-3">
          <h2 className="text-sm font-semibold text-slate-900">Forecast History ({forecasts.length})</h2>
        </div>
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-4 py-3">SKU</th>
              <th className="px-4 py-3">Location</th>
              <th className="px-4 py-3 text-right">Horizon</th>
              <th className="px-4 py-3 text-right">Avg Daily</th>
              <th className="px-4 py-3 text-right">Total Demand</th>
              <th className="px-4 py-3">Model</th>
              <th className="px-4 py-3">Generated</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {forecasts.map((f) => (
              <tr
                key={f.id}
                onClick={() => setSelected(f)}
                className={`cursor-pointer hover:bg-indigo-50/40 ${selected?.id === f.id ? 'bg-indigo-50/70' : ''}`}
              >
                <td className="px-4 py-3 font-mono text-xs font-semibold">{f.sku}</td>
                <td className="px-4 py-3 text-slate-600">{f.locationCode}</td>
                <td className="px-4 py-3 text-right">{f.horizonDays}d</td>
                <td className="px-4 py-3 text-right font-semibold text-indigo-700">{Number(f.predictedDailyDemand).toFixed(2)}</td>
                <td className="px-4 py-3 text-right">{Number(f.predictedTotalDemand).toFixed(1)}</td>
                <td className="px-4 py-3"><StatusBadge status={f.modelVersion} /></td>
                <td className="px-4 py-3 text-xs text-slate-500">{new Date(f.generatedAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
