package com.black.knowledge.repository;

import com.black.knowledge.po.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {
    List<KnowledgePoint> findByFrameworkId(Long frameworkId);

    List<KnowledgePoint> findByFrameworkIdAndCategory(Long frameworkId, String category);

    List<KnowledgePoint> findByFrameworkIdAndTitleContaining(Long frameworkId, String keyword);

    List<KnowledgePoint> findByTitleContaining(String keyword);

    long countByFrameworkIdIn(List<Long> frameworkIds);
}
