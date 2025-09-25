package com.valarpirai.example.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * DTO for account signup responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignupResponse {

    private Long accountId;
    private String accountName;
    private String adminEmail;
    private Long adminUserId;
    private String adminUserName;
    private LocalDateTime createdAt;
    private String message;
    private Boolean demoSetupInProgress;

    /**
     * Create success response.
     */
    public static SignupResponse success(Long accountId, String accountName, String adminEmail,
                                       Long adminUserId, String adminUserName, LocalDateTime createdAt) {
        return SignupResponse.builder()
                .accountId(accountId)
                .accountName(accountName)
                .adminEmail(adminEmail)
                .adminUserId(adminUserId)
                .adminUserName(adminUserName)
                .createdAt(createdAt)
                .message("Account created successfully. Demo environment is being set up in the background.")
                .demoSetupInProgress(true)
                .build();
    }
}