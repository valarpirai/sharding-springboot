package com.valarpirai.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the application.
 * Currently configured for development with minimal security.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Password encoder for hashing user passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for API endpoints
            .csrf(csrf -> csrf.disable())

            // Disable sessions (stateless)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Configure authorization
            .authorizeHttpRequests(auth -> auth
                // Allow signup and swagger endpoints
                .requestMatchers("/api/signup/**").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // All other endpoints require authentication (will be handled by JWT later)
                .anyRequest().permitAll() // Temporarily permit all for development
            )

            // Disable basic auth popup
            .httpBasic(httpBasic -> httpBasic.disable())

            // Disable form login
            .formLogin(formLogin -> formLogin.disable());

        return http.build();
    }
}