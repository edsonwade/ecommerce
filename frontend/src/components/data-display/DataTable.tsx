import {
  Box,
  IconButton,
  Table, TableBody, TableCell, TableContainer, TableHead,
  TableRow, TableSortLabel, Paper, Typography, Pagination,
} from '@mui/material';
import { Edit, Delete, Visibility } from '@mui/icons-material';

export interface Column<T> {
  key: string;
  label: string;
  render?: (row: T) => React.ReactNode;
  sortable?: boolean;
  align?: 'left' | 'right' | 'center';
}

interface DataTableProps<T extends { id: number | string }> {
  columns: Column<T>[];
  rows: T[];
  totalPages?: number;
  page?: number;
  onPageChange?: (page: number) => void;
  onEdit?: (row: T) => void;
  onDelete?: (row: T) => void;
  onView?: (row: T) => void;
  loading?: boolean;
}

export default function DataTable<T extends { id: number | string }>({
  columns,
  rows,
  totalPages,
  page = 1,
  onPageChange,
  onEdit,
  onDelete,
  onView,
}: DataTableProps<T>) {
  const hasActions = !!(onEdit || onDelete || onView);

  return (
    <Box>
      <TableContainer
        component={Paper}
        elevation={0}
        sx={{ border: '1px solid', borderColor: 'divider' }}
      >
        <Table>
          <TableHead>
            <TableRow sx={{ bgcolor: 'var(--surface-sunken)' }}>
              {columns.map((col) => (
                <TableCell
                  key={col.key}
                  align={col.align ?? 'left'}
                  sx={{
                    fontFamily: 'var(--font-sans)',
                    fontWeight: 500,
                    fontSize: '0.7rem',
                    letterSpacing: '0.08em',
                    textTransform: 'uppercase',
                    color: 'text.secondary',
                    py: 1.5,
                  }}
                >
                  {col.sortable ? (
                    <TableSortLabel>{col.label}</TableSortLabel>
                  ) : (
                    col.label
                  )}
                </TableCell>
              ))}
              {hasActions && (
                <TableCell
                  align="right"
                  sx={{
                    fontSize: '0.7rem',
                    letterSpacing: '0.08em',
                    textTransform: 'uppercase',
                    color: 'text.secondary',
                    py: 1.5,
                  }}
                >
                  Actions
                </TableCell>
              )}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={columns.length + (hasActions ? 1 : 0)} sx={{ py: 6, textAlign: 'center' }}>
                  <Typography variant="body2" color="text.secondary">
                    No data available
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row, idx) => (
                <TableRow
                  key={row.id}
                  sx={{
                    bgcolor: idx % 2 === 0 ? 'background.default' : 'background.paper',
                    '&:hover': {
                      bgcolor: 'action.hover',
                      '& td:first-of-type': { borderLeft: '3px solid', borderColor: 'primary.main' },
                    },
                    transition: 'background-color 150ms',
                  }}
                >
                  {columns.map((col) => (
                    <TableCell
                      key={col.key}
                      align={col.align ?? 'left'}
                      sx={{ fontSize: '0.875rem', py: 1.75 }}
                    >
                      {col.render ? col.render(row) : String((row as Record<string, unknown>)[col.key] ?? '')}
                    </TableCell>
                  ))}
                  {hasActions && (
                    <TableCell align="right">
                      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
                        {onView && (
                          <IconButton size="small" onClick={() => onView(row)} sx={{ color: 'text.secondary' }}>
                            <Visibility fontSize="small" />
                          </IconButton>
                        )}
                        {onEdit && (
                          <IconButton size="small" onClick={() => onEdit(row)} sx={{ color: 'text.secondary' }}>
                            <Edit fontSize="small" />
                          </IconButton>
                        )}
                        {onDelete && (
                          <IconButton size="small" onClick={() => onDelete(row)} sx={{ color: 'error.main' }}>
                            <Delete fontSize="small" />
                          </IconButton>
                        )}
                      </Box>
                    </TableCell>
                  )}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {totalPages && totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <Pagination
            count={totalPages}
            page={page}
            onChange={(_, v) => onPageChange?.(v)}
            color="primary"
            shape="rounded"
          />
        </Box>
      )}
    </Box>
  );
}
