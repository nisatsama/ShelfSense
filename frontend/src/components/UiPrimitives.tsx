import React from 'react';
import { AlertTriangle, Inbox, X } from 'lucide-react';

export const PageHeader: React.FC<{
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
}> = ({ title, subtitle, actions }) => (
  <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
    <div>
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
      {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
    </div>
    {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
  </div>
);

export const StatCard: React.FC<{
  label: string;
  value: string | number;
  helper?: string;
  icon?: React.ReactNode;
  tone?: 'default' | 'warning' | 'danger' | 'success' | 'info';
}> = ({ label, value, helper, icon, tone = 'default' }) => {
  const accent: Record<string, string> = {
    default: 'bg-slate-100 text-slate-700',
    warning: 'bg-amber-100 text-amber-700',
    danger: 'bg-rose-100 text-rose-700',
    success: 'bg-emerald-100 text-emerald-700',
    info: 'bg-indigo-100 text-indigo-700',
  };
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500">{label}</p>
          <p className="mt-2 text-2xl font-bold text-slate-900">{value}</p>
          {helper && <p className="mt-1 text-xs text-slate-500">{helper}</p>}
        </div>
        {icon && <div className={`rounded-lg p-2.5 ${accent[tone]}`}>{icon}</div>}
      </div>
    </div>
  );
};

export const StatusBadge: React.FC<{ status: string }> = ({ status }) => {
  const styles: Record<string, string> = {
    ACTIVE: 'bg-indigo-50 text-indigo-700 border-indigo-200',
    CONFIRMED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    RECEIVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    APPROVED: 'bg-sky-50 text-sky-700 border-sky-200',
    ORDERED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
    DISPATCHED: 'bg-amber-50 text-amber-700 border-amber-200',
    IN_TRANSIT: 'bg-amber-50 text-amber-700 border-amber-200',
    PARTIALLY_RECEIVED: 'bg-orange-50 text-orange-700 border-orange-200',
    CREATED: 'bg-slate-100 text-slate-700 border-slate-200',
    DRAFT: 'bg-slate-100 text-slate-700 border-slate-200',
    PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
    RELEASED: 'bg-slate-100 text-slate-600 border-slate-200',
    EXPIRED: 'bg-rose-50 text-rose-700 border-rose-200',
    CANCELLED: 'bg-rose-50 text-rose-700 border-rose-200',
    DISMISSED: 'bg-slate-100 text-slate-600 border-slate-200',
    CRITICAL: 'bg-rose-100 text-rose-800 border-rose-300',
    HIGH: 'bg-orange-100 text-orange-800 border-orange-300',
    MEDIUM: 'bg-amber-100 text-amber-800 border-amber-300',
    LOW: 'bg-emerald-100 text-emerald-800 border-emerald-300',
    ADMIN: 'bg-indigo-100 text-indigo-800 border-indigo-200',
    USER: 'bg-slate-100 text-slate-700 border-slate-200',
    WAREHOUSE: 'bg-indigo-50 text-indigo-700 border-indigo-200',
    DISTRIBUTION_CENTER: 'bg-sky-50 text-sky-700 border-sky-200',
    STORE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  };
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium ${
        (status && styles[status]) || 'bg-slate-100 text-slate-700 border-slate-200'
      }`}
    >
      {status ? status.replace(/_/g, ' ') : 'UNKNOWN'}
    </span>
  );
};

export const LoadingSkeleton: React.FC<{ rows?: number }> = ({ rows = 5 }) => (
  <div className="space-y-3 rounded-xl border border-slate-200 bg-white p-6">
    {Array.from({ length: rows }).map((_, i) => (
      <div key={i} className="h-8 animate-pulse rounded bg-slate-100" />
    ))}
  </div>
);

export const EmptyState: React.FC<{ title: string; description: string; action?: React.ReactNode }> = ({
  title,
  description,
  action,
}) => (
  <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-white px-6 py-12 text-center">
    <Inbox className="h-10 w-10 text-slate-400" />
    <h3 className="mt-3 text-sm font-semibold text-slate-900">{title}</h3>
    <p className="mt-1 max-w-sm text-sm text-slate-500">{description}</p>
    {action && <div className="mt-4">{action}</div>}
  </div>
);

export const ErrorBanner: React.FC<{ message: string; onRetry?: () => void }> = ({ message, onRetry }) => (
  <div className="mb-4 flex items-center justify-between rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
    <div className="flex items-center gap-2">
      <AlertTriangle className="h-4 w-4 shrink-0 text-rose-600" />
      <span>{message}</span>
    </div>
    {onRetry && (
      <button
        type="button"
        onClick={onRetry}
        className="rounded-md bg-rose-100 px-3 py-1 text-xs font-semibold text-rose-900 hover:bg-rose-200"
      >
        Retry
      </button>
    )}
  </div>
);

export const Modal: React.FC<{
  open: boolean;
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}> = ({ open, title, onClose, children }) => {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2 className="text-base font-semibold text-slate-900">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="p-6">{children}</div>
      </div>
    </div>
  );
};
