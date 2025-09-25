package com.valarpirai.example.service;

import com.valarpirai.example.dto.UserCreateRequest;
import com.valarpirai.example.dto.UserUpdateRequest;
import com.valarpirai.example.entity.User;
import com.valarpirai.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getAllActiveUsers(Long tenantId) {
        return userRepository.findByAccountIdAndDeletedFalse(tenantId);
    }

    public User getUserById(Long id, Long tenantId) {
        return userRepository.findByIdAndAccountIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    @Transactional
    public User createUser(UserCreateRequest request, Long tenantId) {
        if (userRepository.existsByEmailAndAccountIdAndDeletedFalse(request.getEmail(), tenantId)) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        User user = User.builder()
                .accountId(tenantId)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .roleId(request.getRoleId())
                .active(request.getActive())
                .build();

        user = userRepository.save(user);
        logger.info("Created user: {} for tenant: {}", user.getId(), tenantId);
        return user;
    }

    @Transactional
    public User updateUser(Long id, UserUpdateRequest request, Long tenantId) {
        User user = getUserById(id, tenantId);

        if (StringUtils.hasText(request.getEmail()) &&
            !request.getEmail().equals(user.getEmail()) &&
            userRepository.existsByEmailAndAccountIdAndDeletedFalse(request.getEmail(), tenantId)) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        if (StringUtils.hasText(request.getEmail())) {
            user.setEmail(request.getEmail());
        }
        if (StringUtils.hasText(request.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        if (StringUtils.hasText(request.getFirstName())) {
            user.setFirstName(request.getFirstName());
        }
        if (StringUtils.hasText(request.getLastName())) {
            user.setLastName(request.getLastName());
        }
        if (request.getRoleId() != null) {
            user.setRoleId(request.getRoleId());
        }
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        user = userRepository.save(user);
        logger.info("Updated user: {} for tenant: {}", user.getId(), tenantId);
        return user;
    }

    @Transactional
    public void deleteUser(Long id, Long tenantId) {
        User user = getUserById(id, tenantId);
        user.delete();
        userRepository.save(user);
        logger.info("Deleted user: {} for tenant: {}", id, tenantId);
    }
}