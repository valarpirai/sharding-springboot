package com.valarpirai.example.service;

import com.valarpirai.example.dto.LoginRequest;
import com.valarpirai.example.dto.LoginResponse;
import com.valarpirai.example.entity.User;
import com.valarpirai.example.repository.UserRepository;
import com.valarpirai.sharding.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request, Long accountId) {
        // Tenant context is already set by ShardSelectorFilter with complete shard info

        try {
            User user = userRepository.findByEmailAndAccountIdAndDeletedFalse(request.getEmail(), accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                throw new IllegalArgumentException("Invalid credentials");
            }

            if (!user.getActive()) {
                throw new SecurityException("User account is inactive");
            }

            String token = jwtService.generateToken(user);
            long expirationTime = jwtService.getExpirationTime();

            logger.info("User {} successfully authenticated for account {}", user.getEmail(), accountId);

            return LoginResponse.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .expiresIn(expirationTime)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .roleId(user.getRoleId())
                    .accountId(user.getAccountId())
                    .build();

        } finally {
            TenantContext.clear();
        }
    }

    public LoginResponse validateToken(String token) {
        try {
            User user = jwtService.validateTokenAndGetUser(token);

            return LoginResponse.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .roleId(user.getRoleId())
                    .accountId(user.getAccountId())
                    .build();

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid token", e);
        }
    }

    public LoginResponse refreshToken(String token) {
        try {
            User user = jwtService.validateTokenAndGetUser(token);
            String newToken = jwtService.generateToken(user);
            long expirationTime = jwtService.getExpirationTime();

            return LoginResponse.builder()
                    .token(newToken)
                    .tokenType("Bearer")
                    .expiresIn(expirationTime)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .roleId(user.getRoleId())
                    .accountId(user.getAccountId())
                    .build();

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid token", e);
        }
    }
}