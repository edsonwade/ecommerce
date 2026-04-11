import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Box, Button, CircularProgress, Container, TextField, Typography } from '@mui/material';
import { useForm, type Resolver } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { productsApi } from '@api/products.api';
import { QUERY_KEYS, ROUTES } from '@utils/constants';
import { useUIStore } from '@stores/ui.store';
import type { ProductRequest } from '@api/types';

// Form uses strings for number fields (HTML inputs are always strings)
const schema = z.object({
  name: z.string().min(1, 'Required'),
  description: z.string().min(1, 'Required'),
  price: z.string().min(1, 'Required'),
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

  const { data: product, isLoading } = useQuery({
    queryKey: [QUERY_KEYS.PRODUCT, id],
    queryFn: () => productsApi.getById(Number(id)),
    enabled: isEdit,
  });

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<FormValues>({
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
    price: parseFloat(values.price),
    availableQuantity: parseInt(values.availableQuantity, 10),
    categoryId: parseInt(values.categoryId, 10),
  });

  const { mutateAsync: createProduct } = useMutation({
    mutationFn: (req: ProductRequest) => productsApi.create(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.PRODUCTS] });
      addToast({ message: 'Product created', variant: 'success' });
      navigate(ROUTES.SELLER_PRODUCTS);
    },
    onError: () => addToast({ message: 'Failed to create product', variant: 'error' }),
  });

  const { mutateAsync: updateProduct } = useMutation({
    mutationFn: (req: ProductRequest) => productsApi.update(Number(id), req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.PRODUCTS] });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.PRODUCT, id] });
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

      <Box component="form" onSubmit={handleSubmit(onSubmit)} sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
        <TextField {...register('name')} label="Product name" error={!!errors.name} helperText={errors.name?.message} fullWidth autoFocus />
        <TextField {...register('description')} label="Description" multiline rows={3} error={!!errors.description} helperText={errors.description?.message} fullWidth />
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
          <TextField {...register('price')} label="Price (USD)" type="number" error={!!errors.price} helperText={errors.price?.message} fullWidth />
          <TextField {...register('availableQuantity')} label="Stock quantity" type="number" error={!!errors.availableQuantity} helperText={errors.availableQuantity?.message} fullWidth />
        </Box>
        <TextField {...register('categoryId')} label="Category ID" type="number" error={!!errors.categoryId} helperText={errors.categoryId?.message} fullWidth />

        <Box sx={{ display: 'flex', gap: 2, mt: 1 }}>
          <Button variant="outlined" onClick={() => navigate(ROUTES.SELLER_PRODUCTS)} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" size="large" disabled={isSubmitting} sx={{ flex: 1 }}>
            {isSubmitting ? <CircularProgress size={22} color="inherit" /> : isEdit ? 'Save changes' : 'Create product'}
          </Button>
        </Box>
      </Box>
    </Container>
  );
}
