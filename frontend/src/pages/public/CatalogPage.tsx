import { useState } from 'react';
import { Box, Container, Pagination, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { productsApi } from '@api/products.api';
import { QUERY_KEYS } from '@utils/constants';
import ProductGrid from '@components/product/ProductGrid';
import { ProductGridSkeleton } from '@components/feedback/LoadingSkeleton';

const PAGE_SIZE = 20;

export default function CatalogPage() {
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.PRODUCTS, page, PAGE_SIZE],
    queryFn: () => productsApi.getAll(page, PAGE_SIZE),
    staleTime: 2 * 60 * 1000,
    placeholderData: (prev) => prev,
  });

  return (
    <Container maxWidth="xl" sx={{ py: 6, px: { xs: 2, md: 4 } }}>
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <Box sx={{ mb: 5 }}>
          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>
            ALL PRODUCTS
          </Typography>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
            <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)' }}>
              Catalog
            </Typography>
            {data && (
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ fontFamily: 'var(--font-mono)' }}
              >
                {data.totalElements} products
              </Typography>
            )}
          </Box>
        </Box>

        {isLoading ? (
          <ProductGridSkeleton count={PAGE_SIZE} />
        ) : (
          <ProductGrid products={data?.content ?? []} />
        )}

        {data && data.totalPages > 1 && (
          <Box sx={{ mt: 6, display: 'flex', justifyContent: 'center' }}>
            <Pagination
              count={data.totalPages}
              page={page + 1}
              onChange={(_, value) => {
                setPage(value - 1);
                window.scrollTo({ top: 0, behavior: 'smooth' });
              }}
              color="primary"
              shape="rounded"
            />
          </Box>
        )}
      </motion.div>
    </Container>
  );
}
