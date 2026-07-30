package com.black.user.repository;

import com.black.user.po.User;
import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据用户名查找用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 判断用户名是否已存在
     */
    boolean existsByUsername(String username);

    /**
     * 判断邮箱是否已存在
     */
    boolean existsByEmail(String email);

    /**
     * 判断手机号是否已存在
     */
    boolean existsByPhone(String phone);

    /**
     * 分页查询所有用户
     */
    @NonNull
    Page<User> findAll(@NonNull Pageable pageable);
}
