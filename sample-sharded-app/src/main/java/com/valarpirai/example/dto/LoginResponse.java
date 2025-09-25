package com.valarpirai.example.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String tokenType;
    private Long expiresIn;
    private Long userId;
    private String email;
    private String fullName;
    private Long roleId;
    private Long accountId;
    private String message;
}