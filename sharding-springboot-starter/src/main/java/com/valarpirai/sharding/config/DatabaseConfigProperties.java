package com.valarpirai.sharding.config;

import lombok.Data;

/**
 * Configuration properties for database connection settings.
 * Represents master or replica database configuration.
 */
@Data
public class DatabaseConfigProperties {

    private String url;
    private String username;
    private String password;
    private String driverClassName;
}