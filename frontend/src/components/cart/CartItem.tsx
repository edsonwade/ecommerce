import { Box, IconButton, Tooltip, Typography } from '@mui/material';
import { Add, Remove, DeleteOutlined } from '@mui/icons-material';
import type { CartItemResponse } from '@api/types';
import { formatCurrency } from '@utils/format';

interface CartItemProps {
  item: CartItemResponse;
  onRemove: () => void;
  onQuantityChange: (quantity: number) => void;
}

export default function CartItem({ item, onRemove, onQuantityChange }: CartItemProps) {
  return (
    <Box
      sx={{
        display: 'flex',
        gap: 2,
        py: 2,
        borderBottom: '1px solid',
        borderColor: 'divider',
        '&:last-child': { borderBottom: 'none' },
      }}
    >
      {/* Thumbnail */}
      <Box
        sx={{
          width: 64,
          height: 64,
          flexShrink: 0,
          borderRadius: 1,
          bgcolor: 'var(--surface-sunken)',
          border: '1px solid',
          borderColor: 'divider',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Typography sx={{ fontFamily: 'var(--font-serif)', fontSize: '1.25rem', opacity: 0.2 }}>
          {item.productName.slice(0, 2).toUpperCase()}
        </Typography>
      </Box>

      {/* Details */}
      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <Typography
          variant="body2"
          sx={{
            fontWeight: 500,
            color: 'text.primary',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {item.productName}
        </Typography>
        <Typography
          variant="caption"
          sx={{ fontFamily: 'var(--font-mono)', color: 'primary.main' }}
        >
          {formatCurrency(item.unitPrice)}
        </Typography>

        {/* Quantity controls */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 1 }}>
          <IconButton
            size="small"
            onClick={() => onQuantityChange(item.quantity - 1)}
            disabled={item.quantity <= 1}
            sx={{ p: 0.25 }}
          >
            <Remove sx={{ fontSize: 16 }} />
          </IconButton>
          <Typography
            variant="body2"
            sx={{ fontFamily: 'var(--font-mono)', minWidth: 24, textAlign: 'center' }}
          >
            {item.quantity}
          </Typography>
          <Tooltip
            title={item.quantity >= item.availableQuantity ? 'Max stock reached' : ''}
            placement="top"
          >
            <span>
              <IconButton
                size="small"
                onClick={() => onQuantityChange(item.quantity + 1)}
                disabled={item.quantity >= item.availableQuantity}
                sx={{ p: 0.25 }}
              >
                <Add sx={{ fontSize: 16 }} />
              </IconButton>
            </span>
          </Tooltip>
        </Box>
        {item.quantity >= item.availableQuantity && (
          <Typography variant="caption" color="warning.main" sx={{ mt: 0.5, display: 'block' }}>
            Max stock reached
          </Typography>
        )}
      </Box>

      {/* Line total + remove */}
      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 1 }}>
        <Typography variant="body2" sx={{ fontFamily: 'var(--font-mono)', fontWeight: 500 }}>
          {formatCurrency(item.lineTotal)}
        </Typography>
        <IconButton
          size="small"
          onClick={onRemove}
          aria-label="Remove item"
          sx={{ color: 'text.disabled', '&:hover': { color: 'error.main' } }}
        >
          <DeleteOutlined sx={{ fontSize: 18 }} />
        </IconButton>
      </Box>
    </Box>
  );
}
