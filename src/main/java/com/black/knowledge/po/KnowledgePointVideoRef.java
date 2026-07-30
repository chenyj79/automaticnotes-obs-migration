package com.black.knowledge.po;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识点与视频时间戳映射实体类
 * 记录某个知识点在哪个视频的哪些时间段出现
 */
@Data
@Entity
@NoArgsConstructor
public class KnowledgePointVideoRef {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Basic
    @Column(name = "knowledge_point_id")
    private Long knowledgePointId;

    @Basic
    @Column(name = "video_id")
    private Long videoId;

    @Basic
    @Column(name = "timestamps", columnDefinition = "TEXT")
    private String timestamps; // JSON格式的时间戳列表，如 [{"start":10,"end":30},{"start":120,"end":150}]

    @Basic
    @Column(name = "segment_summary", columnDefinition = "TEXT")
    private String segmentSummary; // AI对该关联时间片段的专门总结
}
