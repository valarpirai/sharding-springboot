package com.valarpirai.example.service;

import com.valarpirai.example.entity.User;
import com.valarpirai.example.repository.UserRepository;
import com.valarpirai.sharding.context.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    private final UserRepository userRepository;
    private final SecretKey secretKey;
    private final long expirationTime;

    public JwtService(UserRepository userRepository,
                     @Value("${app.jwt.secret:mySecretKey123456789012345678901234567890}") String secret,
                     @Value("${app.jwt.expiration:3600000}") long expirationTime) {
        this.userRepository = userRepository;
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationTime = expirationTime;
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("accountId", user.getAccountId());
        claims.put("roleId", user.getRoleId());
        claims.put("fullName", user.getFullName());

        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public User validateTokenAndGetUser(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Long userId = claims.get("userId", Long.class);
            Long accountId = claims.get("accountId", Long.class);

            TenantContext.setTenantId(accountId);

            try {
                return userRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                        .orElseThrow(() -> new IllegalArgumentException("User not found or inactive"));
            } finally {
                TenantContext.clear();
            }

        } catch (Exception e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid token", e);
        }
    }

    public Long getExpirationTime() {
        return expirationTime / 1000;
    }

    public String extractEmail(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    public Long extractAccountId(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("accountId", Long.class);
    }
}