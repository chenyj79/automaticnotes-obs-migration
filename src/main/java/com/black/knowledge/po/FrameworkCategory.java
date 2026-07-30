package com.black.knowledge.po;

import com.black.knowledge.vo.FrameworkCategoryVo;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 框架分类定义实体
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "framework_category")
public class FrameworkCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "framework_id", nullable = false)
    private Long frameworkId;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "definition", nullable = false, columnDefinition = "TEXT")
    private String definition;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public FrameworkCategoryVo toVo() {
        FrameworkCategoryVo vo = new FrameworkCategoryVo();
        vo.setId(id);
        vo.setFrameworkId(frameworkId);
        vo.setName(name);
        vo.setDefinition(definition);
        vo.setSortOrder(sortOrder);
        vo.setCreateTime(createTime);
        vo.setUpdateTime(updateTime);
        return vo;
    }
}

