import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { ProductsPage } from './pages/ProductsPage';
import { LocationsPage } from './pages/LocationsPage';
import { InventoryPage } from './pages/InventoryPage';
import { ReservationsPage } from './pages/ReservationsPage';
import { TransfersPage } from './pages/TransfersPage';
import { ProcurementPage } from './pages/ProcurementPage';
import { ForecastingPage } from './pages/ForecastingPage';
import { ReplenishmentPage } from './pages/ReplenishmentPage';
import { AdminPage } from './pages/AdminPage';

export const AppRoutes: React.FC = () => (
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route
      path="/"
      element={
        <ProtectedRoute>
          <Layout />
        </ProtectedRoute>
      }
    >
      <Route index element={<DashboardPage />} />
      <Route path="products" element={<ProductsPage />} />
      <Route path="locations" element={<LocationsPage />} />
      <Route path="inventory" element={<InventoryPage />} />
      <Route path="reservations" element={<ReservationsPage />} />
      <Route path="transfers" element={<TransfersPage />} />
      <Route path="procurement" element={<ProcurementPage />} />
      <Route path="forecasting" element={<ForecastingPage />} />
      <Route path="replenishment" element={<ReplenishmentPage />} />
      <Route
        path="admin"
        element={
          <ProtectedRoute requireAdmin>
            <AdminPage />
          </ProtectedRoute>
        }
      />
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
);

export const App: React.FC = () => (
  <AuthProvider>
    <ToastProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </ToastProvider>
  </AuthProvider>
);

export default App;
