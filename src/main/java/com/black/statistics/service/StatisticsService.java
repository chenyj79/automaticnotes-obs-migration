package com.black.statistics.service;

import com.black.statistics.dto.UserStatisticsDto;

/**
 * 统计数据服务接口
 */
public interface StatisticsService {

    /**
     * 获取用户仪表盘统计数据
     */
    UserStatisticsDto getDashboardStatistics(Long userId);
}
