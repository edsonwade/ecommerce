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

/** Seller approval lifecycle — only meaningful for SELLER accounts. */
export type SellerStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'SUSPENDED';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  userId: string;
  email: string;
  role: Role;
  tenantId: string;
  /** Present only for SELLER accounts (backend omits the field for other roles). */
  sellerStatus?: SellerStatus;
}

export interface AccountResponse {
  id: number;
  firstname: string;
  lastname: string;
  email: string;
  role: Role;
  createdAt: string;
  /** Live seller-approval status (backend omits it for non-sellers). The seller UI polls
   *  this so approval/suspension take effect without a re-login. */
  sellerStatus?: SellerStatus;
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

// Fase 4: payload for ADMIN category create/update.
export interface CategoryRequest {
  name: string;
  description?: string;
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

/** Fase 3: product lifecycle status — SUSPENDED products are hidden from the public catalogue. */
export type ProductStatus = 'ACTIVE' | 'SUSPENDED';

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
  /** Fase 3: optional — older cached payloads may not carry it. */
  status?: ProductStatus;
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
export type OrderStatus =
  | 'REQUESTED'
  | 'INVENTORY_RESERVED'
  | 'CONFIRMED'
  | 'CANCELLED'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'REFUNDED';

/** Fase 5: the only statuses a seller/admin may set via PATCH /orders/{id}/status. */
export type FulfillmentStatus = 'SHIPPED' | 'DELIVERED';

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
  // Backend record field is `customerId` (customer-service CustomerResponse.java) — not `id`.
  customerId: string;
  firstname: string;
  lastname: string;
  email: string;
  // Auth-provisioned profiles are created without an address (CustomerProvisioning sends
  // only id/name/email), so this is null until the user saves one.
  address: Address | null;
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
