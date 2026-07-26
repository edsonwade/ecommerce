import { Box, Rating, Typography } from '@mui/material';
import { Star, StarBorder } from '@mui/icons-material';

interface StarRatingProps {
  /** 0–5. Halves render as half-stars in read-only mode. */
  value: number;
  /** Supplying this makes the control interactive; omitting it keeps it read-only. */
  onChange?: (value: number) => void;
  size?: 'small' | 'medium' | 'large';
  /** Renders "(12)" after the stars. Omit to show stars alone. */
  count?: number;
  label?: string;
}

/**
 * The single place stars are drawn in this app — read-only display and the interactive picker in
 * the review form are the same component, so a rating can never look like two different things.
 *
 * Read-only mode renders half-stars because the backend stores the average rounded to one decimal
 * (`ROUND(AVG(rating), 1)`), so 4.5 is a value the API genuinely returns. The interactive mode is
 * whole-star only, matching the backend's 1–5 integer `rating` column and its CHECK constraint.
 */
export default function StarRating({
  value,
  onChange,
  size = 'small',
  count,
  label,
}: StarRatingProps) {
  const readOnly = !onChange;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
      <Rating
        value={value}
        readOnly={readOnly}
        precision={readOnly ? 0.5 : 1}
        size={size}
        onChange={(_, next) => onChange?.(next ?? 0)}
        aria-label={label ?? `${value} out of 5 stars`}
        icon={<Star fontSize="inherit" />}
        emptyIcon={<StarBorder fontSize="inherit" />}
        sx={{ '& .MuiRating-iconFilled': { color: 'var(--status-warning, #f5a623)' } }}
      />
      {typeof count === 'number' && (
        <Typography variant="caption" sx={{ color: 'text.secondary', fontFamily: 'var(--font-mono)' }}>
          ({count})
        </Typography>
      )}
    </Box>
  );
}
