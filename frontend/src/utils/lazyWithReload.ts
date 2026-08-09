import { lazy } from 'react';
import type { ComponentType } from 'react';

const RELOAD_FLAG = 'chunk-reload-attempt';

/**
 * Wraps a route-level dynamic import so a failed chunk fetch self-heals.
 *
 * After a frontend redeploy the container serves a fresh index.html with new
 * hashed asset names; a tab still open from before the deploy holds references
 * to the OLD hashes. Navigating to a not-yet-loaded lazy route then fires an
 * `import('/assets/Page-<oldhash>.js')` that 404s, surfacing as
 * "Failed to fetch dynamically imported module". Without recovery the router's
 * errorElement (RouteErrorFallback) catches it into a dead-end "Something went
 * wrong" page — see AdminDashboard stale-deploy bug, 2026-08-01.
 *
 * On the first such failure we trigger ONE full-page reload, which re-fetches
 * the current index.html and its valid chunks. A sessionStorage guard prevents
 * a reload loop when the import fails for a real reason (offline, genuine 404):
 * the second attempt rethrows so the error boundary can show.
 */
export function lazyWithReload(factory: () => Promise<{ default: ComponentType }>) {
  return lazy(async () => {
    try {
      const mod = await factory();
      // Success — clear the guard so a future stale-deploy blip can reload again.
      sessionStorage.removeItem(RELOAD_FLAG);
      return mod;
    } catch (err) {
      if (!sessionStorage.getItem(RELOAD_FLAG)) {
        sessionStorage.setItem(RELOAD_FLAG, '1');
        window.location.reload();
        // Keep the Suspense fallback on screen during the reload instead of
        // flashing the error boundary: resolve to nothing until the tab reloads.
        return new Promise<{ default: ComponentType }>(() => {});
      }
      throw err;
    }
  });
}
