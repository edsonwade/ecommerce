import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Container,
  FormControl,
  InputLabel,
  MenuItem,
  Pagination,
  Select,
  TextField,
  Typography,
} from '@mui/material';
import { SearchOff } from '@mui/icons-material';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { productsApi } from '@api/products.api';
import { QUERY_KEYS } from '@utils/constants';
import ProductGrid from '@components/product/ProductGrid';
import { ProductGridSkeleton } from '@components/feedback/LoadingSkeleton';
import EmptyState from '@components/feedback/EmptyState';
import type { AppError } from '@api/types';

const PAGE_SIZE = 20;

const SORT_OPTIONS = [
  { value: 'name_asc',   label: 'Name A–Z' },
  { value: 'name_desc',  label: 'Name Z–A' },
  { value: 'price_asc',  label: 'Price: Low to High' },
  { value: 'price_desc', label: 'Price: High to Low' },
];

function parseSortParam(sort: string): { sortBy: string; sortDir: 'asc' | 'desc' } {
  const [sortBy, sortDir] = sort.split('_');
  return { sortBy: sortBy ?? 'name', sortDir: (sortDir === 'desc' ? 'desc' : 'asc') };
}

export default function CatalogPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const queryParam    = searchParams.get('query') ?? '';
  const categoryParam = searchParams.get('category') ?? '';
  const sortParam     = searchParams.get('sort') ?? 'name_asc';
  const pageParam     = parseInt(searchParams.get('page') ?? '0', 10);

  const [inputValue, setInputValue] = useState(queryParam);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setInputValue(queryParam);
  }, [queryParam]);

  function handleSearchChange(value: string) {
    setInputValue(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setSearchParams(prev => {
        const next = new URLSearchParams(prev);
        if (value) next.set('query', value); else next.delete('query');
        next.set('page', '0');
        return next;
      });
    }, 300);
  }

  function handleCategoryChange(id: string) {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      if (id) next.set('category', id); else next.delete('category');
      next.set('page', '0');
      return next;
    });
  }

  function handleSortChange(sort: string) {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      next.set('sort', sort);
      next.set('page', '0');
      return next;
    });
  }

  function handlePageChange(newPage: number) {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      next.set('page', String(newPage));
      return next;
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function clearFilters() {
    setInputValue('');
    setSearchParams({});
  }

  const { sortBy, sortDir } = parseSortParam(sortParam);
  const categoryId = categoryParam ? parseInt(categoryParam, 10) : undefined;
  const hasActiveFilters = !!(queryParam || categoryParam);

  const { data: categories } = useQuery({
    queryKey: [QUERY_KEYS.CATEGORIES],
    queryFn: () => productsApi.getCategories(),
    staleTime: 10 * 60 * 1000,
  });

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: [QUERY_KEYS.PRODUCTS, { query: queryParam, categoryId, sortBy, sortDir, page: pageParam }],
    queryFn: () =>
      productsApi.search({
        query: queryParam || undefined,
        categoryId,
        sortBy,
        sortDir,
        page: pageParam,
        size: PAGE_SIZE,
      }),
    staleTime: 2 * 60 * 1000,
    placeholderData: (prev) => prev,
    retry: 1,
  });

  const apiError = error as AppError | null;
  const totalElements =
    (data as { totalElements?: number } | undefined)?.totalElements ??
    (data as { page?: { totalElements?: number } } | undefined)?.page?.totalElements ??
    0;

  const activeCategoryName = categories?.find(c => c.id === categoryId)?.name;

  return (
    <Container maxWidth="xl" sx={{ py: 6, px: { xs: 2, md: 4 } }}>
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        {/* Header */}
        <Box sx={{ mb: 4 }}>
          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mb: 1 }}>
            ALL PRODUCTS
          </Typography>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
            <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)' }}>
              Catalog
            </Typography>
            {data && (
              <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'var(--font-mono)' }}>
                {totalElements} products
              </Typography>
            )}
          </Box>
        </Box>

        {/* Search / Filter Controls */}
        <Box
          sx={{
            display: 'flex',
            gap: 2,
            mb: 3,
            flexWrap: 'wrap',
            alignItems: 'center',
          }}
        >
          <TextField
            label="Search products"
            variant="outlined"
            size="small"
            value={inputValue}
            onChange={(e) => handleSearchChange(e.target.value)}
            sx={{ minWidth: 260, flexGrow: 1 }}
            slotProps={{ htmlInput: { 'aria-label': 'Search products' } }}
          />

          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel>Category</InputLabel>
            <Select
              label="Category"
              value={categoryParam}
              onChange={(e) => handleCategoryChange(e.target.value)}
            >
              <MenuItem value="">All categories</MenuItem>
              {categories?.map((cat) => (
                <MenuItem key={cat.id} value={String(cat.id)}>
                  {cat.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel>Sort by</InputLabel>
            <Select
              label="Sort by"
              value={sortParam}
              onChange={(e) => handleSortChange(e.target.value)}
            >
              {SORT_OPTIONS.map((opt) => (
                <MenuItem key={opt.value} value={opt.value}>
                  {opt.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          {hasActiveFilters && (
            <Button variant="outlined" size="small" onClick={clearFilters} color="secondary">
              Clear filters
            </Button>
          )}
        </Box>

        {/* Active Filter Chips */}
        {hasActiveFilters && (
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 3 }}>
            {queryParam && (
              <Chip
                label={`Search: "${queryParam}"`}
                onDelete={() => {
                  setInputValue('');
                  setSearchParams(prev => {
                    const next = new URLSearchParams(prev);
                    next.delete('query');
                    next.set('page', '0');
                    return next;
                  });
                }}
                size="small"
              />
            )}
            {categoryParam && activeCategoryName && (
              <Chip
                label={`Category: ${activeCategoryName}`}
                onDelete={() => handleCategoryChange('')}
                size="small"
              />
            )}
          </Box>
        )}

        {/* Error */}
        {isError && (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={() => refetch()}>
                Retry
              </Button>
            }
            sx={{ mb: 4 }}
          >
            {apiError?.status === 401
              ? 'Authentication required — please sign in to browse products.'
              : apiError?.status === 503
              ? 'Product service is temporarily unavailable. Please try again shortly.'
              : `Failed to load products (${apiError?.status ?? 'network error'}): ${apiError?.message ?? 'Unknown error'}`}
          </Alert>
        )}

        {/* Product Grid / Skeletons / Empty State */}
        {isLoading ? (
          <ProductGridSkeleton count={PAGE_SIZE} />
        ) : !isError && data?.content.length === 0 ? (
          <EmptyState
            title={queryParam ? `No products found for "${queryParam}"` : 'No products found'}
            description="Try adjusting your search or filters."
            icon={<SearchOff sx={{ fontSize: 64 }} />}
            action={{ label: 'Clear search', onClick: clearFilters }}
          />
        ) : !isError ? (
          <ProductGrid products={data?.content ?? []} />
        ) : null}

        {/* Pagination */}
        {data && data.totalPages > 1 && (
          <Box sx={{ mt: 6, display: 'flex', justifyContent: 'center' }}>
            <Pagination
              count={data.totalPages}
              page={pageParam + 1}
              onChange={(_, value) => handlePageChange(value - 1)}
              color="primary"
              shape="rounded"
            />
          </Box>
        )}
      </motion.div>
    </Container>
  );
}
