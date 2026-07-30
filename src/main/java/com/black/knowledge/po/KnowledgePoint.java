package com.black.knowledge.po;

import com.black.knowledge.vo.KnowledgePointVo;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 知识点实体类
 * 知识点之间的关系（包括包含/父子）统一通过 KnowledgeRelation 表达
 */
@Data
@Entity
@NoArgsConstructor
public class KnowledgePoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Basic
    @Column(name = "framework_id")
    private Long frameworkId;

    @Basic
    @Column(name = "title")
    private String title;

    @Basic
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content; // AI生成的详细笔记内容

    @Basic
    @Column(name = "category")
    private String category;

    @Basic
    @Column(name = "create_time")
    @CreationTimestamp
    private LocalDateTime createTime;

    @Basic
    @Column(name = "update_time")
    @UpdateTimestamp
    private LocalDateTime updateTime;

    public KnowledgePointVo toVo() {
        KnowledgePointVo vo = new KnowledgePointVo();
        vo.setId(id);
        vo.setFrameworkId(frameworkId);
        vo.setTitle(title);
        vo.setContent(content);
        vo.setCategory(category);
        vo.setCreateTime(createTime);
        vo.setUpdateTime(updateTime);
        return vo;
    }
}
