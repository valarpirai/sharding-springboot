package com.valarpirai.sharding.validation;

import com.valarpirai.sharding.config.ShardingConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates SQL queries to ensure sharded entity operations include tenant_id filtering.
 * Supports configurable strictness levels and multiple tenant column names.
 */
@Component
public class QueryValidator {

    private static final Logger logger = LoggerFactory.getLogger(QueryValidator.class);

    private final ShardingConfigProperties shardingConfig;
    private final List<Pattern> tenantColumnPatterns;

    public QueryValidator(ShardingConfigProperties shardingConfig) {
        this.shardingConfig = shardingConfig;
        this.tenantColumnPatterns = createTenantColumnPatterns();
    }

    /**
     * Validate a SQL query for tenant filtering requirements.
     *
     * @param sql the SQL query to validate
     * @param tableName the table name being accessed (optional)
     * @param isShardedEntity whether this is for a sharded entity
     * @throws QueryValidationException if validation fails and strictness is STRICT
     */
    public void validateQuery(String sql, String tableName, boolean isShardedEntity) {
        if (!isShardedEntity) {
            logger.trace("Skipping validation for non-sharded entity query: {}", sanitizeSql(sql));
            return;
        }

        ShardingConfigProperties.StrictnessLevel strictness = shardingConfig.getValidation().getStrictness();
        if (strictness == ShardingConfigProperties.StrictnessLevel.DISABLED) {
            return;
        }

        QueryValidationResult result = performValidation(sql, tableName);

        switch (strictness) {
            case STRICT:
                if (!result.isValid()) {
                    String message = createValidationErrorMessage(sql, tableName, result);
                    logger.error("Query validation failed: {}", message);
                    throw new QueryValidationException(message);
                }
                break;

            case WARN:
                if (!result.isValid()) {
                    String message = createValidationErrorMessage(sql, tableName, result);
                    logger.warn("Query validation warning: {}", message);
                }
                break;

            case LOG:
                if (!result.isValid()) {
                    String message = createValidationErrorMessage(sql, tableName, result);
                    logger.info("Query validation info: {}", message);
                }
                break;
        }

        if (result.isValid()) {
            logger.debug("Query validation passed for sharded entity: {}", sanitizeSql(sql));
        }
    }

    /**
     * Perform the actual validation logic.
     */
    private QueryValidationResult performValidation(String sql, String tableName) {
        if (sql == null || sql.trim().isEmpty()) {
            return QueryValidationResult.invalid("SQL query is null or empty");
        }

        String normalizedSql = normalizeSql(sql);

        // Check for tenant column presence
        List<String> foundTenantColumns = findTenantColumns(normalizedSql);

        if (foundTenantColumns.isEmpty()) {
            return QueryValidationResult.invalid("No tenant column found in query");
        }

        // Additional validation for different query types
        return validateByQueryType(normalizedSql, foundTenantColumns, tableName);
    }

    /**
     * Validate based on query type (SELECT, INSERT, UPDATE, DELETE).
     */
    private QueryValidationResult validateByQueryType(String sql, List<String> tenantColumns, String tableName) {
        String queryType = getQueryType(sql);

        switch (queryType.toUpperCase()) {
            case "SELECT":
                return validateSelectQuery(sql, tenantColumns, tableName);
            case "INSERT":
                return validateInsertQuery(sql, tenantColumns, tableName);
            case "UPDATE":
                return validateUpdateQuery(sql, tenantColumns, tableName);
            case "DELETE":
                return validateDeleteQuery(sql, tenantColumns, tableName);
            default:
                // For other query types (DDL, etc.), be less strict
                return QueryValidationResult.valid("Non-DML query type: " + queryType);
        }
    }

    /**
     * Validate SELECT queries.
     */
    private QueryValidationResult validateSelectQuery(String sql, List<String> tenantColumns, String tableName) {
        // Check if tenant column is in WHERE clause
        if (hasWhereClauseWithTenantColumn(sql, tenantColumns)) {
            return QueryValidationResult.valid("SELECT query has tenant filtering in WHERE clause");
        }

        // Check for JOIN conditions with tenant columns
        if (hasJoinWithTenantColumn(sql, tenantColumns)) {
            return QueryValidationResult.valid("SELECT query has tenant filtering in JOIN condition");
        }

        return QueryValidationResult.invalid("SELECT query missing tenant filtering in WHERE clause or JOIN");
    }

    /**
     * Validate INSERT queries.
     */
    private QueryValidationResult validateInsertQuery(String sql, List<String> tenantColumns, String tableName) {
        // For INSERT, check if tenant column is being inserted
        if (hasInsertTenantColumn(sql, tenantColumns)) {
            return QueryValidationResult.valid("INSERT query includes tenant column");
        }

        return QueryValidationResult.invalid("INSERT query missing tenant column in VALUES or SET clause");
    }

    /**
     * Validate UPDATE queries.
     */
    private QueryValidationResult validateUpdateQuery(String sql, List<String> tenantColumns, String tableName) {
        // UPDATE must have tenant column in WHERE clause
        if (hasWhereClauseWithTenantColumn(sql, tenantColumns)) {
            return QueryValidationResult.valid("UPDATE query has tenant filtering in WHERE clause");
        }

        return QueryValidationResult.invalid("UPDATE query missing tenant filtering in WHERE clause");
    }

    /**
     * Validate DELETE queries.
     */
    private QueryValidationResult validateDeleteQuery(String sql, List<String> tenantColumns, String tableName) {
        // DELETE must have tenant column in WHERE clause
        if (hasWhereClauseWithTenantColumn(sql, tenantColumns)) {
            return QueryValidationResult.valid("DELETE query has tenant filtering in WHERE clause");
        }

        return QueryValidationResult.invalid("DELETE query missing tenant filtering in WHERE clause");
    }

    /**
     * Find all tenant columns mentioned in the SQL query.
     */
    private List<String> findTenantColumns(String sql) {
        return tenantColumnPatterns.stream()
                .flatMap(pattern -> {
                    Matcher matcher = pattern.matcher(sql);
                    return matcher.results()
                            .map(matchResult -> matchResult.group(1))
                            .distinct();
                })
                .collect(Collectors.toList());
    }

    /**
     * Check if WHERE clause contains tenant column filtering.
     */
    private boolean hasWhereClauseWithTenantColumn(String sql, List<String> tenantColumns) {
        // Look for WHERE clause with tenant column
        Pattern wherePattern = Pattern.compile("\\bwhere\\b.*?(?=\\border\\s+by\\b|\\bgroup\\s+by\\b|\\bhaving\\b|\\blimit\\b|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher whereMatcher = wherePattern.matcher(sql);

        if (!whereMatcher.find()) {
            return false;
        }

        String whereClause = whereMatcher.group();
        return tenantColumns.stream().anyMatch(tenantCol ->
                containsTenantColumnReference(whereClause, tenantCol));
    }

    /**
     * Check if JOIN clause contains tenant column.
     */
    private boolean hasJoinWithTenantColumn(String sql, List<String> tenantColumns) {
        Pattern joinPattern = Pattern.compile("\\b(?:inner\\s+join|left\\s+join|right\\s+join|full\\s+join|join)\\b.*?\\bon\\b.*?(?=\\bwhere\\b|\\border\\s+by\\b|\\bgroup\\s+by\\b|\\bhaving\\b|\\blimit\\b|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher joinMatcher = joinPattern.matcher(sql);

        while (joinMatcher.find()) {
            String joinClause = joinMatcher.group();
            if (tenantColumns.stream().anyMatch(tenantCol ->
                    containsTenantColumnReference(joinClause, tenantCol))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if INSERT query includes tenant column.
     */
    private boolean hasInsertTenantColumn(String sql, List<String> tenantColumns) {
        // Check INSERT ... VALUES format
        Pattern insertValuesPattern = Pattern.compile("\\binsert\\s+into\\s+\\w+\\s*\\([^)]+\\)\\s*values", Pattern.CASE_INSENSITIVE);
        if (insertValuesPattern.matcher(sql).find()) {
            return tenantColumns.stream().anyMatch(tenantCol ->
                    containsTenantColumnReference(sql, tenantCol));
        }

        // Check INSERT ... SET format
        Pattern insertSetPattern = Pattern.compile("\\binsert\\s+into\\s+\\w+\\s+set\\b", Pattern.CASE_INSENSITIVE);
        if (insertSetPattern.matcher(sql).find()) {
            return tenantColumns.stream().anyMatch(tenantCol ->
                    containsTenantColumnReference(sql, tenantCol));
        }

        return false;
    }

    /**
     * Check if SQL contains a reference to the tenant column.
     */
    private boolean containsTenantColumnReference(String sql, String tenantColumn) {
        // Create pattern for tenant column reference (with optional table alias)
        String pattern = "\\b(?:\\w+\\.)?\\b" + Pattern.quote(tenantColumn) + "\\b";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(sql).find();
    }

    /**
     * Create patterns for detecting tenant columns in SQL.
     */
    private List<Pattern> createTenantColumnPatterns() {
        return shardingConfig.getTenantColumnNames().stream()
                .map(columnName -> {
                    // Pattern to match tenant column with optional table alias
                    String pattern = "\\b(?:\\w+\\.)?(" + Pattern.quote(columnName) + ")\\b";
                    return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                })
                .collect(Collectors.toList());
    }

    /**
     * Normalize SQL for consistent processing.
     */
    private String normalizeSql(String sql) {
        return sql.trim()
                .replaceAll("\\s+", " ")  // Normalize whitespace
                .replaceAll("\\n", " ")   // Remove line breaks
                .toLowerCase();
    }

    /**
     * Get the query type (SELECT, INSERT, UPDATE, DELETE, etc.).
     */
    private String getQueryType(String sql) {
        String[] words = sql.trim().split("\\s+");
        return words.length > 0 ? words[0].toUpperCase() : "UNKNOWN";
    }

    /**
     * Create a detailed validation error message.
     */
    private String createValidationErrorMessage(String sql, String tableName, QueryValidationResult result) {
        StringBuilder message = new StringBuilder();
        message.append("Sharded entity query validation failed: ").append(result.getErrorMessage());

        if (tableName != null) {
            message.append(" (table: ").append(tableName).append(")");
        }

        message.append(". Expected one of tenant columns: ")
                .append(shardingConfig.getTenantColumnNames());

        message.append(". Query: ").append(sanitizeSql(sql));

        return message.toString();
    }

    /**
     * Sanitize SQL for logging (remove sensitive data, truncate if too long).
     */
    private String sanitizeSql(String sql) {
        if (sql == null) return "null";

        String sanitized = sql.replaceAll("'[^']*'", "'***'")  // Hide string literals
                             .replaceAll("\\b\\d+\\b", "***"); // Hide numeric literals

        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200) + "...";
        }

        return sanitized;
    }

    /**
     * Result of query validation.
     */
    public static class QueryValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private QueryValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static QueryValidationResult valid(String message) {
            return new QueryValidationResult(true, message);
        }

        public static QueryValidationResult invalid(String errorMessage) {
            return new QueryValidationResult(false, errorMessage);
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
}