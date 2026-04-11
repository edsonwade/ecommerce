import { useParams, Link } from 'react-router-dom';
import {
  Alert, Box, Button, CircularProgress, Container, Divider, Typography,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { ArrowBack } from '@mui/icons-material';
import { motion } from 'framer-motion';
import { ordersApi } from '@api/orders.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { formatCurrency, formatDateTime } from '@utils/format';
import OrderStatusBadge from '@components/order/OrderStatusBadge';
import type { OrderStatus } from '@api/types';

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();

  const { data: order, isLoading, isError } = useQuery({
    queryKey: [QUERY_KEYS.ORDER, id],
    queryFn: () => ordersApi.getById(Number(id)),
    enabled: !!id,
  });

  const { data: lines } = useQuery({
    queryKey: [QUERY_KEYS.ORDER_LINES, id],
    queryFn: () => ordersApi.getLines(Number(id)),
    enabled: !!id,
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (isError || !order) {
    return (
      <Container maxWidth="md" sx={{ py: 8 }}>
        <Alert severity="error">Order not found.</Alert>
      </Container>
    );
  }

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Button component={Link} to={ROUTES.ORDERS} startIcon={<ArrowBack />} sx={{ mb: 4, color: 'text.secondary' }}>
        Back to orders
      </Button>

      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 4, flexWrap: 'wrap', gap: 2 }}>
          <Box>
            <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 0.5 }}>
              ORDER REFERENCE
            </Typography>
            <Typography variant="h4" sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main' }}>
              {order.reference}
            </Typography>
          </Box>
          <OrderStatusBadge status={order.paymentMethod as OrderStatus} />
        </Box>

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '2fr 1fr' }, gap: 4 }}>
          {/* Order lines */}
          <Box>
            <Typography variant="h6" sx={{ fontFamily: 'var(--font-serif)', mb: 2 }}>
              Order items
            </Typography>
            {lines && lines.length > 0 ? (
              <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontSize: '0.7rem', letterSpacing: '0.08em', textTransform: 'uppercase', color: 'text.secondary' }}>Product ID</TableCell>
                      <TableCell sx={{ fontSize: '0.7rem', letterSpacing: '0.08em', textTransform: 'uppercase', color: 'text.secondary' }}>Quantity</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {lines.map((line) => (
                      <TableRow key={line.id}>
                        <TableCell sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem' }}>
                          #{line.productId}
                        </TableCell>
                        <TableCell sx={{ fontFamily: 'var(--font-mono)' }}>{line.quantity}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            ) : (
              <Typography variant="body2" color="text.secondary">No line items available.</Typography>
            )}
          </Box>

          {/* Summary */}
          <Box sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3, height: 'fit-content' }}>
            <Typography variant="h6" sx={{ fontFamily: 'var(--font-serif)', mb: 2.5 }}>
              Summary
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography variant="body2" color="text.secondary">Payment</Typography>
                <Typography variant="body2">{order.paymentMethod}</Typography>
              </Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography variant="body2" color="text.secondary">Customer</Typography>
                <Typography variant="body2" sx={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem' }}>
                  {order.customerId.slice(0, 12)}…
                </Typography>
              </Box>
            </Box>
            <Divider sx={{ my: 2 }} />
            <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
              <Typography variant="body1" sx={{ fontWeight: 600 }}>Total</Typography>
              <Typography variant="h6" sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main' }}>
                {formatCurrency(order.amount)}
              </Typography>
            </Box>
          </Box>
        </Box>
      </motion.div>
    </Container>
  );
}
