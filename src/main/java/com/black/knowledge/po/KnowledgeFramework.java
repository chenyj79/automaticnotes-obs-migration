package com.black.knowledge.po;

import com.black.knowledge.vo.KnowledgeFrameworkVo;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识框架实体类
 */
@Data
@Entity
@NoArgsConstructor
public class KnowledgeFramework {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Basic
    @Column(name = "user_id")
    private Long userId;

    @Basic
    @Column(name = "name")
    private String name;

    @Basic
    @Column(name = "subject")
    private String subject;

    @Basic
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Basic
    @Column(name = "create_time")
    @CreationTimestamp
    private LocalDateTime createTime;

    @Basic
    @Column(name = "update_time")
    @UpdateTimestamp
    private LocalDateTime updateTime;

    public KnowledgeFrameworkVo toVo() {
        return toVo(null);
    }

    public KnowledgeFrameworkVo toVo(List<com.black.knowledge.vo.FrameworkCategoryVo> categories) {
        KnowledgeFrameworkVo vo = new KnowledgeFrameworkVo();
        vo.setId(id);
        vo.setUserId(userId);
        vo.setName(name);
        vo.setSubject(subject);
        vo.setDescription(description);
        vo.setCategories(categories);
        vo.setCreateTime(createTime);
        vo.setUpdateTime(updateTime);
        return vo;
    }
}
