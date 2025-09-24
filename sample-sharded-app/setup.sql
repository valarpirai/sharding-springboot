-- Galaxy Sharding Demo Database Setup Script
-- Run this script to set up the databases for the sample application

-- ========================================
-- 1. Create Databases
-- ========================================

CREATE DATABASE IF NOT EXISTS global_db;
CREATE DATABASE IF NOT EXISTS shard1_db;
CREATE DATABASE IF NOT EXISTS shard2_db;

-- ========================================
-- 2. Global Database Schema
-- ========================================

USE global_db;

-- Tenant to shard mapping table (required by sharding library)
CREATE TABLE IF NOT EXISTS tenant_shard_mapping (
    tenant_id BIGINT NOT NULL,
    shard_id VARCHAR(255) NOT NULL,
    region VARCHAR(255),
    shard_status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id),
    INDEX idx_shard_id (shard_id),
    INDEX idx_shard_status (shard_status)
);

-- Global configuration table (non-sharded entity example)
CREATE TABLE IF NOT EXISTS global_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) UNIQUE NOT NULL,
    config_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ========================================
-- 3. Shard Database Schemas
-- ========================================

-- Shard 1 Schema
USE shard1_db;

CREATE TABLE IF NOT EXISTS customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_email (email),
    UNIQUE KEY unique_tenant_email (tenant_id, email)
);

-- Shard 2 Schema
USE shard2_db;

CREATE TABLE IF NOT EXISTS customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_email (email),
    UNIQUE KEY unique_tenant_email (tenant_id, email)
);

-- ========================================
-- 4. Sample Data
-- ========================================

-- Insert tenant to shard mappings
USE global_db;

INSERT IGNORE INTO tenant_shard_mapping (tenant_id, shard_id, region, shard_status) VALUES
(1001, 'shard1', 'us-east-1', 'ACTIVE'),
(1002, 'shard1', 'us-east-1', 'ACTIVE'),
(1003, 'shard2', 'us-west-2', 'ACTIVE'),
(1004, 'shard2', 'us-west-2', 'ACTIVE'),
(1005, 'shard1', 'us-east-1', 'ACTIVE');

-- Insert global configuration
INSERT IGNORE INTO global_config (config_key, config_value, description) VALUES
('max_customers_per_tenant', '1000', 'Maximum customers allowed per tenant'),
('default_region', 'us-east-1', 'Default region for new tenant signups'),
('read_replica_weight', '70', 'Percentage of read traffic to route to replicas'),
('connection_pool_size', '20', 'Default connection pool size per shard'),
('query_timeout', '30000', 'Default query timeout in milliseconds');

-- ========================================
-- 5. Sample Customer Data
-- ========================================

-- Customers in Shard 1
USE shard1_db;

INSERT IGNORE INTO customers (tenant_id, name, email, phone) VALUES
(1001, 'Alice Johnson', 'alice@tenant1001.com', '+1-555-0101'),
(1001, 'Bob Wilson', 'bob@tenant1001.com', '+1-555-0102'),
(1002, 'Charlie Brown', 'charlie@tenant1002.com', '+1-555-0201'),
(1002, 'Diana Prince', 'diana@tenant1002.com', '+1-555-0202'),
(1005, 'Eve Adams', 'eve@tenant1005.com', '+1-555-0501');

-- Customers in Shard 2
USE shard2_db;

INSERT IGNORE INTO customers (tenant_id, name, email, phone) VALUES
(1003, 'Frank Miller', 'frank@tenant1003.com', '+1-555-0301'),
(1003, 'Grace Lee', 'grace@tenant1003.com', '+1-555-0302'),
(1004, 'Henry Ford', 'henry@tenant1004.com', '+1-555-0401'),
(1004, 'Ivy Chen', 'ivy@tenant1004.com', '+1-555-0402');

-- ========================================
-- 6. Verify Setup
-- ========================================

-- Show tenant distribution
SELECT
    shard_id,
    region,
    COUNT(*) as tenant_count,
    GROUP_CONCAT(tenant_id ORDER BY tenant_id) as tenant_ids
FROM global_db.tenant_shard_mapping
GROUP BY shard_id, region;

-- Show customer counts per shard
SELECT 'shard1' as shard, tenant_id, COUNT(*) as customer_count
FROM shard1_db.customers
GROUP BY tenant_id
UNION ALL
SELECT 'shard2' as shard, tenant_id, COUNT(*) as customer_count
FROM shard2_db.customers
GROUP BY tenant_id
ORDER BY shard, tenant_id;

-- Show global config
SELECT * FROM global_db.global_config;

COMMIT;