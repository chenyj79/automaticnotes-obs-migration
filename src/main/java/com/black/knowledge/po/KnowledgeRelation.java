package com.black.knowledge.po;

import com.black.knowledge.enums.RelationType;
import com.black.knowledge.vo.KnowledgeRelationVo;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识点关联关系实体类
 * 记录知识点之间的关联关系（如前置、相关、包含等）
 */
@Data
@Entity
@NoArgsConstructor
public class KnowledgeRelation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Basic
    @Column(name = "source_point_id")
    private Long sourcePointId;

    @Basic
    @Column(name = "target_point_id")
    private Long targetPointId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type")
    private RelationType relationType;

    public KnowledgeRelationVo toVo(String sourceTitle, String targetTitle) {
        KnowledgeRelationVo vo = new KnowledgeRelationVo();
        vo.setId(this.id);
        vo.setSourcePointId(this.sourcePointId);
        vo.setSourcePointTitle(sourceTitle);
        vo.setTargetPointId(this.targetPointId);
        vo.setTargetPointTitle(targetTitle);
        vo.setRelationType(this.relationType);
        return vo;
    }
}
