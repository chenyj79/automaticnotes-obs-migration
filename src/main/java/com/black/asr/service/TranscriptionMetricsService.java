package com.black.asr.service;

import com.black.asr.po.TranscriptionMetrics;
import com.black.asr.repository.TranscriptionMetricsRepository;
import com.black.asr.vo.VideoSegmentVo;
import com.black.asr.vo.VideoVo;
import com.black.task.context.TaskContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 转写指标采集与聚合查询服务
 * 用于记录每次 ASR 引擎调用的性能数据，并提供聚合统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionMetricsService {

        private final TranscriptionMetricsRepository metricsRepository;

        /**
         * 记录一次成功的转写指标
         * 指标（片段数、字符数等）由本方法内部从 result 和 video 中计算，调用方无需关心
         *
         * @param video      视频信息
         * @param engine     使用的引擎名称
         * @param durationMs 转写耗时（毫秒）
         * @param result     转写结果片段列表
         * @param isFallback 是否为降级调用
         */
        public void recordSuccess(VideoVo video, String engine, long durationMs,
                        List<VideoSegmentVo> result, boolean isFallback) {
                int segmentCount = result != null ? result.size() : 0;
                int totalCharCount = result != null ? result.stream()
                                .mapToInt(s -> {
                                        String text = s.getPolishedText() != null ? s.getPolishedText()
                                                        : s.getRawText();
                                        return text != null ? text.length() : 0;
                                })
                                .sum() : 0;

                TranscriptionMetrics metrics = TranscriptionMetrics.builder()
                                .videoId(video.getId())
                                .taskId(TaskContextHolder.getTaskId()) // 自动获取当前任务 ID
                                .asrEngine(engine)
                                .success(true)
                                .durationMs(durationMs)
                                .segmentCount(segmentCount)
                                .totalCharCount(totalCharCount)
                                .videoDuration(video.getDuration())
                                .fileSize(video.getFileSize())
                                .isFallback(isFallback)
                                .build();

                metricsRepository.save(metrics);
                log.info("[指标] 转写成功 - engine: {}, videoId: {}, taskId: {}, durationMs: {}ms",
                                engine, video.getId(), metrics.getTaskId(), durationMs);
        }

        /**
         * 记录一次失败的转写指标
         *
         * @param video        视频信息
         * @param engine       使用的引擎名称
         * @param durationMs   转写耗时（毫秒）
         * @param isFallback   是否为降级调用
         * @param errorMessage 错误信息
         */
        public void recordFailure(VideoVo video, String engine, long durationMs,
                        boolean isFallback, String errorMessage) {
                TranscriptionMetrics metrics = TranscriptionMetrics.builder()
                                .videoId(video.getId())
                                .taskId(TaskContextHolder.getTaskId()) // 自动获取当前任务 ID
                                .asrEngine(engine)
                                .success(false)
                                .durationMs(durationMs)
                                .segmentCount(0)
                                .totalCharCount(0)
                                .videoDuration(video.getDuration())
                                .fileSize(video.getFileSize())
                                .isFallback(isFallback)
                                .errorMessage(errorMessage)
                                .build();

                metricsRepository.save(metrics);
                log.info("[指标] 转写失败 - engine: {}, videoId: {}, taskId: {}, error: {}",
                                engine, video.getId(), metrics.getTaskId(), errorMessage);
        }

        /**
         * 获取按引擎分组的聚合统计
         */
        public Map<String, EngineStats> getAggregatedStats() {
                List<TranscriptionMetrics> allMetrics = metricsRepository.findAll();
                Map<String, List<TranscriptionMetrics>> grouped = allMetrics.stream()
                                .collect(Collectors.groupingBy(TranscriptionMetrics::getAsrEngine));

                Map<String, EngineStats> result = new HashMap<>();
                for (Map.Entry<String, List<TranscriptionMetrics>> entry : grouped.entrySet()) {
                        result.put(entry.getKey(), calculateStats(entry.getKey(), entry.getValue()));
                }
                return result;
        }

        /**
         * 获取指定视频的转写指标历史
         */
        public List<TranscriptionMetrics> getMetricsByVideoId(Long videoId) {
                return metricsRepository.findByVideoId(videoId);
        }

        /**
         * 获取全部指标数据（导出用）
         */
        public List<TranscriptionMetrics> getAllMetrics() {
                return metricsRepository.findAllByOrderByCreatedAtDesc();
        }

        private EngineStats calculateStats(String engine, List<TranscriptionMetrics> metrics) {
                long totalCalls = metrics.size();
                long successCalls = metrics.stream().filter(TranscriptionMetrics::getSuccess).count();
                long failedCalls = totalCalls - successCalls;

                double avgDurationMs = metrics.stream()
                                .filter(TranscriptionMetrics::getSuccess)
                                .mapToLong(m -> m.getDurationMs() != null ? m.getDurationMs() : 0)
                                .average().orElse(0);

                double avgSegmentCount = metrics.stream()
                                .filter(TranscriptionMetrics::getSuccess)
                                .mapToInt(m -> m.getSegmentCount() != null ? m.getSegmentCount() : 0)
                                .average().orElse(0);

                double avgCharCount = metrics.stream()
                                .filter(TranscriptionMetrics::getSuccess)
                                .mapToInt(m -> m.getTotalCharCount() != null ? m.getTotalCharCount() : 0)
                                .average().orElse(0);

                double successRate = totalCalls > 0 ? (double) successCalls / totalCalls : 0;

                return new EngineStats(engine, totalCalls, successCalls, failedCalls,
                                avgDurationMs, avgSegmentCount, avgCharCount, successRate);
        }

        /**
         * 引擎聚合统计数据
         */
        public record EngineStats(
                        String engine,
                        long totalCalls,
                        long successCalls,
                        long failedCalls,
                        double avgDurationMs,
                        double avgSegmentCount,
                        double avgCharCount,
                        double successRate) {
        }
}
