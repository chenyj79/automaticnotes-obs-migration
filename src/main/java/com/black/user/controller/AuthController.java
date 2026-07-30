package com.black.user.controller;

import com.black.constant.AppConstants;
import com.black.user.dto.LoginRequest;
import com.black.user.dto.LoginResponse;
import com.black.user.dto.RegisterRequest;
import com.black.model.ProcessResult;
import com.black.user.service.AuthService;
import com.black.user.vo.UserVo;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 * 提供用户注册和登录接口（无需认证即可访问）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<ProcessResult<UserVo>> register(@Valid @RequestBody RegisterRequest request) {
        UserVo userVo = authService.register(request);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.REGISTER_SUCCESS, userVo));
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<ProcessResult<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.LOGIN_SUCCESS, response));
    }
}
