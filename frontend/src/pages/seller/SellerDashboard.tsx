import { Box, Button, Container, Typography } from '@mui/material';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { productsApi } from '@api/products.api';
import { ordersApi } from '@api/orders.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { StatCardSkeleton } from '@components/feedback/LoadingSkeleton';
import { formatCurrency } from '@utils/format';

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
    queryKey: [QUERY_KEYS.PRODUCTS, 0, 100],
    queryFn: () => productsApi.getAll(0, 100),
    staleTime: 2 * 60 * 1000,
    retry: false,
    throwOnError: false,
  });
  const { data: orders, isLoading: ordersLoading } = useQuery({
    queryKey: [QUERY_KEYS.ORDERS],
    queryFn: ordersApi.getAll,
    staleTime: 30 * 1000,
    retry: false,
    throwOnError: false,
  });

  const isLoading = productsLoading || ordersLoading;
  const totalRevenue = orders?.reduce((s, o) => s + o.amount, 0) ?? 0;
  const lowStock = products?.content.filter((p) => p.availableQuantity < 5).length ?? 0;

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

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, gap: 2.5, mb: 6 }}>
          {isLoading ? (
            [1, 2, 3].map((i) => <StatCardSkeleton key={i} />)
          ) : (
            <>
              <StatCard label="TOTAL PRODUCTS" value={String(products?.totalElements ?? 0)} />
              <StatCard label="TOTAL REVENUE" value={formatCurrency(totalRevenue)} />
              <StatCard label="LOW STOCK" value={String(lowStock)} sub="Items below 5 units" />
            </>
          )}
        </Box>
      </motion.div>
    </Container>
  );
}
