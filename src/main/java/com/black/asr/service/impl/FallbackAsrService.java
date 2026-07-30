package com.black.asr.service.impl;

import com.black.asr.service.AsrService;
import com.black.asr.service.TranscriptionMetricsService;
import com.black.asr.vo.VideoSegmentVo;
import com.black.asr.vo.VideoVo;
import com.black.exception.BusinessException;
import com.black.task.context.TaskContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ASR 降级策略门面服务（Fallback）
 * 策略：
 * 若阿里云服务 isAvailable() 返回 true，则优先调用阿里云 API 转写。
 * 若阿里云不可用（未配置 Key）或调用过程中抛出任何异常（如额度不足、网络超时等），
 * 则自动降级到本地 Whisper 模型转写。
 * 若 Whisper 也不可用或同样失败，则把异常向上抛出。
 * 此 Bean 标注 @Primary，使得 Spring 在注入 AsrService 时，默认选择此实现。
 * <p>
 * 同时负责：
 * - 通过 ThreadLocal 暴露本次转写实际使用的引擎名称（供 Pipeline 读取）
 * - 委托 TranscriptionMetricsService 记录每次调用的性能指标
 */
@Slf4j
@Primary
@Service("fallbackAsrService")
public class FallbackAsrService implements AsrService {

    public static final String ENGINE_ALIYUN = "ALIYUN_TINGWU";
    public static final String ENGINE_WHISPER = "LOCAL_WHISPER";

    private final AsrService aliyunAsrService;
    private final AsrService whisperAsrService;
    private final TranscriptionMetricsService metricsService;

    public FallbackAsrService(@Qualifier("aliyunTingwuAsrServiceImpl") AsrService aliyunAsrService,
            @Qualifier("whisperAsrServiceImpl") AsrService whisperAsrService,
            TranscriptionMetricsService metricsService) {
        this.aliyunAsrService = aliyunAsrService;
        this.whisperAsrService = whisperAsrService;
        this.metricsService = metricsService;
    }

    @Override
    public List<VideoSegmentVo> transcribe(VideoVo video) {
        // ---- 优先尝试阿里云 ----
        if (aliyunAsrService.isAvailable()) {
            long startTime = System.currentTimeMillis();
            try {
                log.info("[ASR] 使用阿里云通义听悟转写 - videoId: {}", video.getId());
                List<VideoSegmentVo> result = aliyunAsrService.transcribe(video);
                long elapsed = System.currentTimeMillis() - startTime;

                log.info("[ASR] 阿里云转写成功 - videoId: {}, 片段数: {}, 耗时: {}ms",
                        video.getId(), result != null ? result.size() : 0, elapsed);

                metricsService.recordSuccess(video, ENGINE_ALIYUN, elapsed, result, false);

                // 记录当前使用的引擎到任务上下文
                TaskContextHolder.setAsrEngine(ENGINE_ALIYUN);
                return result;
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                log.warn("[ASR] 阿里云转写失败，准备降级到本地 Whisper 模型 - videoId: {}, 原因: {}",
                        video.getId(), e.getMessage());

                metricsService.recordFailure(video, ENGINE_ALIYUN, elapsed, false, e.getMessage());
            }
        } else {
            log.info("[ASR] 阿里云服务未配置或不可用，直接使用本地 Whisper 模型 - videoId: {}", video.getId());
        }

        // ---- 降级到 Whisper ----
        if (!whisperAsrService.isAvailable()) {
            throw BusinessException.configIncomplete();
        }

        boolean isFallback = aliyunAsrService.isAvailable(); // 阿里云可用但失败了才算降级
        long startTime = System.currentTimeMillis();
        try {
            log.info("[ASR] 使用本地 Whisper 模型转写 - videoId: {}", video.getId());
            List<VideoSegmentVo> result = whisperAsrService.transcribe(video);
            long elapsed = System.currentTimeMillis() - startTime;

            log.info("[ASR] Whisper 转写成功 - videoId: {}, 片段数: {}, 耗时: {}ms",
                    video.getId(), result != null ? result.size() : 0, elapsed);

            metricsService.recordSuccess(video, ENGINE_WHISPER, elapsed, result, isFallback);

            // 记录当前使用的引擎到任务上下文
            TaskContextHolder.setAsrEngine(ENGINE_WHISPER);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.warn("[ASR] 本地 Whisper 模型转写失败 - videoId: {}, 原因: {}",
                    video.getId(), e.getMessage());
            metricsService.recordFailure(video, ENGINE_WHISPER, elapsed, isFallback, e.getMessage());
            throw e; // Whisper 也失败，向上抛出
        }
    }

    /**
     * 只要任意一个后端可用，整体即视为可用。
     */
    @Override
    public boolean isAvailable() {
        return aliyunAsrService.isAvailable() || whisperAsrService.isAvailable();
    }
}
