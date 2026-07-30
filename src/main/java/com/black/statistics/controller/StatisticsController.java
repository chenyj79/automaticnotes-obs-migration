package com.black.statistics.controller;

import com.black.constant.AppConstants;
import com.black.model.ProcessResult;
import com.black.statistics.dto.UserStatisticsDto;
import com.black.statistics.service.StatisticsService;
import com.black.user.annotation.CurrentUser;
import com.black.user.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计数据控制器
 * 提供仪表盘统计数据等接口
 */
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 获取用户仪表盘统计数据
     */
    @GetMapping("/user")
    public ResponseEntity<ProcessResult<UserStatisticsDto>> getUserStatistics(
            @CurrentUser LoginUser loginUser) {
        UserStatisticsDto stats = statisticsService.getDashboardStatistics(loginUser.getId());
        return ResponseEntity.ok(ProcessResult.success(AppConstants.Message.QUERY_SUCCESS, stats));
    }
}
