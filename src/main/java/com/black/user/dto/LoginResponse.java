package com.black.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应DTO
 */
@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType;
    private String username;
    private String role;

    public static LoginResponse of(String token, String username, String role) {
        return new LoginResponse(token, "Bearer", username, role);
    }
}
