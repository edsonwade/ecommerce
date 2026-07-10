-- =============================================================================
-- V11 — Reconcile the placeholder tenant tag with the runtime tenant claim
-- =============================================================================
-- B3 Fase 1b: the read path now filters by tenant_id (Hibernate @Filter on the
-- list reads + findByIdAndTenantId on the by-id read). Live requests carry the
-- tenant from the JWT claim, which auth issues as 'default' (User default,
-- app_user column default, AuthService register default). The seed data (V4
-- backfill, V6/V10 inserts) and the old ProductService fallback used a different
-- literal, 'default-tenant', so once the filter activates a 'default' caller would
-- match ZERO of the seeded rows and the catalogue would appear empty.
--
-- This aligns the stored tag to the claim auth already emits. Value-only UPDATEs
-- (no rows added or removed) — safe to re-run (idempotent: the WHERE no longer
-- matches after the first pass).
-- =============================================================================

UPDATE product  SET tenant_id = 'default' WHERE tenant_id = 'default-tenant';
UPDATE category SET tenant_id = 'default' WHERE tenant_id = 'default-tenant';
