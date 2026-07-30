package com.black.user.controller;

import com.black.constant.AppConstants;
import com.black.model.ProcessResult;
import com.black.user.service.UserService;
import com.black.user.vo.UserVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员用户管理控制器
 * 提供用户管理接口（需ADMIN角色）
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 分页查询用户列表
     */
    @GetMapping
    public ResponseEntity<ProcessResult<Page<UserVo>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<UserVo> users = userService.listUsers(pageRequest);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, users));
    }

    /**
     * 修改用户状态（启用/禁用）
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<ProcessResult<UserVo>> updateUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        UserVo userVo = userService.updateUserStatus(id, status);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.UPDATE_SUCCESS, userVo));
    }

    /**
     * 修改用户角色
     */
    @PutMapping("/{id}/role")
    public ResponseEntity<ProcessResult<UserVo>> updateUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String role = body.get("role");
        UserVo userVo = userService.updateUserRole(id, role);
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.UPDATE_SUCCESS, userVo));
    }
}
