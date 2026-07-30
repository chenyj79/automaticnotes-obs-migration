package com.black.knowledge.service;

import com.black.exception.BusinessException;
import com.black.knowledge.config.FallbackConfig;
import com.black.knowledge.dto.ai.ExtractedKnowledgePoint;
import com.black.knowledge.dto.ai.VideoScoreResult;
import com.black.knowledge.po.VideoScore;
import com.black.knowledge.repository.VideoScoreRepository;
import com.black.knowledge.vo.VideoScoreVo;
import com.alibaba.fastjson2.JSON;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 视频评分服务
 * 基于提取的知识点评估视频的知识密度和有效性
 * 评分计算由管道流程触发，提取结果由管道传入
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoScoreService {

        private final VideoScoreRepository scoreRepository;
        private final AiService aiService;
        private final FallbackMetricsService fallbackMetricsService;
        private final FallbackConfig fallbackConfig;
        private final LocalVideoScoreService localVideoScoreService;

        /**
         * 根据提取的知识点计算视频评分
         *
         * @param videoId         视频ID
         * @param extractedPoints 提取的知识点列表（含action类型、content、timestamps）
         * @param duration        视频时长（秒）
         * @param subject         学科/主题
         */
        public VideoScoreVo calculateScore(Long videoId,
                        List<ExtractedKnowledgePoint> extractedPoints,
                        int duration, String subject) {
                // 1. 统计NEW和UPDATE的数量
                int totalCount = extractedPoints.size();
                int newCount = (int) extractedPoints.stream()
                                .filter(p -> ExtractedKnowledgePoint.Action.NEW == p.getAction()).count();
                int updateCount = (int) extractedPoints.stream()
                                .filter(p -> ExtractedKnowledgePoint.Action.UPDATE == p.getAction()).count();
                int redundantCount = totalCount - newCount - updateCount;
                log.info("开始计算视频评分 - videoId: {}, 总点数: {}, NEW: {}, UPDATE: {}, REDUNDANT: {}", 
                                videoId, totalCount, newCount, updateCount, redundantCount);

                // 2. 构建知识点JSON（供AI评分，UPDATE类型携带原始内容供对比）
                String knowledgePointsJson = JSON.toJSONString(extractedPoints.stream()
                                .map(p -> {
                                        java.util.Map<String, Object> map = new java.util.HashMap<>();
                                        map.put("title", p.getTitle() != null ? p.getTitle() : "");
                                        if (ExtractedKnowledgePoint.Action.REDUNDANT != p.getAction()) {
                                            map.put("content", p.getContent() != null ? p.getContent() : "");
                                        }
                                        map.put("action", p.getAction() != null ? p.getAction().name() : "NEW");
                                        map.put("timestamps",
                                                        p.getTimestamps() != null ? p.getTimestamps() : List.of());
                                        if (ExtractedKnowledgePoint.Action.UPDATE == p.getAction() && p.getExistingContent() != null) {
                                                map.put("existingContent", p.getExistingContent());
                                        }
                                        return map;
                                })
                                .collect(Collectors.toList()));

                // 3. 调用AI评分（带降级机制）
                VideoScoreResult scoreResult;
                try {
                    scoreResult = aiService.scoreVideo(
                                    knowledgePointsJson, duration, totalCount, newCount, updateCount, redundantCount, subject);
                    log.info("AI评分完成 - videoId: {}, 总分: {}, 密度分: {}, 效果分: {}", 
                                    videoId, scoreResult.getTotalScore(), scoreResult.getDensityScore(), scoreResult.getEffectivenessScore());
                } catch (Exception e) {
                    log.warn("AI评分失败，降级到本地算法 - videoId: {}, 错误: {}", videoId, e.getMessage());
                    
                    // 降级到本地算法评分
                    scoreResult = localVideoScoreService.calculateLocalScore(
                                    videoId, extractedPoints, duration, subject);
                    log.info("本地算法评分完成（降级） - videoId: {}, 总分: {}, 密度分: {}, 效果分: {}", 
                                    videoId, scoreResult.getTotalScore(), scoreResult.getDensityScore(), scoreResult.getEffectivenessScore());
                }

                // 4. 保存评分结果
                VideoScore score = new VideoScore();
                score.setVideoId(videoId);
                score.setDensityScore(scoreResult.getDensityScore());
                score.setEffectivenessScore(scoreResult.getEffectivenessScore());
                score.setTotalScore(scoreResult.getTotalScore());
                score.setKnowledgeCount(totalCount);
                score.setExplanation(scoreResult.getExplanation());
                scoreRepository.save(score);

                return score.toVo();
        }

        /**
         * 查询视频评分
         */
        public VideoScoreVo getScoreByVideoId(Long videoId) {
                return scoreRepository.findByVideoId(videoId)
                                .orElseThrow(() -> BusinessException.scoreNotFound(videoId)).toVo();
        }
}