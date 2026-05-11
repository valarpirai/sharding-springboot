# Documentation

Comprehensive documentation for the sharding-springboot-starter library and sample application.

## Getting Started

- **[Getting Started](getting-started.md)** - Quick start guide, installation, and basic usage
- **[Production Readiness Assessment](PRODUCTION_READINESS.md)** - ⚠️ Current state and gaps (NOT production ready)
- **[Database Setup](database-setup.md)** - Database provisioning and configuration

## Guides

### Core Functionality
- **[Migrations](migrations.md)** - Liquibase schema migrations across shards (strategies: sequential, parallel, wave, canary; includes idempotency)
- **[Transactions](transactions.md)** - Transaction patterns for single and cross-DataSource operations
- **[Custom Shard Lookup](custom-shard-lookup.md)** - Implementing custom tenant-to-shard mapping strategies

### Implementation Patterns
- **[Account Signup Flow](account-signup.md)** - Complete tenant onboarding implementation with demo data setup

## Deployment

- **[Zero Downtime Best Practices](zero-downtime.md)** - Production deployment strategies inspired by PayPal patterns
- **[Database Setup](database-setup.md)** - PostgreSQL/MySQL setup for global DB and shards

## Testing

- **[Integration Tests](integration-tests.md)** - Comprehensive guide to TestContainers-based integration testing (69 tests covering isolation, security, migrations)

## Reference

- **[Technical Specification](specification.md)** - Complete library specifications, configuration options, and architecture details
- **[Features & Roadmap](FEATURES.md)** - Completed features, pending improvements, and development roadmap

## Archive

Historical documentation (for reference only):
- [Changes Log](changes.md) - Detailed change history
- [Spring Boot 3 Upgrade](upgrade-spring-boot-3.md) - Migration notes from Spring Boot 2.x to 3.x
- [Final Summary](final-summary.md) - Project completion summary

## Support

For issues or questions:
1. Check the relevant guide in this documentation
2. Review `CLAUDE.md` in the project root for development patterns
3. Examine the sample application for working examples
4. Check application logs for detailed error messages

## Contributing

When adding documentation:
- Update this index when adding new files
- Follow existing formatting and style
- Include code examples where applicable
- Keep content concise and actionable
