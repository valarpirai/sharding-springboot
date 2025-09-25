-- PostgreSQL Database Setup Script for Multi-Tenant Ticket Management System
-- Development Environment: Global Database + Single Shard
-- Run as PostgreSQL superuser (e.g., postgres)

-- ===========================
-- Create Databases
-- ===========================

-- Create databases (will error if they exist, that's ok)
CREATE DATABASE global_db;
CREATE DATABASE shard1_db;

-- ===========================
-- Global Database Setup
-- ===========================

-- Connect to global database
\c global_db;

-- Accounts table (Tenants) - The only global business table
CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    admin_email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT false
);

-- Create indexes for accounts
CREATE INDEX IF NOT EXISTS idx_accounts_name ON accounts(name);
CREATE INDEX IF NOT EXISTS idx_accounts_admin_email ON accounts(admin_email);
CREATE INDEX IF NOT EXISTS idx_accounts_active ON accounts(deleted, created_at);

-- Tenant-shard mapping table (used by sharding library)
CREATE TABLE IF NOT EXISTS tenant_shard_mapping (
    tenant_id BIGINT NOT NULL,
    shard_id VARCHAR(255) NOT NULL,
    region VARCHAR(255),
    shard_status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
);

-- Create indexes for tenant_shard_mapping
CREATE INDEX IF NOT EXISTS idx_shard_id ON tenant_shard_mapping (shard_id);
CREATE INDEX IF NOT EXISTS idx_shard_status ON tenant_shard_mapping (shard_status);
CREATE INDEX IF NOT EXISTS idx_region ON tenant_shard_mapping (region);

-- Global configuration table (non-sharded)
CREATE TABLE IF NOT EXISTS global_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(255) UNIQUE NOT NULL,
    config_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert global configuration
INSERT INTO global_config (config_key, config_value, description) VALUES
('max_users_per_account', '100', 'Maximum users per account in development'),
('default_region', 'us-east-1', 'Default region for new tenants'),
('feature_flags.enable_advanced_search', 'true', 'Enable advanced search features'),
('rate_limits.api_requests_per_minute', '1000', 'API rate limit per tenant')
ON CONFLICT (config_key) DO UPDATE SET
    config_value = EXCLUDED.config_value,
    description = EXCLUDED.description;

-- ===========================
-- Shard Database Setup
-- ===========================

-- Connect to shard database
\c shard1_db;

-- Users table (sharded by account_id)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL, -- Tenant isolation field
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role_id BIGINT NOT NULL,
    active BOOLEAN DEFAULT true,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT false,

    -- Ensure email is unique within each tenant
    CONSTRAINT uk_users_account_email UNIQUE (account_id, email)
);

-- Indexes for Users table
CREATE INDEX IF NOT EXISTS idx_users_account_id ON users(account_id);
CREATE INDEX IF NOT EXISTS idx_users_account_email ON users(account_id, email);
CREATE INDEX IF NOT EXISTS idx_users_account_active ON users(account_id, active);
CREATE INDEX IF NOT EXISTS idx_users_role_id ON users(account_id, role_id);

-- Roles table (sharded by account_id)
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL, -- Tenant isolation field
    name VARCHAR(100) NOT NULL,
    permissions_mask BIGINT NOT NULL DEFAULT 0,
    is_system_role BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT false,

    -- Ensure role name is unique within each tenant
    CONSTRAINT uk_roles_account_name UNIQUE (account_id, name)
);

-- Indexes for Roles table
CREATE INDEX IF NOT EXISTS idx_roles_account_id ON roles(account_id);
CREATE INDEX IF NOT EXISTS idx_roles_account_name ON roles(account_id, name);
CREATE INDEX IF NOT EXISTS idx_roles_system ON roles(account_id, is_system_role);

-- Status table (sharded by account_id)
CREATE TABLE IF NOT EXISTS status (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL, -- Tenant isolation field
    name VARCHAR(100) NOT NULL,
    description TEXT,
    color VARCHAR(7), -- Hex color code
    is_default BOOLEAN DEFAULT false,
    is_closed BOOLEAN DEFAULT false,
    position INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT false,

    -- Ensure status name is unique within each tenant
    CONSTRAINT uk_status_account_name UNIQUE (account_id, name)
);

-- Indexes for Status table
CREATE INDEX IF NOT EXISTS idx_status_account_id ON status(account_id);
CREATE INDEX IF NOT EXISTS idx_status_account_name ON status(account_id, name);
CREATE INDEX IF NOT EXISTS idx_status_account_position ON status(account_id, position);
CREATE INDEX IF NOT EXISTS idx_status_default ON status(account_id, is_default);
CREATE INDEX IF NOT EXISTS idx_status_closed ON status(account_id, is_closed);

-- Tickets table (sharded by account_id) - NO FOREIGN KEYS
CREATE TABLE IF NOT EXISTS tickets (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL, -- Tenant isolation field
    subject VARCHAR(255) NOT NULL,
    description TEXT,

    -- User references (by ID only, no FK constraints)
    requester_id BIGINT NOT NULL,
    responder_id BIGINT NULL,

    -- Status reference (by ID only, no FK constraint)
    status_id BIGINT NOT NULL,

    -- Categorization
    priority VARCHAR(20) DEFAULT 'MEDIUM', -- LOW, MEDIUM, HIGH, URGENT
    category VARCHAR(100),
    subcategory VARCHAR(100),

    -- Tracking fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    deleted BOOLEAN DEFAULT false
);

-- Indexes for Tickets table
CREATE INDEX IF NOT EXISTS idx_tickets_account_id ON tickets(account_id);
CREATE INDEX IF NOT EXISTS idx_tickets_requester ON tickets(account_id, requester_id);
CREATE INDEX IF NOT EXISTS idx_tickets_responder ON tickets(account_id, responder_id);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(account_id, status_id);
CREATE INDEX IF NOT EXISTS idx_tickets_priority ON tickets(account_id, priority);
CREATE INDEX IF NOT EXISTS idx_tickets_category ON tickets(account_id, category);
CREATE INDEX IF NOT EXISTS idx_tickets_created_at ON tickets(account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tickets_updated_at ON tickets(account_id, updated_at DESC);

-- ===========================
-- Sample Data for Development
-- ===========================

-- Switch back to global database for account creation
\c global_db;

-- Insert sample account
INSERT INTO accounts (name, admin_email) VALUES
    ('Demo Company', 'admin@demo.com')
ON CONFLICT (admin_email) DO NOTHING;

-- Get the account ID for mapping (assuming it's 1 for first insert)
-- In development, all tenants map to shard1
INSERT INTO tenant_shard_mapping (tenant_id, shard_id, region, shard_status) VALUES
    (1, 'shard1', 'us-east-1', 'ACTIVE')
ON CONFLICT (tenant_id) DO UPDATE SET
    shard_id = EXCLUDED.shard_id,
    region = EXCLUDED.region,
    shard_status = EXCLUDED.shard_status;

-- Switch to shard database for sample data
\c shard1_db;

-- Permission bitmasks (will be defined in Java code)
-- For reference:
-- ADMIN: Full permissions (all bits set)
-- AGENT: Ticket management + limited user viewing
-- REQUESTER: Basic ticket creation and viewing own tickets

-- Insert default roles for account_id = 1
INSERT INTO roles (account_id, name, permissions_mask, is_system_role) VALUES
    (1, 'ADMIN', 9223372036854775807, true),    -- Max BIGINT (all permissions)
    (1, 'AGENT', 2080374784, true),             -- Ticket management permissions
    (1, 'REQUESTER', 17408, true)               -- Basic permissions
ON CONFLICT (account_id, name) DO UPDATE SET
    permissions_mask = EXCLUDED.permissions_mask,
    is_system_role = EXCLUDED.is_system_role;

-- Insert default statuses for account_id = 1
INSERT INTO status (account_id, name, description, color, is_default, is_closed, position) VALUES
    (1, 'Open', 'Newly created ticket', '#28a745', true, false, 1),
    (1, 'In Progress', 'Ticket is being worked on', '#ffc107', false, false, 2),
    (1, 'Pending', 'Waiting for customer response', '#fd7e14', false, false, 3),
    (1, 'Resolved', 'Issue has been resolved', '#6f42c1', false, true, 4),
    (1, 'Closed', 'Ticket is closed', '#6c757d', false, true, 5)
ON CONFLICT (account_id, name) DO UPDATE SET
    description = EXCLUDED.description,
    color = EXCLUDED.color,
    is_default = EXCLUDED.is_default,
    is_closed = EXCLUDED.is_closed,
    position = EXCLUDED.position;

-- Insert sample users for account_id = 1
-- Password hash is for 'password123' using BCrypt
INSERT INTO users (account_id, email, password_hash, first_name, last_name, role_id, active) VALUES
    (1, 'admin@demo.com', '$2a$10$7EqJtq98hPqEX7fNZaFWoOYqr5xO9tTmzeCJ8z8nR8tTmzeCJ8z8n', 'Admin', 'User', 1, true),
    (1, 'agent@demo.com', '$2a$10$7EqJtq98hPqEX7fNZaFWoOYqr5xO9tTmzeCJ8z8nR8tTmzeCJ8z8n', 'Agent', 'User', 2, true),
    (1, 'andrea@example.com', '$2a$10$7EqJtq98hPqEX7fNZaFWoOYqr5xO9tTmzeCJ8z8nR8tTmzeCJ8z8n', 'Andrea', 'Sample', 3, true)
ON CONFLICT (account_id, email) DO UPDATE SET
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    role_id = EXCLUDED.role_id;

-- Insert sample tickets for account_id = 1
INSERT INTO tickets (account_id, subject, description, requester_id, responder_id, status_id, priority, category, subcategory) VALUES
    (1, 'Login Issues', 'Cannot login to the system with my credentials', 3, 2, 1, 'HIGH', 'Technical', 'Authentication'),
    (1, 'Feature Request: Dark Mode', 'Would love to have a dark mode option in the application', 3, NULL, 1, 'MEDIUM', 'Enhancement', 'UI/UX'),
    (1, 'Bug: Dashboard Loading Slow', 'The dashboard takes too long to load, especially in the morning', 3, 2, 2, 'LOW', 'Performance', 'Speed'),
    (1, 'Email Notifications Not Working', 'Not receiving email notifications for ticket updates', 3, NULL, 1, 'MEDIUM', 'Technical', 'Notifications'),
    (1, 'Add Export Feature', 'Need ability to export ticket data to CSV format', 3, 2, 4, 'LOW', 'Enhancement', 'Reporting')
ON CONFLICT DO NOTHING;

-- ===========================
-- Verification Queries
-- ===========================

-- Verify global database setup
\c global_db;
SELECT 'Global Database Verification' as status;
SELECT 'Accounts: ' || COUNT(*) as result FROM accounts;
SELECT 'Tenant Mappings: ' || COUNT(*) as result FROM tenant_shard_mapping;
SELECT 'Global Configs: ' || COUNT(*) as result FROM global_config;

-- Verify shard database setup
\c shard1_db;
SELECT 'Shard Database Verification' as status;
SELECT 'Users: ' || COUNT(*) as result FROM users;
SELECT 'Roles: ' || COUNT(*) as result FROM roles;
SELECT 'Statuses: ' || COUNT(*) as result FROM status;
SELECT 'Tickets: ' || COUNT(*) as result FROM tickets;

-- Show sample data summary
SELECT
    'Account 1 Summary' as description,
    (SELECT COUNT(*) FROM users WHERE account_id = 1) as users,
    (SELECT COUNT(*) FROM roles WHERE account_id = 1) as roles,
    (SELECT COUNT(*) FROM status WHERE account_id = 1) as statuses,
    (SELECT COUNT(*) FROM tickets WHERE account_id = 1) as tickets;

-- Success message
SELECT 'Database setup completed successfully!' as message;
SELECT 'Run application with: mvn spring-boot:run -Dspring-boot.run.profiles=dev' as instructions;