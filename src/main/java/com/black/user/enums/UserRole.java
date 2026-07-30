package com.black.user.enums;

import lombok.Getter;

/**
 * 用户角色枚举
 */
@Getter
public enum UserRole {
    ROLE_USER("普通用户"),
    ROLE_ADMIN("管理员");

    private final String description;

    UserRole(String description) {
        this.description = description;
    }
}
