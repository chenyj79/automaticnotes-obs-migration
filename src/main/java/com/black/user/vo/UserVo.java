package com.black.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息VO（不含密码等敏感信息）
 */
@Data
public class UserVo {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String avatarUrl;
    private String role;
    private String status;
    private LocalDateTime createTime;
}
