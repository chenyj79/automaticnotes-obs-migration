package com.black.user.service;

import com.black.user.dto.LoginRequest;
import com.black.user.dto.LoginResponse;
import com.black.user.dto.RegisterRequest;
import com.black.user.vo.UserVo;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 注册成功的用户信息
     */
    UserVo register(RegisterRequest request);

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应（包含JWT Token）
     */
    LoginResponse login(LoginRequest request);
}
