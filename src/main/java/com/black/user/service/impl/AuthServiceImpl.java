package com.black.user.service.impl;

import com.black.user.dto.LoginRequest;
import com.black.user.dto.LoginResponse;
import com.black.user.dto.RegisterRequest;
import com.black.user.enums.UserRole;
import com.black.user.enums.UserStatus;
import com.black.exception.BusinessException;
import com.black.user.po.User;
import com.black.user.repository.UserRepository;
import com.black.user.security.JwtTokenProvider;
import com.black.user.service.AuthService;
import com.black.user.vo.UserVo;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现类
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthServiceImpl(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    @Transactional
    public UserVo register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw BusinessException.usernameExists(request.getUsername());
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.ROLE_USER);
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);
        return savedUser.toVo();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), request.getPassword()));

            String token = jwtTokenProvider.generateToken(authentication);

            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> BusinessException.userNotFound(request.getUsername()));

            return LoginResponse.of(token, user.getUsername(), user.getRole().name());
        } catch (BadCredentialsException e) {
            throw BusinessException.badCredentials();
        } catch (DisabledException e) {
            throw BusinessException.userDisabled();
        }
    }
}
