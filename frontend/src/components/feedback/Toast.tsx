import { useEffect, useRef } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Box, IconButton, Typography } from '@mui/material';
import { Close, CheckCircle, Error, Warning, Info } from '@mui/icons-material';
import { useUIStore, type Toast as ToastType } from '@stores/ui.store';

const VARIANT_CONFIG = {
  success: { icon: CheckCircle, color: 'var(--status-success)' },
  error: { icon: Error, color: 'var(--status-error)' },
  warning: { icon: Warning, color: 'var(--status-warning)' },
  info: { icon: Info, color: 'var(--status-info)' },
};

function ToastItem({ toast }: { toast: ToastType }) {
  const removeToast = useUIStore((s) => s.removeToast);
  const duration = toast.duration ?? 5000;
  const { icon: Icon, color } = VARIANT_CONFIG[toast.variant];
  const progressRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const timer = setTimeout(() => removeToast(toast.id), duration);
    return () => clearTimeout(timer);
  }, [toast.id, duration, removeToast]);

  return (
    <motion.div
      layout
      initial={{ opacity: 0, x: 48, scale: 0.96 }}
      animate={{ opacity: 1, x: 0, scale: 1 }}
      exit={{ opacity: 0, x: 48, scale: 0.96 }}
      transition={{ duration: 0.3, ease: [0.2, 0, 0, 1] }}
      style={{ width: 380, maxWidth: 'calc(100vw - 32px)' }}
    >
      <Box
        sx={{
          position: 'relative',
          bgcolor: 'background.paper',
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 1,
          overflow: 'hidden',
          display: 'flex',
          alignItems: 'flex-start',
          gap: 1.5,
          p: 1.5,
          pr: 5,
          boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
        }}
      >
        {/* Left color bar */}
        <Box
          sx={{
            position: 'absolute',
            left: 0,
            top: 0,
            bottom: 0,
            width: 3,
            bgcolor: color,
          }}
        />

        <Icon sx={{ color, fontSize: 20, mt: 0.15, flexShrink: 0 }} />

        <Typography variant="body2" sx={{ color: 'text.primary', lineHeight: 1.5 }}>
          {toast.message}
        </Typography>

        <IconButton
          size="small"
          onClick={() => removeToast(toast.id)}
          aria-label="Dismiss notification"
          sx={{
            position: 'absolute',
            right: 6,
            top: 6,
            color: 'text.tertiary',
            '&:hover': { color: 'text.secondary' },
          }}
        >
          <Close sx={{ fontSize: 16 }} />
        </IconButton>

        {/* Progress bar */}
        <Box
          ref={progressRef}
          sx={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            height: 2,
            bgcolor: color,
            opacity: 0.4,
            animation: `shrink ${duration}ms linear forwards`,
            '@keyframes shrink': {
              from: { transform: 'scaleX(1)' },
              to: { transform: 'scaleX(0)' },
            },
            transformOrigin: 'left',
          }}
        />
      </Box>
    </motion.div>
  );
}

export default function ToastStack() {
  const toastQueue = useUIStore((s) => s.toastQueue);

  return (
    <Box
      sx={{
        position: 'fixed',
        top: 16,
        right: 16,
        zIndex: 9000,
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        pointerEvents: 'none',
        '& > *': { pointerEvents: 'all' },
      }}
    >
      <AnimatePresence mode="sync">
        {toastQueue.map((toast) => (
          <ToastItem key={toast.id} toast={toast} />
        ))}
      </AnimatePresence>
    </Box>
  );
}
