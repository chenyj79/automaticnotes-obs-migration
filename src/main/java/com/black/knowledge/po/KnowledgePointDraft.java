package com.black.knowledge.po;

import com.black.knowledge.enums.DraftStatus;
import com.black.knowledge.dto.ai.ExtractedKnowledgePoint;
import com.black.knowledge.vo.KnowledgePointDraftVo;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 知识点草稿实体类
 * 用于存储待审核的知识点
 */
@Data
@Entity
@NoArgsConstructor
public class KnowledgePointDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Basic
    @Column(name = "video_id", nullable = false)
    private Long videoId;

    @Basic
    @Column(name = "framework_id", nullable = false)
    private Long frameworkId;

    @Basic
    @Column(name = "title", nullable = false)
    private String title;

    @Basic
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content; // AI生成的详细笔记内容

    @Basic
    @Column(name = "category")
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    private ExtractedKnowledgePoint.Action action; // NEW, UPDATE, REDUNDANT

    @Basic
    @Column(name = "existing_point_id")
    private Long existingPointId; // 如果是UPDATE，对应的已有知识点ID

    @Basic
    @Column(name = "timestamps", columnDefinition = "TEXT")
    private String timestamps; // 视频时间戳 JSON

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DraftStatus status; // PENDING, APPROVED, REJECTED

    @Basic
    @Column(name = "ai_suggestion", columnDefinition = "TEXT")
    private String aiSuggestion; // AI的分类建议

    @Basic
    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment; // 审核意见

    @CreationTimestamp
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public KnowledgePointDraftVo toVo() {
        KnowledgePointDraftVo vo = new KnowledgePointDraftVo();
        vo.setId(id);
        vo.setVideoId(videoId);
        vo.setFrameworkId(frameworkId);
        vo.setTitle(title);
        vo.setContent(content);
        vo.setCategory(category);
        vo.setAction(action != null ? action.name() : null);
        vo.setExistingPointId(existingPointId);
        vo.setTimestamps(timestamps);
        vo.setStatus(status.name());
        vo.setAiSuggestion(aiSuggestion);
        vo.setReviewComment(reviewComment);
        vo.setCreateTime(createTime);
        vo.setUpdateTime(updateTime);
        return vo;
    }
}
