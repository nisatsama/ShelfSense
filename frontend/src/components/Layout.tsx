import React, { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import {
  LayoutDashboard,
  Package,
  MapPin,
  Boxes,
  BookmarkCheck,
  Truck,
  ShoppingCart,
  TrendingUp,
  RefreshCw,
  ShieldCheck,
  LogOut,
  Menu,
  X,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { StatusBadge } from './UiPrimitives';

interface NavItem {
  to: string;
  label: string;
  icon: React.ReactNode;
  adminOnly?: boolean;
}

const navItems: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: <LayoutDashboard className="h-4 w-4" /> },
  { to: '/products', label: 'Products', icon: <Package className="h-4 w-4" /> },
  { to: '/locations', label: 'Locations', icon: <MapPin className="h-4 w-4" /> },
  { to: '/inventory', label: 'Inventory & Ledger', icon: <Boxes className="h-4 w-4" /> },
  { to: '/reservations', label: 'Reservations', icon: <BookmarkCheck className="h-4 w-4" /> },
  { to: '/transfers', label: 'Transfers', icon: <Truck className="h-4 w-4" /> },
  { to: '/procurement', label: 'Procurement', icon: <ShoppingCart className="h-4 w-4" /> },
  { to: '/forecasting', label: 'Demand Forecasting', icon: <TrendingUp className="h-4 w-4" /> },
  { to: '/replenishment', label: 'Replenishment', icon: <RefreshCw className="h-4 w-4" /> },
  { to: '/admin', label: 'Administration', icon: <ShieldCheck className="h-4 w-4" />, adminOnly: true },
];

export const Layout: React.FC = () => {
  const { user, isAdmin, logout } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);

  const visibleItems = navItems.filter((item) => !item.adminOnly || isAdmin);

  const renderLinks = () => (
    <nav className="flex-1 space-y-1 px-3 py-4">
      {visibleItems.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to === '/'}
          onClick={() => setMobileOpen(false)}
          className={({ isActive }) =>
            `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
              isActive
                ? 'bg-indigo-600 text-white shadow-xs'
                : 'text-slate-300 hover:bg-slate-800 hover:text-white'
            }`
          }
        >
          {item.icon}
          <span>{item.label}</span>
        </NavLink>
      ))}
    </nav>
  );

  return (
    <div className="flex min-h-screen bg-slate-50">
      {/* Desktop Sidebar */}
      <aside className="hidden w-64 shrink-0 flex-col bg-slate-900 text-white lg:flex">
        <div className="flex h-16 items-center gap-2.5 border-b border-slate-800 px-6">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-500 font-bold text-white">
            S
          </div>
          <div>
            <div className="text-sm font-bold tracking-tight">ShelfSense</div>
            <div className="text-[11px] text-slate-400">Inventory Intelligence</div>
          </div>
        </div>
        {renderLinks()}
        <div className="border-t border-slate-800 p-4">
          <div className="mb-2 flex items-center justify-between gap-2">
            <span className="truncate text-xs text-slate-300">{user?.loginIdentifier}</span>
            {user && <StatusBadge status={user.role} />}
          </div>
          <button
            type="button"
            onClick={logout}
            className="flex w-full items-center justify-center gap-2 rounded-lg border border-slate-700 px-3 py-1.5 text-xs font-medium text-slate-300 hover:bg-slate-800 hover:text-white"
          >
            <LogOut className="h-3.5 w-3.5" />
            Sign out
          </button>
        </div>
      </aside>

      {/* Mobile Drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 flex lg:hidden">
          <div className="fixed inset-0 bg-slate-900/60" onClick={() => setMobileOpen(false)} />
          <aside className="relative z-50 flex w-64 flex-col bg-slate-900 text-white">
            <div className="flex h-16 items-center justify-between border-b border-slate-800 px-5">
              <span className="font-bold">ShelfSense</span>
              <button type="button" onClick={() => setMobileOpen(false)} className="text-slate-400">
                <X className="h-5 w-5" />
              </button>
            </div>
            {renderLinks()}
          </aside>
        </div>
      )}

      {/* Main content */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-4 lg:hidden">
          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            className="rounded-lg p-2 text-slate-600 hover:bg-slate-100"
            aria-label="Open navigation"
          >
            <Menu className="h-5 w-5" />
          </button>
          <span className="font-semibold text-slate-900">ShelfSense</span>
          <button type="button" onClick={logout} className="text-xs font-medium text-slate-600">
            Logout
          </button>
        </header>
        <main className="mx-auto w-full max-w-7xl flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
