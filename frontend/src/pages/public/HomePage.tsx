import { lazy, Suspense } from 'react';
import { Link } from 'react-router-dom';
import { Box, Button, Container, Typography } from '@mui/material';
import { motion } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import { productsApi } from '@api/products.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { ProductGridSkeleton } from '@components/feedback/LoadingSkeleton';

const ProductGrid = lazy(() => import('@components/product/ProductGrid'));

export default function HomePage() {
  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.PRODUCTS, 0, 8],
    queryFn: () => productsApi.getAll(0, 8),
    staleTime: 2 * 60 * 1000,
  });

  return (
    <Box>
      {/* Hero section */}
      <Box
        sx={{
          minHeight: { xs: '60vh', md: '70vh' },
          display: 'flex',
          alignItems: 'center',
          borderBottom: '1px solid',
          borderColor: 'divider',
          px: { xs: 3, md: 8 },
          py: { xs: 6, md: 8 },
        }}
      >
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: [0.2, 0, 0, 1] }}
        >
          <Typography
            variant="caption"
            sx={{ color: 'primary.main', display: 'block', mb: 2 }}
          >
            MULTI-TENANT SAAS PLATFORM
          </Typography>
          <Typography
            variant="h1"
            sx={{
              fontFamily: 'var(--font-serif)',
              fontSize: { xs: '3rem', md: '4.5rem', lg: '5.5rem' },
              lineHeight: 1.02,
              mb: 3,
              maxWidth: '14ch',
            }}
          >
            Commerce,
            <br />
            <Box component="span" sx={{ color: 'primary.main' }}>
              refined.
            </Box>
          </Typography>
          <Typography
            variant="body1"
            color="text.secondary"
            sx={{ maxWidth: 420, mb: 4, fontSize: '1.125rem', lineHeight: 1.6 }}
          >
            A high-contrast, editorial commerce experience for discerning buyers and sellers.
            Explore our curated catalog.
          </Typography>
          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            <Button
              component={Link}
              to={ROUTES.CATALOG}
              variant="contained"
              size="large"
              sx={{ px: 4 }}
            >
              Explore catalog
            </Button>
            <Button
              component={Link}
              to={ROUTES.REGISTER}
              variant="outlined"
              size="large"
              sx={{ px: 4 }}
            >
              Get started
            </Button>
          </Box>
        </motion.div>
      </Box>

      {/* Featured products */}
      <Container maxWidth="xl" sx={{ py: 8, px: { xs: 2, md: 4 } }}>
        <Box sx={{ mb: 5 }}>
          <Typography
            variant="caption"
            sx={{ color: 'text.secondary', display: 'block', mb: 1 }}
          >
            FEATURED
          </Typography>
          <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)' }}>
            New arrivals
          </Typography>
        </Box>

        {isLoading ? (
          <ProductGridSkeleton count={8} />
        ) : (
          <Suspense fallback={<ProductGridSkeleton count={8} />}>
            <ProductGrid products={data?.content ?? []} />
          </Suspense>
        )}

        <Box sx={{ mt: 6, textAlign: 'center' }}>
          <Button
            component={Link}
            to={ROUTES.CATALOG}
            variant="outlined"
            size="large"
            sx={{ px: 6 }}
          >
            View all products
          </Button>
        </Box>
      </Container>
    </Box>
  );
}
