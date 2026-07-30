package com.black.knowledge.po;

import com.black.knowledge.vo.VideoScoreVo;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 视频评分实体类
 * 记录视频的知识密度评分和有效性评分
 */
@Data
@Entity
@NoArgsConstructor
public class VideoScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Basic
    @Column(name = "video_id")
    private Long videoId;

    @Basic
    @Column(name = "density_score")
    private Double densityScore; // 知识密度评分 0-10

    @Basic
    @Column(name = "effectiveness_score")
    private Double effectivenessScore; // 有效性评分 0-10

    @Basic
    @Column(name = "total_score")
    private Double totalScore; // 综合评分 0-10

    @Basic
    @Column(name = "knowledge_count")
    private Integer knowledgeCount; // 知识点数量

    @Basic
    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation; // 评分解释

    @Basic
    @Column(name = "create_time")
    @CreationTimestamp
    private LocalDateTime createTime;

    public VideoScoreVo toVo() {
        VideoScoreVo vo = new VideoScoreVo();
        vo.setId(id);
        vo.setVideoId(videoId);
        vo.setDensityScore(densityScore);
        vo.setEffectivenessScore(effectivenessScore);
        vo.setTotalScore(totalScore);
        vo.setKnowledgeCount(knowledgeCount);
        vo.setExplanation(explanation);
        vo.setCreateTime(createTime);
        return vo;
    }
}
