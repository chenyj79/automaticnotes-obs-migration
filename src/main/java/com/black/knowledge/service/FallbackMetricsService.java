package com.black.knowledge.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 降级统计服务
 * 记录降级发生的次数和原因
 */
@Slf4j
@Service
public class FallbackMetricsService {

    /**
     * 降级事件类型
     */
    public enum FallbackType {
        ASR_TRANSCRIBE,
        AI_SCORING,
        AI_EXTRACTION
    }

    /**
     * 降级统计信息
     */
    private static class FallbackStats {
        private final AtomicLong totalCount = new AtomicLong(0);
        @Getter
        private final ConcurrentHashMap<String, AtomicLong> reasonCounts = new ConcurrentHashMap<>();

        public void increment(String reason) {
            totalCount.incrementAndGet();
            reasonCounts.computeIfAbsent(reason, k -> new AtomicLong(0)).incrementAndGet();
        }

        public long getTotalCount() {
            return totalCount.get();
        }

    }

    /**
     * 各类降级的统计信息
     */
    private final ConcurrentHashMap<FallbackType, FallbackStats> statsMap = new ConcurrentHashMap<>();

    /**
     * 记录降级事件
     *
     * @param type   降级类型
     * @param reason 降级原因
     */
    public void recordFallback(FallbackType type, String reason) {
        statsMap.computeIfAbsent(type, k -> new FallbackStats()).increment(reason);
        log.info("降级事件记录 - 类型: {}, 原因: {}", type, reason);
    }

    /**
     * 获取降级统计信息
     *
     * @param type 降级类型
     * @return 统计信息
     */
    public FallbackStats getStats(FallbackType type) {
        return statsMap.get(type);
    }

    /**
     * 获取所有降级统计信息
     *
     * @return 所有统计信息
     */
    public ConcurrentHashMap<FallbackType, FallbackStats> getAllStats() {
        return statsMap;
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        statsMap.clear();
        log.info("降级统计信息已重置");
    }

    /**
     * 获取降级统计摘要
     *
     * @return 统计摘要
     */
    public String getStatsSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("降级统计摘要:\n");
        
        for (FallbackType type : FallbackType.values()) {
            FallbackStats stats = statsMap.get(type);
            if (stats != null) {
                summary.append(String.format("  %s: 总计 %d 次\n", type, stats.getTotalCount()));
                
                for (String reason : stats.getReasonCounts().keySet()) {
                    long count = stats.getReasonCounts().get(reason).get();
                    summary.append(String.format("    - %s: %d 次\n", reason, count));
                }
            } else {
                summary.append(String.format("  %s: 无降级记录\n", type));
            }
        }
        
        return summary.toString();
    }
}