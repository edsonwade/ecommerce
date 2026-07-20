import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Box, Button, TextField, Typography } from '@mui/material';
import StarRating from './StarRating';

/** Mirrors the backend contract: rating 1–5 required, comment optional and capped at 2000. */
const MAX_COMMENT = 2000;

interface ReviewFormProps {
  onSubmit: (data: { rating: number; comment?: string }) => void;
  submitting?: boolean;
}

/**
 * The "write a review" form. Callers must only mount this when eligibility said `canReview` —
 * the form itself does no purchase checking, because the POST is the fail-closed authority and
 * re-checking here would just duplicate a decision the backend already made.
 */
export default function ReviewForm({ onSubmit, submitting }: ReviewFormProps) {
  const { t } = useTranslation();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (rating < 1) return; // button is disabled anyway; belt and braces
    onSubmit({ rating, comment: comment.trim() || undefined });
  };

  return (
    <Box
      component="form"
      onSubmit={handleSubmit}
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 1.5,
        p: 2,
        border: '1px solid',
        borderColor: 'var(--border-default)',
        borderRadius: 2,
        bgcolor: 'background.paper',
      }}
    >
      <Typography variant="subtitle2">{t('reviews.write')}</Typography>

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {t('reviews.yourRating')}
        </Typography>
        <StarRating value={rating} onChange={setRating} size="medium" label={t('reviews.yourRating')} />
      </Box>

      <TextField
        multiline
        minRows={3}
        size="small"
        label={t('reviews.comment')}
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        slotProps={{ htmlInput: { maxLength: MAX_COMMENT } }}
        helperText={`${comment.length}/${MAX_COMMENT}`}
      />

      <Button
        type="submit"
        variant="contained"
        size="small"
        disabled={rating < 1 || submitting}
        sx={{ alignSelf: 'flex-start' }}
      >
        {t('reviews.submit')}
      </Button>
    </Box>
  );
}
