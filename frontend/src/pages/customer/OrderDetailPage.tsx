import { useParams, Link, useLocation } from 'react-router-dom';
import {
  Alert, Box, Button, Chip, CircularProgress, Container, Divider, Typography,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper
} from '@mui/material';
import { useQueries, useQuery } from '@tanstack/react-query';
import { ArrowBack } from '@mui/icons-material';
import { motion } from 'framer-motion';
import { ordersApi } from '@api/orders.api';
import { productsApi } from '@api/products.api';
import { authApi } from '@api/auth.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { formatCurrency, formatDateTime } from '@utils/format';
import { distinctSellerIds } from '@utils/seller';
import { deriveSellerSummary } from '@utils/orderSummary';
import OrderStatusBadge from '@components/order/OrderStatusBadge';
import OrderItemRow from '@components/order/OrderItemRow';
import type { OrderStatus, ProductResponse, SellerProfileResponse } from '@api/types';

/** Joins the non-empty parts of an address into a single readable line. */
function addressLine(...parts: (string | undefined | null)[]): string {
  return parts.map((p) => (p ?? '').trim()).filter(Boolean).join(', ');
}

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const isSellerView = location.pathname.startsWith('/seller');
  const backTo = isSellerView ? ROUTES.SELLER_ORDERS : ROUTES.ORDERS;

  const { data: order, isLoading, isError } = useQuery({
    queryKey: [QUERY_KEYS.ORDER, id],
    queryFn: ({ signal }) => ordersApi.getById(Number(id), signal),
    enabled: !!id,
  });

  const { data: lines } = useQuery({
    queryKey: [QUERY_KEYS.ORDER_LINES, id],
    queryFn: ({ signal }) => ordersApi.getLines(Number(id), signal),
    enabled: !!id,
  });

  // Enrich each bare order line (productId + quantity only) with the product's name,
  // description, price and image. Each distinct productId is fetched once and cached.
  const productIds = [...new Set((lines ?? []).map((l) => l.productId))];
  const productQueries = useQueries({
    queries: productIds.map((productId) => ({
      queryKey: [QUERY_KEYS.PRODUCT, productId],
      queryFn: () => productsApi.getById(productId),
      enabled: productIds.length > 0,
      staleTime: 5 * 60 * 1000,
    })),
  });

  const productsById = new Map<number, ProductResponse>();
  productQueries.forEach((q) => {
    if (q.data) productsById.set(q.data.id, q.data);
  });
  const productsLoading = productQueries.some((q) => q.isLoading);

  // The seller(s) "sold by" block: the distinct owners (product.createdBy) of the order's
  // products. Each seller's public business identity is fetched once from auth-service.
  // createdBy is only a numeric user id for real sellers; seed/catalog products are owned by
  // the "system" sentinel (no business profile), so we skip any non-numeric owner — otherwise
  // GET /auth/sellers/system hits a Long path variable and 500s.
  const sellerIds = distinctSellerIds([...productsById.values()].map((p) => p.createdBy));
  const sellerQueries = useQueries({
    queries: sellerIds.map((sellerId) => ({
      queryKey: [QUERY_KEYS.SELLER_PROFILE, sellerId],
      queryFn: () => authApi.getSeller(sellerId),
      enabled: sellerIds.length > 0,
      staleTime: 5 * 60 * 1000,
    })),
  });
  const sellers = sellerQueries
    .map((q) => q.data)
    .filter((s): s is SellerProfileResponse => !!s);

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

  const customerName = addressLine(order.customerFirstname, order.customerLastname);
  const shipping = addressLine(
    addressLine(order.shippingStreet, order.shippingHouseNumber),
    order.shippingZipCode,
    order.shippingCity,
    order.shippingCountry,
  );
  const taxPct = order.taxRate != null ? `${(order.taxRate * 100).toFixed(0)}%` : null;

  // SELLER view: the order-level totals describe the WHOLE basket (every seller's money).
  // The backend already scopes the lines to this seller, so derive the seller's own
  // Subtotal/IVA/Total from those visible lines — never expose other sellers' revenue.
  // Customer/admin keep the authoritative order-level figures.
  const sellerSummary = isSellerView
    ? deriveSellerSummary(lines ?? [], productsById, order.taxRate ?? 0)
    : null;
  const summarySubtotal = sellerSummary ? sellerSummary.subtotal : order.subtotal ?? 0;
  const summaryTax = sellerSummary ? sellerSummary.tax : order.taxAmount ?? 0;
  const summaryTotal = sellerSummary ? sellerSummary.total : order.amount;

  // Discounts/promotions are basket-level (customer-facing), not a seller's revenue line.
  const hasDiscount = !isSellerView && (order.discountAmount ?? 0) > 0;
  const hasPromotion = !isSellerView && (!!order.promotionCode || (order.promotionAmount ?? 0) > 0);

  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Button component={Link} to={backTo} startIcon={<ArrowBack />} sx={{ mb: 4, color: 'text.secondary' }}>
        Back to orders
      </Button>

      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        {/* Header */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 4, flexWrap: 'wrap', gap: 2 }}>
          <Box>
            <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 0.5 }}>
              ORDER REFERENCE
            </Typography>
            <Typography variant="h4" sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main' }}>
              {order.reference}
            </Typography>
            {order.createdDate && (
              <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 0.5 }}>
                Placed on {formatDateTime(order.createdDate)}
              </Typography>
            )}
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <OrderStatusBadge status={order.status as OrderStatus} />
            <Chip
              label={order.paymentMethod}
              size="small"
              sx={{ bgcolor: 'var(--surface-sunken)', color: 'text.secondary', fontFamily: 'var(--font-mono)', fontSize: '0.7rem', height: 22 }}
            />
          </Box>
        </Box>

        {/* Bill-to / Sold-by */}
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 3, mb: 4 }}>
          <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2.5 }}>
            <Typography variant="caption" sx={{ color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.08em' }}>
              Customer
            </Typography>
            <Typography variant="body1" sx={{ fontFamily: 'var(--font-serif)', mt: 0.5 }}>
              {customerName || 'Name not on file'}
            </Typography>
            {order.customerEmail && (
              <Typography variant="body2" color="text.secondary">{order.customerEmail}</Typography>
            )}
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              {shipping || 'Shipping address not provided'}
            </Typography>
            <Typography variant="caption" sx={{ fontFamily: 'var(--font-mono)', color: 'text.disabled', display: 'block', mt: 1 }}>
              ID {order.customerId}
            </Typography>
          </Box>

          <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2.5 }}>
            <Typography variant="caption" sx={{ color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.08em' }}>
              Sold by
            </Typography>
            {sellers.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Seller details unavailable
              </Typography>
            ) : (
              sellers.map((s, i) => (
                <Box key={s.id} sx={{ mt: i === 0 ? 0.5 : 1.5 }}>
                  <Typography variant="body1" sx={{ fontFamily: 'var(--font-serif)' }}>
                    {s.companyName || s.fullName}
                  </Typography>
                  {s.companyName && s.fullName && (
                    <Typography variant="body2" color="text.secondary">{s.fullName}</Typography>
                  )}
                  {s.vatNumber && (
                    <Typography variant="body2" color="text.secondary">VAT / IVA: {s.vatNumber}</Typography>
                  )}
                  {addressLine(s.street, s.postalCode, s.city, s.country) && (
                    <Typography variant="body2" color="text.secondary">
                      {addressLine(s.street, s.postalCode, s.city, s.country)}
                    </Typography>
                  )}
                  {s.email && (
                    <Typography variant="caption" sx={{ color: 'text.disabled', display: 'block' }}>{s.email}</Typography>
                  )}
                </Box>
              ))
            )}
          </Box>
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
                      <TableCell sx={{ fontSize: '0.7rem', letterSpacing: '0.08em', textTransform: 'uppercase', color: 'text.secondary' }}>Product</TableCell>
                      <TableCell align="center" sx={{ fontSize: '0.7rem', letterSpacing: '0.08em', textTransform: 'uppercase', color: 'text.secondary' }}>Qty</TableCell>
                      <TableCell align="right" sx={{ fontSize: '0.7rem', letterSpacing: '0.08em', textTransform: 'uppercase', color: 'text.secondary' }}>Unit price</TableCell>
                      <TableCell align="right" sx={{ fontSize: '0.7rem', letterSpacing: '0.08em', textTransform: 'uppercase', color: 'text.secondary' }}>Total</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {lines.map((line) => (
                      <OrderItemRow
                        key={line.id}
                        line={line}
                        product={productsById.get(line.productId)}
                        isLoading={productsLoading && !productsById.has(line.productId)}
                      />
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
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.25 }}>
              <Row label="Subtotal" value={formatCurrency(summarySubtotal)} />
              {hasDiscount && <Row label="Discount" value={`− ${formatCurrency(order.discountAmount ?? 0)}`} />}
              {hasPromotion && (
                <Row
                  label={order.promotionCode ? `Promotion (${order.promotionCode})` : 'Promotion'}
                  value={`− ${formatCurrency(order.promotionAmount ?? 0)}`}
                />
              )}
              <Row label={`IVA${taxPct ? ` (${taxPct})` : ''}`} value={formatCurrency(summaryTax)} />
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography variant="body2" color="text.secondary">Payment</Typography>
                <Typography variant="body2">{order.paymentMethod}</Typography>
              </Box>
            </Box>
            <Divider sx={{ my: 2 }} />
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
              <Typography variant="body1" sx={{ fontWeight: 600 }}>Total</Typography>
              <Typography variant="h6" sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main' }}>
                {formatCurrency(summaryTotal)}
              </Typography>
            </Box>
          </Box>
        </Box>
      </motion.div>
    </Container>
  );
}

/** A label/value line in the summary panel. */
function Row({ label, value }: { label: string; value: string }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="body2" sx={{ fontFamily: 'var(--font-mono)' }}>{value}</Typography>
    </Box>
  );
}
