import React, { useEffect, useState } from 'react';
import { Plus } from 'lucide-react';
import { api, extractErrorMessage } from '../api/client';
import type { Account, AccountRole } from '../api/types';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { ErrorBanner, LoadingSkeleton, Modal, PageHeader, StatusBadge } from '../components/UiPrimitives';

export const AdminPage: React.FC = () => {
  const { user } = useAuth();
  const { showSuccess, showError } = useToast();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [loginIdentifier, setLoginIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<AccountRole>('USER');

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.get<Account[]>('/api/v1/accounts');
      setAccounts(data);
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to load accounts.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post('/api/v1/accounts', { loginIdentifier: loginIdentifier.trim(), password, role });
      showSuccess('Account created.');
      setCreateOpen(false);
      setLoginIdentifier('');
      setPassword('');
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Failed to create account.'));
    }
  };

  const toggleEnabled = async (acc: Account) => {
    try {
      await api.patch(`/api/v1/accounts/${acc.id}/status`, { enabled: !acc.enabled });
      showSuccess(`Account ${!acc.enabled ? 'enabled' : 'disabled'}.`);
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Failed to update status.'));
    }
  };

  const toggleRole = async (acc: Account) => {
    const nextRole: AccountRole = acc.role === 'ADMIN' ? 'USER' : 'ADMIN';
    try {
      await api.patch(`/api/v1/accounts/${acc.id}/role`, { role: nextRole });
      showSuccess(`Role updated to ${nextRole}.`);
      load();
    } catch (err) {
      showError(extractErrorMessage(err, 'Failed to update role.'));
    }
  };

  return (
    <div>
      <PageHeader
        title="Administration & Access Control"
        subtitle="Manage user accounts, role assignments, and account activation status."
        actions={
          <button
            type="button"
            onClick={() => setCreateOpen(true)}
            className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
          >
            <Plus className="h-4 w-4" /> Create Account
          </button>
        }
      />

      {error && <ErrorBanner message={error} onRetry={load} />}
      {loading ? (
        <LoadingSkeleton />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">ID</th>
                <th className="px-4 py-3">Login Identifier</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {accounts.map((acc) => {
                const isSelf = acc.id === user?.id;
                return (
                  <tr key={acc.id}>
                    <td className="px-4 py-3 font-mono text-xs">#{acc.id}</td>
                    <td className="px-4 py-3 font-medium text-slate-900">
                      {acc.loginIdentifier} {isSelf && <span className="text-xs text-slate-400">(you)</span>}
                    </td>
                    <td className="px-4 py-3"><StatusBadge status={acc.role} /></td>
                    <td className="px-4 py-3"><StatusBadge status={acc.enabled ? 'ACTIVE' : 'CANCELLED'} /></td>
                    <td className="px-4 py-3 text-right">
                      {!isSelf && (
                        <div className="inline-flex gap-1.5">
                          <button type="button" onClick={() => toggleRole(acc)} className="rounded border border-slate-300 px-2.5 py-1 text-xs">
                            Make {acc.role === 'ADMIN' ? 'USER' : 'ADMIN'}
                          </button>
                          <button
                            type="button"
                            onClick={() => toggleEnabled(acc)}
                            className={`rounded px-2.5 py-1 text-xs font-semibold text-white ${acc.enabled ? 'bg-rose-600' : 'bg-emerald-600'}`}
                          >
                            {acc.enabled ? 'Disable' : 'Enable'}
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={createOpen} title="Create User Account" onClose={() => setCreateOpen(false)}>
        <form onSubmit={handleCreate} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-700">Login Identifier (Email / Username)</label>
            <input required value={loginIdentifier} onChange={(e) => setLoginIdentifier(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Initial Password (min 8 chars)</label>
            <input required minLength={8} type="password" value={password} onChange={(e) => setPassword(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-700">Role</label>
            <select value={role} onChange={(e) => setRole(e.target.value as AccountRole)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm">
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setCreateOpen(false)} className="rounded-lg border px-4 py-2 text-sm">Cancel</button>
            <button type="submit" className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white">Create</button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
