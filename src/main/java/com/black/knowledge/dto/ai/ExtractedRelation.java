package com.black.knowledge.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI提取的知识点关系
 */
@Data
@NoArgsConstructor
public class ExtractedRelation {
    /** 源知识点标题（当前提取的知识点标题） */
    private String sourceTitle;
    /** 目标知识点标题（关联的知识点标题） */
    private String targetTitle;
    /** 关系类型：PREREQUISITE / RELATED / EXTENDS */
    private String relationType;
}
