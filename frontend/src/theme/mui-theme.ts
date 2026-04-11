import { createTheme, type PaletteMode } from '@mui/material';
import { colors, typography, radius } from './tokens';

export const createAppTheme = (mode: PaletteMode) => {
  const isDark = mode === 'dark';
  const c = isDark ? colors.dark : { ...colors.dark, ...colors.light };

  return createTheme({
    palette: {
      mode,
      background: {
        default: isDark ? colors.dark.surfaceBase : colors.light.surfaceBase,
        paper: isDark ? colors.dark.surfaceRaised : colors.light.surfaceRaised,
      },
      text: {
        primary: isDark ? colors.dark.textPrimary : colors.light.textPrimary,
        secondary: isDark ? colors.dark.textSecondary : colors.light.textSecondary,
        disabled: isDark ? colors.dark.textTertiary : '#9E9E9E',
      },
      primary: {
        main: isDark ? colors.dark.accentPrimary : colors.light.accentPrimary,
        light: isDark ? colors.dark.accentPrimaryHover : '#A0784A',
        dark: isDark ? '#C9A560' : '#6B4520',
        contrastText: isDark ? colors.dark.surfaceBase : '#FFFFFF',
      },
      secondary: {
        main: isDark ? colors.dark.accentSecondary : colors.light.accentSecondary,
        light: isDark ? colors.dark.accentSecondaryHover : '#B5633D',
        dark: isDark ? '#A05E3A' : '#7D3E1E',
        contrastText: '#FFFFFF',
      },
      error: { main: c.statusError },
      warning: { main: c.statusWarning },
      success: { main: c.statusSuccess },
      info: { main: c.statusInfo },
      divider: isDark ? colors.dark.borderDefault : colors.light.borderDefault,
    },
    typography: {
      fontFamily: typography.fontSans,
      h1: { fontFamily: typography.fontSerif, fontWeight: 400, fontSize: '4.5rem', lineHeight: 1.0 },
      h2: { fontFamily: typography.fontSerif, fontWeight: 400, fontSize: '3rem', lineHeight: 1.1 },
      h3: { fontFamily: typography.fontSerif, fontWeight: 400, fontSize: '2.25rem', lineHeight: 1.15 },
      h4: { fontFamily: typography.fontSans, fontWeight: 700, fontSize: '1.75rem', lineHeight: 1.2 },
      h5: { fontFamily: typography.fontSans, fontWeight: 700, fontSize: '1.375rem', lineHeight: 1.3 },
      h6: { fontFamily: typography.fontSans, fontWeight: 500, fontSize: '1.125rem', lineHeight: 1.35 },
      body1: { fontFamily: typography.fontSans, fontWeight: 400, fontSize: '1rem', lineHeight: 1.5 },
      body2: { fontFamily: typography.fontSans, fontWeight: 400, fontSize: '0.875rem', lineHeight: 1.5 },
      caption: {
        fontFamily: typography.fontSans,
        fontWeight: 500,
        fontSize: '0.75rem',
        lineHeight: 1.4,
        letterSpacing: '0.08em',
        textTransform: 'uppercase' as const,
      },
      button: { fontFamily: typography.fontSans, fontWeight: 500, letterSpacing: '0.02em' },
    },
    shape: { borderRadius: 4 },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            backgroundColor: isDark ? colors.dark.surfaceBase : colors.light.surfaceBase,
            color: isDark ? colors.dark.textPrimary : colors.light.textPrimary,
            scrollbarColor: isDark ? `${colors.dark.borderEmphasis} ${colors.dark.surfaceSunken}` : undefined,
            '&::-webkit-scrollbar': { width: '8px' },
            '&::-webkit-scrollbar-track': {
              background: isDark ? colors.dark.surfaceSunken : colors.light.surfaceSunken,
            },
            '&::-webkit-scrollbar-thumb': {
              background: isDark ? colors.dark.borderEmphasis : colors.light.borderDefault,
              borderRadius: '4px',
            },
          },
        },
      },
      MuiButton: {
        styleOverrides: {
          root: {
            borderRadius: radius.sm,
            textTransform: 'none',
            fontWeight: 500,
            padding: '10px 20px',
          },
          contained: {
            boxShadow: 'none',
            '&:hover': { boxShadow: 'none' },
          },
          outlined: {
            borderColor: isDark ? colors.dark.borderEmphasis : colors.light.borderDefault,
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: radius.md,
            border: `1px solid ${isDark ? colors.dark.borderDefault : colors.light.borderDefault}`,
            backgroundImage: 'none',
            boxShadow: 'none',
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            backgroundImage: 'none',
            border: `1px solid ${isDark ? colors.dark.borderDefault : colors.light.borderDefault}`,
          },
        },
      },
      MuiTextField: {
        defaultProps: { variant: 'outlined' as const },
        styleOverrides: {
          root: {
            '& .MuiOutlinedInput-root': {
              borderRadius: radius.sm,
              '& fieldset': {
                borderColor: isDark ? colors.dark.borderDefault : colors.light.borderDefault,
              },
              '&:hover fieldset': {
                borderColor: isDark ? colors.dark.borderEmphasis : colors.dark.textSecondary,
              },
            },
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: { borderRadius: radius.sm },
        },
      },
      MuiTooltip: {
        styleOverrides: {
          tooltip: {
            borderRadius: radius.sm,
            backgroundColor: isDark ? colors.dark.surfaceOverlay : colors.light.textPrimary,
            fontSize: '0.75rem',
          },
        },
      },
      MuiDivider: {
        styleOverrides: {
          root: {
            borderColor: isDark ? colors.dark.borderDefault : colors.light.borderDefault,
          },
        },
      },
      MuiTableHead: {
        styleOverrides: {
          root: {
            backgroundColor: isDark ? colors.dark.surfaceSunken : colors.light.surfaceSunken,
          },
        },
      },
    },
  });
};
