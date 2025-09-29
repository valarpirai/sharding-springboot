# @Transactional with Sharding - Complete Guide

## 🎯 **How @Transactional Works with Our Sharding Implementation**

Our sharding library provides **automatic transaction routing** with **dual DataSource support** for seamless handling of global and sharded operations.

## ⚠️ **Important: Dual DataSource Transaction Limitations**

With dual DataSource configuration, **@Transactional cannot span both global and sharded operations** in a single method. Each DataSource has its own transaction manager:

- **Global entities**: Use `globalTransactionManager`
- **Sharded entities**: Use `shardedTransactionManager` (primary)
- **Cross-DataSource operations**: Require manual transaction coordination

## 📋 **Architecture Overview**

### **Component Stack**
```
@Transactional Method
       ↓
DataSourceTransactionManager (primary)
       ↓
ShardedRoutingDataSource (with dual config)
       ↓
ConnectionRouter
       ↓
Correct Shard DataSource
```

**Key Changes in Latest Version:**
- ✅ **Simplified Architecture**: Removed RoutingTransactionManager (redundant with dual DataSource)
- ✅ **No AOP Overhead**: Removed RepositoryShardingAspect for better performance
- ✅ **Dual DataSource**: Automatic routing based on entity packages
- ✅ **Lombok Integration**: Reduced boilerplate in configuration classes

## 🔄 **Complete Transaction Flow**

### **1. Single DataSource @Transactional (✅ Recommended)**

```java
@Service
public class UserService {

    @Transactional  // ✅ Works perfectly for sharded operations only
    public User createUser(UserCreateRequest request, Long tenantId) {
        return TenantContext.executeInTenantContext(tenantId, () -> {
            // All operations use shardedTransactionManager
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email exists");
            }

            User user = userRepository.save(newUser);  // Sharded operation
            roleRepository.findById(user.getRoleId());  // Sharded operation

            return user;
        });
    }
}

@Service
public class GlobalConfigService {

    @Transactional  // ✅ Works perfectly for global operations only
    public GlobalConfig updateConfig(String key, String value) {
        // All operations use globalTransactionManager
        GlobalConfig config = globalConfigRepository.findByKey(key);
        config.setValue(value);
        return globalConfigRepository.save(config);
    }
}
```

### **2. Cross-DataSource Operations (❌ @Transactional Limitations)**

```java
@Service
public class AccountSignupService {

    // ❌ DON'T DO THIS - @Transactional can't span both DataSources
    @Transactional
    public AccountResponse createAccountWithUser(SignupRequest request) {
        Account account = accountRepository.save(newAccount);     // Global
        User user = userRepository.save(newUser);                // Sharded
        return new AccountResponse(account, user);
    }

    // ✅ DO THIS - Manual coordination without @Transactional
    public AccountResponse createAccountWithUser(SignupRequest request) {
        Account account = null;
        try {
            // 1. Global operation (auto-managed by globalTransactionManager)
            account = accountRepository.save(newAccount);

            // 2. Set tenant context for sharded operations
            TenantContext.setTenantId(account.getId());

            // 3. Sharded operation (auto-managed by shardedTransactionManager)
            User user = userRepository.save(newUser);

            return new AccountResponse(account, user);

        } catch (Exception e) {
            // 4. Manual cleanup (compensating transaction pattern)
            if (account != null) {
                account.setDeleted(true);
                accountRepository.save(account);
            }
            throw e;
        } finally {
            TenantContext.clear();
        }
    }
}
```

### **2. Step-by-Step Transaction Flow**

1. **@Transactional Triggered**: Spring detects `@Transactional` annotation
2. **DataSourceTransactionManager**: Uses primary DataSource for transaction management
3. **Transaction Begin**: Creates transaction on the routing DataSource
4. **ConnectionRouter**: Determines target shard from tenant context for each operation
5. **Entity Package Detection**: Dual DataSource config routes based on entity packages automatically
6. **Repository Operations**: All operations automatically routed to correct DataSource
7. **Transaction Commit/Rollback**: Committed/rolled back on the same shard connection

### **3. Shard Selection Logic**

```java
// In RoutingTransactionManager.determineTargetTransactionManager()

TenantInfo tenantInfo = TenantContext.getTenantInfo();

if (tenantInfo != null && tenantInfo.getShardDataSource() != null) {
    // ✅ Use pre-resolved shard DataSource (from ShardSelectorFilter)
    return getOrCreateTransactionManager(tenantInfo.getShardDataSource());
}

// ✅ Fallback to global database for non-tenant operations
return getOrCreateTransactionManager(globalDataSource);
```

## 🚀 **Usage Examples**

### **✅ Sharded Entity Operations**
```java
@Service
public class TicketService {

    @Transactional
    public Ticket createTicketWithAssignment(CreateTicketRequest request, Long tenantId) {
        // All operations automatically routed to same shard

        // 1. Check requester exists (User is @ShardedEntity)
        User requester = userRepository.findById(request.getRequesterId())
            .orElseThrow(() -> new IllegalArgumentException("Requester not found"));

        // 2. Get default status (Status is @ShardedEntity)
        Status defaultStatus = statusRepository.findByIsDefaultTrue()
            .orElseThrow(() -> new IllegalStateException("No default status"));

        // 3. Create ticket (Ticket is @ShardedEntity)
        Ticket ticket = Ticket.builder()
            .accountId(tenantId)
            .subject(request.getSubject())
            .requesterId(requester.getId())
            .statusId(defaultStatus.getId())
            .build();

        // 4. Save ticket - all in same transaction on same shard!
        return ticketRepository.save(ticket);
    }
}
```

### **✅ Mixed Operations (Sharded + Global)**
```java
@Service
public class AccountSignupService {

    @Transactional
    public Account createAccountWithSetup(SignupRequest request) {
        // 1. Create account in GLOBAL database (Account has no @ShardedEntity)
        Account account = Account.builder()
            .name(request.getAccountName())
            .adminEmail(request.getAdminEmail())
            .build();
        account = accountRepository.save(account); // → Global DB

        // 2. Create tenant-shard mapping in GLOBAL database
        shardLookupService.createMapping(account.getId(), "shard1");

        // 3. Switch context to shard for tenant-specific operations
        return TenantContext.executeInTenantContext(account.getId(), () -> {
            // Create admin user in SHARD database (User is @ShardedEntity)
            User adminUser = User.builder()
                .accountId(account.getId())
                .email(request.getAdminEmail())
                .build();
            userRepository.save(adminUser); // → Shard DB

            return account;
        });
    }
}
```

### **✅ Background Job Transactions**
```java
@Service
public class AccountDemoSetupService {

    @Async("demoSetupTaskExecutor")
    public CompletableFuture<Void> setupDemoEnvironmentAsync(Long accountId) {
        return executeWithShardContext(accountId, () -> {
            setupDemoEnvironment(accountId);  // This method has @Transactional
            return null;
        });
    }

    @Transactional  // ← Works in background jobs too!
    public void setupDemoEnvironment(Long accountId) {
        // Create roles (Role is @ShardedEntity)
        Role adminRole = roleRepository.save(createAdminRole(accountId));

        // Create statuses (Status is @ShardedEntity)
        Status openStatus = statusRepository.save(createOpenStatus(accountId));

        // Create demo user (User is @ShardedEntity)
        User demoUser = userRepository.save(createDemoUser(accountId, adminRole.getId()));

        // Create demo tickets (Ticket is @ShardedEntity)
        ticketRepository.save(createDemoTicket(accountId, demoUser.getId(), openStatus.getId()));

        // ALL operations in same transaction on correct shard!
    }
}
```

## ⚙️ **Advanced Configuration**

### **Transaction Manager Properties**
The `RoutingTransactionManager` inherits standard Spring transaction properties:

```java
@Service
public class UserService {

    @Transactional(
        readOnly = true,           // ✅ Supports read-only optimization
        timeout = 30,              // ✅ Supports custom timeout
        isolation = REPEATABLE_READ, // ✅ Supports isolation levels
        propagation = REQUIRED     // ✅ Supports propagation behavior
    )
    public List<User> getUsers(Long tenantId) {
        return userRepository.findByAccountIdAndDeletedFalse(tenantId);
    }

    @Transactional(rollbackFor = Exception.class)  // ✅ Custom rollback rules
    public User updateUser(Long userId, UpdateRequest request, Long tenantId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Update operations...
        return userRepository.save(user);
    }
}
```

### **Multiple Shard Operations (Advanced)**
```java
@Service
public class CrossShardReportService {

    // ❌ DON'T DO: Cannot span transaction across multiple shards
    @Transactional
    public void updateUsersAcrossShards() {
        // This won't work - each shard needs separate transaction
    }

    // ✅ DO: Process each tenant separately
    public void updateUsersForAllTenants() {
        List<Long> tenantIds = getAllTenantIds();

        for (Long tenantId : tenantIds) {
            // Each tenant processed in separate transaction on its shard
            processUserUpdatesForTenant(tenantId);
        }
    }

    @Transactional
    public void processUserUpdatesForTenant(Long tenantId) {
        // All operations for this tenant in single transaction
        List<User> users = userRepository.findByAccountIdAndDeletedFalse(tenantId);

        for (User user : users) {
            // Update user...
            userRepository.save(user);
        }
    }
}
```

## 🔧 **Key Benefits**

### **✅ Automatic Routing**
- No manual transaction manager selection required
- Transactions automatically bound to correct shard DataSource
- Works with pre-resolved shard information from filters

### **✅ Performance Optimized**
- Transaction managers cached per DataSource
- Uses pre-resolved shard info when available
- Minimizes transaction overhead

### **✅ Spring Integration**
- Full compatibility with Spring's `@Transactional` features
- Supports all transaction propagation behaviors
- Works with declarative transaction management

### **✅ Error Handling**
- Proper rollback on correct shard
- Graceful fallback to global database
- Comprehensive error logging

## 🚨 **Important Considerations**

### **❌ Limitations**
1. **No Cross-Shard Transactions**: Cannot span single transaction across multiple shards
2. **Context Required**: Tenant context must be set before transaction begins
3. **Same-Shard Operations**: All operations in a transaction must target same shard

### **⚠️ Best Practices**
1. **Set Tenant Context Early**: Use ShardSelectorFilter or executeInTenantContext
2. **Keep Transactions Focused**: Don't mix global and sharded operations in same @Transactional method
3. **Handle Context Properly**: Ensure tenant context is available before @Transactional methods

## 🎯 **Complete Working Example**

```java
// ✅ Perfect sharded transaction example
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    @PostMapping
    @Transactional  // ← Automatically routes to correct shard!
    public TicketResponse createTicket(
        @RequestHeader("account-id") Long tenantId,
        @RequestBody CreateTicketRequest request) {

        // ShardSelectorFilter already set tenant context
        // RoutingTransactionManager automatically selected shard transaction manager
        // All repository operations below use same shard in same transaction

        // Validate requester exists
        User requester = userRepository.findByIdAndAccountIdAndDeletedFalse(
            request.getRequesterId(), tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Requester not found"));

        // Get status
        Status status = statusRepository.findByIdAndAccountIdAndDeletedFalse(
            request.getStatusId(), tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Status not found"));

        // Create and save ticket
        Ticket ticket = Ticket.builder()
            .accountId(tenantId)
            .subject(request.getSubject())
            .description(request.getDescription())
            .requesterId(requester.getId())
            .statusId(status.getId())
            .priority(request.getPriority())
            .build();

        ticket = ticketRepository.save(ticket);

        // All operations committed together on same shard!
        return TicketResponse.from(ticket);
    }
}
```

The `@Transactional` annotation now works seamlessly with our sharding architecture, automatically routing transactions to the correct shard while maintaining all the benefits of Spring's declarative transaction management! 🚀