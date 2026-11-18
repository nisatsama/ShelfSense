import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { ToastProvider } from '../context/ToastContext';
import { AppRoutes } from '../App';
import { api } from '../api/client';

function mockAuthenticatedUser(role: 'USER' | 'ADMIN') {
  localStorage.setItem('shelfsense_token', 'test-jwt');
  vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
    if (url === '/api/v1/auth/me') {
      return {
        data: {
          id: role === 'ADMIN' ? 1 : 2,
          loginIdentifier: role === 'ADMIN' ? 'admin@example.com' : 'user@example.com',
          role,
          enabled: true,
          createdAt: '',
          updatedAt: '',
        },
      } as never;
    }
    if (url === '/api/v1/dashboard/summary') {
      return {
        data: {
          totalProducts: 12,
          totalLocations: 4,
          totalInventoryUnits: 850,
          totalReservedUnits: 25,
          totalAvailableUnits: 825,
          lowStockItems: 2,
          criticalStockItems: 1,
          activeReservations: 3,
          pendingTransfers: 1,
          pendingPurchaseOrders: 2,
          pendingReplenishments: 4,
          totalForecasts: 6,
        },
      } as never;
    }
    return { data: [] } as never;
  });
}

describe('ShelfSense Frontend Routing, Auth & Role Guards', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('redirects unauthenticated users to login page and validates empty submission', async () => {
    render(
      <AuthProvider>
        <ToastProvider>
          <MemoryRouter initialEntries={['/']}>
            <AppRoutes />
          </MemoryRouter>
        </ToastProvider>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('ShelfSense')).toBeInTheDocument();
    });

    const buttons = screen.getAllByRole('button', { name: /Sign In/i });
    fireEvent.click(buttons[buttons.length - 1]);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/Please enter both/i);
    });
  });

  it('hides Administration link for standard USER role and renders dashboard KPIs', async () => {
    mockAuthenticatedUser('USER');

    render(
      <AuthProvider>
        <ToastProvider>
          <MemoryRouter initialEntries={['/']}>
            <AppRoutes />
          </MemoryRouter>
        </ToastProvider>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Operations Overview')).toBeInTheDocument();
      expect(screen.getByText('850')).toBeInTheDocument();
    });
    expect(screen.queryByText('Administration')).not.toBeInTheDocument();
  });

  it('displays Administration link for ADMIN role', async () => {
    mockAuthenticatedUser('ADMIN');

    render(
      <AuthProvider>
        <ToastProvider>
          <MemoryRouter initialEntries={['/']}>
            <AppRoutes />
          </MemoryRouter>
        </ToastProvider>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Operations Overview')).toBeInTheDocument();
      expect(screen.getByText('Administration')).toBeInTheDocument();
    });
  });
});
