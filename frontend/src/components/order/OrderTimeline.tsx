import { useEffect } from 'react';
import { Box, CircularProgress, Typography } from '@mui/material';
import { CheckCircle, Cancel, RadioButtonUnchecked } from '@mui/icons-material';
import { motion, AnimatePresence } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import { ordersApi } from '@api/orders.api';
import { QUERY_KEYS } from '@utils/constants';
import type { OrderStatus } from '@api/types';
import { formatDateTime } from '@utils/format';

interface Step {
  status: OrderStatus;
  label: string;
  description: string;
}

const STEPS: Step[] = [
  { status: 'REQUESTED', label: 'Order Placed', description: 'Your order has been submitted and is being processed' },
  { status: 'INVENTORY_RESERVED', label: 'Inventory Reserved', description: 'Items have been reserved in our warehouse' },
  { status: 'CONFIRMED', label: 'Order Confirmed', description: 'Payment authorized and order confirmed' },
];

const STATUS_ORDER: OrderStatus[] = ['REQUESTED', 'INVENTORY_RESERVED', 'CONFIRMED'];

interface OrderTimelineProps {
  correlationId: string;
  onComplete?: (status: OrderStatus) => void;
}

export default function OrderTimeline({ correlationId, onComplete }: OrderTimelineProps) {
  const { data: statusData } = useQuery({
    queryKey: [QUERY_KEYS.ORDER_STATUS, correlationId],
    queryFn: () => ordersApi.getStatus(correlationId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status === 'CONFIRMED' || status === 'CANCELLED') return false;
      return 3000;
    },
    staleTime: 0,
  });

  const currentStatus = statusData?.status;
  const isCancelled = currentStatus === 'CANCELLED';
  const isConfirmed = currentStatus === 'CONFIRMED';
  const isDone = isConfirmed || isCancelled;

  useEffect(() => {
    if (isDone && currentStatus && onComplete) {
      onComplete(currentStatus);
    }
  }, [isDone, currentStatus, onComplete]);

  const getStepIndex = (status: OrderStatus) => STATUS_ORDER.indexOf(status);
  const currentIndex = currentStatus ? getStepIndex(currentStatus) : -1;

  return (
    <Box sx={{ py: 2 }}>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
        {isCancelled ? (
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.3 }}
          >
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 2,
                p: 3,
                bgcolor: 'background.paper',
                border: '1px solid var(--status-error)',
                borderRadius: 2,
              }}
            >
              <Cancel sx={{ color: 'var(--status-error)', fontSize: 32 }} />
              <Box>
                <Typography variant="h6" sx={{ color: 'var(--status-error)', fontFamily: 'var(--font-serif)' }}>
                  Order Cancelled
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {statusData?.message ?? 'Your order could not be completed'}
                </Typography>
                {statusData?.timestamp && (
                  <Typography variant="caption" sx={{ fontFamily: 'var(--font-mono)', color: 'text.disabled' }}>
                    {formatDateTime(statusData.timestamp)}
                  </Typography>
                )}
              </Box>
            </Box>
          </motion.div>
        ) : (
          STEPS.map((step, index) => {
            const isCompleted = currentIndex >= index;
            const isCurrent = currentIndex === index && !isDone;
            const isFuture = currentIndex < index;

            return (
              <AnimatePresence key={step.status}>
                <motion.div
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.4, delay: index * 0.1, ease: 'easeOut' }}
                >
                  <Box sx={{ display: 'flex', gap: 2.5 }}>
                    {/* Icon + connector line */}
                    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                      <Box
                        sx={{
                          width: 32,
                          height: 32,
                          borderRadius: '50%',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          flexShrink: 0,
                          position: 'relative',
                        }}
                      >
                        {isConfirmed && isCompleted ? (
                          <CheckCircle sx={{ color: 'var(--status-success)', fontSize: 28 }} />
                        ) : isCurrent ? (
                          <Box sx={{ position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <Box
                              sx={{
                                position: 'absolute',
                                width: 28,
                                height: 28,
                                borderRadius: '50%',
                                bgcolor: 'primary.main',
                                opacity: 0.3,
                                animation: 'pulse 1.5s ease-in-out infinite',
                                '@keyframes pulse': {
                                  '0%': { transform: 'scale(1)', opacity: 0.3 },
                                  '50%': { transform: 'scale(1.4)', opacity: 0.1 },
                                  '100%': { transform: 'scale(1)', opacity: 0.3 },
                                },
                              }}
                            />
                            <CircularProgress size={20} sx={{ color: 'primary.main' }} />
                          </Box>
                        ) : isCompleted ? (
                          <CheckCircle sx={{ color: 'primary.main', fontSize: 28 }} />
                        ) : (
                          <RadioButtonUnchecked sx={{ color: 'var(--border-emphasis)', fontSize: 28 }} />
                        )}
                      </Box>

                      {/* Connector */}
                      {index < STEPS.length - 1 && (
                        <Box
                          sx={{
                            width: 2,
                            flex: 1,
                            minHeight: 32,
                            bgcolor: isCompleted ? 'primary.main' : 'var(--border-default)',
                            my: 0.5,
                            transition: 'background-color 400ms ease',
                          }}
                        />
                      )}
                    </Box>

                    {/* Content */}
                    <Box sx={{ pb: index < STEPS.length - 1 ? 2 : 0, pt: 0.25 }}>
                      <Typography
                        variant="body1"
                        sx={{
                          fontWeight: 600,
                          color: isFuture ? 'text.disabled' : 'text.primary',
                          mb: 0.25,
                        }}
                      >
                        {step.label}
                      </Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
                        {step.description}
                      </Typography>
                      {isCompleted && statusData?.timestamp && step.status === currentStatus && (
                        <Typography
                          variant="caption"
                          sx={{ fontFamily: 'var(--font-mono)', color: 'text.disabled' }}
                        >
                          {formatDateTime(statusData.timestamp)}
                        </Typography>
                      )}
                    </Box>
                  </Box>
                </motion.div>
              </AnimatePresence>
            );
          })
        )}
      </Box>
    </Box>
  );
}
