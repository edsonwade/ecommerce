import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Button,
  CircularProgress,
  Container,
  FormControl,
  FormHelperText,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
} from '@mui/material';
import { useForm, type Resolver, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert } from '@mui/material';
import { productsApi } from '@api/products.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { useUIStore } from '@stores/ui.store';
import { useAuthStore } from '@stores/auth.store';
import type { ProductRequest } from '@api/types';

// Accept both `.` and `,` as decimal separator (handles pt-PT locale)
const priceRegex = /^\d+([.,]\d+)?$/;

// Form uses strings for number fields (HTML inputs are always strings)
const schema = z.object({
  name: z.string().min(1, 'Required'),
  description: z.string().min(1, 'Required'),
  price: z.string().min(1, 'Required').regex(priceRegex, 'Enter a valid price (e.g. 49.99)'),
  availableQuantity: z.string().min(1, 'Required'),
  categoryId: z.string().min(1, 'Required'),
});
type FormValues = z.infer<typeof schema>;

export default function ProductForm() {
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const addToast = useUIStore((s) => s.addToast);
  const sellerStatus = useAuthStore((s) => s.sellerStatus);
  // Mirrors the backend write-guard: pending/suspended sellers get 403 on product
  // writes, so the submit is disabled up-front with an explanatory banner.
  const writesLocked = sellerStatus === 'PENDING_APPROVAL' || sellerStatus === 'SUSPENDED';

  // Canonical PRODUCT key type is the NUMERIC id — see the note in ProductPage.tsx.
  // NaN when creating (no :id), which is harmless: the query is disabled via `isEdit`.
  const productId = Number(id);

  const { data: product, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.PRODUCT, productId],
    queryFn: () => productsApi.getById(productId),
    enabled: isEdit,
  });

  const { data: categories, isLoading: categoriesLoading } = useQuery({
    queryKey: [QUERY_KEYS.CATEGORIES],
    queryFn: () => productsApi.getCategories(),
    staleTime: 5 * 60 * 1000,
  });

  const { register, handleSubmit, reset, control, formState: { errors, isSubmitting } } = useForm<FormValues>({
    resolver: zodResolver(schema) as Resolver<FormValues>,
  });

  useEffect(() => {
    if (product) {
      reset({
        name: product.name,
        description: product.description,
        price: String(product.price),
        availableQuantity: String(product.availableQuantity),
        categoryId: String(product.categoryId),
      });
    }
  }, [product, reset]);

  const toRequest = (values: FormValues): ProductRequest => ({
    name: values.name,
    description: values.description,
    // Normalise comma → period so parseFloat works in all locales
    price: parseFloat(values.price.replace(',', '.')),
    availableQuantity: parseInt(values.availableQuantity, 10),
    categoryId: parseInt(values.categoryId, 10),
  });

  const { mutateAsync: createProduct } = useMutation({
    mutationFn: (req: ProductRequest) => productsApi.create(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.MY_PRODUCTS] });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.PRODUCTS] });
      addToast({ message: 'Product created', variant: 'success' });
      navigate(ROUTES.SELLER_PRODUCTS);
    },
    onError: () => addToast({ message: 'Failed to create product', variant: 'error' }),
  });

  const { mutateAsync: updateProduct } = useMutation({
    mutationFn: (req: ProductRequest) => productsApi.update(productId, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.MY_PRODUCTS] });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.PRODUCTS] });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.PRODUCT, productId] });
      addToast({ message: 'Product updated', variant: 'success' });
      navigate(ROUTES.SELLER_PRODUCTS);
    },
    onError: () => addToast({ message: 'Failed to update product', variant: 'error' }),
  });

  const onSubmit = (values: FormValues) => {
    const req = toRequest(values);
    return isEdit ? updateProduct(req) : createProduct(req);
  };

  if (isEdit && isLoading) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}><CircularProgress /></Box>;
  }

  return (
    <Container maxWidth="sm" sx={{ py: 2 }}>
      <Typography variant="h3" sx={{ fontFamily: 'var(--font-serif)', mb: 5 }}>
        {isEdit ? 'Edit product' : 'New product'}
      </Typography>

      {writesLocked && (
        <Alert severity={sellerStatus === 'SUSPENDED' ? 'error' : 'warning'} sx={{ mb: 3 }}>
          {sellerStatus === 'SUSPENDED'
            ? 'Your seller account is suspended — product changes are blocked.'
            : 'Your seller account is pending approval — you can save products once an administrator approves it.'}
        </Alert>
      )}

      <Box component="form" onSubmit={handleSubmit(onSubmit)} sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
        <TextField {...register('name')} label="Product name" error={!!errors.name} helperText={errors.name?.message} fullWidth autoFocus />
        <TextField {...register('description')} label="Description" multiline rows={3} error={!!errors.description} helperText={errors.description?.message} fullWidth />
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
          {/* type="text" + inputMode="decimal" bypasses browser locale decimal-separator coercion */}
          <TextField
            {...register('price')}
            label="Price (USD)"
            type="text"
            slotProps={{ htmlInput: { inputMode: 'decimal' } }}
            error={!!errors.price}
            helperText={errors.price?.message}
            fullWidth
          />
          <TextField {...register('availableQuantity')} label="Stock quantity" type="number" error={!!errors.availableQuantity} helperText={errors.availableQuantity?.message} fullWidth />
        </Box>

        <FormControl fullWidth error={!!errors.categoryId}>
          <InputLabel id="category-label">Category</InputLabel>
          <Controller
            name="categoryId"
            control={control}
            render={({ field }) => (
              <Select
                labelId="category-label"
                label="Category"
                {...field}
                disabled={categoriesLoading}
              >
                <MenuItem value=""><em>Select a category</em></MenuItem>
                {(categories ?? []).map((cat) => (
                  <MenuItem key={cat.id} value={String(cat.id)}>
                    {cat.name}
                  </MenuItem>
                ))}
              </Select>
            )}
          />
          {errors.categoryId && <FormHelperText>{errors.categoryId.message}</FormHelperText>}
        </FormControl>

        <Box sx={{ display: 'flex', gap: 2, mt: 1 }}>
          <Button variant="outlined" onClick={() => navigate(ROUTES.SELLER_PRODUCTS)} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" size="large" disabled={isSubmitting || writesLocked} sx={{ flex: 1 }}>
            {isSubmitting ? <CircularProgress size={22} color="inherit" /> : isEdit ? 'Save changes' : 'Create product'}
          </Button>
        </Box>
      </Box>
    </Container>
  );
}
