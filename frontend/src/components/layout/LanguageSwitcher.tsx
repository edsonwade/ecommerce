import { useState } from 'react';
import { IconButton, Menu, MenuItem, ListItemText, Tooltip, Typography } from '@mui/material';
import { Translate, Check } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { SUPPORTED_LANGUAGES } from '../../i18n/config';

/**
 * Language picker shown in the top navbar. Switching is instant (i18next
 * swaps resources in memory) and the choice is persisted to localStorage by
 * the language detector, so it survives reloads and future visits.
 */
export default function LanguageSwitcher() {
  const { t, i18n } = useTranslation();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  const current =
    SUPPORTED_LANGUAGES.find((l) => i18n.resolvedLanguage === l.code) ?? SUPPORTED_LANGUAGES[0];

  const handleSelect = (code: string) => {
    void i18n.changeLanguage(code);
    setAnchorEl(null);
  };

  return (
    <>
      <Tooltip title={t('nav.language')}>
        <IconButton
          onClick={(e) => setAnchorEl(e.currentTarget)}
          size="small"
          sx={{ color: 'text.secondary' }}
          aria-label={t('nav.language')}
        >
          <Translate fontSize="small" />
        </IconButton>
      </Tooltip>

      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => setAnchorEl(null)}
        slotProps={{ paper: { sx: { mt: 1, minWidth: 180 } } }}
        transformOrigin={{ horizontal: 'right', vertical: 'top' }}
        anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
      >
        {SUPPORTED_LANGUAGES.map((lang) => (
          <MenuItem
            key={lang.code}
            selected={lang.code === current.code}
            onClick={() => handleSelect(lang.code)}
            sx={{ gap: 1.5 }}
          >
            <Typography component="span" sx={{ fontSize: '1.1rem', lineHeight: 1 }}>
              {lang.flag}
            </Typography>
            <ListItemText primary={lang.label} />
            {lang.code === current.code && <Check fontSize="small" color="primary" />}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}
