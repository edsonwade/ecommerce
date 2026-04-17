-- V3 — Role change audit log for ADMIN promote/demote operations.
CREATE TABLE role_audit_log (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    changed_by     VARCHAR(255) NOT NULL,
    target_user_id VARCHAR(255) NOT NULL,
    previous_role  VARCHAR(50)  NOT NULL,
    new_role       VARCHAR(50)  NOT NULL,
    timestamp      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_role_audit_target ON role_audit_log (target_user_id);
CREATE INDEX idx_role_audit_time   ON role_audit_log (timestamp DESC);
