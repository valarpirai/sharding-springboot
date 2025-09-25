package com.valarpirai.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security configuration for the application.
 * Currently configured for development with minimal security.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    /**
     * Password encoder for hashing user passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for API endpoints
            .csrf().disable()

            // Disable sessions (stateless)
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)

            // Configure authorization
            .and()
            .authorizeRequests()
                // Allow signup and swagger endpoints
                .antMatchers("/api/signup/**").permitAll()
                .antMatchers("/api/auth/login").permitAll()
                .antMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .antMatchers("/actuator/**").permitAll()
                // All other endpoints require authentication (will be handled by JWT later)
                .anyRequest().permitAll() // Temporarily permit all for development

            // Disable basic auth popup
            .and()
            .httpBasic().disable()

            // Disable form login
            .formLogin().disable();
    }
}