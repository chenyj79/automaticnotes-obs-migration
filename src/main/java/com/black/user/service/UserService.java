package com.black.user.service;

import com.black.user.dto.ChangePasswordRequest;
import com.black.user.dto.UpdateProfileRequest;
import com.black.user.vo.UserVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 获取当前登录用户信息
     *
     * @param username 当前用户名
     * @return 用户信息VO
     */
    UserVo getCurrentUser(String username);

    /**
     * 根据ID获取用户信息
     *
     * @param id 用户ID
     * @return 用户信息VO
     */
    UserVo getUserById(Long id);

    /**
     * 修改个人信息
     *
     * @param userId 当前用户名
     * @param request  修改请求
     * @return 更新后的用户信息
     */
    UserVo updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * 修改密码
     *
     * @param userId 当前用户名
     * @param request  修改密码请求
     */
    void changePassword(Long userId, ChangePasswordRequest request);

    /**
     * 分页查询用户列表（管理员）
     *
     * @param pageable 分页参数
     * @return 用户分页列表
     */
    Page<UserVo> listUsers(Pageable pageable);

    /**
     * 更新用户状态（管理员）
     *
     * @param userId 用户ID
     * @param status 新状态（ACTIVE/DISABLED）
     * @return 更新后的用户信息
     */
    UserVo updateUserStatus(Long userId, String status);

    /**
     * 更新用户角色（管理员）
     *
     * @param userId 用户ID
     * @param role   新角色（ROLE_USER/ROLE_ADMIN）
     * @return 更新后的用户信息
     */
    UserVo updateUserRole(Long userId, String role);

    /**
     * 上传并更新用户头像
     *
     * @param userId 用户ID
     * @param file   头像文件
     * @return 新的头像URL
     */
    String uploadAvatar(Long userId, MultipartFile file);
}
