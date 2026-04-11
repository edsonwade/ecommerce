import { Box, Button, Typography } from '@mui/material';
import { Inbox } from '@mui/icons-material';
import { Link } from 'react-router-dom';

interface EmptyStateProps {
  title: string;
  description?: string;
  action?: { label: string; to?: string; onClick?: () => void };
  icon?: React.ReactNode;
}

export default function EmptyState({ title, description, action, icon }: EmptyStateProps) {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        py: 10,
        px: 3,
        gap: 2,
      }}
    >
      <Box sx={{ color: 'text.disabled', mb: 1 }}>
        {icon ?? <Inbox sx={{ fontSize: 64 }} />}
      </Box>
      <Typography variant="h5" sx={{ fontFamily: 'var(--font-serif)' }}>
        {title}
      </Typography>
      {description && (
        <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 380 }}>
          {description}
        </Typography>
      )}
      {action && (
        <Button
          variant="contained"
          sx={{ mt: 1 }}
          component={action.to ? Link : 'button'}
          to={action.to}
          onClick={action.onClick}
        >
          {action.label}
        </Button>
      )}
    </Box>
  );
}
