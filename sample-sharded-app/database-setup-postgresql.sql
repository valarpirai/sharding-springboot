-- PostgreSQL Database Setup Script for Sample Sharded Application
-- This script sets up the databases and sample data for testing the sharding library

-- ===========================
-- Global Database Setup
-- ===========================

-- Create global database
CREATE DATABASE global_db;

-- Connect to global database
\c global_db;

-- Create tenant_shard_mapping table (automatically created by the library)
-- This is shown for reference only
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
(2002, 'shard2', 'us-west-2', 'ACTIVE'),
(3001, 'shard1', 'us-east-1', 'ACTIVE'),
(3002, 'shard2', 'us-west-2', 'ACTIVE')
ON CONFLICT (tenant_id) DO NOTHING;

-- Insert global configuration
INSERT INTO global_config (config_key, config_value, description) VALUES
('max_customers_per_tenant', '1000', 'Maximum customers per tenant'),
('default_region', 'us-east-1', 'Default region for new tenants'),
('feature_flags.enable_advanced_search', 'true', 'Enable advanced search features'),
('rate_limits.api_requests_per_minute', '1000', 'API rate limit per tenant')
ON CONFLICT (config_key) DO UPDATE SET
    config_value = EXCLUDED.config_value,
    description = EXCLUDED.description;

-- ===========================
-- Shard 1 Database Setup
-- ===========================

-- Create shard1 database
CREATE DATABASE shard1_db;

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
CREATE INDEX idx_customers_tenant_name ON customers (tenant_id, name);

-- Orders table (sharded entity)
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    order_number VARCHAR(100) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT unique_tenant_order_number UNIQUE (tenant_id, order_number),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
);

-- Indexes for orders
CREATE INDEX idx_orders_tenant_id ON orders (tenant_id);
CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_status ON orders (tenant_id, status);
CREATE INDEX idx_orders_created_at ON orders (created_at);

-- Insert sample data for Shard 1
INSERT INTO customers (tenant_id, name, email, phone, address, city, country) VALUES
-- Tenant 1001
(1001, 'John Doe', 'john.doe@example.com', '+1-555-0101', '123 Main St', 'New York', 'USA'),
(1001, 'Jane Smith', 'jane.smith@example.com', '+1-555-0102', '456 Oak Ave', 'New York', 'USA'),
(1001, 'Bob Johnson', 'bob.johnson@example.com', '+1-555-0103', '789 Pine St', 'New York', 'USA'),

-- Tenant 1002
(1002, 'Alice Brown', 'alice.brown@company.com', '+1-555-0201', '321 Elm St', 'Boston', 'USA'),
(1002, 'Charlie Wilson', 'charlie.wilson@company.com', '+1-555-0202', '654 Maple Ave', 'Boston', 'USA'),

-- Tenant 1003
(1003, 'David Lee', 'david.lee@startup.com', '+1-555-0301', '987 Cedar Blvd', 'San Francisco', 'USA'),
(1003, 'Eva Martinez', 'eva.martinez@startup.com', '+1-555-0302', '147 Birch Lane', 'San Francisco', 'USA'),
(1003, 'Frank Taylor', 'frank.taylor@startup.com', '+1-555-0303', '258 Spruce Way', 'San Francisco', 'USA'),

-- Tenant 3001
(3001, 'Grace Chen', 'grace.chen@enterprise.com', '+1-555-0401', '369 Willow Dr', 'Seattle', 'USA'),
(3001, 'Henry Kim', 'henry.kim@enterprise.com', '+1-555-0402', '741 Aspen St', 'Seattle', 'USA');

-- Insert sample orders
INSERT INTO orders (tenant_id, customer_id, order_number, total_amount, status) VALUES
-- Tenant 1001 orders
(1001, 1, 'ORD-1001-001', 299.99, 'COMPLETED'),
(1001, 1, 'ORD-1001-002', 149.50, 'PENDING'),
(1001, 2, 'ORD-1001-003', 89.99, 'SHIPPED'),
(1001, 3, 'ORD-1001-004', 499.99, 'PROCESSING'),

-- Tenant 1002 orders
(1002, 4, 'ORD-1002-001', 1299.99, 'COMPLETED'),
(1002, 5, 'ORD-1002-002', 799.50, 'SHIPPED'),

-- Tenant 1003 orders
(1003, 6, 'ORD-1003-001', 199.99, 'PENDING'),
(1003, 7, 'ORD-1003-002', 349.99, 'COMPLETED'),
(1003, 8, 'ORD-1003-003', 89.99, 'CANCELLED'),

-- Tenant 3001 orders
(3001, 9, 'ORD-3001-001', 2999.99, 'COMPLETED'),
(3001, 10, 'ORD-3001-002', 1599.50, 'PROCESSING');

-- ===========================
-- Shard 2 Database Setup
-- ===========================

-- Create shard2 database
CREATE DATABASE shard2_db;

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
CREATE INDEX idx_customers_tenant_name ON customers (tenant_id, name);

-- Orders table
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    order_number VARCHAR(100) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT unique_tenant_order_number UNIQUE (tenant_id, order_number),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
);

-- Indexes for orders
CREATE INDEX idx_orders_tenant_id ON orders (tenant_id);
CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_status ON orders (tenant_id, status);
CREATE INDEX idx_orders_created_at ON orders (created_at);

-- Insert sample data for Shard 2
INSERT INTO customers (tenant_id, name, email, phone, address, city, country) VALUES
-- Tenant 2001
(2001, 'Isabel Rodriguez', 'isabel.rodriguez@west-corp.com', '+1-555-0501', '456 Pacific Ave', 'Los Angeles', 'USA'),
(2001, 'James Anderson', 'james.anderson@west-corp.com', '+1-555-0502', '789 Sunset Blvd', 'Los Angeles', 'USA'),
(2001, 'Karen Thompson', 'karen.thompson@west-corp.com', '+1-555-0503', '321 Hollywood Way', 'Los Angeles', 'USA'),

-- Tenant 2002
(2002, 'Luis Garcia', 'luis.garcia@tech-west.com', '+1-555-0601', '654 Innovation Dr', 'San Jose', 'USA'),
(2002, 'Maria Gonzalez', 'maria.gonzalez@tech-west.com', '+1-555-0602', '987 Silicon Valley Blvd', 'San Jose', 'USA'),

-- Tenant 3002
(3002, 'Nathan White', 'nathan.white@global-inc.com', '+1-555-0701', '147 Business Park', 'Portland', 'USA'),
(3002, 'Olivia Davis', 'olivia.davis@global-inc.com', '+1-555-0702', '258 Commerce St', 'Portland', 'USA');

-- Insert sample orders for Shard 2
INSERT INTO orders (tenant_id, customer_id, order_number, total_amount, status) VALUES
-- Tenant 2001 orders
(2001, 1, 'ORD-2001-001', 599.99, 'COMPLETED'),
(2001, 2, 'ORD-2001-002', 399.50, 'SHIPPED'),
(2001, 3, 'ORD-2001-003', 799.99, 'PROCESSING'),

-- Tenant 2002 orders
(2002, 4, 'ORD-2002-001', 1999.99, 'COMPLETED'),
(2002, 5, 'ORD-2002-002', 899.50, 'PENDING'),

-- Tenant 3002 orders
(3002, 6, 'ORD-3002-001', 3499.99, 'COMPLETED'),
(3002, 7, 'ORD-3002-002', 1299.99, 'SHIPPED');

-- ===========================
-- Verification Queries
-- ===========================

-- Connect back to global database to verify setup
\c global_db;

SELECT 'Global Database Setup Verification' as status;
SELECT COUNT(*) as tenant_mappings FROM tenant_shard_mapping;
SELECT COUNT(*) as global_configs FROM global_config;

-- Verify shard1 data
\c shard1_db;
SELECT 'Shard 1 Setup Verification' as status;
SELECT tenant_id, COUNT(*) as customer_count FROM customers GROUP BY tenant_id ORDER BY tenant_id;
SELECT tenant_id, COUNT(*) as order_count FROM orders GROUP BY tenant_id ORDER BY tenant_id;

-- Verify shard2 data
\c shard2_db;
SELECT 'Shard 2 Setup Verification' as status;
SELECT tenant_id, COUNT(*) as customer_count FROM customers GROUP BY tenant_id ORDER BY tenant_id;
SELECT tenant_id, COUNT(*) as order_count FROM orders GROUP BY tenant_id ORDER BY tenant_id;

-- Success message
SELECT 'PostgreSQL Database setup completed successfully!' as message;
SELECT 'You can now run the sample application with: java -jar sample-sharded-app.jar --spring.profiles.active=postgresql' as instructions;