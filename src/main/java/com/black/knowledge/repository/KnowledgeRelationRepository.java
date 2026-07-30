package com.black.knowledge.repository;

import com.black.knowledge.enums.RelationType;
import com.black.knowledge.po.KnowledgeRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeRelationRepository extends JpaRepository<KnowledgeRelation, Long> {
    List<KnowledgeRelation> findBySourcePointId(Long sourcePointId);

    List<KnowledgeRelation> findByTargetPointId(Long targetPointId);

    List<KnowledgeRelation> findBySourcePointIdAndTargetPointId(Long sourcePointId, Long targetPointId);


    List<KnowledgeRelation> findBySourcePointIdOrTargetPointId(Long sourcePointId, Long targetPointId);

    void deleteBySourcePointIdOrTargetPointId(Long sourcePointId, Long targetPointId);

    void deleteBySourcePointIdAndTargetPointIdAndRelationType(Long sourcePointId, Long targetPointId, RelationType relationType);
}
