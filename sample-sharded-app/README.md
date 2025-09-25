# Sample Sharded Application

This is a demonstration Spring Boot application showcasing the Galaxy Sharding library with multi-tenancy and database sharding capabilities.

## Features Demonstrated

- **Multi-tenant Architecture**: Each tenant's data is isolated in separate shards
- **Database Sharding**: Distributes tenant data across multiple databases
- **Read/Write Splitting**: Master-replica configuration for each shard
- **Entity Classification**: Sharded vs non-sharded entities
- **Tenant Context Management**: Thread-local tenant information
- **Query Validation**: Ensures tenant isolation at the SQL level

## Entities

### Sharded Entity
- **Customer**: Marked with `@ShardedEntity`, stored in tenant-specific shards
  - Must include `tenant_id` in all queries
  - Automatically routed to appropriate shard based on tenant context

### Non-Sharded Entity
- **GlobalConfig**: Not marked, stored in global database
  - Shared across all tenants
  - No tenant isolation required

## API Endpoints

### Customer Operations
```http
# Set tenant context (required before other operations)
POST /api/customers/set-tenant/{tenantId}

# Create customer
POST /api/customers
Content-Type: application/json
{
  "name": "John Doe",
  "email": "john@example.com",
  "phone": "123-456-7890"
}

# Get all customers for current tenant
GET /api/customers

# Get specific customer
GET /api/customers/{id}

# Update customer
PUT /api/customers/{id}
Content-Type: application/json
{
  "name": "John Smith",
  "email": "johnsmith@example.com"
}

# Delete customer
DELETE /api/customers/{id}

# Clear tenant context
POST /api/customers/clear-tenant
```

## Configuration

The application uses the following configuration structure:

```properties
# Global Database (tenant_shard_mapping)
app.sharding.global-db.url=jdbc:mysql://localhost:3306/global_db
app.sharding.global-db.username=global_user
app.sharding.global-db.password=global_password

# Shard 1 (Latest shard for new signups)
app.sharding.shard1.master.url=jdbc:mysql://localhost:3306/shard1_db
app.sharding.shard1.replica1.url=jdbc:mysql://localhost:3307/shard1_db
app.sharding.shard1.latest=true

# Shard 2
app.sharding.shard2.master.url=jdbc:mysql://localhost:3309/shard2_db
app.sharding.shard2.replica1.url=jdbc:mysql://localhost:3310/shard2_db

# Validation
app.sharding.validation.strictness=STRICT
app.sharding.tenant-column-names=tenant_id,company_id
```

## Database Setup

### 1. Global Database
```sql
CREATE DATABASE global_db;

CREATE TABLE tenant_shard_mapping (
    tenant_id BIGINT NOT NULL,
    shard_id VARCHAR(255) NOT NULL,
    region VARCHAR(255),
    shard_status VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
);

CREATE TABLE global_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) UNIQUE NOT NULL,
    config_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 2. Shard Databases
```sql
-- Create for each shard (shard1_db, shard2_db, etc.)
CREATE DATABASE shard1_db;

CREATE TABLE customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id)
);
```

### 3. Sample Data
```sql
-- Insert tenant-shard mappings
INSERT INTO global_db.tenant_shard_mapping (tenant_id, shard_id, region, shard_status) VALUES
(1001, 'shard1', 'us-east-1', 'ACTIVE'),
(1002, 'shard1', 'us-east-1', 'ACTIVE'),
(1003, 'shard2', 'us-west-2', 'ACTIVE');

-- Insert global configuration
INSERT INTO global_db.global_config (config_key, config_value, description) VALUES
('max_customers_per_tenant', '1000', 'Maximum customers allowed per tenant'),
('default_region', 'us-east-1', 'Default region for new signups');
```

## Running the Application

1. **Start MySQL databases** with the configured ports and databases
2. **Run the application**:
   ```bash
   mvn spring-boot:run
   ```
3. **Test the endpoints** using the provided API examples

## Testing Multi-Tenancy

```bash
# Set tenant context to 1001
curl -X POST http://localhost:8080/api/customers/set-tenant/1001

# Create customer for tenant 1001
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice Johnson","email":"alice@tenant1.com"}'

# Switch to tenant 1002
curl -X POST http://localhost:8080/api/customers/set-tenant/1002

# Create customer for tenant 1002
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"Bob Smith","email":"bob@tenant2.com"}'

# Verify isolation - get customers for tenant 1001
curl -X POST http://localhost:8080/api/customers/set-tenant/1001
curl http://localhost:8080/api/customers
# Should only return Alice Johnson
```

## Key Features in Action

1. **Automatic Routing**: Customers are automatically routed to correct shard based on tenant_id
2. **Tenant Isolation**: Each tenant only sees their own data
3. **Latest Shard**: New tenant signups use the shard marked as `latest=true`
4. **Query Validation**: SQL queries without tenant_id are rejected (configurable)
5. **Read/Write Splitting**: Reads can be distributed across replicas

## Next Steps

- Implement JWT-based tenant identification instead of manual context setting
- Add tenant onboarding workflow that assigns new tenants to latest shard
- Implement background jobs using tenant iterator for cross-tenant operations
- Add monitoring and metrics for shard utilization