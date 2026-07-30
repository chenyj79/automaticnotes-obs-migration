package com.black.user.enums;

import lombok.Getter;

/**
 * 用户状态枚举
 */
@Getter
public enum UserStatus {
    ACTIVE("正常"),
    DISABLED("已禁用");

    private final String description;

    UserStatus(String description) {
        this.description = description;
    }
}
