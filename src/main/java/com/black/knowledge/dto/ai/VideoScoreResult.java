package com.black.knowledge.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI视频评分结构化输出
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class VideoScoreResult {
    /** 知识密度评分 0-10 */
    private Double densityScore;

    /** 有效性评分 0-10 */
    private Double effectivenessScore;

    /** 综合评分 0-10 */
    private Double totalScore;

    /** 评分解释（200字以内） */
    private String explanation;
}
