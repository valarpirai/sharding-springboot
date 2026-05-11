# Database Setup Guide

Complete guide for setting up the PostgreSQL databases for the Multi-Tenant Ticket Management System.

## Quick Setup (Recommended)

### Prerequisites
- PostgreSQL installed and running
- `postgres` superuser access
- Java 11+ and Maven installed

### 1-Step Setup
```bash
# 1. Run the complete setup script
psql -U postgres -f database-setup.sql

# 2. Start the application
mvn spring-boot:run

# 3. Access Swagger documentation
open http://localhost:8080/swagger-ui/
```

This creates everything you need for development and testing.

## What Gets Created

### Databases
- **`global_db`**: Stores accounts and tenant-shard mappings
- **`shard1_db`**: Contains all tenant data (users, tickets, roles, statuses)

### Global Database Tables
```sql
-- Tenant/Account information
accounts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    admin_email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT false
);

-- Tenant-to-shard routing information
tenant_shard_mapping (
    tenant_id BIGINT NOT NULL,
    shard_id VARCHAR(255) NOT NULL,
    region VARCHAR(255),
    shard_status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
);
```

### Shard Database Tables
```sql
-- Users within each tenant
users (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role_id BIGINT NOT NULL,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT false
);

-- Support tickets
tickets (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    subject VARCHAR(500) NOT NULL,
    description TEXT,
    requester_id BIGINT NOT NULL,
    responder_id BIGINT,
    status_id BIGINT NOT NULL,
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    category VARCHAR(100),
    subcategory VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    deleted BOOLEAN DEFAULT false
);

-- User roles with permission bitmasks
roles (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    permissions_mask BIGINT NOT NULL DEFAULT 0,
    is_system_role BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT false
);

-- Ticket statuses
statuses (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(20) DEFAULT '#6B7280',
    position INTEGER DEFAULT 0,
    is_default BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT false
);
```

## Sample Data Included

The setup script automatically creates a demo environment:

### Demo Account
- **Account**: "Demo Company" (ID: 1)
- **Admin User**: admin@demo.com / password123
- **Tenant-Shard Mapping**: Account 1 → shard1_db

### Demo Users
1. **Admin User**: admin@demo.com (ADMIN role)
2. **Agent User**: agent@demo.com (AGENT role)
3. **Requester User**: andrea@example.com (REQUESTER role)

### System Roles with Permissions
1. **ADMIN**: Full system access (permissions mask: 4398046511103)
2. **AGENT**: Ticket management, user viewing (permissions mask: 1099511676415)
3. **REQUESTER**: Create/view own tickets (permissions mask: 17179869183)

### Ticket Statuses
1. **Open** (Blue, #3B82F6) - Default for new tickets
2. **In Progress** (Yellow, #F59E0B)
3. **Resolved** (Green, #10B981)
4. **Closed** (Gray, #6B7280)
5. **On Hold** (Red, #EF4444)

### Sample Tickets
- 5 demo tickets with different priorities, statuses, and categories
- Assigned to various users for realistic testing

## Application Configuration

The application is configured to use these databases:

```properties
# Global Database Connection
spring.datasource.url=jdbc:postgresql://localhost:5432/global_db
spring.datasource.username=postgres
spring.datasource.password=

# Sharding Configuration
app.sharding.global-db.url=jdbc:postgresql://localhost:5432/global_db
app.sharding.global-db.username=postgres
app.sharding.global-db.password=

# Shard Configuration
app.sharding.shards.shard1.master.url=jdbc:postgresql://localhost:5432/shard1_db
app.sharding.shards.shard1.master.username=postgres
app.sharding.shards.shard1.master.password=
app.sharding.shards.shard1.latest=true
```

## Testing the Setup

### 1. Verify Database Creation
```sql
-- Check databases exist
\l

-- Check global tables
\c global_db
\dt

-- Check shard tables
\c shard1_db
\dt
```

### 2. Verify Sample Data
```sql
-- Check demo account
SELECT * FROM global_db.accounts;

-- Check tenant mapping
SELECT * FROM global_db.tenant_shard_mapping;

-- Check demo users
SELECT * FROM shard1_db.users;

-- Check demo tickets
SELECT * FROM shard1_db.tickets;
```

### 3. Test API Endpoints
```bash
# Test signup service health
curl http://localhost:8080/api/signup/health

# Test authentication
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "account-id: 1" \
  -d '{"email":"admin@demo.com","password":"password123"}'

# Test ticket retrieval (use JWT from login response)
curl -X GET http://localhost:8080/api/tickets \
  -H "account-id: 1" \
  -H "Authorization: Bearer <jwt_token>"
```

## Development Customization

### Adding New Shards
To add more shards for scaling:

1. **Create new shard database**:
   ```sql
   CREATE DATABASE shard2_db;
   ```

2. **Run shard schema setup**:
   ```sql
   \c shard2_db
   -- Copy the shard table creation statements from database-setup.sql
   ```

3. **Update application.properties**:
   ```properties
   app.sharding.shards.shard2.master.url=jdbc:postgresql://localhost:5432/shard2_db
   app.sharding.shards.shard2.master.username=postgres
   app.sharding.shards.shard2.master.password=
   app.sharding.shards.shard2.latest=true  # Make this the new latest shard
   app.sharding.shards.shard1.latest=false # Remove latest from old shard
   ```

### Custom Sample Data
Modify the INSERT statements in `database-setup.sql` to create your own demo accounts and users.

### Different PostgreSQL Setup
For production or different local setups:

1. Update connection strings in the SQL script
2. Modify application.properties accordingly
3. Ensure PostgreSQL is accessible on the specified hosts/ports

## Troubleshooting

### PostgreSQL Not Running
```bash
# macOS with Homebrew
brew services start postgresql

# Ubuntu/Debian
sudo systemctl start postgresql
sudo systemctl enable postgresql

# Check status
brew services list | grep postgresql  # macOS
sudo systemctl status postgresql       # Ubuntu
```

### Connection Issues
```bash
# Test PostgreSQL connection
psql -U postgres -h localhost -p 5432 -c "SELECT version();"

# Check if databases exist
psql -U postgres -l
```

### Permission Denied
If you get permission errors:

```bash
# Make sure you're running as a user with PostgreSQL access
sudo -u postgres psql -f database-setup.sql

# Or create a superuser for your system user
sudo -u postgres createuser --superuser $USER
```

### Port Conflicts
If port 5432 is in use:

1. Change PostgreSQL to use a different port
2. Update all connection strings in the script and application.properties
3. Or stop the conflicting service

### Schema Already Exists
If you need to reset everything:

```sql
-- Connect as postgres superuser
DROP DATABASE IF EXISTS global_db CASCADE;
DROP DATABASE IF EXISTS shard1_db CASCADE;

-- Then re-run the setup script
\i database-setup.sql
```

## Advanced Configuration

### Read Replicas
For production, you can configure read replicas:

```properties
# Shard 1 with replicas
app.sharding.shards.shard1.master.url=jdbc:postgresql://master1:5432/shard1_db
app.sharding.shards.shard1.replica1.url=jdbc:postgresql://replica1:5432/shard1_db
app.sharding.shards.shard1.replica2.url=jdbc:postgresql://replica2:5432/shard1_db
```

### SSL Connections
For secure connections:

```properties
app.sharding.global-db.url=jdbc:postgresql://localhost:5432/global_db?sslmode=require
```

### Connection Pooling
The application uses HikariCP by default. Configure pool settings:

```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
```

This setup provides a complete, production-ready multi-tenant ticket management system with proper database sharding and isolation.