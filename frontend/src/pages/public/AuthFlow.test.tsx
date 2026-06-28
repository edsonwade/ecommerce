import { describe, it, expect } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createAppTheme } from '@theme/mui-theme';
import LoginPage from './LoginPage';
import RegisterPage from './RegisterPage';
import ForgotPasswordPage from './ForgotPasswordPage';
import ResetPasswordPage from './ResetPasswordPage';

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
            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
            <Route path="/reset-password" element={<ResetPasswordPage />} />
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
    renderAt(['/register']);

    await user.type(screen.getByLabelText(/first name/i), 'Ada');
    await user.type(screen.getByLabelText(/last name/i), 'Lovelace');
    await user.type(screen.getByLabelText(/email/i), 'ada@example.com');
    await user.type(screen.getByLabelText('Password', { selector: 'input' }), 'password123');
    await user.type(screen.getByLabelText('Confirm password', { selector: 'input' }), 'password123');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    // findByText waits for LoginPage's subtitle to appear in the DOM — this is
    // unique to LoginPage and only true after the route commit, avoiding the race
    // between LocationProbe's render-phase path update and the actual DOM transition.
    await screen.findByText('Sign in to your account', {}, { timeout: 5000 });
    // Now LoginPage is definitively mounted — email must be empty (no auto-fill).
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
    await user.type(screen.getByLabelText('Password', { selector: 'input' }), 'password123');
    await user.type(screen.getByLabelText('Confirm password', { selector: 'input' }), 'password123');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => expect(path).toBe('/login'));
    expect(screen.queryByText('seller-dashboard')).not.toBeInTheDocument();
  });

  it('blocks submission and shows an error when passwords do not match', async () => {
    const user = userEvent.setup();
    let path = '/register';
    renderAt(['/register'], (p) => { path = p; });

    await user.type(screen.getByLabelText(/first name/i), 'Ada');
    await user.type(screen.getByLabelText(/last name/i), 'Lovelace');
    await user.type(screen.getByLabelText(/email/i), 'ada@example.com');
    await user.type(screen.getByLabelText('Password', { selector: 'input' }), 'password123');
    await user.type(screen.getByLabelText('Confirm password', { selector: 'input' }), 'different999');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    await screen.findByText(/passwords do not match/i);
    expect(path).toBe('/register');
  });
});

describe('ForgotPasswordPage — enumeration-safe request flow', () => {
  it('shows the neutral confirmation after submitting an email', async () => {
    const user = userEvent.setup();
    renderAt(['/forgot-password']);

    await user.type(screen.getByLabelText(/email/i), 'someone@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    // The success copy is identical whether or not the account exists.
    await screen.findByText(/a reset link has been sent/i);
  });

  it('blocks submission and shows a validation error for an invalid email', async () => {
    const user = userEvent.setup();
    renderAt(['/forgot-password']);

    await user.type(screen.getByLabelText(/email/i), 'not-an-email');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    await screen.findByText(/enter a valid email/i);
    // Still on the form — no confirmation shown.
    expect(screen.queryByText(/a reset link has been sent/i)).not.toBeInTheDocument();
  });
});

describe('ResetPasswordPage — token-gated reset flow', () => {
  it('shows the missing-token error and no form when no token is present', () => {
    renderAt(['/reset-password']);
    expect(screen.getByText(/missing its token/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/new password/i)).not.toBeInTheDocument();
  });

  it('resets the password and navigates to /login when the token is valid', async () => {
    const user = userEvent.setup();
    let path = '';
    renderAt(['/reset-password?token=valid-token'], (p) => { path = p; });

    await user.type(screen.getByLabelText(/new password/i), 'newpassword123');
    await user.type(screen.getByLabelText(/confirm password/i), 'newpassword123');
    await user.click(screen.getByRole('button', { name: /reset password/i }));

    await waitFor(() => expect(path).toBe('/login'));
  });

  it('blocks submission and shows an error when passwords do not match', async () => {
    const user = userEvent.setup();
    let path = '/reset-password';
    renderAt(['/reset-password?token=valid-token'], (p) => { path = p; });

    await user.type(screen.getByLabelText(/new password/i), 'newpassword123');
    await user.type(screen.getByLabelText(/confirm password/i), 'different999');
    await user.click(screen.getByRole('button', { name: /reset password/i }));

    await screen.findByText(/passwords do not match/i);
    expect(path).toBe('/reset-password');
  });
});
