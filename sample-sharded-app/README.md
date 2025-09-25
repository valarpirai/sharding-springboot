# Multi-Tenant Ticket Management System

A comprehensive Spring Boot application demonstrating database sharding and multi-tenancy with a complete ticket management system. Built using the Galaxy Sharding library for enterprise-grade tenant isolation.

## Features

- **Multi-Tenant Architecture**: Complete tenant isolation with database sharding
- **Account Management**: Self-service account signup with automatic demo environment setup
- **User Management**: Role-based access control with granular permissions
- **Ticket Management**: Full-featured ticketing system with priorities, statuses, and assignments
- **JWT Authentication**: Secure token-based authentication with tenant validation
- **Database Sharding**: Automatic tenant routing to appropriate database shards
- **Background Processing**: Asynchronous demo environment setup for new accounts
- **Permission System**: Bitmask-based permissions for efficient role management
- **Swagger Documentation**: Complete API documentation at `/swagger-ui/`

## Architecture Overview

### Database Structure
- **Global Database**: `accounts` table and `tenant_shard_mapping`
- **Sharded Databases**: `users`, `tickets`, `roles`, `statuses` tables (isolated per tenant)
- **Tenant Isolation**: All requests validated by `account-id` header

### Key Entities

#### Global Entities (stored in global_db)
- **Account**: Tenant information (id, name, admin_email, created_at)

#### Sharded Entities (stored per tenant shard)
- **User**: Tenant users with roles (account_id, email, password, role_id, active)
- **Ticket**: Support tickets (account_id, subject, description, requester_id, responder_id, status_id, priority)
- **Role**: User roles with permissions (account_id, name, permissions_mask, is_system_role)
- **Status**: Ticket statuses (account_id, name, color, position, is_default)

## API Endpoints

### Account Signup
```http
# Create new account with admin user and demo environment
POST /api/signup
Content-Type: application/json
{
  "accountName": "Demo Company",
  "adminEmail": "admin@demo.com",
  "password": "password123"
}

# Health check
GET /api/signup/health
```

### Authentication (requires account-id header)
```http
# Login
POST /api/auth/login
account-id: 1
Content-Type: application/json
{
  "email": "admin@demo.com",
  "password": "password123"
}

# Validate token
GET /api/auth/validate
Authorization: Bearer <jwt_token>

# Refresh token
POST /api/auth/refresh
Authorization: Bearer <jwt_token>
```

### User Management (requires account-id header + JWT)
```http
# Get all users
GET /api/users
account-id: 1
Authorization: Bearer <jwt_token>

# Create user
POST /api/users
account-id: 1
Authorization: Bearer <jwt_token>
Content-Type: application/json
{
  "email": "user@demo.com",
  "password": "password123",
  "firstName": "John",
  "lastName": "Doe",
  "roleId": 2,
  "active": true
}

# Update user
PUT /api/users/{id}
account-id: 1
Authorization: Bearer <jwt_token>
Content-Type: application/json
{
  "firstName": "Jane",
  "active": false
}

# Delete user (soft delete)
DELETE /api/users/{id}
account-id: 1
Authorization: Bearer <jwt_token>
```

### Ticket Management (requires account-id header + JWT)
```http
# Get all tickets (with optional filters)
GET /api/tickets?statusId=1&category=Technical&requesterId=2
account-id: 1
Authorization: Bearer <jwt_token>

# Create ticket
POST /api/tickets
account-id: 1
Authorization: Bearer <jwt_token>
Content-Type: application/json
{
  "subject": "Login Issues",
  "description": "Cannot login to my account",
  "requesterId": 2,
  "statusId": 1,
  "priority": "HIGH",
  "category": "Technical"
}

# Update ticket
PUT /api/tickets/{id}
account-id: 1
Authorization: Bearer <jwt_token>
Content-Type: application/json
{
  "statusId": 2,
  "responderId": 3,
  "priority": "MEDIUM"
}

# Assign ticket
PUT /api/tickets/{id}/assign/{responderId}
account-id: 1
Authorization: Bearer <jwt_token>

# Delete ticket (soft delete)
DELETE /api/tickets/{id}
account-id: 1
Authorization: Bearer <jwt_token>
```

## Database Setup

### Quick Setup
```bash
# 1. Ensure PostgreSQL is running
brew services start postgresql  # macOS
# OR
sudo systemctl start postgresql  # Ubuntu

# 2. Run the setup script
psql -U postgres -f database-setup.sql

# 3. Start the application
mvn spring-boot:run
```

### Database Schema

The setup script creates:

#### Global Database (global_db)
```sql
-- Accounts (tenants)
accounts (id, name, admin_email, created_at, updated_at, deleted)

-- Tenant-shard mapping (used by sharding library)
tenant_shard_mapping (tenant_id, shard_id, region, shard_status, created_at)
```

#### Shard Database (shard1_db)
```sql
-- Users with roles
users (id, account_id, email, password_hash, first_name, last_name, role_id, active, created_at, updated_at, deleted)

-- Support tickets
tickets (id, account_id, subject, description, requester_id, responder_id, status_id, priority, category, subcategory, created_at, updated_at, resolved_at, deleted)

-- User roles with permission bitmasks
roles (id, account_id, name, permissions_mask, is_system_role, created_at, updated_at, deleted)

-- Ticket statuses
statuses (id, account_id, name, color, position, is_default, created_at, updated_at, deleted)
```

### Sample Data Included
- 1 demo account ("Demo Company")
- 3 users: Admin, Agent, Requester (including demo user andrea@example.com)
- 3 system roles: ADMIN, AGENT, REQUESTER with proper permissions
- 5 ticket statuses: Open, In Progress, Resolved, Closed, On Hold
- 5 sample tickets with different priorities and statuses

## Configuration

### Application Properties
```properties
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/global_db
spring.datasource.username=postgres
spring.datasource.password=

# Sharding Configuration (handled by sharding library)
app.sharding.global-db.url=jdbc:postgresql://localhost:5432/global_db
app.sharding.shards.shard1.master.url=jdbc:postgresql://localhost:5432/shard1_db
app.sharding.shards.shard1.latest=true

# JWT Configuration
app.jwt.secret=mySecretKey123456789012345678901234567890
app.jwt.expiration=3600000

# Swagger Documentation
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
```

## Permission System

### Permission Structure
The system uses a bitmask-based permission system defined in `permissions.yml`:

- **Account Management**: CREATE_ACCOUNT, UPDATE_ACCOUNT, DELETE_ACCOUNT, etc.
- **User Management**: CREATE_USER, UPDATE_USER, DELETE_USER, etc.
- **Ticket Management**: CREATE_TICKET, UPDATE_TICKET, ASSIGN_TICKET, etc.
- **Role Management**: CREATE_ROLE, UPDATE_ROLE, DELETE_ROLE, etc.
- **Status Management**: CREATE_STATUS, UPDATE_STATUS, DELETE_STATUS, etc.

### Default Roles
- **ADMIN**: Full permissions (all operations)
- **AGENT**: Ticket management, user viewing, status management
- **REQUESTER**: Create/view own tickets, view users

## Development Workflow

### 1. Account Signup Flow
1. `POST /api/signup` creates account in global database
2. Maps new account to latest shard using `ShardLookupService`
3. Creates admin user and ADMIN role in tenant shard
4. Triggers background `AccountDemoSetupService` to:
   - Create default roles (ADMIN, AGENT, REQUESTER)
   - Create default ticket statuses
   - Create demo user (andrea@example.com)
   - Create 3 sample tickets

### 2. Request Flow
1. Client sends request with `account-id` header
2. `TenantValidationFilter` validates tenant and sets `TenantContext`
3. Sharding library routes queries to appropriate shard
4. Business logic operates on tenant-isolated data
5. Response returned, context cleared

### 3. Authentication Flow
1. Client login with email/password + account-id header
2. `AuthService` validates credentials in tenant shard
3. `JwtService` generates signed JWT with user info
4. Client uses JWT in Authorization header for subsequent requests
5. JWT validated and user context established

## Testing the System

### 1. Create Account
```bash
curl -X POST http://localhost:8080/api/signup \
  -H "Content-Type: application/json" \
  -d '{
    "accountName": "Test Company",
    "adminEmail": "admin@test.com",
    "password": "password123"
  }'
```

### 2. Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "account-id: 1" \
  -d '{
    "email": "admin@test.com",
    "password": "password123"
  }'
```

### 3. Create Ticket
```bash
curl -X POST http://localhost:8080/api/tickets \
  -H "Content-Type: application/json" \
  -H "account-id: 1" \
  -H "Authorization: Bearer <jwt_token>" \
  -d '{
    "subject": "System Bug",
    "description": "Found a critical bug",
    "requesterId": 2,
    "statusId": 1,
    "priority": "HIGH"
  }'
```

## Key Features in Action

### 1. Tenant Isolation
- Each account's data is completely isolated in separate database shards
- Cross-tenant data access is impossible due to query validation
- Automatic routing ensures queries hit the correct shard

### 2. Background Processing
- New account signup triggers asynchronous demo environment setup
- Uses `@Async` with dedicated thread pool for background tasks
- Proper tenant context management in background jobs

### 3. Security
- JWT-based authentication with tenant validation
- Role-based access control with granular permissions
- Password hashing using BCrypt
- SQL injection protection through parameterized queries

### 4. Scalability
- New tenants automatically assigned to latest shard
- Horizontal scaling through additional shards
- Caching of tenant-shard mappings for performance
- Soft deletes for data retention and audit trails

## API Documentation

Once the application is running, visit:
- **Swagger UI**: http://localhost:8080/swagger-ui/
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

## Monitoring and Observability

The application includes:
- Comprehensive logging with tenant context
- SQL query logging for debugging
- Performance metrics for shard operations
- Health check endpoints for monitoring

## Next Steps

- **Horizontal Scaling**: Add more shards as tenant count grows
- **Advanced Security**: Implement API rate limiting and audit logging
- **Real-time Features**: Add WebSocket support for real-time ticket updates
- **Analytics**: Implement tenant-specific reporting and analytics
- **Migration Tools**: Build tools for moving tenants between shards