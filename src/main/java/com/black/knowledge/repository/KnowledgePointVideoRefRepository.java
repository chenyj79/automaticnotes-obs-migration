package com.black.knowledge.repository;

import com.black.knowledge.po.KnowledgePointVideoRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgePointVideoRefRepository extends JpaRepository<KnowledgePointVideoRef, Long> {
    List<KnowledgePointVideoRef> findByKnowledgePointId(Long knowledgePointId);

    List<KnowledgePointVideoRef> findByVideoId(Long videoId);

    List<KnowledgePointVideoRef> findByKnowledgePointIdIn(List<Long> knowledgePointIds);

    Optional<KnowledgePointVideoRef> findByKnowledgePointIdAndVideoId(Long knowledgePointId, Long videoId);

    void deleteByKnowledgePointId(Long knowledgePointId);

    void deleteByVideoId(Long videoId);

    void deleteByKnowledgePointIdAndVideoId(Long knowledgePointId, Long videoId);
}
