-- V1__create_tenant_tables.sql
-- Phase 4: Multi-tenancy foundation
-- Stores tenant accounts, feature flags, and API usage metrics

BEGIN;

-- -------------------------------------------------------
-- TENANT — core tenant account table
-- -------------------------------------------------------
CREATE TABLE tenant (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(36)     NOT NULL UNIQUE DEFAULT gen_random_uuid()::text,
    name            VARCHAR(255)    NOT NULL UNIQUE,
    slug            VARCHAR(100)    NOT NULL UNIQUE,
    contact_email   VARCHAR(255)    NOT NULL,
    plan            VARCHAR(30)     NOT NULL DEFAULT 'FREE',
    status          VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    -- Rate limit values per plan (req/min)
    rate_limit      INT             NOT NULL DEFAULT 100,
    -- Storage quota in bytes (-1 = unlimited)
    storage_quota   BIGINT          NOT NULL DEFAULT 1073741824,  -- 1 GB
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE INDEX idx_tenant_slug      ON tenant (slug);
CREATE INDEX idx_tenant_status    ON tenant (status);
CREATE INDEX idx_tenant_plan      ON tenant (plan);

-- -------------------------------------------------------
-- TENANT_FEATURE_FLAG — per-tenant feature toggles
-- -------------------------------------------------------
CREATE TABLE tenant_feature_flag (
    id          BIGSERIAL   PRIMARY KEY,
    tenant_id   VARCHAR(36) NOT NULL REFERENCES tenant(tenant_id) ON DELETE CASCADE,
    flag_name   VARCHAR(100) NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    description VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    UNIQUE (tenant_id, flag_name)
);

CREATE INDEX idx_feature_flag_tenant ON tenant_feature_flag (tenant_id);

-- -------------------------------------------------------
-- TENANT_USAGE_METRIC — API call metering per tenant
-- -------------------------------------------------------
CREATE TABLE tenant_usage_metric (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   VARCHAR(36)  NOT NULL REFERENCES tenant(tenant_id) ON DELETE CASCADE,
    metric_name VARCHAR(100) NOT NULL,   -- e.g. 'api.calls', 'orders.created'
    metric_value BIGINT      NOT NULL DEFAULT 0,
    period_date DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, metric_name, period_date)
);

CREATE INDEX idx_usage_metric_tenant ON tenant_usage_metric (tenant_id, period_date);

COMMIT;
