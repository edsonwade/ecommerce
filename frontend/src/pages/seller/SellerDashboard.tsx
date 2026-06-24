import { Box, Button, Container, Typography } from '@mui/material';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { productsApi } from '@api/products.api';
import { ordersApi } from '@api/orders.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { StatCardSkeleton } from '@components/feedback/LoadingSkeleton';
import { formatCurrency } from '@utils/format';
import { RevenueAreaCard, BreakdownDonutCard } from '@components/data-display/DashboardCharts';
import { revenueByDay, countByKey, averageOrderValue } from '@utils/analytics';

function StatCard({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <Box sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
      <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>{label}</Typography>
      <Typography variant="h3" sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main', mb: 0.5 }}>{value}</Typography>
      {sub && <Typography variant="body2" color="text.secondary">{sub}</Typography>}
    </Box>
  );
}

export default function SellerDashboard() {
  const { data: products, isLoading: productsLoading } = useQuery({
    queryKey: [QUERY_KEYS.MY_PRODUCTS, 0, 100],
    queryFn: ({ signal }) => productsApi.getMine(0, 100, signal),
    staleTime: 2 * 60 * 1000,
    retry: false,
    throwOnError: false,
  });
  const { data: orders, isLoading: ordersLoading } = useQuery({
    queryKey: [QUERY_KEYS.SELLER_ORDERS],
    queryFn: ({ signal }) => ordersApi.getSeller(signal),
    staleTime: 30 * 1000,
    retry: false,
    throwOnError: false,
  });

  const isLoading = productsLoading || ordersLoading;
  const totalRevenue = orders?.reduce((s, o) => s + o.amount, 0) ?? 0;
  const orderCount = orders?.length ?? 0;
  const aov = averageOrderValue(orders);
  const lowStock = products?.content.filter((p) => p.availableQuantity < 5).length ?? 0;

  const revenueSeries = revenueByDay(orders);
  const byStatus = countByKey(orders, (o) => o.status as string);
  const byPaymentMethod = countByKey(orders, (o) => o.paymentMethod);

  return (
    <Container maxWidth="lg" sx={{ py: 2 }}>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 5, flexWrap: 'wrap', gap: 2 }}>
          <Box>
            <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)' }}>Seller Dashboard</Typography>
            <Typography variant="body1" color="text.secondary">Manage your products and track orders</Typography>
          </Box>
          <Button component={Link} to={ROUTES.SELLER_PRODUCT_NEW} variant="contained">
            + Add product
          </Button>
        </Box>

        {/* KPI row */}
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' }, gap: 2.5, mb: 4 }}>
          {isLoading ? (
            [1, 2, 3, 4].map((i) => <StatCardSkeleton key={i} />)
          ) : (
            <>
              <StatCard label="TOTAL REVENUE" value={formatCurrency(totalRevenue)} sub={`${orderCount} orders`} />
              <StatCard label="AVG ORDER VALUE" value={formatCurrency(aov)} />
              <StatCard label="TOTAL PRODUCTS" value={String(products?.totalElements ?? 0)} />
              <StatCard label="LOW STOCK" value={String(lowStock)} sub="Items below 5 units" />
            </>
          )}
        </Box>

        {/* Hero: revenue over time */}
        {!isLoading && (
          <Box sx={{ mb: 4 }}>
            <RevenueAreaCard
              title="Revenue over time"
              subtitle="Daily revenue from your paid orders"
              data={revenueSeries}
            />
          </Box>
        )}

        {/* Breakdown row */}
        {!isLoading && (
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 2.5, mb: 4 }}>
            <BreakdownDonutCard title="Orders by status" subtitle="Saga lifecycle of your orders" data={byStatus} />
            <BreakdownDonutCard title="Payment methods" subtitle="How buyers paid" data={byPaymentMethod} />
          </Box>
        )}
      </motion.div>
    </Container>
  );
}
