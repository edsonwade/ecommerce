import { Chip } from '@mui/material';
import type { Role } from '@api/types';

const palette: Record<Role, { bg: string; fg: string; label: string }> = {
  ADMIN:  { bg: '#fdecea', fg: '#b71c1c', label: 'ADMIN'  },
  SELLER: { bg: '#e3f2fd', fg: '#0d47a1', label: 'SELLER' },
  USER:   { bg: '#eeeeee', fg: '#424242', label: 'USER'   },
};

interface RoleBadgeProps {
  role: Role | null | undefined;
}

export default function RoleBadge({ role }: RoleBadgeProps) {
  if (!role) return null;
  const { bg, fg, label } = palette[role];
  return (
    <Chip
      label={label}
      size="small"
      sx={{
        bgcolor: bg,
        color: fg,
        fontFamily: 'var(--font-mono)',
        fontWeight: 700,
        fontSize: '0.7rem',
        height: 22,
        ml: 1,
      }}
    />
  );
}
