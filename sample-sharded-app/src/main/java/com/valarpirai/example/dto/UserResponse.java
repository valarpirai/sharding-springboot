package com.valarpirai.example.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private Long accountId;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private Long roleId;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}