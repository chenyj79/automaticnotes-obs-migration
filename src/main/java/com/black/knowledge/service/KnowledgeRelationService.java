package com.black.knowledge.service;

import com.black.exception.BusinessException;
import com.black.knowledge.dto.AddRelationRequest;
import com.black.knowledge.dto.ai.ExtractedKnowledgePoint;
import com.black.knowledge.dto.ai.ExtractedRelation;
import com.black.knowledge.enums.RelationType;
import com.black.knowledge.po.KnowledgePoint;
import com.black.knowledge.po.KnowledgeRelation;
import com.black.knowledge.repository.KnowledgePointRepository;
import com.black.knowledge.repository.KnowledgeRelationRepository;
import com.black.knowledge.vo.KnowledgeRelationVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识点关联关系服务
 * 负责关系的CRUD及AI提取后的关系自动入库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRelationService {

    private final KnowledgeRelationRepository relationRepository;
    private final KnowledgePointRepository pointRepository;

    /**
     * 手动添加关系
     */
    public KnowledgeRelationVo addRelation(AddRelationRequest request) {
        // 校验两个知识点存在
        KnowledgePoint source = pointRepository.findById(request.getSourcePointId())
                .orElseThrow(() -> BusinessException.knowledgePointNotFound(request.getSourcePointId()));
        KnowledgePoint target = pointRepository.findById(request.getTargetPointId())
                .orElseThrow(() -> BusinessException.knowledgePointNotFound(request.getTargetPointId()));

        if (isRelationInvalid(request.getSourcePointId(), request.getTargetPointId(), request.getRelationType())) {
            throw BusinessException.knowledgeRelationConflict();
        }

        KnowledgeRelation relation = new KnowledgeRelation();
        relation.setSourcePointId(request.getSourcePointId());
        relation.setTargetPointId(request.getTargetPointId());
        relation.setRelationType(request.getRelationType());
        relationRepository.save(relation);

        return relation.toVo(source.getTitle(), target.getTitle());
    }

    /**
     * 删除关系
     */
    public void deleteRelation(Long relationId) {
        if (!relationRepository.existsById(relationId)) {
            throw new BusinessException("关系不存在, ID: " + relationId);
        }
        relationRepository.deleteById(relationId);
    }

    /**
     * 查询知识点的所有关系（包括作为 source 或 target 的）
     */
    public List<KnowledgeRelationVo> getRelationsByPointId(Long pointId) {
        List<KnowledgeRelation> relations = relationRepository
                .findBySourcePointIdOrTargetPointId(pointId, pointId);
        return toVoList(relations);
    }

    /**
     * 查询框架下的所有关系（前端构建知识图谱）
     */
    public List<KnowledgeRelationVo> getRelationsByFrameworkId(Long frameworkId) {
        // 先获取框架下所有知识点ID
        List<Long> pointIds = pointRepository.findByFrameworkId(frameworkId).stream()
                .map(KnowledgePoint::getId)
                .toList();

        if (pointIds.isEmpty()) {
            return List.of();
        }

        // 查询涉及这些知识点的所有关系
        List<KnowledgeRelation> allRelations = new ArrayList<>();
        for (Long pointId : pointIds) {
            allRelations.addAll(relationRepository.findBySourcePointIdOrTargetPointId(pointId, pointId));
        }

        // 去重（同一关系可能被sourcePointId和targetPointId都匹配到）
        List<KnowledgeRelation> uniqueRelations = allRelations.stream()
                .collect(Collectors.toMap(KnowledgeRelation::getId, r -> r, (a, b) -> a))
                .values().stream().toList();

        return toVoList(uniqueRelations);
    }

    /**
     * 从AI提取结果中保存关系
     * 使用title→ID映射表解析AI输出中的标题引用
     */
    @Transactional
    public void saveExtractedRelations(List<ExtractedKnowledgePoint> extractedPoints,
                                       Map<String, Long> titleToIdMap) {
        for (ExtractedKnowledgePoint point : extractedPoints) {
            if (point.getRelations() == null || point.getRelations().isEmpty()) {
                continue;
            }

            for (ExtractedRelation rel : point.getRelations()) {
                Long sourceId = titleToIdMap.get(rel.getSourceTitle());
                Long targetId = titleToIdMap.get(rel.getTargetTitle());

                if (sourceId == null || targetId == null || sourceId.equals(targetId)) {
                    log.debug("跳过无效关系: {} -> {}", rel.getSourceTitle(), rel.getTargetTitle());
                    continue;
                }

                try {
                    RelationType relationType = RelationType.valueOf(rel.getRelationType());

                    if (isRelationInvalid(sourceId, targetId, relationType)) {
                        log.warn("跳过冲突或重复的AI关系: {} -> {}, type: {}", rel.getSourceTitle(), rel.getTargetTitle(), relationType);
                        continue;
                    }

                    KnowledgeRelation relation = new KnowledgeRelation();
                    relation.setSourcePointId(sourceId);
                    relation.setTargetPointId(targetId);
                    relation.setRelationType(relationType);
                    relationRepository.save(relation);
                } catch (IllegalArgumentException e) {
                    log.warn("无法解析关系类型: {}", rel.getRelationType());
                }
            }
        }
    }

    /**
     * 转换为VO列表（补充标题信息）
     */
    private List<KnowledgeRelationVo> toVoList(List<KnowledgeRelation> relations) {
        // 收集所有涉及的pointId
        Set<Long> allPointIds = new HashSet<>(relations.size() * 2);
        for (KnowledgeRelation r : relations) {
            allPointIds.add(r.getSourcePointId());
            allPointIds.add(r.getTargetPointId());
        }

        Map<Long, String> titleMap = pointRepository.findAllById(allPointIds).stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, KnowledgePoint::getTitle, (a, b) -> a));

        return relations.stream()
                .map(r -> r.toVo(
                        titleMap.getOrDefault(r.getSourcePointId(), ""),
                        titleMap.getOrDefault(r.getTargetPointId(), "")))
                .collect(Collectors.toList());
    }

    /**
     * 校验关系是否合法
     */
    private boolean isRelationInvalid(Long sourceId, Long targetId, RelationType type) {
        // 正向存在相同类型的关系 => 重复
        if (relationRepository.findBySourcePointIdAndTargetPointId(sourceId, targetId)
                .stream().anyMatch(r -> r.getRelationType() == type)) {
            return true;
        }
        // 反向存在相同类型的关系 => 对于 PREREQUISITE/CONTAINS 会造成循环，对于 RELATED 视为重复
        return relationRepository.findBySourcePointIdAndTargetPointId(targetId, sourceId)
                .stream().anyMatch(r -> r.getRelationType() == type);
    }
}
