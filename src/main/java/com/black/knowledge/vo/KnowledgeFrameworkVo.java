package com.black.knowledge.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识框架视图对象
 */
@Data
public class KnowledgeFrameworkVo {
    private Long id;
    private Long userId;
    private String name;
    private String subject;
    private String description;
    private List<FrameworkCategoryVo> categories;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
