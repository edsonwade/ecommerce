// ── Auth ──
export interface RegisterRequest {
  firstname: string;
  lastname: string;
  email: string;
  password: string;
  tenantId?: string;
  role?: 'USER' | 'SELLER';
}

export interface LoginRequest {
  email: string;
  password: string;
}

export type Role = 'USER' | 'SELLER' | 'ADMIN';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  userId: string;
  email: string;
  role: Role;
  tenantId: string;
}

export interface AccountResponse {
  id: number;
  firstname: string;
  lastname: string;
  email: string;
  role: Role;
  createdAt: string;
}

export interface UpdateAccountRequest {
  firstname: string;
  lastname: string;
  email: string;
  currentPassword?: string;
}

export interface AccountUpdateResponse {
  account: AccountResponse;
  // Present ONLY when the email changed — the caller must swap these into the auth store.
  tokens?: AuthResponse;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

// ── Category ──
export interface CategoryResponse {
  id: number;
  name: string;
  description: string;
}

// ── Product ──
export interface ProductRequest {
  id?: number;
  name: string;
  description: string;
  availableQuantity: number;
  price: number;
  categoryId: number;
}

export interface ProductResponse {
  id: number;
  name: string;
  description: string;
  availableQuantity: number;
  price: number;
  categoryId: number;
  categoryName: string;
  categoryDescription: string;
  createdBy?: string;
  imageUrl?: string;
}

export interface ProductPurchaseRequest {
  productId: number;
  quantity: number;
}

export interface ProductPurchaseResponse {
  productId: number;
  name: string;
  price: number;
  quantity: number;
}

// ── Cart ──
export interface AddCartItemRequest {
  productId: number;
  productName: string;
  productDescription?: string;
  unitPrice: number;
  quantity: number;
  availableQuantity: number;
}

export interface CartItemResponse {
  productId: number;
  productName: string;
  productDescription: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  availableQuantity: number;
  imageUrl?: string;
}

export interface CartResponse {
  cartId: string;
  customerId: string;
  items: CartItemResponse[];
  total: number;
  itemCount: number;
  createdAt: string;
  updatedAt: string;
}

// ── Order ──
export type PaymentMethod = 'PAYPAL' | 'CREDIT_CARD' | 'VISA' | 'MASTER_CARD' | 'BITCOIN';
export type OrderStatus = 'REQUESTED' | 'INVENTORY_RESERVED' | 'CONFIRMED' | 'CANCELLED';

export interface OrderRequest {
  reference?: string;
  amount: number;
  paymentMethod: PaymentMethod;
  customerId: string;
  products: ProductPurchaseRequest[];
  // Shipping destination captured at checkout — persisted on the order so the invoice
  // shows THIS order's address rather than the buyer's profile address.
  shippingStreet?: string;
  shippingHouseNumber?: string;
  shippingZipCode?: string;
  shippingCity?: string;
  shippingCountry?: string;
}

export interface OrderResponse {
  id: number;
  reference: string;
  amount: number;
  paymentMethod: string;
  customerId: string;
  /** Saga status (REQUESTED/CONFIRMED/CANCELLED/…) — added so badges stop guessing from paymentMethod. */
  status: OrderStatus | string;

  // ── Customer block (from order-service CustomerSnapshot; omitted if not yet synced) ──
  customerFirstname?: string;
  customerLastname?: string;
  customerEmail?: string;
  shippingStreet?: string;
  shippingHouseNumber?: string;
  shippingZipCode?: string;
  shippingCity?: string;
  shippingCountry?: string;

  // ── Order header ──
  createdDate?: string;

  // ── Money breakdown (tax-inclusive; IVA derived from total when not persisted) ──
  subtotal?: number;
  discountAmount?: number;
  promotionCode?: string;
  promotionAmount?: number;
  taxRate?: number;
  taxAmount?: number;
}

// ── Seller business profile (invoice "sold by" identity) ──
export interface SellerProfileResponse {
  id: number;
  fullName: string;
  firstname?: string;
  lastname?: string;
  email: string;
  companyName?: string;
  vatNumber?: string;
  street?: string;
  city?: string;
  country?: string;
  postalCode?: string;
}

export interface SellerProfileRequest {
  companyName?: string;
  vatNumber?: string;
  street?: string;
  city?: string;
  country?: string;
  postalCode?: string;
}

export interface OrderCreateResponse {
  correlationId: string;
  status: string;
  message: string;
}

export interface OrderStatusResponse {
  correlationId: string;
  status: OrderStatus;
  timestamp: string;
  message: string;
}

export interface OrderLineResponse {
  id: number;
  orderId: number;
  productId: number;
  quantity: number;
}

// ── Payment ──
export interface PaymentResponse {
  id: number;
  amount: number;
  paymentMethod: string;
  orderId: number;
  orderReference: string;
  createdDate: string;
}

// ── Customer ──
export interface Address {
  street: string;
  houseNumber: string;
  zipCode: string;
  city: string;
  country: string;
}

export interface CustomerRequest {
  customerId?: string;
  firstname: string;
  lastname: string;
  email: string;
  address: Address;
}

export interface CustomerResponse {
  id: string;
  firstname: string;
  lastname: string;
  email: string;
  address: Address;
}

// ── Tenant ──
export type TenantPlan = 'FREE' | 'STARTER' | 'GROWTH' | 'ENTERPRISE';
export type TenantStatus = 'ACTIVE' | 'SUSPENDED' | 'CANCELLED';

export interface CreateTenantRequest {
  name: string;
  slug: string;
  contactEmail: string;
  plan?: string;
}

export interface UpdateTenantRequest {
  name?: string;
  contactEmail?: string;
}

export interface TenantResponse {
  tenantId: string;
  name: string;
  slug: string;
  contactEmail: string;
  plan: TenantPlan;
  status: TenantStatus;
  rateLimit: number;
  storageQuota: number;
  createdAt: string;
  updatedAt: string;
}

export interface SetFeatureFlagRequest {
  enabled: boolean;
  description?: string;
}

export interface FeatureFlagResponse {
  flagName: string;
  enabled: boolean;
  description: string;
}

export interface RecordUsageRequest {
  metricName: string;
  value: number;
}

export interface UsageMetricResponse {
  metricName: string;
  value: number;
  recordedAt: string;
}

// ── Pagination ──
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

// ── Errors ──
export interface ApiError {
  errors: Record<string, string>;
}

export interface AppError {
  status: number;
  message: string;
  fieldErrors?: Record<string, string>;
}
