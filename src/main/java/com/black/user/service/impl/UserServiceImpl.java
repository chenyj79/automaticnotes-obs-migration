package com.black.user.service.impl;

import com.black.constant.AppConstants;
import com.black.user.dto.ChangePasswordRequest;
import com.black.user.dto.UpdateProfileRequest;
import com.black.user.enums.UserRole;
import com.black.user.enums.UserStatus;
import com.black.exception.BusinessException;
import com.black.user.po.User;
import com.black.user.repository.UserRepository;
import com.black.user.service.UserService;
import com.black.user.vo.UserVo;
import com.black.util.ObsUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


/**
 * 用户服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObsUtil obsUtil;

    @Override
    public UserVo getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> BusinessException.userNotFound(username)).toVo();
    }

    @Override
    public UserVo getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> BusinessException.userNotFoundById(id)).toVo();
    }

    @Override
    @Transactional
    public UserVo updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.userNotFoundById(userId));

        // 如果要修改邮箱，检查是否已被其他用户使用
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw BusinessException.emailExists(request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
            if(userRepository.existsByPhone(request.getPhone())){
                throw BusinessException.phoneExists(request.getPhone());
            }
            user.setPhone(request.getPhone());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().equals(user.getAvatarUrl())) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        return userRepository.save(user).toVo();
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.userNotFoundById(userId));

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw BusinessException.oldPasswordIncorrect();
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public Page<UserVo> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(User::toVo);
    }

    @Override
    @Transactional
    public UserVo updateUserStatus(Long userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.userNotFoundById(userId));

        try {
            user.setStatus(UserStatus.valueOf(status));
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalidUserStatus(status);
        }

        return userRepository.save(user).toVo();
    }

    @Override
    @Transactional
    public UserVo updateUserRole(Long userId, String role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.userNotFoundById(userId));

        try {
            user.setRole(UserRole.valueOf(role));
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalidUserRole(role);
        }

        return userRepository.save(user).toVo();
    }

    @Override
    @Transactional
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.userNotFoundById(userId));

        ObsUtil.UploadResult res = obsUtil.uploadFile(file, AppConstants.Oss.AVATAR_FOLDER);
        String avatarUrl = res.getUrl();
        
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        
        return avatarUrl;
    }
}
