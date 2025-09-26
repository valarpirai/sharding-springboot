# Spring Boot Transaction Patterns - Complete Guide

## 🎯 **All Transaction Patterns in Spring Boot**

Spring Boot provides multiple ways to handle transactions, each with different use cases, benefits, and trade-offs. Here's a comprehensive overview of all available patterns:

## **1. 📝 Declarative Transactions (@Transactional)**

### **🔥 Most Common Pattern - Annotation-Based**
```java
@Service
public class UserService {

    // Basic declarative transaction
    @Transactional
    public User createUser(UserRequest request) {
        return userRepository.save(new User(request));
    }

    // Advanced configuration
    @Transactional(
        readOnly = true,                    // Read-only optimization
        timeout = 30,                       // 30-second timeout
        isolation = Isolation.REPEATABLE_READ,  // Custom isolation level
        propagation = Propagation.REQUIRES_NEW, // New transaction always
        rollbackFor = {Exception.class},    // Roll back on any exception
        noRollbackFor = {ValidationException.class} // Don't roll back for validation errors
    )
    public List<User> findUsers(SearchCriteria criteria) {
        return userRepository.findByCriteria(criteria);
    }
}
```

### **Propagation Behaviors**
```java
@Service
public class OrderService {

    @Transactional(propagation = Propagation.REQUIRED)  // Default - join existing or create new
    public void processOrder(Order order) { }

    @Transactional(propagation = Propagation.REQUIRES_NEW)  // Always create new transaction
    public void auditOrder(Order order) { }

    @Transactional(propagation = Propagation.MANDATORY)  // Must have existing transaction
    public void validateOrder(Order order) { }

    @Transactional(propagation = Propagation.SUPPORTS)  // Join if exists, non-transactional if not
    public void logOrder(Order order) { }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)  // Always non-transactional
    public void cacheOrder(Order order) { }

    @Transactional(propagation = Propagation.NEVER)  // Fail if transaction exists
    public void externalApiCall(Order order) { }

    @Transactional(propagation = Propagation.NESTED)  // Nested transaction (savepoint)
    public void processOrderItems(Order order) { }
}
```

## **2. 🔧 Programmatic Transactions**

### **A. PlatformTransactionManager (Low-Level)**
```java
@Service
public class PaymentService {

    private final PlatformTransactionManager transactionManager;
    private final PaymentRepository paymentRepository;

    public PaymentService(PlatformTransactionManager transactionManager,
                         PaymentRepository paymentRepository) {
        this.transactionManager = transactionManager;
        this.paymentRepository = paymentRepository;
    }

    public void processPayment(Payment payment) {
        // Define transaction
        TransactionDefinition definition = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(definition);

        try {
            // Business logic
            payment.setStatus(PaymentStatus.PROCESSING);
            paymentRepository.save(payment);

            // External payment gateway call
            PaymentResult result = paymentGateway.charge(payment);

            if (result.isSuccess()) {
                payment.setStatus(PaymentStatus.COMPLETED);
                paymentRepository.save(payment);
                transactionManager.commit(status);
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                transactionManager.rollback(status);
            }

        } catch (Exception e) {
            transactionManager.rollback(status);
            throw new PaymentException("Payment processing failed", e);
        }
    }
}
```

### **B. TransactionTemplate (Higher-Level)**
```java
@Service
public class InventoryService {

    private final TransactionTemplate transactionTemplate;
    private final InventoryRepository inventoryRepository;

    public InventoryService(PlatformTransactionManager transactionManager,
                           InventoryRepository inventoryRepository) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.inventoryRepository = inventoryRepository;
    }

    // Execute with return value
    public Inventory updateInventory(Long productId, int quantity) {
        return transactionTemplate.execute(status -> {
            Inventory inventory = inventoryRepository.findByProductId(productId);

            if (inventory.getQuantity() < quantity) {
                status.setRollbackOnly(); // Mark for rollback
                throw new InsufficientInventoryException("Not enough inventory");
            }

            inventory.decreaseQuantity(quantity);
            return inventoryRepository.save(inventory);
        });
    }

    // Execute without return value
    public void restockInventory(Long productId, int quantity) {
        transactionTemplate.executeWithoutResult(status -> {
            Inventory inventory = inventoryRepository.findByProductId(productId);
            inventory.increaseQuantity(quantity);
            inventoryRepository.save(inventory);

            // Log restock event
            auditService.logRestock(productId, quantity);
        });
    }

    // Custom transaction configuration
    public void criticalInventoryUpdate(Long productId, int quantity) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        template.setTimeout(60); // 60 seconds
        template.setReadOnly(false);

        template.executeWithoutResult(status -> {
            // Critical inventory operations
            Inventory inventory = inventoryRepository.lockByProductId(productId);
            inventory.setQuantity(quantity);
            inventoryRepository.save(inventory);
        });
    }
}
```

## **3. 🔄 Reactive Transactions (WebFlux)**

### **A. @Transactional with Reactive Streams**
```java
@Service
public class ReactiveUserService {

    private final ReactiveUserRepository userRepository;
    private final ReactiveTransactionManager transactionManager;

    // Reactive declarative transaction
    @Transactional
    public Mono<User> createUser(UserRequest request) {
        return userRepository.save(new User(request))
            .doOnNext(user -> log.info("Created user: {}", user.getId()));
    }

    // Reactive transaction with error handling
    @Transactional(rollbackFor = Exception.class)
    public Flux<User> createUsersInBatch(List<UserRequest> requests) {
        return Flux.fromIterable(requests)
            .map(User::new)
            .flatMap(userRepository::save)
            .onErrorResume(error -> {
                log.error("Batch user creation failed", error);
                return Flux.empty();
            });
    }
}
```

### **B. Programmatic Reactive Transactions**
```java
@Service
public class ReactiveOrderService {

    private final ReactiveTransactionTemplate transactionTemplate;
    private final ReactiveOrderRepository orderRepository;

    public ReactiveOrderService(ReactiveTransactionManager transactionManager,
                               ReactiveOrderRepository orderRepository) {
        this.transactionTemplate = new ReactiveTransactionTemplate(transactionManager);
        this.orderRepository = orderRepository;
    }

    public Mono<Order> processOrder(OrderRequest request) {
        return transactionTemplate.execute(status ->
            orderRepository.save(new Order(request))
                .flatMap(order -> {
                    // Additional processing
                    return inventoryService.reserveItems(order.getItems())
                        .then(paymentService.processPayment(order))
                        .thenReturn(order);
                })
                .onErrorResume(error -> {
                    status.setRollbackOnly();
                    return Mono.error(new OrderProcessingException("Failed to process order", error));
                })
        );
    }
}
```

## **4. 🔀 Multiple Transaction Managers**

### **A. Multiple DataSource Configuration**
```java
@Configuration
public class MultipleDataSourceConfig {

    // Primary DataSource (default)
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    // Secondary DataSource
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.secondary")
    public DataSource secondaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    // Primary Transaction Manager
    @Bean
    @Primary
    public PlatformTransactionManager primaryTransactionManager() {
        return new DataSourceTransactionManager(primaryDataSource());
    }

    // Secondary Transaction Manager
    @Bean("secondaryTransactionManager")
    public PlatformTransactionManager secondaryTransactionManager() {
        return new DataSourceTransactionManager(secondaryDataSource());
    }
}
```

### **B. Using Specific Transaction Managers**
```java
@Service
public class MultiDataSourceService {

    // Uses primary transaction manager (default)
    @Transactional
    public void saveToMainDatabase(User user) {
        mainUserRepository.save(user);
    }

    // Uses specific transaction manager
    @Transactional("secondaryTransactionManager")
    public void saveToAnalyticsDatabase(UserEvent event) {
        analyticsEventRepository.save(event);
    }

    // Separate transactions for different databases
    @Transactional("primaryTransactionManager")
    public void updateUserProfile(Long userId, ProfileData data) {
        User user = mainUserRepository.findById(userId);
        user.updateProfile(data);
        mainUserRepository.save(user);

        // This runs in separate transaction
        saveAnalyticsEvent(userId, "PROFILE_UPDATED");
    }

    @Transactional(value = "secondaryTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void saveAnalyticsEvent(Long userId, String eventType) {
        analyticsEventRepository.save(new UserEvent(userId, eventType));
    }
}
```

## **5. ⛓️ Chained Transactions**

### **A. Transaction Chaining with Different Propagation**
```java
@Service
public class OrderProcessingService {

    @Transactional
    public OrderResult processOrder(OrderRequest request) {
        // Main transaction
        Order order = createOrder(request);           // REQUIRED (joins this transaction)

        PaymentResult payment = processPayment(order); // REQUIRES_NEW (separate transaction)

        if (payment.isSuccessful()) {
            confirmOrder(order);                      // REQUIRED (joins main transaction)
            sendNotification(order);                  // REQUIRES_NEW (separate transaction)
        } else {
            cancelOrder(order);                       // REQUIRED (joins main transaction)
        }

        return new OrderResult(order, payment);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Order createOrder(OrderRequest request) {
        return orderRepository.save(new Order(request));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentResult processPayment(Order order) {
        // Separate transaction - won't be rolled back if main transaction fails
        return paymentService.charge(order.getTotal());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void confirmOrder(Order order) {
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void sendNotification(Order order) {
        // Quick separate transaction for notifications
        notificationService.sendOrderConfirmation(order);
    }
}
```

## **6. 🎭 Nested Transactions (Savepoints)**

### **A. Using NESTED Propagation**
```java
@Service
public class BatchProcessingService {

    @Transactional
    public BatchResult processBatch(List<BatchItem> items) {
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

## **7. 🔄 Transaction Events**

### **A. Transaction Event Listeners**
```java
@Component
public class TransactionEventHandler {

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleBeforeCommit(OrderCreatedEvent event) {
        // Executes before transaction commit
        log.info("About to commit order: {}", event.getOrderId());
    }

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAfterCommit(OrderCreatedEvent event) {
        // Executes after successful commit
        notificationService.sendOrderConfirmation(event.getOrder());
    }

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleAfterRollback(OrderCreatedEvent event) {
        // Executes after rollback
        log.warn("Order creation rolled back: {}", event.getOrderId());
        alertingService.notifyOrderFailure(event.getOrder());
    }

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void handleAfterCompletion(OrderCreatedEvent event) {
        // Executes after commit or rollback
        metricsService.recordOrderProcessingComplete(event.getOrder());
    }
}
```

### **B. Publishing Events in Transactions**
```java
@Service
public class OrderService {

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order createOrder(OrderRequest request) {
        Order order = orderRepository.save(new Order(request));

        // Event published after transaction commits
        eventPublisher.publishEvent(new OrderCreatedEvent(order));

        return order;
    }
}
```

## **8. 🔒 Transaction Synchronization**

### **A. Custom Transaction Synchronization**
```java
@Service
public class CacheService {

    @Transactional
    public User updateUser(Long userId, UserUpdate update) {
        User user = userRepository.findById(userId);
        user.update(update);
        user = userRepository.save(user);

        // Register synchronization to clear cache after commit
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheManager.evict("users", userId);
                    log.info("Cleared cache for user: {}", userId);
                }

                @Override
                public void afterRollback() {
                    log.warn("User update rolled back, cache not cleared: {}", userId);
                }
            }
        );

        return user;
    }
}
```

### **B. Resource Synchronization**
```java
@Service
public class FileProcessingService {

    @Transactional
    public ProcessingResult processFile(String filename) {
        // Register resource for cleanup
        TransactionSynchronizationManager.registerSynchronization(
            new ResourceHolderSynchronization<>(
                new FileResourceHolder(filename),
                FileResourceHolder.FILE_RESOURCE_KEY
            ) {
                @Override
                protected void releaseResource(FileResourceHolder resourceHolder, Object resourceKey) {
                    resourceHolder.cleanup();
                }
            }
        );

        // Process file
        ProcessingResult result = fileProcessor.process(filename);
        resultRepository.save(result);

        return result;
    }
}
```

## **9. 🧪 Testing Transaction Patterns**

### **A. Test Transaction Management**
```java
@SpringBootTest
@Transactional  // Rollback after each test
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    @Rollback(false)  // Don't rollback this test
    void testCreateUser() {
        User user = userService.createUser(new UserRequest("test@example.com"));
        assertThat(user.getId()).isNotNull();
    }

    @Test
    @Commit  // Explicitly commit (same as @Rollback(false))
    void testUserPersistence() {
        userService.createUser(new UserRequest("persistent@example.com"));
        // User will persist after test
    }

    @Test
    void testRollbackOnError() {
        assertThrows(ValidationException.class, () ->
            userService.createUser(new UserRequest("invalid-email"))
        );
        // Transaction automatically rolled back
    }
}
```

### **B. Transactional Test Configuration**
```java
@TestConfiguration
public class TestTransactionConfig {

    @Bean
    @Primary
    public PlatformTransactionManager testTransactionManager() {
        // Use H2 for testing
        DataSource testDataSource = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();

        return new DataSourceTransactionManager(testDataSource);
    }
}
```

## **10. 🎯 Pattern Selection Guidelines**

### **When to Use Each Pattern:**

| Pattern | Use Case | Benefits | Drawbacks |
|---------|----------|----------|-----------|
| `@Transactional` | 95% of cases | Simple, declarative, full Spring features | Less control, proxy limitations |
| `PlatformTransactionManager` | Complex logic, conditional transactions | Full control, fine-grained | Verbose, manual management |
| `TransactionTemplate` | Programmatic with less boilerplate | Easier than PlatformTxManager, callback-based | Still programmatic |
| Multiple TxManagers | Multiple databases | Database isolation | Complex configuration |
| Reactive Transactions | WebFlux applications | Reactive streams support | Different programming model |
| Nested Transactions | Partial rollback needs | Savepoint support | Database-dependent |
| Transaction Events | Post-transaction actions | Decoupled side effects | Additional complexity |

### **🏆 Best Practices:**

1. **Start with @Transactional** - Use declarative transactions by default
2. **Keep transactions short** - Minimize lock time and resource usage
3. **Avoid transaction-per-request** - Use service-level transactions
4. **Handle exceptions properly** - Configure rollback rules appropriately
5. **Use read-only transactions** - For query-only operations
6. **Mind the proxy** - `@Transactional` doesn't work for internal calls
7. **Test transaction behavior** - Verify rollback/commit scenarios
8. **Monitor transaction performance** - Watch for long-running transactions

All these patterns work seamlessly with our sharding implementation since we provide routing support at the transaction manager level! 🚀