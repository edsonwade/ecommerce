-- =====================================================================
-- V12 — Fase 3 (Marketplace Role Capabilities): product suspension.
-- Adds a lifecycle status to product. DB default 'ACTIVE' grandfathers
-- every existing row; the entity mirrors the same default in Java.
-- Index supports the public-read filter (WHERE status = 'ACTIVE') that
-- Task 3.2 adds to list/search/detail.
-- =====================================================================
ALTER TABLE product ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_product_status ON product (status);
