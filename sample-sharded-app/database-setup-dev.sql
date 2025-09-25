-- PostgreSQL Database Setup Script - Development Mode
-- This script sets up databases using the postgres superuser (simpler for development)
-- Run as: psql -U postgres -f database-setup-dev.sql

-- ===========================
-- Create Databases Only
-- ===========================

-- Drop databases if they exist (for clean setup)
DROP DATABASE IF EXISTS global_db;
DROP DATABASE IF EXISTS shard1_db;
DROP DATABASE IF EXISTS shard2_db;

-- Create databases
CREATE DATABASE global_db;
CREATE DATABASE shard1_db;
CREATE DATABASE shard2_db;

-- ===========================
-- Global Database Setup
-- ===========================

-- Connect to global database
\c global_db;

-- Create tenant_shard_mapping table
CREATE TABLE IF NOT EXISTS tenant_shard_mapping (
    tenant_id BIGINT NOT NULL,
    shard_id VARCHAR(255) NOT NULL,
    region VARCHAR(255),
    shard_status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_shard_id ON tenant_shard_mapping (shard_id);
CREATE INDEX IF NOT EXISTS idx_shard_status ON tenant_shard_mapping (shard_status);
CREATE INDEX IF NOT EXISTS idx_region ON tenant_shard_mapping (region);

-- Global configuration table (non-sharded)
CREATE TABLE global_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(255) UNIQUE NOT NULL,
    config_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert sample tenant-shard mappings
INSERT INTO tenant_shard_mapping (tenant_id, shard_id, region, shard_status) VALUES
(1001, 'shard1', 'us-east-1', 'ACTIVE'),
(1002, 'shard1', 'us-east-1', 'ACTIVE'),
(1003, 'shard1', 'us-east-1', 'ACTIVE'),
(2001, 'shard2', 'us-west-2', 'ACTIVE'),
(2002, 'shard2', 'us-west-2', 'ACTIVE')
ON CONFLICT (tenant_id) DO NOTHING;

-- Insert global configuration
INSERT INTO global_config (config_key, config_value, description) VALUES
('max_customers_per_tenant', '1000', 'Maximum customers per tenant'),
('default_region', 'us-east-1', 'Default region for new tenants')
ON CONFLICT (config_key) DO UPDATE SET
    config_value = EXCLUDED.config_value,
    description = EXCLUDED.description;

-- ===========================
-- Shard 1 Database Setup
-- ===========================

-- Connect to shard1 database
\c shard1_db;

-- Customers table (sharded entity)
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    address TEXT,
    city VARCHAR(100),
    country VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT unique_tenant_email UNIQUE (tenant_id, email)
);

-- Indexes for better query performance
CREATE INDEX idx_customers_tenant_id ON customers (tenant_id);
CREATE INDEX idx_customers_email ON customers (email);
CREATE INDEX idx_customers_created_at ON customers (created_at);

-- Insert sample data for Shard 1
INSERT INTO customers (tenant_id, name, email, phone, address, city, country) VALUES
-- Tenant 1001
(1001, 'John Doe', 'john.doe@example.com', '+1-555-0101', '123 Main St', 'New York', 'USA'),
(1001, 'Jane Smith', 'jane.smith@example.com', '+1-555-0102', '456 Oak Ave', 'New York', 'USA'),

-- Tenant 1002
(1002, 'Alice Brown', 'alice.brown@company.com', '+1-555-0201', '321 Elm St', 'Boston', 'USA'),
(1002, 'Charlie Wilson', 'charlie.wilson@company.com', '+1-555-0202', '654 Maple Ave', 'Boston', 'USA'),

-- Tenant 1003
(1003, 'David Lee', 'david.lee@startup.com', '+1-555-0301', '987 Cedar Blvd', 'San Francisco', 'USA');

-- ===========================
-- Shard 2 Database Setup
-- ===========================

-- Connect to shard2 database
\c shard2_db;

-- Create the same schema as shard1
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    address TEXT,
    city VARCHAR(100),
    country VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT unique_tenant_email UNIQUE (tenant_id, email)
);

-- Indexes for better query performance
CREATE INDEX idx_customers_tenant_id ON customers (tenant_id);
CREATE INDEX idx_customers_email ON customers (email);
CREATE INDEX idx_customers_created_at ON customers (created_at);

-- Insert sample data for Shard 2
INSERT INTO customers (tenant_id, name, email, phone, address, city, country) VALUES
-- Tenant 2001
(2001, 'Isabel Rodriguez', 'isabel.rodriguez@west-corp.com', '+1-555-0501', '456 Pacific Ave', 'Los Angeles', 'USA'),
(2001, 'James Anderson', 'james.anderson@west-corp.com', '+1-555-0502', '789 Sunset Blvd', 'Los Angeles', 'USA'),

-- Tenant 2002
(2002, 'Luis Garcia', 'luis.garcia@tech-west.com', '+1-555-0601', '654 Innovation Dr', 'San Jose', 'USA'),
(2002, 'Maria Gonzalez', 'maria.gonzalez@tech-west.com', '+1-555-0602', '987 Silicon Valley Blvd', 'San Jose', 'USA');

-- ===========================
-- Verification
-- ===========================

-- Connect back to global database to verify setup
\c global_db;

SELECT 'Development Database Setup Verification' as status;
SELECT COUNT(*) as tenant_mappings FROM tenant_shard_mapping;
SELECT COUNT(*) as global_configs FROM global_config;

-- Verify shard1 data
\c shard1_db;
SELECT 'Shard 1 Data:' as status;
SELECT tenant_id, COUNT(*) as customer_count FROM customers GROUP BY tenant_id ORDER BY tenant_id;

-- Verify shard2 data
\c shard2_db;
SELECT 'Shard 2 Data:' as status;
SELECT tenant_id, COUNT(*) as customer_count FROM customers GROUP BY tenant_id ORDER BY tenant_id;

-- Success message
SELECT 'PostgreSQL Development setup completed successfully!' as message;
SELECT 'Run the application with: mvn spring-boot:run -Dspring-boot.run.profiles=dev' as instructions;