# Transaction Patterns with Sharding

## Overview

This guide covers transaction management patterns for the sharding-springboot library, focusing on multi-tenant sharded database operations with Spring's `@Transactional`.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Basic Transaction Patterns](#basic-transaction-patterns)
3. [Sharding-Specific Patterns](#sharding-specific-patterns)
4. [Advanced Propagation](#advanced-propagation)
5. [Cross-DataSource Operations](#cross-datasource-operations)
6. [Best Practices](#best-practices)

---

## Architecture Overview

### Dual DataSource Configuration

The library uses a dual DataSource approach for automatic routing:

```
@Transactional Method
       ↓
DataSourceTransactionManager
       ↓
RoutingDataSource (dual config)
       ↓
ShardAwareDataSourceDelegate
       ↓
Correct Shard DataSource
```

**Key Features:**
- ✅ Automatic routing based on entity packages
- ✅ Separate transaction managers for global and sharded operations
- ✅ Pre-resolved shard information from filters
- ✅ Full Spring `@Transactional` support

### Entity Package Routing

```java
// Global entities (no @ShardedEntity annotation)
@Entity
public class Account {
    @Id private Long id;
    private String name;
    // Routes to globalDataSource
}

// Sharded entities (@ShardedEntity annotation)
@Entity
@ShardedEntity
public class User {
    @Id private Long id;
    @Column(name = "account_id", nullable = false)
    private Long accountId;  // Tenant ID
    // Routes to shardedDataSource (specific shard)
}
```

---

## Basic Transaction Patterns

### 1. Simple Sharded Operations

```java
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    // ✅ Basic declarative transaction for sharded operations
    @Transactional
    public User createUser(UserRequest request, Long tenantId) {
        return TenantContext.executeInTenantContext(tenantId, () -> {
            User user = new User(request);
            user.setAccountId(tenantId);
            return userRepository.save(user);
        });
    }

    // ✅ Read-only optimization
    @Transactional(readOnly = true)
    public List<User> getUsers(Long tenantId) {
        return TenantContext.executeInTenantContext(tenantId, () ->
            userRepository.findByAccountIdAndDeletedFalse(tenantId)
        );
    }

    // ✅ Custom timeout and rollback rules
    @Transactional(
        timeout = 30,
        rollbackFor = Exception.class,
        isolation = Isolation.REPEATABLE_READ
    )
    public User updateUser(Long userId, UpdateRequest request, Long tenantId) {
        return TenantContext.executeInTenantContext(tenantId, () -> {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
            user.update(request);
            return userRepository.save(user);
        });
    }
}
```

### 2. Global Database Operations

```java
@Service
public class GlobalConfigService {

    @Autowired
    private GlobalConfigRepository configRepository;

    // ✅ Works with global database (no tenant context needed)
    @Transactional
    public GlobalConfig updateConfig(String key, String value) {
        GlobalConfig config = configRepository.findByKey(key)
            .orElseThrow(() -> new ConfigNotFoundException("Config not found"));
        config.setValue(value);
        return configRepository.save(config);
    }
}
```

---

## Sharding-Specific Patterns

### 1. Complex Multi-Entity Operations

```java
@Service
public class TicketService {

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Ticket createTicketWithWorkflow(CreateTicketRequest request, Long tenantId) {
        return TenantContext.executeInTenantContext(tenantId, () -> {
            // All operations in single transaction on same shard

            // 1. Validate requester
            User requester = userRepository.findById(request.getRequesterId())
                .orElseThrow(() -> new IllegalArgumentException("Requester not found"));

            // 2. Get status
            Status status = statusRepository.findById(request.getStatusId())
                .orElseThrow(() -> new IllegalArgumentException("Status not found"));

            // 3. Auto-assign based on category
            User assignee = findAutoAssignee(request.getCategory());

            // 4. Create ticket
            Ticket ticket = Ticket.builder()
                .accountId(tenantId)
                .subject(request.getSubject())
                .requesterId(requester.getId())
                .statusId(status.getId())
                .responderId(assignee != null ? assignee.getId() : null)
                .build();

            ticket = ticketRepository.save(ticket);

            // 5. Create workflow history
            workflowHistoryRepository.save(new WorkflowHistory(ticket, "CREATED"));

            return ticket;
        });
    }
}
```

### 2. Background Job Transactions

```java
@Service
public class DemoSetupService {

    @Async("demoSetupTaskExecutor")
    public CompletableFuture<Void> setupDemoEnvironmentAsync(Long accountId) {
        return TenantContext.executeInTenantContextAsync(accountId, () -> {
            setupDemoEnvironment(accountId);
            return null;
        });
    }

    @Transactional  // ← Works in background jobs
    public void setupDemoEnvironment(Long accountId) {
        // Create roles
        Role adminRole = roleRepository.save(createAdminRole(accountId));

        // Create statuses
        Status openStatus = statusRepository.save(createOpenStatus(accountId));

        // Create demo user
        User demoUser = userRepository.save(createDemoUser(accountId, adminRole.getId()));

        // Create demo tickets
        ticketRepository.save(createDemoTicket(accountId, demoUser.getId(), openStatus.getId()));

        // All operations committed together on same shard
    }
}
```

### 3. Batch Processing Per Tenant

```java
@Service
public class BatchUpdateService {

    // ❌ DON'T: Cannot span transaction across multiple shards
    @Transactional
    public void updateUsersAcrossAllShards() {
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
        TenantContext.executeInTenantContext(tenantId, () -> {
            List<User> users = userRepository.findByAccountIdAndDeletedFalse(tenantId);

            for (User user : users) {
                user.updateLastSeen();
                userRepository.save(user);
            }

            return null;
        });
    }
}
```

---

## Advanced Propagation

### Propagation Behaviors with Sharding

```java
@Service
public class OrderProcessingService {

    // REQUIRED (default) - Joins existing or creates new transaction
    @Transactional(propagation = Propagation.REQUIRED)
    public Order processOrder(OrderRequest request, Long tenantId) {
        return TenantContext.executeInTenantContext(tenantId, () -> {
            Order order = createOrder(request);      // REQUIRED - joins this transaction
            confirmOrder(order);                     // REQUIRED - joins this transaction
            return order;
        });
    }

    // REQUIRES_NEW - Always creates new transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void auditOrder(Order order) {
        // Separate transaction - won't be rolled back if parent fails
        auditRepository.save(new AuditLog(order));
    }

    // MANDATORY - Must have existing transaction
    @Transactional(propagation = Propagation.MANDATORY)
    public void validateOrder(Order order) {
        // Fails if called without active transaction
        validationService.validate(order);
    }

    // NOT_SUPPORTED - Always non-transactional
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void cacheOrder(Order order) {
        // Runs outside transaction even if parent has one
        cacheManager.put(order.getId(), order);
    }
}
```

### Nested Transactions (Savepoints)

```java
@Service
public class BatchProcessingService {

    @Transactional
    public BatchResult processBatch(List<BatchItem> items, Long tenantId) {
        return TenantContext.executeInTenantContext(tenantId, () -> {
            BatchResult result = new BatchResult();

            for (BatchItem item : items) {
                try {
                    // Each item processed in nested transaction
                    processItem(item);
                    result.addSuccess(item);
                } catch (Exception e) {
                    // Nested transaction rolled back, main transaction continues
                    result.addFailure(item, e);
                }
            }

            // Save batch summary in main transaction
            batchSummaryRepository.save(result.createSummary());
            return result;
        });
    }

    @Transactional(propagation = Propagation.NESTED)
    public void processItem(BatchItem item) {
        // If this fails, only this item's changes are rolled back
        itemRepository.save(item);

        if (item.requiresValidation()) {
            validationService.validate(item); // May throw exception
        }

        item.setStatus(ItemStatus.PROCESSED);
        itemRepository.save(item);
    }
}
```

---

## Cross-DataSource Operations

### Pattern 1: Sequential Operations (Recommended)

```java
@Service
public class AccountSignupService {

    // ✅ Best approach - Sequential operations with compensating transactions
    public AccountResponse createAccountWithUser(SignupRequest request) {
        Account account = null;
        User user = null;

        try {
            // 1. Create account in global database (auto-managed by globalTransactionManager)
            account = createAccount(request);

            // 2. Create shard mapping in global database
            shardLookupService.createMapping(account.getId(), determineShardId());

            // 3. Create user in sharded database (auto-managed by shardedTransactionManager)
            user = createUserInShard(account.getId(), request);

            return new AccountResponse(account, user);

        } catch (Exception e) {
            // 4. Compensating transactions (manual cleanup)
            if (user != null) {
                deleteUser(account.getId(), user.getId());
            }
            if (account != null) {
                markAccountDeleted(account.getId());
            }
            throw new SignupException("Account creation failed", e);
        }
    }

    @Transactional  // Uses globalTransactionManager
    private Account createAccount(SignupRequest request) {
        Account account = Account.builder()
            .name(request.getAccountName())
            .adminEmail(request.getAdminEmail())
            .build();
        return accountRepository.save(account);
    }

    @Transactional  // Uses shardedTransactionManager
    private User createUserInShard(Long accountId, SignupRequest request) {
        return TenantContext.executeInTenantContext(accountId, () -> {
            User user = User.builder()
                .accountId(accountId)
                .email(request.getAdminEmail())
                .name(request.getAdminName())
                .build();
            return userRepository.save(user);
        });
    }

    @Transactional
    private void markAccountDeleted(Long accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        account.setDeleted(true);
        accountRepository.save(account);
    }
}
```

### Pattern 2: Idempotent Operations

```java
@Service
public class IdempotentSignupService {

    // ✅ Design for idempotency and retries
    public AccountResponse createAccountSafe(SignupRequest request) {
        // Check if account already exists
        Optional<Account> existing = accountRepository.findByAdminEmail(request.getAdminEmail());
        if (existing.isPresent()) {
            return new AccountResponse(existing.get(), getUserForAccount(existing.get().getId()));
        }

        // Create with unique constraint checks
        Account account = createAccountWithConstraints(request);
        User user = createUserWithConstraints(account.getId(), request);

        return new AccountResponse(account, user);
    }

    @Transactional
    private Account createAccountWithConstraints(SignupRequest request) {
        if (accountRepository.existsByAdminEmail(request.getAdminEmail())) {
            throw new DuplicateEmailException("Account with this email already exists");
        }

        Account account = new Account(request);
        return accountRepository.save(account);
    }

    @Transactional
    private User createUserWithConstraints(Long accountId, SignupRequest request) {
        return TenantContext.executeInTenantContext(accountId, () -> {
            if (userRepository.existsByEmailAndAccountId(request.getAdminEmail(), accountId)) {
                return userRepository.findByEmailAndAccountId(request.getAdminEmail(), accountId);
            }

            User user = new User(accountId, request);
            return userRepository.save(user);
        });
    }
}
```

### ❌ Anti-Pattern: Single Transaction Across DataSources

```java
@Service
public class BadAccountService {

    // ❌ DON'T DO THIS - @Transactional can't span both DataSources
    @Transactional
    public AccountResponse createAccountWithUser(SignupRequest request) {
        // This will fail or produce unexpected behavior
        Account account = accountRepository.save(new Account(request));  // Global DB

        TenantContext.setTenantId(account.getId());
        User user = userRepository.save(new User(request));              // Sharded DB

        // Cannot guarantee atomicity across both DataSources
        return new AccountResponse(account, user);
    }
}
```

---

## Best Practices

### 1. Transaction Scope

**✅ DO:**
```java
@Service
public class UserService {

    // Transaction at service method level
    @Transactional
    public User updateUserProfile(Long userId, ProfileData data, Long tenantId) {
        return TenantContext.executeInTenantContext(tenantId, () -> {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
            user.updateProfile(data);
            return userRepository.save(user);
        });
    }
}
```

**❌ DON'T:**
```java
@RestController
public class UserController {

    // Don't put @Transactional at controller level
    @Transactional
    @PostMapping("/users/{id}")
    public ResponseEntity<UserResponse> updateUser(
        @PathVariable Long id,
        @RequestBody UpdateRequest request) {
        // Transaction includes HTTP serialization, too long
        User user = userService.updateUser(id, request);
        return ResponseEntity.ok(new UserResponse(user));
    }
}
```

### 2. Set Tenant Context Early

**✅ DO:**
```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    // ShardSelectorFilter already set tenant context from header
    @PostMapping
    @Transactional  // Automatically uses correct shard
    public UserResponse createUser(
        @RequestHeader("account-id") Long tenantId,
        @RequestBody CreateUserRequest request) {

        // Tenant context already set by filter
        User user = new User(request);
        user.setAccountId(tenantId);
        return new UserResponse(userRepository.save(user));
    }
}
```

**❌ DON'T:**
```java
@PostMapping
@Transactional  // Transaction starts here
public UserResponse createUser(@RequestBody CreateUserRequest request) {
    // Setting context AFTER transaction started - may route incorrectly
    TenantContext.setTenantId(request.getTenantId());
    return new UserResponse(userRepository.save(new User(request)));
}
```

### 3. Keep Transactions Focused

**✅ DO:**
```java
@Service
public class OrderService {

    @Transactional
    public Order createOrder(OrderRequest request, Long tenantId) {
        return TenantContext.executeInTenantContext(tenantId, () -> {
            // Short, focused database operations
            Order order = new Order(request);
            return orderRepository.save(order);
        });
    }

    public void processOrder(OrderRequest request, Long tenantId) {
        // Fast database operation
        Order order = createOrder(request, tenantId);

        // Slow external operation outside transaction
        paymentGateway.processPayment(order);

        // Another fast database operation
        confirmOrder(order.getId(), tenantId);
    }
}
```

**❌ DON'T:**
```java
@Transactional
public Order processOrder(OrderRequest request, Long tenantId) {
    Order order = createOrder(request);

    // Slow external API call inside transaction - holds DB connection
    PaymentResult result = paymentGateway.processPayment(order);  // Takes 5-10 seconds

    order.setPaymentStatus(result.getStatus());
    return orderRepository.save(order);
}
```

### 4. Handle Cross-DataSource Operations

**✅ DO:**
```java
@Service
public class AccountService {

    // Separate transactions for global and sharded operations
    public AccountWithUser createFullAccount(SignupRequest request) {
        // Step 1: Global database
        Account account = createAccountInGlobal(request);

        try {
            // Step 2: Sharded database
            User user = createUserInShard(account.getId(), request);
            return new AccountWithUser(account, user);
        } catch (Exception e) {
            // Compensate: mark account as deleted
            compensateAccountCreation(account.getId());
            throw e;
        }
    }

    @Transactional  // Separate transaction
    private Account createAccountInGlobal(SignupRequest request) {
        return accountRepository.save(new Account(request));
    }

    @Transactional  // Separate transaction
    private User createUserInShard(Long accountId, SignupRequest request) {
        return TenantContext.executeInTenantContext(accountId, () ->
            userRepository.save(new User(accountId, request))
        );
    }
}
```

### 5. Programmatic Transactions (When Needed)

For complex conditional logic, use `TransactionTemplate`:

```java
@Service
public class ConditionalTransactionService {

    private final TransactionTemplate transactionTemplate;

    public ConditionalTransactionService(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ProcessingResult processWithConditions(ProcessingRequest request, Long tenantId) {
        return TenantContext.executeInTenantContext(tenantId, () -> {

            if (request.isHighPriority()) {
                // Use serializable isolation for critical operations
                TransactionTemplate template = new TransactionTemplate(transactionManager);
                template.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
                template.setTimeout(60);

                return template.execute(status -> {
                    // Critical processing
                    return processCriticalOperation(request);
                });
            } else {
                // Normal transaction
                return processNormalOperation(request);
            }
        });
    }
}
```

---

## Common Pitfalls

### 1. Proxy Limitations

**❌ Problem:**
```java
@Service
public class UserService {

    @Transactional
    public void updateUser(Long userId, UpdateRequest request) {
        // Internal call - @Transactional not applied!
        validateUser(userId);  // No transaction here
        // ... update logic
    }

    @Transactional
    private void validateUser(Long userId) {
        // This @Transactional is ignored when called internally
    }
}
```

**✅ Solution:**
```java
@Service
public class UserService {

    @Autowired
    private UserValidator userValidator;  // Separate bean

    @Transactional
    public void updateUser(Long userId, UpdateRequest request) {
        userValidator.validateUser(userId);  // Proxy applied correctly
        // ... update logic
    }
}

@Component
class UserValidator {
    @Transactional
    public void validateUser(Long userId) {
        // Transaction applied correctly
    }
}
```

### 2. Tenant Context Lifecycle

**❌ Problem:**
```java
@Transactional
public User createUser(CreateUserRequest request) {
    // Tenant context set AFTER transaction starts
    TenantContext.setTenantId(request.getTenantId());
    return userRepository.save(new User(request));
}
```

**✅ Solution:**
```java
public User createUser(CreateUserRequest request, Long tenantId) {
    // Context set BEFORE transaction
    return TenantContext.executeInTenantContext(tenantId, () -> {
        return createUserInternal(request);
    });
}

@Transactional
private User createUserInternal(CreateUserRequest request) {
    return userRepository.save(new User(request));
}
```

---

## Summary

### Key Takeaways

1. **Dual DataSource Architecture**: Automatic routing based on entity packages
2. **Single-Shard Transactions**: `@Transactional` works seamlessly within a single shard
3. **No Cross-Shard Transactions**: Cannot span a single transaction across multiple shards
4. **Cross-DataSource Patterns**: Use sequential transactions with compensating logic
5. **Tenant Context First**: Always set tenant context before transaction starts
6. **Keep Transactions Short**: Minimize lock time and resource usage
7. **Compensating Transactions**: Handle cross-DataSource rollback manually

### Pattern Selection

| Scenario | Recommended Pattern |
|----------|-------------------|
| Single-shard CRUD | `@Transactional` with `TenantContext` |
| Global database operations | `@Transactional` (no tenant context) |
| Cross-DataSource operations | Sequential transactions + compensation |
| Batch processing | One transaction per tenant |
| Background jobs | `@Transactional` with `executeInTenantContextAsync` |
| Complex conditional logic | `TransactionTemplate` |

---

**Version**: 1.0.0
**Last Updated**: January 2025
