import { Container, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { Link } from 'react-router-dom';
import { ordersApi } from '@api/orders.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { TableSkeleton } from '@components/feedback/LoadingSkeleton';
import EmptyState from '@components/feedback/EmptyState';
import OrderStatusBadge from '@components/order/OrderStatusBadge';
import { formatCurrency } from '@utils/format';
import type { OrderStatus } from '@api/types';
import {
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper, Button
} from '@mui/material';

export default function OrdersPage() {
  const { data: orders, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.ORDERS],
    queryFn: ordersApi.getAll,
    staleTime: 30 * 1000,
  });

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 5 }}>
          My Orders
        </Typography>

        {isLoading ? (
          <TableSkeleton rows={6} cols={5} />
        ) : !orders || orders.length === 0 ? (
          <EmptyState
            title="No orders yet"
            description="Browse the catalog and place your first order."
            action={{ label: 'Browse catalog', to: ROUTES.CATALOG }}
          />
        ) : (
          <TableContainer
            component={Paper}
            elevation={0}
            sx={{ border: '1px solid', borderColor: 'divider' }}
          >
            <Table>
              <TableHead>
                <TableRow>
                  {['Reference', 'Amount', 'Payment', 'Customer', ''].map((h) => (
                    <TableCell
                      key={h}
                      sx={{
                        fontFamily: 'var(--font-sans)',
                        fontWeight: 500,
                        fontSize: '0.7rem',
                        letterSpacing: '0.08em',
                        textTransform: 'uppercase',
                        color: 'text.secondary',
                      }}
                    >
                      {h}
                    </TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {orders.map((order) => (
                  <TableRow
                    key={order.id}
                    sx={{ '&:hover': { bgcolor: 'action.hover' } }}
                  >
                    <TableCell sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem' }}>
                      {order.reference}
                    </TableCell>
                    <TableCell sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main' }}>
                      {formatCurrency(order.amount)}
                    </TableCell>
                    <TableCell>
                      <OrderStatusBadge status={order.paymentMethod as OrderStatus} />
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.875rem', color: 'text.secondary' }}>
                      {order.customerId.slice(0, 8)}…
                    </TableCell>
                    <TableCell align="right">
                      <Button
                        component={Link}
                        to={ROUTES.ORDER_DETAIL(order.id)}
                        size="small"
                        variant="outlined"
                      >
                        View
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </motion.div>
    </Container>
  );
}
