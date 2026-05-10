import { http, HttpResponse } from 'msw';
import type {
  AuthResponse,
  CartResponse,
  CustomerResponse,
  FeatureFlagResponse,
  OrderCreateResponse,
  OrderResponse,
  OrderStatusResponse,
  PageResponse,
  PaymentResponse,
  ProductResponse,
  TenantResponse,
} from '@api/types';

// ── Fixtures ────────────────────────────────────────────────────────────────

const AUTH_RESPONSE: AuthResponse = {
  accessToken: 'mock-access-token',
  refreshToken: 'mock-refresh-token',
  tokenType: 'Bearer',
  userId: 'user-001',
  email: 'test@example.com',
  role: 'USER',
  tenantId: 'tenant-001',
};

const PRODUCT: ProductResponse = {
  id: 1,
  name: 'Obsidian Pen',
  description: 'A premium writing instrument with matte black finish.',
  availableQuantity: 50,
  price: 29.99,
  categoryId: 1,
  categoryName: 'Stationery',
  categoryDescription: 'Writing essentials',
};

const PRODUCTS_PAGE: PageResponse<ProductResponse> = {
  content: [PRODUCT, { ...PRODUCT, id: 2, name: 'Ember Notebook', price: 49.99 }],
  totalElements: 2,
  totalPages: 1,
  size: 20,
  number: 0,
  first: true,
  last: true,
};

const CART: CartResponse = {
  cartId: 'cart-001',
  customerId: 'user-001',
  items: [
    {
      productId: 1,
      productName: 'Obsidian Pen',
      productDescription: 'A premium writing instrument',
      unitPrice: 29.99,
      quantity: 2,
      lineTotal: 59.98,
      availableQuantity: 10,
    },
  ],
  total: 59.98,
  itemCount: 2,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const ORDER_CREATE: OrderCreateResponse = {
  correlationId: 'corr-001',
  status: 'REQUESTED',
  message: 'Order submitted',
};

const ORDER_STATUS: OrderStatusResponse = {
  correlationId: 'corr-001',
  status: 'CONFIRMED',
  timestamp: '2024-01-01T00:00:00Z',
  message: 'Order confirmed',
};

const ORDER: OrderResponse = {
  id: 1,
  reference: 'ORD-001',
  amount: 59.98,
  paymentMethod: 'CREDIT_CARD',
  customerId: 'user-001',
};

const CUSTOMER: CustomerResponse = {
  id: 'user-001',
  firstname: 'Jane',
  lastname: 'Doe',
  email: 'test@example.com',
  address: { street: 'Market St', houseNumber: '1', zipCode: '94103', city: 'San Francisco', country: 'US' },
};

const PAYMENT: PaymentResponse = {
  id: 1,
  amount: 59.98,
  paymentMethod: 'CREDIT_CARD',
  orderId: 1,
  orderReference: 'ORD-001',
  createdDate: '2024-01-01T00:00:00Z',
};

const TENANT: TenantResponse = {
  tenantId: 'tenant-001',
  name: 'Demo Store',
  slug: 'demo-store',
  contactEmail: 'admin@demo.com',
  plan: 'STARTER',
  status: 'ACTIVE',
  rateLimit: 100,
  storageQuota: 1024,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const FLAGS: FeatureFlagResponse[] = [
  { flagName: 'ADVANCED_ANALYTICS', enabled: true, description: 'Enable advanced analytics dashboard' },
  { flagName: 'MULTI_CURRENCY', enabled: false, description: 'Support multiple currencies at checkout' },
];

// ── Handlers ─────────────────────────────────────────────────────────────────

export const handlers = [
  // Auth
  http.post('/api/v1/auth/login', () => HttpResponse.json(AUTH_RESPONSE)),
  http.post('/api/v1/auth/register', () => HttpResponse.json(AUTH_RESPONSE, { status: 201 })),
  http.post('/api/v1/auth/refresh', () => HttpResponse.json(AUTH_RESPONSE)),
  http.post('/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),

  // Products
  http.get('/api/v1/products', () => HttpResponse.json(PRODUCTS_PAGE)),
  http.get('/api/v1/products/:id', () => HttpResponse.json(PRODUCT)),
  http.post('/api/v1/products/create', () => HttpResponse.json(PRODUCT, { status: 201 })),
  http.put('/api/v1/products/update/:id', () => HttpResponse.json(PRODUCT)),
  http.delete('/api/v1/products/delete/:id', () => new HttpResponse(null, { status: 204 })),

  // Cart
  http.get('/api/v1/carts/:customerId', () => HttpResponse.json(CART)),
  http.post('/api/v1/carts/:customerId/items', () => HttpResponse.json(CART)),
  http.put('/api/v1/carts/:customerId/items/:productId', () => HttpResponse.json(CART)),
  http.delete('/api/v1/carts/:customerId/items/:productId', () => HttpResponse.json(CART)),
  http.delete('/api/v1/carts/:customerId', () => new HttpResponse(null, { status: 204 })),
  http.get('/api/v1/carts/:customerId/checkout', () => HttpResponse.json(CART)),

  // Orders
  http.post('/api/v1/orders', () => HttpResponse.json(ORDER_CREATE, { status: 202 })),
  http.get('/api/v1/orders', () => HttpResponse.json([ORDER])),
  http.get('/api/v1/orders/:id', () => HttpResponse.json(ORDER)),
  http.get('/api/v1/orders/status/:correlationId', () => HttpResponse.json(ORDER_STATUS)),
  http.get('/api/v1/order-lines/:orderId', () => HttpResponse.json([])),

  // Customers
  http.get('/api/v1/customers', () => HttpResponse.json([CUSTOMER])),
  http.get('/api/v1/customers/:id', () => HttpResponse.json(CUSTOMER)),
  http.post('/api/v1/customers', () => HttpResponse.json(CUSTOMER, { status: 201 })),
  http.put('/api/v1/customers/:id', () => HttpResponse.json(CUSTOMER)),

  // Payments
  http.get('/api/v1/payments', () => HttpResponse.json([PAYMENT])),
  http.get('/api/v1/payments/:id', () => HttpResponse.json(PAYMENT)),

  // Tenants
  http.get('/api/v1/tenants', () => HttpResponse.json([TENANT])),
  http.get('/api/v1/tenants/:id', () => HttpResponse.json(TENANT)),
  http.post('/api/v1/tenants', () => HttpResponse.json(TENANT, { status: 201 })),
  http.put('/api/v1/tenants/:id', () => HttpResponse.json(TENANT)),
  http.patch('/api/v1/tenants/:id/plan', () => HttpResponse.json(TENANT)),
  http.patch('/api/v1/tenants/:id/suspend', () => HttpResponse.json({ ...TENANT, status: 'SUSPENDED' })),
  http.patch('/api/v1/tenants/:id/reactivate', () => HttpResponse.json(TENANT)),
  http.get('/api/v1/tenants/:id/flags', () => HttpResponse.json(FLAGS)),
  http.put('/api/v1/tenants/:id/flags/:flagName', () => HttpResponse.json(FLAGS[0])),
  http.get('/api/v1/tenants/:id/usage/range', () => HttpResponse.json([])),
  http.get('/api/v1/tenants/:id/usage/sum', () => HttpResponse.json([])),
];
