# Account Signup Flow - Implementation Details

> 📋 **Note**: For API endpoint examples, see the sample-sharded-app README.md.

## Detailed Implementation Walkthrough

This guide covers the internal mechanics of the account signup process beyond the basic API usage.

### 1. Account Creation (Global Database)

```java
// Validate name + email are unique before writing anything
validateAccountUniqueness(request); // throws IllegalArgumentException if duplicate

// Create account record in global database
Account account = Account.builder()
        .name(request.getAccountName())
        .adminEmail(request.getAdminEmail())
        .build();
account = accountRepository.save(account);
```

**What happens:**
- Checks `accounts` table for duplicate name (case-insensitive) and duplicate admin email
- Saves a new row to the `accounts` table in the global database — this is the tenant's identity record
- The generated `account.getId()` becomes the `tenant_id` used in all subsequent shard operations

### 2. Tenant Mapping Creation

```java
// Assign to the shard marked latest=true in configuration
shardUtils.assignTenantToLatestShard(account.getId());
```

**What happens:**
- `ShardUtils.assignTenantToLatestShard()` calls `getLatestShard()` then `shardLookupService.createMapping()`
- Creates a row in `tenant_shard_mapping` in the global database linking `tenant_id → shard_id`
- All future read/write operations for this tenant are routed to this shard

### **3. Admin User Setup (Shard Database)**

```java
// 4. Resolve full TenantInfo (shard DataSource) and set context
boolean resolved = shardUtils.resolveAndSetTenantContext(account.getId(), false);

// 5. Create ADMIN role first (needed for admin user)
Role adminRole = createAdminRole(account.getId());

// 6. Create admin user
User adminUser = createAdminUser(account, request, adminRole.getId());
// ... TenantContext.clear() called in finally block
```

**What happens:**
- `resolveAndSetTenantContext` looks up the shard mapping and injects a pre-resolved `TenantInfo` (including shard `DataSource`) into `TenantContext` — routing all subsequent JPA operations to the correct shard
- Creates ADMIN role in shard database with full permissions
- Creates admin user with encrypted password in shard database

### **4. Background Demo Setup**

```java
// 7. Trigger background demo setup
demoSetupService.setupDemoEnvironmentAsync(account.getId());
```

**What happens asynchronously:**
- Creates additional roles (AGENT, REQUESTER)
- Creates default ticket statuses (Open, In Progress, Pending, Resolved, Closed)
- Creates sample user (`andrea@example.com`)
- Creates 3 sample tickets for demonstration

## 🗄️ **Database Schema Changes**

### **Global Database Tables**

#### **accounts** (Tenant Registry)
```sql
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    admin_email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT false
);
```

#### **tenant_shard_mapping** (Shard Routing)
```sql
CREATE TABLE tenant_shard_mapping (
    tenant_id BIGINT NOT NULL,           -- References accounts.id
    shard_id VARCHAR(255) NOT NULL,      -- References shard configuration
    region VARCHAR(255),                 -- Geographic region
    shard_status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
);
```

### **Shard Database Tables**

All tenant-specific data is stored in the designated shard:
- **users** - User accounts within each tenant
- **roles** - Permission roles per tenant
- **status** - Ticket statuses per tenant
- **tickets** - Support tickets per tenant

## 📊 **Performance Considerations**

### **Async Processing Benefits**
- Demo setup runs in background thread pool
- Immediate API response for better UX
- Failure isolation - signup succeeds even if demo setup fails

## 🔄 **What Happens Behind the Scenes**

### **Immediate Operations (Synchronous)**

1. **Global Database**:
   ```sql
   INSERT INTO accounts (name, admin_email) VALUES ('Acme Corporation', 'admin@acme.com');
   INSERT INTO tenant_shard_mapping (tenant_id, shard_id, region) VALUES (5, 'shard2', 'us-east-1');
   ```

2. **Shard Database** (shard2):
   ```sql
   INSERT INTO roles (account_id, name, permissions_mask) VALUES (5, 'ADMIN', 9223372036854775807);
   INSERT INTO users (account_id, email, password_hash, role_id) VALUES (5, 'admin@acme.com', '$2a$10$...', 25);
   ```

### **Background Operations (Asynchronous)**

3. **Additional Roles**:
   ```sql
   INSERT INTO roles (account_id, name, permissions_mask) VALUES
     (5, 'AGENT', 2080374784),
     (5, 'REQUESTER', 17408);
   ```

4. **Default Statuses**:
   ```sql
   INSERT INTO status (account_id, name, is_default) VALUES
     (5, 'Open', true),
     (5, 'In Progress', false),
     (5, 'Resolved', false),
     (5, 'Closed', false);
   ```

5. **Sample Data**:
   ```sql
   INSERT INTO users (account_id, email, first_name, last_name, role_id)
     VALUES (5, 'andrea@example.com', 'Andrea', 'Sample', 27);

   INSERT INTO tickets (account_id, subject, requester_id, status_id) VALUES
     (5, 'Login Issues', 26, 21),
     (5, 'Feature Request: Dark Mode', 26, 21),
     (5, 'Bug: Dashboard Loading Slow', 26, 22);
   ```

## 🏗️ **Architecture Benefits**

### **✅ Automatic Shard Assignment**
- New tenants automatically assigned to latest available shard
- No manual intervention required for tenant placement
- Configurable shard selection strategy

### **✅ Tenant Isolation**
- Each tenant's data stored in designated shard
- Complete data isolation between tenants
- Scalable tenant-per-shard architecture

### **✅ Consistent Context Management**
- Tenant context automatically set during signup
- Background jobs work with proper shard context
- Seamless routing for all tenant operations

### **✅ Demo Environment Ready**
- Immediate usability with sample data
- Pre-configured roles and permissions
- Ready-to-use sample tickets for testing

## 🔧 **Configuration**

### Shard Configuration (application.properties)

```properties
app.sharding.shards.shard1.latest=false
app.sharding.shards.shard1.status=ACTIVE

app.sharding.shards.shard2.latest=true
app.sharding.shards.shard2.status=ACTIVE

app.sharding.shards.shard3.latest=false
app.sharding.shards.shard3.status=MAINTENANCE
```

Only one shard should have `latest=true` at a time. New tenants are always assigned to that shard.

The account signup process is now fully integrated with the sharding architecture, providing automatic tenant placement and complete environment setup! 🚀