import { Box, Button, Container, Typography } from '@mui/material';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { ordersApi } from '@api/orders.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { useAuthStore } from '@stores/auth.store';
import { formatCurrency } from '@utils/format';
import { StatCardSkeleton } from '@components/feedback/LoadingSkeleton';
import OrderStatusBadge from '@components/order/OrderStatusBadge';
import type { OrderStatus } from '@api/types';

function StatCard({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <Box
      sx={{
        bgcolor: 'background.paper',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 2,
        p: 3,
      }}
    >
      <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>
        {label}
      </Typography>
      <Typography variant="h3" sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main', mb: 0.5 }}>
        {value}
      </Typography>
      {sub && (
        <Typography variant="body2" color="text.secondary">
          {sub}
        </Typography>
      )}
    </Box>
  );
}

export default function DashboardPage() {
  const { email } = useAuthStore();

  const { data: orders, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.ORDERS],
    queryFn: ordersApi.getAll,
    staleTime: 30 * 1000,
    retry: false,
    throwOnError: false,
  });

  const totalSpent = orders?.reduce((s, o) => s + o.amount, 0) ?? 0;
  const recentOrders = orders?.slice(0, 5) ?? [];

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 0.5 }}>
          Welcome back
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 5 }}>
          {email}
        </Typography>

        {/* Stats */}
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' },
            gap: 2.5,
            mb: 6,
          }}
        >
          {isLoading ? (
            [1, 2, 3].map((i) => <StatCardSkeleton key={i} />)
          ) : (
            <>
              <StatCard label="TOTAL ORDERS" value={String(orders?.length ?? 0)} />
              <StatCard label="TOTAL SPENT" value={formatCurrency(totalSpent)} />
              <StatCard
                label="LAST ORDER"
                value={orders?.[0]?.reference?.slice(0, 8) ?? '—'}
                sub={orders?.[0] ? formatCurrency(orders[0].amount) : undefined}
              />
            </>
          )}
        </Box>

        {/* Recent orders */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2.5 }}>
          <Typography variant="h5" sx={{ fontFamily: 'var(--font-serif)' }}>
            Recent orders
          </Typography>
          <Button component={Link} to={ROUTES.ORDERS} size="small">
            View all
          </Button>
        </Box>

        {recentOrders.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            No orders yet.{' '}
            <Link to={ROUTES.CATALOG} style={{ color: 'var(--accent-primary)' }}>
              Browse catalog
            </Link>
          </Typography>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            {recentOrders.map((order) => (
              <Box
                key={order.id}
                component={Link}
                to={ROUTES.ORDER_DETAIL(order.id)}
                sx={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  p: 2.5,
                  bgcolor: 'background.paper',
                  border: '1px solid',
                  borderColor: 'divider',
                  borderRadius: 2,
                  textDecoration: 'none',
                  transition: 'border-color 200ms',
                  '&:hover': { borderColor: 'var(--border-emphasis)' },
                }}
              >
                <Box>
                  <Typography variant="body2" sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main' }}>
                    {order.reference}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {order.paymentMethod}
                  </Typography>
                </Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <OrderStatusBadge status={order.paymentMethod as OrderStatus} />
                  <Typography variant="body2" sx={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>
                    {formatCurrency(order.amount)}
                  </Typography>
                </Box>
              </Box>
            ))}
          </Box>
        )}
      </motion.div>
    </Container>
  );
}
