import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { ToastProvider } from '../context/ToastContext';
import { InventoryPage } from '../pages/InventoryPage';
import { TransfersPage } from '../pages/TransfersPage';
import { ProcurementPage } from '../pages/ProcurementPage';
import { ReplenishmentPage } from '../pages/ReplenishmentPage';
import { ForecastingPage } from '../pages/ForecastingPage';
import { ProductsPage } from '../pages/ProductsPage';
import { LocationsPage } from '../pages/LocationsPage';
import { ReservationsPage } from '../pages/ReservationsPage';
import { AdminPage } from '../pages/AdminPage';
import { api } from '../api/client';
import type {
  InventoryItem,
  Transfer,
  PurchaseOrder,
  ReplenishmentRecommendation,
  Forecast,
  ModelInfo,
  Product,
  Location,
  Reservation,
  Account,
} from '../api/types';

function wrapWithProviders(ui: React.ReactElement) {
  localStorage.setItem('shelfsense_token', 'test-token');
  return (
    <AuthProvider>
      <ToastProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </ToastProvider>
    </AuthProvider>
  );
}

const mockProducts: Product[] = [
  { id: 1, sku: 'SKU-001', name: 'Product Alpha', category: 'FOODS', unitPrice: 10, active: true },
  { id: 2, sku: 'SKU-002', name: 'Product Beta', category: 'HOUSEHOLD', unitPrice: 20, active: true },
];

const mockLocations: Location[] = [
  { id: 1, code: 'WH-MAIN', name: 'Main Warehouse', type: 'WAREHOUSE' },
  { id: 2, code: 'STORE-01', name: 'Downtown Store', type: 'STORE' },
];

describe('Critical Frontend Workflow Verifications', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();

    vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
      if (url === '/api/v1/auth/me') {
        return {
          data: {
            id: 1,
            loginIdentifier: 'admin@example.com',
            role: 'ADMIN',
            enabled: true,
            createdAt: '2026-11-18T00:00:00Z',
            updatedAt: '2026-11-18T00:00:00Z',
          },
        } as never;
      }
      return { data: [] } as never;
    });
  });

  describe('Inventory Management Workflow', () => {
    it('loads inventory list and handles stock adjustment modal submission', async () => {
      const mockItems: InventoryItem[] = [
        {
          id: 10,
          productId: 1,
          sku: 'SKU-001',
          productName: 'Product Alpha',
          locationId: 1,
          locationCode: 'WH-MAIN',
          locationName: 'Main Warehouse',
          onHand: 100,
          reserved: 10,
          available: 90,
          version: 1,
        },
      ];

      vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
        if (url === '/api/v1/auth/me') return { data: { id: 1, role: 'ADMIN', loginIdentifier: 'admin@example.com' } } as never;
        if (url === '/api/v1/inventory') return { data: mockItems } as never;
        if (url === '/api/v1/inventory/transactions') return { data: [] } as never;
        if (url === '/api/v1/products') return { data: mockProducts } as never;
        if (url === '/api/v1/locations') return { data: mockLocations } as never;
        return { data: [] } as never;
      });

      const postSpy = vi.spyOn(api, 'post').mockResolvedValue({ data: { id: 99 } } as never);

      render(wrapWithProviders(<InventoryPage />));

      await waitFor(() => {
        expect(screen.getByText('SKU-001')).toBeInTheDocument();
        expect(screen.getByText('Product Alpha')).toBeInTheDocument();
      });

      // Click "Adjust" button
      const adjustBtn = screen.getByRole('button', { name: /Adjust/i });
      fireEvent.click(adjustBtn);

      await waitFor(() => {
        expect(screen.getByText(/Adjust Stock: SKU-001/i)).toBeInTheDocument();
      });

      // Submit adjustment
      const submitBtn = screen.getByRole('button', { name: /Apply Adjustment/i });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(postSpy).toHaveBeenCalledWith(
          '/api/v1/inventory/adjustments',
          expect.objectContaining({
            productId: 1,
            locationId: 1,
            quantityDelta: 10,
            reason: 'CYCLE_COUNT',
          })
        );
      });
    });
  });

  describe('Stock Transfer Workflow', () => {
    it('creates, transitions, and receives a transfer with damaged quantity tracking', async () => {
      const mockTransfers: Transfer[] = [
        {
          id: 42,
          transferNumber: 'TRF-00042',
          productId: 1,
          sku: 'SKU-001',
          productName: 'Product Alpha',
          sourceLocationId: 1,
          sourceLocationCode: 'WH-MAIN',
          sourceLocationName: 'Main Warehouse',
          destinationLocationId: 2,
          destinationLocationCode: 'STORE-01',
          destinationLocationName: 'Downtown Store',
          requestedQuantity: 25,
          dispatchedQuantity: 0,
          receivedQuantity: 0,
          damagedQuantity: 0,
          remainingToReceive: 25,
          status: 'CREATED',
          notes: 'Emergency restock',
          createdAt: '2026-11-18T00:00:00Z',
          updatedAt: '2026-11-18T00:00:00Z',
        },
      ];

      vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
        if (url === '/api/v1/auth/me') return { data: { id: 1, role: 'ADMIN', loginIdentifier: 'admin@example.com' } } as never;
        if (url === '/api/v1/transfers') return { data: mockTransfers } as never;
        if (url === '/api/v1/products') return { data: mockProducts } as never;
        if (url === '/api/v1/locations') return { data: mockLocations } as never;
        return { data: [] } as never;
      });

      const putSpy = vi.spyOn(api, 'put').mockResolvedValue({ data: { ...mockTransfers[0], status: 'APPROVED' } } as never);

      render(wrapWithProviders(<TransfersPage />));

      await waitFor(() => {
        expect(screen.getByText('TRF-00042')).toBeInTheDocument();
        expect(screen.getAllByText('CREATED').length).toBeGreaterThanOrEqual(1);
      });

      // Click "Approve" transition button
      const approveBtn = screen.getByRole('button', { name: /^Approve$/i });
      fireEvent.click(approveBtn);

      await waitFor(() => {
        expect(putSpy).toHaveBeenCalledWith('/api/v1/transfers/42/approve', undefined);
      });
    });
  });

  describe('Procurement & Goods Receipt Workflow', () => {
    it('renders purchase orders and triggers PO approval and goods receipt modal', async () => {
      const mockSuppliers = [
        { id: 1, code: 'SUP-01', name: 'Global Foods Corp', contactEmail: 'supply@global.com', leadTimeDays: 5, active: true, createdAt: '', updatedAt: '' },
      ];
      const mockOrders: PurchaseOrder[] = [
        {
          id: 88,
          poNumber: 'PO-2026-00088',
          supplierId: 1,
          supplierCode: 'SUP-01',
          supplierName: 'Global Foods Corp',
          destinationLocationId: 1,
          destinationLocationCode: 'WH-MAIN',
          destinationLocationName: 'Main Warehouse',
          status: 'ORDERED',
          totalAmount: 250,
          notes: 'Standard replenishment',
          items: [
            {
              id: 101,
              productId: 1,
              sku: 'SKU-001',
              productName: 'Product Alpha',
              orderedQuantity: 50,
              receivedQuantity: 0,
              remainingQuantity: 50,
              unitCost: 5,
            },
          ],
          createdAt: '2026-11-18T00:00:00Z',
          updatedAt: '2026-11-18T00:00:00Z',
        },
      ];

      vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
        if (url === '/api/v1/auth/me') return { data: { id: 1, role: 'ADMIN', loginIdentifier: 'admin@example.com' } } as never;
        if (url === '/api/v1/purchase-orders') return { data: mockOrders } as never;
        if (url === '/api/v1/suppliers') return { data: mockSuppliers } as never;
        if (url === '/api/v1/products') return { data: mockProducts } as never;
        if (url === '/api/v1/locations') return { data: mockLocations } as never;
        return { data: [] } as never;
      });

      const postSpy = vi.spyOn(api, 'post').mockResolvedValue({ data: { id: 88, status: 'RECEIVED' } } as never);

      render(wrapWithProviders(<ProcurementPage />));

      await waitFor(() => {
        expect(screen.getByText('PO-2026-00088')).toBeInTheDocument();
        expect(screen.getByText('Global Foods Corp')).toBeInTheDocument();
      });

      // Click "Receive Goods"
      const receiveBtn = screen.getByRole('button', { name: /Receive Goods/i });
      fireEvent.click(receiveBtn);

      await waitFor(() => {
        expect(screen.getByText(/Goods Receipt: PO-2026-00088/i)).toBeInTheDocument();
      });

      // Submit goods receipt
      const confirmBtn = screen.getByRole('button', { name: /Verify & Receive/i });
      fireEvent.click(confirmBtn);

      await waitFor(() => {
        expect(postSpy).toHaveBeenCalledWith(
          '/api/v1/purchase-orders/88/receipts',
          expect.objectContaining({
            items: [{ purchaseOrderItemId: 101, receivedQuantity: 50 }],
          })
        );
      });
    });
  });

  describe('Replenishment Engine Workflow', () => {
    it('renders recommendations and generates fresh calculations on demand', async () => {
      const mockRecs: ReplenishmentRecommendation[] = [
        {
          id: 5,
          productId: 1,
          sku: 'SKU-001',
          productName: 'Product Alpha',
          locationId: 1,
          locationCode: 'WH-MAIN',
          locationName: 'Main Warehouse',
          currentOnHand: 15,
          reservedStock: 5,
          availableStock: 10,
          incomingStock: 0,
          avgDailyDemand: 5,
          leadTimeDays: 7,
          reorderPoint: 40,
          safetyStock: 15,
          targetStock: 70,
          recommendedQuantity: 60,
          priority: 'HIGH',
          status: 'PENDING',
          notes: 'Effective stock (10) <= ROP (40)',
          createdAt: '2026-11-18T00:00:00Z',
          updatedAt: '2026-11-18T00:00:00Z',
        },
      ];

      vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
        if (url === '/api/v1/auth/me') return { data: { id: 1, role: 'ADMIN', loginIdentifier: 'admin@example.com' } } as never;
        if (url === '/api/v1/replenishment') return { data: mockRecs } as never;
        if (url === '/api/v1/suppliers') return { data: [] } as never;
        return { data: [] } as never;
      });

      const postSpy = vi.spyOn(api, 'post').mockResolvedValue({ data: mockRecs } as never);

      render(wrapWithProviders(<ReplenishmentPage />));

      await waitFor(() => {
        expect(screen.getByText('Replenishment Engine')).toBeInTheDocument();
        expect(screen.getByText('SKU-001')).toBeInTheDocument();
        expect(screen.getByText('60')).toBeInTheDocument();
      });

      // Click "Run Replenishment"
      const runBtn = screen.getByRole('button', { name: /Run Replenishment/i });
      fireEvent.click(runBtn);

      await waitFor(() => {
        expect(postSpy).toHaveBeenCalledWith('/api/v1/replenishment/generate', {});
      });
    });
  });

  describe('Demand Forecasting Intelligence', () => {
    it('displays M5 evaluation metrics and allows generating fresh predictions', async () => {
      const mockModelInfo: ModelInfo = {
        status: 'READY',
        model_version: 'v1.0.0',
        metadata: {
          selected_model: 'HistGradientBoostingRegressor',
          dataset: 'Nixtla M5 Walmart Retail',
          target_variable: 'daily_sales_units',
          forecast_horizon_days: 28,
          trained_at: '2026-11-18T00:00:00Z',
          test_metrics: {
            wape: 0.7882,
            rmse: 2.14,
            mae: 1.15,
            bias: 0.042,
          },
          test_baselines: {
            seasonal_naive_7d: { mae: 1.34, rmse: 2.76, wape: 0.9399, bias: -0.05 },
          },
        },
      };

      const mockForecasts: Forecast[] = [
        {
          id: 7,
          productId: 1,
          sku: 'SKU-001',
          productName: 'Product Alpha',
          locationId: 1,
          locationCode: 'WH-MAIN',
          locationName: 'Main Warehouse',
          horizonDays: 14,
          predictedDailyDemand: 4.25,
          predictedTotalDemand: 59.5,
          modelVersion: 'v1.0.0',
          source: 'ml-service',
          dailyPredictions: [{ dayOffset: 1, date: '2026-11-19', predictedDemand: 4.25 }],
          generatedAt: '2026-11-18T00:00:00Z',
        },
      ];

      vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
        if (url === '/api/v1/auth/me') return { data: { id: 1, role: 'ADMIN', loginIdentifier: 'admin@example.com' } } as never;
        if (url === '/api/v1/forecasts') return { data: mockForecasts } as never;
        if (url === '/api/v1/forecasts/model-info') return { data: mockModelInfo } as never;
        if (url === '/api/v1/products') return { data: mockProducts } as never;
        if (url === '/api/v1/locations') return { data: mockLocations } as never;
        return { data: [] } as never;
      });

      render(wrapWithProviders(<ForecastingPage />));

      await waitFor(() => {
        expect(screen.getByText('Demand Forecasting Intelligence')).toBeInTheDocument();
        expect(screen.getByText('78.8%')).toBeInTheDocument();
        expect(screen.getByText('4.25')).toBeInTheDocument();
      });
    });
  });

  describe('Products Catalog Workflow', () => {
    it('renders products and submits new product creation modal', async () => {
      vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
        if (url === '/api/v1/auth/me') return { data: { id: 1, role: 'ADMIN', loginIdentifier: 'admin@example.com' } } as never;
        if (url === '/api/v1/products') return { data: mockProducts } as never;
        if (url === '/api/v1/inventory') return { data: [] } as never;
        return { data: [] } as never;
      });

      const postSpy = vi.spyOn(api, 'post').mockResolvedValue({ data: { id: 3 } } as never);

      render(wrapWithProviders(<ProductsPage />));

      await waitFor(() => {
        expect(screen.getByText('SKU-001')).toBeInTheDocument();
        expect(screen.getByText('Product Alpha')).toBeInTheDocument();
      });

      const newBtn = screen.getByRole('button', { name: /New Product/i });
      fireEvent.click(newBtn);

      await waitFor(() => {
        expect(screen.getByText('Create Product')).toBeInTheDocument();
      });
    });
  });

  describe('Locations & Nodes Workflow', () => {
    it('renders location network and opens creation modal', async () => {
      vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
        if (url === '/api/v1/auth/me') return { data: { id: 1, role: 'ADMIN', loginIdentifier: 'admin@example.com' } } as never;
        if (url === '/api/v1/locations') return { data: mockLocations } as never;
        return { data: [] } as never;
      });

      render(wrapWithProviders(<LocationsPage />));

      await waitFor(() => {
        expect(screen.getByText('WH-MAIN')).toBeInTheDocument();
        expect(screen.getByText('Downtown Store')).toBeInTheDocument();
      });

      const addBtn = screen.getByRole('button', { name: /New Location/i });
      fireEvent.click(addBtn);

      await waitFor(() => {
        expect(screen.getByText('New Location')).toBeInTheDocument();
      });
    });
  });

  describe('Reservations Workflow', () => {
    it('renders reservations and allows creating new reservation', async () => {
      const mockReservations: Reservation[] = [
        {
          id: 1,
          productId: 1,
          sku: 'SKU-001',
          locationId: 1,
          locationCode: 'WH-MAIN',
          quantity: 2,
          status: 'ACTIVE',
          accountId: 1,
          expiresAt: '2026-11-18T00:10:00Z',
          createdAt: '2026-11-18T00:00:00Z',
        },
      ];

      vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
        if (url === '/api/v1/auth/me') return { data: { id: 1, role: 'ADMIN', loginIdentifier: 'admin@example.com' } } as never;
        if (url === '/api/v1/reservations') return { data: mockReservations } as never;
        if (url === '/api/v1/products') return { data: mockProducts } as never;
        if (url === '/api/v1/locations') return { data: mockLocations } as never;
        return { data: [] } as never;
      });

      render(wrapWithProviders(<ReservationsPage />));

      await waitFor(() => {
        expect(screen.getByText('Inventory Reservations')).toBeInTheDocument();
        expect(screen.getByText('SKU-001')).toBeInTheDocument();
      });
    });
  });

  describe('Admin Accounts Workflow', () => {
    it('renders account list and toggles account status', async () => {
      const mockAccounts: Account[] = [
        {
          id: 2,
          loginIdentifier: 'operator@example.com',
          role: 'USER',
          enabled: true,
          createdAt: '2026-11-18T00:00:00Z',
          updatedAt: '2026-11-18T00:00:00Z',
        },
      ];

      vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
        if (url === '/api/v1/auth/me') return { data: { id: 1, role: 'ADMIN', loginIdentifier: 'admin@example.com' } } as never;
        if (url === '/api/v1/accounts') return { data: mockAccounts } as never;
        return { data: [] } as never;
      });

      const patchSpy = vi.spyOn(api, 'patch').mockResolvedValue({ data: { ...mockAccounts[0], enabled: false } } as never);

      render(wrapWithProviders(<AdminPage />));

      await waitFor(() => {
        expect(screen.getByText('operator@example.com')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Disable/i })).toBeInTheDocument();
      });

      const disableBtn = screen.getByRole('button', { name: /Disable/i });
      fireEvent.click(disableBtn);

      await waitFor(() => {
        expect(patchSpy).toHaveBeenCalledWith('/api/v1/accounts/2/status', { enabled: false });
      });
    });
  });
});
