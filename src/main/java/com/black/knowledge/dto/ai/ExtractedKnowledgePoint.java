package com.black.knowledge.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI知识提取结构化输出
 * 表示从转写文本中提取的一个知识点
 */
@Data
@NoArgsConstructor
public class ExtractedKnowledgePoint {
    /** 知识点标题 */
    private String title;

    /** AI生成的详细笔记内容（Markdown格式） */
    private String content;

    /** 知识点分类（如：定义、公式、方法、例题） */
    private String category;

    /** 操作类型 */
    private Action action;

    /** 如果是UPDATE，对应的已有知识点ID */
    private Long existingPointId;

    /** 如果是UPDATE，已有知识点的原始内容（由程序填充，非AI输出） */
    private transient String existingContent;

    /** 知识点在视频中出现的时间段列表 */
    private List<Timestamp> timestamps;

    /** 该知识点与其他知识点的关系列表 */
    private List<ExtractedRelation> relations;

    @Data
    @NoArgsConstructor
    public static class Timestamp {
        private Integer start;
        private Integer end;
    }

    /** 操作类型枚举 */
    public enum Action {
        /** 新增 */
        NEW,
        /** 补充 */
        UPDATE,
        /** 重复 */
        REDUNDANT
    }
}
