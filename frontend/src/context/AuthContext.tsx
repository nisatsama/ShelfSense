import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Account, LoginResponse } from '../api/types';

interface AuthContextValue {
  user: Account | null;
  token: string | null;
  loading: boolean;
  isAdmin: boolean;
  login: (loginIdentifier: string, password: string) => Promise<void>;
  register: (loginIdentifier: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('shelfsense_token'));
  const [user, setUser] = useState<Account | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  const logout = useCallback(() => {
    localStorage.removeItem('shelfsense_token');
    setToken(null);
    setUser(null);
  }, []);

  const fetchMe = useCallback(async () => {
    if (!localStorage.getItem('shelfsense_token')) {
      setUser(null);
      setLoading(false);
      return;
    }
    try {
      const { data } = await api.get<Account>('/api/v1/auth/me');
      setUser(data);
    } catch {
      logout();
    } finally {
      setLoading(false);
    }
  }, [logout]);

  useEffect(() => {
    fetchMe();
  }, [fetchMe]);

  useEffect(() => {
    const onUnauth = () => logout();
    window.addEventListener('shelfsense:unauthorized', onUnauth);
    return () => window.removeEventListener('shelfsense:unauthorized', onUnauth);
  }, [logout]);

  const login = async (loginIdentifier: string, password: string) => {
    const { data } = await api.post<LoginResponse>('/api/v1/auth/login', { loginIdentifier, password });
    localStorage.setItem('shelfsense_token', data.accessToken);
    setToken(data.accessToken);
    const me = await api.get<Account>('/api/v1/auth/me');
    setUser(me.data);
  };

  const register = async (loginIdentifier: string, password: string) => {
    await api.post<Account>('/api/v1/auth/register', { loginIdentifier, password });
    await login(loginIdentifier, password);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        loading,
        isAdmin: user?.role === 'ADMIN',
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
