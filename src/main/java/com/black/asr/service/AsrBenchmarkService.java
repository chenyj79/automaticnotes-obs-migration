package com.black.asr.service;

import com.black.asr.config.WhisperAsrProperties;
import com.black.asr.po.AsrBenchmarkResult;
import com.black.asr.po.Video;
import com.black.asr.repository.AsrBenchmarkRepository;
import com.black.asr.repository.VideoRepository;
import com.black.asr.service.impl.FallbackAsrService;
import com.black.asr.util.AsrAccuracyUtils;
import com.black.asr.vo.VideoSegmentVo;
import com.black.asr.vo.VideoVo;
import com.black.exception.BusinessException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ASR A/B 基准测试服务
 * 对同一视频分别调用两个 ASR 引擎，记录各自的转写结果和性能指标，
 * 并计算双引擎之间的 CER/WER 互比指标。
 * 支持后续上传人工参考文本来计算绝对准确率。
 *
 * 此服务独立于主处理流程，不影响正常的视频 Pipeline。
 */
@Slf4j
@Service
public class AsrBenchmarkService {

    private final AsrService aliyunAsrService;
    private final AsrService whisperAsrService;
    private final AsrBenchmarkRepository benchmarkRepository;
    private final TranscriptionMetricsService metricsService;
    private final VideoRepository videoRepository;
    private final WhisperAsrProperties whisperProperties;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public AsrBenchmarkService(@Qualifier("aliyunTingwuAsrServiceImpl") AsrService aliyunAsrService,
                               @Qualifier("whisperAsrServiceImpl") AsrService whisperAsrService,
                               AsrBenchmarkRepository benchmarkRepository,
                               TranscriptionMetricsService metricsService,
                               VideoRepository videoRepository,
                               WhisperAsrProperties whisperProperties) {
        this.aliyunAsrService = aliyunAsrService;
        this.whisperAsrService = whisperAsrService;
        this.benchmarkRepository = benchmarkRepository;
        this.metricsService = metricsService;
        this.videoRepository = videoRepository;
        this.whisperProperties = whisperProperties;
    }

    /**
     * 对指定视频运行 A/B 测试：分别调用两个 ASR 引擎
     * 返回包含双引擎结果及 CER/WER 互比指标的 BenchmarkResult
     */
    public AsrBenchmarkResult runBenchmark(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> BusinessException.videoNotFound(videoId));
        VideoVo videoVo = video.toVo();

        // 如果已有 benchmark 结果，覆盖更新
        AsrBenchmarkResult result = benchmarkRepository.findByVideoId(videoId)
                .orElse(new AsrBenchmarkResult());
        result.setVideoId(videoId);
        result.setVideoDuration(video.getDuration());
        result.setFileSize(video.getFileSize());
        result.setVideoName(video.getOriginalFileName());

        // ----- 引擎 A: 阿里云通义听悟 -----
        runAliyunEngine(result, videoVo);

        // ----- 引擎 B: 本地 Whisper -----
        runWhisperEngine(result, videoVo);

        // ----- 计算双引擎互比 CER/WER -----
        computeCrossEngineMetrics(result);

        // ----- 如果之前已有参考文本，同步更新绝对指标 -----
        if (result.getReferenceText() != null && !result.getReferenceText().isBlank()) {
            computeReferenceMetrics(result, result.getReferenceText());
        }

        AsrBenchmarkResult saved = benchmarkRepository.save(result);
        log.info("[Benchmark] A/B 测试完成 - videoId: {}, crossCER: {}, crossWER: {}",
                videoId, saved.getCrossEngineCer(), saved.getCrossEngineWer());
        return saved;
    }

    /**
     * 上传人工参考文本，计算两个引擎各自的绝对 CER/WER
     */
    public AsrBenchmarkResult submitReferenceText(Long benchmarkId, String referenceText) {
        AsrBenchmarkResult result = benchmarkRepository.findById(benchmarkId)
                .orElseThrow(() -> BusinessException.benchmarkNotFound(benchmarkId));

        result.setReferenceText(referenceText);
        computeReferenceMetrics(result, referenceText);

        AsrBenchmarkResult saved = benchmarkRepository.save(result);
        log.info("[Benchmark] 参考文本已提交 - benchmarkId: {}, aliyunCER: {}, whisperCER: {}",
                benchmarkId, saved.getAliyunReferenceCer(), saved.getWhisperReferenceCer());
        return saved;
    }

    /**
     * 获取指定视频的 benchmark 结果
     */
    public AsrBenchmarkResult getBenchmarkByVideoId(Long videoId) {
        return benchmarkRepository.findByVideoId(videoId)
                .orElseThrow(() -> BusinessException.benchmarkNotFound(videoId));
    }

    /**
     * 获取所有 benchmark 结果（论文实验数据导出用）
     */
    public List<AsrBenchmarkResult> getAllBenchmarks() {
        return benchmarkRepository.findAllByOrderByCreatedAtDesc();
    }

    // ===== 内部方法 =====

    private void runAliyunEngine(AsrBenchmarkResult result, VideoVo video) {
        if (!aliyunAsrService.isAvailable()) {
            log.warn("[Benchmark] 阿里云 ASR 不可用，跳过");
            result.setAliyunSuccess(false);
            result.setAliyunErrorMessage("引擎不可用（未配置密钥）");
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            log.info("[Benchmark] 开始阿里云转写 - videoId: {}", video.getId());
            List<VideoSegmentVo> segments = aliyunAsrService.transcribe(video);
            long elapsed = System.currentTimeMillis() - startTime;

            String fullText = buildFullText(segments);
            result.setAliyunSuccess(true);
            result.setAliyunDurationMs(elapsed);
            result.setAliyunSegmentCount(segments != null ? segments.size() : 0);
            result.setAliyunCharCount(fullText.length());
            result.setAliyunFullText(fullText);
            result.setAliyunErrorMessage(null);

            // 同时记录到 metrics 表
            metricsService.recordSuccess(video, FallbackAsrService.ENGINE_ALIYUN, elapsed, segments, false);

            log.info("[Benchmark] 阿里云转写完成 - 耗时: {}ms, 片段: {}, 字符: {}",
                    elapsed, result.getAliyunSegmentCount(), result.getAliyunCharCount());
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[Benchmark] 阿里云转写失败 - videoId: {}", video.getId(), e);
            result.setAliyunSuccess(false);
            result.setAliyunDurationMs(elapsed);
            result.setAliyunErrorMessage(e.getMessage());

            metricsService.recordFailure(video, FallbackAsrService.ENGINE_ALIYUN, elapsed, false, e.getMessage());
        }
    }

    private void runWhisperEngine(AsrBenchmarkResult result, VideoVo video) {
        if (!whisperAsrService.isAvailable()) {
            log.warn("[Benchmark] Whisper ASR 不可用，跳过");
            result.setWhisperSuccess(false);
            result.setWhisperErrorMessage("引擎不可用（未配置服务地址）");
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            log.info("[Benchmark] 开始 Whisper 转写 - videoId: {}", video.getId());
            List<VideoSegmentVo> segments = whisperAsrService.transcribe(video);
            long elapsed = System.currentTimeMillis() - startTime;

            String fullText = buildFullText(segments);
            result.setWhisperSuccess(true);
            result.setWhisperDurationMs(elapsed);
            result.setWhisperSegmentCount(segments != null ? segments.size() : 0);
            result.setWhisperCharCount(fullText.length());
            result.setWhisperFullText(fullText);
            result.setWhisperErrorMessage(null);

            metricsService.recordSuccess(video, FallbackAsrService.ENGINE_WHISPER, elapsed, segments, false);

            log.info("[Benchmark] Whisper 转写完成 - 耗时: {}ms, 片段: {}, 字符: {}",
                    elapsed, result.getWhisperSegmentCount(), result.getWhisperCharCount());
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[Benchmark] Whisper 转写失败 - videoId: {}", video.getId(), e);
            result.setWhisperSuccess(false);
            result.setWhisperDurationMs(elapsed);
            result.setWhisperErrorMessage(e.getMessage());

            metricsService.recordFailure(video, FallbackAsrService.ENGINE_WHISPER, elapsed, false, e.getMessage());
        }
    }

    private void computeCrossEngineMetrics(AsrBenchmarkResult result) {
        if (Boolean.TRUE.equals(result.getAliyunSuccess())
                && Boolean.TRUE.equals(result.getWhisperSuccess())
                && result.getAliyunFullText() != null
                && result.getWhisperFullText() != null) {
            result.setCrossEngineCer(AsrAccuracyUtils.calculateCer(
                    result.getWhisperFullText(), result.getAliyunFullText()));
            result.setCrossEngineWer(AsrAccuracyUtils.calculateWer(
                    result.getWhisperFullText(), result.getAliyunFullText()));
            result.setCrossEnginePuncF1(AsrAccuracyUtils.calculatePunctuationF1(
                    result.getWhisperFullText(), result.getAliyunFullText()));
        }
    }

    private void computeReferenceMetrics(AsrBenchmarkResult result, String referenceText) {
        if (result.getAliyunFullText() != null && Boolean.TRUE.equals(result.getAliyunSuccess())) {
            result.setAliyunReferenceCer(AsrAccuracyUtils.calculateCer(result.getAliyunFullText(), referenceText));
            result.setAliyunReferenceWer(AsrAccuracyUtils.calculateWer(result.getAliyunFullText(), referenceText));
            result.setAliyunReferencePuncF1(AsrAccuracyUtils.calculatePunctuationF1(result.getAliyunFullText(), referenceText));
        }
        if (result.getWhisperFullText() != null && Boolean.TRUE.equals(result.getWhisperSuccess())) {
            result.setWhisperReferenceCer(AsrAccuracyUtils.calculateCer(result.getWhisperFullText(), referenceText));
            result.setWhisperReferenceWer(AsrAccuracyUtils.calculateWer(result.getWhisperFullText(), referenceText));
            result.setWhisperReferencePuncF1(AsrAccuracyUtils.calculatePunctuationF1(result.getWhisperFullText(), referenceText));
        }

        // ----- 运行标点恢复孤立评估 -----
        runIsolatedPuncEvaluation(result, referenceText);
    }

    /**
     * 将转写片段拼接为完整文本（优先使用润色文本）
     */
    private String buildFullText(List<VideoSegmentVo> segments) {
        if (segments == null || segments.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (VideoSegmentVo seg : segments) {
            String text = seg.getPolishedText() != null ? seg.getPolishedText() : seg.getRawText();
            if (text != null && !text.isBlank()) {
                sb.append(text);
            }
        }
        return sb.toString();
    }

    /**
     * 运行标点恢复孤立评估
     * 1. 剥离参考文本中的所有标点 -> 得到纯净文本
     * 2. 调用本地标点恢复模型 -> 得到模型预测带标点文本
     * 3. 计算预测文本 vs 参考文本的 标点 F1
     */
    private void runIsolatedPuncEvaluation(AsrBenchmarkResult result, String referenceText) {
        if (referenceText == null || referenceText.isBlank()) return;

        try {
            // 1. 剥离标点
            String strippedText = AsrAccuracyUtils.stripPunctuation(referenceText);
            if (strippedText.isEmpty()) return;

            // 2. 调用本地服务恢复标点
            String predictedText = callLocalPuncService(strippedText);

            // 3. 计算 F1
            if (predictedText != null) {
                double f1 = AsrAccuracyUtils.calculatePunctuationF1(predictedText, referenceText);
                result.setIsolatedPuncF1(f1);
                log.info("[Benchmark] 标点孤立评估完成 - isolatedPuncF1: {}", f1);
            }
        } catch (Exception e) {
            log.error("[Benchmark] 标点孤立评估失败", e);
        }
    }

    private String callLocalPuncService(String text) throws Exception {
        // 构造请求体
        String requestBody = objectMapper.writeValueAsString(new PuncRequest(text));

        URL url = new URL(whisperProperties.getRestorePuncUrl());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(30000); // 标点恢复通常很快
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            PuncResponse response = objectMapper.readValue(sb.toString(), PuncResponse.class);
            return response.getData();
        } else {
            log.error("调用标点恢复服务失败: HTTP {}", responseCode);
            return null;
        }
    }

    @Data
    private static class PuncRequest {
        private final String text;
    }

    @Data
    private static class PuncResponse {
        private int code;
        private String message;
        private String data;
    }
}
