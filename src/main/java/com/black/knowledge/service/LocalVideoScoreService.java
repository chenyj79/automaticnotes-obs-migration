package com.black.knowledge.service;

import com.black.knowledge.dto.ai.ExtractedKnowledgePoint;
import com.black.knowledge.dto.ai.VideoScoreResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 本地视频评分服务
 * 当AI评分失败时，使用本地算法进行评分
 */
@Slf4j
@Service
public class LocalVideoScoreService {

    /**
     * 本地算法计算视频评分
     * 基于知识点数量、内容质量、时间分布等因素进行评分
     *
     * @param videoId         视频ID
     * @param extractedPoints 提取的知识点列表
     * @param duration        视频时长（秒）
     * @param subject         学科/主题
     * @return 评分结果
     */
    public VideoScoreResult calculateLocalScore(Long videoId,
                                          List<ExtractedKnowledgePoint> extractedPoints,
                                          int duration,
                                          String subject) {
        log.info("开始本地算法评分 - videoId: {}, 知识点数: {}, 时长: {}s", 
                videoId, extractedPoints.size(), duration);

        // 1. 基础分计算
        double baseScore = calculateBaseScore(extractedPoints.size(), duration);
        
        // 2. 密度分计算
        double densityScore = calculateDensityScore(extractedPoints.size(), duration);
        
        // 3. 效果分计算
        double effectivenessScore = calculateEffectivenessScore(extractedPoints);
        
        // 4. 综合评分（10分制，保留一位小数）
        double totalScore = Math.round((baseScore * 0.3 + densityScore * 0.4 + effectivenessScore * 0.3) * 10.0) / 10.0;
        
        // 5. 生成评分说明
        String explanation = generateExplanation(extractedPoints, duration, densityScore, effectivenessScore);
        
        log.info("本地算法评分完成 - videoId: {}, 总分: {}, 密度分: {}, 效果分: {}", 
                videoId, totalScore, densityScore, effectivenessScore);

        return VideoScoreResult.builder()
                .totalScore(totalScore)
                .densityScore(densityScore)
                .effectivenessScore(effectivenessScore)
                .explanation(explanation)
                .build();
    }
    
    /**
     * 计算基础分（10分制）
     * 基于知识点数量和视频时长的比例
     */
    private double calculateBaseScore(int pointCount, int duration) {
        if (duration == 0) return 0.0;
        
        // 知识点密度：每分钟的知识点数量
        double density = (double) pointCount / (duration / 60.0);
        
        // 基础分：密度越高，基础分越高（最高10分）
        double baseScore = Math.min(10.0, density * 2.0);
        
        return Math.max(0.0, Math.round(baseScore * 10.0) / 10.0);
    }
    
    /**
     * 计算知识密度分（10分制）
     * 考虑知识点数量和视频时长的关系
     */
    private double calculateDensityScore(int pointCount, int duration) {
        if (duration == 0) return 0.0;
        
        // 每分钟的知识点数量
        double pointsPerMinute = (double) pointCount / (duration / 60.0);
        
        // 密度评分标准（10分制）
        double densityScore;
        if (pointsPerMinute >= 3.0) {
            densityScore = 10.0; // 高密度
        } else if (pointsPerMinute >= 2.0) {
            densityScore = 8.0;  // 中高密度
        } else if (pointsPerMinute >= 1.0) {
            densityScore = 6.0;  // 中等密度
        } else if (pointsPerMinute >= 0.5) {
            densityScore = 4.0;  // 中低密度
        } else {
            densityScore = 2.0;  // 低密度
        }
        
        return Math.round(densityScore * 10.0) / 10.0;
    }
    
    /**
     * 计算知识效果分（10分制）
     * 基于知识点的内容质量、多样性等
     */
    private double calculateEffectivenessScore(List<ExtractedKnowledgePoint> extractedPoints) {
        if (extractedPoints.isEmpty()) return 0.0;
        
        double totalScore = 0.0;
        
        // 1. 内容质量分（基于内容长度）
        double contentQualityScore = calculateContentQualityScore(extractedPoints);
        totalScore += contentQualityScore * 0.4;
        
        // 2. 多样性分（基于分类多样性）
        double diversityScore = calculateDiversityScore(extractedPoints);
        totalScore += diversityScore * 0.3;
        
        // 3. 新增率分（NEW和UPDATE的比例）
        double noveltyScore = calculateNoveltyScore(extractedPoints);
        totalScore += noveltyScore * 0.3;
        
        return Math.round(totalScore * 10.0) / 10.0;
    }
    
    /**
     * 计算内容质量分（10分制）
     */
    private double calculateContentQualityScore(List<ExtractedKnowledgePoint> extractedPoints) {
        if (extractedPoints.isEmpty()) return 0.0;
        
        int totalLength = 0;
        int validPoints = 0;
        
        for (ExtractedKnowledgePoint point : extractedPoints) {
            String content = point.getContent();
            if (content != null && !content.trim().isEmpty()) {
                totalLength += content.length();
                validPoints++;
            }
        }
        
        if (validPoints == 0) return 0.0;
        
        // 平均内容长度
        double avgLength = (double) totalLength / validPoints;
        
        // 内容质量评分（10分制）
        double qualityScore;
        if (avgLength >= 200) {
            qualityScore = 10.0; // 优秀
        } else if (avgLength >= 100) {
            qualityScore = 8.0;  // 良好
        } else if (avgLength >= 50) {
            qualityScore = 6.0;  // 中等
        } else if (avgLength >= 20) {
            qualityScore = 4.0;  // 及格
        } else {
            qualityScore = 2.0;  // 较差
        }
        
        return Math.round(qualityScore * 10.0) / 10.0;
    }
    
    /**
     * 计算多样性分（10分制）
     */
    private double calculateDiversityScore(List<ExtractedKnowledgePoint> extractedPoints) {
        if (extractedPoints.isEmpty()) return 0.0;
        
        // 统计不同的分类
        java.util.Set<String> categories = new java.util.HashSet<>();
        for (ExtractedKnowledgePoint point : extractedPoints) {
            String category = point.getCategory();
            if (category != null && !category.trim().isEmpty()) {
                categories.add(category);
            }
        }
        
        // 多样性评分（10分制）
        double diversityScore;
        if (categories.size() >= 5) {
            diversityScore = 10.0; // 高多样性
        } else if (categories.size() >= 3) {
            diversityScore = 8.0;  // 中高多样性
        } else if (categories.size() >= 2) {
            diversityScore = 6.0;  // 中等多样性
        } else if (categories.size() >= 1) {
            diversityScore = 4.0;  // 中低多样性
        } else {
            diversityScore = 2.0;  // 低多样性
        }
        
        return Math.round(diversityScore * 10.0) / 10.0;
    }
    
    /**
     * 计算新颖性分（10分制）
     */
    private double calculateNoveltyScore(List<ExtractedKnowledgePoint> extractedPoints) {
        if (extractedPoints.isEmpty()) return 0.0;
        
        int totalCount = extractedPoints.size();
        int newCount = (int) extractedPoints.stream()
                .filter(p -> ExtractedKnowledgePoint.Action.NEW == p.getAction()).count();
        int updateCount = (int) extractedPoints.stream()
                .filter(p -> ExtractedKnowledgePoint.Action.UPDATE == p.getAction()).count();
        
        // 新增率：新增和更新的比例
        double noveltyRate = (double) (newCount + updateCount) / totalCount;
        
        // 新颖性评分（10分制）
        double noveltyScore;
        if (noveltyRate >= 0.8) {
            noveltyScore = 10.0; // 高新颖性
        } else if (noveltyRate >= 0.6) {
            noveltyScore = 8.0;  // 中高新颖性
        } else if (noveltyRate >= 0.4) {
            noveltyScore = 6.0;  // 中等新颖性
        } else if (noveltyRate >= 0.2) {
            noveltyScore = 4.0;  // 中低新颖性
        } else {
            noveltyScore = 2.0;  // 低新颖性
        }
        
        return Math.round(noveltyScore * 10.0) / 10.0;
    }
    
    /**
     * 生成评分说明
     */
    private String generateExplanation(List<ExtractedKnowledgePoint> extractedPoints, int duration,
                                       double densityScore, double effectivenessScore) {
        int pointCount = extractedPoints.size();
        
        StringBuilder explanation = new StringBuilder();
        explanation.append("【本地算法评分】\n");
        explanation.append(String.format("共提取 %d 个知识点，视频时长 %d 分 %d 秒\n", 
                pointCount, duration / 60, duration % 60));
        
        // 密度说明（10分制）
        if (densityScore >= 8.0) {
            explanation.append("知识密度：高（每分钟超过3个知识点）\n");
        } else if (densityScore >= 6.0) {
            explanation.append("知识密度：中高（每分钟2-3个知识点）\n");
        } else if (densityScore >= 4.0) {
            explanation.append("知识密度：中等（每分钟1-2个知识点）\n");
        } else {
            explanation.append("知识密度：较低（每分钟少于1个知识点）\n");
        }
        
        // 效果说明（10分制）
        if (effectivenessScore >= 8.0) {
            explanation.append("知识效果：优秀\n");
        } else if (effectivenessScore >= 6.0) {
            explanation.append("知识效果：良好\n");
        } else if (effectivenessScore >= 4.0) {
            explanation.append("知识效果：中等\n");
        } else {
            explanation.append("知识效果：有待提升\n");
        }
        
        explanation.append("注：此评分由本地算法计算，AI评分服务不可用时使用。");
        
        return explanation.toString();
    }
}