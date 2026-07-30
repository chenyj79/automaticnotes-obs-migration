package com.black.user.controller;

import com.black.constant.AppConstants;
import com.black.user.annotation.CurrentUser;
import com.black.user.dto.ChangePasswordRequest;
import com.black.user.dto.UpdateProfileRequest;
import com.black.model.ProcessResult;
import com.black.user.security.LoginUser;
import com.black.user.service.UserService;
import com.black.user.vo.UserVo;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户信息控制器
 * 提供当前用户个人信息管理接口（需认证）
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public ResponseEntity<ProcessResult<UserVo>> getCurrentUser(
            @CurrentUser LoginUser loginUser) {
        UserVo userVo = userService.getUserById(loginUser.getId());
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, userVo));
    }


    /**
     * 修改个人信息
     */
    @PutMapping("/profile")
    public ResponseEntity<ProcessResult<UserVo>> updateProfile(
            @CurrentUser LoginUser loginUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserVo userVo = userService.updateProfile(loginUser.getId(), request);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.UPDATE_SUCCESS, userVo));
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    public ResponseEntity<ProcessResult<Void>> changePassword(
            @CurrentUser LoginUser loginUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(loginUser.getId(), request);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.UPDATE_SUCCESS));
    }

    /**
     * 上传头像到OBS并更新用户信息
     */
    @PostMapping("/avatar")
    public ResponseEntity<ProcessResult<String>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @CurrentUser LoginUser loginUser) {
        String avatarUrl = userService.uploadAvatar(loginUser.getId(), file);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.UPLOAD_SUCCESS, avatarUrl));
    }
}
