import { useState } from 'react';
import { Box, Skeleton, TableCell, TableRow, Typography } from '@mui/material';
import type { OrderLineResponse, ProductResponse } from '@api/types';
import { getCategoryFallbackImage } from '@utils/productImages';
import { formatCurrency } from '@utils/format';

interface OrderItemRowProps {
  line: OrderLineResponse;
  product?: ProductResponse;
  isLoading: boolean;
}

/**
 * A single row in the order-detail "Order items" table.
 *
 * Enriches the bare order line (which only carries productId + quantity) with the
 * product's name and image, fetched from product-service. Owns its own image-error
 * state so it can run the same image fallback ladder as ProductCard
 * (imageUrl → category fallback → initials gradient) without sharing state across rows.
 *
 * When the product cannot be resolved (still loading, deleted, or fetch failed) the
 * row degrades gracefully to "Product #{id}" + a placeholder thumbnail — the page
 * never breaks just because one product lookup did.
 */
export default function OrderItemRow({ line, product, isLoading }: OrderItemRowProps) {
  const [imgError, setImgError] = useState(false);
  const [fallbackError, setFallbackError] = useState(false);

  const name = product?.name ?? `Product #${line.productId}`;
  const unitPrice = product?.price;
  const lineTotal = unitPrice != null ? unitPrice * line.quantity : undefined;
  const fallbackUrl = getCategoryFallbackImage(product?.categoryName);
  const showImage =
    (product?.imageUrl && !imgError) || (!product?.imageUrl && !fallbackError);
  const imgSrc = product?.imageUrl && !imgError ? product.imageUrl : fallbackUrl;

  return (
    <TableRow>
      <TableCell>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 44,
              height: 44,
              borderRadius: 1,
              overflow: 'hidden',
              flexShrink: 0,
              bgcolor: 'var(--surface-sunken)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {isLoading ? (
              <Skeleton variant="rectangular" width={44} height={44} />
            ) : showImage ? (
              <Box
                component="img"
                src={imgSrc}
                alt={name}
                onError={() => {
                  if (product?.imageUrl && !imgError) setImgError(true);
                  else setFallbackError(true);
                }}
                sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
              />
            ) : (
              <Typography
                sx={{
                  fontFamily: 'var(--font-serif)',
                  fontSize: '1rem',
                  opacity: 0.4,
                  color: 'text.primary',
                }}
              >
                {name.slice(0, 2).toUpperCase()}
              </Typography>
            )}
          </Box>

          <Box sx={{ minWidth: 0 }}>
            {isLoading ? (
              <Skeleton variant="text" width={140} />
            ) : (
              <Typography
                variant="body2"
                sx={{
                  fontFamily: 'var(--font-serif)',
                  color: 'text.primary',
                  lineHeight: 1.3,
                }}
              >
                {name}
              </Typography>
            )}
            {product?.description && (
              <Typography
                variant="caption"
                sx={{
                  color: 'text.secondary',
                  display: '-webkit-box',
                  WebkitLineClamp: 2,
                  WebkitBoxOrient: 'vertical',
                  overflow: 'hidden',
                  lineHeight: 1.35,
                }}
              >
                {product.description}
              </Typography>
            )}
            <Typography
              variant="caption"
              sx={{ fontFamily: 'var(--font-mono)', color: 'text.secondary', fontSize: '0.7rem', display: 'block' }}
            >
              #{line.productId}
            </Typography>
          </Box>
        </Box>
      </TableCell>
      <TableCell align="center" sx={{ fontFamily: 'var(--font-mono)' }}>{line.quantity}</TableCell>
      <TableCell align="right" sx={{ fontFamily: 'var(--font-mono)' }}>
        {isLoading ? <Skeleton variant="text" width={50} /> : unitPrice != null ? formatCurrency(unitPrice) : '—'}
      </TableCell>
      <TableCell align="right" sx={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>
        {isLoading ? <Skeleton variant="text" width={60} /> : lineTotal != null ? formatCurrency(lineTotal) : '—'}
      </TableCell>
    </TableRow>
  );
}
