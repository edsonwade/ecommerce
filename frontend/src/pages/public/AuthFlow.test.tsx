import { describe, it, expect } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createAppTheme } from '@theme/mui-theme';
import LoginPage from './LoginPage';
import RegisterPage from './RegisterPage';

const theme = createAppTheme('dark');

function LocationProbe({ onChange }: { onChange: (path: string) => void }) {
  const loc = useLocation();
  onChange(loc.pathname);
  return null;
}

function renderAt(initialEntries: Array<string | { pathname: string; state?: unknown }>, onLocation?: (p: string) => void) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={initialEntries}>
          {onLocation && <LocationProbe onChange={onLocation} />}
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/seller" element={<div>seller-dashboard</div>} />
            <Route path="/account" element={<div>account-dashboard</div>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('LoginPage — email is always empty', () => {
  it('renders an empty email field even when navigation state carries an email', () => {
    renderAt([{ pathname: '/login', state: { email: 'leftover@example.com' } }]);
    const email = screen.getByLabelText(/email/i) as HTMLInputElement;
    expect(email.value).toBe('');
  });
});

describe('RegisterPage — both roles must sign in after registering', () => {
  it('USER registration navigates to /login (no auto-login)', async () => {
    const user = userEvent.setup();
    let path = '';
    renderAt(['/register'], (p) => { path = p; });

    await user.type(screen.getByLabelText(/first name/i), 'Ada');
    await user.type(screen.getByLabelText(/last name/i), 'Lovelace');
    await user.type(screen.getByLabelText(/email/i), 'ada@example.com');
    await user.type(screen.getByLabelText(/password/i), 'password123');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => expect(path).toBe('/login'));
    // And the login form is empty (no prefill from registration)
    const email = screen.getByLabelText(/email/i) as HTMLInputElement;
    expect(email.value).toBe('');
  });

  it('SELLER registration also navigates to /login (no auto-login)', async () => {
    const user = userEvent.setup();
    let path = '';
    renderAt(['/register'], (p) => { path = p; });

    await user.click(screen.getByRole('button', { name: /i want to sell/i }));
    await user.type(screen.getByLabelText(/first name/i), 'Sam');
    await user.type(screen.getByLabelText(/last name/i), 'Seller');
    await user.type(screen.getByLabelText(/email/i), 'sam@shop.example');
    await user.type(screen.getByLabelText(/password/i), 'password123');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => expect(path).toBe('/login'));
    expect(screen.queryByText('seller-dashboard')).not.toBeInTheDocument();
  });
});
