package com.black.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人信息请求DTO
 */
@Data
public class UpdateProfileRequest {
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone;

    @Size(max = 500, message = "头像URL长度不能超过500")
    private String avatarUrl;
}
