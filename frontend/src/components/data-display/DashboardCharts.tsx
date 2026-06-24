import { type ReactNode } from 'react';
import { Box, Typography } from '@mui/material';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { formatCurrency } from '@utils/format';
import { shortDay, type DayPoint, type Slice } from '@utils/analytics';

/** Gold/copper theme palette (mirrors the obsidian theme accent ramp). */
const CHART_PALETTE = ['#E8C48A', '#C47D5A', '#6B98B8', '#5CB87A', '#D4A544', '#C75450'];

const TOOLTIP_STYLE = {
  background: 'var(--surface-overlay)',
  border: '1px solid var(--border-default)',
  borderRadius: 4,
  color: 'var(--text-primary)',
  fontSize: 12,
} as const;

const AXIS_TICK = { fontSize: 11, fill: 'var(--text-secondary)' } as const;

function ChartCard({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  return (
    <Box sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
      <Typography variant="h6" sx={{ fontFamily: 'var(--font-serif)' }}>{title}</Typography>
      {subtitle && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>{subtitle}</Typography>
      )}
      <Box sx={{ mt: 2.5 }}>{children}</Box>
    </Box>
  );
}

function EmptyState({ height = 280 }: { height?: number }) {
  return (
    <Box sx={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Typography variant="body2" color="text.secondary">No data yet</Typography>
    </Box>
  );
}

/** Hero revenue/GMV-over-time area chart. */
export function RevenueAreaCard({
  title,
  subtitle,
  data,
  height = 300,
}: {
  title: string;
  subtitle?: string;
  data: DayPoint[];
  height?: number;
}) {
  return (
    <ChartCard title={title} subtitle={subtitle}>
      {data.length === 0 ? (
        <EmptyState height={height} />
      ) : (
        <ResponsiveContainer width="100%" height={height}>
          <AreaChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="revFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--accent-primary)" stopOpacity={0.35} />
                <stop offset="100%" stopColor="var(--accent-primary)" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="var(--border-default)" strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="date" tickFormatter={shortDay} tick={AXIS_TICK} />
            <YAxis tick={AXIS_TICK} tickFormatter={(v: number) => formatCurrency(v)} width={80} />
            <Tooltip
              contentStyle={TOOLTIP_STYLE}
              formatter={(value) => [formatCurrency(Number(value ?? 0)), 'Revenue']}
              labelFormatter={(label) => `Date: ${label}`}
            />
            <Area
              type="monotone"
              dataKey="revenue"
              stroke="var(--accent-primary)"
              strokeWidth={2}
              fill="url(#revFill)"
              dot={false}
            />
          </AreaChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}

/** Categorical breakdown donut (order status, payment method, …). */
export function BreakdownDonutCard({
  title,
  subtitle,
  data,
  height = 260,
}: {
  title: string;
  subtitle?: string;
  data: Slice[];
  height?: number;
}) {
  return (
    <ChartCard title={title} subtitle={subtitle}>
      {data.length === 0 ? (
        <EmptyState height={height} />
      ) : (
        <ResponsiveContainer width="100%" height={height}>
          <PieChart>
            <Pie data={data} dataKey="value" nameKey="name" innerRadius="55%" outerRadius="80%" paddingAngle={2}>
              {data.map((_, i) => (
                <Cell key={i} fill={CHART_PALETTE[i % CHART_PALETTE.length]} stroke="var(--surface-base)" />
              ))}
            </Pie>
            <Tooltip contentStyle={TOOLTIP_STYLE} />
            <Legend
              wrapperStyle={{ fontSize: 12, color: 'var(--text-secondary)' }}
              formatter={(value) => <span style={{ color: 'var(--text-secondary)' }}>{value}</span>}
            />
          </PieChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}

/** Activity-volume bar chart (orders/payments per day). */
export function ActivityBarCard({
  title,
  subtitle,
  data,
  height = 260,
}: {
  title: string;
  subtitle?: string;
  data: DayPoint[];
  height?: number;
}) {
  return (
    <ChartCard title={title} subtitle={subtitle}>
      {data.length === 0 ? (
        <EmptyState height={height} />
      ) : (
        <ResponsiveContainer width="100%" height={height}>
          <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid stroke="var(--border-default)" strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="date" tickFormatter={shortDay} tick={AXIS_TICK} />
            <YAxis allowDecimals={false} tick={AXIS_TICK} width={36} />
            <Tooltip
              contentStyle={TOOLTIP_STYLE}
              formatter={(value) => [Number(value ?? 0), 'Orders']}
              labelFormatter={(label) => `Date: ${label}`}
            />
            <Bar dataKey="count" fill="var(--accent-primary)" radius={[3, 3, 0, 0]} maxBarSize={32} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}
