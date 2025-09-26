# Account Signup Flow with Tenant Mapping

## 🎯 **Complete Account Signup Process**

The account signup process now creates tenant mapping and properly integrates with the sharding architecture.

## 📋 **Signup Flow Steps**

### **1. Account Creation (Global Database)**

```java
@Transactional
public SignupResponse createAccount(SignupRequest request) {
    // 1. Validate uniqueness
    validateAccountUniqueness(request);

    // 2. Create account in global database
    Account account = Account.builder()
        .name(request.getAccountName())
        .adminEmail(request.getAdminEmail())
        .build();
    account = accountRepository.save(account);
}
```

### **2. Tenant Mapping Creation**

```java
// 3. Get latest shard and create tenant-shard mapping
String latestShardId = shardUtils.getLatestShard();
shardLookupService.createMapping(account.getId(), latestShardId, "us-east-1");
```

**What happens:**
- Uses `ShardUtils.getLatestShard()` to find the shard marked as "latest" in configuration
- Creates entry in `tenant_shard_mapping` table in global database
- Associates new tenant with the designated shard for new signups

### **3. Admin User Setup (Shard Database)**

```java
// 4. Set tenant context for subsequent operations
TenantContext.setTenantId(account.getId());

// 5. Create ADMIN role first (needed for admin user)
Role adminRole = createAdminRole(account.getId());

// 6. Create admin user
User adminUser = createAdminUser(account, request, adminRole.getId());
```

**What happens:**
- Sets tenant context to route operations to correct shard
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

## 🚀 **API Usage Example**

### **Account Signup Request**

```bash
curl -X POST http://localhost:8080/api/signup \
  -H "Content-Type: application/json" \
  -d '{
    "accountName": "Acme Corporation",
    "adminEmail": "admin@acme.com",
    "password": "securePassword123"
  }'
```

### **Response**

```json
{
  "success": true,
  "accountId": 5,
  "accountName": "Acme Corporation",
  "adminEmail": "admin@acme.com",
  "adminUserId": 15,
  "adminUserName": "Admin User",
  "createdAt": "2023-12-07T10:30:00Z"
}
```

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

### **Shard Configuration** (application.yml)

```yaml
sharding:
  shards:
    shard1:
      latest: false    # Not accepting new tenants
      status: "ACTIVE"
    shard2:
      latest: true     # ✅ New tenants go here
      status: "ACTIVE"
    shard3:
      latest: false
      status: "MAINTENANCE"
```

### **Background Task Configuration**

```yaml
async:
  demo-setup:
    core-pool-size: 2
    max-pool-size: 4
    queue-capacity: 100
    thread-name-prefix: "demo-setup-"
```

The account signup process is now fully integrated with the sharding architecture, providing automatic tenant placement and complete environment setup! 🚀