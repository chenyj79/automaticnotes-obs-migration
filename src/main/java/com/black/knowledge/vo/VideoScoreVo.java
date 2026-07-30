package com.black.knowledge.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频评分视图对象
 */
@Data
public class VideoScoreVo {
    private Long id;
    private Long videoId;
    private Double densityScore;
    private Double effectivenessScore;
    private Double totalScore;
    private Integer knowledgeCount;
    private String explanation;
    private LocalDateTime createTime;
}
