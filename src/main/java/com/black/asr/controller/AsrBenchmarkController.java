package com.black.asr.controller;

import com.black.asr.po.AsrBenchmarkResult;
import com.black.asr.po.TranscriptionMetrics;
import com.black.asr.service.AsrBenchmarkService;
import com.black.asr.service.TranscriptionMetricsService;
import com.black.asr.service.TranscriptionMetricsService.EngineStats;
import com.black.model.ProcessResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ASR 基准测试与转写指标控制器
 * 提供 A/B 测试、参考文本上传、指标查询和数据导出等接口
 * 主要用于论文实验数据的采集和分析
 */
@Slf4j
@RestController
@RequestMapping("/api/asr")
@RequiredArgsConstructor
public class AsrBenchmarkController {

    private final AsrBenchmarkService benchmarkService;
    private final TranscriptionMetricsService metricsService;

    // ===== A/B 基准测试接口 =====

    /**
     * 对指定视频运行 A/B 测试
     * 分别调用阿里云通义听悟和本地 Whisper 引擎，记录结果并计算互比 CER/WER
     */
    @PostMapping("/benchmark/run/{videoId}")
    public ResponseEntity<ProcessResult<AsrBenchmarkResult>> runBenchmark(@PathVariable Long videoId) {
        log.info("[Benchmark API] 触发 A/B 测试 - videoId: {}", videoId);
        AsrBenchmarkResult result = benchmarkService.runBenchmark(videoId);
        return ResponseEntity.ok(ProcessResult.success("A/B 测试完成", result));
    }

    /**
     * 上传人工参考文本，计算两个引擎各自的绝对 CER/WER
     */
    @PostMapping("/benchmark/{benchmarkId}/reference")
    public ResponseEntity<ProcessResult<AsrBenchmarkResult>> submitReference(
            @PathVariable Long benchmarkId,
            @RequestBody String referenceText) {
        log.info("[Benchmark API] 提交参考文本 - benchmarkId: {}, 文本长度: {}",
                benchmarkId, referenceText != null ? referenceText.length() : 0);
        AsrBenchmarkResult result = benchmarkService.submitReferenceText(benchmarkId, referenceText);
        return ResponseEntity.ok(ProcessResult.success("参考文本已提交，CER/WER 已计算", result));
    }

    /**
     * 获取指定视频的 benchmark 结果
     */
    @GetMapping("/benchmark/video/{videoId}")
    public ResponseEntity<ProcessResult<AsrBenchmarkResult>> getBenchmarkByVideo(@PathVariable Long videoId) {
        AsrBenchmarkResult result = benchmarkService.getBenchmarkByVideoId(videoId);
        return ResponseEntity.ok(ProcessResult.success("获取 benchmark 结果成功", result));
    }

    /**
     * 导出所有 benchmark 数据（论文实验数据导出用）
     */
    @GetMapping("/benchmark/export")
    public ResponseEntity<ProcessResult<List<AsrBenchmarkResult>>> exportBenchmarks() {
        List<AsrBenchmarkResult> results = benchmarkService.getAllBenchmarks();
        return ResponseEntity.ok(ProcessResult.success("导出 benchmark 数据成功", results));
    }

    // ===== 单引擎指标统计接口 =====

    /**
     * 获取按引擎分组的聚合统计数据
     */
    @GetMapping("/metrics/stats")
    public ResponseEntity<ProcessResult<Map<String, EngineStats>>> getStats() {
        Map<String, EngineStats> stats = metricsService.getAggregatedStats();
        return ResponseEntity.ok(ProcessResult.success("获取引擎统计成功", stats));
    }

    /**
     * 获取指定视频的转写指标历史
     */
    @GetMapping("/metrics/video/{videoId}")
    public ResponseEntity<ProcessResult<List<TranscriptionMetrics>>> getVideoMetrics(
            @PathVariable Long videoId) {
        List<TranscriptionMetrics> metrics = metricsService.getMetricsByVideoId(videoId);
        return ResponseEntity.ok(ProcessResult.success("获取视频转写指标成功", metrics));
    }

    /**
     * 导出全部 metrics 数据
     */
    @GetMapping("/metrics/export")
    public ResponseEntity<ProcessResult<List<TranscriptionMetrics>>> exportMetrics() {
        List<TranscriptionMetrics> metrics = metricsService.getAllMetrics();
        return ResponseEntity.ok(ProcessResult.success("导出指标数据成功", metrics));
    }
}
