# Getting Started

Quick start guide for the sharding-springboot-starter library and sample application.

## Quick Start

```bash
# 1. Build the library
mvn clean install -DskipTests

# 2. Set up PostgreSQL databases
cd sample-sharded-app
psql -U postgres -f database-setup.sql

# 3. Run the sample application
mvn spring-boot:run

# 4. Access Swagger UI
open http://localhost:8080/swagger-ui.html
```

## Prerequisites

- **Java 21+**
- **Maven 3.6+**
- **PostgreSQL 11+** or **MySQL 5.7+**
- **Docker** (for integration tests)

## Project Structure

```
sharding-springboot/
├── sharding-springboot-starter/   # Core sharding library
├── sample-sharded-app/            # Demo application
└── docs/                          # Documentation
```

## Using the Library

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.valarpirai</groupId>
    <artifactId>sharding-springboot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configure Sharding

```properties
# Global Database
app.sharding.global-db.url=jdbc:postgresql://localhost:5432/global_db
app.sharding.global-db.username=user
app.sharding.global-db.password=pass

# Shard Configuration
app.sharding.shards.shard1.master.url=jdbc:postgresql://localhost:5432/shard1_db
app.sharding.shards.shard1.master.username=user
app.sharding.shards.shard1.master.password=pass
app.sharding.shards.shard1.latest=true

# Validation
app.sharding.validation.strictness=STRICT
app.sharding.tenant-column-names=tenant_id
```

### 3. Mark Sharded Entities

```java
@Entity
@ShardedEntity
public class Customer {
    @Id
    private Long id;
    
    @Column(nullable = false)
    private Long tenantId;  // Required
    
    private String name;
}
```

### 4. Use Tenant Context

```java
@Service
public class CustomerService {
    
    @Autowired
    private CustomerRepository repository;
    
    public Customer save(Long tenantId, Customer customer) {
        return TenantContext.executeInTenantContext(tenantId, () -> {
            return repository.save(customer);
        });
    }
}
```

## Sample Application

### Database Setup

The sample app demonstrates a multi-tenant ticket management system:

**Global Database:**
- Accounts (tenant information)
- tenant_shard_mapping (tenant → shard routing)

**Sharded Databases:**
- Users (per tenant)
- Tickets (per tenant)
- Roles and Statuses (per tenant)

### API Endpoints

**Signup:**
```bash
POST /api/signup
{
  "accountName": "Demo Company",
  "adminEmail": "admin@demo.com",
  "password": "password123"
}
```

**Login:**
```bash
POST /api/auth/login
Headers: account-id: 1
{
  "email": "admin@demo.com",
  "password": "password123"
}
```

**Create Ticket:**
```bash
POST /api/tickets
Headers: 
  account-id: 1
  Authorization: Bearer <jwt_token>
{
  "subject": "Bug Report",
  "description": "Found an issue",
  "requesterId": 2,
  "statusId": 1,
  "priority": "HIGH"
}
```

## Key Concepts

### Two-Database Model

1. **Global DB**: Central database for tenant-shard mappings and global data
2. **Shard DBs**: Multiple databases with tenant-specific data

### Tenant Context

All sharded operations must execute within a tenant context:

```java
// Method 1: executeInTenantContext
TenantContext.executeInTenantContext(tenantId, () -> {
    return repository.findAll();
});

// Method 2: try-with-resources
try (TenantContext.TenantScope scope = TenantContext.setCurrentTenant(tenantId)) {
    repository.save(entity);
}
```

### Package-Based Routing

Configure package structure for dual DataSource:

```properties
app.sharding.dual-datasource.enabled=true
app.sharding.dual-datasource.global-repository-base-package=com.example.repository.global
app.sharding.dual-datasource.sharded-repository-base-package=com.example.repository.sharded
```

**Rules:**
- Global repositories → use `globalDataSource`
- Sharded repositories → use `primaryDataSource` with routing
- Wrong package = wrong DataSource = errors

## Common Patterns

### Adding a New Sharded Entity

1. Create entity with `@ShardedEntity` + `tenant_id` column
2. Place in sharded entity package
3. Create repository in sharded repository package
4. Use TenantContext in service methods

### Adding a New Shard

1. Provision database (master + replicas)
2. Add configuration:
   ```properties
   app.sharding.shards.shard3.master.url=...
   app.sharding.shards.shard3.latest=true
   ```
3. Run migrations
4. New tenants auto-assigned to latest shard

### Transaction Patterns

**Single DataSource (✅ Works):**
```java
@Transactional
public User createUser(UserRequest request, Long accountId) {
    return TenantContext.executeInTenantContext(accountId, () -> {
        return userRepository.save(newUser);
    });
}
```

**Cross DataSource (❌ Don't use @Transactional):**
```java
public SignupResponse signup(SignupRequest request) {
    Account account = null;
    try {
        account = accountRepository.save(newAccount);
        TenantContext.setTenantId(account.getId());
        User user = userRepository.save(newUser);
        return success(account, user);
    } catch (Exception e) {
        if (account != null) compensate(account);
        throw e;
    } finally {
        TenantContext.clear();
    }
}
```

## Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=MultiTenantDataIsolationTest

# Integration tests (requires Docker)
mvn test -Dtest="*IT"
```

## Troubleshooting

### "No tenant context set"
→ Wrap operations in `TenantContext.executeInTenantContext()`

### "Query validation failed"
→ Add WHERE tenant_id = ? to query, or check validation strictness

### Connection pool exhausted
→ Tune `app.sharding.shards.{shardId}.hikari.maximum-pool-size`

### Wrong DataSource
→ Check repository package matches configuration

## Next Steps

- **Read**: [Migrations Guide](migrations.md) for database schema changes
- **Read**: [Transactions Guide](transactions.md) for advanced patterns
- **Read**: [Account Signup Flow](account-signup.md) for signup implementation
- **Deploy**: [Zero Downtime Guide](../deployment/zero-downtime.md) for production

## Additional Resources

- **API Docs**: http://localhost:8080/swagger-ui.html (when running)
- **Specification**: [Technical Specification](../reference/specification.md)
- **Testing**: [Integration Tests Guide](../testing/integration-tests.md)
