# Database Setup Guide

## Quick Setup for Development

If you're getting authentication errors, use the simplified development setup:

### Option 1: Development Mode (Recommended for Local Development)

1. **Run the development setup script:**
   ```bash
   psql -U postgres -f database-setup-dev.sql
   ```

2. **Start the application with dev profile:**
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

This uses the postgres superuser and is simpler for local development.

### Option 2: Production-like Setup

1. **Run the full setup script:**
   ```bash
   psql -U postgres -f database-setup-postgresql.sql
   ```

2. **Start the application:**
   ```bash
   mvn spring-boot:run
   ```

This creates separate database users for better security.

### Database Configuration

The application is configured with the following PostgreSQL databases:

- **Global Database**: `global_db` (port 5432)
- **Shard 1**: `shard1_db` (port 5432, replicas on 5433, 5434)
- **Shard 2**: `shard2_db` (port 5435, replica on 5436)

### Connection Details

Default configuration assumes:
- Host: `localhost`
- Username: `global_user`, `shard1_user`, `shard2_user`
- Password: `global_password`, `shard1_password`, `shard2_password`

Update these in `application.properties` as needed.

## Alternative Configurations

### Using MySQL

If you prefer MySQL, you can:

1. Use the MySQL setup script: `setup.sql`
2. Switch Maven dependencies to prioritize MySQL
3. Update `application.properties` with MySQL configuration

### Using Redis Cache

To use Redis instead of in-memory caching:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=redis
```

## Verification

After setup, you can verify the database configuration:

1. Check tenant mappings in `global_db.tenant_shard_mapping`
2. Verify sample data in each shard
3. Test the application endpoints

The setup script includes verification queries at the end.

## Troubleshooting

### "password authentication failed" Error

If you see this error, it means PostgreSQL users don't exist or have wrong passwords.

**Quick Fix - Use Development Mode:**
```bash
# Use the simplified setup
psql -U postgres -f database-setup-dev.sql
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Alternative Fix - Check PostgreSQL Authentication:**
1. Edit your `pg_hba.conf` file (location varies by OS)
2. Change authentication method to `trust` for local connections:
   ```
   # TYPE  DATABASE        USER            ADDRESS                 METHOD
   local   all             all                                     trust
   host    all             all             127.0.0.1/32            trust
   host    all             all             ::1/128                 trust
   ```
3. Restart PostgreSQL service
4. Run the setup script

### Connection Refused Error

If PostgreSQL isn't running:
- **macOS**: `brew services start postgresql`
- **Ubuntu**: `sudo systemctl start postgresql`
- **Windows**: Start PostgreSQL service from Services panel

### Database Already Exists

If you need to reset the databases:
```sql
DROP DATABASE IF EXISTS global_db;
DROP DATABASE IF EXISTS shard1_db;
DROP DATABASE IF EXISTS shard2_db;
```

Then re-run the setup script.