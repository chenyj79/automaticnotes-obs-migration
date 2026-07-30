package com.black.knowledge.service;

import com.black.knowledge.dto.ai.IndependentKnowledgePoint;
import com.alibaba.fastjson2.JSON;
import cn.hutool.core.util.StrUtil;
import com.black.asr.po.Video;
import com.black.asr.repository.VideoRepository;
import com.black.exception.BusinessException;
import com.black.knowledge.dto.AddKnowledgePointRequest;
import com.black.knowledge.dto.AddRelationRequest;
import com.black.knowledge.dto.UpdateKnowledgePointRequest;
import com.black.knowledge.dto.ai.ExtractedKnowledgePoint;
import com.black.knowledge.po.KnowledgePoint;
import com.black.knowledge.po.KnowledgePointVideoRef;
import com.black.knowledge.repository.KnowledgePointRepository;
import com.black.knowledge.repository.KnowledgePointVideoRefRepository;
import com.black.knowledge.repository.KnowledgeRelationRepository;
import com.black.knowledge.vo.KnowledgePointVo;
import com.black.knowledge.vo.PointVideoIndexingVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识点服务
 * 负责知识点的CRUD、树形结构构建、以及AI知识提取后的增量学习
 * 使用Redis VectorStore进行语义匹配做增量初筛
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePointService {

    private final KnowledgePointRepository pointRepository;
    private final KnowledgeRelationRepository relationRepository;
    private final KnowledgePointVideoRefRepository videoRefRepository;
    private final VideoRepository videoRepository;
    private final AiService aiService;
    private final VectorStore vectorStore;
    private final KnowledgeRelationService relationService;
    private final FrameworkCategoryService frameworkCategoryService;
    private final KnowledgePointDraftService draftService; // 注入draftService以便调用generateAndSaveSegmentSummary

    private static final int VECTOR_TOP_K = 5;

    // ==================== 向量检索结果 ====================

    /**
     * 向量检索结果：包含按ID索引的知识点Map
     */
    public record VectorSearchResult(
            Map<Long, KnowledgePoint> matchedPoints) {
    }

    // ==================== 知识提取 ====================

    /**
     * 从视频中提取知识点（3阶段流水线：独立提取 -> 向量检索 -> AI对齐比对）
     * 注意：此方法只返回提取结果，不直接操作数据库
     *
     * @param videoId        视频ID
     * @param frameworkId    知识框架ID
     * @param transcriptText 转写文本
     * @param subject        学科/主题
     * @return 最终提取并对齐后的知识点列表
     */
    public List<ExtractedKnowledgePoint> extractFromVideo(Long videoId, Long frameworkId,
            String transcriptText, String subject) {
        String categoryDefinitions = frameworkCategoryService.buildCategoryDefinitionsForPrompt(frameworkId);

        // 查询"股票"分类下的现有知识点作为锚点
        String anchorTitles = buildAnchorTitles(frameworkId);

        // 1. 阶段一：独立提取（仅基于转写文本）
        log.info("阶段一：开始独立知识提取 - videoId: {}, anchorTitles: {}", videoId,
                anchorTitles != null && !anchorTitles.isEmpty() ? anchorTitles : "无");
        List<IndependentKnowledgePoint> independentPoints = aiService.extractIndependentKnowledge(transcriptText,
                subject, categoryDefinitions, anchorTitles);
        if (independentPoints == null || independentPoints.isEmpty()) {
            log.warn("阶段一：未提取到任何独立知识点 - videoId: {}", videoId);
            return Collections.emptyList();
        }
        log.info("阶段一：独立知识提取完成 - videoId: {}, 提取到 {} 个知识点", videoId, independentPoints.size());

        // 2. 向量语义检索：针对每个提取出的点，在库中找相似点
        log.info("阶段二：开始向量检索相似知识点 - videoId: {}", videoId);
        Map<Long, KnowledgePoint> allMatchedPoints = new HashMap<>();
        for (IndependentKnowledgePoint ip : independentPoints) {
            VectorSearchResult res = searchSimilarKnowledge(ip.getTitle() + ": " + ip.getContent(), frameworkId);
            log.info("  - 相似点检索: [{}] -> 找到 {} 个候选点", ip.getTitle(), res.matchedPoints().size());
            allMatchedPoints.putAll(res.matchedPoints());
        }
        log.info("阶段二：相似知识点聚合完成 - 总计 {} 个候选点", allMatchedPoints.size());

        // 构建给AI对比的上下文JSON
        String similarContextJson = JSON.toJSONString(allMatchedPoints.values().stream()
                .map(p -> Map.of(
                        "id", p.getId(),
                        "title", p.getTitle(),
                        "category", p.getCategory() != null ? p.getCategory() : "",
                        "content", p.getContent() != null ? p.getContent() : ""))
                .collect(Collectors.toList()));

        // 3. 阶段三：AI对齐比对（确定 NEW/UPDATE/REDUNDANT 及关系）
        log.info("阶段三：开始AI对齐比对");
        List<ExtractedKnowledgePoint> extractedPoints = aiService.reconcileKnowledge(
                JSON.toJSONString(independentPoints), similarContextJson, categoryDefinitions);
        log.info("阶段三：AI对齐比对完成 - videoId: {}, 得到 {} 个最终知识点", videoId, extractedPoints.size());

        // 4. 填充现有知识点内容（仅用于返回结果，不保存）
        for (ExtractedKnowledgePoint item : extractedPoints) {
            if ((ExtractedKnowledgePoint.Action.UPDATE == item.getAction()
                    || ExtractedKnowledgePoint.Action.REDUNDANT == item.getAction())
                    && item.getExistingPointId() != null) {
                KnowledgePoint point = allMatchedPoints.get(item.getExistingPointId());
                if (point != null) {
                    item.setExistingContent(point.getContent());
                }
            }
        }

        return extractedPoints;
    }

    /**
     * 构建锚点标题列表（用于"股票"等预建知识点类别）
     * 查询框架下 category="股票" 的所有知识点，格式化为 prompt 可用的标题列表
     */
    private String buildAnchorTitles(Long frameworkId) {
        List<KnowledgePoint> stockPoints = pointRepository.findByFrameworkIdAndCategory(frameworkId, "股票");
        if (stockPoints == null || stockPoints.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (KnowledgePoint point : stockPoints) {
            sb.append("- ").append(point.getTitle()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 向量语义检索Top-K相似知识点
     */
    private VectorSearchResult searchSimilarKnowledge(String queryText, Long frameworkId) {
        try {
            List<Document> similarDocs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(queryText)
                            .topK(VECTOR_TOP_K)
                            .filterExpression("frameworkId == " + "'" + frameworkId + "'")
                            .build());

            if (similarDocs == null || similarDocs.isEmpty()) {
                return new VectorSearchResult(new HashMap<>());
            }

            // 提取匹配到的知识点ID
            List<Long> pointIds = similarDocs.stream()
                    .map(doc -> Long.parseLong((String) doc.getMetadata().get("pointId")))
                    .toList();

            // 从数据库批量查回完整实体
            Map<Long, KnowledgePoint> matchedPoints = pointRepository.findAllById(pointIds).stream()
                    .collect(Collectors.toMap(KnowledgePoint::getId, p -> p, (a, b) -> a));

            log.debug("向量检索执行成功 - query: {}, 匹配数: {}", queryText, matchedPoints.size());
            return new VectorSearchResult(matchedPoints);
        } catch (Exception e) {
            log.warn("向量检索失败，回退到空列表: {}", e.getMessage());
            return new VectorSearchResult(new HashMap<>());
        }
    }

    // ==================== 向量存储操作 ====================

    public void addToVectorStore(KnowledgePoint point) {
        try {
            String textContent = point.getTitle() + ": " +
                    (StrUtil.isNotBlank(point.getCategory()) ? "[" + point.getCategory() + "] " : "") +
                    (point.getContent() != null ? point.getContent() : "");
            Document doc = new Document(
                    "kp-" + point.getId(),
                    textContent,
                    Map.of("title", point.getTitle(),
                            "category", point.getCategory() != null ? point.getCategory() : "",
                            "frameworkId", String.valueOf(point.getFrameworkId()),
                            "pointId", String.valueOf(point.getId())));
            vectorStore.add(List.of(doc));
        } catch (Exception e) {
            log.warn("向量存储写入失败: {}", e.getMessage());
        }
    }

    public void removeFromVectorStore(Long pointId) {
        try {
            vectorStore.delete(List.of("kp-" + pointId));
        } catch (Exception e) {
            log.warn("向量存储删除失败: {}", e.getMessage());
        }
    }

    // ==================== 知识点创建 ====================

    /**
     * 创建新知识点（父节点通过ID从Top-K匹配结果中查找）
     */
    private KnowledgePoint createNewPoint(Long frameworkId, ExtractedKnowledgePoint item) {
        KnowledgePoint point = new KnowledgePoint();
        point.setFrameworkId(frameworkId);
        point.setTitle(item.getTitle());
        point.setContent(item.getContent());
        point.setCategory(normalizeCategory(item.getCategory(), frameworkId));

        point = pointRepository.save(point);
        addToVectorStore(point);

        return point;
    }

    // ==================== CRUD ====================

    public KnowledgePointVo addKnowledgePoint(AddKnowledgePointRequest request) {
        KnowledgePoint point = new KnowledgePoint();
        point.setFrameworkId(request.getFrameworkId());
        point.setTitle(request.getTitle());
        point.setContent(request.getContent());
        point.setCategory(normalizeCategory(request.getCategory(), request.getFrameworkId()));
        pointRepository.save(point);
        addToVectorStore(point);

        // 处理视频索引关联
        if (request.getVideoRefs() != null && !request.getVideoRefs().isEmpty()) {
            for (AddKnowledgePointRequest.VideoRefRequest refReq : request.getVideoRefs()) {
                if (refReq.getVideoId() != null && refReq.getSegments() != null && !refReq.getSegments().isEmpty()) {
                    KnowledgePointVideoRef ref = new KnowledgePointVideoRef();
                    ref.setKnowledgePointId(point.getId());
                    ref.setVideoId(refReq.getVideoId());
                    ref.setTimestamps(JSON.toJSONString(refReq.getSegments()));
                    videoRefRepository.save(ref);

                    draftService.generateAndSaveSegmentSummary(point, ref);
                }
            }
        }

        KnowledgePointVo vo = point.toVo();
        populateVideoIndexings(Collections.singletonList(vo));
        return vo;
    }

    /**
     * 保存知识点（从草稿创建）
     */
    @Transactional
    public KnowledgePoint saveKnowledgePoint(KnowledgePoint point) {
        if (point.getCategory() != null) {
            point.setCategory(normalizeCategory(point.getCategory(), point.getFrameworkId()));
        }
        point = pointRepository.save(point);
        addToVectorStore(point);
        return point;
    }

    @Transactional
    public KnowledgePointVo updateKnowledgePoint(Long pointId, UpdateKnowledgePointRequest request) {
        KnowledgePoint point = pointRepository.findById(pointId)
                .orElseThrow(() -> BusinessException.knowledgePointNotFound(pointId));

        boolean vectorChanged = false;
        if (request.getTitle() != null) {
            point.setTitle(request.getTitle());
            vectorChanged = true;
        }
        if (request.getContent() != null) {
            point.setContent(request.getContent());
            vectorChanged = true;
        }
        if (request.getCategory() != null) {
            point.setCategory(normalizeCategory(request.getCategory(), point.getFrameworkId()));
            vectorChanged = true;
        }

        if (vectorChanged) {
            pointRepository.save(point);
            removeFromVectorStore(pointId);
            addToVectorStore(point);
        }

        // 处理关系增量更新
        if (request.getRelationUpdates() != null) {
            for (UpdateKnowledgePointRequest.RelationUpdate relUpd : request.getRelationUpdates()) {
                if ("ADD".equals(relUpd.getAction())) {
                    relationService.addRelation(AddRelationRequest.builder()
                            .sourcePointId(relUpd.getSourcePointId())
                            .targetPointId(relUpd.getTargetPointId())
                            .relationType(relUpd.getRelationType())
                            .build());
                } else if ("DELETE".equals(relUpd.getAction())) {
                    relationRepository.deleteBySourcePointIdAndTargetPointIdAndRelationType(
                            relUpd.getSourcePointId(), relUpd.getTargetPointId(), relUpd.getRelationType());
                }
            }
        }

        // 处理视频索引增量更新
        if (request.getVideoUpdates() != null) {
            for (UpdateKnowledgePointRequest.VideoUpdate vidUpd : request.getVideoUpdates()) {
                if ("SET".equals(vidUpd.getAction())) {
                    Optional<KnowledgePointVideoRef> existingRef = videoRefRepository
                            .findByKnowledgePointIdAndVideoId(pointId, vidUpd.getVideoId());
                    KnowledgePointVideoRef ref = existingRef.orElseGet(KnowledgePointVideoRef::new);
                    ref.setKnowledgePointId(pointId);
                    ref.setVideoId(vidUpd.getVideoId());
                    ref.setTimestamps(JSON.toJSONString(vidUpd.getSegments()));
                    videoRefRepository.save(ref);

                    draftService.generateAndSaveSegmentSummary(point, ref);
                } else if ("DELETE".equals(vidUpd.getAction())) {
                    videoRefRepository.deleteByKnowledgePointIdAndVideoId(pointId, vidUpd.getVideoId());
                }
            }
        }

        KnowledgePointVo vo = point.toVo();
        populateVideoIndexings(Collections.singletonList(vo));
        return vo;
    }

    @Transactional
    public void deleteKnowledgePoint(Long pointId) {
        if (!pointRepository.existsById(pointId)) {
            throw BusinessException.knowledgePointNotFound(pointId);
        }

        removeFromVectorStore(pointId);
        relationRepository.deleteBySourcePointIdOrTargetPointId(pointId, pointId);
        videoRefRepository.deleteByKnowledgePointId(pointId);
        pointRepository.deleteById(pointId);
    }

    public Long getFrameworkIdByPointId(Long pointId) {
        KnowledgePoint point = pointRepository.findById(pointId)
                .orElseThrow(() -> BusinessException.knowledgePointNotFound(pointId));
        return point.getFrameworkId();
    }

    @Transactional
    public void deleteAllByFrameworkId(Long frameworkId) {
        List<KnowledgePoint> points = pointRepository.findByFrameworkId(frameworkId);

        try {
            List<String> ids = points.stream().map(p -> "kp-" + p.getId()).toList();
            if (!ids.isEmpty()) {
                vectorStore.delete(ids);
            }
        } catch (Exception e) {
            log.warn("向量存储批量删除失败: {}", e.getMessage());
        }

        for (KnowledgePoint point : points) {
            relationRepository.deleteBySourcePointIdOrTargetPointId(point.getId(), point.getId());
            videoRefRepository.deleteByKnowledgePointId(point.getId());
        }
        pointRepository.deleteAll(points);
    }

    // ==================== 知识点查询 ====================

    /**
     * 获取框架下所有知识点（扁平列表，前端通过CONTAINS关系构建树）
     */
    public List<KnowledgePointVo> getKnowledgePoints(Long frameworkId) {
        return getKnowledgePointsByCategory(frameworkId, null);
    }

    public List<KnowledgePointVo> getKnowledgePointsByCategory(Long frameworkId, String category) {
        List<KnowledgePoint> pointEntities;
        if (StrUtil.isBlank(category)) {
            pointEntities = pointRepository.findByFrameworkId(frameworkId);
        } else {
            pointEntities = pointRepository.findByFrameworkIdAndCategory(frameworkId,
                    normalizeCategory(category, frameworkId));
        }

        List<KnowledgePointVo> points = pointEntities.stream()
                .map(KnowledgePoint::toVo)
                .collect(Collectors.toList());

        populateVideoIndexings(points);
        return points;
    }

    /**
     * 根据视频ID获取相关联的所有知识点（用于视频详情页的笔记功能）
     */
    public List<KnowledgePointVo> getKnowledgePointsByVideoId(Long videoId) {
        List<KnowledgePointVideoRef> refs = videoRefRepository.findByVideoId(videoId);
        if (refs.isEmpty())
            return Collections.emptyList();

        List<Long> pointIds = refs.stream().map(KnowledgePointVideoRef::getKnowledgePointId).toList();

        List<KnowledgePointVo> vos = pointRepository.findAllById(pointIds).stream()
                .map(KnowledgePoint::toVo)
                .toList();

        populateVideoIndexings(vos);
        return vos;
    }

    /**
     * 批量填充知识点的视频索引信息（聚合所有涉及的视频）
     */
    private void populateVideoIndexings(List<KnowledgePointVo> points) {
        if (points == null || points.isEmpty())
            return;

        List<Long> pointIds = points.stream().map(KnowledgePointVo::getId).toList();
        List<KnowledgePointVideoRef> allRefs = videoRefRepository.findByKnowledgePointIdIn(pointIds);
        if (allRefs.isEmpty())
            return;

        // 获取涉及的所有视频ID
        Set<Long> videoIds = allRefs.stream().map(KnowledgePointVideoRef::getVideoId).collect(Collectors.toSet());
        Map<Long, Video> videoMap = videoRepository.findAllById(videoIds).stream()
                .collect(Collectors.toMap(Video::getId, v -> v, (a, b) -> a));

        // 按知识点ID分组
        Map<Long, List<KnowledgePointVideoRef>> refsByPointId = allRefs.stream()
                .collect(Collectors.groupingBy(KnowledgePointVideoRef::getKnowledgePointId));

        for (KnowledgePointVo vo : points) {
            List<KnowledgePointVideoRef> refs = refsByPointId.get(vo.getId());
            if (refs != null) {
                List<PointVideoIndexingVo> indexingVos = refs.stream()
                        .map(ref -> {
                            Video v = videoMap.get(ref.getVideoId());
                            return PointVideoIndexingVo.builder()
                                    .videoId(ref.getVideoId())
                                    .videoTitle(v != null ? v.getOriginalFileName() : "未知视频")
                                    .timestamps(ref.getTimestamps())
                                    .videoSummary(ref.getSegmentSummary() != null ? ref.getSegmentSummary()
                                            : (v != null ? v.getSummary() : null))
                                    .build();
                        })
                        .collect(Collectors.toList());
                vo.setVideoIndexings(indexingVos);
            }
        }
    }

    private String normalizeCategory(String category, Long frameworkId) {
        return frameworkCategoryService.resolveCategoryForFramework(frameworkId, category);
    }
}
