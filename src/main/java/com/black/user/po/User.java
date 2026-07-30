package com.black.user.po;

import com.black.user.enums.UserRole;
import com.black.user.enums.UserStatus;
import com.black.user.vo.UserVo;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
@Entity
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password", nullable = false, length = 200)
    private String password;

    @Column(name = "email", unique = true, length = 100)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.ROLE_USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 转换为VO对象（不含密码等敏感信息）
     */
    public UserVo toVo() {
        UserVo vo = new UserVo();
        vo.setId(id);
        vo.setUsername(username);
        vo.setEmail(email);
        vo.setPhone(phone);
        vo.setAvatarUrl(avatarUrl);
        vo.setRole(role.name());
        vo.setStatus(status.name());
        vo.setCreateTime(createTime);
        return vo;
    }
}
