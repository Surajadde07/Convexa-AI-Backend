-- V3__Multi_Workspace_Stage1.sql
-- Create Organization Memberships Table
CREATE TABLE IF NOT EXISTS organization_memberships (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL,
    department VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    removed_by BIGINT,
    removed_at TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_membership_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT uq_user_company UNIQUE (user_id, company_id)
);

-- Define Core Query Performance Indexes
CREATE INDEX IF NOT EXISTS idx_membership_user_id ON organization_memberships(user_id);
CREATE INDEX IF NOT EXISTS idx_membership_company_id ON organization_memberships(company_id);
CREATE INDEX IF NOT EXISTS idx_membership_company_role ON organization_memberships(company_id, role);
CREATE INDEX IF NOT EXISTS idx_membership_company_status ON organization_memberships(company_id, status);

-- Migrate Existing Active Users to the Membership Table
INSERT INTO organization_memberships (user_id, company_id, role, department, status, joined_at, last_activated_at, updated_at, version)
SELECT id, company_id, role, department, 'ACTIVE', COALESCE(created_at, CURRENT_TIMESTAMP), COALESCE(created_at, CURRENT_TIMESTAMP), COALESCE(created_at, CURRENT_TIMESTAMP), 0
FROM users
WHERE company_id IS NOT NULL
ON CONFLICT (user_id, company_id) DO NOTHING;

-- Add last_active_company_id column to users table if not exists
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_active_company_id BIGINT;
