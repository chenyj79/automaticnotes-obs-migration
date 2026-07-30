package com.black.knowledge.vo;

import com.black.knowledge.enums.RelationType;
import lombok.Data;

/**
 * 知识点关联关系VO
 */
@Data
public class KnowledgeRelationVo {
    private Long id;
    private Long sourcePointId;
    private String sourcePointTitle;
    private Long targetPointId;
    private String targetPointTitle;
    private RelationType relationType;
}
