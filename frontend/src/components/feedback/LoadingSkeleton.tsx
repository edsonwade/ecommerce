import { Box, Skeleton } from '@mui/material';

export function ProductCardSkeleton() {
  return (
    <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, overflow: 'hidden' }}>
      <Skeleton variant="rectangular" height={200} animation="wave" />
      <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1 }}>
        <Skeleton variant="text" width="40%" height={14} animation="wave" />
        <Skeleton variant="text" width="80%" height={20} animation="wave" />
        <Skeleton variant="text" width="30%" height={22} animation="wave" />
        <Skeleton variant="rectangular" height={36} sx={{ borderRadius: 1 }} animation="wave" />
      </Box>
    </Box>
  );
}

export function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', sm: 'repeat(2,1fr)', md: 'repeat(3,1fr)', lg: 'repeat(4,1fr)' },
        gap: 2.5,
      }}
    >
      {Array.from({ length: count }).map((_, i) => (
        <ProductCardSkeleton key={i} />
      ))}
    </Box>
  );
}

export function TableSkeleton({ rows = 8, cols = 5 }: { rows?: number; cols?: number }) {
  return (
    <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, overflow: 'hidden' }}>
      <Box sx={{ p: 2, bgcolor: 'background.paper', borderBottom: '1px solid', borderColor: 'divider', display: 'flex', gap: 2 }}>
        {Array.from({ length: cols }).map((_, i) => (
          <Skeleton key={i} variant="text" height={14} sx={{ flex: 1 }} animation="wave" />
        ))}
      </Box>
      {Array.from({ length: rows }).map((_, ri) => (
        <Box key={ri} sx={{ p: 2, borderBottom: '1px solid', borderColor: 'divider', display: 'flex', gap: 2 }}>
          {Array.from({ length: cols }).map((_, ci) => (
            <Skeleton key={ci} variant="text" height={18} sx={{ flex: 1 }} animation="wave" />
          ))}
        </Box>
      ))}
    </Box>
  );
}

export function StatCardSkeleton() {
  return (
    <Box sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
      <Skeleton variant="text" width="50%" height={12} animation="wave" />
      <Skeleton variant="text" width="70%" height={40} animation="wave" sx={{ mt: 1 }} />
      <Skeleton variant="text" width="40%" height={14} animation="wave" />
    </Box>
  );
}
