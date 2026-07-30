package com.black.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 降级配置
 * 控制AI服务降级到本地算法的行为
 */
@Data
@Component
@ConfigurationProperties(prefix = "fallback")
public class FallbackConfig {

    /**
     * 是否启用AI评分降级
     */
    private boolean scoringFallbackEnabled = true;

    /**
     * 降级日志级别
     */
    private String fallbackLogLevel = "WARN";

    /**
     * 是否记录降级统计信息
     */
    private boolean enableFallbackMetrics = true;
}