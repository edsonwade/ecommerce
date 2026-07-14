/* eslint-disable react-refresh/only-export-components */
import { lazy, Suspense } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import { Box, CircularProgress } from '@mui/material';

// Layouts
import PublicLayout from './layouts/PublicLayout';
import CustomerLayout from './layouts/CustomerLayout';
import SellerLayout from './layouts/SellerLayout';
import AdminLayout from './layouts/AdminLayout';
import ProtectedRoute from './ProtectedRoute';
import RoleRoute from './RoleRoute';
import RouteErrorFallback from '@components/feedback/RouteErrorFallback';

// Page loading fallback
const PageLoader = () => (
  <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
    <CircularProgress size={36} />
  </Box>
);

function lazy_page(factory: () => Promise<{ default: React.ComponentType }>) {
  const Component = lazy(factory);
  return (
    <Suspense fallback={<PageLoader />}>
      <Component />
    </Suspense>
  );
}

// ── Public pages ──
const HomePage = () => lazy_page(() => import('@pages/public/HomePage'));
const CatalogPage = () => lazy_page(() => import('@pages/public/CatalogPage'));
const ProductPage = () => lazy_page(() => import('@pages/public/ProductPage'));
const LoginPage = () => lazy_page(() => import('@pages/public/LoginPage'));
const RegisterPage = () => lazy_page(() => import('@pages/public/RegisterPage'));
const ForgotPasswordPage = () => lazy_page(() => import('@pages/public/ForgotPasswordPage'));
const ResetPasswordPage = () => lazy_page(() => import('@pages/public/ResetPasswordPage'));
const NotFoundPage = () => lazy_page(() => import('@pages/public/NotFoundPage'));

// ── Customer pages ──
const DashboardPage = () => lazy_page(() => import('@pages/customer/DashboardPage'));
const OrdersPage = () => lazy_page(() => import('@pages/customer/OrdersPage'));
const OrderDetailPage = () => lazy_page(() => import('@pages/customer/OrderDetailPage'));
const ProfilePage = () => lazy_page(() => import('@pages/customer/ProfilePage'));
const AccountSettingsPage = () => lazy_page(() => import('@pages/customer/AccountSettingsPage'));
const CheckoutPage = () => lazy_page(() => import('@pages/customer/CheckoutPage'));

// ── Seller pages ──
const SellerDashboard = () => lazy_page(() => import('@pages/seller/SellerDashboard'));
const ProductManagement = () => lazy_page(() => import('@pages/seller/ProductManagement'));
const ProductForm = () => lazy_page(() => import('@pages/seller/ProductForm'));
const OrderManagement = () => lazy_page(() => import('@pages/seller/OrderManagement'));
const InventoryPage = () => lazy_page(() => import('@pages/seller/InventoryPage'));
const SellerSettings = () => lazy_page(() => import('@pages/seller/SellerSettings'));

// ── Admin pages ──
const AdminDashboard = () => lazy_page(() => import('@pages/admin/AdminDashboard'));
const TenantsPage = () => lazy_page(() => import('@pages/admin/TenantsPage'));
const TenantDetailPage = () => lazy_page(() => import('@pages/admin/TenantDetailPage'));
const UsersPage = () => lazy_page(() => import('@pages/admin/UsersPage'));
const AdminProductsPage = () => lazy_page(() => import('@pages/admin/AdminProductsPage'));
const AdminCategoriesPage = () => lazy_page(() => import('@pages/admin/AdminCategoriesPage'));
const AdminOrdersPage = () => lazy_page(() => import('@pages/admin/AdminOrdersPage'));
const PaymentsPage = () => lazy_page(() => import('@pages/admin/PaymentsPage'));
const AnalyticsPage = () => lazy_page(() => import('@pages/admin/AnalyticsPage'));

export const router = createBrowserRouter([
  // ── Public ──
  {
    element: <PublicLayout />,
    errorElement: <RouteErrorFallback />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/catalog', element: <CatalogPage /> },
      { path: '/catalog/:id', element: <ProductPage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
      { path: '/reset-password', element: <ResetPasswordPage /> },
    ],
  },

  // ── Customer (authenticated) ──
  {
    element: <ProtectedRoute />,
    errorElement: <RouteErrorFallback />,
    children: [
      {
        element: <CustomerLayout />,
        children: [
          { path: '/account', element: <DashboardPage /> },
          { path: '/account/orders', element: <OrdersPage /> },
          { path: '/account/orders/:id', element: <OrderDetailPage /> },
          { path: '/account/profile', element: <ProfilePage /> },
          { path: '/account/settings', element: <AccountSettingsPage /> },
          { path: '/checkout', element: <CheckoutPage /> },
        ],
      },
    ],
  },

  // ── Seller ──
  {
    element: <RoleRoute allowedRoles={['SELLER', 'ADMIN']} />,
    errorElement: <RouteErrorFallback />,
    children: [
      {
        element: <SellerLayout />,
        children: [
          { path: '/seller', element: <SellerDashboard /> },
          { path: '/seller/products', element: <ProductManagement /> },
          { path: '/seller/products/new', element: <ProductForm /> },
          { path: '/seller/products/:id/edit', element: <ProductForm /> },
          { path: '/seller/orders', element: <OrderManagement /> },
          { path: '/seller/orders/:id', element: <OrderDetailPage /> },
          { path: '/seller/inventory', element: <InventoryPage /> },
          { path: '/seller/settings', element: <SellerSettings /> },
          { path: '/seller/account', element: <AccountSettingsPage /> },
        ],
      },
    ],
  },

  // ── Admin ──
  {
    element: <RoleRoute allowedRoles={['ADMIN']} />,
    errorElement: <RouteErrorFallback />,
    children: [
      {
        element: <AdminLayout />,
        children: [
          { path: '/admin', element: <AdminDashboard /> },
          { path: '/admin/tenants', element: <TenantsPage /> },
          { path: '/admin/tenants/:id', element: <TenantDetailPage /> },
          { path: '/admin/users', element: <UsersPage /> },
          { path: '/admin/products', element: <AdminProductsPage /> },
          { path: '/admin/categories', element: <AdminCategoriesPage /> },
          { path: '/admin/orders', element: <AdminOrdersPage /> },
          { path: '/admin/payments', element: <PaymentsPage /> },
          { path: '/admin/analytics', element: <AnalyticsPage /> },
          { path: '/admin/account', element: <AccountSettingsPage /> },
        ],
      },
    ],
  },

  // ── 404 ──
  { path: '*', element: <NotFoundPage /> },
]);
