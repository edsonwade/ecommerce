import type { SellerStatus } from '../api/types';

export type SellerStatusAction = 'none' | 'unlock' | 'suspend' | 'sync';

/**
 * Decide what the SPA should do when the LIVE seller status (from GET /auth/account/me)
 * differs from what the auth store currently holds. Pure + framework-free so it is unit
 * testable in isolation.
 *
 *  - `unlock`  : became APPROVED  → silently refresh the token (new APPROVED claim), toast,
 *                unlock the UI. No logout/login.
 *  - `suspend` : became SUSPENDED → toast + force logout (the refresh token is already
 *                revoked server-side, so the session cannot continue).
 *  - `sync`    : any other change (e.g. re-queued to PENDING_APPROVAL) → mirror it into the
 *                store so the banner matches reality.
 *  - `none`    : unchanged, or there is nothing to act on (no live value yet).
 */
export function reconcileSellerStatus(
  stored: SellerStatus | null,
  live: SellerStatus | null | undefined,
): SellerStatusAction {
  if (live == null || live === stored) return 'none';
  if (live === 'APPROVED') return 'unlock';
  if (live === 'SUSPENDED') return 'suspend';
  return 'sync';
}
