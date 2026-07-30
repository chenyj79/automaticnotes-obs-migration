package com.black.knowledge.service;

import com.black.asr.po.VideoSegment;
import com.black.asr.repository.VideoSegmentRepository;
import com.black.asr.vo.VideoSegmentVo;
import com.black.asr.vo.VideoVo;
import com.black.exception.BusinessException;
import com.black.knowledge.dto.ai.ExtractedKnowledgePoint;
import com.black.knowledge.repository.KnowledgePointVideoRefRepository;
import com.black.knowledge.repository.VideoScoreRepository;
import com.black.knowledge.po.VideoScore;
import com.black.knowledge.vo.KnowledgeFrameworkVo;
import com.black.knowledge.vo.VideoScoreVo;
import com.black.task.context.TaskContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import com.black.knowledge.vo.VideoProcessResultVo;
import com.black.knowledge.po.KnowledgePointDraft;
import com.black.knowledge.enums.DraftStatus;
import com.black.task.enums.TaskStatus;
import com.black.asr.service.AsrService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.black.asr.repository.VideoRepository;
import com.black.task.po.VideoTask;
import com.black.task.service.VideoTaskService;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePipelineService {

    private final KnowledgePointService knowledgePointService;
    private final VideoScoreService videoScoreService;
    private final KnowledgeFrameworkService frameworkService;
    private final VideoTaskService videoTaskService;
    private final VideoRepository videoRepository;
    private final AsrService asrService;
    private final AiService aiService;
    private final VideoSegmentRepository videoSegmentRepository;
    private final KnowledgePointVideoRefRepository knowledgePointVideoRefRepository;
    private final VideoScoreRepository videoScoreRepository;
    private final TransactionTemplate transactionTemplate;
    private final KnowledgePointDraftService draftService;
    private final LocalVideoScoreService localVideoScoreService;
    private final FallbackMetricsService fallbackMetricsService;

    public void executePipeline(Long taskId) {
        TaskContextHolder.setTaskId(taskId);
        try {
            VideoTask task = videoTaskService.getTaskById(taskId);
            TaskStatus currentStatus = task.getStatus();
            Long videoId = task.getVideoId();
            Long frameworkId = task.getFrameworkId();

            log.info("执行管道处理 - taskId: {}, videoId: {}, 当前状态: {}, 将从阶段 {} 开始",
                    taskId, videoId, currentStatus, currentStatus.getDescription());

            VideoVo videoVo = videoRepository.findById(videoId)
                    .orElseThrow(() -> BusinessException.videoNotFound(videoId)).toVo();

            // ===== 阶段 1: 转写 =====
            List<VideoSegmentVo> transcribeResult;
            if (shouldRunStage(currentStatus, TaskStatus.STATUS_TRANSCRIPTING)) {
                transcribeResult = executeTranscriptionStage(task, videoVo);
            } else {
                transcribeResult = loadExistingSegments(task, videoId);
                log.info("断点续跑：使用已有转写结果 - taskId: {}, 片段数: {}", taskId, transcribeResult.size());
            }

            // ===== 生成视频简介 =====
            if (videoVo.getSummary() == null || videoVo.getSummary().isEmpty()) {
                log.info("开始生成视频简介 - taskId: {}", taskId);
                String transcriptText = transcribeResult.stream()
                        .map(VideoSegmentVo::getRawText)
                        .collect(Collectors.joining(" "));
                String summary = aiService.generateVideoSummary(transcriptText);
                videoVo.setSummary(summary);

                // Update the video entity
                com.black.asr.po.Video video = videoRepository.findById(videoId).orElseThrow();
                video.setSummary(summary);
                videoRepository.save(video);
                log.info("视频简介生成完成 - taskId: {}", taskId);
            }

            // ===== 阶段 2: 知识提取 =====
            List<ExtractedKnowledgePoint> extractedPoints;
            if (shouldRunStage(currentStatus, TaskStatus.STATUS_EXTRACTING)) {
                extractedPoints = executeExtractionStage(task, videoId, frameworkId, transcribeResult);
            } else {
                extractedPoints = loadExtractedPointsSnapshot(task);
                log.info("断点续跑：从快照恢复知识点 - taskId: {}, 知识点数: {}", taskId, extractedPoints.size());
            }

            // ===== 阶段 2.5: 保存草稿 =====
            log.info("开始保存草稿 - taskId: {}", taskId);
            List<KnowledgePointDraft> drafts = extractedPoints.stream()
                    .map(point -> {
                        KnowledgePointDraft draft = new KnowledgePointDraft();
                        draft.setVideoId(videoId);
                        draft.setFrameworkId(frameworkId);
                        draft.setTitle(point.getTitle());
                        draft.setContent(point.getContent());
                        draft.setCategory(point.getCategory());
                        draft.setAction(point.getAction());
                        draft.setExistingPointId(point.getExistingPointId());
                        if (point.getTimestamps() != null && !point.getTimestamps().isEmpty()) {
                            draft.setTimestamps(JSON.toJSONString(point.getTimestamps()));
                        }
                        draft.setStatus(DraftStatus.PENDING);
                        return draft;
                    })
                    .collect(Collectors.toList());
            draftService.saveDrafts(drafts);
            log.info("保存草稿完成 - taskId: {}, 数量: {}", taskId, drafts.size());

            // ===== 阶段 3: 评分 =====
            log.info("开始视频评分 - taskId: {}", taskId);
            prepareStage(task, TaskStatus.STATUS_SCORING);

            KnowledgeFrameworkVo framework = frameworkService.getFrameworkDetail(frameworkId);
            int duration = videoVo.getDuration() != null ? videoVo.getDuration() : 0;
            VideoScoreVo score;
            try {
                score = videoScoreService.calculateScore(
                        videoId, extractedPoints, duration, framework.getSubject());
                log.info("视频评分完成 - taskId: {}, 总分: {}", taskId, score.getTotalScore());
            } catch (Exception e) {
                log.warn("视频评分失败，降级到本地算法 - taskId: {}, 错误: {}", taskId, e.getMessage());
                fallbackMetricsService.recordFallback(FallbackMetricsService.FallbackType.AI_SCORING, e.getMessage());

                var localScoreResult = localVideoScoreService.calculateLocalScore(
                        videoId, extractedPoints, duration, framework.getSubject());

                VideoScore localScore = new VideoScore();
                localScore.setVideoId(videoId);
                localScore.setDensityScore(localScoreResult.getDensityScore());
                localScore.setEffectivenessScore(localScoreResult.getEffectivenessScore());
                localScore.setTotalScore(localScoreResult.getTotalScore());
                localScore.setKnowledgeCount(extractedPoints.size());
                localScore.setExplanation(localScoreResult.getExplanation());
                videoScoreRepository.save(localScore);

                score = localScore.toVo();
                log.info("本地算法评分完成（降级） - taskId: {}, 总分: {}", taskId, score.getTotalScore());
            }

            // ===== 完成 =====
            VideoProcessResultVo resultVo = VideoProcessResultVo.builder()
                    .video(videoVo)
                    .extractedPoints(extractedPoints)
                    .score(score)
                    .build();

            videoTaskService.completeTask(task, JSON.toJSONString(resultVo));
            log.info("视频处理任务成功完成 - taskId: {}", taskId);
        } finally {
            TaskContextHolder.clear();
        }
    }

    // ===== 内部处理方法 =====

    private void prepareStage(VideoTask task, TaskStatus stage) {
        transactionTemplate.executeWithoutResult(status -> {
            videoTaskService.updateStatus(task, stage);
            doCleanupStageData(task.getVideoId(), stage);
        });
    }

    private void doCleanupStageData(Long videoId, TaskStatus stage) {
        switch (stage) {
            case STATUS_TRANSCRIPTING -> {
                videoSegmentRepository.deleteByVideoId(videoId);
                log.debug("清理残留转写片段 - videoId: {}", videoId);
            }
            case STATUS_EXTRACTING -> {
                knowledgePointVideoRefRepository.deleteByVideoId(videoId);
                log.debug("清理残留知识点关联 - videoId: {}", videoId);
            }
            case STATUS_SCORING -> {
                videoScoreRepository.deleteByVideoId(videoId);
                log.debug("清理残留评分数据 - videoId: {}", videoId);
            }
            default -> {
            }
        }
    }

    private List<VideoSegmentVo> executeTranscriptionStage(VideoTask task, VideoVo videoVo) {
        Long taskId = task.getId();
        prepareStage(task, TaskStatus.STATUS_TRANSCRIPTING);

        long startTime = System.currentTimeMillis();
        List<VideoSegmentVo> transcribeResult = asrService.transcribe(videoVo);
        long elapsed = System.currentTimeMillis() - startTime;

        log.info("转写完成 - taskId: {}, 得到 {} 条片段, 耗时 {}ms",
                taskId, transcribeResult != null ? transcribeResult.size() : 0, elapsed);

        task.setAsrEngine(TaskContextHolder.getAsrEngine());
        task.setTranscriptionDurationMs(elapsed);
        videoTaskService.save(task);

        return transcribeResult;
    }

    private List<ExtractedKnowledgePoint> executeExtractionStage(VideoTask task,
            Long videoId, Long frameworkId,
            List<VideoSegmentVo> transcribeResult) {
        Long taskId = task.getId();
        prepareStage(task, TaskStatus.STATUS_EXTRACTING);

        KnowledgeFrameworkVo framework = frameworkService.getFrameworkDetail(frameworkId);

        StringBuilder transcriptBuilder = new StringBuilder();
        for (VideoSegmentVo seg : transcribeResult) {
            String text = seg.getPolishedText() != null ? seg.getPolishedText() : seg.getRawText();
            transcriptBuilder.append(String.format("[%d-%d] %s\n", seg.getStartTime(), seg.getEndTime(), text));
        }
        String transcriptText = transcriptBuilder.toString();

        log.info("开始知识提取 - taskId: {}", taskId);
        List<ExtractedKnowledgePoint> extractedPoints = knowledgePointService.extractFromVideo(
                videoId, frameworkId, transcriptText, framework.getSubject());

        log.info("知识提取完成 - taskId: {}, 总计: {}, NEW: {}, UPDATE: {}, REDUNDANT: {}",
                taskId,
                extractedPoints.size(),
                extractedPoints.stream().filter(p -> p.getAction() == ExtractedKnowledgePoint.Action.NEW).count(),
                extractedPoints.stream().filter(p -> p.getAction() == ExtractedKnowledgePoint.Action.UPDATE).count(),
                extractedPoints.stream().filter(p -> p.getAction() == ExtractedKnowledgePoint.Action.REDUNDANT)
                        .count());

        saveExtractedPointsSnapshot(task, extractedPoints);

        return extractedPoints;
    }

    // ===== 辅助方法 =====

    private boolean shouldRunStage(TaskStatus currentStatus, TaskStatus targetStage) {
        return currentStatus.stageOrder() <= targetStage.stageOrder();
    }

    private List<VideoSegmentVo> loadExistingSegments(VideoTask task, Long videoId) {
        List<VideoSegment> segments = videoSegmentRepository.findByVideoId(videoId);
        if (segments.isEmpty()) {
            log.warn("断点续跑发现转写片段丢失，自愈回退到转写阶段 - taskId: {}, videoId: {}",
                    task.getId(), videoId);
            prepareStage(task, TaskStatus.STATUS_TRANSCRIPTING);
            throw BusinessException.segmentMissing();
        }
        return segments.stream().map(VideoSegment::toVo).toList();
    }

    private void saveExtractedPointsSnapshot(VideoTask task, List<ExtractedKnowledgePoint> points) {
        task.setExtractedPointsJson(JSON.toJSONString(points));
        videoTaskService.save(task);
        log.debug("已保存知识提取快照 - taskId: {}, 知识点数: {}", task.getId(), points.size());
    }

    private List<ExtractedKnowledgePoint> loadExtractedPointsSnapshot(VideoTask task) {
        String json = task.getExtractedPointsJson();
        if (json == null || json.isBlank()) {
            log.warn("断点续跑发现知识提取快照丢失，自愈回退到提取阶段 - taskId: {}", task.getId());
            prepareStage(task, TaskStatus.STATUS_EXTRACTING);
            throw BusinessException.extractedPointMissing();
        }
        return JSON.parseObject(json, new TypeReference<>() {
        });
    }
}
