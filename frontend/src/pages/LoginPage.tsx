import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Layers, ArrowRight } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { extractErrorMessage } from '../api/client';

export const LoginPage: React.FC = () => {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [loginIdentifier, setLoginIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!loginIdentifier.trim() || !password) {
      setError('Please enter both your login identifier and password.');
      return;
    }
    if (mode === 'register' && password.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }
    setSubmitting(true);
    try {
      if (mode === 'login') {
        await login(loginIdentifier.trim(), password);
      } else {
        await register(loginIdentifier.trim(), password);
      }
      navigate('/', { replace: true });
    } catch (err) {
      setError(extractErrorMessage(err, 'Authentication failed.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-950 px-4 py-12">
      <div className="w-full max-w-md rounded-2xl border border-slate-800 bg-slate-900 p-8 shadow-2xl">
        <div className="mb-6 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-600 text-white">
            <Layers className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-white">ShelfSense</h1>
            <p className="text-xs text-slate-400">Retail Inventory Intelligence &amp; Forecasting</p>
          </div>
        </div>

        <div className="mb-6 grid grid-cols-2 rounded-lg bg-slate-800 p-1 text-xs font-medium">
          <button
            type="button"
            onClick={() => { setMode('login'); setError(null); }}
            className={`rounded-md py-2 transition ${mode === 'login' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white'}`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => { setMode('register'); setError(null); }}
            className={`rounded-md py-2 transition ${mode === 'register' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white'}`}
          >
            Create Account
          </button>
        </div>

        {error && (
          <div role="alert" className="mb-4 rounded-lg border border-rose-500/40 bg-rose-950/60 px-4 py-3 text-xs text-rose-200">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="loginIdentifier" className="block text-xs font-medium text-slate-300">
              Email or Username
            </label>
            <input
              id="loginIdentifier"
              type="text"
              autoComplete="username"
              value={loginIdentifier}
              onChange={(e) => setLoginIdentifier(e.target.value)}
              placeholder="operator@shelfsense.local"
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-800 px-3.5 py-2.5 text-sm text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none"
            />
          </div>
          <div>
            <label htmlFor="password" className="block text-xs font-medium text-slate-300">
              Password
            </label>
            <input
              id="password"
              type="password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••••••"
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-800 px-3.5 py-2.5 text-sm text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none"
            />
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-indigo-500 disabled:opacity-60"
          >
            {submitting ? 'Authenticating...' : mode === 'login' ? 'Sign In' : 'Register & Sign In'}
            <ArrowRight className="h-4 w-4" />
          </button>
        </form>
      </div>
    </div>
  );
};
