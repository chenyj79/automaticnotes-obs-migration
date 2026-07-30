package com.black.knowledge.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 阶段一：独立提取的知识点结构
 */
@Data
@NoArgsConstructor
public class IndependentKnowledgePoint {
    private String title;
    private String content;
    private String category;
    private List<ExtractedKnowledgePoint.Timestamp> timestamps;
}
