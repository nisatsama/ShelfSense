export type AccountRole = 'ADMIN' | 'USER';

export interface Account {
  id: number;
  loginIdentifier: string;
  role: AccountRole;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

export interface Product {
  id: number;
  sku: string;
  name: string;
  category: string;
  unitPrice: number;
  active: boolean;
}

export type LocationType = 'WAREHOUSE' | 'DISTRIBUTION_CENTER' | 'STORE';

export interface Location {
  id: number;
  code: string;
  name: string;
  type: LocationType;
  address?: string | null;
}

export interface InventoryItem {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  locationId: number;
  locationCode: string;
  locationName: string;
  onHand: number;
  reserved: number;
  available: number;
  version: number;
}

export type AdjustmentReason =
  | 'CYCLE_COUNT'
  | 'DAMAGE'
  | 'SHRINKAGE'
  | 'EXPIRY'
  | 'RESTOCK_CORRECTION'
  | 'RETURN_RESTOCK'
  | 'OTHER';

export interface InventoryTransaction {
  id: number;
  inventoryId: number;
  productId: number;
  sku: string;
  locationId: number;
  locationCode: string;
  type: string;
  quantity: number;
  onHandDelta: number;
  reservedDelta: number;
  referenceId?: number | null;
  reservationId?: number | null;
  createdAt: string;
}

export type ReservationStatus = 'ACTIVE' | 'CONFIRMED' | 'RELEASED' | 'EXPIRED';

export interface Reservation {
  id: number;
  productId: number;
  sku: string;
  locationId: number;
  locationCode: string;
  quantity: number;
  status: ReservationStatus;
  createdAt: string;
  expiresAt?: string | null;
  accountId?: number | null;
}

export type TransferStatus =
  | 'CREATED'
  | 'APPROVED'
  | 'DISPATCHED'
  | 'IN_TRANSIT'
  | 'PARTIALLY_RECEIVED'
  | 'RECEIVED'
  | 'CANCELLED';

export interface Transfer {
  id: number;
  transferNumber: string;
  productId: number;
  sku: string;
  productName: string;
  sourceLocationId: number;
  sourceLocationCode: string;
  sourceLocationName: string;
  destinationLocationId: number;
  destinationLocationCode: string;
  destinationLocationName: string;
  requestedQuantity: number;
  dispatchedQuantity: number;
  receivedQuantity: number;
  damagedQuantity: number;
  remainingToReceive: number;
  status: TransferStatus;
  notes?: string | null;
  dispatchedAt?: string | null;
  receivedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Supplier {
  id: number;
  code: string;
  name: string;
  contactEmail?: string | null;
  phone?: string | null;
  address?: string | null;
  leadTimeDays: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export type PurchaseOrderStatus =
  | 'DRAFT'
  | 'APPROVED'
  | 'ORDERED'
  | 'PARTIALLY_RECEIVED'
  | 'RECEIVED'
  | 'CANCELLED';

export interface PurchaseOrderItem {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  orderedQuantity: number;
  receivedQuantity: number;
  remainingQuantity: number;
  unitCost: number;
}

export interface PurchaseOrder {
  id: number;
  poNumber: string;
  supplierId: number;
  supplierCode: string;
  supplierName: string;
  destinationLocationId: number;
  destinationLocationCode: string;
  destinationLocationName: string;
  status: PurchaseOrderStatus;
  expectedDeliveryDate?: string | null;
  totalAmount: number;
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
  items: PurchaseOrderItem[];
}

export interface GoodsReceipt {
  id: number;
  receiptNumber: string;
  purchaseOrderId: number;
  poNumber: string;
  notes?: string | null;
  receivedAt: string;
  items: Array<{
    id: number;
    purchaseOrderItemId: number;
    productId: number;
    sku: string;
    receivedQuantity: number;
  }>;
}

export interface Forecast {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  locationId: number;
  locationCode: string;
  locationName: string;
  horizonDays: number;
  predictedDailyDemand: number;
  predictedTotalDemand: number;
  modelVersion: string;
  source: string;
  dailyPredictions: Array<{ dayOffset: number; date: string; predictedDemand: number }>;
  generatedAt: string;
}

export interface ModelInfo {
  status: string;
  model_version?: string;
  error?: string;
  metadata?: {
    selected_model: string;
    dataset: string;
    target_variable: string;
    forecast_horizon_days: number;
    trained_at: string;
    test_metrics: { mae: number; rmse: number; wape: number; bias: number };
    test_baselines: Record<string, { mae: number; rmse: number; wape: number; bias: number }>;
    limitations?: string[];
  };
  evaluation?: {
    overall_test_metrics: { mae: number; rmse: number; wape: number; bias: number };
    by_category?: Record<string, { mae: number; rmse: number; wape: number; bias: number }>;
    by_store?: Record<string, { mae: number; rmse: number; wape: number; bias: number }>;
    daily_actual_vs_predicted?: Array<{ date: string; actual: number; predicted: number }>;
  };
}

export type ReplenishmentPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type ReplenishmentStatus = 'PENDING' | 'APPROVED' | 'ORDERED' | 'DISMISSED';

export interface ReplenishmentRecommendation {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  locationId: number;
  locationCode: string;
  locationName: string;
  currentOnHand: number;
  reservedStock: number;
  availableStock: number;
  incomingStock: number;
  avgDailyDemand: number;
  leadTimeDays: number;
  safetyStock: number;
  reorderPoint: number;
  targetStock: number;
  recommendedQuantity: number;
  priority: ReplenishmentPriority;
  status: ReplenishmentStatus;
  forecastId?: number | null;
  purchaseOrderId?: number | null;
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DashboardSummary {
  totalProducts: number;
  totalLocations: number;
  totalInventoryUnits: number;
  totalReservedUnits: number;
  totalAvailableUnits: number;
  lowStockItems: number;
  criticalStockItems: number;
  activeReservations: number;
  pendingTransfers: number;
  pendingPurchaseOrders: number;
  pendingReplenishments: number;
  totalForecasts: number;
}
