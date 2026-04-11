// Obsidian Market — Design Tokens

export const colors = {
  dark: {
    surfaceBase: '#0E0E0E',
    surfaceRaised: '#1A1A1A',
    surfaceOverlay: '#242424',
    surfaceSunken: '#080808',
    borderDefault: '#2A2A2A',
    borderSubtle: '#1F1F1F',
    borderEmphasis: '#3D3D3D',
    textPrimary: '#F5F0EB',
    textSecondary: '#A09890',
    textTertiary: '#6B6560',
    accentPrimary: '#E8C48A',
    accentPrimaryHover: '#F0D4A0',
    accentSecondary: '#C47D5A',
    accentSecondaryHover: '#D48E6B',
    statusSuccess: '#5CB87A',
    statusWarning: '#D4A544',
    statusError: '#C75450',
    statusInfo: '#6B98B8',
  },
  light: {
    surfaceBase: '#FAF8F5',
    surfaceRaised: '#FFFFFF',
    surfaceSunken: '#F0ECE6',
    borderDefault: '#E5E0D8',
    textPrimary: '#1A1714',
    textSecondary: '#6B6560',
    accentPrimary: '#8B5E34',
    accentSecondary: '#A0522D',
  },
} as const;

export const typography = {
  fontSerif: '"DM Serif Display", Georgia, serif',
  fontSans: '"DM Sans", system-ui, sans-serif',
  fontMono: '"JetBrains Mono", "Fira Code", monospace',
} as const;

export const radius = {
  sm: '4px',
  md: '8px',
  lg: '12px',
  full: '9999px',
} as const;

export const transitions = {
  fast: '150ms ease-out',
  normal: '200ms cubic-bezier(0.2, 0, 0, 1)',
  slow: '300ms cubic-bezier(0.2, 0, 0, 1)',
} as const;
