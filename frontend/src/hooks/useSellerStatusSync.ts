import { useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { authApi } from '../api/auth.api';
import { useAuthStore } from '../stores/auth.store';
import { useUIStore } from '../stores/ui.store';
import { reconcileSellerStatus } from '../utils/sellerStatusSync';

// A seller who is still waiting is likely watching the screen — poll briskly so approval
// feels instant. Once approved we only need to catch a (rare) suspension, so we back off to
// keep background traffic light. Each poll is a single tiny request (one findById).
const PENDING_POLL_MS = 20000;
const APPROVED_POLL_MS = 60000;

/**
 * Keeps a SELLER's session in sync with their LIVE approval status WITHOUT a re-login.
 *
 * Problem this solves: the JWT bakes `sellerStatus` at login, so before this hook a seller
 * had to log out and back in to see an approval, and a suspension only took effect when the
 * (formerly 24h) access token expired. Here we poll `GET /auth/account/me` (a live DB read)
 * while the seller is not already suspended, and react the moment it changes:
 *   - APPROVED  → silently rotate the token (the new token carries the APPROVED claim, so
 *                 product writes are unlocked), show a success toast, drop the banner.
 *   - SUSPENDED → toast + force logout.
 *
 * NOTE (verified in production): for an already-authenticated seller, suspension is normally
 * enforced *before* this SUSPENDED branch runs — auth-service revokes the tokens, so the very
 * next `/account/me` poll returns 401, the shared 401 interceptor tries a refresh (also 401,
 * revoked) and clears auth + redirects to /login. The SUSPENDED branch below is the fallback
 * for the case where `/me` actually returns a SUSPENDED body (e.g. an access token still
 * accepted for reads); both paths end in an immediate logout.
 *
 * Mount once, high in the seller tree (SellerLayout).
 */
export function useSellerStatusSync() {
  const role = useAuthStore((s) => s.role);
  const storedStatus = useAuthStore((s) => s.sellerStatus);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  // Poll for any seller who is not already suspended: catches PENDING→APPROVED (unlock) and
  // APPROVED→SUSPENDED (lockout). A suspended session is terminal — it gets logged out.
  const shouldPoll = isAuthenticated && role === 'SELLER' && storedStatus !== 'SUSPENDED';
  const pollMs = storedStatus === 'PENDING_APPROVAL' ? PENDING_POLL_MS : APPROVED_POLL_MS;

  const { data } = useQuery({
    queryKey: ['seller-status-sync'],
    queryFn: () => authApi.getAccount(),
    enabled: shouldPoll,
    refetchInterval: shouldPoll ? pollMs : false,
    refetchOnWindowFocus: shouldPoll,
    staleTime: 0,
    retry: false,
  });

  // Guards against firing the async token rotation twice while it is in flight.
  const busy = useRef(false);

  useEffect(() => {
    const live = data?.sellerStatus ?? null;
    const action = reconcileSellerStatus(storedStatus, live);
    if (action === 'none' || busy.current) return;

    const { refreshToken, setTokens, setSellerStatus, clearAuth } = useAuthStore.getState();
    const { addToast } = useUIStore.getState();

    if (action === 'unlock') {
      busy.current = true;
      void (async () => {
        try {
          if (refreshToken) {
            // Rotating the token is what actually unlocks writes: the fresh access token
            // carries sellerStatus=APPROVED, which the product-service guard reads.
            const res = await authApi.refresh(refreshToken);
            setTokens(res.accessToken, res.refreshToken);
            setSellerStatus(res.sellerStatus ?? 'APPROVED');
          } else {
            setSellerStatus('APPROVED');
          }
          addToast({
            id: 'seller-approved',
            message: 'Your seller account was approved — you can start adding products now.',
            variant: 'success',
          });
        } catch {
          // If the silent refresh fails, still reflect the status; the next authenticated
          // call refreshes or bounces to login through the normal 401 interceptor path.
          setSellerStatus('APPROVED');
        } finally {
          busy.current = false;
        }
      })();
      return;
    }

    if (action === 'suspend') {
      setSellerStatus('SUSPENDED');
      addToast({
        id: 'seller-suspended',
        message: 'Your seller account has been suspended. Contact support to restore access.',
        variant: 'error',
      });
      clearAuth();
      window.location.href = '/login';
      return;
    }

    // 'sync' — mirror any other transition (e.g. re-queued to PENDING_APPROVAL).
    setSellerStatus(live);
  }, [data, storedStatus]);
}
